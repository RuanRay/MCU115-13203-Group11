import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { bestTrip, citiesOf, taiwanNowMin, toCompare, type CityTransport } from "../src/compare";
import worker from "../src/index";
import type { Trip } from "../src/rail";
import cityTransport from "../src/static/city-transport.json";
import interchangeCity from "../src/static/interchange-city.json";
import { resetTokenForTest, type Env } from "../src/tdx";
import fwLive from "./fixtures/freeway-live.json";
import fwSection from "./fixtures/freeway-section.json";

const trip = (tripId: string, departTime: string, durationMin: number, typeName = "自強", typeCode = "3"): Trip => ({
  tripId, typeCode, typeName, line: null, departTime, arriveTime: "", durationMin,
  arriveEstimated: false, reservedSeat: true, bikeAllowed: false,
});

const ct = cityTransport as Record<string, CityTransport>;
const taoyuan = ct["桃園市"]; // 高鐵桃園站轉乘 25 分
const tainan = ct["臺南市"]; // 高鐵臺南站轉乘 25 分
const now = 14 * 60 + 5; // 14:05

describe("對照表", () => {
  it("交流道 → 縣市 → 代表車站", () => {
    const c = citiesOf("桃園", "永康");
    expect([c.fromCity, c.toCity, c.a.tra?.name, c.b.thsr?.name]).toEqual(["桃園市", "臺南市", "桃園", "臺南"]);
  });
  it("所有交流道對應的縣市都在代表車站表裡", () => {
    for (const city of new Set(Object.values(interchangeCity))) expect(ct[city], city).toBeDefined();
  });
  it("不在表裡：NOT_FOUND", () => {
    expect(() => citiesOf("不存在", "永康")).toThrow(expect.objectContaining({ code: "NOT_FOUND" }));
  });
});

describe("bestTrip（實作者確認：挑最早到達的一班）", () => {
  it("慢車先開但晚到時，選晚幾分鐘發車的快車", () => {
    const t = bestTrip([trip("區間", "14:10", 300), trip("139", "14:12", 188)], now);
    expect(t?.tripId).toBe("139");
  });
  it("已開出的班次不算；都開走了回 null", () => {
    expect(bestTrip([trip("1", "14:00", 60), trip("2", "14:05", 90)], now)?.tripId).toBe("2");
    expect(bestTrip([trip("1", "13:00", 60)], now)).toBeNull();
  });
});

describe("toCompare → E-5", () => {
  it("AC：開車 3:05、高鐵含轉乘 2:40 → 高鐵最快", () => {
    // 高鐵 14:31 發、行駛 84 分：等 26＋84＋轉乘（桃園 25＋臺南 25）＝160（2:40）
    const c = toCompare("桃園市", "臺南市", taoyuan, tainan, {
      THSR: [trip("0657", "14:31", 84, "", "")],
      TRA: [trip("139", "14:12", 188)],
      BUS: [trip("1610-14:30", "14:30", 250, "統聯客運", "1610")],
    }, 185, now);
    const [thsr, tra, bus] = c.options;
    expect(thsr).toEqual({
      operator: "THSR", label: "高鐵・桃園站", departTime: "14:31", arriveTime: "",
      waitMin: 26, transferMin: 50, totalMin: 160, note: "含轉乘",
    });
    expect([tra.label, tra.totalMin, tra.note]).toEqual(["台鐵・自強 139", 195, "7 分後"]);
    expect([bus.label, bus.totalMin, bus.note]).toEqual(["國道客運・統聯 1610", 275, "25 分後"]);
    expect(c.drive).toEqual({ totalMin: 185 });
    expect(c.fastest).toBe("THSR");
  });

  it("沒有班次：「今日已無班次」；縣市沒有該運具：「—」；兩者都不參與最快，開車可能最快", () => {
    const c = toCompare("基隆市", "臺南市", ct["基隆市"], tainan, { THSR: null, TRA: [], BUS: [trip("1", "13:00", 60)] }, 300, now);
    expect(c.options.map((o) => [o.operator, o.label, o.departTime, o.totalMin, o.note])).toEqual([
      ["THSR", "高鐵", null, null, "—"],
      ["TRA", "台鐵", null, null, "今日已無班次"],
      ["BUS", "國道客運", null, null, "今日已無班次"],
    ]);
    expect(c.fastest).toBe("DRIVE");
  });

  it("同分時大眾運輸優先；開車要嚴格較快才算最快", () => {
    const opts = { THSR: [trip("1", "14:20", 145)], TRA: null, BUS: null };
    expect(toCompare("臺北市", "高雄市", ct["臺北市"], ct["高雄市"], opts, 160, now).fastest).toBe("THSR");
    expect(toCompare("臺北市", "高雄市", ct["臺北市"], ct["高雄市"], opts, 159, now).fastest).toBe("DRIVE");
  });

  it("客運業者名稱缺漏時只顯示路線號", () => {
    const c = toCompare("臺北市", "臺南市", ct["臺北市"], tainan, { THSR: null, TRA: null, BUS: [trip("x", "14:30", 250, "", "1610")] }, 400, now);
    expect(c.options[2].label).toBe("國道客運・1610");
  });

  it("高鐵站在市區、不用轉乘時，說明為「N 分後」", () => {
    const c = toCompare("臺北市", "高雄市", ct["臺北市"], ct["高雄市"], { THSR: [trip("1", "14:20", 94)], TRA: null, BUS: null }, 400, now);
    expect([c.options[0].transferMin, c.options[0].note]).toEqual([0, "15 分後"]);
  });
});

