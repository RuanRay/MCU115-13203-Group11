package com.group11.hometide.ui.profile

import android.os.Bundle
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityProfileBinding
import com.group11.hometide.ui.base.BaseActivity

class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)
        setupBottomNav(binding.bottomNav, R.id.navProfile)
    }
}
