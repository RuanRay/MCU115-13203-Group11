package com.group11.hometide.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.util.NotificationChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 同步回饋與使用紀錄（規格 10.1、FR-SYNC-02）。前景服務：使用者送出回饋後立刻離開，上傳也不會被取消。
 * 工作內容在 SyncRepository；這裡只負責前景通知與生命週期。
 */
class SyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    // 執行中又被觸發（例如同步到一半時送出回饋）：跑完再補一輪
    @Volatile private var rerun = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 10.1：5 秒內呼叫 startForeground
        val notification = NotificationCompat.Builder(this, NotificationChannels.SYNC)
            .setSmallIcon(R.drawable.ic_nav_next)
            .setContentText(getString(R.string.sync_notification))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        ServiceCompat.startForeground(this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (running) {
            rerun = true
        } else {
            running = true
            scope.launch {
                try {
                    do {
                        rerun = false
                        val failed = (application as HometideApp).container.syncRepository.runAll()
                        if (failed.isNotEmpty()) Log.w(TAG, "Sync finished with failures: $failed")
                    } while (rerun)
                } finally {
                    running = false
                    ServiceCompat.stopForeground(this@SyncService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SyncService"
        private const val NOTIF_ID = 2001

        /** FR-SYNC-01：只在 App 位於前景時啟動（Android 12 起背景不能啟動前景服務） */
        fun start(context: Context) {
            if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
            runCatching { ContextCompat.startForegroundService(context, Intent(context, SyncService::class.java)) }
                .onFailure { Log.w(TAG, "Start failed ${it.javaClass.simpleName}") }
        }
    }
}
