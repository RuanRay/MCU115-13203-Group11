package com.group11.hometide

import android.app.Application
import com.group11.hometide.util.NotificationChannels

class HometideApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationChannels.createAll(this)
        // TODO(第 7 週，FR-USG)：以 ProcessLifecycleOwner 註冊 UsageTracker（規格 6.2）
    }
}
