import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { buildIndex, type TdxRoute, type TdxStopOfRoute } from "../scripts/build-index";
import { candidateSubs, pickPrice, stageFare, toBusFares, toBusLive, toBusTrips, toStageMap, type BusTrip } from "../src/bus";
import { resetTokenForTest, type Env } from "../src/tdx";
import routes from "./fixtures/bus-route.json";
import stopOfRoutes from "./fixtures/bus-stopofroute.json";
import schedule from "./fixtures/bus-schedule.json";
import routeFare from "./fixtures/bus-routefare.json";

const built = buildIndex(routes as TdxRoute[], stopOfRoutes as TdxStopOfRoute[]);
const index = built.index;
const OP = new Map([["1610", "統聯客運"]]);

describe("buildIndex", () => {
  it("站牌以站名去重，id 即站名、縣市由 LocationCityCode 對應、sortKey 依首次出現", () => {
    const names = built.stations.map((s) => s.id);
    expect(new Set(names).size).toBe(names.length);
    expect(built.stations[0]).toEqual({ id: "臺北轉運站", nameZh: "臺北轉運站", nameEn: "Taipei Bus Station", city: "臺北市", sortKey: 0 });
    expect(built.stations.find((s) => s.id === "中港轉運站")?.city).toBe("臺中市");
    expect(built.stations.map((s) => s.sortKey)).toEqual(names.map((_, i) => i));
  });

  it("索引：子路線含路線資訊與依站序排列的站名，站名對應 [子路線, 站序, 上下車]", () => {
    const r = index.routes["THB161001"];
    expect(r).toMatchObject({ route: "1610", direction: 0, operators: ["統聯客運"] });
    expect(r.stops[0]).toEqual(["臺北轉運站", 1]);
    expect(r.stops.at(-1)).toEqual(["建國客運站", -1]);
    expect(index.stops["臺北轉運站"]).toContainEqual(["THB161001", 1, 1]);
    expect(new Set(index.stops["臺北轉運站"].map(([s]) => s))).toEqual(new Set(["THB161001", "THB161002"]));
  });

  it("只收國道客運（BusRouteType 13），台→臺", () => {
    expect(buildIndex([{ RouteUID: "THB1610", BusRouteType: 12 }], stopOfRoutes as TdxStopOfRoute[]).stations).toEqual([]);
    const tai = buildIndex(
      [{ RouteUID: "R", BusRouteType: 13 }],
      [{ RouteUID: "R", RouteName: { Zh_tw: "9" }, SubRouteUID: "S", Stops: [{ StopName: { Zh_tw: "台南轉運站" }, StopSequence: 1 }] }],
    );
    expect(tai.stations[0]).toMatchObject({ id: "臺南轉運站", nameEn: null, city: null });
  });
});

describe("candidateSubs", () => {
  it("方向正確、起站可上車、迄站可下車", () => {
    expect(candidateSubs(index, "臺北轉運站", "中港轉運站")).toEqual(["THB161001"]);
    expect(candidateSubs(index, "中港轉運站", "臺北轉運站")).toEqual(["THB161002"]);
    // 建國客運站在去程只可下車（-1），不能當起站
    expect(candidateSubs(index, "建國客運站", "臺北轉運站")).toEqual(["THB161002"]);
    // 中壢服務區在去程只可上車（1），不能當迄站
    expect(candidateSubs(index, "臺北轉運站", "中壢服務區")).toEqual([]);
  });
});

