# 歸潮 Hometide（返鄉交通助手）專案實作指導準則與需求

| 項目 | 內容 |
|---|---|
| 版本 | v1.2（新增需求總覽、Worker 開發規則與完成標準、Worker API 合約、CI 範例、決策紀錄） |
| 日期 | 2026-11-09 |
| 適用對象 | 主要實作者 阮梓睿（14130133）、本機 Claude Code（Android Studio 終端機） |
| 上位文件 | 需求規格書 v1.1（`docs/spec/00`～`16`，內容與期中報告附件相同） |
| 期限 | 8 週後期末 Demo |

---

## 0. 這份文件怎麼用

1. **規格書管「做什麼」，本準則管「怎麼做」。** 功能、畫面、資料、API、驗收條件一律以 `docs/spec/` 為準；開發流程、時程、AI 使用方式、品質標準以本準則為準。
2. **兩者衝突或規格沒寫時，停下來問人，不要自行決定。** 尤其是 UI（見 3.2）。
3. **Claude Code 不需要每次讀完整份文件。** 根目錄的 `CLAUDE.md` 是精簡版，每次自動載入；本準則與規格書依任務需要「只讀相關章節」。
4. **第 14 章（報告版分工）不用於實作排程。** 實際時程看本準則第 8 節。
5. 本文件中「必須／不得」是硬性規則；「建議」可依實況調整，但調整要記在開發日誌。

---

## 1. 已定案的實作條件

| 項目 | 決定 |
|---|---|
| 題目 | 歸潮 Hometide（返鄉交通助手），照期中報告與需求規格書 v1.1 實作 |
| 套件名稱 | `com.group11.hometide` |
| 架構 | App → 自建 Cloudflare Worker → TDX；App 內不放任何 TDX 金鑰 |
| 範圍 | 原有功能＋國道客運（第三種運具）＋「國道」分頁（路況、回家方式比較、路線圖），全部為必做 |
| 實作者 | 阮梓睿一人以 AI 主寫，負責全部階段 |
| 報告 | 期中報告已更新為 v1.1；規格書第 14 章維持報告版的四人分工 |
| 開發工具 | Android Studio ＋ 終端機內的 Claude Code |
| Claude 方案 | Pro（額度有限，見第 7 節的省額度策略） |
| 多代理 | Agent Teams 為實驗功能，平時關閉，只在里程碑審查與疑難除錯時開啟 |
| 專案骨架 | 由本機 Claude Code 從零建立（第 1 週） |
| 外部帳號 | Firebase 專案已有；TDX 金鑰已取得（測試後建議換一組新的）；需另建 Cloudflare 帳號 |
| 時程 | 8 週，第 8 週 Demo |

## 1A. 需求總覽

規格書共 64 條功能需求（FR）、42 個測試案例（TC），全部為本學期必做；S 級（可延後）只有拖曳排序（FR-FAV-02 後半）與依時段問候（FR-SRCH-05）。

| 模組 | 功能需求 | 主要畫面 | 實作週 | 主要測試 |
|---|---|---|---|---|
| 帳號 AUTH | FR-AUTH-01～06 | P1、P2、登入提示 | 2、5 | TC-01～05 |
| 查詢 SRCH | FR-SRCH-01～07 | P3 | 2、4、5 | TC-06、07、31 |
| AI 一句話 AI | FR-AI-01～05 | AI 確認卡 | 6 | TC-08～11、28 |
| 查詢結果 RES | FR-RES-01～06 | P4 | 4、5 | TC-12～15、32 |
| 收藏 FAV | FR-FAV-01～05 | P5 車票夾／出發提醒 | 5 | TC-16、17 |
| 提醒 RMD | FR-RMD-01～05 | 提醒對話框、通知 | 5 | TC-18～20 |
| 使用紀錄 USG | FR-USG-01～04 | P6 | 7 | TC-21～23 |
| 回饋 FB | FR-FB-01～04 | P7 | 7 | TC-24、25 |
| 同步 SYNC | FR-SYNC-01～02 | （背景） | 7 | TC-25 |
| 離線與設定 OFF／SET | FR-OFF-01、FR-SET-01 | 全部 | 4、7 | TC-15、26 |
| 下一班 NEXT | FR-NEXT-01～06 | 下一班 | 5、6 | TC-29、30 |
| 國道路況 HWY | FR-HWY-01～06 | 國道 | 6 | TC-34～38 |
| 國道客運 BUS | FR-BUS-01～06 | P3、P4、下一班、P5 | 3～6 | TC-39～41 |
| 非功能 NFR | NFR-01～11 | — | 全期、8 | TC-27、33、42 |

**後端需求（Worker）：** 7 個端點（規格 8.3）、15 支 TDX API（8.4，其中 10 支待查證）、快取規則（8.5）、CPU 限制對策（8.6）。

---

## 2. 環境與外部服務需求

### 2.1 開發環境

| 項目 | 需求 |
|---|---|
| IDE | Android Studio 最新穩定版 |
| JDK | 使用 Android Studio 內附的 JDK（Gradle JDK 設為 Embedded JDK） |
| 建置 | Gradle Kotlin DSL（`build.gradle.kts`）＋ Version Catalog（`gradle/libs.versions.toml`） |
| 語言與介面 | Kotlin、XML Layout、ViewBinding；**不得**使用 Jetpack Compose、Hilt |
| 註解處理 | Room 使用 KSP（不用 kapt） |
| SDK | `minSdk 26`；`compileSdk`／`targetSdk` 用建立專案當時 Android Studio 預設的最新穩定版，確定後寫入開發日誌，之後不再變動 |
| 套件名稱 | `com.group11.hometide` |
| 測試手機 | 至少一支 Android 13 以上實機（通知權限、提醒、開機重排都要實機測）；規格書 12.1 要求 Android 13 與 14 各測一次，第二支可借用 |
| 後端 | Node.js LTS、Wrangler CLI（`npm i -D wrangler`）、TypeScript；Cloudflare 免費帳號 |
| 版本控制 | GitHub **私人** repo（App 與 `worker/` 同一個 repo）；組員可加入檢視 |

### 2.2 外部服務與金鑰

