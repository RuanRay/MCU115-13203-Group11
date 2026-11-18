package com.group11.hometide.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.receiver.ReminderReceiver

/**
 * AlarmManager 包裝（FR-RMD-02）：`setAndAllowWhileIdle(RTC_WAKEUP, …)`，不申請精準鬧鐘權限（提醒可能延遲數分鐘）。
 * PendingIntent 以 reminderId 為 requestCode，FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT。
 */
class ReminderScheduler(context: Context) {

    private val app = context.applicationContext
    private val alarms = app.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: TripReminder) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent(reminder.id))
    }

    fun cancel(reminderId: Long) {
        alarms.cancel(pendingIntent(reminderId))
    }

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        app,
        id.toInt(),
        Intent(app, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION).putExtra(ReminderReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
