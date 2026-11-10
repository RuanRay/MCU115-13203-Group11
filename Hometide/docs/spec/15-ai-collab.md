## 15. AI 協作開發指引

本章說明如何把本規格交給 AI（如 Claude、Gemini、Copilot）產生程式碼，以減少反覆溝通。

### 15.1 原則

1. **一次一個模組：** 每次只貼上該功能的需求編號（第 3 章）、畫面規格（第 4 章）、相關資料表（第 7 章）與 API（第 8、9 章），不要一次丟整份文件。
2. **固定專案慣例：** 每次對話開頭都附上「共同約定」（15.2），確保套件、命名、架構一致。
3. **要求可驗收：** 請 AI 對照需求的 AC 與第 12 章測試案例，說明如何驗證。
4. **人工把關：** AI 產生的程式碼必須理解、執行並通過測試後才能提交；開發日誌記錄提問內容、採用與修改之處及理由。

### 15.2 共同約定（每次貼給 AI）

```
專案：歸潮 Hometide（返鄉交通助手；Android，Kotlin，XML Layout，MVVM，不使用 Compose 與 Hilt）
後端：App 只呼叫自建 Cloudflare Worker（HometideApi），App 內不得出現 TDX 金鑰或直接呼叫 TDX
視覺：硬式車票風格；車票用 ui/widget/TicketView、印章用 StampView；顏色只用規格書 5.1 色票，字體依 5.2；動畫依 5.4 的 AN 編號
套件：com.group11.hometide，結構依規格書 6.2 節
架構：Activity/Fragment → ViewModel(StateFlow UiState) → Repository(suspend, Result<T>) → Room/Retrofit/Firebase
依賴注入：AppContainer 手動建立單例
字串放 strings.xml（繁體中文），顏色只用 colors.xml 語意色票（含 values-night）
所有 Activity 繼承 BaseActivity；minSdk 26；PendingIntent 一律 FLAG_IMMUTABLE
請產生完整可編譯的檔案，並說明要放在哪個路徑、需要新增哪些 Gradle 依賴
```

### 15.3 提示詞範本

```
【共同約定】（貼上 15.2）
【本次任務】實作 FR-RES-01～03：查詢結果頁 ResultActivity 與 TrainAdapter
【需求內容】（貼上規格書 3.4 節對應條目）
【畫面規格】（貼上規格書 4.5 節）
【資料】timetable_cache、fare_cache 的 Entity（貼上 7.1.4、7.1.5）
【API】（貼上 8.3 節 Worker 端點與 8.7 轉換規則的相關內容）
【驗收】需通過 TC-12、TC-13、TC-15
請先列出要新增或修改的檔案清單，再逐一給出完整程式碼。
```

### 15.4 建議的實作順序

1. 專案骨架：Gradle 依賴、AppContainer、BaseActivity、主題、色票與字體、TicketView／StampView、9 頁空版面；Worker 專案初始化。
2. 資料層：Room 全部 Entity 與 DAO、DataStore、StationProvider。
3. Worker：Token、車站／站牌、時刻表、票價、即時、快取；App 端 HometideApi 與 Room 快取。
4. 帳號：登入、註冊、訪客、自動登入、登出。
5. 查詢與結果：P3、P4、票價、即時、排序。
6. 收藏與提醒：P5、下一班、AlarmManager、Receivers、通知（含國道客運）。
6.5 國道分頁：路況、路線圖、回家方式比較。
7. 使用紀錄與 P6 統計。
8. AI：確認卡、回饋分類、測試集。
9. 回饋與 SyncService、Firestore。
10. 動畫、深色模式、無障礙與整體測試。
