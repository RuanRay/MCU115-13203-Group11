## 8. 後端與外部 API 規格（Worker＋TDX）

### 8.1 為什麼要有後端

1. **金鑰安全：** TDX Client Secret 若放在 App 裡，APK 反編譯就能取得，被濫用會耗盡額度甚至停權。金鑰只放在 Worker 的加密環境變數（`wrangler secret put`）。
2. **額度與效能：** TDX 呼叫次數按金鑰計算。由 Worker 統一換 Token、快取資料後分給所有使用者，不論多少人使用，對 TDX 的呼叫量都維持固定。
3. **資料整理：** 國道路況、國道客運的原始資料量大、欄位多；Worker 先篩選、合併成精簡 JSON，App 省流量也省電。

### 8.2 部署與限制

- **平台：** Cloudflare Workers 免費方案（不需信用卡），網址例：`https://hometide-api.<帳號>.workers.dev`。App 以 `local.properties` 的 `WORKER_BASE_URL` 經 `buildConfigField` 注入（網址不是秘密，但仍不寫死在程式碼）。
- **限制：** 每天 100,000 次請求；每次執行 CPU 約 10 ms；每次最多 50 個子請求；Cron Trigger 每帳號 5 個。對策見 8.6。
- **金鑰：** `TDX_CLIENT_ID`、`TDX_CLIENT_SECRET` 以 Wrangler Secret 設定；本機開發放 `worker/.dev.vars`（不進 Git）。客運路線索引的排程在 GitHub Actions 執行，金鑰放在 GitHub Secrets。
- **防濫用（選做）：** Worker 驗證請求標頭中的 Firebase App Check token，只接受本 App 的請求；未啟用前以每 IP 每分鐘請求數限制作為替代。

### 8.3 App 呼叫的 Worker 端點

所有回應統一格式：成功 `{"updatedAt": 毫秒時間, "source": "TDX", "data": …}`；失敗 `{"error": {"code": "NETWORK|AUTH|QUOTA|NOT_FOUND|PARSE|UNKNOWN", "message": "…"}}`，App 對應到第 6.3 節的錯誤類型。

| 端點 | 參數 | 回傳 | 對應需求 |
|---|---|---|---|
| `GET /api/stations` | `operator`＝TRA／THSR／BUS | 車站或站牌清單（代碼、中英文名、縣市） | FR-SRCH-02、FR-BUS-01 |
| `GET /api/timetable` | `operator`、`from`、`to`、`date` | 當日班次（已轉換成 TrainItem 欄位；BUS 含 `arriveEstimated`） | FR-RES-01、FR-BUS-02 |
| `GET /api/fare` | `operator`、`from`、`to`（BUS 另加 `route`） | 各車種或路線的全票票價 | FR-RES-02、FR-BUS-03 |
| `GET /api/live` | `operator`、`from`、`to` | 台鐵：各車次誤點分鐘；BUS：各班次預估到站分鐘 | FR-RES-03、FR-BUS-02 |
| `GET /api/freeway/meta` | — | 國道、方向、交流道清單（依里程排序） | FR-HWY-01、FR-HWY-06 |
| `GET /api/freeway/traffic` | `freeway`、`dir`、`from`、`to` | 起訖交流道之間各路段的時速、壅塞等級、旅行時間；預估總時間、平常時間、壅塞段數 | FR-HWY-02、03 |
| `GET /api/compare` | `freeway`、`dir`、`from`、`to` | 起訖縣市、高鐵／台鐵／國道客運各自的下一班與含等車總耗時、開車預估、最快者 | FR-HWY-04 |

### 8.4 Worker 呼叫的 TDX API

- **認證：** `POST https://tdx.transportdata.tw/auth/realms/TDXConnect/protocol/openid-connect/token`（`grant_type=client_credentials`）；Token 有效期內重複使用，存於 Worker 記憶體與 Cache API，到期前 5 分鐘更新；收到 401 時更新一次並重送。
- **基底網址：** `https://tdx.transportdata.tw/api/basic`，所有請求加 `$format=JSON`；能篩選的一律加 `$filter`、`$select`，減少 Worker 的 CPU 用量。
- 「狀態」欄：**範例確認**＝已見於官方範例或公開專案；**待查證**＝依 TDX 命名規則推定，第 3 週以 Swagger 實際呼叫確認後更新本表。

