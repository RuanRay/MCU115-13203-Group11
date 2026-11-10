// 路由（規格 6.5：index.ts 只做路由；回應格式見規格 8.3、GUIDE 附錄 E）

type ErrorCode = "NETWORK" | "AUTH" | "QUOTA" | "NOT_FOUND" | "PARSE" | "UNKNOWN";

const STATUS: Record<ErrorCode, number> = {
  NOT_FOUND: 404,
  QUOTA: 429,
  AUTH: 502,
  NETWORK: 500,
  PARSE: 500,
  UNKNOWN: 500,
};

function json(body: unknown, status = 200, headers: HeadersInit = {}): Response {
  return Response.json(body, { status, headers });
}

function error(code: ErrorCode, message: string): Response {
  return json({ error: { code, message } }, STATUS[code]);
}

export default {
  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/api/health") {
      // GUIDE 附錄 E-0：存活檢查，不快取
      return json(
        { updatedAt: Date.now(), source: "hometide-api", data: { status: "ok" } },
        200,
        { "Cache-Control": "no-store" },
      );
    }

    return error("NOT_FOUND", "Not found");
  },
} satisfies ExportedHandler;
