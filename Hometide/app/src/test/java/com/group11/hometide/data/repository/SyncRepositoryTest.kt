package com.group11.hometide.data.repository

import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.dao.PageTotal
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.entity.AiStatus
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.PageVisit
import com.group11.hometide.data.local.entity.Sentiment
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** FR-SYNC-02、FR-FB-04；TC-25（資料層） */
class SyncRepositoryTest {

    private class FakeFeedback : FeedbackDao {
        val rows = mutableListOf<Feedback>()
        override suspend fun insert(feedback: Feedback): Long = TODO()
        override suspend fun get(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun updateAi(id: Long, category: FeedbackCategory?, sentiment: Sentiment?, summary: String?, status: AiStatus) = Unit
        override suspend fun pendingAi() = rows.filter { it.aiStatus == AiStatus.PENDING }
        override suspend fun unsynced(limit: Int) = rows.filter { !it.synced }.take(limit)
        override suspend fun readyToUpload(limit: Int, pendingBefore: Long) =
            rows.filter { !it.synced && (it.aiStatus != AiStatus.PENDING || it.createdAt < pendingBefore) }.take(limit)
        override suspend fun markSynced(ids: List<Long>) {
            rows.replaceAll { if (it.id in ids) it.copy(synced = true) else it }
        }
    }

    private class FakeUsage : UsageDao {
        val sessions = mutableListOf<AppSession>()
        val visits = mutableListOf<PageVisit>()
        override suspend fun openSession(session: AppSession): Long = TODO()
        override suspend fun closeSession(id: Long, endAt: Long) = Unit
        override suspend fun insertVisit(visit: PageVisit): Long = TODO()
        override suspend fun fixUnclosed() = 0
        override suspend fun sessionCount(uid: String?, from: Long) = sessions.count { it.uid == uid && it.startAt >= from }
        override suspend fun totalDuration(uid: String?, from: Long) = sessions.filter { it.uid == uid && it.startAt >= from }.sumOf { it.durationMs }
        override suspend fun topPages(uid: String?, from: Long, limit: Int) = listOf(PageTotal("SearchFragment", 1_000))
        override suspend fun unsyncedSessions(limit: Int) = sessions.filter { !it.synced && it.endAt != null }.take(limit)
        override suspend fun visitsOf(sessionIds: List<Long>) = visits.filter { it.sessionId in sessionIds }
        override suspend fun markSessionsSynced(ids: List<Long>) {
            sessions.replaceAll { if (it.id in ids) it.copy(synced = true) else it }
        }
        override suspend fun markVisitsSynced(sessionIds: List<Long>) {
            visits.replaceAll { if (it.sessionId in sessionIds) it.copy(synced = true) else it }
        }
    }

    private val now = 10 * 24 * 3_600_000L
    private val fb = FakeFeedback()
    private val usage = FakeUsage()
    private val uploads = mutableListOf<Pair<String, List<Map<String, Any?>>>>()
    private val analyzed = mutableListOf<Long>()
    private val refreshed = mutableListOf<Operator>()
    private var online = true
    private var startedAt: Long? = null
    private var upload: suspend (String, List<Map<String, Any?>>) -> Unit = { c, d -> uploads += c to d }

    private val repo = SyncRepository(
        feedbackDao = fb,
        usageDao = usage,
        analyze = { analyzed += it },
        refreshStations = { refreshed += it },
        upload = { c, d -> upload(c, d) },
        online = { online },
        markStarted = { startedAt = it },
        now = { now },
    )

    private fun feedback(id: Long, attach: Boolean, status: AiStatus = AiStatus.DONE) = Feedback(
        id = id, uid = "u1", deviceId = "d", rating = 4, category = FeedbackCategory.BUG, content = "內容$id",
        attachUsage = attach, aiCategory = FeedbackCategory.BUG, aiSentiment = Sentiment.NEGATIVE, aiSummary = "摘要",
        aiStatus = status, appVersion = "1.0", createdAt = now,
    )

