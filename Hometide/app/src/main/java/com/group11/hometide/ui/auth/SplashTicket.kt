package com.group11.hometide.ui.auth

import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.group11.hometide.R
import com.group11.hometide.databinding.LayoutSplashTicketBinding
import com.group11.hometide.util.DateUtils
import java.time.LocalDate

/**
 * AN-01 售票機出票（約 900 ms，規格 5.4）：冷啟動時蓋在第一個畫面上。指示燈亮、機器輕震，車票分段印出後下垂，
 * 最後淡出（[onPrinted] 在印完時呼叫；[fadeOut] 為 false 時由呼叫端接手，例如直接切到首頁）。
 */
object SplashTicket {

    private const val PRINT_MS = 900L
    // 等系統啟動畫面淡出後才開始出票
    private const val PRINT_DELAY_MS = 350L
    private const val BUZZ_MS = 810L
    private const val BUZZ_LEAD_MS = 120L // 機器先震、再出票
    private const val HOLD_MS = 150L
    private const val FADE_MS = 250L

    /** 只在 App 程序剛啟動時播一次（實作者確認） */
    private var played = false

    fun consumeColdStart(): Boolean = !played.also { played = true }

    fun show(activity: Activity, fadeOut: Boolean = true, onPrinted: () -> Unit = {}) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            onPrinted()
            return
        }
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val b = LayoutSplashTicketBinding.inflate(activity.layoutInflater, content, false)
        content.addView(b.root)
        b.tvSplashDate.text = DateUtils.rocDate(LocalDate.now())
        b.vLamp.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.color_accent))
        // 機器輕震（±1dp，90 ms 一次）
        val px = activity.resources.displayMetrics.density
        ObjectAnimator.ofPropertyValuesHolder(
            b.vMachine,
            PropertyValuesHolder.ofKeyframe(
                View.TRANSLATION_X,
                *(0..36).map { i -> Keyframe.ofFloat(i / 36f, if (i % 4 == 1) px else if (i % 4 == 3) -px else 0f) }.toTypedArray(),
            ),
        ).apply {
            startDelay = PRINT_DELAY_MS - BUZZ_LEAD_MS
            interpolator = LinearInterpolator()
        }.setDuration(BUZZ_MS).start()
        b.printerSplash.post {
            b.printerSplash.hideTicket()
            b.printerSplash.print(PRINT_MS, PRINT_DELAY_MS) {
                b.vLamp.backgroundTintList = null
                b.root.postDelayed({
                    if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                    onPrinted()
                    if (fadeOut) {
                        b.root.animate().alpha(0f).setDuration(FADE_MS).withEndAction { content.removeView(b.root) }.start()
                    }
                }, HOLD_MS)
            }
        }
    }
}
