## 7. 資料設計

### 7.1 Room 資料表

所有時間欄位為 INTEGER（Unix 毫秒）；`uid` 可為 NULL 表示訪客。資料庫名稱 `homebound.db`。

#### 7.1.1 user_profile

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| uid | TEXT | PK | Firebase uid |
| email | TEXT | NOT NULL | — |
| nickname | TEXT | NOT NULL | 1–20 字 |
| default_operator | TEXT | NOT NULL，TRA／THSR | 預設運具 |
| created_at | INTEGER | NOT NULL | 註冊時間 |

#### 7.1.2 station

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| operator | TEXT | PK（複合） | TRA／THSR／BUS |
| station_id | TEXT | PK（複合） | TDX 車站代碼；BUS 為 Worker 去重後的站牌代碼 |
| name_zh | TEXT | NOT NULL，INDEX | 正規化後中文站名（臺） |
| name_en | TEXT | — | 英文站名 |
| sort_key | INTEGER | — | 顯示排序 |
| updated_at | INTEGER | NOT NULL | 最後下載時間 |

#### 7.1.3 search_history

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NULL | — |
| operator、origin_id、dest_id | TEXT | NOT NULL | 查詢條件 |
| travel_date | TEXT | NOT NULL | yyyy-MM-dd |
| source | TEXT | NOT NULL | MANUAL／FAVORITE／AI |
| searched_at | INTEGER | NOT NULL | — |

#### 7.1.4 timetable_cache

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| operator、origin_id、dest_id、travel_date | TEXT | NOT NULL，複合 INDEX | 查詢條件 |
| train_no | TEXT | NOT NULL | 車次；BUS 為「路線號-班次序」 |
| train_type_code、train_type_name | TEXT | — | 車種代碼與名稱；BUS 為路線號與業者名稱 |
| trip_line | INTEGER | NULL | 台鐵：0 不經山海線、1 山線、2 海線（以 TDX 定義為準） |
| depart_time、arrive_time | TEXT | NOT NULL | HH:mm |
| duration_min | INTEGER | NOT NULL | 行駛分鐘 |
| reserved_seat | INTEGER | NOT NULL | 1 對號、0 非對號 |
| bike_allowed | INTEGER | NOT NULL | 1 可帶自行車 |
| arrive_estimated | INTEGER | NOT NULL 預設 0 | 1＝抵達時間為推算（BUS） |
| fetched_at | INTEGER | NOT NULL | 超過 3 天刪除 |

#### 7.1.5 fare_cache

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| operator、origin_id、dest_id、train_type_code | TEXT | PK（複合） | — |
| adult_price | INTEGER | NOT NULL | 全票（新臺幣元） |
| fetched_at | INTEGER | NOT NULL | 超過 7 天重抓 |

#### 7.1.6 favorite_route

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NOT NULL | — |
| operator、origin_id、dest_id | TEXT | NOT NULL，UNIQUE(uid, operator, origin_id, dest_id) | — |
| label | TEXT | NOT NULL | 預設「起站 → 迄站」，可改名 |
| sort_order | INTEGER | NOT NULL | 排序 |
| created_at | INTEGER | NOT NULL | — |

#### 7.1.7 trip_reminder

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | 兼作 PendingIntent requestCode |
| uid | TEXT | NOT NULL | — |
| operator、train_no、train_type_name | TEXT | NOT NULL | — |
| origin_name、dest_name | TEXT | NOT NULL | 通知文字直接使用 |
| depart_at | INTEGER | NOT NULL | 發車時間 |
| remind_before_min | INTEGER | NOT NULL，15／30／60 | — |
| trigger_at | INTEGER | NOT NULL | depart_at − 提前分鐘 |
| status | TEXT | NOT NULL | SCHEDULED／FIRED／CANCELLED |

#### 7.1.8 app_session

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NULL | — |
| device_id | TEXT | NOT NULL | 安裝時產生的 UUID（匿名） |
| start_at | INTEGER | NOT NULL | — |
| end_at | INTEGER | NULL | — |
| duration_ms | INTEGER | NOT NULL 預設 0 | — |
| synced | INTEGER | NOT NULL 預設 0 | — |

#### 7.1.9 page_visit

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| session_id | INTEGER | FK → app_session.id，INDEX | — |
| uid | TEXT | NULL | — |
| page_name | TEXT | NOT NULL | Activity 類別名稱 |
| enter_at、leave_at | INTEGER | NOT NULL | — |
| duration_ms | INTEGER | NOT NULL | — |
| synced | INTEGER | NOT NULL 預設 0 | — |

#### 7.1.10 feedback

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NULL | — |
| device_id | TEXT | NOT NULL | — |
| rating | INTEGER | NOT NULL，1–5 | — |
| category | TEXT | NOT NULL | SUGGESTION／BUG／DATA_ERROR／OTHER |
| content | TEXT | NOT NULL | ≤ 500 字 |
| attach_usage | INTEGER | NOT NULL | — |
| ai_category | TEXT | NULL | 同 category 代碼 |
| ai_sentiment | TEXT | NULL | POSITIVE／NEUTRAL／NEGATIVE |
| ai_summary | TEXT | NULL | ≤ 30 字 |
| ai_status | TEXT | NOT NULL | PENDING／DONE／FAILED |
| app_version | TEXT | NOT NULL | BuildConfig.VERSION_NAME |
| created_at | INTEGER | NOT NULL | — |
| synced | INTEGER | NOT NULL 預設 0 | — |

