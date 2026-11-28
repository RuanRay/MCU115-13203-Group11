package com.group11.hometide.data.repository

import android.util.Log
import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.PageVisit
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * FR-SYNC-02 的工作內容（SyncService 呼叫）：補做 AI 分析 → 上傳 feedback、app_session＋page_visit → 車站清單過期就重新下載。
 * 任一步驟失敗不影響其他步驟；失敗的資料維持 synced = 0。
 * [upload] 把一批文件寫進 Firestore 的 collection（autoId），全部成功才返回，失敗或逾時拋例外。
 */
class SyncRepository(
    private val feedbackDao: FeedbackDao,
    private val usageDao: UsageDao,
    private val analyze: suspend (Long) -> Unit,
    private val refreshStations: suspend (Operator) -> Unit,
    private val upload: suspend (collection: String, docs: List<Map<String, Any?>>) -> Unit,
    private val online: () -> Boolean,
    private val markStarted: suspend (Long) -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 回傳失敗的步驟名稱（測試與 log 用） */
    suspend fun runAll(): List<String> {
        // 實作者確認：開始時就更新上次同步時間，避免短時間重複觸發
        try {
            markStarted(now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Mark sync time failed ${e.javaClass.simpleName}")
        }
        val failed = mutableListOf<String>()
        // 實作者確認：沒有網路就跳過需要連線的步驟（Firestore 離線時會排隊，避免一直等不到完成）
        if (online()) {
            step("ai", failed) { feedbackDao.pendingAi().forEach { analyze(it.id) } }
            step("feedback", failed) { uploadFeedback() }
            step("usage", failed) { uploadUsage() }
        }
        step("stations", failed) { Operator.entries.forEach { refreshStations(it) } }
        return failed
    }

    /** FR-FB-04：feedback/{autoId}；勾選附上統計時加上近 7 天彙總 */
    private suspend fun uploadFeedback() {
        while (true) {
            val batch = feedbackDao.readyToUpload(BATCH, now() - PENDING_GIVE_UP_MS)
            if (batch.isEmpty()) return
            upload(FEEDBACK, batch.map { feedbackDoc(it) })
            feedbackDao.markSynced(batch.map { it.id })
            if (batch.size < BATCH) return
        }
    }

    /** 7.4 usage_sessions/{autoId}：每個已結束的 session 一筆，附上該次的頁面 */
    private suspend fun uploadUsage() {
        while (true) {
            val sessions = usageDao.unsyncedSessions(BATCH)
            if (sessions.isEmpty()) return
            val visits = usageDao.visitsOf(sessions.map { it.id }).groupBy { it.sessionId }
            upload(USAGE, sessions.map { sessionDoc(it, visits[it.id].orEmpty()) })
            usageDao.markSynced(sessions.map { it.id })
            if (sessions.size < BATCH) return
        }
    }

    private suspend fun feedbackDoc(f: Feedback): Map<String, Any?> = mapOf(
        "uid" to f.uid,
        "deviceId" to f.deviceId,
        "rating" to f.rating,
        "category" to f.category.name,
        "content" to f.content,
        "aiCategory" to f.aiCategory?.name,
        "aiSentiment" to f.aiSentiment?.name,
        "aiSummary" to f.aiSummary,
        "appVersion" to f.appVersion,
        "createdAt" to f.createdAt,
    ) + (if (f.attachUsage) mapOf("usageSummary" to usageSummary(f)) else emptyMap())

    /** 近 7 天（回饋送出前），與 P6 相同：會員算自己的、訪客算沒有帳號的紀錄 */
    private suspend fun usageSummary(f: Feedback): Map<String, Any?> {
        val from = f.createdAt - SEVEN_DAYS_MS
        return mapOf(
            "openCount" to usageDao.sessionCount(f.uid, from),
            "totalMs" to usageDao.totalDuration(f.uid, from),
            "pages" to usageDao.topPages(f.uid, from, MAX_PAGES).map { mapOf("pageName" to it.pageName, "durationMs" to it.durationMs) },
        )
    }

    private fun sessionDoc(s: AppSession, visits: List<PageVisit>): Map<String, Any?> = mapOf(
        "uid" to s.uid,
        "deviceId" to s.deviceId,
        "startAt" to s.startAt,
        "endAt" to s.endAt,
        "durationMs" to s.durationMs,
        "pages" to visits.map { mapOf("pageName" to it.pageName, "durationMs" to it.durationMs) },
    )

    private suspend fun step(name: String, failed: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Sync step $name timed out")
            failed += name
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Sync step $name failed ${e.javaClass.simpleName}")
            failed += name
        }
    }

    companion object {
        private const val TAG = "SyncRepository"
        const val FEEDBACK = "feedback"
        const val USAGE = "usage_sessions"

        /** FR-SYNC-02：每批最多 200 筆 */
        const val BATCH = 200
        private const val SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1_000L

        /** AI 一直沒結果時，一天後不帶 AI 欄位上傳 */
        private const val PENDING_GIVE_UP_MS = 24 * 60 * 60 * 1_000L

        // 各頁時長全列（頁面種類不到 20）
        private const val MAX_PAGES = 50
    }
}
