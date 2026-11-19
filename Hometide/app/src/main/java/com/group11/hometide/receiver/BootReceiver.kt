package com.group11.hometide.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.group11.hometide.HometideApp
import kotlinx.coroutines.launch

/**
 * 開機重排（FR-RMD-04、規格 10.2）：AlarmManager 的排程開機後會消失，
 * 觸發時間未過的重新排程；觸發時間已過但發車時間未過的立即發出通知。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as HometideApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.container.reminderRepository.rescheduleAfterBoot(app.container.authRepository.currentUid).let { due ->
                    val now = System.currentTimeMillis()
                    due.forEach { ReminderReceiver.notify(app, it, ReminderReceiver.minutesLeft(it, now)) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
