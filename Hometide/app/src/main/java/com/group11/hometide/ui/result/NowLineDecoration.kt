package com.group11.hometide.ui.result

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.RecyclerView
import com.group11.hometide.R

/**
 * 「現在 HH:mm」紅線（規格 4.6 `vNowLine`、FR-RES-03、result.png）：插在 [index] 這一班之前，
 * 留出一行高度畫文字與橫線。index 為 null 時不畫（非今天）。AN-14 位置動畫於第 7 週。
 */
class NowLineDecoration(context: Context) : RecyclerView.ItemDecoration() {

    var index: Int? = null
    var label: String = ""

    private val gap = context.resources.getDimensionPixelSize(R.dimen.now_line_gap)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_stamp)
        strokeWidth = context.resources.getDimension(R.dimen.now_line_width)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.color_stamp)
        textSize = context.resources.getDimension(R.dimen.now_line_text)
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.noto_serif_tc_bold) }.getOrNull() ?: Typeface.SERIF
    }
    private val textGap = context.resources.getDimension(R.dimen.now_line_text_gap)

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val pos = parent.getChildAdapterPosition(view)
        val i = index ?: return
        val count = parent.adapter?.itemCount ?: 0
        if (pos == i) outRect.top = gap
        if (i >= count && pos == count - 1) outRect.bottom = gap // 全部已開出：畫在最後
    }

    override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val i = index ?: return
        val count = parent.adapter?.itemCount ?: return
        if (count == 0) return
        for (k in 0 until parent.childCount) {
            val child = parent.getChildAt(k)
            val pos = parent.getChildAdapterPosition(child)
            val y = when {
                pos == i -> child.top - gap / 2f
                i >= count && pos == count - 1 -> child.bottom + gap / 2f
                else -> continue
            }
            val baseline = y - (textPaint.ascent() + textPaint.descent()) / 2
            val left = parent.paddingLeft.toFloat()
            c.drawText(label, left, baseline, textPaint)
            c.drawLine(left + textPaint.measureText(label) + textGap, y, (parent.width - parent.paddingRight).toFloat(), y, linePaint)
        }
    }
}
