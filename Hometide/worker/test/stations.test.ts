import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { cityFromAddress, toStations } from "../src/rail";
import { resetTokenForTest, type Env } from "../src/tdx";
import tra from "./fixtures/tra-stations.json";
import thsr from "./fixtures/thsr-stations.json";

const env: Env = { TDX_CLIENT_ID: "id", TDX_CLIENT_SECRET: "secret" };
const pending: Promise<unknown>[] = [];
const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;

async function get(path: string) {
  const res = await worker.fetch(new Request(`https://x.dev${path}`), env, ctx);
  await Promise.all(pending.splice(0));
  return res;
}

describe("toStations", () => {
  it("TRA v3（包在 Stations 裡）：縣市取自地址、台→臺、依回傳順序給 sortKey", () => {
    expect(toStations(tra)).toEqual([
      { id: "0900", nameZh: "基隆", nameEn: "Keelung", city: "基隆市", sortKey: 0 },
      { id: "4220", nameZh: "臺南", nameEn: "Tainan", city: "臺南市", sortKey: 1 },
    ]);
  });

  it("cityFromAddress", () => {
    expect(cityFromAddress("203001基隆市中山區中山一路 16 之 1 號")).toBe("基隆市");
    expect(cityFromAddress("50047彰化縣彰化市三民路 1 號")).toBe("彰化縣");
    expect(cityFromAddress("新北市板橋區")).toBe("新北市");
    expect(cityFromAddress("")).toBeNull();
  });

  it("THSR v2（陣列）：缺縣市時為 null", () => {
    expect(toStations(thsr).map((s) => [s.nameZh, s.city])).toEqual([
      ["臺北", "臺北市"],
      ["臺南", null],
    ]);
  });

  it("格式不符時回 PARSE", () => {
    expect(() => toStations({ foo: 1 })).toThrow(expect.objectContaining({ code: "PARSE" }));
    expect(() => toStations([{ StationName: { Zh_tw: "臺北" } }])).toThrow(expect.objectContaining({ code: "PARSE" }));
  });
});

describe("GET /api/stations", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    resetTokenForTest();
    fetchMock = vi.fn(async (url: string) =>
      url.includes("/openid-connect/token")
        ? Response.json({ access_token: "T", expires_in: 86_400 })
        : Response.json(url.includes("/TRA/") ? tra : thsr),
    );
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("回傳統一格式，第二次命中快取不再呼叫 TDX", async () => {
    const first = await get("/api/stations?operator=TRA");
    expect(first.status).toBe(200);
    expect(first.headers.get("X-Cache")).toBe("MISS");
    const body = (await first.json()) as { source: string; updatedAt: number; data: { operator: string; stations: unknown[] } };
    expect(body.source).toBe("TDX");
    expect(typeof body.updatedAt).toBe("number");
    expect(body.data.operator).toBe("TRA");
    expect(body.data.stations).toHaveLength(2);

    const calls = fetchMock.mock.calls.length;
    const second = await get("/api/stations?operator=TRA&noise=1");
    expect(second.headers.get("X-Cache")).toBe("HIT");
    expect(fetchMock.mock.calls.length).toBe(calls);
  });

  it("TRA 與 THSR 分開快取", async () => {
    await get("/api/stations?operator=TRA");
    const res = await get("/api/stations?operator=THSR");
    expect(res.headers.get("X-Cache")).toBe("MISS");
  });

  it.each(["BUS", "XYZ", ""])("operator=%s 回 404 NOT_FOUND，不呼叫 TDX", async (op) => {
    const res = await get(`/api/stations?operator=${op}`);
    expect(res.status).toBe(404);
    expect(((await res.json()) as { error: { code: string } }).error.code).toBe("NOT_FOUND");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("TDX 錯誤不快取", async () => {
    fetchMock.mockImplementation(async (url: string) =>
      url.includes("/openid-connect/token") ? Response.json({ access_token: "T", expires_in: 86_400 }) : new Response("", { status: 429 }),
    );
    const res = await get("/api/stations?operator=TRA");
    expect(res.status).toBe(429);
    expect(((await res.json()) as { error: { code: string } }).error.code).toBe("QUOTA");

    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url.includes("/openid-connect/token") ? Response.json({ access_token: "T", expires_in: 86_400 }) : Response.json(tra),
    ));
    expect((await get("/api/stations?operator=TRA")).status).toBe(200);
  });
});
