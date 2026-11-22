// 回家方式比較（規格 6.5、8.6；FR-HWY-04；GUIDE 附錄 E-5）

import { busTimetable } from "./bus";
import { cached } from "./cache";
import { freewayParams, trafficData } from "./freeway";
import { railTrips, taiwanToday, type Trip } from "./rail";
import cityTransport from "./static/city-transport.json";
import interchangeCity from "./static/interchange-city.json";
import { ApiError, type Env } from "./tdx";

const COMPARE_TTL_S = 60; // 與路況一致（8.5）

export type Mode = "THSR" | "TRA" | "BUS";

/** 縣市的代表車站（static/city-transport.json）；沒有該運具時為 null */
export interface CityTransport {
  tra: { id: string; name: string } | null;
  thsr: { id: string; name: string; transferMin: number } | null;
  bus: string | null;
}

export interface CompareOption {
  operator: Mode;
  label: string;
  departTime: string | null;
  arriveTime: string | null;
  waitMin: number | null;
  transferMin: number | null;
  totalMin: number | null;
  note: string;
}

export interface Compare {
  fromCity: string;
  toCity: string;
  drive: { totalMin: number };
  options: CompareOption[];
  fastest: Mode | "DRIVE";
}

/** 某運具的班次：null＝縣市沒有這種運具（或起訖同站），顯示「—」；空陣列＝今天沒有班次 */
export type ModeTrips = Record<Mode, Trip[] | null>;

const MODES: Mode[] = ["THSR", "TRA", "BUS"];
const MODE_NAME: Record<Mode, string> = { THSR: "高鐵", TRA: "台鐵", BUS: "國道客運" };

const toMin = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

/** 臺灣時間現在是當天第幾分鐘 */
export function taiwanNowMin(now = Date.now()): number {
  return Math.floor(((now + 8 * 3_600_000) % 86_400_000) / 60_000);
}

/** 交流道 → 縣市 → 代表車站；對照表沒有時 NOT_FOUND */
export function citiesOf(from: string, to: string): { fromCity: string; toCity: string; a: CityTransport; b: CityTransport } {
  const ic = interchangeCity as Record<string, string>;
  const ct = cityTransport as Record<string, CityTransport>;
  const fromCity = ic[from];
  const toCity = ic[to];
  if (!fromCity || !toCity || !ct[fromCity] || !ct[toCity]) throw new ApiError("NOT_FOUND", "Interchange not in city table");
  return { fromCity, toCity, a: ct[fromCity], b: ct[toCity] };
}

/** 實作者確認：從現在之後的班次中挑「等車＋行駛」最短的一班（最早到達），而不是最早發車的一班 */
export function bestTrip(trips: Trip[], nowMin: number): Trip | null {
  let best: Trip | null = null;
  for (const t of trips) {
    const wait = toMin(t.departTime) - nowMin;
    if (wait < 0) continue;
    if (!best || wait + t.durationMin < toMin(best.departTime) - nowMin + best.durationMin) best = t;
  }
  return best;
}

function label(mode: Mode, t: Trip, a: CityTransport): string {
  switch (mode) {
    case "THSR":
      return `高鐵・${a.thsr!.name}站`;
    case "TRA":
      return `台鐵・${t.typeName} ${t.tripId}`;
    case "BUS":
      return `國道客運・${[t.typeName.replace(/客運$/, ""), t.typeCode].filter(Boolean).join(" ")}`;
  }
}

/**
 * E-5：每種運具的總耗時＝等車＋行駛＋轉乘（高鐵站在市區外者，起訖兩端各加固定轉乘分鐘，見對照表）。
 * 沒有班次的運具不參與「最快」；開車也參與比較。同分時大眾運輸優先（依高鐵、台鐵、國道客運順序），開車要嚴格較快才算最快。
 */
export function toCompare(
  fromCity: string,
  toCity: string,
  a: CityTransport,
  b: CityTransport,
  trips: ModeTrips,
  driveMin: number,
  nowMin: number,
): Compare {
  const options = MODES.map((mode): CompareOption => {
    const list = trips[mode];
    const empty = { operator: mode, label: MODE_NAME[mode], departTime: null, arriveTime: null, waitMin: null, transferMin: null, totalMin: null };
    if (list === null) return { ...empty, note: "—" };
    const t = bestTrip(list, nowMin);
    if (!t) return { ...empty, note: "今日已無班次" };
    const waitMin = toMin(t.departTime) - nowMin;
    const transferMin = mode === "THSR" ? a.thsr!.transferMin + b.thsr!.transferMin : 0;
    return {
      operator: mode,
      label: label(mode, t, a),
      departTime: t.departTime,
      arriveTime: t.arriveTime,
      waitMin,
      transferMin,
      totalMin: waitMin + t.durationMin + transferMin,
      note: transferMin > 0 ? "含轉乘" : `${waitMin} 分後`,
    };
  });

  let fastest: Mode | "DRIVE" = "DRIVE";
  let best = Infinity;
  for (const o of options) {
    if (o.totalMin !== null && o.totalMin < best) {
      best = o.totalMin;
      fastest = o.operator;
    }
  }
  if (driveMin < best) fastest = "DRIVE";
  return { fromCity, toCity, drive: { totalMin: driveMin }, options, fastest };
}

/**
 * 單一運具：縣市沒有或起訖同站回 null。
 * 限流（QUOTA）、金鑰（AUTH）、解析（PARSE）錯誤往外拋，整個比較失敗、不快取；
 * 連線（NETWORK）、查無（NOT_FOUND，例如客運索引未建立）與其他錯誤視為查不到（「—」）。
 */
async function modeTrips(produce: (() => Promise<Trip[]>) | null): Promise<Trip[] | null> {
  if (!produce) return null;
  try {
    return await produce();
  } catch (e) {
    if (e instanceof ApiError && (e.code === "QUOTA" || e.code === "AUTH" || e.code === "PARSE")) throw e;
    return null;
  }
}

/** GET /api/compare?freeway&dir&from&to（E-5）：三種運具與路況並行查詢（8.6），時刻與路況沿用各自端點的快取 */
export async function handleCompare(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const p = freewayParams(url);
  const { fromCity, toCity, a, b } = citiesOf(p.from, p.to);
  const key = `/api/compare?freeway=${p.roadId}&dir=${p.dir}&from=${encodeURIComponent(p.from)}&to=${encodeURIComponent(p.to)}`;
  return cached(key, COMPARE_TTL_S, ctx, async () => {
    const now = Date.now();
    const date = taiwanToday(now);
    const rail = (op: "THSR" | "TRA", x: { id: string } | null, y: { id: string } | null) =>
      x && y && x.id !== y.id ? () => railTrips(env, ctx, op, x.id, y.id, date) : null;
    const bus = a.bus && b.bus && a.bus !== b.bus ? async () => (await busTimetable(env, ctx, a.bus!, b.bus!, date)).data : null;

    const [traffic, thsr, tra, busTrips] = await Promise.all([
      trafficData(env, ctx, p),
      modeTrips(rail("THSR", a.thsr, b.thsr)),
      modeTrips(rail("TRA", a.tra, b.tra)),
      modeTrips(bus),
    ]);
    return toCompare(fromCity, toCity, a, b, { THSR: thsr, TRA: tra, BUS: busTrips }, traffic.estimatedMin, taiwanNowMin(now));
  });
}
