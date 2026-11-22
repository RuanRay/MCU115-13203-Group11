package com.group11.hometide.data.remote

import com.google.gson.Gson
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result

/**
 * 規格 11.2 QuotaExceeded：收到 QUOTA 後 60 秒內只讀快取。
 * 所有 Worker 呼叫都經過同一個 gate（AppContainer 共用一份），任何一支被限流，其他呼叫也一起冷卻。
 */
class QuotaGate(private val gson: Gson, private val now: () -> Long = System::currentTimeMillis) {

    @Volatile private var until = 0L

    /** 冷卻中直接回 QuotaExceeded 不打網路；收到 QuotaExceeded 就開始冷卻 */
    suspend fun <T> call(block: suspend () -> T): Result<T> {
        if (now() < until) return Result.Failure(AppError.QuotaExceeded)
        return apiCall(gson, block).also {
            if (it is Result.Failure && it.error == AppError.QuotaExceeded) until = now() + COOLDOWN_MS
        }
    }

    companion object {
        const val COOLDOWN_MS = 60_000L
    }
}
