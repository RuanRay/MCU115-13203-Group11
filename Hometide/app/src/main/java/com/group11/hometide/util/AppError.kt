package com.group11.hometide.util

/** 統一錯誤類型（規格 6.3）；對應訊息見規格第 11 章，接 UI 時再對照 strings.xml。 */
sealed interface AppError {
    data object NetworkError : AppError
    data object AuthError : AppError
    data object QuotaExceeded : AppError
    data object NotFound : AppError
    data object ParseError : AppError
    data object Unknown : AppError

    // 11.2「Firebase 帳號錯誤」（FR-AUTH-01、02）
    data object EmailInUse : AppError
    data object InvalidCredentials : AppError
    data object TooManyAttempts : AppError
}
