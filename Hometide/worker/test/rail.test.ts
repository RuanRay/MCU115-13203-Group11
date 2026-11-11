import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import {
  durationMin,
  taiwanToday,
  validDate,
  toThsrFares,
  toThsrTrips,
  toTraFares,
  toTraLive,
  toTraTrips,
  traTypeName,
} from "../src/rail";
import { resetTokenForTest, type Env } from "../src/tdx";
import thsrFare from "./fixtures/thsr-odfare.json";
import thsrTimetable from "./fixtures/thsr-timetable.json";
import traFare from "./fixtures/tra-odfare.json";
import traLive from "./fixtures/tra-live.json";
import traTimetable from "./fixtures/tra-timetable.json";

describe("durationMin（8.7 跨午夜）", () => {
  it("一般", () => expect(durationMin("06:27", "10:56")).toBe(269));
  it("跨午夜加 24 小時", () => expect(durationMin("23:10", "01:05")).toBe(115));
  it("同分鐘為 0", () => expect(durationMin("08:00", "08:00")).toBe(0));
  it("時間格式錯誤回 PARSE", () => expect(() => durationMin("8:00", "09:00")).toThrow(expect.objectContaining({ code: "PARSE" })));
});

describe("台鐵時刻 → E-1", () => {
  const trips = toTraTrips(traTimetable, "1000", "4220");

  it("依出發時間排序，欄位轉換正確", () => {
    expect(trips.map((t) => t.tripId)).toEqual(["103", "105", "107", "111", "109"]);
    expect(trips[0]).toEqual({
      tripId: "103", typeCode: "3", typeName: "自強", line: 2,
      departTime: "06:27", arriveTime: "10:56", durationMin: 269,
      arriveEstimated: false, reservedSeat: true, bikeAllowed: false,
    });
  });

  it("車種簡稱與自行車旗標", () => {
    expect(trips.find((t) => t.tripId === "111")?.typeName).toBe("自強3000");
    expect(trips.find((t) => t.tripId === "107")?.typeName).toBe("普悠瑪");
    expect(trips.find((t) => t.tripId === "109")?.bikeAllowed).toBe(true);
  });

  it("跨午夜班次", () => {
    const raw = { TrainTimetables: [{ TrainInfo: { TrainNo: "1", TrainTypeCode: "6", TripLine: 0, BikeFlag: 1 },
      StopTimes: [{ StationID: "1000", DepartureTime: "23:40" }, { StationID: "4220", ArrivalTime: "00:20" }] }] };
    const [t] = toTraTrips(raw, "1000", "4220");
    expect(t.durationMin).toBe(40);
    expect(t.reservedSeat).toBe(false);
  });

  it("未知車種取括號前名稱、非對號", () => {
    expect(traTypeName("99", "觀光(郵輪式列車)")).toBe("觀光");
  });

  it("格式不符回 PARSE", () => expect(() => toTraTrips({}, "1000", "4220")).toThrow(expect.objectContaining({ code: "PARSE" })));
});

describe("高鐵時刻 → E-1", () => {
  it("固定車種、對號、無山海線與自行車", () => {
    const trips = toThsrTrips(thsrTimetable);
    expect(trips.map((t) => t.tripId)).toEqual(["0803", "0203", "0603", "0805", "0609"]);
    expect(trips[1]).toMatchObject({ typeCode: "THSR", typeName: "高鐵", line: null, durationMin: 91, reservedSeat: true, bikeAllowed: false });
  });
});

