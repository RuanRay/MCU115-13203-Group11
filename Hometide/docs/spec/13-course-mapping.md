## 13. 課程要求對照

| 課程要求 | 本 App 實現 | 對應規格 |
|---|---|---|
| 至少 5 個頁面，每頁有互動元件 | 9 個 Activity（另有 AI 確認卡、訪客提示） | 第 4 章 |
| 網路連線：搜尋資料 | 經自建 Worker 取得 TDX 台鐵、高鐵、國道客運的時刻、票價、即時資料與國道路況 | FR-SRCH、FR-RES、FR-BUS、FR-HWY、第 8 章 |
| 網路連線：儲存資料 | 結果寫入快取；回饋與紀錄上傳 Firestore | FR-OFF-01、FR-SYNC |
| 資料庫 | Room 13 張表 | 第 7 章 |
| 註冊、登入、登出 | Firebase Authentication | FR-AUTH |
| 使用紀錄：開啟與關閉時間 | ProcessLifecycleOwner | FR-USG-01 |
| 使用紀錄：每頁停留時間 | BaseActivity | FR-USG-02 |
| 使用者回饋機制 | 評分、類型、內容＋AI 分類 | FR-FB |
| Background Service | SyncService（前景服務） | 10.1 |
| Broadcast Receiver | ReminderReceiver、BootReceiver | 10.2 |
| Content Provider | StationProvider | 10.3 |
| Text Input | 帳密、註冊、AI 查詢句、站名、回饋 | 第 4 章 |
| Button | 各頁主要操作 | 第 4 章 |
| Dropdown List Menu | 預設運具、運具、排序、外觀、統計區間、回饋類型、國道路線（國道、方向、交流道） | 第 4 章 |
| Animation | 16 項動畫（AN-01～16），含售票機出票、撕票、蓋章、倒數翻牌、轉場 | 5.4 |
| AI 參與（老師要求） | 自然語言查詢、回饋分類；開發過程 AI 協作紀錄 | 第 9、15 章 |
