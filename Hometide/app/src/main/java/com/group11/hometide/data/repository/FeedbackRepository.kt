package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.ai.GeminiClient
import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.entity.AiStatus
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.local.entity.Sentiment
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 意見回饋（FR-FB-02、03）：先寫入 Room（ai_status = PENDING、synced = 0），再呼叫 Gemini 分類與摘要並寫回。
 * 上傳 Firestore 由 SyncService 負責（FR-FB-04，7-2b）。
 */
class FeedbackRepository(
    private val dao: FeedbackDao,
    private val deviceId: suspend () -> String,
    private val uid: () -> String?,
    /** 傳入提示詞、回傳模型 JSON */
    private val classify: suspend (String) -> String?,
    private val appVersion: String,
    private val gson: Gson = Gson(),
    private val now: () -> Long = System::currentTimeMillis,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

    /** FR-FB-02：回傳 feedback id */
    suspend fun submit(rating: Int, category: FeedbackCategory, content: String, attachUsage: Boolean): Result<Long> = guarded {
        val id = dao.insert(
            Feedback(
                uid = uid(), deviceId = deviceId(), rating = rating, category = category, content = content.trim(),
                attachUsage = attachUsage, aiStatus = AiStatus.PENDING, appVersion = appVersion, createdAt = now(),
            ),
        )
        Result.Success(id)
    }

    /**
     * FR-FB-03：分類、情緒、30 字摘要。
     * - 成功：DONE 並回傳更新後的資料
     * - 連線失敗或逾時 8 秒（同 FR-AI-02）：維持 PENDING（之後由 SyncService 補做），回傳原資料
     * - 其他失敗（格式錯誤、拒答）：FAILED
     */
    suspend fun analyze(id: Long): Result<Feedback> {
        // P7 與 SyncService 可能同時分析同一筆：共用同一個工作，Gemini 只呼叫一次；離開 P7 也不會中斷
        val job = synchronized(inFlight) {
            inFlight.getOrPut(id) {
                scope.async { analyzeNow(id) }.also { d -> d.invokeOnCompletion { synchronized(inFlight) { inFlight.remove(id) } } }
            }
        }
        return job.await()
    }

    private val inFlight = HashMap<Long, Deferred<Result<Feedback>>>()

    private suspend fun analyzeNow(id: Long): Result<Feedback> = guarded {
        val fb = dao.get(id) ?: return@guarded Result.Failure(AppError.NotFound)
        if (fb.aiStatus != AiStatus.PENDING) return@guarded Result.Success(fb)
        val text = try {
            withTimeout(GeminiClient.TIMEOUT_MS) { classify(GeminiClient.feedbackPrompt(fb.content)) }
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Feedback AI timeout")
            return@guarded Result.Success(fb)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Feedback AI failed ${e.javaClass.simpleName}")
            if (generateSequence<Throwable>(e) { it.cause }.any { it is IOException }) return@guarded Result.Success(fb)
            null
        }
        val dto = text?.let { runCatching { gson.fromJson(it, AiDto::class.java) }.getOrNull() }
        val category = FeedbackCategory.entries.firstOrNull { it.name == dto?.category }
        val sentiment = Sentiment.entries.firstOrNull { it.name == dto?.sentiment }
        val summary = dto?.summary?.trim()?.take(SUMMARY_MAX)?.ifEmpty { null }
        val updated = if (category != null && sentiment != null && summary != null) {
            fb.copy(aiCategory = category, aiSentiment = sentiment, aiSummary = summary, aiStatus = AiStatus.DONE)
        } else {
            Log.w(TAG, "Feedback AI parse error") // 11.2 ParseError：只記類別，不記內容
            fb.copy(aiStatus = AiStatus.FAILED)
        }
        dao.updateAi(id, updated.aiCategory, updated.aiSentiment, updated.aiSummary, updated.aiStatus)
        Result.Success(updated)
    }

    private inline fun <T> guarded(block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Feedback failed ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    private data class AiDto(val category: String?, val sentiment: String?, val summary: String?)

    private companion object {
        const val TAG = "FeedbackRepository"
        const val SUMMARY_MAX = 30
    }
}
