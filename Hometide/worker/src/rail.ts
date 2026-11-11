// 台鐵、高鐵（規格 6.5）：車站、時刻、票價、即時（8.3；GUIDE 附錄 E-1、E-3、E-7、E-8）

import { cached, cachedData } from "./cache";
import { ApiError, tdxGet, type Env } from "./tdx";

// 8.5 快取時間（秒）
const STATIONS_TTL_S = 7 * 86_400;
const TIMETABLE_TTL_S = 2 * 3_600;
const FARE_TTL_S = 7 * 86_400;
const LIVE_TTL_S = 60;
const LIVE_FILTER_MAX = 80; // 超過時不用 $filter（網址約 2 KB 上限的保守值）

const STATION_ID = /^\d{4}$/;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

/** 驗證 operator、from、to（台鐵、高鐵站碼皆為 4 位數字）；不合法一律 NOT_FOUND（錯誤代碼表沒有參數錯誤） */
function railQuery(url: URL): { op: RailOperator; from: string; to: string } {
  const op = url.searchParams.get("operator");
  if (op === "BUS") throw new ApiError("NOT_FOUND", "BUS not supported yet");
  if (op !== "TRA" && op !== "THSR") throw new ApiError("NOT_FOUND", "Unknown operator");
  const from = url.searchParams.get("from") ?? "";
  const to = url.searchParams.get("to") ?? "";
  if (!STATION_ID.test(from) || !STATION_ID.test(to) || from === to) throw new ApiError("NOT_FOUND", "Invalid stations");
  return { op, from, to };
}

/** 來回比對，排除 2026-02-30 這類不存在的日期 */
export function validDate(s: string | null): string {
  const t = s && DATE.test(s) ? Date.parse(`${s}T00:00:00Z`) : NaN;
  if (Number.isNaN(t) || new Date(t).toISOString().slice(0, 10) !== s) throw new ApiError("NOT_FOUND", "Invalid date");
  return s;
}

/** 臺灣時間的今天 yyyy-MM-dd */
export function taiwanToday(now = Date.now()): string {
  return new Date(now + 8 * 3_600_000).toISOString().slice(0, 10);
}

// GUIDE 3.4-5：以 $select 只取轉換需要的欄位（3-2 以真實 TDX 確認可用）
async function timetable(env: Env, op: RailOperator, from: string, to: string, date: string) {
  const trips: Trip[] =
    op === "TRA"
      ? toTraTrips(
          await tdxGet(env, `/v3/Rail/TRA/DailyTrainTimetable/OD/${from}/to/${to}/${date}`, { $select: "TrainInfo,StopTimes" }),
          from,
          to,
        )
      : toThsrTrips(
          await tdxGet(env, `/v2/Rail/THSR/DailyTimetable/OD/${from}/to/${to}/${date}`, {
            $select: "DailyTrainInfo,OriginStopTime,DestinationStopTime",
          }),
        );
  return { operator: op, trips };
}

const timetableKey = (op: string, from: string, to: string, date: string) =>
  `/api/timetable?operator=${op}&from=${from}&to=${to}&date=${date}`;

/** GET /api/timetable?operator=TRA|THSR&from&to&date（E-1） */
export async function handleTimetable(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { op, from, to } = railQuery(url);
  const date = validDate(url.searchParams.get("date"));
  return cached(timetableKey(op, from, to, date), TIMETABLE_TTL_S, ctx, () => timetable(env, op, from, to, date));
}

/** GET /api/fare?operator=TRA|THSR&from&to（E-8） */
export async function handleFare(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { op, from, to } = railQuery(url);
  return cached(`/api/fare?operator=${op}&from=${from}&to=${to}`, FARE_TTL_S, ctx, async () => ({
    operator: op,
    fares:
      op === "TRA"
        ? toTraFares(await tdxGet(env, `/v3/Rail/TRA/ODFare/${from}/to/${to}`, { $select: "TrainType,TravelDistance,Fares" }))
        : toThsrFares(await tdxGet(env, `/v2/Rail/THSR/ODFare/${from}/to/${to}`, { $select: "Fares" })),
  }));
}

/**
 * GET /api/live?operator=TRA|THSR&from&to（E-3）。只查今天；高鐵沒有誤點資料，回空陣列（FR-RES-03，實作者確認）。
 * 台鐵：先取今天時刻（共用 2 小時快取），再以車次篩選 TrainLiveBoard。
 */
