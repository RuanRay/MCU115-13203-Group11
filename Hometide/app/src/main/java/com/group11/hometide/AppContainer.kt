package com.group11.hometide

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.QuotaGate
import com.group11.hometide.data.ai.GeminiClient
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.group11.hometide.usage.UsageTracker
import com.group11.hometide.data.repository.UsageRepository
import com.group11.hometide.data.repository.AiRepository
import com.group11.hometide.data.repository.DriveRouteRepository
import com.group11.hometide.data.repository.FeedbackRepository
import com.group11.hometide.data.repository.HighwayRepository
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.FavoriteRepository
import com.group11.hometide.data.repository.ReminderRepository
import com.group11.hometide.reminder.ReminderScheduler
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.ui.routes.FavoriteCards

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
        AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), database.userProfileDao(), settingsStore, reminderRepository)
    }
    /** 11.2：所有 Worker 呼叫共用的 QUOTA 冷卻 */
    private val quotaGate = QuotaGate(gson)

    val transitRepository: TransitRepository by lazy {
        TransitRepository(api, database.stationDao(), database.searchHistoryDao(), database.timetableDao(), database.fareDao(), gson, quotaGate)
    }
    val highwayRepository: HighwayRepository by lazy { HighwayRepository(api, database.highwayCacheDao(), gson, quotaGate) }
    val driveRouteRepository: DriveRouteRepository by lazy { DriveRouteRepository(database.driveRouteDao(), settingsStore) }
    val favoriteRepository: FavoriteRepository by lazy { FavoriteRepository(database.favoriteDao(), transitRepository, settingsStore) }
    val reminderRepository: ReminderRepository by lazy { ReminderRepository(database.reminderDao(), ReminderScheduler(context)) }
    val aiRepository: AiRepository by lazy {
        val model = GeminiClient.jsonModel(GeminiClient.tripSchema)
        AiRepository(
            logDao = database.aiQueryLogDao(),
            findStation = { op, name -> (transitRepository.findStation(op, name) as? Result.Success)?.data },
            loadWindow = { settingsStore.aiCallsWindow.first() },
            saveWindow = settingsStore::setAiCallsWindow,
            generate = { prompt -> model.generateContent(prompt).text },
            gson = gson,
        )
    }
    /** FR-USG-01～03：寫入失敗只記 log */
    val usageTracker: UsageTracker by lazy {
        UsageTracker(database.usageDao(), settingsStore::deviceId, { authRepository.currentUid }, CoroutineScope(SupervisorJob()))
    }
    val usageRepository: UsageRepository by lazy { UsageRepository(database.usageDao(), usageTracker) }
    /** FR-FB-02、03 */
    val feedbackRepository: FeedbackRepository by lazy {
        val model = GeminiClient.jsonModel(GeminiClient.feedbackSchema)
        FeedbackRepository(
            dao = database.feedbackDao(),
            deviceId = settingsStore::deviceId,
            uid = { authRepository.currentUid },
            classify = { prompt -> model.generateContent(prompt).text },
            appVersion = BuildConfig.VERSION_NAME,
            gson = gson,
        )
    }
    val favoriteCards: FavoriteCards by lazy { FavoriteCards(authRepository, favoriteRepository, transitRepository) }
}
