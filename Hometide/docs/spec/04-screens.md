## 4. 畫面規格

本章逐頁列出版面由上到下的元件、元件 ID（XML `android:id`）、類型與行為。整體採「硬式車票」風格（第 5 章），共 9 個 Activity，加上 AI 確認卡（Bottom Sheet）與訪客提示（Dialog）。頁面代號：P1 登入、P2 註冊、P3 查詢（首頁）、P4 查詢結果、P5 我的路線、P6 個人、P7 意見回饋；「下一班」「國道」兩個分頁不另編號，文中一律稱「下一班」「國道」。報告第 6 節的畫面圖以中文名稱標示，對應關係同上。元件 ID 命名規則：類型前綴＋用途（`et` 輸入框、`btn` 按鈕、`sp` 下拉、`rv` 列表、`tv` 文字、`iv` 圖片、`chip` 晶片、`cb` 核取方塊、`tk` 車票元件）。

### 4.1 共用元件

- **BaseActivity：** 所有頁面父類別，處理頁面停留紀錄（FR-USG-02）、共用頂部標題列、Snackbar 工具方法、動畫開關判斷（系統「移除動畫」時停用所有自訂動畫）。
- **底部導覽列 `bottomNav`：** 出現在查詢、下一班、國道、我的路線、個人五頁，五個項目：查詢（search）、下一班（schedule）、國道（road）、我的路線（star）、個人（person）。目前項目以紙色底＋藏青字標示；切換時左右滑動（AN-02），不保留返回堆疊。
- **車票元件 `TicketView`（自訂 View）：** 本體＋存根兩區，存根寬 56–92dp，中間虛線撕線，撕線上下兩端各有半圓缺口；樣式分「主票」（藏青底、米色字）、「紙票」（車票紙色底、墨黑字）、「空白票」（卡片底＋框線）。全 App 的路線、班次、提醒、AI 結果都用這個元件。
- **印章元件 `StampView`（自訂 View）：** 圓形雙線框＋宋體文字，旋轉 −12°；顏色有紅（已開出、會員限定、已收件、已預約）、灰（已使用）、綠（一致）、芥末黃（站名已驗證）；蓋章時播放 AN-07 同款的蓋章動畫。
- **日期章 `DateStampView`：** 紅色方框民國年日期（例：115.11.06），用於頁首與結果頁。
- **登入提示對話框 `LoginPromptDialog`：** 一張車票造型的對話框，中央紅色「會員限定」章，標題「登入後就能收藏」，說明兩行，撕線下方「稍後再說」「去登入」兩個按鈕（FR-AUTH-06）。
- **Snackbar：** 顯示於底部導覽列上方，3 秒，藏青底。
- **骨架車票 `item_ticket_skeleton`：** 載入中使用，閃光動畫 AN-11。

### 4.2 P1 登入頁（LoginActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 插圖：疊放的兩張硬式車票＋紅色日期戳章 | `ivHero` | ImageView（VectorDrawable） | 淺色、深色各一版 |
| 2 | 標語「回家的車，一鍵就到」＋「歸潮 HOMETIDE・返鄉交通助手」 | `tvSlogan` | TextView | 宋體 |
| 3 | Email | `etEmail` | TextInputLayout | inputType=textEmailAddress |
| 4 | 密碼 | `etPassword` | TextInputLayout | 密碼顯示切換圖示 |
| 5 | 登　入 | `btnLogin` | MaterialButton（主色） | FR-AUTH-02；處理中顯示三點跳動 |
| 6 | 先不登入，直接查詢 | `btnGuest` | MaterialButton（外框） | FR-AUTH-04 |
| 7 | 還沒有帳號？註冊 | `tvRegister` | TextView（可點） | → P2 |

- 啟動畫面：App 冷啟動時先播放 AN-01（售票機出票），再淡入本頁或首頁。

### 4.3 P2 註冊頁（RegisterActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 返回＋標題「建立帳號」 | `toolbar` | MaterialToolbar | 返回 → P1 |
| 2 | 註冊資料（空白票外框，內含以下欄位） | `tkForm` | TicketView（空白票） | — |
| 3 | 暱稱 | `etNickname` | TextInputLayout | 1–20 字 |
| 4 | Email | `etEmail` | TextInputLayout | Email 格式 |
| 5 | 密碼（至少 6 碼） | `etPassword` | TextInputLayout | 6–32 字元 |
| 6 | 確認密碼 | `etConfirm` | TextInputLayout | 不一致時紅框＋抖動（AN-16）＋下方紅字 |
| 7 | 預設運具 | `spOperator` | Spinner | 台鐵、高鐵、國道客運 |
| 8 | 註冊並登入 | `btnRegister` | MaterialButton | FR-AUTH-01 |

