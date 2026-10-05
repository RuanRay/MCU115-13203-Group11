package com.example.hw_page_switching

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.hw_page_switching.databinding.ActivityMainBinding

const val KEY_INPUT = "input_key"
const val KEY_REPLY = "reply_key"

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** STEP 01 / 05：宣告 ActivityResultLauncher 作為啟動器，並在此取得第二頁回傳的資料 */
    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val replyText = result.data?.extras?.getString(KEY_REPLY)
            if (!replyText.isNullOrEmpty()) binding.tvResult.text = replyText
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // STEP 02：把輸入文字裝進 Bundle，透過 launcher 發送並前往 SecActivity
        binding.btnSwitch.setOnClickListener {
            val bundle = Bundle().apply { putString(KEY_INPUT, binding.etInput.text.toString()) }
            startForResult.launch(Intent(this, SecActivity::class.java).putExtras(bundle))
        }
    }
}
