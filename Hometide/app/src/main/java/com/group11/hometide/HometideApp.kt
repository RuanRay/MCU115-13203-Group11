package com.group11.hometide

import android.app.Application
import android.util.Log
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.util.NotificationChannels
import com.group11.hometide.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HometideApp : Application() {

    lateinit var container: AppContainer
        private set

    /** App 存活期間的背景工作 */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        refreshStations()
        // TODO(第 7 週，FR-USG)：以 ProcessLifecycleOwner 註冊 UsageTracker（規格 6.2）
    }

    /** 啟動時在背景更新台鐵、高鐵車站（沒有資料或超過 7 天才下載）；失敗不提示，下次啟動再試 */
    private fun refreshStations() {
        appScope.launch {
            for (op in listOf(Operator.TRA, Operator.THSR)) {
                val res = container.transitRepository.refreshStations(op)
                if (res is Result.Failure) Log.w(TAG, "Station refresh failed: $op ${res.error}")
            }
        }
    }

    private companion object {
        const val TAG = "HometideApp"
    }
}