### 4.4 P3 查詢（SearchActivity，首頁）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 問候語＋標題 | `tvGreeting`、`tvTitle` | TextView | FR-SRCH-05 |
| 2 | 民國年日期章 | `vDateStamp` | DateStampView | 今天日期 |
| 3 | 乘車申請單・用一句話查詢 | `etAiQuery`、`btnParse` | EditText＋MaterialButton | 藏青粗框；FR-AI-01 |
| 4 | 範例句晶片 ×3 | `chipGroupExamples` | ChipGroup | FR-SRCH-06 |
| 5 | 「我的車票夾」標題＋「滑動看更多 ›」 | `tvFavTitle` | TextView | — |
| 6 | 車票夾（橫向） | `rvFavorites` | RecyclerView（水平，item_ticket_route） | FR-SRCH-04；點存根播放 AN-05 |
| 7 | 空白車票・手動查詢 | `tkManual` | TicketView（空白票） | 內含以下 8–11；存根「查詢班次」為送出按鈕 |
| 8 | 運具 | `spOperator` | Spinner | 台鐵／高鐵／國道客運；切換時清空不屬於該運具的站名（FR-BUS-01） |
| 9 | 起站／迄站 | `actvOrigin`、`actvDest` | AutoCompleteTextView | FR-SRCH-02；宋體大字 |
| 10 | 交換起訖站 | `btnSwap` | ImageButton | AN-09 |
| 11 | 乘車日期 | `btnDate` | 文字按鈕「乘車日期 11/7（六）▾」 | DatePickerDialog |
| 12 | 最近查詢 ×5 | `rvRecent` | RecyclerView | FR-SRCH-07 |
| 13 | 底部導覽列 | `bottomNav` | BottomNavigationView | 目前項目：查詢 |

- **AI 確認卡（`AiConfirmBottomSheet`）：** 頂端出票口、`tkAiPreview`（主票樣式預覽車票）、`vVerifyStamp`（芥末黃「站名已驗證」章）、`tvOriginal`（原句）、`tvError`（驗證失敗時紅字）、`btnEdit`（修改）、`btnConfirm`（查詢班次）。播放 AN-04。
- **狀態：** 訪客（車票夾顯示空白提示票）、AI 解析中（AN-15）、AI 失敗（Snackbar）。

### 4.5 下一班（NextTrainActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 「下一班回家」＋標題「還有 N 分鐘」 | `tvNextTitle` | TextView | FR-NEXT-01 |
| 2 | 民國年日期章 | `vDateStamp` | DateStampView | — |
| 3 | 路線切換晶片（橫向） | `chipGroupRoutes` | ChipGroup（可橫向捲動） | FR-NEXT-02 |
| 4 | 主車票 | `tkNextMain` | TicketView（主票，大尺寸） | 存根特大字倒數（AN-13） |
| 5 | 「接下來的班次」＋小車票 ×3 | `rvUpcoming` | RecyclerView（item_ticket_small） | FR-NEXT-04 |
| 6 | 查看今天全部班次 › | `tvSeeAll` | TextView（可點） | → P4 |
| 7 | 浮動一句話查詢列 | `layoutFloatingAi` | 圓角輸入列＋解析按鈕 | FR-NEXT-05；固定在導覽列上方 |
| 8 | 底部導覽列 | `bottomNav` | BottomNavigationView | 目前項目：下一班 |

- **空狀態 `layoutNextEmpty`：** 問號車票「？ → 家」、說明文字、`btnLogin`「登入／註冊」、`btnGoSearch`「先去查詢」（FR-NEXT-06）。

