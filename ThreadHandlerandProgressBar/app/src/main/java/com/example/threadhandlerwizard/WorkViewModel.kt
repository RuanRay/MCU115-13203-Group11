package com.example.threadhandlerwizard

import android.app.Application
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.AndroidViewModel

enum class Phase { IDLE, PREPARING, WORKING, DONE, CANCELED }

class WorkViewModel(app: Application) : AndroidViewModel(app) {
    private val handlerThread = HandlerThread("VM-Work").apply { start() }
    private val worker = Handler(handlerThread.looper)
    private val main = Handler(Looper.getMainLooper())

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _phase = MutableLiveData(Phase.IDLE)
    val phase: LiveData<Phase> = _phase

    // 每次 start 產生新 token；cancel 換掉 token，舊工作的回報就全部作廢（只在主執行緒讀寫）
    // 背景音樂：只在主執行緒操作，開始時播放、完成或取消時停止
    private var player: MediaPlayer? = null

    @Volatile private var token = Any()

    val isActive get() = _phase.value == Phase.PREPARING || _phase.value == Phase.WORKING

    fun start() {
        if (isActive) return
        val t = Any().also { token = it }
        _phase.value = Phase.PREPARING
        _progress.value = 0
        startMusic()
        worker.post {
            if (!sleep(t, PREPARE_MS)) return@post   // 準備工作放這裡
            emit(t) { _phase.value = Phase.WORKING }
            for (i in 1..100) {
                if (!sleep(t, STEP_MS)) return@post  // 真正的背景工作放這裡
                emit(t) { _progress.value = i }
            }
            emit(t) { _phase.value = Phase.DONE; stopMusic() }
        }
    }

    fun cancel() {
        if (!isActive) return
        token = Any()
        _phase.value = Phase.CANCELED
        stopMusic()
    }

    private fun startMusic() {
        stopMusic()
        player = MediaPlayer.create(getApplication(), R.raw.background_music)?.apply {
            isLooping = true
            start()
        }
    }

    private fun stopMusic() {
        player?.release()
        player = null
    }

    // 切成小段睡，按 Cancel 後 50ms 內就會停；回傳 false 表示已被取消
    private fun sleep(t: Any, ms: Long): Boolean {
        var left = ms
        while (left > 0) {
            if (token !== t) return false
            Thread.sleep(minOf(left, 50))
            left -= 50
        }
        return token === t
    }

    // 回到主執行緒再確認一次 token，避免取消後還有舊進度蓋掉「已取消」
    private fun emit(t: Any, block: () -> Unit) = main.post { if (token === t) block() }

    override fun onCleared() {
        token = Any()
        stopMusic()
        handlerThread.quitSafely()
    }

    companion object {
        const val PREPARE_MS = 10000L
        const val STEP_MS = 3500L
    }
}
