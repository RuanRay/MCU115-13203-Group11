package com.example.hw_page_switching

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.hw_page_switching.databinding.ActivitySecBinding

class SecActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivitySecBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 取出第一頁放在 Bundle 裡的文字
        binding.tvReceived.text = intent.extras?.getString(KEY_INPUT).orEmpty()

        binding.btnReturn.setOnClickListener {
            val bundle = Bundle().apply { putString(KEY_REPLY, binding.etReply.text.toString()) }
            // STEP 03：setResult() 儲存要回傳的資料
            setResult(RESULT_OK, Intent().putExtras(bundle))
            // STEP 04：finish() 結束 SecActivity，回到 MainActivity
            finish()
        }
    }
}