### 4.6 P4 查詢結果（ResultActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 返回＋運具＋路線標題（宋體） | `toolbar`、`tvRoute` | MaterialToolbar | 由車票夾進入時以共享元素展開（AN-03） |
| 2 | 收藏 ♥ | `btnFavorite` | ImageButton | FR-RES-05；AN-06 |
| 3 | 離線橫幅 | `tvOfflineBanner` | TextView | FR-OFF-01，由上滑下（AN-16） |
| 4 | 民國年日期章（含星期）、班次數、時段晶片 | `vDateStamp`、`tvCount`、`chipTimeRange` | DateStampView、TextView、Chip | 時段晶片可關閉 |
| 5 | 排序／篩選 | `spSort` | Spinner | FR-RES-04 |
| 6 | 時間軸列表 | `rvTimeline` | RecyclerView（item_timeline_train） | FR-RES-01～03 |
| 7 | 「現在 HH:mm」紅線 | `vNowLine` | ItemDecoration | 每 60 秒移動（AN-14） |
| 8 | 下拉重新整理 | `swipeRefresh` | SwipeRefreshLayout（自訂小火車指示器） | AN-12 |
| 9 | 空狀態／錯誤狀態 | `layoutEmpty`、`btnRetry` | include | FR-RES-06 |

**時間軸項目 `item_timeline_train`：** `tvHour`（左側發車時刻，宋體）、`vNode`（狀態色圓點）、`tkTrain`（TicketView 紙票）：`tvTrainMeta`（自強 139 次・山線・3:08）、`tvTime`（14:12 → 17:20）、`chipStatus`、`chipFacilities`、存根 `tvFare`（$739）＋「全票」＋`btnRemind`（🔔 提醒）。已開出的項目另有 `vDepartedStamp`。

- **國道客運：** 運具為國道客運時，車票本體改顯示「業者 路線號」、約略行車時間、狀態（N 分後／依班表／已開出）與「抵達為預估」標籤（FR-BUS-04）；版面示意見報告第 6 節「國道客運結果」。

### 4.7 P5 我的路線（MyRoutesActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 標題「我的路線」＋「＋ 新增」 | `btnAdd` | MaterialButton（文字） | → P3 並聚焦手動查詢 |
| 2 | 分頁「車票夾 N」「出發提醒 N」 | `tabLayout` | TabLayout（宋體、芥末黃底線） | — |
| 3 | 內容 | `viewPager` | ViewPager2 + 兩個 Fragment | — |
| 4 | 車票夾項目 | `item_ticket_route` | TicketView | FR-FAV-01～04；左滑露出紅色「刪除」（AN-08）、長按 ≡ 拖曳 |
| 5 | 出發提醒項目 | `item_ticket_reminder` | TicketView＋StampView | FR-FAV-05；「已預約」／「已使用」章；SCHEDULED 可左滑取消 |
| 6 | 通知權限提示列 | `tvPermissionBanner` | TextView（芥末黃底） | FR-RMD-05，含「前往設定 ›」 |
| 7 | 底部導覽列 | `bottomNav` | BottomNavigationView | 目前項目：我的路線 |

- **狀態：** 訪客（問號車票＋「登入後即可使用我的路線」＋登入按鈕）、空清單（空白票「還沒有收藏的路線」）。

### 4.8 P6 個人（ProfileActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 乘車會員證 | `cardMember` | CardView（藏青底） | 「HOMETIDE 乘車會員證」、暱稱（宋體）、Email、預設運具；不顯示加入日期；訪客顯示「登入／註冊」 |
| 2 | 「我的乘車紀錄」＋統計區間 | `spRange` | Spinner | 今天／本週（預設）／全部 |
| 3 | 開啟次數、總使用時間 | `tvOpenCount`、`tvTotalTime` | 兩張小紙票 | FR-USG-04 |
| 4 | 各頁停留時間 | `layoutPageBars` | LinearLayout + ProgressBar | 前 5 名 |
| 5 | 外觀 | `spTheme` | Spinner | 跟隨系統／淺色／深色（FR-SET-01） |
| 6 | 意見回饋 | `btnFeedback` | MaterialButton（外框） | → P7 |
| 7 | 登出 | `btnLogout` | TextView（可點，灰字） | FR-AUTH-05，確認對話框 |
| 8 | 底部導覽列 | `bottomNav` | BottomNavigationView | 目前項目：個人 |

### 4.9 P7 意見回饋（FeedbackActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 返回＋標題「意見回饋」 | `toolbar` | MaterialToolbar | — |
| 2 | 這次用起來如何？評分 | `ratingBar` | RatingBar（芥末黃星） | 1–5，必填 |
| 3 | 回饋類型 | `spCategory` | Spinner | 4 個選項 |
| 4 | 內容（橫線信紙底） | `etContent` | TextInputLayout（多行，計數器 500） | 必填 |
| 5 | 附上我的使用統計 | `cbAttachUsage` | CheckBox | 預設勾選 |
| 6 | 送　出 | `btnSubmit` | MaterialButton | 必填未完成時停用 |
| 7 | 送出成功區 | `layoutSuccess` | ViewFlipper 第二頁 | 「已收件」郵戳（AN-10）、「謝謝你的回饋！」、AI 結果車票 `tkAiResult`（分類、情緒、摘要；存根「一致」章）、`btnBack`「返回個人頁」 |

