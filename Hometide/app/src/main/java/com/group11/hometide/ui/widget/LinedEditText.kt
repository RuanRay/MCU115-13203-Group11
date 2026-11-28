package com.group11.hometide.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.group11.hometide.R

/** P7 內容欄的橫線信紙底（4.9、feedback.png）：每行文字下方一條細線，填滿整個高度 */
class LinedEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextInputEditText(context, attrs) {

    private val linePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.color_outline)
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        // 第一行基線往下一點畫線，之後每隔一個行高一條，畫到底
        var y = baseline + lineHeight / LINE_OFFSET_DIVISOR
        val bottom = height + scrollY
        while (y < bottom) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), linePaint)
            y += lineHeight
        }
        super.onDraw(canvas)
    }

    private companion object {
        const val LINE_OFFSET_DIVISOR = 4
    }
}
