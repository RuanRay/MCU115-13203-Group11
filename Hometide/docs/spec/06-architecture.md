## 6. 系統架構與程式結構

### 6.1 架構

系統分成兩部分：**Android App** 與**自建後端 Worker**（Cloudflare Workers）。App 不直接呼叫 TDX，所有交通資料都經過 Worker：Worker 保管 TDX 金鑰、統一換 Token、快取資料並整理成精簡 JSON（第 8 章）。

App 採 MVVM：View（Activity／Fragment + XML）只負責顯示與事件；ViewModel 以 `StateFlow`／`LiveData` 提供畫面狀態；Repository 決定資料來源（Worker、快取、資料庫）。為降低學習成本，不使用 Hilt，改以 `AppContainer`（手動依賴注入）在 Application 中建立單例。

```
View 層        Activity / Fragment（XML Layout，繼承 BaseActivity）
                  │ 觀察 UiState、呼叫方法
ViewModel 層   SearchViewModel、ResultViewModel、MyRoutesViewModel、
               ProfileViewModel、FeedbackViewModel、AuthViewModel、
               NextTrainViewModel、HighwayViewModel
                  │
Repository 層  TransitRepository │ AuthRepository │ FavoriteRepository
               ReminderRepository │ UsageRepository │ FeedbackRepository │ AiRepository
               HighwayRepository
                  │
資料來源       HometideApi（Retrofit → Worker）│ Room（AppDatabase）│ DataStore
               Firebase Auth / Firestore / AI Logic │ StationProvider
背景元件       SyncService │ ReminderReceiver │ BootReceiver │ ReminderScheduler
```

```
Android App ──HTTPS──▶ Cloudflare Worker（/api/*）──▶ TDX（Token、台鐵、高鐵、公路客運、國道路況）
                         │  Cache API：依資料類型快取（第 8.5 節）
                         │  Secrets：TDX_CLIENT_ID、TDX_CLIENT_SECRET
                         └─ 靜態對照表：客運路線索引、交流道↔縣市、縣市↔代表車站（每日排程產生）
Android App ──────────▶ Firebase（Auth、Firestore、AI Logic）
```

### 6.2 套件結構

```
com.group11.hometide
├── HometideApp.kt            Application：建立 AppContainer、註冊 ProcessLifecycleOwner
├── AppContainer.kt            手動 DI：Retrofit、Room、各 Repository
├── data
│   ├── local
│   │   ├── AppDatabase.kt      Room Database（version 1）
│   │   ├── entity/             10 個 Entity（第 7 章）
│   │   ├── dao/                StationDao、TimetableDao、FavoriteDao …
│   │   └── SettingsStore.kt    DataStore 包裝
│   ├── remote
│   │   ├── HometideApi.kt      Retrofit 介面，只呼叫自建 Worker（第 8.3 節）
│   │   └── dto/                Worker 回應的資料類別
│   ├── ai
│   │   └── GeminiClient.kt     Firebase AI Logic 包裝（第 9 章）
│   └── repository/             8 個 Repository（新增 HighwayRepository）
├── provider
│   └── StationProvider.kt      ContentProvider（第 10.3 節）
├── service
│   └── SyncService.kt          前景服務（第 10.1 節）
├── receiver
│   ├── ReminderReceiver.kt
│   └── BootReceiver.kt
├── reminder
│   └── ReminderScheduler.kt    AlarmManager 包裝
├── usage
│   └── UsageTracker.kt         Session 與頁面紀錄
├── ui
│   ├── base/BaseActivity.kt
│   ├── auth/                   LoginActivity、RegisterActivity、LoginPromptDialog
│   ├── search/                 SearchActivity、AiConfirmBottomSheet、FavoriteTicketAdapter、RecentAdapter
│   ├── next/                   NextTrainActivity、UpcomingAdapter
│   ├── highway/                HighwayActivity、CompareAdapter、RoadStripAdapter
│   ├── result/                 ResultActivity、TimelineAdapter、NowLineDecoration、TrainRefreshIndicator
│   ├── routes/                 MyRoutesActivity、FavoritesFragment、RemindersFragment
│   ├── widget/                 TicketView、StampView、DateStampView、TicketDispenserView（出票動畫）、TearAnimator（撕票動畫）
│   ├── profile/                ProfileActivity
│   └── feedback/               FeedbackActivity
└── util                        DateUtils、StationNameNormalizer、NetworkMonitor、Result 封裝
```

### 6.3 執行緒與錯誤處理慣例

- 網路與資料庫一律在 `viewModelScope` 的協程中執行，Repository 函式為 `suspend`，IO 工作用 `Dispatchers.IO`。
- Repository 回傳 `Result<T>`（成功／失敗＋錯誤類型），ViewModel 轉為 `UiState`（Loading／Success／Empty／Error／Offline）。
- 錯誤類型統一為：`NetworkError`、`AuthError`、`QuotaExceeded`、`NotFound`、`ParseError`、`Unknown`，對應訊息見第 11 章。

### 6.4 主要依賴（版本以實作當時穩定版為準）

| 套件 | 用途 |
|---|---|
| androidx.core-ktx、appcompat、activity-ktx、fragment-ktx | 基礎 |
| com.google.android.material | Material Components（含 BottomSheet、Chip） |
| androidx.lifecycle（viewmodel-ktx、livedata-ktx、process、runtime-ktx） | MVVM、ProcessLifecycleOwner |
| androidx.room（runtime、ktx、compiler 使用 KSP） | 本地資料庫 |
| androidx.datastore:datastore-preferences | 設定與登入狀態 |
| androidx.swiperefreshlayout、viewpager2 | 下拉重新整理、分頁 |
| com.squareup.retrofit2（retrofit、converter-gson） | 呼叫自建 Worker |
| com.squareup.okhttp3（okhttp、logging-interceptor） | HTTP、除錯紀錄 |
| org.jetbrains.kotlinx:kotlinx-coroutines-android | 協程 |
| Firebase BoM：firebase-auth、firebase-firestore、firebase-ai、firebase-appcheck-playintegrity | 帳號、雲端資料、Gemini、防濫用 |

### 6.5 Worker 專案結構

與 App 放在同一個 Git repo 的 `worker/` 資料夾，使用 TypeScript 與 Wrangler 部署。

```
worker/
├── wrangler.toml            名稱 hometide-api、Cron Trigger、KV 綁定
├── src/
│   ├── index.ts             路由：/api/*（第 8.3 節）
│   ├── tdx.ts               取得與快取 Token、呼叫 TDX、統一錯誤
│   ├── cache.ts             Cache API 包裝（TTL 依第 8.5 節）
│   ├── rail.ts              台鐵、高鐵：車站、時刻、票價、即時
│   ├── bus.ts               國道客運：站牌、班次、預估到站、票價
│   ├── freeway.ts           國道：路段、即時路況、預估時間
│   ├── compare.ts           回家方式比較
│   └── static/              對照表 JSON（交流道↔縣市、縣市↔代表車站與轉乘分鐘）
├── scripts/build-index.ts   產生客運路線索引（GitHub Actions 每日執行，寫入 KV）
└── .dev.vars                本機開發用金鑰（不進 Git）
```
