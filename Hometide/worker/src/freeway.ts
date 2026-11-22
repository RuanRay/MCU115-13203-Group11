// 國道（規格 6.5）：交流道清單、即時路況（8.3；GUIDE 附錄 E-4、E-9；8.7 國道路況）

import { cached, cachedData } from "./cache";
import { normalizeName } from "./rail";
import { ApiError, tdxGet, type Env } from "./tdx";

const META_TTL_S = 7 * 86_400; // 交流道清單 7 天（8.5）
const TRAFFIC_TTL_S = 60; // 即時路況 60 秒（8.5）
const LIVE_FILTER_MAX = 80; // 路段多於此數時改抓整份即時資料再篩選，避免網址過長

export type Direction = "S" | "N" | "E" | "W";
export type Level = "SMOOTH" | "BUSY" | "JAM" | "UNKNOWN";

const DIRECTION_NAME: Record<Direction, string> = { S: "南下", N: "北上", E: "東向", W: "西向" };
const isDirection = (d: unknown): d is Direction => typeof d === "string" && Object.hasOwn(DIRECTION_NAME, d);

export type TdxSection = {
  SectionID?: string;
  RoadID?: string;
  RoadName?: string;
  RoadDirection?: string;
  RoadSection?: { Start?: string; End?: string };
  SectionLength?: number;
  SectionMile?: { StartKM?: string; EndKM?: string };
  SpeedLimit?: number;
};

export type TdxLive = {
  SectionID?: string;
  TravelTime?: number; // 秒
  TravelSpeed?: number;
  CongestionLevel?: string;
};

/** Worker 內部整理後的路段（只保留國道、欄位齊全者） */
export interface Section {
  id: string;
  roadId: string;
  roadName: string;
  dir: Direction;
  start: string;
  end: string;
  startKm: number;
  endKm: number;
  lengthKm: number;
  speedLimit: number;
}

export interface Interchange {
  name: string;
  km: number;
}

export interface FreewayMeta {
  freeways: {
    id: string;
    name: string;
    directions: { dir: Direction; name: string; interchanges: Interchange[] }[];
  }[];
}

export interface Segment {
  fromIc: string;
  toIc: string;
  speedKmh: number | null;
  level: Level;
  travelMin: number | null;
}

export interface Traffic {
  freewayName: string;
  directionName: string;
  from: string;
  to: string;
  estimatedMin: number;
  normalMin: number;
  congestedCount: number;
  segments: Segment[];
}

/** 「12K+300」→ 12.3 */
export function parseKm(s: string | undefined): number | null {
  const m = /^(\d+)K\+(\d+)$/.exec(s ?? "");
  return m ? Number(m[1]) + Number(m[2]) / 1000 : null;
}

/** 「國道1號」→「國道 1 號」、「國道2甲」→「國道 2 甲」（GUIDE 附錄 E-4 範例格式） */
export function freewayDisplayName(roadName: string): string {
  return roadName.replace(/^國道(\d+)(號|甲)/, "國道 $1 $2");
}

/** TDX 壅塞等級（freeway-congestion.json）：1 順暢、2～3 車多、4～5 壅塞、-1 道路封閉（視為壅塞）；其他為無資料 */
export function toLevel(level: string | undefined): Level {
  switch (level) {
    case "1":
      return "SMOOTH";
    case "2":
    case "3":
      return "BUSY";
    case "4":
    case "5":
    case "-1":
      return "JAM";
    default:
      return "UNKNOWN";
  }
}

