package com.group11.hometide

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.group11.hometide.ui.widget.DateStampView
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        // 1-4 元件展示（暫時），1-5 移除；日期對照 home.png／result.png
        findViewById<DateStampView>(R.id.demoDateStamp).setDate(LocalDate.of(2026, 11, 6))
        findViewById<DateStampView>(R.id.demoDateStampWeekday).setDate(LocalDate.of(2026, 11, 7), showWeekday = true)
    }
}