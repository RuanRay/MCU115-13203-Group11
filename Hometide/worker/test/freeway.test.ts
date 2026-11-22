import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { freewayDisplayName, parseKm, toLevel, toMeta, toSections, toTraffic, type TdxLive } from "../src/freeway";
import { resetTokenForTest, type Env } from "../src/tdx";
import fwLive from "./fixtures/freeway-live.json";
import fwSection from "./fixtures/freeway-section.json";

// fixtures：國道1號 基隆端～五堵（南下、北上各 4 段）、國道2甲、高港高架（非國道，應排除）；順序已打亂
const sections = toSections(fwSection);
const live = fwLive.LiveTraffics as TdxLive[];

describe("小工具", () => {
  it("里程「12K+300」", () => {
    expect(parseKm("12K+300")).toBe(12.3);
    expect(parseKm("0K+000")).toBe(0);
    expect(parseKm("12.3")).toBeNull();
  });
  it("國道名稱加空格（E-4）", () => {
    expect(freewayDisplayName("國道1號")).toBe("國道 1 號");
    expect(freewayDisplayName("國道2甲")).toBe("國道 2 甲");
    expect(freewayDisplayName("國道1號汐止五股高架道路")).toBe("國道 1 號汐止五股高架道路");
  });
  it("壅塞等級：1 順暢、2～3 車多、4～5 與 -1（封閉）壅塞、其他無資料", () => {
    expect(["1", "2", "3", "4", "5", "-1", "0", undefined].map(toLevel)).toEqual([
      "SMOOTH", "BUSY", "BUSY", "JAM", "JAM", "JAM", "UNKNOWN", "UNKNOWN",
    ]);
  });
});

describe("meta → E-9", () => {
  const meta = toMeta(sections);

  it("只列國道，依 RoadID 排序，名稱加空格", () => {
    expect(meta.freeways.map((f) => [f.id, f.name])).toEqual([
      ["000010", "國道 1 號"],
      ["000021", "國道 2 甲"],
    ]);
  });

  it("交流道依行車方向排序（南下遞增、北上遞減），含里程", () => {
    const [s, n] = meta.freeways[0].directions;
    expect([s.dir, s.name, n.dir, n.name]).toEqual(["S", "南下", "N", "北上"]);
    expect(s.interchanges).toEqual([
      { name: "基隆端", km: 0 }, { name: "基隆", km: 1.1 }, { name: "八堵", km: 2.6 },
      { name: "大華系統", km: 5 }, { name: "五堵", km: 6.8 },
    ]);
    expect(n.interchanges.map((i) => i.name)).toEqual(["五堵", "大華系統", "八堵", "基隆", "基隆端"]);
  });

  it("東西向", () => {
    expect(meta.freeways[1].directions.map((d) => [d.name, d.interchanges.map((i) => i.name)])).toEqual([
      ["東向", ["圳頭", "大園"]],
      ["西向", ["大園", "圳頭"]],
    ]);
  });

  it("交流道名稱「台」→「臺」（8.7）", () => {
    const raw = { Sections: [{ ...fwSection.Sections.find((s) => s.SectionID === "0001"), RoadSection: { Start: "台北端", End: "台南" } }] };
    expect(toMeta(toSections(raw)).freeways[0].directions[0].interchanges.map((i) => i.name)).toEqual(["臺北端", "臺南"]);
  });

  it("格式錯誤回 PARSE", () => {
    expect(() => toSections({})).toThrow(expect.objectContaining({ code: "PARSE" }));
  });
});

