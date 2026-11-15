// 國道客運（規格 6.5）：站牌（8.3；附錄 E-7）。資料來自 scripts/build-index.ts 每日寫入的 KV（8.6），請求中不掃描路線

import { cached } from "./cache";
import { ApiError, type Env } from "./tdx";

const STATIONS_TTL_S = 7 * 86_400; // 8.5
const STATIONS_KEY = "bus:stations"; // 與 scripts/build-index.ts 一致

/** GET /api/stations?operator=BUS（E-7）；索引尚未建立時回 NOT_FOUND */
export async function handleBusStations(env: Env, ctx: ExecutionContext): Promise<Response> {
  return cached("/api/stations?operator=BUS", STATIONS_TTL_S, ctx, async () => {
    const v = await env.BUS_INDEX.get<{ stations?: unknown[] }>(STATIONS_KEY, "json");
    if (!Array.isArray(v?.stations)) throw new ApiError("NOT_FOUND", "Bus index not built");
    return { operator: "BUS", stations: v.stations };
  });
}
