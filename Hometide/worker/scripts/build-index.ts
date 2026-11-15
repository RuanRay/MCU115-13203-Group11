// 國道客運路線索引（規格 8.6、GUIDE 3.4-6）：GitHub Actions 每日執行，寫入 Worker KV（BUS_INDEX）。
// 執行：node scripts/build-index.ts（Node 24 直接執行 TS）
// 環境變數：TDX_CLIENT_ID、TDX_CLIENT_SECRET；CF_API_TOKEN、CF_ACCOUNT_ID、KV_NAMESPACE_ID 缺任一個時只印統計、不寫入。
// 不得輸出金鑰或 Token（GUIDE 3.4-7）。

declare const process: { env: Record<string, string | undefined>; argv: string[]; exitCode?: number };

export const STATIONS_KEY = "bus:stations";
export const INDEX_KEY = "bus:index";

const TOKEN_URL = "https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token";
const BASE_URL = "https://tdx.transportdata.tw/api/basic";
const NATIONAL_BUS = 13; // TDX BusRouteType：13＝國道客運（3-1 查證，1610 統聯）

/** TDX LocationCityCode → 縣市（TDX 縣市代碼表） */
const CITY: Record<string, string> = {
  TPE: "臺北市", NWT: "新北市", TAO: "桃園市", TXG: "臺中市", TNN: "臺南市", KHH: "高雄市",
  KEE: "基隆市", HSZ: "新竹市", HSQ: "新竹縣", MIA: "苗栗縣", CHA: "彰化縣", NAN: "南投縣",
  YUN: "雲林縣", CYI: "嘉義市", CYQ: "嘉義縣", PIF: "屏東縣", ILA: "宜蘭縣", HUA: "花蓮縣",
  TTT: "臺東縣", PEN: "澎湖縣", KIN: "金門縣", LIE: "連江縣",
};

type Name = { Zh_tw?: string; En?: string };
export type TdxRoute = { RouteUID?: string; BusRouteType?: number };
export type TdxStopOfRoute = {
  RouteUID?: string;
  RouteName?: Name;
  SubRouteUID?: string;
  Direction?: number;
  Operators?: { OperatorName?: Name }[];
  Stops?: { StopName?: Name; StopSequence?: number; StopBoarding?: number; LocationCityCode?: string }[];
};

/** 與 TRA／THSR 相同欄位（附錄 E-7）；id 即站名（實作者確認：以站名去重） */
export interface BusStation {
  id: string;
  nameZh: string;
  nameEn: string | null;
  city: string | null;
  sortKey: number;
}

/** 子路線（SubRouteUID）→ 路線資訊；站名 → 經過的 [SubRouteUID, StopSequence, StopBoarding] */
export interface BusIndex {
  routes: Record<string, { route: string; direction: number; operators: string[] }>;
  stops: Record<string, [string, number, number][]>;
}

const zh = (s: string) => s.replaceAll("台", "臺"); // 規格 8.7

/** 只保留國道客運，產生站牌清單（以站名去重，順序為首次出現）與索引 */
export function buildIndex(routes: TdxRoute[], stopOfRoutes: TdxStopOfRoute[]): { stations: BusStation[]; index: BusIndex } {
  const national = new Set(routes.filter((r) => r.BusRouteType === NATIONAL_BUS).map((r) => r.RouteUID));
  const stations = new Map<string, BusStation>();
  const index: BusIndex = { routes: {}, stops: {} };

  for (const sr of stopOfRoutes) {
    const sub = sr.SubRouteUID;
    const route = sr.RouteName?.Zh_tw;
    if (!national.has(sr.RouteUID) || !sub || !route || !sr.Stops?.length) continue;
    index.routes[sub] = {
      route,
      direction: sr.Direction ?? 0,
      operators: (sr.Operators ?? []).map((o) => o.OperatorName?.Zh_tw).filter((n): n is string => !!n),
    };
    for (const s of sr.Stops) {
      const raw = s.StopName?.Zh_tw?.trim();
      if (!raw || typeof s.StopSequence !== "number") continue;
      const name = zh(raw);
      // ponytail: 同名不同縣市的站牌會合併、縣市取首次出現者；國道站牌多為轉運站，衝突時再改用「站名＋縣市」當 id
      if (!stations.has(name)) {
        const city = s.LocationCityCode ? (CITY[s.LocationCityCode] ?? null) : null;
        stations.set(name, { id: name, nameZh: name, nameEn: s.StopName?.En?.trim() || null, city, sortKey: stations.size });
      }
      (index.stops[name] ??= []).push([sub, s.StopSequence, s.StopBoarding ?? 0]);
    }
  }
  return { stations: [...stations.values()], index };
}