describe("traffic → E-4", () => {
  it("基隆端 → 五堵（南下）：預估＝即時旅行時間總和、平常＝長度／速限", () => {
    const t = toTraffic(sections, live, "000010", "S", "基隆端", "五堵");
    // 即時 59＋61＋95＋77＝292 秒 ≈ 5 分；平常 6.8 公里／100 公里每時 ≈ 4 分
    expect(t).toMatchObject({ freewayName: "國道 1 號", directionName: "南下", from: "基隆端", to: "五堵", estimatedMin: 5, normalMin: 4, congestedCount: 0 });
    expect(t.segments[0]).toEqual({ fromIc: "基隆端", toIc: "基隆", speedKmh: 66, level: "BUSY", travelMin: 1 });
    expect(t.segments.map((s) => s.toIc)).toEqual(["基隆", "八堵", "大華系統", "五堵"]);
  });

  it("只取起訖之間的路段；北上依北上順序", () => {
    const t = toTraffic(sections, live, "000010", "N", "大華系統", "基隆");
    expect(t.segments.map((s) => [s.fromIc, s.toIc])).toEqual([["大華系統", "八堵"], ["八堵", "基隆"]]);
  });

  it("無即時資料：UNKNOWN、時速與旅行時間為 null，預估改用平常時間；封閉算壅塞", () => {
    const patched: TdxLive[] = [
      { SectionID: "0001", TravelTime: 31, TravelSpeed: 0, CongestionLevel: "0" },
      { SectionID: "0003", TravelTime: 0, TravelSpeed: 0, CongestionLevel: "-1" },
    ];
    const t = toTraffic(sections, patched, "000010", "S", "基隆端", "八堵");
    expect(t.segments.map((s) => [s.level, s.speedKmh])).toEqual([["UNKNOWN", null], ["JAM", null]]);
    expect(t.segments[1].travelMin).toBeNull();
    // 0001 有旅行時間 31 秒；0003 沒有 → 平常 1.5 公里 54 秒；共 85 秒 ≈ 1 分
    expect(t.estimatedMin).toBe(1);
    expect(t.congestedCount).toBe(1);
  });

  it("中間路段因資料不齊被排除（接不起來）：NOT_FOUND", () => {
    const gap = toSections({ Sections: fwSection.Sections.map((x) => (x.SectionID === "0003" ? { ...x, SpeedLimit: 0 } : x)) });
    expect(() => toTraffic(gap, live, "000010", "S", "基隆端", "五堵")).toThrow(expect.objectContaining({ code: "NOT_FOUND" }));
  });

  it("路線不存在或方向相反：NOT_FOUND", () => {
    expect(() => toTraffic(sections, live, "000010", "S", "五堵", "基隆")).toThrow(expect.objectContaining({ code: "NOT_FOUND" }));
    expect(() => toTraffic(sections, live, "000030", "S", "基隆端", "五堵")).toThrow(expect.objectContaining({ code: "NOT_FOUND" }));
  });
});

describe("端點", () => {
  const env = { TDX_CLIENT_ID: "id", TDX_CLIENT_SECRET: "secret" } as Env;
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
      if (url.includes("/Section/Freeway")) return Response.json(fwSection);
      if (url.includes("/Live/Freeway")) return Response.json(fwLive);
      return new Response("", { status: 404 });
    });
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("meta：E-9 格式，用 $select", async () => {
    const { res, body } = await get("/api/freeway/meta");
    expect(res.status).toBe(200);
    expect(body.data.freeways).toHaveLength(2);
    expect(new URL(tdxCalls()[0]).searchParams.get("$select")).toContain("SectionMile");
  });

  it("traffic：以 $filter 只取需要的路段；路段資料與 meta 共用快取；第二次命中快取", async () => {
    await get("/api/freeway/meta");
    const { body } = await get(`/api/freeway/traffic?freeway=000010&dir=S&from=${encodeURIComponent("基隆端")}&to=${encodeURIComponent("八堵")}`);
    expect(body.data.estimatedMin).toBe(2); // 59＋61 秒
    const calls = tdxCalls();
    expect(calls.filter((u) => u.includes("/Section/Freeway"))).toHaveLength(1);
    expect(new URL(calls.find((u) => u.includes("/Live/Freeway"))!).searchParams.get("$filter")).toBe("SectionID eq '0001' or SectionID eq '0003'");
    const again = await get(`/api/freeway/traffic?freeway=000010&dir=S&from=${encodeURIComponent("基隆端")}&to=${encodeURIComponent("八堵")}&x=1`);
    expect(again.res.headers.get("X-Cache")).toBe("HIT");
    expect(tdxCalls()).toHaveLength(calls.length);
  });

  it("traffic：「台」與「臺」視為同一個交流道名稱", async () => {
    const raw = { ...fwSection, Sections: fwSection.Sections.map((s) => (s.SectionID === "0001" ? { ...s, RoadSection: { Start: "台北端", End: "基隆" } } : s)) };
    fetchMock.mockImplementation(async (url: string) =>
      url.includes("openid-connect") ? Response.json({ access_token: "T", expires_in: 86_400 })
        : Response.json(url.includes("/Section/") ? raw : fwLive));
    const { res } = await get(`/api/freeway/traffic?freeway=000010&dir=S&from=${encodeURIComponent("台北端")}&to=${encodeURIComponent("基隆")}`);
    expect(res.status).toBe(200);
  });

  it("參數錯誤或路線不存在：404 NOT_FOUND，且不打即時資料", async () => {
    for (const q of ["freeway=1&dir=S&from=a&to=b", "freeway=000010&dir=X&from=a&to=b", "freeway=000010&dir=constructor&from=a&to=b", "freeway=000010&dir=S&from=a&to=a", `freeway=000010&dir=S&from=${encodeURIComponent("五堵")}&to=${encodeURIComponent("基隆")}`]) {
      const { res, body } = await get(`/api/freeway/traffic?${q}`);
      expect([res.status, body.error?.code]).toEqual([404, "NOT_FOUND"]);
    }
    expect(tdxCalls().some((u) => u.includes("/Live/Freeway"))).toBe(false);
  });
});
