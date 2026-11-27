package com.group11.hometide.ui.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.SearchSource
import com.group11.hometide.data.repository.AiOutcome
import com.group11.hometide.data.repository.AiRepository
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.data.repository.TripDraft
import com.group11.hometide.util.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * 一句話查詢（FR-AI-01～05、FR-NEXT-05）：P3 與「下一班」浮動列各有一個（Fragment 範圍），
 * 確認卡 [AiConfirmBottomSheet] 是該 Fragment 的子 Fragment，共用這個 ViewModel。
 */
class AiQueryViewModel(
    private val ai: AiRepository,
    private val auth: AuthRepository,
    private val transit: TransitRepository,
) : ViewModel() {

    private val _parsing = MutableStateFlow(false)

    /** FR-AI-01：解析中按鈕改為三點跳動、輸入框不可編輯 */
    val parsing: StateFlow<Boolean> = _parsing

    private val _card = MutableStateFlow<AiOutcome.Ticket?>(null)

    /** 確認卡內容（轉向後仍保留） */
    val card: StateFlow<AiOutcome.Ticket?> = _card

    sealed interface Event {
        data class Message(@param:StringRes val res: Int) : Event
        data object ShowCard : Event
        data class Open(val draft: TripDraft) : Event
        data class Edit(val draft: TripDraft) : Event
    }

    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun parse(input: String) {
        if (input.isBlank() || _parsing.value) return
        _parsing.value = true
        viewModelScope.launch {
            try {
                // FR-AI-03：模型沒提到運具時用會員的預設運具（訪客為台鐵）；沒提到乘客時用預設乘客
                val op = auth.profile.first()?.defaultOperator ?: Operator.TRA
                val pax = auth.defaultPassengers.first()
                val outcome = (ai.parse(input, auth.currentUid, op, pax) as? Result.Success)?.data ?: AiOutcome.Failed
                when (outcome) {
                    is AiOutcome.Ticket -> {
                        _card.value = outcome
                        events.send(Event.ShowCard)
                    }
                    AiOutcome.Unrelated -> events.send(Event.Message(R.string.ai_unrelated))
                    AiOutcome.RateLimited -> events.send(Event.Message(R.string.ai_rate_limited))
                    AiOutcome.Failed -> events.send(Event.Message(R.string.ai_failed))
                }
            } finally {
                _parsing.value = false
            }
        }
    }

    /** FR-AI-04「查詢班次」：寫入最近查詢（來源 AI）後開啟 P4 */
    fun confirm() {
        val draft = _card.value?.draft?.takeIf { it.canSearch } ?: return
        _card.value = null
        viewModelScope.launch {
            transit.saveSearch(auth.currentUid, draft.operator, draft.origin!!.stationId, draft.dest!!.stationId, draft.date.toString(), SearchSource.AI, draft.passengers)
            events.send(Event.Open(draft))
        }
    }

    /** FR-AI-04「修改」：記錄 user_edited，內容交給手動表單 */
    fun edit() {
        val ticket = _card.value ?: return
        _card.value = null
        viewModelScope.launch {
            ai.markEdited(ticket.logId)
            events.send(Event.Edit(ticket.draft))
        }
    }

    /** 下滑或點外面關閉 */
    fun dismiss() {
        _card.value = null
    }
}
