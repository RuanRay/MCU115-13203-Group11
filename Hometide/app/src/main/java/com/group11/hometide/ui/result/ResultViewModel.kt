package com.group11.hometide.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.util.Result
import com.group11.hometide.util.messageRes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/** P4 查詢結果（FR-RES-01、03、FR-BUS-04）。狀態以裝置時間計算，畫面每 60 秒呼叫 [tick] 更新。 */
class ResultViewModel(
    private val transit: TransitRepository,
    auth: AuthRepository,
    private val query: TimetableQuery,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    data class Item(val train: TimetableCache, val status: TrainStatus)

    data class UiState(
        val operator: Operator,
        val date: LocalDate,
        val originName: String = "",
        val destName: String = "",
        val loading: Boolean = true,
        val items: List<Item> = emptyList(),
        val isToday: Boolean = false,
        val now: LocalDateTime,
        val isGuest: Boolean = true,
    ) {
        /** 「現在」線插在第一個尚未開出的班次之前；全部已開出時在最後 */
        val nowLineIndex: Int?
            get() = if (!isToday || loading) null else items.indexOfFirst { it.status != TrainStatus.Departed }.let { if (it < 0) items.size else it }
    }

    private val date = LocalDate.parse(query.travelDate)
    private val trains = MutableStateFlow<List<TimetableCache>?>(null)
    private val names = MutableStateFlow("" to "")
    private val now = MutableStateFlow(clock())
    private val events = Channel<Int>(Channel.BUFFERED)

    /** 錯誤訊息（11.2）；空狀態與離線於 4-4 */
    val messages = events.receiveAsFlow()

    val state: StateFlow<UiState> = combine(trains, names, now, auth.profile.map { it == null }) { t, n, nowTime, guest ->
        val today = nowTime.toLocalDate()
        UiState(
            operator = query.operator,
            date = date,
            originName = n.first,
            destName = n.second,
            loading = t == null,
            items = t.orEmpty().map { Item(it, TrainStatus.of(query.operator, it.departTime, date, today, nowTime.toLocalTime())) },
            isToday = date == today,
            now = nowTime,
            isGuest = guest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState(query.operator, date, now = clock()))

    init {
        viewModelScope.launch {
            names.value = transit.stationName(query.operator, query.originId) to transit.stationName(query.operator, query.destId)
        }
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val r = transit.timetable(query)) {
                is Result.Success -> trains.value = r.data
                is Result.Failure -> {
                    trains.value = emptyList()
                    events.send(r.error.messageRes)
                }
            }
        }
    }

    /** FR-RES-03：每 60 秒依目前時間重算狀態並移動「現在」線 */
    fun tick() = now.update { clock() }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