describe("toBusTrips", () => {
  // 2026-11-16 為星期一、2026-11-20 為星期五
  it("依站名找起訖、班表抵達時間、業者全名、依出發排序", () => {
    const trips = toBusTrips(schedule, "臺北轉運站", "中港轉運站", "2026-11-16", OP);
    expect(trips.map((t) => [t.tripId, t.departTime, t.arriveTime, t.durationMin, t.arriveEstimated])).toEqual([
      ["1610-00:30", "00:30", "02:40", 130, false],
      ["1610-09:30", "09:30", "12:10", 160, false],
    ]);
    expect(trips[0]).toMatchObject({ typeCode: "1610", typeName: "統聯客運", line: null, reservedSeat: false, bikeAllowed: false, subs: ["THB1610A1"] });
  });

  it("依 ServiceDay 過濾星期；反方向不配對", () => {
    expect(toBusTrips(schedule, "中港轉運站", "臺北轉運站", "2026-11-16", OP).map((t) => t.tripId)).toEqual(["1610-10:35"]);
    expect(toBusTrips(schedule, "中港轉運站", "臺北轉運站", "2026-11-20", OP).map((t) => t.tripId)).toEqual(["1610-10:35", "1610-19:35"]);
  });

  const mk = (sub: string, times: [string, string | undefined, string][]) => ({
    RouteName: { Zh_tw: "9" },
    SubRouteUID: sub,
    Timetables: times.map(([dep, arr, toName]) => ({
      ServiceDay: { Monday: 1 },
      StopTimes: [
        { StopName: { Zh_tw: "台北" }, DepartureTime: dep },
        { StopName: { Zh_tw: toName }, ArrivalTime: arr },
      ],
    })),
  });

  it("跨午夜、缺抵達時間以同路線平均推算並標 arriveEstimated、同路線同時間只一筆", () => {
    const raw = [
      mk("A", [["23:30", "01:30", "臺南"], ["10:00", "12:00", "臺南"]]),
      mk("B", [["10:00", undefined, "臺南"], ["14:00", undefined, "臺南"]]),
    ];
    const trips = toBusTrips(raw, "臺北", "臺南", "2026-11-16", new Map());
    expect(trips.map((t) => [t.tripId, t.arriveTime, t.durationMin, t.arriveEstimated, t.subs])).toEqual([
      ["9-10:00", "12:00", 120, false, ["A", "B"]],
      ["9-14:00", "16:00", 120, true, ["B"]],
      ["9-23:30", "01:30", 120, false, ["A"]],
    ]);
  });

  it("格式不符回 PARSE", () => {
    expect(() => toBusTrips({}, "a", "b", "2026-11-16", OP)).toThrow();
  });
});

// 中港轉運站→建國客運站：全票取最低優惠 280（約）；孩童、敬老、愛心退回半票 146；沒有學生票
const BUS_FARES = [
  { typeCode: "1610", fareClass: "ADULT", price: 280, approx: true },
  { typeCode: "1610", fareClass: "CHILD", price: 146, approx: false },
  { typeCode: "1610", fareClass: "SENIOR", price: 146, approx: false },
  { typeCode: "1610", fareClass: "DISABLED", price: 146, approx: false },
];

describe("票價", () => {
  const stages = toStageMap(routeFare).get("1610")!;
  const stops = index.routes["THB161001"].stops;

  const pickAdultPrice = (f: Parameters<typeof pickPrice>[0]) => pickPrice(f, "ADULT");

  it("pickPrice 全票：只收「全票_」；有優惠取最低優惠並標示；沒有優惠取原價／不分時段最低", () => {
    expect(pickAdultPrice([
      { FareName: "全票_優惠_四排座", Price: 280 }, { FareName: "全票_原價_四排座", Price: 290 },
      { FareName: "全票_優惠_三排座", Price: 300 }, { FareName: "全票_原價_三排座", Price: 364 },
      { FareName: "半票_不分時段_四排座", Price: 146 },
    ])).toEqual({ price: 280, discounted: true });
    expect(pickAdultPrice([
      { FareName: "全票_不分時段_三排座", Price: 400 }, { FareName: "全票_不分時段_四排座", Price: 350 },
      { FareName: "電子票證全票_不分時段_四排座", Price: 300 }, { FareName: "來回票-去_不分時段_四排座", Price: 200 },
    ])).toEqual({ price: 350, discounted: false });
    expect(pickAdultPrice([{ FareName: "半票_不分時段_四排座", Price: 146 }])).toBeNull();
  });

  it("pickPrice 其他票種：孩童／敬老／愛心沒有專屬名稱時退回半票；學生只收「學生票」；查無回 null", () => {
    const f = [
      { FareName: "全票_不分時段_四排座", Price: 290 }, { FareName: "半票_不分時段_四排座", Price: 146 },
      { FareName: "電子票證半票_不分時段_四排座", Price: 100 },
    ];
    expect(pickPrice(f, "CHILD")).toEqual({ price: 146, discounted: false });
    expect(pickPrice(f, "SENIOR")).toEqual({ price: 146, discounted: false });
    expect(pickPrice(f, "STUDENT")).toBeNull();
    expect(pickPrice([...f, { FareName: "學生票_不分時段", Price: 200 }], "STUDENT")?.price).toBe(200);
    expect(pickPrice([...f, { FareName: "敬老票_不分時段", Price: 120 }], "SENIOR")?.price).toBe(120);
  });

  it("stageFare：精確對中、迄站往後吸附、起站前沒有分段起點時對不到", () => {
    expect(stageFare(stops, stages, "中港轉運站", "建國客運站")?.exact).toBe(true);
    const snapped = stageFare(stops, stages, "中港轉運站", "楠梓站");
    expect(snapped?.exact).toBe(false);
    expect(pickAdultPrice(snapped!.fares)?.price).toBe(270); // 吸附到「捷運苓雅運動園區站」
    expect(stageFare(stops, stages, "經國轉運站", "建國客運站")).toBeNull();
  });

  it("toBusFares：取優惠或吸附時 approximate=true；對不到的路線不輸出", () => {
    expect(toBusFares(index, ["THB161001"], routeFare, "中港轉運站", "建國客運站")).toEqual(BUS_FARES);
    const noPromo = [{ RouteName: "1610", StageFares: [{ OriginStage: { StopName: "中港轉運站" }, DestinationStage: { StopName: "建國客運站" }, Fares: [{ FareName: "全票_不分時段_四排座", Price: 290 }] }] }];
    expect(toBusFares(index, ["THB161001"], noPromo, "中港轉運站", "建國客運站")).toEqual([
      { typeCode: "1610", fareClass: "ADULT", price: 290, approx: false },
    ]);
    expect(toBusFares(index, ["THB161001"], routeFare, "經國轉運站", "建國客運站")).toEqual([]);
  });
});