| 服務 | 要申請／開啟的項目 | 金鑰或設定檔放哪裡 | 何時需要 |
|---|---|---|---|
| TDX | 會員（學研單位）→ API 金鑰 Client Id／Secret | **只放 Worker**：`wrangler secret put TDX_CLIENT_ID`、`TDX_CLIENT_SECRET`；本機開發放 `worker/.dev.vars`；客運索引排程用 GitHub Secrets | 第 2 週 |
| Cloudflare Workers | 免費帳號、`wrangler login`、建立 `hometide-api`；KV namespace（客運路線索引） | Worker 網址放 App 的 `local.properties`：`WORKER_BASE_URL`，以 `buildConfigField` 注入 | 第 2 週 |
| GitHub Actions | 每日執行 `worker/scripts/build-index.ts` 產生客運路線索引並寫入 KV | Secrets：`TDX_CLIENT_ID`、`TDX_CLIENT_SECRET`、`CLOUDFLARE_API_TOKEN` | 第 3 週 |
| Firebase Authentication | 開啟「電子郵件／密碼」 | `app/google-services.json` | 第 2 週 |
| Cloud Firestore | 建立資料庫，貼上規格書 7.4 安全規則 | 同上 | 第 7 週 |
| Firebase AI Logic | 開啟，選 Gemini Developer API | 不需要在 App 放 Gemini 金鑰 | 第 6 週 |
| Firebase App Check | 開發與 Demo 期間一律用 **Debug Provider**；把 Logcat 印出的 debug token 登記到主控台；**Demo 前不要開啟「強制執行」** | debug 依賴只加在 `debugImplementation` | 第 6 週 |

> App Check 的 Play Integrity 原則上要求 App 由 Google Play 安裝；本專案以 Android Studio 直接安裝，因此不依賴 Play Integrity 驗證。

**金鑰規則（必須）：**
- **App 內不得出現 TDX 金鑰，也不得直接呼叫 TDX**；App 只呼叫 Worker（規格書第 8 章）。驗收方式：TC-42 用「Analyze APK」搜尋金鑰字串，必須找不到。
- `local.properties`、`worker/.dev.vars` 必須在 `.gitignore` 內；任何金鑰不得寫進原始碼、`strings.xml`、`wrangler.toml`、commit 訊息或開發日誌。
- `google-services.json` 可以進私人 repo，但 repo 不得公開。
- Claude Code 不得把金鑰內容輸出到終端機或檔案；設定 Worker Secret 時由實作者自己在終端機輸入值。

### 2.3 會用到的 API 一覽

| 類別 | API | 規格位置 |
|---|---|---|
| App → Worker | `/api/stations`、`/api/timetable`、`/api/fare`、`/api/live`、`/api/freeway/meta`、`/api/freeway/traffic`、`/api/compare` | 8.3 |
| Worker → TDX 認證 | Token（client_credentials） | 8.4 |
| Worker → TDX 台鐵、高鐵 | Station、DailyTrainTimetable／DailyTimetable OD、ODFare、TrainLiveBoard | 8.4 |
| Worker → TDX 國道 | Road/Traffic Section、Live、CongestionLevel（Freeway） | 8.4（待查證） |
| Worker → TDX 國道客運 | Bus Route、StopOfRoute、Schedule、EstimatedTimeOfArrival、RouteFare（InterCity） | 8.4（待查證） |
| Firebase | Auth、Firestore、AI Logic（Gemini 結構化輸出）、App Check | 7.4、第 9 章 |
| Android 系統 | AlarmManager、BroadcastReceiver、Foreground Service（dataSync）、ContentProvider、Notification、ProcessLifecycleOwner、DataStore、Room | 第 10 章 |

---

## 3. 給 Claude Code 的硬性規則

### 3.1 架構與程式

1. 架構固定為 `Activity/Fragment → ViewModel（StateFlow<UiState>）→ Repository（suspend, Result<T>）→ Room／Retrofit（HometideApi → Worker）／Firebase`（規格 6.1、6.3）。App 不得直接呼叫 TDX。
2. 依賴注入只用 `AppContainer` 手動建立單例；`HometideApp` 持有 `AppContainer`。
3. 套件結構照規格 6.2；Worker 照規格 6.5；新增檔案前先確認放在哪個套件，**不得**自創平行結構。
4. 所有 Activity 繼承 `BaseActivity`；`PendingIntent` 一律 `FLAG_IMMUTABLE`。
5. 字串一律放 `res/values/strings.xml`（繁體中文）；顏色只用規格 5.1 的語意色票，並同時提供 `values-night`；**不得**在 layout 或程式寫死色碼與中文字串。
6. 元件 ID 必須與規格第 4 章表格完全一致（例：`tkManual`、`chipGroupRoutes`）。
7. 錯誤類型只用 `NetworkError`、`AuthError`、`QuotaExceeded`、`NotFound`、`ParseError`、`Unknown`，訊息對照規格 11.2。
8. 新增 Gradle 依賴前要先列出依賴名稱與理由，經實作者同意才加；版本放 Version Catalog。
9. 不得修改 `docs/spec/` 的內容；發現規格錯誤時寫進開發日誌的「規格問題」並告知實作者。

### 3.2 UI 規則（最重要）

1. 畫面以 `docs/mockups/*.png` 為視覺依據、規格第 4、5 章為元件與數值依據，動畫以 `docs/mockups/animations.html` 與規格 5.4 為依據。
2. **規格與畫面圖都沒有寫到的 UI 細節（位置、尺寸、顏色、文案、互動、動畫），Claude Code 必須停下來提問，不得自行決定後直接實作。** 提問格式見 9.7。
3. 畫面圖與規格文字不一致時，先停下來提問，不要自行挑一個。
4. 本準則 11.2 列出的「暫定 UI 項目」，實作到時要先請實作者確認。

### 3.3 工作方式

1. 一次只做一個任務（一個或一組相關的 FR），做完並通過完成標準（第 5 節）才開始下一個。
2. 動手前先列出「要新增／修改的檔案清單」與「對應的 FR、AC、TC」，等實作者回覆「可以」再寫。
3. 只讀任務需要的規格章節；不要一次讀整個 `docs/spec/`。
4. 編譯由 Claude Code 執行 `./gradlew assembleDebug`（Windows 為 `gradlew.bat`），只回報錯誤摘要，不要貼整份 log。
5. 每個任務結束時更新開發日誌（第 10 節）。

