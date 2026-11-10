package com.group11.hometide.ui.search

import android.os.Bundle
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivitySearchBinding
import com.group11.hometide.ui.base.BaseActivity

class SearchActivity : BaseActivity() {

    private lateinit var binding: ActivitySearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)
        setupBottomNav(binding.bottomNav, R.id.navSearch)
    }
}
