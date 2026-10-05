package com.example.hw_ordering_system

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.hw_ordering_system.databinding.ActivityConfirmBinding

class ConfirmActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityConfirmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mainMeal = intent.getStringExtra(KEY_MAIN)
        val sides = intent.getStringArrayListExtra(KEY_SIDES).orEmpty()
        val drink = intent.getStringExtra(KEY_DRINK)
        val summary = orderSummary(mainMeal, sides, drink)
        binding.tvSummary.text = summary

        binding.btnConfirm.setOnClickListener {
            // 任何一項沒選：只跳 Toast，不開對話框
            if (!isOrderComplete(mainMeal, sides, drink)) {
                Toast.makeText(this, R.string.order_incomplete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // 都有選：列出所選項目，提交後帶著結果回主畫面
            AlertDialog.Builder(this)
                .setTitle(R.string.submit_order)
                .setMessage(summary)
                .setPositiveButton(R.string.submit) { _, _ ->
                    setResult(RESULT_OK, Intent().putExtra(KEY_SUBMITTED, true))
                    finish()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
