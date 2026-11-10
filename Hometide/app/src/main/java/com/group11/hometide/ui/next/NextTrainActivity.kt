package com.group11.hometide.ui.next

import android.os.Bundle
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityNextTrainBinding
import com.group11.hometide.ui.base.BaseActivity

class NextTrainActivity : BaseActivity() {

    private lateinit var binding: ActivityNextTrainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNextTrainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)
        setupBottomNav(binding.bottomNav, R.id.navNext)
    }
}
