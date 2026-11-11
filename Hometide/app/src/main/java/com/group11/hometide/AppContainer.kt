package com.group11.hometide

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.TransitRepository

/**
 * 手動 DI（規格 6.1、6.2），由 HometideApp 持有，全 App 共用單例。
 *
 * 之後依任務加入：
 * - 資料來源：GeminiClient（Firebase AI Logic）
 * - Repository：FavoriteRepository、ReminderRepository、UsageRepository、FeedbackRepository、AiRepository、HighwayRepository
 */
class AppContainer(private val context: Context) {
    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val settingsStore: SettingsStore by lazy { SettingsStore(context) }

    private val gson = Gson()
    val api: HometideApi by lazy { HometideApi.create(BuildConfig.WORKER_BASE_URL, gson) }

    val authRepository: AuthRepository by lazy {
        AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), database.userProfileDao(), settingsStore)
    }
    val transitRepository: TransitRepository by lazy { TransitRepository(api, database.stationDao(), gson) }
}