#### 7.1.11 ai_query_log

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NULL | — |
| input_text | TEXT | NOT NULL | 使用者原句 |
| parsed_json | TEXT | NULL | 模型回傳 JSON |
| result | TEXT | NOT NULL | SUCCESS／STATION_NOT_FOUND／INVALID_DATE／PARSE_ERROR／TIMEOUT／NETWORK_ERROR／RATE_LIMITED |
| user_edited | INTEGER | NOT NULL 預設 0 | 確認卡後是否按「修改」 |
| latency_ms | INTEGER | NOT NULL | — |
| created_at | INTEGER | NOT NULL | — |

#### 7.1.12 drive_route

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| id | INTEGER | PK 自動遞增 | — |
| uid | TEXT | NOT NULL | — |
| freeway_id、direction | TEXT | NOT NULL | 例：1、S（南下） |
| entry_ic、exit_ic | TEXT | NOT NULL，UNIQUE(uid, freeway_id, direction, entry_ic, exit_ic) | 交流道代碼 |
| label | TEXT | NOT NULL | 例：「開車回家」 |
| sort_order | INTEGER | NOT NULL | 排序 |
| created_at | INTEGER | NOT NULL | — |

#### 7.1.13 highway_cache

| 欄位 | 型別 | 限制 | 說明 |
|---|---|---|---|
| route_key | TEXT | PK | `freeway_id|direction|entry_ic|exit_ic` |
| payload_json | TEXT | NOT NULL | Worker 回傳的路況與比較結果 |
| updated_at | INTEGER | NOT NULL | 資料時間，離線時顯示 |

> 註：相較最初設計，新增 `fare_cache`（票價快取）、`drive_route`（開車路線）、`highway_cache`（路況快取）三張表，共 13 張；國道客運沿用 station、timetable_cache、fare_cache、favorite_route、trip_reminder，以 operator＝BUS 區分。

### 7.2 主要 DAO 方法

| DAO | 方法 | 說明 |
|---|---|---|
| StationDao | `searchCursor(operator, keyword): Cursor` | 供 StationProvider 使用，LIKE ? 參數綁定 |
| StationDao | `upsertAll(list)`、`lastUpdated(operator)` | 車站清單更新 |
| TimetableDao | `replace(query, trains)`（@Transaction） | 同條件先刪後寫 |
| TimetableDao | `get(query)`、`deleteOlderThan(time)` | 快取讀取與清理 |
| FavoriteDao | `observe(uid): Flow<List>`、`toggle(...)`、`updateOrder(...)` | 收藏 |
| ReminderDao | `scheduled(uid)`、`pendingForBoot(now)`、`updateStatus(id, status)` | 提醒 |
| UsageDao | `openSession`、`closeSession`、`insertVisit`、`fixUnclosed()`、`stats(uid, from, to)` | 使用紀錄 |
| FeedbackDao | `insert`、`pendingAi()`、`unsynced(limit)`、`markSynced(ids)` | 回饋 |
| DriveRouteDao | `observe(uid): Flow<List>`、`insert`、`delete`、`updateOrder(...)` | 開車路線 |
| HighwayCacheDao | `get(routeKey)`、`put(routeKey, json, updatedAt)` | 國道路況離線快取 |

### 7.3 DataStore 鍵值

| 鍵 | 型別 | 說明 |
|---|---|---|
| is_logged_in | Boolean | 是否已登入 |
| guest_mode | Boolean | 是否以訪客進入 |
| device_id | String | 安裝時產生的 UUID |
| theme_mode | String | SYSTEM／LIGHT／DARK |
| last_sync_at | Long | 上次同步時間 |
| ai_calls_window | String | 最近 1 分鐘 AI 呼叫時間戳（限流用） |
| last_next_route | Long | 「下一班」分頁上次選擇的常用路線 id（FR-NEXT-02） |
| last_drive_route | Long | 「國道」分頁上次選擇的開車路線 id（FR-HWY-01） |

### 7.4 Firestore 結構與安全規則

- `users/{uid}`：nickname、email、defaultOperator、createdAt
- `feedback/{autoId}`：uid、deviceId、rating、category、content、aiCategory、aiSentiment、aiSummary、appVersion、createdAt、usageSummary（選填）
- `usage_sessions/{autoId}`：uid、deviceId、startAt、endAt、durationMs、pages（陣列：pageName、durationMs）

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{uid} {
      allow read, write: if request.auth != null && request.auth.uid == uid;
    }
    match /feedback/{id} {
      allow create: if request.resource.data.content is string
                    && request.resource.data.content.size() <= 500;
      allow read, update, delete: if false;
    }
    match /usage_sessions/{id} {
      allow create: if true;
      allow read, update, delete: if false;
    }
  }
}
```

> 訪客也能送出回饋與使用紀錄，因此 create 不要求登入；搭配 Firebase App Check 限制只有本 App 能寫入。
