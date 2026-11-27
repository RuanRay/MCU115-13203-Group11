package com.group11.hometide.ui.widget

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/** AN-15 三點跳動（900 ms 循環，三點錯開上下跳）；用作解析中按鈕的 icon */
class BouncingDotsDrawable(color: Int, private val dotPx: Float) : Drawable(), Animatable {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private var phase = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CYCLE_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidateSelf() }
    }

    override fun getIntrinsicWidth() = (dotPx * 5).toInt()
    override fun getIntrinsicHeight() = (dotPx * 3).toInt()

    override fun draw(canvas: Canvas) {
        val r = dotPx / 2
        val baseY = bounds.exactCenterY() + dotPx / 2
        repeat(DOTS) { i ->
            // 每點落後 1/3 週期；只取正半波，讓點在底部停一下
            val lift = max(0f, sin(((phase - i / 3f) * 2 * PI).toFloat()))
            canvas.drawCircle(bounds.left + r + i * dotPx * 2, baseY - lift * dotPx, r, paint)
        }
    }

    override fun start() = animator.start()
    override fun stop() = animator.cancel()
    override fun isRunning() = animator.isRunning

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val DOTS = 3
        const val CYCLE_MS = 900L
    }
}
