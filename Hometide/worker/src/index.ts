// 路由（規格 6.5：index.ts 只做路由；回應格式見規格 8.3、GUIDE 附錄 E）

import { handleStations } from "./rail";
import { ApiError, errorResponse, json, type Env } from "./tdx";

async function route(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  const url = new URL(request.url);
  if (request.method !== "GET") throw new ApiError("NOT_FOUND", "Not found");

  switch (url.pathname) {
    case "/api/health":
      // 附錄 E-0：存活檢查，不快取
      return json({ updatedAt: Date.now(), source: "hometide-api", data: { status: "ok" } }, 200, {
        "Cache-Control": "no-store",
      });

    case "/api/stations":
      return handleStations(url, env, ctx);
  }
  throw new ApiError("NOT_FOUND", "Not found");
}

export default {
  async fetch(request, env, ctx): Promise<Response> {
    const start = Date.now();
    let res: Response;
    let code = "OK";
    try {
      res = await route(request, env, ctx);
    } catch (e) {
      code = e instanceof ApiError ? e.code : "UNKNOWN";
      res = errorResponse(e);
    }
    // GUIDE 3.4-7：只記錄端點、參數、耗時與錯誤代碼
    const url = new URL(request.url);
    console.log(JSON.stringify({ path: url.pathname, query: url.search, ms: Date.now() - start, status: res.status, code }));
    return res;
  },
} satisfies ExportedHandler<Env>;
