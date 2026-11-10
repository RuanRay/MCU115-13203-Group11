package com.group11.hometide.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import com.group11.hometide.R
import com.group11.hometide.util.DateUtils
import java.time.LocalDate

/** 日期章（規格 4.1）：紅色圓角方框＋民國年日期，例：115.11.06、115.11.07（六）。 */
class DateStampView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatTextView(context, attrs) {

    private val corner = resources.getDimension(R.dimen.date_stamp_corner)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.date_stamp_stroke)
    }
    private val rect = RectF()

    init {
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Hometide_TicketTime_Small)
        setTextColor(ContextCompat.getColor(context, R.color.color_stamp))
        val h = resources.getDimensionPixelSize(R.dimen.date_stamp_padding_horizontal)
        val v = resources.getDimensionPixelSize(R.dimen.date_stamp_padding_vertical)
        setPadding(h, v, h, v)
        rotation = resources.getInteger(R.integer.stamp_rotation_date).toFloat()
    }

    fun setDate(date: LocalDate, showWeekday: Boolean = false) {
        val roc = DateUtils.rocDate(date)
        text = if (showWeekday) {
            context.getString(R.string.date_with_weekday, roc, DateUtils.weekdayNarrow(date))
        } else {
            roc
        }
    }

    override fun onDraw(canvas: Canvas) {
        borderPaint.color = currentTextColor
        val half = borderPaint.strokeWidth / 2
        rect.set(half, half, width - half, height - half)
        canvas.drawRoundRect(rect, corner, corner, borderPaint)
        super.onDraw(canvas)
    }
}