### 3.4 Worker（後端）規則

1. 語言 TypeScript（`strict: true`），檔案分工照規格 6.5；每個端點的處理函式放在對應檔案（rail／bus／freeway／compare），`index.ts` 只做路由。
2. 所有回應使用統一格式（規格 8.3）：成功 `{updatedAt, source, data}`，失敗 `{error: {code, message}}`，HTTP 狀態碼與 `code` 對應（NOT_FOUND→404、QUOTA→429、AUTH→502、其他→500）。
3. 呼叫 TDX 一律經過 `tdx.ts`：自動帶 Token、逾時 8 秒、收到 401 更新 Token 後重試一次、收到 429 回傳 `QUOTA`。
4. 每個端點都必須設定快取，時間照規格 8.5；快取鍵包含所有查詢參數。
5. 能用 `$filter`／`$select`／`$top` 讓 TDX 先篩選的一律使用；單次請求子請求數不得超過 10 個（平台上限 50）。
6. 需要掃描大量資料的工作（客運路線索引）不得放在請求處理中，只能放在 `scripts/build-index.ts` 由 GitHub Actions 執行。
7. 不得在 log、錯誤訊息、回應中輸出金鑰或 Token；`console.log` 只記錄端點、參數、耗時與錯誤代碼。
8. 回應欄位名稱照附錄 E 的 API 合約；要變更合約時先更新附錄 E 並告知實作者，App 與 Worker 同一個 commit 一起改。

---

## 4. Repo 結構

```
Hometide/
├── CLAUDE.md                     ← Claude Code 每次自動載入的精簡規則（附錄 A）
├── .claude/
│   ├── settings.json             ← 專案設定（附錄 C）
│   └── agents/
│       ├── spec-reader.md        ← 規格查詢員（附錄 B-1）
│       └── spec-reviewer.md      ← 規格審查員（附錄 B-2）
├── docs/
│   ├── GUIDE.md                  ← 本文件
│   ├── spec/00～16-*.md          ← 需求規格書，依章拆檔
│   ├── mockups/                  ← 畫面圖、流程圖、animations.html
│   ├── devlog/                   ← 開發日誌，一天一檔 YYYY-MM-DD.md
│   └── test-report.md            ← 第 8 週填寫
├── app/
│   ├── google-services.json
│   └── src/main/java/com/group11/hometide/...（規格 6.2）
├── worker/                       ← Cloudflare Worker（規格 6.5、第 8 章）
│   ├── wrangler.toml
│   ├── src/…
│   ├── scripts/build-index.ts
│   └── .dev.vars                 ← 不進 Git
├── .github/workflows/build-index.yml  ← 每日產生客運路線索引
├── gradle/libs.versions.toml
└── local.properties              ← 不進 Git
```

`docs/mockups/` 檔案對照：

| 檔名 | 畫面 | 規格章節 |
|---|---|---|
| login.png | P1 登入 | 4.2 |
| register.png | P2 建立帳號 | 4.3 |
| home.png、home_dark.png | P3 查詢（首頁） | 4.4 |
| ai_confirm.png、ai_confirm_dark.png | AI 確認卡 | 4.4、FR-AI-04 |
| next.png、next_dark.png、next_guest.png | 下一班 | 4.5 |
| result.png、result_dark.png | P4 查詢結果 | 4.6 |
| guest_prompt.png | 登入提示對話框 | 4.1、FR-AUTH-06 |
| routes.png、reminders.png | P5 我的路線（車票夾／出發提醒） | 4.7 |
| highway.png、highway_dark.png、highway_guest.png | 國道（會員、深色、訪客／未收藏） | 4.11、FR-HWY |
| bus_result.png | 國道客運查詢結果 | 4.6、FR-BUS-04 |
| profile.png、profile_dark.png | P6 個人 | 4.8 |
| feedback.png、feedback_done.png | P7 意見回饋／送出成功 | 4.9 |
| flow.png | 頁面流程總覽 | 4.10 |
| animations.html | 16 項動畫（瀏覽器開啟可播放） | 5.4 |

> 畫面中的班次、時間、票價是示意資料，實作時以 TDX 實際資料為準。

---

## 5. 完成標準（Definition of Done）

每個任務都要全部符合才算完成：

1. `./gradlew assembleDebug` 編譯成功，沒有新增警告以外的錯誤。
2. 有單元測試的項目（規格 12.1 列出的工具類別、DAO）`./gradlew testDebugUnitTest` 通過。
3. 在實機上依該 FR 的 **AC** 操作一次，結果符合；有對應 **TC** 的照 TC 執行。
4. 畫面與 `docs/mockups/` 對照過（淺色、深色各看一次）；有差異的地方已提問或記錄。
5. 沒有寫死的中文字串與色碼；元件 ID 與規格一致。
6. 開發日誌已更新。
7. 已 commit（第 6 節格式）。

**Worker 任務另外要符合：**

1. `npx wrangler dev` 本機執行，用 curl 或瀏覽器呼叫該端點，回應符合附錄 E 合約。
2. 轉換邏輯（時刻轉換、票價挑選、抵達推算、路段排序、比較計算）有 Vitest 單元測試（`@cloudflare/vitest-pool-workers`），以 `worker/test/fixtures/` 的 TDX 範例回應為輸入，`npx vitest run` 通過。
3. `npx wrangler deploy` 後，正式網址同一請求第二次呼叫明顯變快（快取命中）。
4. 檢查 `wrangler.toml` 與程式碼中沒有任何金鑰。

---

## 6. Git 規範

| 項目 | 規則 |
|---|---|
| 分支 | `main` 永遠可編譯可執行；每個任務開 `feature/<FR編號或主題>`，例：`feature/FR-AUTH-01-02`、`feature/w1-skeleton` |
| 合併 | 通過完成標準後合併回 `main` |
| Commit 訊息 | `<類型>(<FR編號>): <中文說明>`；類型用 `feat`、`fix`、`ui`、`test`、`docs`、`chore`。例：`feat(FR-AUTH-01): 註冊與欄位驗證` |
| 每週標籤 | 每週里程碑完成後打 tag：`w1`～`w8` |
| 禁止 | 提交 `local.properties`、金鑰、`build/` 產物；在 `main` 上直接做大改動 |

