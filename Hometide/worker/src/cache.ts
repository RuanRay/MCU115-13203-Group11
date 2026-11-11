// Cache API 包裝（規格 8.5、GUIDE 3.4-4）。只快取成功結果；錯誤由 produce 拋出、不快取。

import { json } from "./tdx";

/**
 * key 由呼叫端以「驗證過的參數」組成（例：/api/stations?operator=TRA），
 * 不直接用請求網址，避免附加無關參數就繞過快取、每次都打到 TDX。
 */
export async function cachedData<T>(
  key: string,
  ttlSeconds: number,
  ctx: ExecutionContext,
  produce: () => Promise<T>,
): Promise<{ data: T; updatedAt: number; hit: boolean }> {
  const cache = await caches.open("api");
  const cacheKey = `https://hometide-api.internal${key}`;

  const hit = await cache.match(cacheKey);
  if (hit) {
    const body = (await hit.json().catch(() => null)) as { data?: T; updatedAt?: number } | null;
    if (body && body.data !== undefined && typeof body.updatedAt === "number") {
      return { data: body.data, updatedAt: body.updatedAt, hit: true };
    }
  }

  const data = await produce();
  const updatedAt = Date.now();
  const stored = Response.json({ updatedAt, data }, { headers: { "Cache-Control": `max-age=${ttlSeconds}` } });
  ctx.waitUntil(cache.put(cacheKey, stored));
  return { data, updatedAt, hit: false };
}

/** 統一成功格式 {updatedAt, source, data}（8.3）；X-Cache 供除錯 */
export async function cached(
  key: string,
  ttlSeconds: number,
  ctx: ExecutionContext,
  produce: () => Promise<unknown>,
): Promise<Response> {
  const r = await cachedData(key, ttlSeconds, ctx, produce);
  return json({ updatedAt: r.updatedAt, source: "TDX", data: r.data }, 200, {
    "Cache-Control": `max-age=${ttlSeconds}`,
    "X-Cache": r.hit ? "HIT" : "MISS",
  });
}