describe("票價挑選（8.7、FR-RES-02）", () => {
  const fares = Object.fromEntries(toTraFares(traFare).map((f) => [f.typeCode, f]));

  it("同車種取里程最短方向（西部直達 324.7 km，不取繞東部 551.2 km）", () => {
    expect(fares["3"]).toEqual({ typeCode: "3", adultPrice: 891, approximate: false });
    expect(fares["4"].adultPrice).toBe(687);
  });

  it("非對號車種一律取區間車價格", () => {
    expect(fares["6"].adultPrice).toBe(574);
    expect(fares["7"].adultPrice).toBe(574); // 普快自己是 344，但規格取區間
    expect(fares["10"].adultPrice).toBe(574);
  });

  it("對號車種沒有票價時以自強代替並標示約", () => {
    const raw = { ODFares: [
      { TrainType: 3, TravelDistance: 10, Fares: [{ TicketType: 1, FareClass: 1, CabinClass: 1, Price: 100 }] },
      { TrainType: 6, TravelDistance: 10, Fares: [{ TicketType: 1, FareClass: 1, CabinClass: 1, Price: 60 }] },
    ] };
    const f = Object.fromEntries(toTraFares(raw).map((x) => [x.typeCode, x]));
    expect(f["1"]).toEqual({ typeCode: "1", adultPrice: 100, approximate: true });
    expect(f["3"].approximate).toBe(false);
  });

  it("全部取不到時為空", () => expect(toTraFares({ ODFares: [] })).toEqual([]));

  it("高鐵取標準車廂全票", () => {
    expect(toThsrFares(thsrFare)).toEqual([{ typeCode: "THSR", adultPrice: 1350, approximate: false }]);
    expect(toThsrFares([])).toEqual([]);
  });
});

describe("即時誤點（8.7）", () => {
  it("只輸出時刻表內的車次，對應不到視為準點", () => {
    expect(toTraLive(traLive, new Set(["4248", "4235", "999"]))).toEqual([
      { tripId: "4248", delayMin: 2 },
      { tripId: "4235", delayMin: 3 },
    ]);
  });
});

describe("validDate", () => {
  it("合法日期", () => expect(validDate("2026-11-12")).toBe("2026-11-12"));
  it.each(["2026-02-30", "2026-13-01", "2026-11-1", "", null])("不存在或格式錯誤回 NOT_FOUND：%s", (d) =>
    expect(() => validDate(d)).toThrow(expect.objectContaining({ code: "NOT_FOUND" })));
});

describe("taiwanToday", () => {
  it("UTC 16:00 已是臺灣隔天", () => expect(taiwanToday(Date.UTC(2026, 10, 11, 16, 0))).toBe("2026-11-12"));
});

