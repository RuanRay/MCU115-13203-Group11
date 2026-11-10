package com.group11.hometide.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.group11.hometide.R

/** 通知頻道（規格 10.5）。重複建立不會改變使用者已調整的設定。 */
object NotificationChannels {
    const val TRIP_REMINDER = "trip_reminder" // FR-RMD-03
    const val SYNC = "sync" // SyncService 前景通知

    fun createAll(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    TRIP_REMINDER,
                    context.getString(R.string.channel_trip_reminder),
                    NotificationManager.IMPORTANCE_HIGH,
                ),
                NotificationChannel(
                    SYNC,
                    context.getString(R.string.channel_sync),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            ),
        )
    }
}
