package com.group11.hometide.ui.routes

import android.os.Bundle
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityMyRoutesBinding
import com.group11.hometide.ui.base.BaseActivity

class MyRoutesActivity : BaseActivity() {

    private lateinit var binding: ActivityMyRoutesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyRoutesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)
        setupBottomNav(binding.bottomNav, R.id.navRoutes)
    }
}
