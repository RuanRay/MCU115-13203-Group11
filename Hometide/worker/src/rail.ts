// 台鐵、高鐵（規格 6.5）：目前只有車站清單（8.3 /api/stations、GUIDE 附錄 E-7）

import { cached } from "./cache";
import { ApiError, tdxGet, type Env } from "./tdx";

const STATIONS_TTL_S = 7 * 86_400; // 8.5：車站清單 7 天

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
