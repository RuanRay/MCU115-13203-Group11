package com.group11.smartpomodorofocustimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import java.util.Locale

/** CH12: runs the countdown on a background Thread so it survives MainActivity closing. */
class TimerService : Service() {

    private var worker: Thread? = null
    @Volatile private var remaining = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var ambient: MediaPlayer

    /** CH13: dynamic system-broadcast receiver, alive only while a session runs. */
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> remind()
                Intent.ACTION_HEADSET_PLUG -> {
                    // Ambient sound only through headphones, never blaring from the speaker.
                    val plugged = intent.getIntExtra("state", 0) == 1
                    if (plugged) ambient.start() else if (ambient.isPlaying) ambient.pause()
                    // HEADSET_PLUG is sticky: the replay at registration just syncs state, no toast.
                    if (!isInitialStickyBroadcast) toast(
                        getString(if (plugged) R.string.toast_headset_in else R.string.toast_headset_out)
                    )
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun remind() = toast(getString(R.string.toast_reminder, minutesLeft(remaining)))

    override fun onCreate() {
        super.onCreate()
        ambient = MediaPlayer.create(this, R.raw.focus_ambient).apply { isLooping = true }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        registerReceiver(systemReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val total = intent?.getIntExtra(EXTRA_SECONDS, FOCUS_SECONDS) ?: FOCUS_SECONDS
        // Without foreground status Android kills a backgrounded service within ~1 minute.
        startForeground(
            NOTIF_RUNNING,
            notification(getString(R.string.notif_running), MainActivity::class.java),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )

        worker?.interrupt()
        // Count against the clock, not by summing sleeps, so the timer doesn't drift.
        val end = SystemClock.elapsedRealtime() + total * 1000L
        worker = Thread {
            try {
                while (true) {
                    val msLeft = end - SystemClock.elapsedRealtime()
                    remaining = if (msLeft <= 0) 0 else ((msLeft + 999) / 1000).toInt()
                    sendBroadcast(
                        Intent(ACTION_TICK).setPackage(packageName)
                            .putExtra(EXTRA_REMAINING, remaining)
                    )
                    if (remaining == 0) break
                    if (isReminderTick(remaining, total)) mainHandler.post { remind() }
                    Thread.sleep((msLeft - 1) % 1000 + 1) // wake on the next second boundary
                }
                onFinished()
            } catch (_: InterruptedException) {
                // Stopped by the user or replaced by a new session.
            }
        }.apply { start() }

        return START_NOT_STICKY
    }

    private fun onFinished() {
        // Direct launch works while the app is visible; Android blocks it from the background,
        // so the notification's full-screen intent / tap is the fallback path.
        startActivity(Intent(this, FinishActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        getSystemService(NotificationManager::class.java).notify(
            NOTIF_FINISHED,
            notification(getString(R.string.finish_title), FinishActivity::class.java, fullScreen = true),
        )
        stopSelf()
    }

    override fun onDestroy() {
        worker?.interrupt()
        worker?.join() // after this no stale tick can be sent...
        // ...so this reset is guaranteed to be the last update MainActivity sees.
        sendBroadcast(Intent(ACTION_TICK).setPackage(packageName).putExtra(EXTRA_REMAINING, 0))
        mainHandler.removeCallbacksAndMessages(null)
        unregisterReceiver(systemReceiver)
        ambient.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_TICK = "com.group11.smartpomodorofocustimer.TIMER_UPDATE"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_REMAINING = "remaining"
        const val FOCUS_SECONDS = 25 * 60 // shorten (e.g. 10) to test the finish screen quickly

        private const val CHANNEL_ID = "focus"
        private const val NOTIF_RUNNING = 1
        private const val NOTIF_FINISHED = 2
        const val NOTIF_BOOT = 3

        fun formatTime(seconds: Int): String =
            String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

        /** 18:59 left -> "19 mins", matching the reminder toast. */
        fun minutesLeft(seconds: Int): Int = (seconds + 59) / 60

        const val REMIND_EVERY_SECONDS = 2 * 60

        /** Periodic reminder every 2 minutes, skipping the very first second of the session. */
        fun isReminderTick(remaining: Int, total: Int): Boolean =
            remaining in 1 until total && (total - remaining) % REMIND_EVERY_SECONDS == 0

        fun Context.notification(
            text: String,
            target: Class<*>,
            fullScreen: Boolean = false,
        ): Notification {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
            val pi = PendingIntent.getActivity(
                this, target.hashCode(), Intent(this, target), PendingIntent.FLAG_IMMUTABLE
            )
            return Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .apply { if (fullScreen) setFullScreenIntent(pi, true) }
                .build()
        }
    }
}