async function tdx<T>(token: string, path: string, params: Record<string, string>): Promise<T> {
  const url = new URL(BASE_URL + path);
  url.searchParams.set("$format", "JSON");
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${token}`, "Accept-Encoding": "gzip" },
    signal: AbortSignal.timeout(60_000), // 全部站序資料較大，逾時比 Worker 的 8 秒寬
  });
  if (!res.ok) throw new Error(`TDX ${path} failed (${res.status})`);
  return (await res.json()) as T;
}

async function token(id: string, secret: string): Promise<string> {
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "client_credentials", client_id: id, client_secret: secret }),
  });
  const body = (await res.json().catch(() => ({}))) as { access_token?: string };
  if (!res.ok || !body.access_token) throw new Error(`TDX token request failed (${res.status})`);
  return body.access_token;
}

/** 一次 bulk 寫入兩個 key（KV 每日寫入 1 次，規格 8.6） */
async function writeKv(apiToken: string, account: string, ns: string, entries: Record<string, unknown>): Promise<void> {
  const res = await fetch(`https://api.cloudflare.com/client/v4/accounts/${account}/storage/kv/namespaces/${ns}/bulk`, {
    method: "PUT",
    headers: { Authorization: `Bearer ${apiToken}`, "Content-Type": "application/json" },
    body: JSON.stringify(Object.entries(entries).map(([key, v]) => ({ key, value: JSON.stringify(v) }))),
  });
  if (!res.ok) throw new Error(`KV bulk write failed (${res.status})`);
}

export async function fetchAndBuild(env: Record<string, string | undefined>) {
  const { TDX_CLIENT_ID: id, TDX_CLIENT_SECRET: secret } = env;
  if (!id || !secret) throw new Error("TDX_CLIENT_ID / TDX_CLIENT_SECRET not set");
  const t = await token(id, secret);
  const routes = await tdx<TdxRoute[]>(t, "/v2/Bus/Route/InterCity", {
    $select: "RouteUID,BusRouteType",
    $filter: `BusRouteType eq ${NATIONAL_BUS}`,
  });
  const stopOfRoutes = await tdx<TdxStopOfRoute[]>(t, "/v2/Bus/StopOfRoute/InterCity", {
    $select: "RouteUID,RouteName,SubRouteUID,Direction,Operators,Stops",
  });
  return { ...buildIndex(routes, stopOfRoutes), builtAt: Date.now() };
}

async function main(): Promise<void> {
  const env = process.env;
  const { stations, index, builtAt } = await fetchAndBuild(env);
  console.log(`stations=${stations.length} subRoutes=${Object.keys(index.routes).length}`);
  if (stations.length === 0) throw new Error("Empty index, refusing to overwrite KV");

  const { CF_API_TOKEN, CF_ACCOUNT_ID, KV_NAMESPACE_ID } = env;
  if (!CF_API_TOKEN || !CF_ACCOUNT_ID || !KV_NAMESPACE_ID) {
    console.log("Cloudflare env not set: dry run, KV not written");
    return;
  }
  await writeKv(CF_API_TOKEN, CF_ACCOUNT_ID, KV_NAMESPACE_ID, {
    [STATIONS_KEY]: { builtAt, stations },
    [INDEX_KEY]: { builtAt, ...index },
  });
  console.log("KV written");
}

// 被測試 import 時不執行
if (process.argv[1]?.replaceAll("\\", "/").endsWith("scripts/build-index.ts")) {
  main().catch((e: unknown) => {
    console.error(e instanceof Error ? e.message : "build-index failed");
    process.exitCode = 1;
  });
}