describe("toBusLive（子路線＋時間最近）", () => {
  const trip = (tripId: string, departTime: string, subs: string[]) => ({ tripId, departTime, subs }) as BusTrip;
  const trips = [trip("9-10:00", "10:00", ["A"]), trip("9-10:30", "10:30", ["A"]), trip("9-10:05", "10:05", ["B"])];
  const eta = (sub: string, sec: number, status = 0, stop = "台北") => ({ SubRouteUID: sub, EstimateTime: sec, StopStatus: status, StopName: { Zh_tw: stop } });
  const now = 9 * 60 + 50; // 09:50

  it("配到同子路線中表定最接近者（誤點的車不會被配到下一班）", () => {
    // A 的車 25 分後到（10:15）：10:00 與 10:30 相差都是 15 分，取先找到的 10:00；B 的車配 B 的班
    expect(toBusLive([eta("A", 25 * 60), eta("B", 10 * 60)], trips, "臺北", now)).toEqual([
      { tripId: "9-10:00", etaMin: 25 },
      { tripId: "9-10:05", etaMin: 10 },
    ]);
    // 10:00 的車誤點，35 分後才到（10:25）→ 仍配 10:30（較近）；20 分後到（10:10）→ 配 10:00
    expect(toBusLive([eta("A", 20 * 60)], trips, "臺北", now)).toEqual([{ tripId: "9-10:00", etaMin: 20 }]);
  });

  it("相差超過 60 分、非起站、StopStatus≠0、子路線不符都不指派；秒數無條件進位", () => {
    expect(toBusLive([eta("A", 3 * 3600)], trips, "臺北", now)).toEqual([]);
    expect(toBusLive([eta("A", 600, 0, "臺南")], trips, "臺北", now)).toEqual([]);
    expect(toBusLive([eta("A", 600, 1)], trips, "臺北", now)).toEqual([]);
    expect(toBusLive([eta("Z", 600)], trips, "臺北", now)).toEqual([]);
    expect(toBusLive([eta("B", 61)], trips, "臺北", now)).toEqual([{ tripId: "9-10:05", etaMin: 2 }]);
  });

  it("跨午夜：23:50 時 20 分後到配 00:10 的班", () => {
    expect(toBusLive([eta("A", 1200)], [trip("9-00:10", "00:10", ["A"])], "臺北", 23 * 60 + 50)).toEqual([{ tripId: "9-00:10", etaMin: 20 }]);
  });
});