---

## 7. Claude Code 使用策略（Pro 方案）

### 7.1 省額度原則

1. **一個任務一個對話。** 任務完成並 commit 後用 `/clear` 清空，再開始下一個任務。
2. **只讀需要的章節。** 不知道該讀哪章時，交給 `spec-reader` 子代理查，主對話只拿結論。
3. **長對話用 `/compact`。** 感覺上下文變長、回應變慢時先壓縮。
4. **編譯錯誤只看摘要。** 讓 Claude Code 自己跑 Gradle、讀錯誤尾段，不要把整份 log 貼進對話。
5. **模型選擇。** 日常實作用預設模型；遇到難解的 bug 或架構問題才用 `/model` 切到較強的模型（以 Pro 方案當時可用的為準）。
6. **額度用完時**，改做不需要 AI 的工作：實機測試、對照畫面圖、寫測試報告、整理開發日誌。

### 7.2 子代理（平常就可以用，較省）

| 名稱 | 用途 | 何時用 |
|---|---|---|
| `spec-reader` | 只讀 `docs/spec/`，回答「某功能的規則是什麼、在哪一章」 | 動手前查規則 |
| `spec-reviewer` | 對照規格檢查剛寫完的程式：FR、AC、元件 ID、字串色碼、架構 | 每個任務完成前 |

定義檔見附錄 B。

### 7.3 Agent Teams（實驗功能，少量使用）

- **平時關閉。** 專案 `.claude/settings.json` 設 `CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS` 為 `"0"`；要開隊時改成 `"1"`，用完改回 `"0"`。開啟期間 Claude 自己具名的子代理也會變成隊友，額度消耗會變大。
- **使用時機（建議）：**
  - 第 2、4、6、7 週的里程碑審查。
  - 卡關超過一個對話仍解不開的 bug（讓隊友各自驗證不同假設）。
- **規模：** 最多 2 個隊友，並在指令中指定較省的模型。
- **檔案分工：** 每個隊友只能改自己負責的檔案；`AndroidManifest.xml`、`build.gradle.kts`、`libs.versions.toml`、`AppContainer.kt`、`themes.xml`、`colors.xml`、`strings.xml`、`worker/wrangler.toml`、`worker/src/index.ts` 只有 lead 可以改。
- **只讓 lead 執行 Gradle（建議）。** 隊友共用同一個專案資料夾，同時跑 Gradle 可能互相卡住（這是推測，官方文件未說明），所以隊友寫完通知 lead，由 lead 統一編譯。
- **結束時**要求 lead 關閉所有隊友，再把設定改回 `"0"`。
- 開隊提示詞見 9.5。

---

## 8. 八週實作計畫

原則：先打通「App → Worker → TDX」這條路，再往上疊功能；國道客運跟著台鐵、高鐵一起做（共用同一套查詢、結果、收藏、提醒），國道分頁在第 6 週集中完成。各頁先完成靜態畫面並預留動畫呼叫位置，動畫集中在第 7 週。

### 第 1 週：專案骨架

| 任務 | 內容 | 規格 |
|---|---|---|
| 1-1 | 建立專案（`com.group11.hometide`）、Version Catalog、6.4 全部依賴、ViewBinding、KSP、google-services 外掛、`buildConfigField` 讀 `WORKER_BASE_URL` | 6.4、8.2 |
| 1-2 | `HometideApp`、`AppContainer`（空殼）、`util/Result` 與錯誤類型 | 6.1～6.3 |
| 1-3 | `colors.xml`＋`values-night`、字體、`themes.xml`、文字樣式 | 5.1、5.2 |
| 1-4 | `TicketView`、`StampView`、`DateStampView` 雛形 | 4.1、5.2 |
| 1-5 | 9 個 Activity 空版面、`BaseActivity`、底部導覽列**五分頁**切換、P1 → P3 基本跳轉 | 第 4 章、4.10 |
| 1-6 | `AndroidManifest.xml`、兩個通知頻道 | 10.4、10.5 |
| 1-7 | `worker/` 初始化（TypeScript＋Wrangler）、`GET /api/health` 部署成功 | 6.5、8.2 |

**本週完成標準：** 實機能開啟、五個分頁能切換；Worker 網址在瀏覽器回應 health。

### 第 2 週：資料層、帳號、Worker 基礎

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 2-1 | Room 13 個 Entity、DAO、`AppDatabase`；DataStore `SettingsStore` | 7.1～7.3 | DAO in-memory 測試 |
| 2-2 | 註冊、登入、自動登入；訪客模式、登入提示對話框；登出 | FR-AUTH-01～06 | TC-01～03 |
| 2-3 | Worker：Token 取得與快取、統一錯誤格式、`/api/stations`（TRA／THSR） | 8.3～8.5 | 401 重試 |
| 2-4 | App：`HometideApi`、`TransitRepository`、車站下載、`StationProvider`、`StationNameNormalizer` | FR-SRCH-02、10.3 | TC-06 |

**里程碑審查（可開隊）。**

### 第 3 週：鐵路與客運資料

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 3-1 | **先查證**：用 `tdx_api_test.py` 與 Swagger 確認 8.4 所有「待查證」路徑與欄位，回填規格問題到 devlog；範例回應存 `worker/test/fixtures/` | 8.4 | — |
| 3-2 | Worker：`/api/timetable`、`/api/fare`、`/api/live`（台鐵、高鐵） | 8.3、8.7 | 單元測試（跨午夜、票價挑選） |
| 3-3 | 客運路線索引：`build-index.ts`＋GitHub Actions＋KV；`/api/stations?operator=BUS` | 8.6、FR-BUS-01 | — |
| 3-4 | Worker：國道客運 `/api/timetable`、`/api/fare`、`/api/live`（預估到站、抵達推算） | FR-BUS-02、03 | — |

**本週完成標準：** 用瀏覽器或 curl 呼叫 Worker，查得到台鐵、高鐵、國道客運真實班次。

