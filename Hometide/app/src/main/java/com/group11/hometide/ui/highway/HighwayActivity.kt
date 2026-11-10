package com.group11.hometide.ui.highway

import android.os.Bundle
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityHighwayBinding
import com.group11.hometide.ui.base.BaseActivity

class HighwayActivity : BaseActivity() {

    private lateinit var binding: ActivityHighwayBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHighwayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)
        setupBottomNav(binding.bottomNav, R.id.navHighway)
    }
}