export async function handleLive(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { op, from, to } = railQuery(url);
  return cached(`/api/live?operator=${op}&from=${from}&to=${to}`, LIVE_TTL_S, ctx, async () => {
    if (op === "THSR") return { items: [] };
    const date = taiwanToday();
    const { data } = await cachedData(timetableKey(op, from, to, date), TIMETABLE_TTL_S, ctx, () =>
      timetable(env, op, from, to, date),
    );
    const ids = new Set(data.trips.map((t) => t.tripId));
    if (ids.size === 0) return { items: [] };
    // 車次太多時網址過長：改抓整份（只取兩個欄位）再由 toTraLive 篩選
    const params: Record<string, string> = { $select: "TrainNo,DelayTime" };
    if (ids.size <= LIVE_FILTER_MAX) params.$filter = [...ids].map((id) => `TrainNo eq '${id}'`).join(" or ");
    const raw = await tdxGet(env, "/v3/Rail/TRA/TrainLiveBoard", params);
    return { items: toTraLive(raw, ids) };
  });
}

/** GET /api/stations?operator=TRA|THSR（附錄 E-7）；BUS 站牌於第 3 週接客運路線索引 */
export async function handleStations(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const op = url.searchParams.get("operator");
  if (op === "BUS") throw new ApiError("NOT_FOUND", "BUS stations not supported yet");
  if (op !== "TRA" && op !== "THSR") throw new ApiError("NOT_FOUND", "Unknown operator");
  return cached(`/api/stations?operator=${op}`, STATIONS_TTL_S, ctx, () => stations(env, op));
}

export type RailOperator = "TRA" | "THSR";

export interface StationItem {
  id: string;
  nameZh: string;
  nameEn: string | null;
  city: string | null;
  sortKey: number;
}

type TdxStation = {
  StationID?: string;
  StationName?: { Zh_tw?: string; En?: string };
  LocationCity?: string; // THSR v2
  StationAddress?: string; // TRA v3 沒有 LocationCity，從地址取縣市
};

const STATION_QUERY: Record<RailOperator, { path: string; select: string }> = {
  TRA: { path: "/v3/Rail/TRA/Station", select: "StationID,StationName,StationAddress" },
  THSR: { path: "/v2/Rail/THSR/Station", select: "StationID,StationName,LocationCity" },
};

/** 規格 8.7：一律把「台」轉成「臺」 */
export function normalizeName(s: string): string {
  return s.replaceAll("台", "臺");
}

/** 地址開頭「郵遞區號＋縣市」，例：203001基隆市中山區… → 基隆市（縣市皆為兩字＋縣／市） */
export function cityFromAddress(address: string): string | null {
  return /^\d*\s*(\S{2}[縣市])/.exec(address)?.[1] ?? null;
}

/** TRA v3 回傳 { Stations: [...] }，THSR v2 回傳陣列；sortKey 依 TDX 回傳順序 */
export function toStations(raw: unknown): StationItem[] {
  const list = Array.isArray(raw) ? raw : (raw as { Stations?: unknown } | null)?.Stations;
  if (!Array.isArray(list)) throw new ApiError("PARSE", "Unexpected TDX station response");

  return (list as TdxStation[]).map((s, i) => {
    const id = s.StationID;
    const zh = s.StationName?.Zh_tw;
    if (!id || !zh) throw new ApiError("PARSE", "TDX station missing id or name");
    return {
      id,
      nameZh: normalizeName(zh),
      nameEn: s.StationName?.En ?? null,
      city: city(s),
      sortKey: i,
    };
  });
}

function city(s: TdxStation): string | null {
  const c = s.LocationCity ?? (s.StationAddress ? cityFromAddress(s.StationAddress) : null);
  return c ? normalizeName(c) : null;
}

export async function stations(env: Env, operator: RailOperator) {
  const { path, select } = STATION_QUERY[operator];
  const raw = await tdxGet<unknown>(env, path, { $select: select });
  return { operator, stations: toStations(raw) };
}

// ---- 回應轉換（規格 8.7；GUIDE 附錄 E-1、E-3、E-8）：純函式，不連網路，方便單元測試 ----

