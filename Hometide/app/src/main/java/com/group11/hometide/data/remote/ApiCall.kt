package com.group11.hometide.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.group11.hometide.data.remote.dto.ApiErrorBody
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

/** 呼叫 Worker 並把例外轉成 Result（規格 6.3、8.3 錯誤代碼） */
suspend fun <T> apiCall(gson: Gson, block: suspend () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Result.Failure(workerErrorOf(gson, e))
} catch (e: JsonParseException) {
    Log.w(TAG, "Worker response parse failed: ${e.javaClass.simpleName}") // 11.2：ParseError 記錄 log
    Result.Failure(AppError.ParseError)
} catch (e: IOException) {
    Result.Failure(AppError.NetworkError) // 無網路、逾時、DNS
}

private const val TAG = "ApiCall"

/** 依 Worker 錯誤外殼的 code 對應；讀不到時以 HTTP 狀態碼判斷（GUIDE 3.4-2：NOT_FOUND→404、QUOTA→429、AUTH→502） */
internal fun workerErrorOf(gson: Gson, e: HttpException): AppError {
    val code = runCatching { gson.fromJson(e.response()?.errorBody()?.string(), ApiErrorBody::class.java)?.error?.code }
        .getOrNull()
    return errorCodeOf(code) ?: when (e.code()) {
        404 -> AppError.NotFound
        429 -> AppError.QuotaExceeded
        502 -> AppError.AuthError
        else -> AppError.Unknown
    }
}

internal fun errorCodeOf(code: String?): AppError? = when (code) {
    "NETWORK" -> AppError.NetworkError
    "AUTH" -> AppError.AuthError
    "QUOTA" -> AppError.QuotaExceeded
    "NOT_FOUND" -> AppError.NotFound
    "PARSE" -> AppError.ParseError
    "UNKNOWN" -> AppError.Unknown
    else -> null
}
