package com.group11.hometide.ui.auth

import com.group11.hometide.R
import com.group11.hometide.ui.auth.AuthValidator.Field
import com.group11.hometide.ui.auth.AuthValidator.FieldError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** FR-AUTH-01、02 欄位驗證；TC-01 */
class AuthValidatorTest {

    // JVM 測試無 android.util.Patterns，以簡化規則代替
    private val isEmail: (String) -> Boolean = { Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(it) }

    private fun register(
        nickname: String = "小明",
        email: String = "a@b.tw",
        password: String = "123456",
        confirm: String = password,
    ) = AuthValidator.validateRegister(nickname, email, password, confirm, isEmail)

    @Test
    fun valid_returnsNull() = assertNull(register())

    @Test
    fun tc01_passwordMismatch() =
        assertEquals(FieldError(Field.CONFIRM, R.string.error_password_mismatch), register(confirm = "654321"))

    @Test
    fun nickname_requiredAndMax20() {
        assertEquals(FieldError(Field.NICKNAME, R.string.error_nickname_required), register(nickname = ""))
        assertNull(register(nickname = "一".repeat(20)))
        assertEquals(FieldError(Field.NICKNAME, R.string.error_nickname_too_long), register(nickname = "一".repeat(21)))
        // emoji 以字元（code point）計算
        assertNull(register(nickname = "😀".repeat(20)))
    }

    @Test
    fun email_requiredAndFormat() {
        assertEquals(FieldError(Field.EMAIL, R.string.error_email_required), register(email = ""))
        assertEquals(FieldError(Field.EMAIL, R.string.error_email_invalid), register(email = "abc"))
    }

    @Test
    fun password_length6to32() {
        assertEquals(FieldError(Field.PASSWORD, R.string.error_password_required), register(password = ""))
        assertEquals(FieldError(Field.PASSWORD, R.string.error_password_length), register(password = "12345"))
        assertNull(register(password = "x".repeat(32)))
        assertEquals(FieldError(Field.PASSWORD, R.string.error_password_length), register(password = "x".repeat(33)))
    }

    @Test
    fun checksInOrder_firstErrorWins() =
        assertEquals(Field.NICKNAME, register(nickname = "", email = "", password = "", confirm = "x")?.field)

    @Test
    fun login_rules() {
        assertEquals(FieldError(Field.EMAIL, R.string.error_email_required), AuthValidator.validateLogin("", "x", isEmail))
        assertEquals(FieldError(Field.EMAIL, R.string.error_email_invalid), AuthValidator.validateLogin("abc", "x", isEmail))
        assertEquals(FieldError(Field.PASSWORD, R.string.error_password_required), AuthValidator.validateLogin("a@b.tw", "", isEmail))
        assertNull(AuthValidator.validateLogin("a@b.tw", "x", isEmail))
    }
}