/** E-1 / E-2 的班次 */
export interface Trip {
  tripId: string;
  typeCode: string;
  typeName: string;
  line: number | null;
  departTime: string;
  arriveTime: string;
  durationMin: number;
  arriveEstimated: boolean;
  reservedSeat: boolean;
  bikeAllowed: boolean;
}

/** E-8 的票價 */
export interface Fare {
  typeCode: string;
  adultPrice: number;
  approximate: boolean;
}

export const THSR_TYPE = { code: "THSR", name: "高鐵" } as const;

/** 台鐵車種代碼（3-1 由 /v3/Rail/TRA/TrainType 查證）→ 簡稱、是否對號（實作者確認） */
const TRA_TYPES: Record<string, { name: string; reserved: boolean }> = {
  "1": { name: "太魯閣", reserved: true },
  "2": { name: "普悠瑪", reserved: true },
  "3": { name: "自強", reserved: true },
  "4": { name: "莒光", reserved: true },
  "5": { name: "復興", reserved: true },
  "6": { name: "區間", reserved: false },
  "7": { name: "普快", reserved: false },
  "10": { name: "區間快", reserved: false },
  "11": { name: "自強3000", reserved: true },
};
const TRA_LOCAL = "6"; // FR-RES-02：非對號列車取區間車票價
const TRA_EXPRESS = "3"; // 對號車種找不到票價時以自強代替（標示約）

const HHMM = /^([01]\d|2[0-3]):[0-5]\d$/;

function minutes(t: string | undefined): number {
  if (!t || !HHMM.test(t)) throw new ApiError("PARSE", "Invalid time in TDX timetable");
  return Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5));
}

/** 行駛分鐘；抵達早於出發表示跨午夜，加 24 小時（8.7） */
export function durationMin(depart: string, arrive: string): number {
  const d = minutes(arrive) - minutes(depart);
  return d < 0 ? d + 1440 : d;
}

