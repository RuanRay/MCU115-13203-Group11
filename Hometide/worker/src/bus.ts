// 國道客運（規格 6.5）：站牌、時刻、票價、預估到站（8.3；附錄 E-2、E-3、E-7、E-8；FR-BUS-01～03）
// 「哪些路線依序經過起訖站」只讀 scripts/build-index.ts 每日寫入 KV 的索引（8.6），請求中不掃描全部路線

import { cached, cachedData } from "./cache";
import { durationMin, normalizeName, taiwanToday, validDate, type Fare, type Trip } from "./rail";
import { ApiError, json, tdxGet, type Env } from "./tdx";

// 8.5 快取時間（秒）
const STATIONS_TTL_S = 7 * 86_400;
const TIMETABLE_TTL_S = 2 * 3_600;
const FARE_TTL_S = 7 * 86_400;
const LIVE_TTL_S = 60;
const KV_CACHE_TTL_S = 3_600; // KV 邊緣快取；索引每日更新一次
const ETA_MATCH_MIN = 60; // 預估到站與表定時間相差超過此分鐘數就不指派（實作者確認的優化方案）
const FILTER_BATCH = 40; // $filter 每批路線數（網址長度保護，與 rail.ts LIVE_FILTER_MAX 同理）
const MAX_STOP_NAME = 40;

// 與 scripts/build-index.ts 一致
const STATIONS_KEY = "bus:stations";
const INDEX_KEY = "bus:index";

export interface BusIndex {
  routes: Record<string, { route: string; direction: number; operators: string[]; stops: [string, number][] }>;
  stops: Record<string, [string, number, number][]>;
}

/** GET /api/stations?operator=BUS（E-7）；索引尚未建立時回 NOT_FOUND */
export async function handleBusStations(env: Env, ctx: ExecutionContext): Promise<Response> {
  return cached("/api/stations?operator=BUS", STATIONS_TTL_S, ctx, async () => {
    const v = await env.BUS_INDEX.get<{ stations?: unknown[] }>(STATIONS_KEY, { type: "json", cacheTtl: KV_CACHE_TTL_S });
    if (!Array.isArray(v?.stations)) throw new ApiError("NOT_FOUND", "Bus index not built");
    return { operator: "BUS", stations: v.stations };
  });
}

async function readIndex(env: Env): Promise<BusIndex> {
  const v = await env.BUS_INDEX.get<BusIndex>(INDEX_KEY, { type: "json", cacheTtl: KV_CACHE_TTL_S });
  if (!v?.routes || !v.stops) throw new ApiError("NOT_FOUND", "Bus index not built");
  return v;
}

/** 候選子路線：起站可上車（StopBoarding ≠ -1）、迄站可下車（≠ 1）且迄站在起站之後 */
export function candidateSubs(index: BusIndex, from: string, to: string): string[] {
  const toSeq = new Map<string, number>();
  for (const [sub, seq, b] of index.stops[to] ?? []) if (b !== 1) toSeq.set(sub, seq);
  const subs = (index.stops[from] ?? [])
    .filter(([sub, seq, b]) => b !== -1 && (toSeq.get(sub) ?? -1) > seq)
    .map(([sub]) => sub);
  return [...new Set(subs)];
}

/** from／to 為站名（id 即站名，3-3 實作者確認）；缺少或起訖相同回 NOT_FOUND（站名不在索引中由 busRoutes 檢查；有站但沒有路線回空陣列） */
function busParams(url: URL): { from: string; to: string } {
  const from = normalizeName(url.searchParams.get("from") ?? "").trim(); // 8.7：比對前台→臺
  const to = normalizeName(url.searchParams.get("to") ?? "").trim();
  if (!from || !to || from === to || from.length > MAX_STOP_NAME || to.length > MAX_STOP_NAME) {
    throw new ApiError("NOT_FOUND", "Invalid stops");
  }
  return { from, to };
}

async function busRoutes(env: Env, from: string, to: string) {
  const index = await readIndex(env);
  if (!index.stops[from] || !index.stops[to]) throw new ApiError("NOT_FOUND", "Unknown stop");
  const subs = candidateSubs(index, from, to);
  const routes = [...new Set(subs.map((s) => index.routes[s].route))];
  // 業者名稱不簡稱（實作者確認，與 8.7 不同，已記入 devlog）；聯營取第一家
  const operator = new Map<string, string>();
  for (const s of subs) if (!operator.has(index.routes[s].route)) operator.set(index.routes[s].route, index.routes[s].operators[0] ?? "");
  return { index, subs, routes, operator };
}

const orFilter = (field: string, values: string[]) => values.map((v) => `${field} eq '${v.replaceAll("'", "''")}'`).join(" or ");