### 第 4 週：查詢頁與結果頁

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 4-1 | P3 完整畫面（AI 先不接）：範例句、車票夾（先空）、空白車票（運具三選）、交換、最近查詢、日期章 | FR-SRCH-01、03、05～07、FR-BUS-01 | TC-07 |
| 4-2 | P4 時間軸、車票、「現在」線、狀態；國道客運車票版本 | FR-RES-01、03、FR-BUS-04 | TC-12、13、39 |
| 4-3 | 票價、即時誤點／預估到站、排序與篩選 | FR-RES-02、04 | TC-14 |
| 4-4 | 下拉重新整理、骨架、空狀態、離線 | FR-RES-06、FR-OFF-01 | TC-15 |

**里程碑審查（可開隊）。**

### 第 5 週：收藏、下一班、提醒

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 5-1 | 收藏 ♥、P5 車票夾、首頁車票夾、撕存根查詢（含國道客運） | FR-RES-05、FR-FAV-01～04、FR-SRCH-04、FR-BUS-05 | TC-16、17 |
| 5-2 | 下一班分頁（含國道客運路線）；浮動一句話列先放靜態外觀 | FR-NEXT-01～04、06 | TC-29、30 |
| 5-3 | 提醒對話框、排程、`ReminderReceiver`、通知、點通知開 P5 | FR-RMD-01～03、FR-FAV-05 | TC-18、40 |
| 5-4 | `BootReceiver`、通知權限、登出取消提醒 | FR-RMD-04、05、FR-AUTH-05 | TC-19、20、05、04 |

### 第 6 週：國道分頁與 AI

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 6-1 | Worker：`/api/freeway/meta`、`/api/freeway/traffic`、靜態對照表、`/api/compare` | 8.3、8.6、8.7 | — |
| 6-2 | 國道分頁：開車路線晶片與收藏、大車票、比較車票、路線圖、每分鐘更新、離線快取、訪客表單 | FR-HWY-01～06、4.11 | TC-34～38 |
| 6-3 | **先查證** Gemini 模型名稱；App Check Debug Provider | 9.1 | — |
| 6-4 | 一句話解析（含 BUS）、確認卡、失敗與限流、`ai_query_log`；接上下一班浮動列 | FR-AI-01～05、FR-BUS-06、FR-NEXT-05 | TC-08～11、41 |

**里程碑審查（可開隊）。**

### 第 7 週：統計、回饋、同步、動畫、深色

| 任務 | 內容 | 規格 | 測試 |
|---|---|---|---|
| 7-1 | `UsageTracker`、P6 統計與會員證 | FR-USG-01～04 | TC-21～23 |
| 7-2 | P7 意見回饋、AI 分類、`SyncService`、Firestore | FR-FB-01～04、FR-SYNC | TC-24、25 |
| 7-3 | 16 項動畫（先做 AN-01、AN-05、AN-04） | 5.4 | TC-31～33 |
| 7-4 | 深色模式逐頁檢查、無障礙 | FR-SET-01、NFR-07 | TC-26、27 |

### 第 8 週：測試與交付

- 週一至週三：TC-01～42 在 Android 13、14 各跑一次，填 `docs/test-report.md`；AI 35 句測試集（TC-28）；APK 金鑰檢查（TC-42）。
- 週四起：功能凍結，只修 bug；錄展示影片、整理期末報告（開發日誌是「AI 參與」的佐證）。
- Demo 前一天：在 Demo 手機上完整走一次流程圖 `flow.png` 的每一條箭頭，並確認 Worker 當日請求量正常。

### 進度落後時的取捨順序

先砍 S 級需求（規格標示 S：拖曳排序、依時段問候語）→ 再簡化動畫（保留 AN-01、AN-05、AN-04，其餘可改成簡單淡入）→ 最後才考慮砍 M 級功能。砍任何 M 級功能前要先討論。

---

## 9. 提示詞範本（直接貼給 Claude Code）

### 9.1 每週開工

```
這週是第 N 週，目標見 docs/GUIDE.md 第 8 節「第 N 週」。
請先讀那一節，列出本週任務的執行順序與每個任務會讀哪些規格章節，
不要開始寫程式，等我確認。
```

### 9.2 單一任務

```
任務：<任務編號，例 2-3>，對應 <FR 編號>。
請：
1. 讀 docs/spec/03-functional-requirements.md 中 <FR 編號> 的條目，
   以及 docs/spec/04-screens.md 的 <4.x 節>、相關資料表與 API 章節（只讀需要的）。
2. 看 docs/mockups/<畫面>.png。
3. 列出要新增或修改的檔案清單、對應的 AC 與 TC，以及你發現規格沒寫清楚的 UI 細節。
4. 等我回覆「可以」再實作。實作完執行 assembleDebug，
   再請 spec-reviewer 審查，最後告訴我實機要怎麼驗收。
```

### 9.3 修編譯錯誤

```
執行 ./gradlew assembleDebug，只讀錯誤的最後部分，
說明原因後修正。修正不得改變規格行為；若必須改，先問我。
```

### 9.4 修 bug

```
Bug：<實際看到什麼>，預期：<規格寫的行為，附 FR／TC 編號>。
重現步驟：<1、2、3>。
請先列出可能原因與驗證方式，確認原因後再修，修完說明怎麼驗證。
```

### 9.5 里程碑審查（開 Agent Teams）

```
（先把 .claude/settings.json 的 CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS 改成 "1"）
建立 2 個隊友，使用 Sonnet：
- reviewer-spec：對照 docs/spec 第 3、4 章，審查第 N 週完成的 FR，
  逐條列出符合／不符合／未實作，不得修改檔案。
- reviewer-code：審查架構是否符合 docs/spec/06-architecture.md、
  有無寫死字串色碼、PendingIntent 旗標、金鑰外洩，不得修改檔案。
兩人互相補充後，由你彙整成一份清單給我，然後關閉兩位隊友。
```

### 9.6 Worker 任務

