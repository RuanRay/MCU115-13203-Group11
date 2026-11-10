## 10. 背景元件與系統整合規格

### 10.1 SyncService（Background Service）

- **類型：** 前景服務，`foregroundServiceType="dataSync"`。
- **啟動：** `ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java))`，只在 App 位於前景時呼叫（FR-SYNC-01）。
- **onStartCommand：** 5 秒內呼叫 `startForeground(NOTIF_ID_SYNC, notification)`，通知頻道 `sync`（重要性 LOW，文字「正在同步回饋與使用紀錄」）；在協程中依序執行 FR-SYNC-02；完成後 `stopForeground(STOP_FOREGROUND_REMOVE)` 與 `stopSelf()`；回傳 `START_NOT_STICKY`。
- **為什麼需要：** 回饋送出後使用者常立刻離開，工作若綁在 Activity 會被取消；Service 讓上傳與畫面生命週期分離。

### 10.2 Broadcast Receiver

| Receiver | 監聽 | exported | 動作 |
|---|---|---|---|
| ReminderReceiver | 自訂 action `com.group11.hometide.ACTION_TRIP_REMINDER`（由 AlarmManager 送出，extra：reminderId） | false | 發出通知、更新狀態為 FIRED |
| BootReceiver | `android.intent.action.BOOT_COMPLETED` | true（系統廣播需要） | 重新排定提醒（FR-RMD-04） |

- 網路狀態以 `ConnectivityManager.registerDefaultNetworkCallback` 監聽（非 Receiver），由 `NetworkMonitor` 以 StateFlow 提供給 ViewModel。

### 10.3 StationProvider（Content Provider）

- **Authority：** `com.group11.hometide.stations`；**URI：** `content://com.group11.hometide.stations/stations`
- **query 參數：** `selection` 不使用自由字串，改由 URI 查詢參數 `operator`、`q` 傳入，Provider 內部轉為參數綁定的 SQL。
- **回傳欄位：** `_id`（SimpleCursorAdapter 必要）、`station_id`、`name_zh`、`name_en`、`operator`。
- **getType：** `vnd.android.cursor.dir/vnd.com.group11.hometide.station`
- **insert／update／delete：** 拋出 `UnsupportedOperationException`（唯讀）。
- **exported：** false，只供本 App 的自動完成輸入框與 AI 站名驗證使用。

### 10.4 AndroidManifest 重點

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

<application android:name=".HometideApp" ...>
    <activity android:name=".ui.auth.LoginActivity" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
    <!-- 其餘 8 個 Activity（含 NextTrainActivity、HighwayActivity）exported="false"，screenOrientation="portrait" -->
    <service android:name=".service.SyncService"
        android:foregroundServiceType="dataSync" android:exported="false" />
    <receiver android:name=".receiver.ReminderReceiver" android:exported="false" />
    <receiver android:name=".receiver.BootReceiver" android:exported="true">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
    <provider android:name=".provider.StationProvider"
        android:authorities="com.group11.hometide.stations"
        android:exported="false" />
</application>
```

### 10.5 通知頻道

| 頻道 ID | 名稱 | 重要性 | 用途 |
|---|---|---|---|
| trip_reminder | 出發提醒 | HIGH | FR-RMD-03 |
| sync | 資料同步 | LOW | SyncService 前景通知 |
