package com.group11.smartpomodorofocustimer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {

    private lateinit var timeText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    /** CH13: dynamic receiver for the service's custom TIMER_UPDATE broadcast. */
    private val tickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val left = intent.getIntExtra(TimerService.EXTRA_REMAINING, 0)
            if (left > 0) showRunning(left) else showIdle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        timeText = findViewById(R.id.timeText)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)
        showIdle()

        registerReceiver(tickReceiver, IntentFilter(TimerService.ACTION_TICK), RECEIVER_NOT_EXPORTED)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }

        startButton.setOnClickListener {
            startService(
                Intent(this, TimerService::class.java)
                    .putExtra(TimerService.EXTRA_SECONDS, TimerService.FOCUS_SECONDS)
            )
            showRunning(TimerService.FOCUS_SECONDS)
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopService(Intent(this, TimerService::class.java))
            showIdle()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(tickReceiver)
        super.onDestroy()
    }

    private fun showIdle() {
        timeText.text = TimerService.formatTime(TimerService.FOCUS_SECONDS)
        statusText.setText(R.string.status_ready)
        statusText.setTextColor(getColor(R.color.green))
        startButton.isEnabled = true
    }

    private fun showRunning(seconds: Int) {
        timeText.text = TimerService.formatTime(seconds)
        statusText.setText(R.string.status_focusing)
        statusText.setTextColor(getColor(R.color.red))
        startButton.isEnabled = false
    }
}
