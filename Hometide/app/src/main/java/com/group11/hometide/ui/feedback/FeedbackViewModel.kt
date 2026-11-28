package com.group11.hometide.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.AiStatus
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.repository.FeedbackRepository
import com.group11.hometide.util.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** P7 意見回饋（FR-FB-01～03） */
class FeedbackViewModel(
    private val repo: FeedbackRepository,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    /** AI 結果車票（FR-FB-03） */
    sealed interface AiCard {
        /** 4 秒內還沒結果時先不顯示（AN-10 的淡入在有內容後才播） */
        data object Waiting : AiCard
        data object Pending : AiCard
        data object Hidden : AiCard
        data class Done(val feedback: Feedback) : AiCard
    }

    data class UiState(
        val submitting: Boolean = false,
        /** 不為 null 表示已送出，顯示成功頁 */
        val receivedOn: LocalDate? = null,
        val ai: AiCard = AiCard.Waiting,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val errors = Channel<Unit>(Channel.BUFFERED)

    /** 寫入失敗（Snackbar） */
    val errorFlow = errors.receiveAsFlow()

    fun submit(rating: Int, category: FeedbackCategory, content: String, attachUsage: Boolean) {
        if (_state.value.submitting || _state.value.receivedOn != null || !FeedbackRules.canSubmit(rating, content)) return
        _state.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val id = when (val r = repo.submit(rating, category, content, attachUsage)) {
                is Result.Failure -> {
                    _state.update { it.copy(submitting = false) }
                    errors.send(Unit)
                    return@launch
                }
                is Result.Success -> r.data
            }
            _state.update { it.copy(submitting = false, receivedOn = today()) }
            // FR-FB-03：4 秒內未完成先顯示「AI 分析中」；之後完成仍會更新卡片（離開畫面就交給 SyncService）
            val analysis = async { repo.analyze(id) }
            val first = withTimeoutOrNull(AI_WAIT_MS) { analysis.await() }
            if (first == null) {
                _state.update { it.copy(ai = AiCard.Pending) }
                show(analysis.await())
            } else {
                show(first)
            }
        }
    }

    private fun show(r: Result<Feedback>) {
        val card = when {
            r !is Result.Success -> AiCard.Hidden
            r.data.aiStatus == AiStatus.DONE -> AiCard.Done(r.data)
            r.data.aiStatus == AiStatus.PENDING -> AiCard.Pending // 連線失敗，稍後補做
            else -> AiCard.Hidden
        }
        _state.update { it.copy(ai = card) }
    }

    private companion object {
        const val AI_WAIT_MS = 4_000L
    }
}

/** FR-FB-01、03 的純規則 */
object FeedbackRules {
    const val MAX_LENGTH = 500

    fun canSubmit(rating: Int, content: String): Boolean =
        rating in 1..MAX_RATING && content.isNotBlank() && content.trim().length <= MAX_LENGTH

    /** 存根「一致」：使用者選的類型與 AI 判斷相同 */
    fun matches(feedback: Feedback): Boolean = feedback.aiCategory == feedback.category

    private const val MAX_RATING = 5
}
