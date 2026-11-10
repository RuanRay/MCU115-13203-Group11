package com.group11.hometide.ui.auth

import android.content.Intent
import android.os.Bundle
import com.group11.hometide.databinding.ActivityLoginBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.main.MainActivity

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        // TODO(第 2 週，FR-AUTH-02)：登入驗證；目前兩個按鈕都直接進 P3
        binding.btnLogin.setOnClickListener { goToSearch() }
        binding.btnGuest.setOnClickListener { goToSearch() } // FR-AUTH-04
    }

    /** P1 → P3（主頁的查詢分頁），清除堆疊（規格 4.10）。 */
    private fun goToSearch() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }
}
