package com.group11.hometide.ui.auth

import android.util.Patterns
import androidx.annotation.StringRes
import com.group11.hometide.R

/** 帳號欄位驗證（FR-AUTH-01、02）：依序檢查，回傳第一個錯誤；全部通過回傳 null。 */
object AuthValidator {

    enum class Field { NICKNAME, EMAIL, PASSWORD, CONFIRM }

    data class FieldError(val field: Field, @param:StringRes val message: Int)

    private const val NICKNAME_MAX = 20
    private val PASSWORD_LENGTH = 6..32

    /** android.util.Patterns 在 JVM 單元測試不可用，測試時注入 */
    val androidEmailMatcher: (String) -> Boolean = { Patterns.EMAIL_ADDRESS.matcher(it).matches() }

    /** nickname、email 須先去除前後空白 */
    fun validateRegister(
        nickname: String,
        email: String,
        password: String,
        confirm: String,
        isEmail: (String) -> Boolean = androidEmailMatcher,
    ): FieldError? {
        val nicknameLength = nickname.codePointCount(0, nickname.length)
        return when {
            nickname.isEmpty() -> FieldError(Field.NICKNAME, R.string.error_nickname_required)
            nicknameLength > NICKNAME_MAX -> FieldError(Field.NICKNAME, R.string.error_nickname_too_long)
            else -> validateEmail(email, isEmail)
                ?: validatePassword(password)
                ?: if (confirm != password) FieldError(Field.CONFIRM, R.string.error_password_mismatch) else null
        }
    }

    fun validateLogin(email: String, password: String, isEmail: (String) -> Boolean = androidEmailMatcher): FieldError? =
        validateEmail(email, isEmail)
            ?: if (password.isEmpty()) FieldError(Field.PASSWORD, R.string.error_password_required) else null

    private fun validateEmail(email: String, isEmail: (String) -> Boolean): FieldError? = when {
        email.isEmpty() -> FieldError(Field.EMAIL, R.string.error_email_required)
        !isEmail(email) -> FieldError(Field.EMAIL, R.string.error_email_invalid)
        else -> null
    }

    private fun validatePassword(password: String): FieldError? = when {
        password.isEmpty() -> FieldError(Field.PASSWORD, R.string.error_password_required)
        password.length !in PASSWORD_LENGTH -> FieldError(Field.PASSWORD, R.string.error_password_length)
        else -> null
    }
}

/** 使用者重新輸入時清除該欄位的錯誤（規格未寫，2-2 審查建議） */
internal fun clearErrorOnInput(layout: com.google.android.material.textfield.TextInputLayout) {
    layout.editText?.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        override fun afterTextChanged(s: android.text.Editable?) {
            layout.error = null
        }
    })
}
