package com.example.hw_ordering_system

import android.content.Context
import android.widget.CompoundButton
import java.io.Serializable

data class Order(
    val mainMeal: String? = null,
    val sideDishes: List<String> = emptyList(),
    val drink: String? = null
) : Serializable {

    /** 主餐、副餐、飲料都有選才算完整 */
    fun isComplete() = mainMeal != null && sideDishes.isNotEmpty() && drink != null
}

/** 選項顯示為「中文\n英文」兩行，儲存與比對時統一成單行 */
val CompoundButton.label: String get() = text.toString().replace('\n', ' ')

/** 主畫面、確認頁與 AlertDialog 共用的訂單文字，沒選的項目顯示「—」 */
fun Context.orderSummary(order: Order): String =
    getString(
        R.string.order_summary,
        order.mainMeal ?: "—",
        order.sideDishes.joinToString("、").ifEmpty { "—" },
        order.drink ?: "—"
    )
