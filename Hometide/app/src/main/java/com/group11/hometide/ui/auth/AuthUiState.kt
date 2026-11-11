package com.group11.hometide.ui.auth

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result

/** P1、P2 共用的畫面狀態 */
sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState

    /** 顯示在欄位下方（11.2：Firebase 帳號錯誤顯示於欄位下方） */
    data class FieldError(val error: AuthValidator.FieldError) : AuthUiState

    /** 不屬於任何欄位的錯誤，以 Snackbar 顯示一次 */
    data class Message(@param:StringRes val message: Int) : AuthUiState
}

/** Repository 結果轉畫面狀態 */
internal fun Result<*>.toUiState(): AuthUiState = when (this) {
    is Result.Success -> AuthUiState.Success
    is Result.Failure -> when (error) {
        AppError.EmailInUse ->
            AuthUiState.FieldError(AuthValidator.FieldError(AuthValidator.Field.EMAIL, R.string.error_email_in_use))
        AppError.InvalidCredentials ->
            AuthUiState.FieldError(AuthValidator.FieldError(AuthValidator.Field.PASSWORD, R.string.error_invalid_credentials))
        AppError.TooManyAttempts -> AuthUiState.Message(R.string.error_too_many_attempts)
        AppError.NetworkError -> AuthUiState.Message(R.string.error_network)
        else -> AuthUiState.Message(R.string.error_unknown)
    }
}