describe("端點（operator=BUS）", () => {
  const kv = new Map<string, string>();
  const env = {
    TDX_CLIENT_ID: "id",
    TDX_CLIENT_SECRET: "secret",
    BUS_INDEX: { get: async (k: string) => (kv.has(k) ? JSON.parse(kv.get(k)!) : null) },
  } as unknown as Env;
  const pending: Promise<unknown>[] = [];
  const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;
  const fetchMock = vi.fn();

  async function get(path: string) {
    const res = await worker.fetch(new Request(`https://x.dev${path}`), env, ctx);
    await Promise.all(pending.splice(0));
    return { res, body: (await res.json()) as { data: Record<string, unknown>; error?: { code: string } } };
  }
  const tdxCalls = () => fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => !u.includes("openid-connect"));

  beforeEach(() => {
    kv.clear();
    resetTokenForTest();
    vi.stubGlobal("fetch", fetchMock);
    fetchMock.mockImplementation(async (url: string) => {
      if (url.includes("openid-connect")) return Response.json({ access_token: "T", expires_in: 86_400 });
      if (url.includes("/Schedule/")) return Response.json(schedule);
      if (url.includes("/RouteFare/")) return Response.json(routeFare);
      return Response.json([]);
    });
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("stations：索引尚未建立回 404 且不快取；建立後 200、第二次命中快取、忽略無關參數", async () => {
    expect((await get("/api/stations?operator=BUS")).res.status).toBe(404);
    kv.set("bus:stations", JSON.stringify({ builtAt: 1, stations: built.stations }));
    const { res, body } = await get("/api/stations?operator=BUS");
    expect(res.status).toBe(200);
    expect(body.data).toEqual({ operator: "BUS", stations: built.stations });
    expect((await get("/api/stations?operator=BUS&x=1")).res.headers.get("X-Cache")).toBe("HIT");
    expect(tdxCalls()).toEqual([]);
  });

  describe("有索引", () => {
    beforeEach(() => kv.set("bus:index", JSON.stringify(index)));
    const enc = encodeURIComponent;
    const T = `from=${enc("臺北轉運站")}&to=${enc("中港轉運站")}`;

    it.each([
      `/api/timetable?operator=BUS&${T}&date=2026-02-30`,
      `/api/timetable?operator=BUS&from=${enc("臺北轉運站")}&to=${enc("臺北轉運站")}&date=2026-11-16`,
      `/api/fare?operator=BUS&from=${enc("不存在站")}&to=${enc("中港轉運站")}`,
      `/api/live?operator=BUS&from=${enc("臺北轉運站")}`,
    ])("參數不合法回 404 且不呼叫 TDX：%s", async (path) => {
      const { res, body } = await get(path);
      expect(res.status).toBe(404);
      expect(body.error?.code).toBe("NOT_FOUND");
      expect(tdxCalls()).toEqual([]);
    });

    it("timetable：一次 Schedule 查詢（$filter 路線名），輸出不含內部欄位", async () => {
      const { res, body } = await get(`/api/timetable?operator=BUS&${T}&date=2026-11-16`);
      expect(res.status).toBe(200);
      const trips = body.data.trips as Record<string, unknown>[];
      expect(trips.map((t) => t.tripId)).toEqual(["1610-00:30", "1610-09:30"]);
      expect(trips[0]).not.toHaveProperty("subs");
      expect(tdxCalls()).toHaveLength(1);
      expect(decodeURIComponent(tdxCalls()[0]).replaceAll("+", " ")).toContain("RouteName/Zh_tw eq '1610'");
    });

    it("fare：省略 route 回傳全部路線；帶 route 只回該路線、共用同一次 TDX 查詢；沒有路線回空陣列", async () => {
      const F = `from=${enc("中港轉運站")}&to=${enc("建國客運站")}`;
      expect((await get(`/api/fare?operator=BUS&${F}`)).body.data).toEqual({
        operator: "BUS",
        fares: BUS_FARES,
      });
      expect((await get(`/api/fare?operator=BUS&${F}&route=1610`)).body.data.fares).toHaveLength(BUS_FARES.length);
      expect((await get(`/api/fare?operator=BUS&${F}&route=9999`)).body.data.fares).toEqual([]);
      expect(tdxCalls()).toHaveLength(1);
      const none = await get(`/api/fare?operator=BUS&from=${enc("臺北轉運站")}&to=${enc("中壢服務區")}`);
      expect(none.body.data).toEqual({ operator: "BUS", fares: [] });
    });

    it("附加無關參數仍命中同一快取；帶不存在的 route 回空 fares 且不新增 TDX 查詢；台→臺", async () => {
      await get(`/api/timetable?operator=BUS&${T}&date=2026-11-16`);
      expect((await get(`/api/timetable?operator=BUS&${T}&date=2026-11-16&x=1`)).res.headers.get("X-Cache")).toBe("HIT");
      const tai = await get(`/api/timetable?operator=BUS&from=${enc("台北轉運站")}&to=${enc("中港轉運站")}&date=2026-11-16`);
      expect(tai.res.headers.get("X-Cache")).toBe("HIT");
      const F = `from=${enc("中港轉運站")}&to=${enc("建國客運站")}`;
      await get(`/api/fare?operator=BUS&${F}`);
      const odd = await get(`/api/fare?operator=BUS&${F}&route=${enc("x".repeat(30))}`);
      expect(odd.body.data.fares).toEqual([]);
      expect(odd.res.headers.get("X-Cache")).toBe("HIT");
      expect(tdxCalls()).toHaveLength(2); // Schedule、RouteFare 各一次
    });

    it("live：共用今天時刻快取，再查 1 次預估到站", async () => {
      const { res, body } = await get(`/api/live?operator=BUS&${T}`);
      expect(res.status).toBe(200);
      expect(body.data).toEqual({ items: [] });
      expect(tdxCalls().filter((u) => u.includes("EstimatedTimeOfArrival"))).toHaveLength(1);
    });
  });
});
