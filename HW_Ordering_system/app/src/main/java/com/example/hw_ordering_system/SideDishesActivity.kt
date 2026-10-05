package com.example.hw_ordering_system

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import com.example.hw_ordering_system.databinding.ActivitySideDishesBinding

class SideDishesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySideDishesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val options = binding.llSideDishes.children.filterIsInstance<CheckBox>()

        // 首次進入時還原先前勾的副餐；旋轉螢幕則由 View 自己保存勾選狀態
        if (savedInstanceState == null) {
            val current = intent.getStringArrayListExtra(KEY_SIDES).orEmpty()
            options.forEach { it.isChecked = it.label in current }
        }

        // 全部取消勾選也照實回傳空清單，「至少一項」留給確認頁驗證
        binding.btnDone.setOnClickListener {
            val picked = ArrayList(options.filter { it.isChecked }.map { it.label }.toList())
            setResult(RESULT_OK, Intent().putStringArrayListExtra(KEY_SIDES, picked))
            finish()
        }
    }
}
