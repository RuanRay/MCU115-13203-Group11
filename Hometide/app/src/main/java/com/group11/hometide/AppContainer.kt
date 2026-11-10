package com.group11.hometide

import android.content.Context
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.SettingsStore

/**
 * 手動 DI（規格 6.1、6.2），由 HometideApp 持有，全 App 共用單例。
 *
 * 之後依任務加入：
 * - 資料來源：HometideApi（Retrofit → Worker，BuildConfig.WORKER_BASE_URL）、
 *   AppDatabase（Room）、SettingsStore（DataStore）、GeminiClient（Firebase AI Logic）
 * - Repository：TransitRepository、AuthRepository、FavoriteRepository、ReminderRepository、
 *   UsageRepository、FeedbackRepository、AiRepository、HighwayRepository
 */
class AppContainer(private val context: Context) {
    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }
}
