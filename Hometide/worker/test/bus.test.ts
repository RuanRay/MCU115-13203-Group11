import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { buildIndex, type TdxRoute, type TdxStopOfRoute } from "../scripts/build-index";
import type { Env } from "../src/tdx";
import routes from "./fixtures/bus-route.json";
import stopOfRoutes from "./fixtures/bus-stopofroute.json";

describe("buildIndex", () => {
  const r = buildIndex(routes as TdxRoute[], stopOfRoutes as TdxStopOfRoute[]);

  it("站牌以站名去重，id 即站名、縣市由 LocationCityCode 對應、sortKey 依首次出現", () => {
    const names = r.stations.map((s) => s.id);
    expect(new Set(names).size).toBe(names.length);
    expect(r.stations[0]).toEqual({ id: "臺北轉運站", nameZh: "臺北轉運站", nameEn: "Taipei Bus Station", city: "臺北市", sortKey: 0 });
    expect(r.stations.find((s) => s.id === "中港轉運站")?.city).toBe("臺中市");
    expect(r.stations.map((s) => s.sortKey)).toEqual(names.map((_, i) => i));
  });

  it("索引：每個子路線一筆路線資訊，站名對應 [子路線, 站序, 上下車]，雙向都在", () => {
    expect(r.index.routes["THB161001"]).toEqual({ route: "1610", direction: 0, operators: ["統聯客運"] });
    expect(r.index.stops["臺北轉運站"]).toContainEqual(["THB161001", 1, 1]);
    const subs = new Set(r.index.stops["臺北轉運站"].map(([s]) => s));
    expect(subs).toEqual(new Set(["THB161001", "THB161002"]));
  });

  it("只收國道客運（BusRouteType 13），台→臺", () => {
    const other = buildIndex([{ RouteUID: "THB1610", BusRouteType: 12 }], stopOfRoutes as TdxStopOfRoute[]);
    expect(other.stations).toEqual([]);
    const tai = buildIndex(
      [{ RouteUID: "R", BusRouteType: 13 }],
      [{ RouteUID: "R", RouteName: { Zh_tw: "9" }, SubRouteUID: "S", Stops: [{ StopName: { Zh_tw: "台南轉運站" }, StopSequence: 1 }] }],
    );
    expect(tai.stations[0]).toMatchObject({ id: "臺南轉運站", nameEn: null, city: null });
  });
});

describe("GET /api/stations?operator=BUS", () => {
  const kv = new Map<string, string>();
  const env = {
    TDX_CLIENT_ID: "id",
    TDX_CLIENT_SECRET: "secret",
    BUS_INDEX: { get: async (k: string, type: string) => (kv.has(k) ? (type === "json" ? JSON.parse(kv.get(k)!) : kv.get(k)) : null) },
  } as unknown as Env;
  const pending: Promise<unknown>[] = [];
  const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;
  const fetchMock = vi.fn();

  async function get(path: string) {
    const res = await worker.fetch(new Request(`https://x.dev${path}`), env, ctx);
    await Promise.all(pending.splice(0));
    return res;
  }

  beforeEach(() => {
    kv.clear();
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("索引尚未建立：404 NOT_FOUND，不呼叫 TDX、不快取", async () => {
    const res = await get("/api/stations?operator=BUS");
    expect(res.status).toBe(404);
    expect(((await res.json()) as { error: { code: string } }).error.code).toBe("NOT_FOUND");
    kv.set("bus:stations", JSON.stringify({ builtAt: 1, stations: [] }));
    expect((await get("/api/stations?operator=BUS")).status).toBe(200);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("從 KV 讀站牌，統一格式回傳；第二次命中快取、忽略無關參數", async () => {
    const { stations } = buildIndex(routes as TdxRoute[], stopOfRoutes as TdxStopOfRoute[]);
    kv.set("bus:stations", JSON.stringify({ builtAt: 1, stations }));
    const res = await get("/api/stations?operator=BUS");
    const body = (await res.json()) as { source: string; data: { operator: string; stations: unknown[] } };
    expect(res.status).toBe(200);
    expect(body.source).toBe("TDX");
    expect(body.data).toEqual({ operator: "BUS", stations });
    expect((await get("/api/stations?operator=BUS&x=1")).headers.get("X-Cache")).toBe("HIT");
  });
});
