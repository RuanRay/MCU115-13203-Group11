package com.example.computer_project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 把一串按鍵敲進計算機：`<` 是 ⌫、`C` 是 AC、其餘照字面 */
private fun keys(input: String): Calculator = Calculator().apply {
    input.forEach { k ->
        when (k) {
            in '0'..'9' -> digit(k)
            '.' -> dot()
            '+', '−', '×', '÷' -> operator(k)
            '=' -> equal()
            '%' -> percent()
            '±' -> negate()
            '<' -> backspace()
            'C' -> clear()
            ' ' -> Unit
            else -> error("未知按鍵 $k")
        }
    }
}

class CalculatorLogicTest {

    @Test
    fun applyOp_四則運算() {
        assertEquals(5.0, applyOp(2.0, '+', 3.0), 0.0)
        assertEquals(-1.0, applyOp(2.0, '−', 3.0), 0.0)
        assertEquals(6.0, applyOp(2.0, '×', 3.0), 0.0)
        assertEquals(0.5, applyOp(1.0, '÷', 2.0), 0.0)
        assertFalse(applyOp(1.0, '÷', 0.0).isFinite()) // 除以 0 → 由錯誤狀態接手
    }

    @Test
    fun formatNumber_顯示格式() {
        assertEquals("3", formatNumber(3.0))
        assertEquals("0.3", formatNumber(0.1 + 0.2)) // 浮點誤差尾數要消掉
        assertEquals("-2.5", formatNumber(-2.5))
        assertEquals(ERROR_TEXT, formatNumber(Double.NaN))
    }

    @Test
    fun evaluate_先乘除後加減() {
        assertEquals(14.0, evaluate(listOf(2.0, 3.0, 4.0), listOf('+', '×')), 0.0)
        assertEquals(4.0, evaluate(listOf(10.0, 2.0, 3.0), listOf('−', '×')), 0.0)
        assertEquals(10.0, evaluate(listOf(2.0, 3.0, 4.0), listOf('×', '+')), 0.0)
        assertEquals(10.0, evaluate(listOf(100.0, 5.0, 2.0), listOf('÷', '÷')), 0.0) // 同優先權由左至右
        assertEquals(7.0, evaluate(listOf(7.0), emptyList()), 0.0)
    }

    @Test
    fun 算式顯示_輸入中與按下等號後() {
        assertEquals("", keys("12").expression)      // 還沒按運算子，上方留白
        assertEquals("12 + 3", keys("12+3").expression)
        assertEquals("2 + 3 ×", keys("2+3×").expression)

        val done = keys("2+3×4=")
        assertEquals("2 + 3 × 4 =", done.expression)
        assertEquals("14", done.display)
    }

    @Test
    fun 按下運算子後_結果區歸零() {
        val c = keys("12+")
        assertEquals("12 +", c.expression)
        assertEquals("0", c.display)                   // 不留著 12，避免誤讀成還在輸入
        assertEquals("0", keys("2+3×").display)
        assertEquals("0", keys("2+3=×").display)       // 算完再接運算子也一樣
        assertEquals("14", keys("2+3×4=").display)     // 歸零不影響實際運算
    }

    @Test
    fun 算式不完整時_按等號不運算只提醒() {
        val c = keys("2+=")
        assertEquals(NOTICE_NEED_NUMBER, c.notice)
        assertEquals("0", c.display)                   // 沒有偷算成 2 + 2
        assertEquals("2 +", c.expression)              // 算式原封不動，可以接著輸入

        c.digit('3')
        assertEquals("", c.notice)                     // 一輸入就收掉提示
        c.equal()
        assertEquals("5", c.display)
        assertEquals("", c.notice)

        assertEquals(NOTICE_NEED_NUMBER, keys("2+3×=").notice)
        assertEquals("", keys("2+3=").notice)          // 算式完整時不提醒
        assertEquals("", keys("2+=C").notice)          // AC 也收掉提示
        assertEquals("", keys("2+=<").notice)          // 退格也收掉提示
    }

    @Test
    fun 連按等號_重複上次運算() {
        val c = keys("5+3=")
        assertEquals("8", c.display)
        c.equal()
        assertEquals("11", c.display)
        assertEquals("8 + 3 =", c.expression)
        c.equal()
        assertEquals("14", c.display)
    }

    @Test
    fun 連按運算子_只換符號() {
        assertEquals("6", keys("2+×3=").display)
    }

    @Test
    fun 百分比_情境語意() {
        assertEquals("220", keys("200+10%=").display)  // 加減：取前段結果的百分比
        assertEquals("180", keys("200−10%=").display)
        assertEquals("20", keys("200×10%=").display)   // 乘除：單純除以 100
        assertEquals("0.5", keys("50%").display)
    }

    @Test
    fun 正負號切換() {
        assertEquals("-5", keys("5±").display)
        assertEquals("5", keys("5±±").display)
        assertEquals("-5", keys("2+3=±").display)      // 結果也能切
        assertEquals("-12", keys("1±2").display)       // 切完繼續輸入，符號保留
        assertEquals("-1", keys("3−4=").display)
    }

    @Test
    fun 退格() {
        val afterOp = keys("12+<")                     // 剛按完運算子 → 退掉運算子，取回運算元
        assertEquals("12", afterOp.display)
        assertEquals("", afterOp.expression)
        assertEquals("1", keys("12+<<").display)

        assertEquals("1", keys("2+3×4=<").display)     // 算完的結果也能退格（14 → 1）
        assertEquals("0", keys("7<").display)          // 刪到空 → 0
    }

    @Test
    fun 除以零_進入錯誤狀態並可恢復() {
        val c = keys("5÷0=")
        assertEquals(ERROR_TEXT, c.display)
        c.digit('7')
        assertEquals("7", c.display)
        assertEquals("", c.expression)
    }

    @Test
    fun 狀態快照_來回還原() {
        val c = keys("12+3×")
        val restored = Calculator().apply { restore(c.snapshot()) }
        assertEquals(c.expression, restored.expression)
        assertEquals(c.display, restored.display)

        restored.digit('4')
        restored.equal()
        assertEquals("24", restored.display)           // 12 + 3 × 4
        assertEquals("12 + 3 × 4 =", restored.expression)

        val warned = keys("2+=")                       // 提示也要撐過旋轉
        assertEquals(NOTICE_NEED_NUMBER, Calculator().apply { restore(warned.snapshot()) }.notice)
    }

    @Test
    fun 狀態快照_格式不符時忽略() {
        val c = keys("42")
        c.restore("垃圾")
        assertEquals("42", c.display)
    }
}