/** 依路線名查 TDX；路線多時分批（每批 FILTER_BATCH 條）避免網址過長，結果合併 */
async function tdxByRoutes(env: Env, path: string, field: string, routes: string[], select: string): Promise<unknown[]> {
  const batches: string[][] = [];
  for (let i = 0; i < routes.length; i += FILTER_BATCH) batches.push(routes.slice(i, i + FILTER_BATCH));
  const parts = await Promise.all(batches.map((b) => tdxGet<unknown>(env, path, { $filter: orFilter(field, b), $select: select })));
  if (parts.some((p) => !Array.isArray(p))) throw new ApiError("PARSE", "Unexpected TDX format");
  return (parts as unknown[][]).flat();
}
const q = (s: string) => encodeURIComponent(s);

// ---------- 時刻 ----------

type TdxSchedule = {
  RouteName?: { Zh_tw?: string };
  SubRouteUID?: string;
  Timetables?: {
    ServiceDay?: Record<string, number>;
    StopTimes?: { StopName?: { Zh_tw?: string }; ArrivalTime?: string; DepartureTime?: string }[];
  }[];
};

/** 內部班次：多帶子路線（Schedule 的 SubRouteUID），供預估到站配對；輸出前移除 */
export type BusTrip = Trip & { subs: string[] };

const DAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
const TIME = /^(\d{1,2}):([0-5]\d)$/;

/** 客運班表可能出現 24:00 以後的時間，一律換成當日 HH:mm；格式不對回 null（略過該班，不讓整份失敗） */
function hhmm(t: string | undefined): string | null {
  const m = t ? TIME.exec(t) : null;
  if (!m) return null;
  return `${String(Number(m[1]) % 24).padStart(2, "0")}:${m[2]}`;
}

