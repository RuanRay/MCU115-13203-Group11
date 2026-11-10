## 11. 非功能需求與錯誤處理

### 11.1 非功能需求

| 編號 | 類別 | 需求 |
|---|---|---|
| NFR-01 | 效能 | 冷啟動到首頁可操作 ≤ 2 秒（中階手機）；查詢結果 ≤ 3 秒（P90） |
| NFR-02 | 效能 | 列表捲動不掉幀，RecyclerView 使用 DiffUtil（ListAdapter） |
| NFR-03 | 可靠性 | 任何網路錯誤不得造成閃退；未處理例外以 CoroutineExceptionHandler 記錄 |
| NFR-04 | 相容性 | Android 8.0（API 26）以上；在 Android 13、14 實機或模擬器完整測試 |
| NFR-05 | 安全 | App（APK）內不含任何 TDX 金鑰，金鑰只存在 Worker Secret 與 GitHub Secrets；金鑰不進版控；Firestore 規則如 7.4；PendingIntent 一律 FLAG_IMMUTABLE；Provider 不對外 |
| NFR-06 | 隱私 | 使用紀錄只記頁面名稱與時間，不記輸入內容；AI 只收到查詢句或回饋文字；首次開啟顯示隱私說明 |
| NFR-07 | 無障礙 | 所有圖示按鈕有 contentDescription；文字對比 ≥ 4.5:1（淺、深色皆是）；支援系統字體放大至 1.3 倍不破版 |
| NFR-08 | 可維護性 | 遵守第 6 章套件結構；字串放 strings.xml；顏色只用語意色票 |
| NFR-09 | 離線 | 無網路時首頁、我的路線、曾查詢過的結果皆可開啟 |
| NFR-10 | 耗電 | 不常駐背景服務；即時資料只在結果頁、國道分頁可見時每 60 秒更新 |
| NFR-11 | 後端 | Worker 回應時間 P90 ≤ 1.5 秒（快取命中 ≤ 300 ms）；單日請求量不超過免費額度的 50% |

### 11.2 錯誤訊息對照

| 錯誤類型 | 情境 | 顯示訊息 | 處理方式 |
|---|---|---|---|
| NetworkError | 無網路、逾時 | 無法連線，請檢查網路後再試 | 有快取則顯示快取＋離線橫幅 |
| AuthError | Worker 回傳 AUTH（TDX Token 更新失敗） | 資料服務暫時無法使用 | 顯示快取；記錄 log |
| QuotaExceeded | Worker 回傳 QUOTA（TDX 429 或 Worker 額度用盡） | 查詢太頻繁，請稍後再試 | 60 秒內只讀快取 |
| NotFound | 無班次 | 這天沒有符合的班次 | 空狀態插圖 |
| ParseError | Worker 或 AI 回應格式錯誤 | 資料格式異常，請稍後再試 | 記錄 log |
| 國道無資料 | 路段沒有即時資料 | 路段顯示「—」，大車票顯示「部分路段無資料」 | 不影響其他路段 |
| Firebase 帳號錯誤 | 帳密錯誤、Email 已註冊 | 見 FR-AUTH-01、02 | 顯示於欄位下方 |
| AI 失敗 | 見 FR-AI-05 | 無法解析這句話，請改用手動查詢 | 不顯示確認卡 |