```
任務：<任務編號>，Worker 端點 <例：GET /api/timetable（BUS）>，對應 <FR 編號>。
請讀 docs/spec/08-backend-api.md 的 8.3～8.7 與 docs/GUIDE.md 3.4、附錄 E，
先列出要新增／修改的 worker/ 檔案、要用的 TDX API（標明是否待查證）、快取鍵與 TTL、
單元測試要用的 fixtures，等我說「可以」再寫。完成後執行 vitest 與 wrangler dev，
用 curl 示範一次回應，並確認沒有金鑰外洩。
```

### 9.7 Claude Code 遇到 UI 不明時的提問格式

```
【UI 待確認】<畫面>／<元件 ID>
規格與畫面圖的現況：<引用規格條目或描述圖>
不明之處：<具體問題>
選項：A <…>　B <…>　（若有建議，標註理由）
在你回覆前，這個元件先不實作／先用佔位。
```

---

## 10. 開發日誌（AI 參與紀錄）

老師要求實作中要有 AI 參與，日誌就是佐證，同時也延續規格 15.1 第 4 點。檔案：`docs/devlog/YYYY-MM-DD.md`，由 Claude Code 在每個任務結束時追加，實作者確認內容。

```
## <任務編號>：<標題>（<FR 編號>）
- 使用的 AI：Claude Code（模型：<…>）；是否開隊：否／是（隊友 N 位）
- 主要提示：<一兩句摘要>
- AI 產出：<新增／修改的檔案>
- 人工修改與理由：<改了什麼、為什麼>
- 驗收：<AC／TC 結果、實機型號與 Android 版本>
- 規格問題／待確認：<有則列出>
```

---

## 11. 待查證、待確認與勘誤

### 11.1 技術待查證（到該週先做）

| 項目 | 何時 | 方式 |
|---|---|---|
| TDX 台鐵／高鐵 ODFare 實際路徑與版本 | 第 3 週 | TDX Swagger 實際呼叫 |
| TDX 國道路段 Section／Live／CongestionLevel（Freeway）路徑與欄位 | 第 3 週 | `tdx_api_test.py`、Swagger |
| TDX 公路客運 Route／StopOfRoute／Schedule／ETA／RouteFare（InterCity）路徑、班表是否有抵達時間 | 第 3 週 | 同上 |
| 交流道↔縣市、縣市↔代表車站／轉運站、高鐵轉乘分鐘對照表內容 | 第 6 週 | 依 TDX 資料整理，數值記錄在 devlog |
| TDX 回應欄位：車種代碼、山海線、對號／自行車設施 | 第 3 週 | 同上，存範例 JSON |
| Gemini 模型名稱 | 第 6 週 | Firebase AI Logic 官方文件 |
| compileSdk／targetSdk、各依賴版本 | 第 1 週 | Android Studio 新專案預設，記錄在日誌 |

### 11.2 暫定 UI 項目（實作到時先請實作者確認）

以下內容已寫在規格書，但當初是設計時補上的細節，未經逐條確認：

| 項目 | 規格位置 | 何時確認 |
|---|---|---|
| 間距、圓角、撕線、印章角度等數值 | 5.2 | 第 1 週做 TicketView 前 |
| 字級（各樣式的 sp 數） | 5.2 | 第 1 週 |
| 插圖：空白車票、斷線售票機、空收藏的問號車票 | 5.3 | 第 4、5 週做狀態畫面前 |
| 點下一班主車票：未設提醒→開提醒對話框；已設→開 P4 | FR-NEXT-01 | 第 5 週 |
| 倒數 ≥ 60 分鐘時顯示「還有 X 小時 Y 分」 | FR-NEXT-01 | 第 5 週 |
| AI 解析失敗提示文字「請改用手動查詢」 | FR-AI-05、11.2 | 第 6 週 |
| 從下一班開的確認卡按「修改」→ 切到查詢分頁並填入空白車票 | FR-NEXT-05 | 第 6 週 |
| 國道分頁：會員查看路況後如何「收藏這條路線」（規格寫明待確認） | FR-HWY-06 | 第 6 週 |
| 國道分頁：點比較車票後的行為（例如開啟該運具的查詢結果） | FR-HWY-04 | 第 6 週 |
| 國道路線圖：路段很多時的呈現（全部列出或折疊） | FR-HWY-03 | 第 6 週 |
| 「平常時間」的計算方式（目前以速限換算）是否符合期待 | FR-HWY-02 | 第 6 週 |

### 11.3 規格勘誤

規格書 v1.1 已修正先前列出的兩處勘誤（Entity 數量、架構圖 ViewModel），目前沒有未處理的勘誤。

---

## 附錄 A：`CLAUDE.md`（放專案根目錄）

