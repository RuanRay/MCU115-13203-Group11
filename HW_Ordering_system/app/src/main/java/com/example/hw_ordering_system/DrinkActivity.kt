package com.example.hw_ordering_system

import android.content.Intent
import android.os.Bundle
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.example.hw_ordering_system.databinding.ActivityDrinkBinding

class DrinkActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityDrinkBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val options = binding.rgDrink.children.filterIsInstance<RadioButton>()

        // 首次進入時還原先前選的飲料；旋轉螢幕則由 View 自己保存勾選狀態
        if (savedInstanceState == null) {
            val current = intent.getStringExtra(KEY_DRINK)
            options.firstOrNull { it.label == current }?.isChecked = true
        }

        // 沒選就直接返回，主畫面維持原狀
        binding.btnDone.setOnClickListener {
            options.firstOrNull { it.isChecked }?.let {
                setResult(RESULT_OK, Intent().putExtra(KEY_DRINK, it.label))
            }
            finish()
        }
    }
}