const toMin = (t: string) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
const fromMin = (m: number) => `${String(Math.floor(m / 60) % 24).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;

export function toBusTrips(raw: unknown, from: string, to: string, date: string, operator: Map<string, string>): BusTrip[] {
  if (!Array.isArray(raw)) throw new ApiError("PARSE", "Unexpected TDX schedule format");
  const day = DAYS[new Date(`${date}T00:00:00Z`).getUTCDay()];
  type Row = { route: string; sub: string; depart: string; arrive: string | null };
  const rows: Row[] = [];
  for (const r of raw as TdxSchedule[]) {
    const route = r.RouteName?.Zh_tw;
    if (!route || !r.SubRouteUID) continue;
    for (const t of r.Timetables ?? []) {
      if (t.ServiceDay?.[day] !== 1) continue;
      const stops = t.StopTimes ?? [];
      const i = stops.findIndex((s) => normalizeName(s.StopName?.Zh_tw ?? "") === from);
      const j = stops.findIndex((s, k) => k > i && normalizeName(s.StopName?.Zh_tw ?? "") === to);
      if (i < 0 || j < 0) continue;
      const depart = hhmm(stops[i].DepartureTime ?? stops[i].ArrivalTime);
      if (!depart) continue;
      rows.push({ route, sub: r.SubRouteUID, depart, arrive: hhmm(stops[j].ArrivalTime ?? stops[j].DepartureTime) });
    }
  }

  // 班表沒有抵達時間時，以同路線其他班次的平均行車時間推算（8.7）；沒有可參考的班次就略過
  const avg = new Map<string, number>();
  for (const route of new Set(rows.map((r) => r.route))) {
    const d = rows.filter((r) => r.route === route && r.arrive).map((r) => durationMin(r.depart, r.arrive!));
    if (d.length) avg.set(route, Math.round(d.reduce((a, b) => a + b, 0) / d.length));
  }

  // 同一路線同一發車時間只輸出一筆（8.7）；有班表抵達時間者優先
  const trips = new Map<string, BusTrip>();
  for (const r of [...rows].sort((a, b) => Number(!a.arrive) - Number(!b.arrive))) {
    const tripId = `${r.route}-${r.depart}`;
    const seen = trips.get(tripId);
    if (seen) {
      if (!seen.subs.includes(r.sub)) seen.subs.push(r.sub);
      continue;
    }
    const est = r.arrive ? null : avg.get(r.route);
    if (!r.arrive && est === undefined) continue;
    const arrive = r.arrive ?? fromMin(toMin(r.depart) + est!);
    trips.set(tripId, {
      tripId,
      typeCode: r.route,
      typeName: operator.get(r.route) ?? "",
      line: null,
      departTime: r.depart,
      arriveTime: arrive,
      durationMin: durationMin(r.depart, arrive),
      arriveEstimated: !r.arrive,
      reservedSeat: false,
      bikeAllowed: false,
      subs: [r.sub],
    });
  }
  return [...trips.values()].sort((a, b) => a.departTime.localeCompare(b.departTime));
}

/** 內部時刻（含 subs），時刻與預估到站共用同一份 2 小時快取 */
async function busTimetable(env: Env, ctx: ExecutionContext, from: string, to: string, date: string) {
  return cachedData(`/internal/bus-timetable?from=${q(from)}&to=${q(to)}&date=${date}`, TIMETABLE_TTL_S, ctx, async () => {
    const { routes, operator } = await busRoutes(env, from, to);
    if (routes.length === 0) return [] as BusTrip[];
    const raw = await tdxByRoutes(env, "/v2/Bus/Schedule/InterCity", "RouteName/Zh_tw", routes, "RouteName,SubRouteUID,Timetables");
    return toBusTrips(raw, from, to, date, operator);
  });
}

/** GET /api/timetable?operator=BUS&from&to&date（E-2） */
export async function handleBusTimetable(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { from, to } = busParams(url);
  const date = validDate(url.searchParams.get("date"));
  return cached(`/api/timetable?operator=BUS&from=${q(from)}&to=${q(to)}&date=${date}`, TIMETABLE_TTL_S, ctx, async () => {
    const { data } = await busTimetable(env, ctx, from, to, date);
    return { operator: "BUS", trips: data.map(({ subs: _subs, ...t }) => t) };
  });
}

// ---------- 票價 ----------

type TdxFareItem = { FareName?: string; Price?: number };
type TdxRouteFare = {
  RouteName?: string;
  StageFares?: { OriginStage?: { StopName?: string }; DestinationStage?: { StopName?: string }; Fares?: TdxFareItem[] }[];
};

/**
 * 依票種挑價（實作者確認：先篩票種 → 優惠價 → 最低原價）。目前規格只有全票（CR-01 於任務 4-5 擴充其他票種）。
 * 全票：只收名稱以「全票_」開頭者（排除電子票證、現金、刷卡、來回票、半票）；有「優惠」取最低優惠並標約，否則取原價／不分時段的最低價。
 */
export function pickAdultPrice(fares: TdxFareItem[] | undefined): { price: number; discounted: boolean } | null {
  const adult = (fares ?? []).filter((f) => f.FareName?.startsWith("全票_") && typeof f.Price === "number" && f.Price > 0);
  const promo = adult.filter((f) => f.FareName!.includes("_優惠_"));
  const pool = promo.length ? promo : adult;
  if (!pool.length) return null;
  return { price: Math.min(...pool.map((f) => f.Price!)), discounted: promo.length > 0 };
}

/** 分段：路線名 → 分段起點 → 分段終點 → 票價（同路線多筆子路線合併） */
export function toStageMap(raw: unknown): Map<string, Map<string, Map<string, TdxFareItem[]>>> {
  if (!Array.isArray(raw)) throw new ApiError("PARSE", "Unexpected TDX fare format");
  const map = new Map<string, Map<string, Map<string, TdxFareItem[]>>>();
  for (const r of raw as TdxRouteFare[]) {
    if (!r.RouteName) continue;
    const route = map.get(r.RouteName) ?? new Map<string, Map<string, TdxFareItem[]>>();
    map.set(r.RouteName, route);
    for (const s of r.StageFares ?? []) {
      const o = normalizeName(s.OriginStage?.StopName ?? "");
      const d = normalizeName(s.DestinationStage?.StopName ?? "");
      if (!o || !d) continue;
      if (!route.has(o)) route.set(o, new Map());
      if (!route.get(o)!.has(d)) route.get(o)!.set(d, s.Fares ?? []);
    }
  }
  return map;
}

/**
 * 分段吸附（實作者確認）：起站往前找最近的分段起點、迄站往後找該起點最近的分段終點。
 * 真實資料覆蓋率：直接比對 15.6% → 吸附 95.7%（2026-11-15 實測）。回傳 exact 表示起訖剛好是分段點。
 */
export function stageFare(
  stops: [string, number][],
  stages: Map<string, Map<string, TdxFareItem[]>>,
  from: string,
  to: string,
): { fares: TdxFareItem[]; exact: boolean } | null {
  const i = stops.findIndex(([n]) => n === from);
  const j = stops.findIndex(([n], k) => k > i && n === to);
  if (i < 0 || j < 0) return null;
  let oi = i;
  while (oi >= 0 && !stages.has(stops[oi][0])) oi--;
  if (oi < 0) return null;
  const dests = stages.get(stops[oi][0])!;
  for (let k = j; k < stops.length; k++) {
    const fares = dests.get(stops[k][0]);
    if (fares) return { fares, exact: oi === i && k === j };
  }
  return null;
}

export function toBusFares(index: BusIndex, subs: string[], raw: unknown, from: string, to: string): Fare[] {
  const stageMap = toStageMap(raw);
  const best = new Map<string, Fare>();
  for (const sub of subs) {
    const { route, stops } = index.routes[sub];
    const stages = stageMap.get(route);
    const hit = stages && stageFare(stops, stages, from, to);
    const p = hit && pickAdultPrice(hit.fares);
    if (!hit || !p) continue;
    const fare = { typeCode: route, adultPrice: p.price, approximate: !hit.exact || p.discounted };
    const prev = best.get(route);
    // 同路線多條子路線：精確對中優先，其次取較低價
    if (!prev || (prev.approximate && !fare.approximate) || (prev.approximate === fare.approximate && fare.adultPrice < prev.adultPrice)) {
      best.set(route, fare);
    }
  }
  return [...best.values()].sort((a, b) => a.typeCode.localeCompare(b.typeCode));
}

/** GET /api/fare?operator=BUS&from&to[&route]（E-8）；route 可省略，省略時回傳所有經過兩站的路線（實作者確認） */
export async function handleBusFare(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { from, to } = busParams(url);
  const route = url.searchParams.get("route");
  const allKey = `/api/fare?operator=BUS&from=${q(from)}&to=${q(to)}`;
  const produceAll = async () => {
    const { index, subs, routes } = await busRoutes(env, from, to);
    const raw = routes.length ? await tdxByRoutes(env, "/v2/Bus/RouteFare/InterCity", "RouteName", routes, "RouteName,StageFares") : [];
    return { operator: "BUS", fares: toBusFares(index, subs, raw, from, to) };
  };
  if (!route) return cached(allKey, FARE_TTL_S, ctx, produceAll);
  // 帶 route 時由同一份全部路線快取篩出，不另建快取鍵（GUIDE 3.4-4：任意 route 不會產生新鍵）
  const r = await cachedData(allKey, FARE_TTL_S, ctx, produceAll);
  return json({ updatedAt: r.updatedAt, source: "TDX", data: { ...r.data, fares: r.data.fares.filter((f) => f.typeCode === route) } }, 200, {
    "Cache-Control": `max-age=${FARE_TTL_S}`,
    "X-Cache": r.hit ? "HIT" : "MISS",
  });
}

// ---------- 預估到站 ----------

type TdxEta = { StopName?: { Zh_tw?: string }; SubRouteUID?: string; EstimateTime?: number; StopStatus?: number };

/**
 * 預估到站配對（實作者確認的優化方案「子路線＋時間最近」）：只用 StopStatus 0 且有 EstimateTime 的起站資料；
 * 在同一子路線的班次中，找表定發車最接近「現在＋預估」且相差 ≤ 60 分鐘者。每筆預估只指派一班。
 */
export function toBusLive(raw: unknown, trips: BusTrip[], from: string, nowMin: number): { tripId: string; etaMin: number }[] {
  if (!Array.isArray(raw)) throw new ApiError("PARSE", "Unexpected TDX ETA format");
  const out = new Map<string, number>();
  for (const e of raw as TdxEta[]) {
    if (e.StopStatus !== 0 || typeof e.EstimateTime !== "number" || !e.SubRouteUID) continue;
    if (normalizeName(e.StopName?.Zh_tw ?? "") !== from) continue;
    const etaMin = Math.ceil(e.EstimateTime / 60);
    const expected = nowMin + etaMin;
    let best: { id: string; diff: number } | null = null;
    for (const t of trips) {
      if (!t.subs.includes(e.SubRouteUID)) continue;
      let diff = Math.abs(toMin(t.departTime) - expected) % 1440;
      diff = Math.min(diff, 1440 - diff); // 跨午夜
      if (diff <= ETA_MATCH_MIN && (!best || diff < best.diff)) best = { id: t.tripId, diff };
    }
    if (best && (out.get(best.id) ?? Infinity) > etaMin) out.set(best.id, etaMin);
  }
  return [...out].map(([tripId, etaMin]) => ({ tripId, etaMin }));
}

/** GET /api/live?operator=BUS&from&to（E-3）。只查今天：共用今天時刻快取，再查 1 次預估到站 */
export async function handleBusLive(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { from, to } = busParams(url);
  return cached(`/api/live?operator=BUS&from=${q(from)}&to=${q(to)}`, LIVE_TTL_S, ctx, async () => {
    const now = Date.now();
    const { data: trips } = await busTimetable(env, ctx, from, to, taiwanToday(now));
    const routes = [...new Set(trips.map((t) => t.typeCode))];
    if (routes.length === 0) return { items: [] };
    const raw = await tdxByRoutes(env, "/v2/Bus/EstimatedTimeOfArrival/InterCity", "RouteName/Zh_tw", routes, "StopName,SubRouteUID,EstimateTime,StopStatus");
    const nowMin = Math.floor(((now + 8 * 3_600_000) % 86_400_000) / 60_000);
    return { items: toBusLive(raw, trips, from, nowMin) };
  });
}