### 4.10 頁面跳轉總表

| 起點 | 操作 | 終點 | 備註 |
|---|---|---|---|
| 啟動 | 播放 AN-01 後：已登入 | 查詢（P3） | FR-AUTH-03 |
| 啟動 | 播放 AN-01 後：未登入 | 登入（P1） | — |
| P1 | 登入成功／先不登入 | P3 | 清除堆疊 |
| P1 | 點註冊 | P2 | — |
| P2 | 註冊成功 | P3 | 自動登入、清除堆疊 |
| P3 | 點車票夾存根 | P4 | AN-05 撕票 → AN-03 展開 |
| P3 | 手動查詢「查詢班次」／點最近查詢 | P4 | — |
| P3 | 解析 → 確認卡「查詢班次」 | P4 | 帶時段篩選 |
| P3 | 確認卡「修改」 | P3 | 填入表單並高亮 |
| P3 | 頭像 | P6 | — |
| 下一班 | 點小車票／查看今天全部班次 | P4 | — |
| P3 | 運具選「國道客運」→「查詢班次」 | P4（國道客運版本） | FR-BUS-04 |
| 國道 | 訪客或尚未收藏 | 國道（空白車票・選擇國道路線） | FR-HWY-06 |
| 國道（空白車票） | 選好路線按「查看路況」 | 國道（同頁顯示大車票、比較、路線圖） | 不儲存 |
| 國道 | 訪客或會員按收藏 | 登入提示／收藏 | FR-HWY-06 |
| P4 | 訪客按 ♥／提醒 → 去登入 | P1 → 原 P4 | registerForActivityResult |
| P5 | 點車票夾項目 | P4 | 今天日期 |
| 通知 | 點擊出發提醒 | P5（出發提醒分頁） | PendingIntent |
| P6 | 意見回饋 | P7 | — |
| P6 | 登出 | P1 | 清除堆疊 |
| P7 | 返回個人頁 | P6 | finish() |
| 查詢／下一班／國道／我的路線／個人 | 底部導覽列 | 互相切換 | AN-02，不保留堆疊 |

### 4.11 國道（HighwayActivity）

| 順序 | 元件 | ID | 類型 | 行為／規則 |
|---|---|---|---|---|
| 1 | 「開車回家・國道路況」＋標題「比平常多 N 分鐘」 | `tvHwyTitle` | TextView | FR-HWY-02 |
| 2 | 民國年日期章 | `vDateStamp` | DateStampView | — |
| 3 | 開車路線晶片（橫向）＋「＋ 新增」 | `chipGroupDriveRoutes` | ChipGroup（可橫向捲動） | FR-HWY-01 |
| 4 | 預估行車時間大車票 | `tkHwyMain` | TicketView（主票） | FR-HWY-02；存根「預估 H:mm」「+N 分」 |
| 5 | 「回家方式比較・起 → 迄」＋「含等車」 | `tvCompareTitle` | TextView | FR-HWY-04 |
| 6 | 比較車票 ×3（高鐵、台鐵、國道客運） | `rvCompare` | RecyclerView（item_ticket_compare） | 最快者為主票樣式＋「最快」 |
| 7 | 「沿路路況」＋圖例 | `tvRoadTitle`、`layoutLegend` | TextView | 順暢／車多／壅塞 |
| 8 | 路線圖 | `rvRoadStrip` | RecyclerView（item_road_node、item_road_segment），外框為空白票 | FR-HWY-03 |
| 9 | 底部導覽列 | `bottomNav` | BottomNavigationView | 目前項目：國道 |

- **空白車票・選擇國道路線 `tkHwyForm`（FR-HWY-06）：** `spFreeway`（國道）、`spDirection`（方向）、`spEntryIc`（上路交流道）、`spExitIc`（下路交流道）四個 Spinner；存根 `btnShowTraffic`「查看路況」；訪客另顯示提示卡 `cardLoginHint`「登入後可以收藏開車路線」。
- **狀態：** 載入中（骨架車票 AN-11）、離線（芥末黃橫幅＋最後更新時間）、無資料（路段顯示灰色與「—」）。
- 版面示意見報告第 6 節「國道」三張畫面（會員、深色、訪客／未收藏）。