    /** TC-25（資料層）：送出的回饋與已結束的 session 都上傳並標記；使用中的 session 不傳；還在等 AI 的回饋先不傳 */
    @Test
    fun runAll_uploadsAndMarks() = runBlocking {
        fb.rows += feedback(1, attach = true)
        fb.rows += feedback(2, attach = false)
        fb.rows += feedback(3, attach = false, status = AiStatus.PENDING)
        usage.sessions += AppSession(1, "u1", "d", now - 5_000, now - 1_000, 4_000)
        usage.sessions += AppSession(2, "u1", "d", now, null, 0)
        usage.visits += PageVisit(1, 1, "u1", "SearchFragment", now - 5_000, now - 4_000, 1_000)

        assertEquals(emptyList<String>(), repo.runAll())
        assertEquals(now, startedAt)
        assertEquals(listOf(3L), analyzed)
        assertEquals(Operator.entries, refreshed)
        assertEquals(listOf(SyncRepository.FEEDBACK, SyncRepository.USAGE), uploads.map { it.first })

        val (withUsage, without) = uploads[0].second
        assertEquals("BUG", withUsage["category"])
        assertEquals(mapOf("openCount" to 2, "totalMs" to 4_000L, "pages" to listOf(mapOf("pageName" to "SearchFragment", "durationMs" to 1_000L))), withUsage["usageSummary"])
        assertFalse(without.containsKey("usageSummary"))
        assertEquals(listOf(true, true, false), fb.rows.map { it.synced })

        val session = uploads[1].second.single()
        assertEquals(listOf(mapOf("pageName" to "SearchFragment", "durationMs" to 1_000L)), session["pages"])
        assertEquals(listOf(true, false), usage.sessions.map { it.synced })
        assertTrue(usage.visits.single().synced)
    }

    /** FR-SYNC-02：每批最多 200 筆 */
    @Test
    fun batchesOf200() = runBlocking {
        repeat(450) { fb.rows += feedback(it + 1L, attach = false) }
        repo.runAll()
        assertEquals(listOf(200, 200, 50), uploads.filter { it.first == SyncRepository.FEEDBACK }.map { it.second.size })
    }

    /** 任一步驟失敗不影響其他步驟；失敗的資料維持 synced = 0（含逾時） */
    @Test
    fun failedStep_keepsUnsynced_othersContinue() = runBlocking {
        fb.rows += feedback(1, attach = false)
        usage.sessions += AppSession(1, null, "d", 0, 1_000, 1_000)
        upload = { c, d -> if (c == SyncRepository.FEEDBACK) throw IOException("x") else uploads += c to d }
        assertEquals(listOf("feedback"), repo.runAll())
        assertFalse(fb.rows.single().synced)
        assertTrue(usage.sessions.single().synced)

        upload = { _, _ -> withTimeout(1) { delay(1_000) } }
        usage.sessions += AppSession(2, null, "d", 0, 1_000, 1_000)
        assertEquals(listOf("feedback", "usage"), repo.runAll())
        assertFalse(usage.sessions[1].synced)
    }

    /** AI 一直沒結果：一天後不帶 AI 欄位上傳 */
    @Test
    fun pendingOverADay_uploaded() = runBlocking {
        fb.rows += feedback(1, attach = false, status = AiStatus.PENDING).copy(createdAt = now - 25 * 3_600_000L)
        repo.runAll()
        assertTrue(fb.rows.single().synced)
    }

    /** 沒有網路：只做車站更新 */
    @Test
    fun offline_skipsNetworkSteps() = runBlocking {
        online = false
        fb.rows += feedback(1, attach = false, status = AiStatus.PENDING)
        repo.runAll()
        assertTrue(analyzed.isEmpty() && uploads.isEmpty())
        assertEquals(Operator.entries, refreshed)
        assertNull(fb.rows.single().takeIf { it.synced })
    }
}
