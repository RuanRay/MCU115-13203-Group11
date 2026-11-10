package com.group11.hometide.ui.auth

import android.os.Bundle
import com.group11.hometide.databinding.ActivityRegisterBinding
import com.group11.hometide.ui.base.BaseActivity

class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
    }
}
