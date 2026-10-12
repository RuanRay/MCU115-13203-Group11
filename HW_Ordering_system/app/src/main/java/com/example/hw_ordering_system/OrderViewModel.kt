package com.example.hw_ordering_system

import androidx.lifecycle.LiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

/** MainActivity 與四個 Fragment 共用；SavedStateHandle 讓訂單在旋轉與行程重建後都還在 */
class OrderViewModel(state: SavedStateHandle) : ViewModel() {

    private val _order = state.getLiveData("order", Order())
    val order: LiveData<Order> = _order

    private val _submitted = state.getLiveData("submitted", false)
    val submitted: LiveData<Boolean> = _submitted

    /** 目前顯示的副畫面，PAGE_NONE 代表主畫面 */
    private val _page = state.getLiveData("page", PAGE_NONE)
    val page: LiveData<Int> = _page

    fun show(page: Int) {
        _page.value = page
    }

    /** 提交後選項真的有變動才變回「目前選擇」 */
    fun update(change: Order.() -> Order) {
        val old = _order.value ?: Order()
        val new = old.change()
        if (new != old) {
            _order.value = new
            _submitted.value = false
        }
    }

    fun submit() {
        _submitted.value = true
        show(PAGE_NONE)
    }
}
