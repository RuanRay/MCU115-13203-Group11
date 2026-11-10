// Cache API 包裝（規格 8.5、GUIDE 3.4-4）。只快取成功回應；錯誤由 produce 拋出、不快取。

import { json } from "./tdx";

/**
 * key 由呼叫端以「驗證過的參數」組成（例：/api/stations?operator=TRA），
 * 不直接用請求網址，避免附加無關參數就繞過快取、每次都打到 TDX。
 */
export async function cached(
  key: string,
  ttlSeconds: number,
  ctx: ExecutionContext,
  produce: () => Promise<unknown>,
): Promise<Response> {
  const cache = await caches.open("api");
  const cacheKey = `https://hometide-api.internal${key}`;

  const hit = await cache.match(cacheKey);
  if (hit) {
    const res = new Response(hit.body, hit);
    res.headers.set("X-Cache", "HIT");
    return res;
  }

  const data = await produce();
  const res = json({ updatedAt: Date.now(), source: "TDX", data }, 200, {
    "Cache-Control": `max-age=${ttlSeconds}`,
    "X-Cache": "MISS",
  });
  ctx.waitUntil(cache.put(cacheKey, res.clone()));
  return res;
}
