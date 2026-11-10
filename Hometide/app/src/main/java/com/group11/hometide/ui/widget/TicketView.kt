package com.group11.hometide.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import com.group11.hometide.R

/**
 * 硬式車票（規格 4.1）。第一個子 View 是本體、第二個是存根（寬 56–92dp，由 layout 決定），
 * 兩者交界畫虛線撕線，撕線上下兩端挖半圓缺口。
 */
class TicketView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    enum class Style(@param:ColorRes val body: Int, @param:ColorRes val onBody: Int) {
        MAIN(R.color.color_primary, R.color.color_on_primary),
        PAPER(R.color.color_ticket, R.color.color_on_background),
        BLANK(R.color.color_surface, R.color.color_on_background),
    }

    var ticketStyle: Style = Style.PAPER
        set(value) {
            field = value
            invalidate()
        }

    private val corner = resources.getDimension(R.dimen.corner_ticket)
    private val notchRadius = resources.getDimension(R.dimen.tear_notch_radius)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    // 描邊只畫在 shape 內側：clip 到 shape 後用 2 倍寬度畫，留下內側一半
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.ticket_outline_width) * 2
    }
    private val tearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.tear_line_width)
        pathEffect = DashPathEffect(
            floatArrayOf(
                resources.getDimension(R.dimen.tear_dash_length),
                resources.getDimension(R.dimen.tear_dash_gap),
            ),
            0f,
        )
    }
    private val shape = Path()
    private val notches = Path()

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        context.withStyledAttributes(attrs, R.styleable.TicketView) {
            ticketStyle = Style.entries[getInt(R.styleable.TicketView_ticketStyle, Style.PAPER.ordinal)]
        }
    }

    /** 撕線位置＝本體右緣；只有一個子 View 時沒有撕線與缺口。 */
    private val tearX: Float?
        get() = if (childCount >= 2) getChildAt(0).right.toFloat() else null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val x = tearX

        shape.reset()
        shape.addRoundRect(0f, 0f, w, h, corner, corner, Path.Direction.CW)
        if (x != null) {
            notches.reset()
            notches.addCircle(x, 0f, notchRadius, Path.Direction.CW)
            notches.addCircle(x, h, notchRadius, Path.Direction.CW)
            shape.op(notches, Path.Op.DIFFERENCE)
        }

        fillPaint.color = color(ticketStyle.body)
        canvas.drawPath(shape, fillPaint)

        if (ticketStyle == Style.BLANK) {
            // 空白票：卡片底＋框線，存根填主色（home.png）
            canvas.withClip(shape) {
                outlinePaint.color = color(R.color.color_outline)
                drawPath(shape, outlinePaint)
                if (x != null) {
                    fillPaint.color = color(R.color.color_primary)
                    drawRect(x, 0f, w, h, fillPaint)
                }
            }
        } else if (x != null) {
            // 撕線＝票上文字色 50% 透明（畫面圖量測，實作者確認）
            tearPaint.color = ColorUtils.setAlphaComponent(color(ticketStyle.onBody), 128)
            canvas.drawLine(x, notchRadius, x, h - notchRadius, tearPaint)
        }
    }

    private fun color(@ColorRes id: Int) = ContextCompat.getColor(context, id)
}