/** 台鐵車種簡稱：對照表沒有時取括號前的文字 */
export function traTypeName(code: string, zh: string | undefined): string {
  return TRA_TYPES[code]?.name ?? (zh ?? "").replace(/[（(].*$/, "").trim();
}

const byDepart = (a: Trip, b: Trip) => a.departTime.localeCompare(b.departTime);

type TraTimetable = {
  TrainTimetables?: {
    TrainInfo?: { TrainNo?: string; TrainTypeCode?: string; TrainTypeName?: { Zh_tw?: string }; TripLine?: number; BikeFlag?: number };
    StopTimes?: { StationID?: string; ArrivalTime?: string; DepartureTime?: string }[];
  }[];
};

export function toTraTrips(raw: unknown, from: string, to: string): Trip[] {
  const list = (raw as TraTimetable | null)?.TrainTimetables;
  if (!Array.isArray(list)) throw new ApiError("PARSE", "Unexpected TRA timetable");
  return list
    .map((t) => {
      const info = t.TrainInfo;
      const stops = t.StopTimes ?? [];
      const o = stops.find((s) => s.StationID === from) ?? stops[0];
      const d = stops.find((s) => s.StationID === to) ?? stops[stops.length - 1];
      if (!info?.TrainNo || !o?.DepartureTime || !d?.ArrivalTime) throw new ApiError("PARSE", "TRA trip missing fields");
      const code = info.TrainTypeCode ?? "";
      return {
        tripId: info.TrainNo,
        typeCode: code,
        typeName: traTypeName(code, info.TrainTypeName?.Zh_tw),
        line: typeof info.TripLine === "number" ? info.TripLine : null,
        departTime: o.DepartureTime,
        arriveTime: d.ArrivalTime,
        durationMin: durationMin(o.DepartureTime, d.ArrivalTime),
        arriveEstimated: false,
        reservedSeat: TRA_TYPES[code]?.reserved ?? false,
        bikeAllowed: info.BikeFlag === 1,
      };
    })
    .sort(byDepart);
}

type ThsrTimetable = {
  DailyTrainInfo?: { TrainNo?: string };
  OriginStopTime?: { DepartureTime?: string };
  DestinationStopTime?: { ArrivalTime?: string };
}[];

export function toThsrTrips(raw: unknown): Trip[] {
  if (!Array.isArray(raw)) throw new ApiError("PARSE", "Unexpected THSR timetable");
  return (raw as ThsrTimetable)
    .map((t) => {
      const no = t.DailyTrainInfo?.TrainNo;
      const dep = t.OriginStopTime?.DepartureTime;
      const arr = t.DestinationStopTime?.ArrivalTime;
      if (!no || !dep || !arr) throw new ApiError("PARSE", "THSR trip missing fields");
      return {
        tripId: no,
        typeCode: THSR_TYPE.code,
        typeName: THSR_TYPE.name,
        line: null,
        departTime: dep,
        arriveTime: arr,
        durationMin: durationMin(dep, arr),
        arriveEstimated: false,
        reservedSeat: true,
        bikeAllowed: false,
      };
    })
    .sort(byDepart);
}

type TdxFare = { TicketType?: number; FareClass?: number; CabinClass?: number; Price?: number };

/** 單程（TicketType 1）＋全票（FareClass 1）＋標準座／對號座（CabinClass 1），由 3-1 數值推定、實作者確認 */
function adultPrice(fares: TdxFare[] | undefined): number | null {
  const f = fares?.find((x) => x.TicketType === 1 && x.FareClass === 1 && x.CabinClass === 1);
  return typeof f?.Price === "number" ? f.Price : null;
}

/**
 * 台鐵：每個已知車種一筆。對號車種取自己的價格，沒有時以自強代替並標示約；
 * 非對號車種一律取區間車價格（FR-RES-02）。都取不到的車種不輸出（App 顯示「—」）。
 */
export function toTraFares(raw: unknown): Fare[] {
  type Od = { TrainType?: number | string; TravelDistance?: number; Fares?: TdxFare[] };
  const list = (raw as { ODFares?: Od[] } | null)?.ODFares;
  if (!Array.isArray(list)) throw new ApiError("PARSE", "Unexpected TRA fare");
  // 同一車種有兩個方向（Direction 0／1）：一個走西部直達、一個繞東部環島，里程不同（3-1 查證）。取里程最短者
  const best = new Map<string, { price: number; dist: number }>();
  for (const od of list) {
    const p = adultPrice(od.Fares);
    const code = od.TrainType == null ? "" : String(od.TrainType);
    const dist = typeof od.TravelDistance === "number" ? od.TravelDistance : Infinity;
    if (p == null || !code) continue;
    const cur = best.get(code);
    if (!cur || dist < cur.dist) best.set(code, { price: p, dist });
  }
  const price = new Map([...best].map(([k, v]) => [k, v.price]));
  const out: Fare[] = [];
  for (const [code, t] of Object.entries(TRA_TYPES)) {
    if (!t.reserved) {
      const p = price.get(TRA_LOCAL);
      if (p != null) out.push({ typeCode: code, adultPrice: p, approximate: false });
    } else if (price.has(code)) {
      out.push({ typeCode: code, adultPrice: price.get(code)!, approximate: false });
    } else if (price.has(TRA_EXPRESS)) {
      out.push({ typeCode: code, adultPrice: price.get(TRA_EXPRESS)!, approximate: true });
    }
  }
  return out;
}

export function toThsrFares(raw: unknown): Fare[] {
  if (!Array.isArray(raw)) throw new ApiError("PARSE", "Unexpected THSR fare");
  const p = adultPrice((raw[0] as { Fares?: TdxFare[] } | undefined)?.Fares);
  return p == null ? [] : [{ typeCode: THSR_TYPE.code, adultPrice: p, approximate: false }];
}

/** E-3：台鐵以車次對應延誤分鐘；對應不到的車次視為準點，不輸出（8.7） */
export function toTraLive(raw: unknown, tripIds: Set<string>): { tripId: string; delayMin: number }[] {
  const list = (raw as { TrainLiveBoards?: { TrainNo?: string; DelayTime?: number }[] } | null)?.TrainLiveBoards;
  if (!Array.isArray(list)) throw new ApiError("PARSE", "Unexpected TRA live board");
  const seen = new Set<string>();
  const items: { tripId: string; delayMin: number }[] = [];
  for (const b of list) {
    if (!b.TrainNo || !tripIds.has(b.TrainNo) || seen.has(b.TrainNo) || typeof b.DelayTime !== "number") continue;
    seen.add(b.TrainNo);
    items.push({ tripId: b.TrainNo, delayMin: b.DelayTime });
  }
  return items;
}
