import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, resetTokenForTest, tdxGet, type Env } from "../src/tdx";

const env: Env = { TDX_CLIENT_ID: "test-id", TDX_CLIENT_SECRET: "test-secret" };

const tokenRes = (token: string) => Response.json({ access_token: token, expires_in: 86_400 });
const isToken = (url: string) => url.includes("/openid-connect/token");

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  resetTokenForTest();
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const apiCalls = () => fetchMock.mock.calls.filter(([url]) => !isToken(url as string));
const tokenCalls = () => fetchMock.mock.calls.filter(([url]) => isToken(url as string));
const authOf = (call: unknown[]) => new Headers((call[1] as RequestInit).headers).get("Authorization");

async function codeOf(p: Promise<unknown>) {
  try {
    await p;
  } catch (e) {
    return e instanceof ApiError ? e.code : "NOT_API_ERROR";
  }
  return "NO_ERROR";
}

describe("tdxGet", () => {
  it("401 時更新 Token 並重送一次", async () => {
    const tokens = ["T1", "T2"];
    fetchMock.mockImplementation(async (url: string, init: RequestInit) => {
      if (isToken(url)) return tokenRes(tokens.shift()!);
      const auth = new Headers(init.headers).get("Authorization");
      return auth === "Bearer T1" ? new Response("", { status: 401 }) : Response.json([{ ok: 1 }]);
    });

    await expect(tdxGet(env, "/v3/Rail/TRA/Station")).resolves.toEqual([{ ok: 1 }]);
    expect(tokenCalls()).toHaveLength(2);
    expect(apiCalls().map(authOf)).toEqual(["Bearer T1", "Bearer T2"]);
  });

  it("重送後仍 401 回 AUTH，不再重試", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : new Response("", { status: 401 })));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("AUTH");
    expect(apiCalls()).toHaveLength(2);
  });

  it("Token 有效期內重複使用", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : Response.json({})));
    await tdxGet(env, "/a");
    await tdxGet(env, "/b");
    expect(tokenCalls()).toHaveLength(1);
  });

  it("記憶體清空時從 Cache API 取回 Token", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : Response.json({})));
    await tdxGet(env, "/a");
    resetTokenForTest();
    await tdxGet(env, "/b");
    expect(tokenCalls()).toHaveLength(1);
  });

  it("加上 $format=JSON 與查詢參數", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : Response.json({})));
    await tdxGet(env, "/v2/Rail/THSR/Station", { $select: "StationID" });
    const url = new URL(apiCalls()[0][0] as string);
    expect(url.pathname).toBe("/api/basic/v2/Rail/THSR/Station");
    expect(url.searchParams.get("$format")).toBe("JSON");
    expect(url.searchParams.get("$select")).toBe("StationID");
  });

  it.each([
    [429, "QUOTA"],
    [404, "NOT_FOUND"],
    [500, "NETWORK"],
    [400, "UNKNOWN"],
  ])("TDX 回 %i 時為 %s", async (status, code) => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : new Response("", { status })));
    expect(await codeOf(tdxGet(env, "/x"))).toBe(code);
  });

  it("逾時或連線失敗回 NETWORK", async () => {
    fetchMock.mockRejectedValue(new DOMException("timed out", "TimeoutError"));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("NETWORK");
  });

  it("回應不是 JSON 時回 PARSE", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : new Response("<html>")));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("PARSE");
  });

  it("Token 端點拒絕時回 AUTH，429 時回 QUOTA", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 401 }));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("AUTH");
    fetchMock.mockResolvedValueOnce(new Response("", { status: 429 }));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("QUOTA");
  });

  it("沒有設定金鑰時回 AUTH，不呼叫 TDX", async () => {
    expect(await codeOf(tdxGet({ TDX_CLIENT_ID: "", TDX_CLIENT_SECRET: "" }, "/x"))).toBe("AUTH");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("錯誤訊息不含金鑰或 Token", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("SECRET_TOKEN") : new Response("", { status: 401 })));
    const e = await tdxGet(env, "/x").catch((err: unknown) => err);
    expect(e).toBeInstanceOf(ApiError);
    for (const s of ["SECRET_TOKEN", env.TDX_CLIENT_ID, env.TDX_CLIENT_SECRET]) expect((e as Error).message).not.toContain(s);
  });

  it("Token 到期前 5 分鐘內重新換取（8.4）", async () => {
    vi.useFakeTimers();
    try {
      // expires_in 3600 → 有效 3300 秒
      fetchMock.mockImplementation(async (url: string) =>
        isToken(url) ? Response.json({ access_token: "T", expires_in: 3600 }) : Response.json({}),
      );
      await tdxGet(env, "/a");
      vi.advanceTimersByTime(3_299_000);
      await tdxGet(env, "/b");
      expect(tokenCalls()).toHaveLength(1);
      vi.advanceTimersByTime(2_000); // 距到期剩不到 5 分鐘
      await tdxGet(env, "/c");
      expect(tokenCalls()).toHaveLength(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("TDX 回 403 時為 AUTH", async () => {
    fetchMock.mockImplementation(async (url: string) => (isToken(url) ? tokenRes("T") : new Response("", { status: 403 })));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("AUTH");
  });

  it("Token 端點 5xx 時為 NETWORK", async () => {
    fetchMock.mockResolvedValueOnce(new Response("", { status: 503 }));
    expect(await codeOf(tdxGet(env, "/x"))).toBe("NETWORK");
  });
});
