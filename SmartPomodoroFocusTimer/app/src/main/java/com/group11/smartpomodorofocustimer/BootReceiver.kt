package com.group11.smartpomodorofocustimer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.group11.smartpomodorofocustimer.TimerService.Companion.notification

/** CH13: static receiver declared in AndroidManifest.xml; fires before the app is ever opened. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.i("BootReceiver", "Device booted, reminding user to start today's study session")
        context.getSystemService(NotificationManager::class.java).notify(
            TimerService.NOTIF_BOOT,
            context.notification(context.getString(R.string.notif_boot), MainActivity::class.java),
        )
    }
}
