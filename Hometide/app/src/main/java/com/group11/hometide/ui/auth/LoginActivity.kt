package com.group11.hometide.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityLoginBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.base.showLoading
import com.group11.hometide.ui.main.MainActivity
import com.group11.hometide.util.DateUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * P1 登入頁（規格 4.2）。
 * 一般啟動：成功後清除堆疊進 P3。從登入提示對話框進來（[intentForResult]）：成功後回傳 RESULT_OK 回到原頁（FR-AUTH-06）。
 */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val vm: LoginViewModel by viewModels {
        viewModelFactory { initializer { LoginViewModel((application as HometideApp).container.authRepository) } }
    }
    private val returnResult: Boolean get() = intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)

    private val registerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) finishWithSuccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FR-AUTH-03：已登入直接進 P3，不顯示 P1
        // TODO(第 7 週，AN-01)：冷啟動先播放售票機出票動畫
        if (!returnResult && vm.isLoggedIn) {
            goToSearch()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        binding.heroStamp.text = getString(R.string.hero_stamp, DateUtils.rocDate(LocalDate.now()))

        listOf(binding.etEmail, binding.etPassword).forEach(::clearErrorOnInput)
        binding.btnLogin.setOnClickListener { submit() }
        binding.etPasswordInput.setOnEditorActionListener { _, actionId, _ ->
            (actionId == EditorInfo.IME_ACTION_DONE).also { if (it) submit() }
        }
        binding.btnGuest.setOnClickListener {
            // 從對話框進來時本來就是訪客，直接回原頁
            if (returnResult) finish() else vm.enterGuest()
        }
        binding.tvRegister.setOnClickListener {
            registerLauncher.launch(RegisterActivity.intent(this, returnResult))
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) }
        }
    }

    private fun submit() {
        vm.login(binding.etEmailInput.text?.toString().orEmpty(), binding.etPasswordInput.text?.toString().orEmpty())
    }

    private fun render(state: AuthUiState) {
        binding.btnLogin.showLoading(state == AuthUiState.Loading, R.string.btn_login)
        binding.btnGuest.isEnabled = state != AuthUiState.Loading
        val fieldError = (state as? AuthUiState.FieldError)?.error
        binding.etEmail.error = fieldError?.takeIf { it.field == AuthValidator.Field.EMAIL }?.let { getString(it.message) }
        binding.etPassword.error = fieldError?.takeIf { it.field == AuthValidator.Field.PASSWORD }?.let { getString(it.message) }
        when (state) {
            AuthUiState.Success -> finishWithSuccess()
            is AuthUiState.Message -> {
                showSnackbar(state.message)
                vm.messageShown()
            }
            else -> Unit
        }
    }

    private fun finishWithSuccess() {
        if (returnResult) {
            setResult(RESULT_OK)
            finish()
        } else {
            goToSearch()
        }
    }

    /** P1 → P3，清除堆疊（規格 4.10；FR-AUTH-02 AC：返回鍵不會回到 P1） */
    private fun goToSearch() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_RETURN_RESULT = "return_result"

        /** 登入提示對話框「去登入」用：成功後回到原頁 */
        fun intentForResult(context: Context) =
            Intent(context, LoginActivity::class.java).putExtra(EXTRA_RETURN_RESULT, true)

        /** 登出後回 P1，清除堆疊（FR-AUTH-05） */
        fun intentAfterLogout(context: Context) =
            Intent(context, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
