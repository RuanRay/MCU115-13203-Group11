// 呼叫 TDX 的唯一入口（GUIDE 3.4-3）：Token 取得與快取、逾時、401 重試、統一錯誤（規格 8.3、8.4）
// 不得在 log、錯誤訊息、回應中輸出金鑰或 Token（GUIDE 3.4-7）

export interface Env {
  TDX_CLIENT_ID: string;
  TDX_CLIENT_SECRET: string;
}

export type ErrorCode = "NETWORK" | "AUTH" | "QUOTA" | "NOT_FOUND" | "PARSE" | "UNKNOWN";

const STATUS: Record<ErrorCode, number> = {
  NOT_FOUND: 404,
  QUOTA: 429,
  AUTH: 502,
  NETWORK: 500,
  PARSE: 500,
  UNKNOWN: 500,
};

export class ApiError extends Error {
  constructor(
    readonly code: ErrorCode,
    message: string,
  ) {
    super(message);
  }
}

export function json(body: unknown, status = 200, headers: HeadersInit = {}): Response {
  return Response.json(body, { status, headers });
}

export function errorResponse(e: unknown): Response {
  const err = e instanceof ApiError ? e : new ApiError("UNKNOWN", "Internal error");
  return json({ error: { code: err.code, message: err.message } }, STATUS[err.code]);
}

const TOKEN_URL = "https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token";
const BASE_URL = "https://tdx.transportdata.tw/api/basic";
const TIMEOUT_MS = 8_000;
const REFRESH_MARGIN_S = 300; // 到期前 5 分鐘更新（8.5）
const TOKEN_CACHE_KEY = "https://hometide-api.internal/tdx-token";

type Token = { value: string; expiresAt: number };

// ponytail: 同時多個請求遇到過期會各自換 Token，流量小可接受；需要時再加單一 in-flight Promise
let memToken: Token | null = null;

/** 測試用：清除記憶體中的 Token */
export function resetTokenForTest(): void {
  memToken = null;
}

async function timedFetch(input: string, init: RequestInit = {}): Promise<Response> {
  try {
    return await fetch(input, { ...init, signal: AbortSignal.timeout(TIMEOUT_MS) });
  } catch {
    throw new ApiError("NETWORK", "TDX unreachable or timed out");
  }
}

async function getToken(env: Env, forceRefresh: boolean): Promise<string> {
  const now = Date.now();
  const cache = await caches.open("tdx");
  if (!forceRefresh) {
    if (memToken && memToken.expiresAt > now) return memToken.value;
    const hit = await cache.match(TOKEN_CACHE_KEY);
    if (hit) {
      const t = (await hit.json()) as Token;
      if (t.expiresAt > now) return (memToken = t).value;
    }
  }

  if (!env.TDX_CLIENT_ID || !env.TDX_CLIENT_SECRET) throw new ApiError("AUTH", "TDX credentials not configured");
  const res = await timedFetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "client_credentials",
      client_id: env.TDX_CLIENT_ID,
      client_secret: env.TDX_CLIENT_SECRET,
    }),
  });
  if (res.status === 429) throw new ApiError("QUOTA", "TDX token endpoint rate limited");
  if (res.status >= 500) throw new ApiError("NETWORK", `TDX token endpoint error (${res.status})`);
  if (!res.ok) throw new ApiError("AUTH", `TDX token request failed (${res.status})`);

  const body = (await res.json().catch(() => ({}))) as { access_token?: string; expires_in?: number };
  if (!body.access_token || !body.expires_in) throw new ApiError("AUTH", "Invalid TDX token response");

  const ttl = Math.max(body.expires_in - REFRESH_MARGIN_S, 60);
  memToken = { value: body.access_token, expiresAt: now + ttl * 1000 };
  await cache.put(TOKEN_CACHE_KEY, new Response(JSON.stringify(memToken), { headers: { "Cache-Control": `max-age=${ttl}` } }));
  return memToken.value;
}

/** GET TDX（基底 /api/basic），自動加 $format=JSON；401 時更新 Token 重送一次 */
export async function tdxGet<T>(env: Env, path: string, params: Record<string, string> = {}): Promise<T> {
  const url = new URL(BASE_URL + path);
  url.searchParams.set("$format", "JSON");
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);

  const call = async (token: string) =>
    timedFetch(url.toString(), { headers: { Authorization: `Bearer ${token}`, "Accept-Encoding": "gzip" } });

  let res = await call(await getToken(env, false));
  if (res.status === 401) res = await call(await getToken(env, true));

  if (res.status === 401 || res.status === 403) throw new ApiError("AUTH", "TDX unauthorized");
  if (res.status === 429) throw new ApiError("QUOTA", "TDX rate limited");
  if (res.status === 404) throw new ApiError("NOT_FOUND", "TDX resource not found");
  // 5xx 視為 TDX 端故障；其他 4xx（例如 OData 語法錯誤 400）是本端請求錯誤
  if (!res.ok) throw new ApiError(res.status >= 500 ? "NETWORK" : "UNKNOWN", `TDX error (${res.status})`);

  try {
    return (await res.json()) as T;
  } catch {
    throw new ApiError("PARSE", "Invalid TDX response");
  }
}
