package com.example.hw_ordering_system

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.CompoundButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.hw_ordering_system.databinding.ActivityMainBinding

const val KEY_MAIN = "main_meal_key"
const val KEY_SIDES = "side_dishes_key"
const val KEY_DRINK = "drink_key"
const val KEY_SUBMITTED = "submitted_key"

/** 選項顯示為「中文\n英文」兩行，傳遞與比對時統一成單行 */
val CompoundButton.label: String get() = text.toString().replace('\n', ' ')

/** 主餐、副餐、飲料都有選才算完整 */
fun isOrderComplete(mainMeal: String?, sides: List<String>, drink: String?) =
    mainMeal != null && sides.isNotEmpty() && drink != null

/** 主畫面、確認頁與 AlertDialog 共用的訂單文字，沒選的項目顯示「—」 */
fun Context.orderSummary(mainMeal: String?, sides: List<String>, drink: String?): String =
    getString(
        R.string.order_summary,
        mainMeal ?: "—",
        sides.joinToString("、").ifEmpty { "—" },
        drink ?: "—"
    )

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mainMeal: String? = null
    private var sides = arrayListOf<String>()
    private var drink: String? = null
    private var submitted = false

    /** 四個副畫面共用同一個啟動器；各頁只回傳自己負責的那個 key */
    private val startForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val before = Triple(mainMeal, sides, drink)
            data.getStringExtra(KEY_MAIN)?.let { mainMeal = it }
            data.getStringArrayListExtra(KEY_SIDES)?.let { sides = it }
            data.getStringExtra(KEY_DRINK)?.let { drink = it }
            // 只有確認頁會回傳 true；提交後選項真的有變動才變回「目前選擇」
            submitted = data.getBooleanExtra(KEY_SUBMITTED, false) ||
                (submitted && before == Triple(mainMeal, sides, drink))
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 旋轉螢幕後還原訂單
        savedInstanceState?.let {
            mainMeal = it.getString(KEY_MAIN)
            sides = it.getStringArrayList(KEY_SIDES) ?: arrayListOf()
            drink = it.getString(KEY_DRINK)
            submitted = it.getBoolean(KEY_SUBMITTED)
        }
        render()

        binding.btnMainMeal.setOnClickListener { open(MainMealActivity::class.java) }
        binding.btnSideDishes.setOnClickListener { open(SideDishesActivity::class.java) }
        binding.btnDrink.setOnClickListener { open(DrinkActivity::class.java) }
        binding.btnOrder.setOnClickListener { open(ConfirmActivity::class.java) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(orderBundle())
        outState.putBoolean(KEY_SUBMITTED, submitted)
    }

    private fun orderBundle() = Bundle().apply {
        putString(KEY_MAIN, mainMeal)
        putStringArrayList(KEY_SIDES, sides)
        putString(KEY_DRINK, drink)
    }

    /** 把目前訂單帶去副畫面：選擇頁用來還原勾選，確認頁用來驗證與顯示 */
    private fun open(target: Class<*>) =
        startForResult.launch(Intent(this, target).putExtras(orderBundle()))

    private fun render() {
        binding.tvSummaryTitle.setText(
            if (submitted) R.string.submitted_order else R.string.current_selection
        )
        binding.tvSummary.text = orderSummary(mainMeal, sides, drink)
    }
}