```markdown
# 歸潮 Hometide（返鄉交通助手）— Claude Code 專案規則

## 專案
Android App，Kotlin + XML Layout + ViewBinding，MVVM，套件 com.group11.hometide，minSdk 26。
後端：worker/ 資料夾的 Cloudflare Worker（TypeScript＋Wrangler），App 只呼叫 Worker，Worker 才呼叫 TDX。
不得使用 Jetpack Compose、Hilt。實作者：阮梓睿（一人以 AI 實作）。

## 文件（需要時才讀，不要一次全讀）
- docs/GUIDE.md：需求總覽（1A）、Worker 規則（3.4）、完成標準（第 5 節）、時程（第 8 節）、提示詞（第 9 節）、暫定 UI 項目（11.2）、Worker API 合約（附錄 E）
- docs/spec/03-functional-requirements.md：FR 與 AC（國道路況 3.12 FR-HWY、國道客運 3.13 FR-BUS）
- docs/spec/04-screens.md：各頁元件、元件 ID、跳轉（國道分頁 4.11）
- docs/spec/05-visual-design.md：色票、字體、動畫 AN-01～16
- docs/spec/06-architecture.md：架構、套件結構、錯誤處理
- docs/spec/08-backend-api.md：Worker 端點、TDX API、快取、CPU 限制對策
- docs/spec/07-data.md、09-ai.md、10-background-components.md
- docs/spec/11-nfr-errors.md（錯誤訊息）、12-tests.md（TC）
- docs/mockups/*.png：畫面依據；docs/mockups/animations.html：動畫依據
- 不確定該讀哪章時，交給 spec-reader 子代理查。

## 硬性規則
1. 架構：Activity/Fragment → ViewModel(StateFlow UiState) → Repository(suspend, Result<T>) → Room／Retrofit(HometideApi → Worker)／Firebase；DI 只用 AppContainer。App 不得直接呼叫 TDX。
2. 套件結構照 docs/spec/06-architecture.md 6.2（App）與 6.5（Worker），不自創結構。
3. 所有 Activity 繼承 BaseActivity；PendingIntent 一律 FLAG_IMMUTABLE。
4. 字串只放 strings.xml（繁體中文）；顏色只用語意色票並提供 values-night；不寫死色碼與中文。
5. 元件 ID 與 docs/spec/04-screens.md 完全一致。
6. 規格與畫面圖沒寫的 UI 細節、或兩者不一致時：停下來用 GUIDE 9.7 格式提問，不得自行決定。
7. 一次一個任務；動手前先列檔案清單與對應 FR/AC/TC，等我說「可以」。
8. 新增依賴前先徵求同意；版本放 gradle/libs.versions.toml。
9. App 內不得出現任何 TDX 金鑰；local.properties 只放 WORKER_BASE_URL。TDX 金鑰只在 Worker Secret（wrangler secret put）與 worker/.dev.vars；不得輸出、提交或寫入任何其他檔案。
10. 不得修改 docs/spec/；發現問題寫進當日 devlog 並告知我。
11. 編譯：./gradlew assembleDebug；Worker：cd worker && npx wrangler dev／deploy；只回報錯誤摘要。
12. 任務完成前請 spec-reviewer 審查，並在 docs/devlog/ 當日檔案追加紀錄（GUIDE 第 10 節格式）。
13. Commit：<feat|fix|ui|test|docs|chore>(<FR編號>): <中文說明>，在 feature/ 分支上進行。
14. 回覆一律使用繁體中文。
```

## 附錄 B：子代理定義（放 `.claude/agents/`）

### B-1 `spec-reader.md`

```markdown
---
name: spec-reader
description: 查詢需求規格書。當需要知道某個功能、畫面、資料表、API 或錯誤訊息的規則，或不確定該讀哪一章時使用。只讀不寫。
tools: Read, Grep, Glob
model: sonnet
---
你是「歸潮 Hometide（返鄉交通助手）」需求規格書的查詢員。規格書在 docs/spec/，依章拆檔；畫面圖在 docs/mockups/。

做法：
1. 先用 Grep 找到相關的 FR 編號、元件 ID、章節標題，再只讀需要的段落。
2. 回答時引用原文重點，並標明檔名與章節（例：03-functional-requirements.md FR-RES-03）。
3. 規格沒寫到的就明說「規格未定義」，不要推測或補完，特別是 UI 細節。
4. 發現規格前後矛盾時，列出兩處原文。
5. 回覆使用繁體中文，簡潔條列。
```

### B-2 `spec-reviewer.md`

```markdown
---
name: spec-reviewer
description: 對照需求規格書審查剛完成的程式碼。每個任務完成、commit 前使用。只讀不改。
tools: Read, Grep, Glob
model: sonnet
---
你是「歸潮 Hometide（返鄉交通助手）」的規格審查員。使用者會告訴你本次任務的 FR 編號與修改過的檔案。

依序檢查並逐項回報「符合／不符合／無法判斷」：
1. 功能：docs/spec/03-functional-requirements.md 中該 FR 的每一條規則與 AC 是否都已實作。
2. 畫面：docs/spec/04-screens.md 的元件、元件 ID、順序、跳轉是否一致；對照 docs/mockups/ 對應圖檔。
3. 架構：是否符合 docs/spec/06-architecture.md（MVVM、AppContainer、Result<T>、UiState、套件位置）；App 是否只呼叫 Worker；Worker 是否符合 docs/spec/08-backend-api.md。
4. 資源：有無寫死中文字串或色碼；values-night 是否有對應色票。
5. 安全：PendingIntent 是否 FLAG_IMMUTABLE；App 程式碼、資源、log 與 wrangler.toml 中有無 TDX 金鑰。
6. 錯誤處理：錯誤類型與訊息是否符合 docs/spec/11-nfr-errors.md。
7. 列出規格沒寫、但程式自行決定的 UI 細節（需要人確認）。

只回報，不修改任何檔案。每個「不符合」附檔名與行號、規格出處、建議修正方式。回覆使用繁體中文。
```

## 附錄 C：`.claude/settings.json`

```json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "0"
  },
  "permissions": {
    "deny": [
      "Read(./local.properties)",
      "Read(./**/local.properties)",
      "Read(./worker/.dev.vars)"
    ]
  }
}
```

- 要開 Agent Teams 時把 `"0"` 改成 `"1"`，用完改回。
- `deny` 讓 Claude Code 無法讀取 `local.properties` 與放 TDX 金鑰的 `worker/.dev.vars`；Gradle、Wrangler 執行時仍會正常讀取。需要 Claude Code 協助設定時，只告訴它鍵名，不要貼值。

## 附錄 D：第一天的操作順序

1. TDX 金鑰已取得；建議到會員中心新增一組新金鑰並刪除曾貼在對話中的那組。
2. Firebase 主控台：Android App 登記套件名 `com.group11.hometide`，下載 `google-services.json`；開啟 Authentication 的電子郵件／密碼。
3. 註冊 Cloudflare 免費帳號；電腦安裝 Node.js LTS。
4. GitHub 建私人 repo `Hometide`，加入組員。
5. Android Studio 建立 Empty Views Activity 專案（Kotlin、Gradle KTS、minSdk 26、套件名同上），推上 repo。
6. 把開工包的 `CLAUDE.md`、`.claude/`、`docs/` 複製到專案根目錄，commit。
7. 在 Android Studio 終端機啟動 Claude Code，貼上 9.1 的第 1 週開工提示詞。
8. 第 1 週任務 1-7 建好 Worker 後，在終端機自己執行 `npx wrangler secret put TDX_CLIENT_ID` 與 `TDX_CLIENT_SECRET` 輸入金鑰（不要讓 Claude Code 代打）。

## 附錄 E：Worker API 合約（初版）