describe("端點", () => {
  const env: Env = { TDX_CLIENT_ID: "id", TDX_CLIENT_SECRET: "secret" };
  const pending: Promise<unknown>[] = [];
  const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;
  let fetchMock: ReturnType<typeof vi.fn>;
  const tdxCalls = () => fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => !u.includes("openid-connect"));

  async function get(path: string) {
    const res = await worker.fetch(new Request(`https://x.dev${path}`), env, ctx);
    await Promise.all(pending.splice(0));
    return { res, body: (await res.json()) as { data?: any; error?: { code: string } } };
  }

  beforeEach(() => {
    resetTokenForTest();
    fetchMock = vi.fn(async (url: string) => {
      if (url.includes("openid-connect")) return Response.json({ access_token: "T", expires_in: 86_400 });
      if (url.includes("/TRA/DailyTrainTimetable/")) return Response.json(traTimetable);
      if (url.includes("/THSR/DailyTimetable/")) return Response.json(thsrTimetable);
      if (url.includes("/TRA/ODFare/")) return Response.json(traFare);
      if (url.includes("/THSR/ODFare/")) return Response.json(thsrFare);
      if (url.includes("/TRA/TrainLiveBoard")) return Response.json({ TrainLiveBoards: [{ TrainNo: "105", DelayTime: 7 }] });
      return new Response("", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("timetable：E-1 格式，第二次命中快取", async () => {
    const { res, body } = await get("/api/timetable?operator=TRA&from=1000&to=4220&date=2026-11-12");
    expect(res.status).toBe(200);
    expect(body.data.operator).toBe("TRA");
    expect(body.data.trips).toHaveLength(5);
    const n = tdxCalls().length;
    const again = await get("/api/timetable?operator=TRA&from=1000&to=4220&date=2026-11-12&x=1");
    expect(again.res.headers.get("X-Cache")).toBe("HIT");
    expect(tdxCalls().length).toBe(n);
  });

  it("fare：E-8 格式", async () => {
    const { body } = await get("/api/fare?operator=THSR&from=1000&to=1060");
    expect(body.data).toEqual({ operator: "THSR", fares: [{ typeCode: "THSR", adultPrice: 1350, approximate: false }] });
  });

  it("live 台鐵：以今天時刻的車次篩選 TrainLiveBoard", async () => {
    const { body } = await get("/api/live?operator=TRA&from=1000&to=4220");
    expect(body.data).toEqual({ items: [{ tripId: "105", delayMin: 7 }] });
    const live = tdxCalls().find((u) => u.includes("TrainLiveBoard"))!;
    expect(new URL(live).searchParams.get("$filter")).toContain("TrainNo eq '103'");
    expect(tdxCalls().some((u) => u.includes(`/${taiwanToday()}`))).toBe(true);
  });

  it("live 台鐵：車次超過 80 班時不用 $filter，改抓整份再篩選", async () => {
    const many = { TrainTimetables: Array.from({ length: 81 }, (_, i) => ({
      TrainInfo: { TrainNo: String(1000 + i), TrainTypeCode: "6" },
      StopTimes: [{ StationID: "1000", DepartureTime: "06:00" }, { StationID: "4220", ArrivalTime: "07:00" }] })) };
    fetchMock.mockImplementation(async (url: string) => {
      if (url.includes("openid-connect")) return Response.json({ access_token: "T", expires_in: 86_400 });
      if (url.includes("/TRA/DailyTrainTimetable/")) return Response.json(many);
      return Response.json({ TrainLiveBoards: [{ TrainNo: "1005", DelayTime: 4 }, { TrainNo: "9", DelayTime: 9 }] });
    });
    const { body } = await get("/api/live?operator=TRA&from=1000&to=4220");
    expect(body.data).toEqual({ items: [{ tripId: "1005", delayMin: 4 }] });
    const live = new URL(tdxCalls().find((u) => u.includes("TrainLiveBoard"))!);
    expect(live.searchParams.has("$filter")).toBe(false);
  });

  it("timetable／fare 以 $select 限縮欄位", async () => {
    await get("/api/timetable?operator=TRA&from=1000&to=4220&date=2026-11-12");
    await get("/api/fare?operator=TRA&from=1000&to=4220");
    const selects = tdxCalls().map((u) => new URL(u).searchParams.get("$select"));
    expect(selects).toEqual(["TrainInfo,StopTimes", "TrainType,TravelDistance,Fares"]);
  });

  it("live 高鐵：空陣列且不呼叫 TDX", async () => {
    const { body } = await get("/api/live?operator=THSR&from=1000&to=1060");
    expect(body.data).toEqual({ items: [] });
    expect(tdxCalls()).toHaveLength(0);
  });

  it.each([
    "/api/timetable?operator=TRA&from=1000&to=4220",
    "/api/timetable?operator=TRA&from=1000&to=4220&date=2026-13-40",
    "/api/timetable?operator=TRA&from=100&to=4220&date=2026-11-12",
    "/api/timetable?operator=TRA&from=1000&to=1000&date=2026-11-12",
    "/api/fare?operator=XYZ&from=1000&to=4220",
    "/api/live?operator=BUS&from=1000&to=4220",
  ])("參數不合法回 404 且不呼叫 TDX：%s", async (path) => {
    const { res, body } = await get(path);
    expect(res.status).toBe(404);
    expect(body.error?.code).toBe("NOT_FOUND");
    expect(tdxCalls()).toHaveLength(0);
  });
});
