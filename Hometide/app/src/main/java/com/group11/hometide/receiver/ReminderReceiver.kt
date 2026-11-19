package com.group11.hometide.receiver

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.ui.main.MainActivity
import com.group11.hometide.util.NotificationChannels
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 出發提醒（FR-RMD-03、規格 10.2）：AlarmManager 送出 [ACTION]（extra：reminderId）→ 讀取提醒、改為 FIRED、發出通知；
 * 點通知開 P5「出發提醒」分頁。已取消的提醒不發通知。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it > 0 } ?: return
        val app = context.applicationContext as HometideApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.reminderRepository.fire(id, app.container.authRepository.currentUid)?.let { notify(app, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.group11.hometide.ACTION_TRIP_REMINDER"
        const val EXTRA_ID = "reminderId"
        private const val TAG = "ReminderReceiver"
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * 發出提醒通知（FR-RMD-03；BootReceiver 補發也用這裡）。標題「N 分鐘後發車」預設為設定的提前分鐘數；
         * 開機補發時改傳 [minutesLeft]。未授權通知時（FR-RMD-05）提醒仍改為 FIRED，只是不顯示通知。
         */
        // areNotificationsEnabled 在 Android 13 以上已反映 POST_NOTIFICATIONS；
        // 不用 checkSelfPermission：Android 12 以下系統不認得此權限，一律回 DENIED，會讓通知永遠發不出去
        @SuppressLint("MissingPermission")
        fun notify(context: Context, r: TripReminder, minutes: Int = r.remindBeforeMin) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                Log.w(TAG, "Notifications disabled")
                return
            }
            val open = PendingIntent.getActivity(
                context,
                r.id.toInt(),
                MainActivity.intentForReminders(context),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, NotificationChannels.TRIP_REMINDER)
                .setSmallIcon(R.drawable.ic_nav_next)
                .setContentTitle(context.getString(R.string.notify_reminder_title, minutes))
                .setContentText(contentText(context, r))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(r.id.toInt(), notification)
        }

        /** 開機補發用：距發車剩幾分鐘（無條件進位、至少 1），不超過設定的提前分鐘數 */
        fun minutesLeft(r: TripReminder, now: Long): Int =
            ((r.departAt - now + 59_999) / 60_000).toInt().coerceIn(1, r.remindBeforeMin)

        /**
         * 「自強 139 次 14:12 桃園 → 臺南」；國道客運「統聯客運 1610 14:30 臺北轉運站 → 臺南轉運站（實際以現場為準）」（FR-BUS-05）
         */
        fun contentText(context: Context, r: TripReminder): String {
            val time = Instant.ofEpochMilli(r.departAt).atZone(ZoneId.systemDefault()).format(HHMM)
            return if (r.operator == Operator.BUS) {
                context.getString(R.string.notify_reminder_bus, r.trainTypeName, time, r.originName, r.destName)
            } else {
                context.getString(R.string.notify_reminder_rail, r.trainTypeName, r.trainNo, time, r.originName, r.destName)
            }
        }
    }
}
