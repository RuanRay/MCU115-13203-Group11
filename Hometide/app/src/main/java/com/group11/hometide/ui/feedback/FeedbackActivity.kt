package com.group11.hometide.ui.feedback

import android.os.Bundle
import com.group11.hometide.databinding.ActivityFeedbackBinding
import com.group11.hometide.ui.base.BaseActivity

class FeedbackActivity : BaseActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
    }
}
