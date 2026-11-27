package com.group11.hometide.data.repository

import android.util.Log
import com.group11.hometide.data.local.dao.PageTotal
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.usage.UsageTracker
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlin.coroutines.cancellation.CancellationException

/** FR-USG-04 個人統計 */
class UsageRepository(private val dao: UsageDao, private val tracker: UsageTracker) {

    /** [uid] 為 null：訪客的紀錄；[from] 為區間起點（epoch ms） */
    suspend fun stats(uid: String?, from: Long): Result<UsageStats> = try {
        Result.Success(
            UsageStats(
                openCount = dao.sessionCount(uid, from),
                totalMs = dao.totalDuration(uid, from) + tracker.liveSessionMs(uid, from),
                pages = dao.topPages(uid, from, TOP_PAGES),
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Usage stats failed ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    private companion object {
        const val TAG = "UsageRepository"
        const val TOP_PAGES = 5
    }
}

data class UsageStats(val openCount: Int, val totalMs: Long, val pages: List<PageTotal>)
