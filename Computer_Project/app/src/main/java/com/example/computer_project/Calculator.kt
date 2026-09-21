package com.example.computer_project

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.floor

internal const val ERROR_TEXT = "錯誤"
internal const val NOTICE_NEED_NUMBER = "請先輸入數字再按 ="
private const val MAX_DIGITS = 15

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

/** 兩階段求值：先掃一遍 × ÷，再由左至右結算 + −。沒有括號，不需要 shunting-yard */
internal fun evaluate(nums: List<Double>, ops: List<Char>): Double {
    require(nums.size == ops.size + 1) { "運算元與運算子數量不符" }
    val n = nums.toMutableList()
    val o = ops.toMutableList()
    var i = 0
    while (i < o.size) {
        if (o[i] == '×' || o[i] == '÷') {
            n[i] = applyOp(n[i], o[i], n[i + 1])
            n.removeAt(i + 1)
            o.removeAt(i)
        } else {
            i++
        }
    }
    var acc = n[0]
    for (j in o.indices) acc = applyOp(acc, o[j], n[j + 1])
    return acc
}

/**
 * 計算機狀態機。純 Kotlin、不碰 Android，方便單元測試與狀態保存。
 *
 * [nums] 與 [ops] 交替存放已確定的算式（nums.size == ops.size 或 ops.size + 1），
 * [current] 是還在輸入中、尚未推進 [nums] 的那個數字。
 */
class Calculator {

    private val nums = mutableListOf<Double>()
    private val ops = mutableListOf<Char>()
    private var current = "0"
    private var freshInput = true          // true 表示下一個數字要覆蓋 current
    private var lastOp: Char? = null       // 連按 = 時重複用
    private var lastOperand: Double? = null
    private var frozen: String? = null     // 按下 = 後定格的算式
    private var error = false
    private var noticeText: String? = null

    /** 下方結果區要顯示的文字 */
    val display: String get() = current

    /** 提示區要顯示的文字；沒有要提醒的事就留白 */
    val notice: String get() = noticeText.orEmpty()

    /** 上方算式區要顯示的文字；還沒按運算子時留白，不跟結果區重複 */
    val expression: String
        get() {
            frozen?.let { return it }
            return if (ops.isEmpty()) "" else joinExpression(if (freshInput) "" else current)
        }

    fun digit(c: Char) {
        beginInput()
        when {
            freshInput -> {
                current = if (current == "-0") "-$c" else c.toString()
                freshInput = false
            }
            current == "0" -> current = c.toString()
            current == "-0" -> current = "-$c"
            current.count { it.isDigit() } < MAX_DIGITS -> current += c
        }
    }

    fun dot() {
        beginInput()
        if (freshInput) {
            current = "0."
            freshInput = false
        } else if (!current.contains('.')) {
            current += '.'
        }
    }

    /** 按下運算子；連按時只換符號，不重算（有優先權，中途也算不出部分結果） */
    fun operator(op: Char) {
        noticeText = null
        if (error) return clear()
        frozen = null
        if (freshInput && ops.isNotEmpty()) {
            ops[ops.lastIndex] = op
            return
        }
        if (!freshInput || nums.isEmpty()) nums += current.toDoubleOrNull() ?: return clear()
        ops += op
        freshInput = true
        current = "0" // 結果區歸零，免得把已經推進算式的前一個運算元誤讀成還在輸入的數字
    }

    /** 按下 =。算式為空時重複上一次的運算（5 + 3 = → 8，再按 = → 11） */
    fun equal() {
        noticeText = null
        if (error) return clear()
        if (ops.isEmpty()) {
            val op = lastOp ?: return
            val operand = lastOperand ?: return
            val x = current.toDoubleOrNull() ?: return clear()
            frozen = "${formatNumber(x)} $op ${formatNumber(operand)} ="
            finish(applyOp(x, op, operand))
            return
        }
        if (freshInput) { // 運算子後面還沒輸入數字，算式不完整 → 不運算，只提醒
            noticeText = NOTICE_NEED_NUMBER
            return
        }
        nums += current.toDoubleOrNull() ?: return clear()
        lastOp = ops.last()
        lastOperand = nums.last()
        frozen = joinExpression(formatNumber(nums.last())) + " ="
        finish(evaluate(nums, ops))
    }

