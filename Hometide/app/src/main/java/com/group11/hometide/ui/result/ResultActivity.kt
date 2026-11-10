package com.group11.hometide.ui.result

import android.os.Bundle
import com.group11.hometide.databinding.ActivityResultBinding
import com.group11.hometide.ui.base.BaseActivity

class ResultActivity : BaseActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
    }
}
