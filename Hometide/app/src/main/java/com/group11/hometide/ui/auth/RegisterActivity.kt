package com.group11.hometide.ui.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.ActivityRegisterBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.base.showLoading
import com.group11.hometide.ui.main.MainActivity
import com.group11.hometide.util.labelRes
import kotlinx.coroutines.launch

/** P2 註冊頁（規格 4.3、FR-AUTH-01） */
class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val vm: RegisterViewModel by viewModels {
        viewModelFactory { initializer { RegisterViewModel((application as HometideApp).container.authRepository) } }
    }
    private val operators = Operator.entries
    private var selectedOperator = Operator.TRA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        listOf(binding.etNickname, binding.etEmail, binding.etPassword, binding.etConfirm).forEach(::clearErrorOnInput)

        val labels = operators.map { getString(it.labelRes) }
        binding.spOperator.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
        selectedOperator = savedInstanceState?.getString(KEY_OPERATOR)?.let(Operator::valueOf) ?: Operator.TRA
        binding.spOperator.setText(getString(selectedOperator.labelRes), false)
        binding.spOperator.setOnItemClickListener { _, _, position, _ -> selectedOperator = operators[position] }

        binding.btnRegister.setOnClickListener {
            vm.register(
                nickname = binding.etNicknameInput.text?.toString().orEmpty(),
                email = binding.etEmailInput.text?.toString().orEmpty(),
                password = binding.etPasswordInput.text?.toString().orEmpty(),
                confirm = binding.etConfirmInput.text?.toString().orEmpty(),
                operator = selectedOperator,
            )
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.state.collect(::render) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_OPERATOR, selectedOperator.name)
    }

    private fun render(state: AuthUiState) {
        binding.btnRegister.showLoading(state == AuthUiState.Loading, R.string.btn_register)
        val error = (state as? AuthUiState.FieldError)?.error
        val fields = mapOf(
            AuthValidator.Field.NICKNAME to binding.etNickname,
            AuthValidator.Field.EMAIL to binding.etEmail,
            AuthValidator.Field.PASSWORD to binding.etPassword,
            AuthValidator.Field.CONFIRM to binding.etConfirm,
        )
        fields.forEach { (field, layout) ->
            layout.error = error?.takeIf { it.field == field }?.let { getString(it.message) }
        }
        when (state) {
            AuthUiState.Success -> onRegistered()
            is AuthUiState.Message -> {
                showSnackbar(state.message)
                vm.messageShown()
            }
            else -> Unit
        }
    }

    /** 自動登入並清除堆疊進 P3（4.10）；從登入提示對話框一路進來時回到原頁 */
    private fun onRegistered() {
        if (intent.getBooleanExtra(EXTRA_RETURN_RESULT, false)) {
            setResult(RESULT_OK)
        } else {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
        }
        finish()
    }

    companion object {
        private const val EXTRA_RETURN_RESULT = "return_result"
        private const val KEY_OPERATOR = "operator"

        fun intent(context: Context, returnResult: Boolean) =
            Intent(context, RegisterActivity::class.java).putExtra(EXTRA_RETURN_RESULT, returnResult)
    }
}