/** 只保留名稱以「國道」開頭、欄位齊全的路段；交流道名稱「台」→「臺」（8.7） */
export function toSections(raw: unknown): Section[] {
  const list = (raw as { Sections?: TdxSection[] })?.Sections;
  if (!Array.isArray(list)) throw new ApiError("PARSE", "Invalid freeway sections");
  const out: Section[] = [];
  for (const s of list) {
    const dir = s.RoadDirection as Direction;
    const startKm = parseKm(s.SectionMile?.StartKM);
    const endKm = parseKm(s.SectionMile?.EndKM);
    if (!s.RoadName?.startsWith("國道") || !isDirection(dir)) continue;
    if (!s.SectionID || !s.RoadID || !s.RoadSection?.Start || !s.RoadSection?.End) continue;
    if (startKm === null || endKm === null || !(s.SectionLength! > 0) || !(s.SpeedLimit! > 0)) continue;
    out.push({
      id: s.SectionID,
      roadId: s.RoadID,
      roadName: s.RoadName,
      dir,
      start: normalizeName(s.RoadSection.Start),
      end: normalizeName(s.RoadSection.End),
      startKm,
      endKm,
      lengthKm: s.SectionLength!,
      speedLimit: s.SpeedLimit!,
    });
  }
  return out;
}

/** 某國道某方向的路段，依行車方向排序（南下、東向里程遞增；北上、西向遞減） */
export function orderedSections(sections: Section[], roadId: string, dir: Direction): Section[] {
  const forward = dir === "S" || dir === "E";
  return sections
    .filter((s) => s.roadId === roadId && s.dir === dir)
    .sort((a, b) => (forward ? a.startKm - b.startKm : b.startKm - a.startKm));
}

/**
 * 上路 [from] 到下路 [to] 之間的路段（依行車方向）；路線不存在、方向相反，
 * 或中間有路段因資料不齊被 [toSections] 排除（前後接不起來）時 NOT_FOUND，避免時間被低估。
 */
export function routeSections(sections: Section[], roadId: string, dir: Direction, from: string, to: string): Section[] {
  const ss = orderedSections(sections, roadId, dir);
  const i = ss.findIndex((s) => s.start === from);
  const j = ss.findIndex((s) => s.end === to);
  if (i < 0 || j < i) throw new ApiError("NOT_FOUND", "Unknown freeway route");
  const route = ss.slice(i, j + 1);
  if (route.some((s, k) => k > 0 && route[k - 1].end !== s.start)) throw new ApiError("NOT_FOUND", "Freeway route has gaps");
  return route;
}

/** E-9：國道、方向、依行車方向排序的交流道清單 */
export function toMeta(sections: Section[]): FreewayMeta {
  const roads = new Map<string, { name: string; dirs: Set<Direction> }>();
  for (const s of sections) {
    const r = roads.get(s.roadId) ?? { name: s.roadName, dirs: new Set<Direction>() };
    r.dirs.add(s.dir);
    roads.set(s.roadId, r);
  }
  const dirOrder: Direction[] = ["S", "N", "E", "W"];
  return {
    freeways: [...roads.entries()]
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([id, r]) => ({
        id,
        name: freewayDisplayName(r.name),
        directions: dirOrder
          .filter((d) => r.dirs.has(d))
          .map((dir) => {
            const ss = orderedSections(sections, id, dir);
            return {
              dir,
              name: DIRECTION_NAME[dir],
              interchanges: [
                { name: ss[0].start, km: ss[0].startKm },
                ...ss.map((s) => ({ name: s.end, km: s.endKm })),
              ],
            };
          }),
      })),
  };
}

/**
 * E-4：只取上路 [from] 到下路 [to] 之間的路段（[routeSections]）。
 * 預估時間＝各路段即時旅行時間總和；沒有即時資料的路段以平常時間代替，避免低估。
 * 平常時間＝各路段長度依速限換算之和；壅塞段數＝JAM 的段數（含道路封閉）。
 */