| 用途 | 方法與路徑 | 狀態 |
|---|---|---|
| 台鐵車站 | `GET /v3/Rail/TRA/Station` | 範例確認 |
| 台鐵起訖站時刻 | `GET /v3/Rail/TRA/DailyTrainTimetable/OD/{originId}/to/{destId}/{yyyy-MM-dd}` | 範例確認 |
| 台鐵起訖站票價 | `GET /v3/Rail/TRA/ODFare/{originId}/to/{destId}` | 待查證 |
| 台鐵即時位置與誤點 | `GET /v3/Rail/TRA/TrainLiveBoard` | 範例確認 |
| 高鐵車站 | `GET /v2/Rail/THSR/Station` | 範例確認 |
| 高鐵起訖站時刻 | `GET /v2/Rail/THSR/DailyTimetable/OD/{originId}/to/{destId}/{yyyy-MM-dd}` | 範例確認 |
| 高鐵起訖站票價 | `GET /v2/Rail/THSR/ODFare/{originId}/to/{destId}` | 待查證 |
| 國道路段基本資料 | `GET /v2/Road/Traffic/Section/Freeway` | 待查證（市區道路版 `/Section/City/{City}` 已有範例） |
| 國道路段即時路況 | `GET /v2/Road/Traffic/Live/Freeway` | 待查證（同上） |
| 國道壅塞等級定義 | `GET /v2/Road/Traffic/CongestionLevel/Freeway` | 待查證（同上） |
| 公路客運路線 | `GET /v2/Bus/Route/InterCity` | 待查證 |
| 公路客運路線站序 | `GET /v2/Bus/StopOfRoute/InterCity` | 待查證 |
| 公路客運班表 | `GET /v2/Bus/Schedule/InterCity/{RouteName}` | 待查證 |
| 公路客運預估到站 | `GET /v2/Bus/EstimatedTimeOfArrival/InterCity/{RouteName}` | 待查證 |
| 公路客運票價 | `GET /v2/Bus/RouteFare/InterCity/{RouteName}` | 待查證 |

### 8.5 快取策略（Worker Cache API）

| 資料 | 快取時間 | 說明 |
|---|---|---|
| TDX Token | 到期前 5 分鐘 | 所有請求共用 |
| 車站、站牌、交流道清單 | 7 天 | — |
| 時刻表（台鐵、高鐵、客運） | 2 小時 | 以 operator＋起訖＋日期為鍵 |
| 票價 | 7 天 | — |
| 台鐵即時誤點、客運預估到站 | 60 秒 | — |
| 國道即時路況、回家方式比較 | 60 秒 | 與路況更新頻率一致 |

App 端另有 Room 快取（timetable_cache、fare_cache、highway_cache）供離線使用；同條件 2 小時內不重複呼叫時刻端點。

### 8.6 CPU 限制與資料整理對策

- **讓 TDX 先篩選：** 國道路況以 `$filter` 指定國道與路段、`$select` 只取需要欄位；客運預估到站以路線名稱查詢。
- **客運路線索引離線產生：** 「哪些路線依序經過起站與迄站」需要掃描全部客運路線站序，超過 Worker 單次 CPU 限制，因此由 GitHub Actions 每日執行 `scripts/build-index.ts` 產生「站牌 → 路線與站序」索引，寫入 Worker KV（每天 1 次寫入）；Worker 查詢時只讀索引。
- **靜態對照表：** 交流道↔縣市、縣市↔各運具代表車站／轉運站、高鐵站轉乘分鐘，以 JSON 放在 `worker/src/static/`，隨部署更新。
- **回家方式比較：** `/api/compare` 在 Worker 內並行查詢三種運具的時刻（子請求 ≤ 10 個），計算後一次回傳，App 只需一次請求。

### 8.7 回應轉換規則（在 Worker 執行）

- **時刻表 → `TrainItem`：** 車次、車種名稱、起站出發時間、迄站抵達時間、行駛分鐘（跨午夜時加 24 小時計算）、山海線、對號與自行車旗標。
- **票價：** 從票價陣列中挑選「全票、標準座／對號座」那一筆；台鐵依車種代碼對應（自強、莒光、區間），找不到該車種時以同起訖站最接近車種的價格並標示「約」。
- **即時誤點：** TrainLiveBoard 以車次（TrainNo）對應時刻表班次，取延誤分鐘數；對應不到視為準點。
- **站名正規化：** 一律把「台」轉成「臺」後再存入與比對。

- **國道路況：** 依交流道里程排序路段，只保留起訖交流道之間的路段；壅塞等級對應 順暢／車多／壅塞 三級；預估時間＝各路段旅行時間總和，平常時間＝路段長度依速限換算之和。
- **國道客運：** 同一路線同一班次只輸出一筆；抵達時間有班表時採用，否則以「起站發車時間＋路線平均行車時間」推算並設 `arriveEstimated=true`；業者名稱取中文簡稱（例：統聯）。

### 8.8 呼叫量估算與節流

- App 一次查詢最多呼叫 Worker 3 次（時刻、票價、即時），因 App 與 Worker 兩層快取，實際打到 TDX 的次數遠低於使用者數。
- 國道分頁在前景時每 60 秒呼叫一次 `/api/freeway/traffic` 與 `/api/compare`；Worker 60 秒快取讓同一路線在一分鐘內只打一次 TDX。
- Worker 收到 TDX 429 時回傳 `QUOTA`，App 顯示「查詢太頻繁，請稍後再試」，並在 60 秒內只讀快取。
