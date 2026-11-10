package com.group11.hometide.util

/** Repository 回傳值（規格 6.3）。使用時 import 本類別，避免誤用 kotlin.Result。 */
sealed interface Result<out T> {
    data class Success<out T>(val data: T) : Result<T>
    data class Failure(val error: AppError) : Result<Nothing>
}
