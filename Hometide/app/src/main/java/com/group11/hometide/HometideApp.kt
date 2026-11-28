package com.group11.hometide

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.group11.hometide.service.SyncService
import kotlinx.coroutines.flow.first
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.util.NotificationChannels
import com.group11.hometide.util.Result
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HometideApp : Application() {

    lateinit var container: AppContainer
        private set

    /** App 存活期間的背景工作；未預期的例外只記 log，不讓 App 閃退 */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "Background task failed: ${e.javaClass.simpleName}") },
    )

    override fun onCreate() {
        super.onCreate()
        AppCheckInstaller.install() // 規格 9.1：在使用任何 Firebase 功能前
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        refreshStations()
        appScope.launch { container.reminderRepository.purgeOld() } // FR-FAV-05：保留 7 天
        appScope.launch { container.transitRepository.purgeOldTimetables() } // FR-OFF-01：快取保留 3 天
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.usageTracker) // FR-USG-01
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = syncIfStale()
        })
        watchNetwork()
    }

    /** FR-SYNC-01 (2)：回到前景且距上次同步超過 15 分鐘 */
    private fun syncIfStale() {
        appScope.launch {
            val last = container.settingsStore.lastSyncAt.first() ?: 0L
            if (System.currentTimeMillis() - last > SYNC_INTERVAL_MS) SyncService.start(this@HometideApp)
        }
    }

    /** FR-SYNC-01 (3)：網路恢復且 App 在前景（SyncService.start 會檢查前景） */
    private fun watchNetwork() {
        appScope.launch {
            var wasOnline = container.networkMonitor.online.value
            container.networkMonitor.online.collect { online ->
                if (online && !wasOnline) SyncService.start(this@HometideApp)
                wasOnline = online
            }
        }
    }

    /** 啟動時在背景更新台鐵、高鐵車站與國道客運站牌（沒有資料或超過 7 天才下載）；失敗不提示，下次啟動再試 */
    private fun refreshStations() {
        appScope.launch {
            for (op in Operator.entries) {
                val res = container.transitRepository.refreshStations(op)
                if (res is Result.Failure) Log.w(TAG, "Station refresh failed: $op ${res.error}")
            }
        }
    }

    private companion object {
        const val TAG = "HometideApp"
        const val SYNC_INTERVAL_MS = 15 * 60_000L
    }
}