以下為 App 與 Worker 之間的回應格式約定。欄位以 TDX 查證結果為準可微調，但調整時須同時更新本附錄與 App 的 DTO（3.4 第 8 點）。時間一律為當地時間字串 `HH:mm`，日期 `yyyy-MM-dd`，`updatedAt` 為 Unix 毫秒。

### E-1 `GET /api/timetable?operator=TRA&from=1080&to=4220&date=2026-11-07`

```json
{
  "updatedAt": 1762495500000,
  "source": "TDX",
  "data": {
    "operator": "TRA",
    "trips": [
      {
        "tripId": "139",
        "typeCode": "1100", "typeName": "自強",
        "line": 1,
        "departTime": "14:12", "arriveTime": "17:20", "durationMin": 188,
        "arriveEstimated": false,
        "reservedSeat": true, "bikeAllowed": false
      }
    ]
  }
}
```

### E-2 `GET /api/timetable?operator=BUS&from=<站牌代碼>&to=<站牌代碼>&date=…`

```json
{
  "updatedAt": 1762495500000,
  "source": "TDX",
  "data": {
    "operator": "BUS",
    "trips": [
      {
        "tripId": "1610-14:30",
        "typeCode": "1610", "typeName": "統聯",
        "line": null,
        "departTime": "14:30", "arriveTime": "18:40", "durationMin": 250,
        "arriveEstimated": true,
        "reservedSeat": false, "bikeAllowed": false
      }
    ]
  }
}
```

### E-3 `GET /api/live?operator=TRA&from=…&to=…`／`operator=BUS`

```json
{ "updatedAt": 1762495500000, "source": "TDX",
  "data": { "items": [ { "tripId": "143", "delayMin": 5 },
                       { "tripId": "1610-14:30", "etaMin": 25 } ] } }
```

### E-4 `GET /api/freeway/traffic?freeway=1&dir=S&from=<交流道>&to=<交流道>`

```json
{
  "updatedAt": 1762495500000,
  "source": "TDX",
  "data": {
    "freewayName": "國道 1 號", "directionName": "南下",
    "from": "桃園", "to": "永康",
    "estimatedMin": 185, "normalMin": 160, "congestedCount": 3,
    "segments": [
      { "fromIc": "桃園", "toIc": "機場系統", "speedKmh": 98, "level": "SMOOTH", "travelMin": 4 },
      { "fromIc": "機場系統", "toIc": "中壢", "speedKmh": 45, "level": "JAM", "travelMin": 9 },
      { "fromIc": "中壢", "toIc": "內壢", "speedKmh": null, "level": "UNKNOWN", "travelMin": null }
    ]
  }
}
```

`level` 只有四種值：`SMOOTH`（順暢）、`BUSY`（車多）、`JAM`（壅塞）、`UNKNOWN`（無資料）。

### E-5 `GET /api/compare?freeway=1&dir=S&from=…&to=…`

```json
{
  "updatedAt": 1762495500000,
  "source": "TDX",
  "data": {
    "fromCity": "桃園", "toCity": "臺南",
    "drive": { "totalMin": 185 },
    "options": [
      { "operator": "THSR", "label": "高鐵・桃園站", "departTime": "14:31", "arriveTime": "15:36",
        "waitMin": 26, "transferMin": 30, "totalMin": 160, "note": "含轉乘" },
      { "operator": "TRA", "label": "台鐵・自強 139", "departTime": "14:12", "arriveTime": "17:20",
        "waitMin": 7, "transferMin": 0, "totalMin": 195, "note": "7 分後" },
      { "operator": "BUS", "label": "國道客運・統聯 1610", "departTime": "14:30", "arriveTime": "18:40",
        "waitMin": 25, "transferMin": 0, "totalMin": 275, "note": "25 分後" }
    ],
    "fastest": "THSR"
  }
}
```

某運具當天已無班次時，該項 `departTime` 為 `null`、`note` 為「今日已無班次」，不參與 `fastest` 判斷；`fastest` 可能為 `DRIVE`。

### E-6 錯誤回應

```json
{ "error": { "code": "QUOTA", "message": "TDX rate limited" } }
```

## 附錄 F：GitHub Actions 範例（客運路線索引）

`.github/workflows/build-index.yml`。以下為範例，Wrangler 指令旗標以實作當時的官方文件為準。

```yaml
name: build-bus-index
on:
  schedule:
    - cron: "30 19 * * *"   # 台灣時間每天 03:30
  workflow_dispatch:
jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: worker
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: 20
      - run: npm ci
      - run: npx tsx scripts/build-index.ts > bus-index.json
        env:
          TDX_CLIENT_ID: ${{ secrets.TDX_CLIENT_ID }}
          TDX_CLIENT_SECRET: ${{ secrets.TDX_CLIENT_SECRET }}
      - run: npx wrangler kv key put bus-index --path=bus-index.json --binding=BUS_INDEX --remote
        env:
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
```

## 附錄 G：決策紀錄

| 日期 | 決定 | 說明 |
|---|---|---|
| 2026-11-07 | 視覺採 C2 硬式車票風格、16 項動畫 | 首頁 H1＋H3、結果頁 R1＋R3、下一班獨立分頁 |
| 2026-11-09 | 阮梓睿以 AI 主寫全部階段；Pro 方案；Agent Teams 平時關閉 | 報告第 14 章保留四人分工 |
| 2026-11-09 | 架構改為 App → Cloudflare Worker → TDX | 金鑰安全、額度、資料整理三個理由（規格 8.1） |
| 2026-11-09 | 新增國道路況與國道客運，皆為必做，不刪減原需求 | 國道用 TDX 路段路況，不用高公局原始 VD |
| 2026-11-09 | 國道分頁版面 A＋B＋C：大車票 → 回家方式比較 → 路線圖；訪客可看、收藏需登入 | 底部導覽列改五分頁，國道位於下一班與我的路線之間 |
| 2026-11-09 | 國道客運支援 AI、車票夾、下一班、出發提醒；運具用下拉選單；推算抵達標「抵達為預估」 | 國道客運結果頁沿用時間軸 |
| 2026-11-09 | 命名「歸潮 Hometide」，專題名稱「歸潮：返鄉交通助手」 | 套件 `com.group11.hometide` |