    /** ±：切換正負號。剛按完運算子時不作用 */
    fun negate() {
        noticeText = null
        if (error) return clear()
        if (freshInput && ops.isNotEmpty()) return
        // ponytail: 不支援直接輸入負的運算元（2 + −3），要的話得讓 current 能在 freshInput 下帶符號
        current = if (current.startsWith('-')) current.drop(1) else "-$current"
        frozen = null
    }

    /** %：加減取「前段結果的百分比」（200 + 10% = 220），乘除單純除以 100（200 × 10% = 20） */
    fun percent() {
        noticeText = null
        if (error) return clear()
        if (freshInput && ops.isNotEmpty()) return
        val x = current.toDoubleOrNull() ?: return clear()
        val op = ops.lastOrNull()
        current = formatNumber(
            if (op == '+' || op == '−') evaluate(nums, ops.dropLast(1)) * x / 100 else x / 100
        )
        frozen = null
    }

    /** AC：清除全部 */
    fun clear() {
        nums.clear()
        ops.clear()
        current = "0"
        freshInput = true
        lastOp = null
        lastOperand = null
        frozen = null
        error = false
        noticeText = null
    }

    /** ⌫：刪一個字元；剛按完運算子時改成退掉那個運算子，取回前一個運算元繼續編輯 */
    fun backspace() {
        noticeText = null
        if (error) return clear()
        frozen = null
        if (freshInput) {
            freshInput = false
            if (ops.isNotEmpty()) {
                ops.removeAt(ops.lastIndex)
                current = formatNumber(nums.removeAt(nums.lastIndex))
                return
            }
        }
        current = current.dropLast(1)
        if (current.isEmpty() || current == "-") current = "0"
    }

    /** 把整個狀態壓成一個字串，讓 Activity 只需要存一個 String */
    fun snapshot(): String = listOf(
        nums.joinToString(","),
        ops.joinToString(""),
        current,
        if (freshInput) "1" else "0",
        lastOp?.toString().orEmpty(),
        lastOperand?.toString().orEmpty(),
        frozen.orEmpty(),
        if (error) "1" else "0",
        noticeText.orEmpty(),
    ).joinToString("\n")

    /** 還原 [snapshot] 的內容；格式不符就整個忽略，維持現狀 */
    fun restore(s: String) {
        val f = s.split("\n")
        if (f.size != FIELD_COUNT) return
        val savedNums = f[0].split(",").filter { it.isNotEmpty() }.map { it.toDoubleOrNull() ?: return }
        clear()
        nums += savedNums
        ops += f[1].toList()
        current = f[2]
        freshInput = f[3] == "1"
        lastOp = f[4].firstOrNull()
        lastOperand = f[5].toDoubleOrNull()
        frozen = f[6].ifEmpty { null }
        error = f[7] == "1"
        noticeText = f[8].ifEmpty { null }
    }

    /** 開始新一輪輸入：清掉錯誤狀態與定格的算式 */
    private fun beginInput() {
        noticeText = null
        if (error) clear()
        frozen = null
    }

    /** 把 nums/ops 交錯串成算式，尾端接上 [tail] */
    private fun joinExpression(tail: String): String = buildString {
        for (i in ops.indices) {
            append(formatNumber(nums[i]))
            append(' ')
            append(ops[i])
            append(' ')
        }
        append(tail)
    }.trimEnd()

    /** 收尾：定格結果、清空算式；除以 0 之類的非有限結果進入錯誤狀態 */
    private fun finish(result: Double) {
        nums.clear()
        ops.clear()
        freshInput = true
        error = !result.isFinite()
        if (error) {
            current = ERROR_TEXT
            lastOp = null
            lastOperand = null
        } else {
            current = formatNumber(result)
        }
    }

    private companion object {
        const val FIELD_COUNT = 9
    }
}