export function toTraffic(sections: Section[], live: TdxLive[], roadId: string, dir: Direction, from: string, to: string): Traffic {
  const ss = routeSections(sections, roadId, dir, from, to);
  const byId = new Map(live.filter((l) => l.SectionID).map((l) => [l.SectionID!, l]));

  let estimatedSec = 0;
  let normalSec = 0;
  const segments = ss.map((s): Segment => {
    const l = byId.get(s.id);
    const speed = l?.TravelSpeed && l.TravelSpeed > 0 ? l.TravelSpeed : null;
    const travelSec = l?.TravelTime && l.TravelTime > 0 ? l.TravelTime : null;
    const level = speed === null && l?.CongestionLevel !== "-1" ? "UNKNOWN" : toLevel(l?.CongestionLevel);
    const normal = (s.lengthKm / s.speedLimit) * 3600;
    normalSec += normal;
    estimatedSec += travelSec ?? normal;
    return { fromIc: s.start, toIc: s.end, speedKmh: speed, level, travelMin: travelSec === null ? null : Math.round(travelSec / 60) };
  });

  return {
    freewayName: freewayDisplayName(ss[0].roadName),
    directionName: DIRECTION_NAME[dir],
    from,
    to,
    estimatedMin: Math.round(estimatedSec / 60),
    normalMin: Math.round(normalSec / 60),
    congestedCount: segments.filter((s) => s.level === "JAM").length,
    segments,
  };
}

/** 路段基本資料：meta 與 traffic 共用 7 天快取 */
function sections(env: Env, ctx: ExecutionContext): Promise<Section[]> {
  return cachedData("/internal/freeway/sections", META_TTL_S, ctx, async () =>
    toSections(
      await tdxGet(env, "/v2/Road/Traffic/Section/Freeway", {
        $select: "SectionID,RoadID,RoadName,RoadDirection,RoadSection,SectionLength,SectionMile,SpeedLimit",
      }),
    ),
  ).then((r) => r.data);
}

async function liveFor(env: Env, ids: string[]): Promise<TdxLive[]> {
  const params: Record<string, string> = { $select: "SectionID,TravelTime,TravelSpeed,CongestionLevel" };
  if (ids.length <= LIVE_FILTER_MAX) params.$filter = ids.map((id) => `SectionID eq '${id}'`).join(" or ");
  const raw = await tdxGet<{ LiveTraffics?: TdxLive[] }>(env, "/v2/Road/Traffic/Live/Freeway", params);
  if (!Array.isArray(raw?.LiveTraffics)) throw new ApiError("PARSE", "Invalid freeway live traffic");
  const wanted = new Set(ids);
  return raw.LiveTraffics.filter((l) => l.SectionID && wanted.has(l.SectionID));
}

export async function handleFreewayMeta(env: Env, ctx: ExecutionContext): Promise<Response> {
  return cached("/api/freeway/meta", META_TTL_S, ctx, async () => toMeta(await sections(env, ctx)));
}

/** 驗證 freeway（TDX RoadID，6 碼英數）、dir、from、to（交流道名稱，正規化後比對）；不合法一律 NOT_FOUND */
export function freewayParams(url: URL): { roadId: string; dir: Direction; from: string; to: string } {
  const roadId = url.searchParams.get("freeway") ?? "";
  const dir = url.searchParams.get("dir") as Direction;
  const from = normalizeName(url.searchParams.get("from") ?? "");
  const to = normalizeName(url.searchParams.get("to") ?? "");
  const validName = (s: string) => s.length > 0 && s.length <= 20;
  if (!/^[0-9A-Z]{6}$/.test(roadId) || !isDirection(dir) || !validName(from) || !validName(to) || from === to) {
    throw new ApiError("NOT_FOUND", "Invalid freeway parameters");
  }
  return { roadId, dir, from, to };
}

export async function handleFreewayTraffic(url: URL, env: Env, ctx: ExecutionContext): Promise<Response> {
  const { roadId, dir, from, to } = freewayParams(url);
  const key = `/api/freeway/traffic?freeway=${roadId}&dir=${dir}&from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
  return cached(key, TRAFFIC_TTL_S, ctx, async () => {
    const all = await sections(env, ctx);
    // 先驗證路線再打即時資料：不存在的路線不消耗 TDX 額度
    const ids = routeSections(all, roadId, dir, from, to).map((s) => s.id);
    return toTraffic(all, await liveFor(env, ids), roadId, dir, from, to);
  });
}
