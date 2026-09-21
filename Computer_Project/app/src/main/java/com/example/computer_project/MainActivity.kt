package com.example.computer_project

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.computer_project.databinding.ActivityMainBinding
import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor

internal const val ERROR_TEXT = "錯誤"
private const val MAX_DIGITS = 15

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding

    // 顯示區
    private val tvExpression: TextView get() = binding.tvExpression
    private val tvResult: TextView get() = binding.tvResult

    // 功能鍵
    private val btnClear: Button get() = binding.btnClear
    private val btnBackspace: Button get() = binding.btnBackspace

    // 運算子
    private val btnDiv: Button get() = binding.btnDiv
    private val btnMul: Button get() = binding.btnMul
    private val btnSub: Button get() = binding.btnSub
    private val btnAdd: Button get() = binding.btnAdd
    private val btnEquals: Button get() = binding.btnEquals

    // 數字與小數點
    private val btn0: Button get() = binding.btn0
    private val btn1: Button get() = binding.btn1
    private val btn2: Button get() = binding.btn2
    private val btn3: Button get() = binding.btn3
    private val btn4: Button get() = binding.btn4
    private val btn5: Button get() = binding.btn5
    private val btn6: Button get() = binding.btn6
    private val btn7: Button get() = binding.btn7
    private val btn8: Button get() = binding.btn8
    private val btn9: Button get() = binding.btn9
    private val btnDot: Button get() = binding.btnDot

    // ── 計算狀態 ──
    private var current = "0"               // 目前輸入中的數字
    private var accumulator: Double? = null // 前一個運算元（已累積的結果）
    private var pendingOp: Char? = null     // 待執行的運算子
    private var freshInput = true           // true 表示下一個數字要覆蓋 current
    private var expression = ""             // 顯示在上方的算式

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindClickListeners()
        updateDisplay()
    }

    /** 綁定所有按鍵的點擊事件 */
    private fun bindClickListeners() {
        mapOf(
            btn0 to '0', btn1 to '1', btn2 to '2', btn3 to '3', btn4 to '4',
            btn5 to '5', btn6 to '6', btn7 to '7', btn8 to '8', btn9 to '9',
        ).forEach { (button, digit) -> button.setOnClickListener { appendDigit(digit) } }

        mapOf(
            btnAdd to '+', btnSub to '−', btnMul to '×', btnDiv to '÷',
        ).forEach { (button, op) -> button.setOnClickListener { setOperator(op) } }

        btnDot.setOnClickListener { appendDot() }
        btnEquals.setOnClickListener { calculate() }
        btnClear.setOnClickListener { clearAll() }
        btnBackspace.setOnClickListener { backspace() }
    }

    /** 附加一個數字 */
    private fun appendDigit(digit: Char) {
        when {
            freshInput -> {
                if (pendingOp == null) expression = "" // 開始一條新算式
                current = digit.toString()
                freshInput = false
            }
            current == "0" -> current = digit.toString()
            current.length < MAX_DIGITS -> current += digit
        }
        updateDisplay()
    }

    /** 附加小數點，一個數字只能有一個 */
    private fun appendDot() {
        if (freshInput) {
            if (pendingOp == null) expression = ""
            current = "0."
            freshInput = false
        } else if (!current.contains('.')) {
            current += "."
        }
        updateDisplay()
    }

    /** 設定運算符號；若已有待執行的運算，先把前面算完 */
    private fun setOperator(op: Char) {
        val x = current.toDoubleOrNull() ?: run { clearAll(); return }
        val prevOp = pendingOp
        if (!(freshInput && prevOp != null)) { // 連按運算子時只換符號，不重算
            val result = if (prevOp != null) applyOp(accumulator ?: 0.0, prevOp, x) else x
            if (!result.isFinite()) { // 除以 0：算式也要收尾，不能停在「5 ÷」
                expression = "${formatNumber(accumulator ?: 0.0)} $prevOp ${formatNumber(x)} ="
                return showError()
            }
            accumulator = result
            current = formatNumber(result)
        }
        pendingOp = op
        freshInput = true
        expression = "${formatNumber(accumulator ?: 0.0)} $op"
        updateDisplay()
    }

    /** 按下 =：計算結果 */
    private fun calculate() {
        val op = pendingOp ?: return
        val acc = accumulator ?: return
        val x = current.toDoubleOrNull() ?: run { clearAll(); return }
        val result = applyOp(acc, op, x)
        expression = "${formatNumber(acc)} $op ${formatNumber(x)} ="
        if (!result.isFinite()) return showError() // 除以 0
        current = formatNumber(result)
        accumulator = null
        pendingOp = null
        freshInput = true
        updateDisplay()
    }

    /** AC：清除全部輸入與運算狀態 */
    private fun clearAll() {
        current = "0"
        accumulator = null
        pendingOp = null
        freshInput = true
        expression = ""
        updateDisplay()
    }

    /** ⌫：刪除目前輸入的最後一個字元 */
    private fun backspace() {
        if (freshInput) return // 剛算完或剛選運算子，沒有可刪的輸入
        current = current.dropLast(1).ifEmpty { "0" }
        updateDisplay()
    }

    /** 運算錯誤（除以 0）：顯示錯誤並重置狀態 */
    private fun showError() {
        current = ERROR_TEXT
        accumulator = null
        pendingOp = null
        freshInput = true
        updateDisplay()
    }

    /** 更新顯示：上方算式、下方結果 */
    private fun updateDisplay() {
        tvExpression.text = expression
        tvResult.text = current
    }
}

/** 單一次四則運算 */
internal fun applyOp(a: Double, op: Char, b: Double): Double = when (op) {
    '+' -> a + b
    '−' -> a - b
    '×' -> a * b
    '÷' -> a / b
    else -> b
}

/** 數值轉顯示字串：整數不帶小數點，並消掉浮點誤差尾數（0.1+0.2 → 0.3） */
internal fun formatNumber(v: Double): String = when {
    !v.isFinite() -> ERROR_TEXT
    v == floor(v) && abs(v) < 1e15 -> v.toLong().toString()
    else -> BigDecimal(v).round(MathContext(12)).stripTrailingZeros().toPlainString()
}
