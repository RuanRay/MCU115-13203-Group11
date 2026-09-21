package com.example.computer_project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CalculatorLogicTest {

    @Test
    fun applyOp_四則運算() {
        assertEquals(5.0, applyOp(2.0, '+', 3.0), 0.0)
        assertEquals(-1.0, applyOp(2.0, '−', 3.0), 0.0)
        assertEquals(6.0, applyOp(2.0, '×', 3.0), 0.0)
        assertEquals(0.5, applyOp(1.0, '÷', 2.0), 0.0)
        assertFalse(applyOp(1.0, '÷', 0.0).isFinite()) // 除以 0 → 由 showError 接手
    }

    @Test
    fun formatNumber_顯示格式() {
        assertEquals("3", formatNumber(3.0))
        assertEquals("0.3", formatNumber(0.1 + 0.2)) // 浮點誤差尾數要消掉
        assertEquals("-2.5", formatNumber(-2.5))
        assertEquals(ERROR_TEXT, formatNumber(Double.NaN))
    }
}
