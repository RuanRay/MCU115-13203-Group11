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
