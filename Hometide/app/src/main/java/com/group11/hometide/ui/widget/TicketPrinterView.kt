package com.group11.hometide.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout

/**
 * 出票口（AN-01 售票機、AN-04 AI 開票共用）：裁切的容器，唯一的子 View（車票）從上緣的出票口內分 5 段被送出，
 * 每段停頓一下，完全出來後微微下垂（animations.html @keyframes feedOut）。
 * 容器底部要留一點內距給下垂；系統「移除動畫」時直接顯示。
 */
class TicketPrinterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    init {
        clipChildren = true
        clipToPadding = false
    }

    private val ticket: View? get() = getChildAt(0)

    /** 印票前先藏在出票口內 */
    fun hideTicket() {
        ticket?.alpha = 0f // 尚未量測時高度為 0，先用透明藏起來
    }

    fun print(durationMs: Long, startDelayMs: Long = 0L, onEnd: () -> Unit = {}) {
        val t = ticket ?: return onEnd()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            t.alpha = 1f
            t.translationY = 0f
            t.rotation = 0f
            onEnd()
            return
        }
        // 尚未量測時等第一次版面配置後再播
        if (t.height == 0) {
            t.post { print(durationMs, startDelayMs, onEnd) }
            return
        }
        val h = t.height.toFloat()
        val px = resources.displayMetrics.density
        val y = PropertyValuesHolder.ofKeyframe(
            View.TRANSLATION_Y,
            *listOf(
                0f to -1f, .12f to -.82f, .18f to -.82f, .32f to -.60f, .38f to -.60f, .52f to -.36f, .58f to -.36f,
                .72f to -.12f, .78f to -.12f, .88f to 0f,
            ).map { (f, v) -> Keyframe.ofFloat(f, v * h) }.toTypedArray(),
            Keyframe.ofFloat(.94f, 5 * px),
            Keyframe.ofFloat(1f, 3 * px),
        )
        val r = PropertyValuesHolder.ofKeyframe(
            View.ROTATION,
            Keyframe.ofFloat(0f, 0f), Keyframe.ofFloat(.88f, 0f), Keyframe.ofFloat(.94f, -1.5f), Keyframe.ofFloat(1f, -1f),
        )
        t.pivotX = 0f
        t.pivotY = 0f
        t.translationY = -h
        t.alpha = 1f
        ObjectAnimator.ofPropertyValuesHolder(t, y, r).apply {
            duration = durationMs
            startDelay = startDelayMs
            interpolator = LinearInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }
}