describe("臺灣時間", () => {
  it("UTC 06:05 為臺灣 14:05", () => expect(taiwanNowMin(Date.UTC(2026, 10, 22, 6, 5))).toBe(now));
});

describe("端點", () => {
  // fixtures 只有國 1 基隆端～五堵：基隆市 → 新北市
  const env = { TDX_CLIENT_ID: "id", TDX_CLIENT_SECRET: "secret", BUS_INDEX: { get: async () => null } } as unknown as Env;
  const pending: Promise<unknown>[] = [];
  const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException() {} } as unknown as ExecutionContext;
  let fetchMock: ReturnType<typeof vi.fn>;
  const tdxCalls = () => fetchMock.mock.calls.map(([u]) => String(u)).filter((u) => !u.includes("openid-connect"));
  const path = `/api/compare?freeway=000010&dir=S&from=${encodeURIComponent("基隆端")}&to=${encodeURIComponent("五堵")}`;

  async function get(p: string) {
    const res = await worker.fetch(new Request(`https://x.dev${p}`), env, ctx);
    await Promise.all(pending.splice(0));
    return { res, body: (await res.json()) as { data?: any; error?: { code: string } } };
  }

  let traStatus = 200;
  beforeEach(() => {
    resetTokenForTest();
    traStatus = 200;
    fetchMock = vi.fn(async (url: string) => {
      if (url.includes("openid-connect")) return Response.json({ access_token: "T", expires_in: 86_400 });
      if (url.includes("/Section/Freeway")) return Response.json(fwSection);
      if (url.includes("/Live/Freeway")) return Response.json(fwLive);
      if (url.includes("/TRA/DailyTrainTimetable/OD/0900/to/1020/")) {
        return traStatus === 200 ? Response.json({ TrainTimetables: [] }) : new Response("", { status: traStatus });
      }
      return new Response("", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("基隆市 → 新北市：開車取路況預估；基隆沒有高鐵為「—」；台鐵查無班次；客運索引未建立視為「—」；第二次命中快取", async () => {
    const { res, body } = await get(path);
    expect(res.status).toBe(200);
    expect(body.data.fromCity).toBe("基隆市");
    expect(body.data.toCity).toBe("新北市");
    expect(body.data.drive).toEqual({ totalMin: 5 });
    expect(body.data.options.map((o: { note: string }) => o.note)).toEqual(["—", "今日已無班次", "—"]);
    expect(body.data.fastest).toBe("DRIVE");
    expect(tdxCalls().some((u) => u.includes("/TRA/DailyTrainTimetable/OD/0900/to/1020/"))).toBe(true);
    const n = tdxCalls().length;
    expect(n).toBeLessThanOrEqual(10); // 8.6：子請求 ≤ 10
    const again = await get(`${path}&x=1`);
    expect(again.res.headers.get("X-Cache")).toBe("HIT");
    expect(tdxCalls()).toHaveLength(n);
  });

  it("TDX 限流（429）：整個比較回 QUOTA，不快取成「—」", async () => {
    traStatus = 429;
    const { res, body } = await get(path);
    expect([res.status, body.error?.code]).toEqual([429, "QUOTA"]);
  });

  it("TDX 解析錯誤（PARSE）不當成「—」：整個比較失敗", async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.includes("openid-connect")) return Response.json({ access_token: "T", expires_in: 86_400 });
      if (url.includes("/Section/Freeway")) return Response.json(fwSection);
      if (url.includes("/Live/Freeway")) return Response.json(fwLive);
      return new Response("not json", { status: 200 });
    });
    const { body } = await get(path);
    expect(body.error?.code).toBe("PARSE");
  });

  it("參數錯誤：NOT_FOUND", async () => {
    const { res } = await get("/api/compare?freeway=000010&dir=S&from=a&to=b");
    expect(res.status).toBe(404);
  });
});
