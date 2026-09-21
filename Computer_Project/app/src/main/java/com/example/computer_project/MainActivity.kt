package com.example.computer_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.example.computer_project.databinding.ActivityMainBinding

private const val STATE_CALC = "calc"

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val calc = Calculator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        savedInstanceState?.getString(STATE_CALC)?.let(calc::restore)
        bindClickListeners()
        updateDisplay()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_CALC, calc.snapshot())
    }

    /** 綁定所有按鍵的點擊事件 */
    private fun bindClickListeners() = with(binding) {
        mapOf(
            btn0 to '0', btn1 to '1', btn2 to '2', btn3 to '3', btn4 to '4',
            btn5 to '5', btn6 to '6', btn7 to '7', btn8 to '8', btn9 to '9',
        ).forEach { (button, d) -> button.setOnClickListener { act { digit(d) } } }

        mapOf(
            btnAdd to '+', btnSub to '−', btnMul to '×', btnDiv to '÷',
        ).forEach { (button, op) -> button.setOnClickListener { act { operator(op) } } }

        btnDot.setOnClickListener { act { dot() } }
        btnEquals.setOnClickListener { act { equal() } }
        btnNegate.setOnClickListener { act { negate() } }
        btnPercent.setOnClickListener { act { percent() } }
        btnClear.setOnClickListener { act { clear() } }
        btnBackspace.setOnClickListener { act { backspace() } }
        btnBackspace.setOnLongClickListener { act { clear() }; true }
    }

    /** 執行一個計算機動作，然後刷新畫面 */
    private fun act(action: Calculator.() -> Unit) {
        calc.action()
        updateDisplay()
    }

    /** 更新顯示：上方算式、下方結果 */
    private fun updateDisplay() {
        binding.tvExpression.text = calc.expression
        binding.tvResult.text = calc.display
    }
}
