package com.group11.hometide.ui.widget

import android.view.animation.OvershootInterpolator
import android.view.HapticFeedbackConstants
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.withStyledAttributes
import com.group11.hometide.R

/** 印章（規格 4.1）：圓形單線框（依 guest_prompt.png）＋宋體文字，文字可用 \n 分行。 */
class StampView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    enum class Tone(@param:ColorRes val color: Int) {
        RED(R.color.color_stamp),
        GRAY(R.color.color_on_surface_muted),
        GREEN(R.color.color_success),
        MUSTARD(R.color.color_accent),
    }

    var text: String = ""
        set(value) {
            field = value
            contentDescription = value.replace('\n', ' ')
            invalidate()
        }

    var tone: Tone = Tone.RED
        set(value) {
            field = value
            invalidate()
        }

    private val size = resources.getDimensionPixelSize(R.dimen.stamp_size)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.stamp_stroke)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.stamp_text_size)
        textAlign = Paint.Align.CENTER
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.noto_serif_tc_black) }
            .getOrNull() ?: Typeface.SERIF
    }

    init {
        rotation = resources.getInteger(R.integer.stamp_rotation_status).toFloat()
        context.withStyledAttributes(attrs, R.styleable.StampView) {
            text = getString(R.styleable.StampView_stampText).orEmpty()
            tone = Tone.entries[getInt(R.styleable.StampView_stampColor, Tone.RED.ordinal)]
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val c = ContextCompat.getColor(context, tone.color)
        ringPaint.color = c
        textPaint.color = c
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, minOf(cx, cy) - ringPaint.strokeWidth / 2, ringPaint)

        val lines = text.split('\n')
        val lineHeight = textPaint.fontSpacing
        val fm = textPaint.fontMetrics
        val top = cy - lineHeight * lines.size / 2
        lines.forEachIndexed { i, line ->
            canvas.drawText(line, cx, top + i * lineHeight - fm.ascent, textPaint)
        }
    }

    /**
     * 蓋章（AN-07、AN-04、AN-10 共用，220 ms）：從 1.8 倍、偏轉 -18° 落下到原本角度，並觸發觸覺回饋（5.4 共同原則）。
     * 系統「移除動畫」時直接顯示。
     */
    fun playStampAnimation(startDelayMs: Long = 0L) {
        val endRotation = resources.getInteger(R.integer.stamp_rotation_status).toFloat()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            alpha = 1f
            return
        }
        scaleX = STAMP_FROM_SCALE
        scaleY = STAMP_FROM_SCALE
        rotation = endRotation + STAMP_FROM_ROTATION
        alpha = 0f
        animate().scaleX(1f).scaleY(1f).rotation(endRotation).alpha(1f)
            .setStartDelay(startDelayMs).setDuration(STAMP_MS).setInterpolator(OvershootInterpolator())
            .withEndAction { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
            .start()
    }

    private companion object {
        const val STAMP_FROM_SCALE = 1.8f
        const val STAMP_FROM_ROTATION = -18f
        const val STAMP_MS = 220L
    }
}
