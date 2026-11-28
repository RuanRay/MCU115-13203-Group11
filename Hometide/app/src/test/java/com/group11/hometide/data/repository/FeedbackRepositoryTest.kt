package com.group11.hometide.data.repository

import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.entity.AiStatus
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.local.entity.Sentiment
import com.group11.hometide.ui.feedback.FeedbackRules
import com.group11.hometide.util.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** FR-FB-01～03；TC-24（資料層） */
class FeedbackRepositoryTest {

    private class FakeDao : FeedbackDao {
        val rows = mutableListOf<Feedback>()
        override suspend fun insert(feedback: Feedback): Long {
            rows += feedback.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }
        override suspend fun get(id: Long) = rows.getOrNull(id.toInt() - 1)
        override suspend fun updateAi(id: Long, category: FeedbackCategory?, sentiment: Sentiment?, summary: String?, status: AiStatus) {
            val i = id.toInt() - 1
            rows[i] = rows[i].copy(aiCategory = category, aiSentiment = sentiment, aiSummary = summary, aiStatus = status)
        }
        override suspend fun pendingAi() = rows.filter { it.aiStatus == AiStatus.PENDING }
        override suspend fun unsynced(limit: Int) = rows.filter { !it.synced }.take(limit)
        override suspend fun markSynced(ids: List<Long>) = Unit
    }

    private val dao = FakeDao()
    private val prompts = mutableListOf<String>()
    private var reply: suspend () -> String? = { """{"category":"BUG","sentiment":"NEUTRAL","summary":"高鐵臺南站查詢偶爾解析失敗"}""" }
    private val repo = FeedbackRepository(dao, { "device" }, { "u1" }, { prompts += it; reply() }, "1.0", now = { 42L })

    private fun submitAndAnalyze(): Feedback = runBlocking {
        val id = (repo.submit(4, FeedbackCategory.BUG, "  查詢高鐵臺南站時偶爾跳出無法解析  ", true) as Result.Success).data
        (repo.analyze(id) as Result.Success).data
    }

    /** TC-24：寫入 PENDING、只送回饋文字給 AI、寫回分類與摘要 */
    @Test
    fun submit_thenAnalyze_done() {
        val fb = submitAndAnalyze()
        assertEquals(AiStatus.DONE, fb.aiStatus)
        assertEquals(FeedbackCategory.BUG to Sentiment.NEUTRAL, fb.aiCategory to fb.aiSentiment)
        assertEquals(fb, dao.rows.single())
        assertEquals("查詢高鐵臺南站時偶爾跳出無法解析", fb.content)
        assertFalse(fb.synced)
        assertTrue(prompts.single().endsWith("回饋內容：查詢高鐵臺南站時偶爾跳出無法解析"))
        assertFalse(prompts.single().contains("u1"))
        assertTrue(FeedbackRules.matches(fb))
    }

    @Test
    fun summary_cutTo30() {
        reply = { """{"category":"OTHER","sentiment":"POSITIVE","summary":"${"字".repeat(40)}"}""" }
        val fb = submitAndAnalyze()
        assertEquals(30, fb.aiSummary?.length)
        assertFalse(FeedbackRules.matches(fb))
    }

    /** 連線失敗維持 PENDING（SyncService 補做）；格式錯誤為 FAILED（隱藏卡片） */
    @Test
    fun failures() {
        reply = { throw RuntimeException("wrapped", IOException("offline")) }
        assertEquals(AiStatus.PENDING, submitAndAnalyze().aiStatus)
        reply = { "not json" }
        assertEquals(AiStatus.FAILED, submitAndAnalyze().aiStatus)
        reply = { """{"category":"???","sentiment":"NEUTRAL","summary":"x"}""" }
        assertEquals(AiStatus.FAILED, submitAndAnalyze().aiStatus)
    }

    @Test
    fun analyze_skipsAlreadyDone() {
        val fb = submitAndAnalyze()
        runBlocking { repo.analyze(fb.id) }
        assertEquals(1, prompts.size)
    }

    /** FR-FB-01 */
    @Test
    fun canSubmit_rules() {
        assertFalse(FeedbackRules.canSubmit(0, "內容"))
        assertFalse(FeedbackRules.canSubmit(3, "   "))
        assertTrue(FeedbackRules.canSubmit(1, "好"))
        assertTrue(FeedbackRules.canSubmit(5, "字".repeat(500)))
        assertFalse(FeedbackRules.canSubmit(5, "字".repeat(501)))
    }
}
