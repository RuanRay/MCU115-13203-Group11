package com.group11.hometide.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.LiveInfo
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

/**
 * P4 查詢結果（FR-RES-01～04、FR-BUS-02～04）。狀態以裝置時間計算，畫面每 60 秒呼叫 [tick]：
 * 重算狀態，今天的查詢另更新即時誤點／預估到站（即時資料失敗時不提示，維持依班表狀態）。
 */
class ResultViewModel(
    private val transit: TransitRepository,
    auth: AuthRepository,
    private val query: TimetableQuery,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    data class Item(val train: TimetableCache, val status: TrainStatus, val fare: FareCache? = null)

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
        val sort: SortOption = SortOption.DEPART,
    ) {
        /** 「現在」線插在第一個尚未開出的班次之前；全部已開出時在最後。依行駛時間、票價排序時不顯示 */
        val nowLineIndex: Int?
            get() = if (!isToday || loading || !sort.keepsTimeOrder) null
            else items.indexOfFirst { it.status != TrainStatus.Departed }.let { if (it < 0) items.size else it }

        val sortOptions: List<SortOption> get() = SortOption.optionsFor(operator)
    }

    private data class Data(val trains: List<TimetableCache>?, val fares: Map<String, FareCache>, val live: Map<String, LiveInfo>)

    private val date = LocalDate.parse(query.travelDate)
    private val trains = MutableStateFlow<List<TimetableCache>?>(null)
    private val fares = MutableStateFlow<Map<String, FareCache>>(emptyMap())
    private val live = MutableStateFlow<Map<String, LiveInfo>>(emptyMap())
    private val sort = MutableStateFlow(SortOption.DEPART)
    private val names = MutableStateFlow("" to "")
    private val now = MutableStateFlow(clock())
    private val events = Channel<Int>(Channel.BUFFERED)

    /** 錯誤訊息（11.2）；空狀態與離線於 4-4 */
    val messages = events.receiveAsFlow()

    private val data = combine(trains, fares, live, ::Data)

    val state: StateFlow<UiState> = combine(data, names, now, auth.profile.map { it == null }, sort) { d, n, nowTime, guest, option ->
        val today = nowTime.toLocalDate()
        val items = d.trains.orEmpty().map { t ->
            val base = TrainStatus.of(query.operator, t.departTime, date, today, nowTime.toLocalTime())
            // 國道客運 typeCode 為路線號；台鐵、高鐵為車種代碼（E-8）
            Item(t, TrainStatus.withLive(base, d.live[t.trainNo]), t.trainTypeCode?.let { d.fares[it] })
        }
        UiState(
            operator = query.operator,
            date = date,
            originName = n.first,
            destName = n.second,
            loading = d.trains == null,
            items = ResultSort.apply(items, option),
            isToday = date == today,
            now = nowTime,
            isGuest = guest,
            sort = option,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState(query.operator, date, now = clock()))

    init {
        loadNames()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            when (val r = transit.timetable(query)) {
                is Result.Success -> {
                    trains.value = r.data
                    refreshLive()
                }
                is Result.Failure -> {
                    trains.value = emptyList()
                    events.send(r.error.messageRes)
                }
            }
        }
        viewModelScope.launch {
            // 票價取不到時存根顯示「—」，不影響列表（FR-RES-02）
            (transit.fares(query.operator, query.originId, query.destId) as? Result.Success)?.let { r ->
                fares.value = r.data.associateBy { it.trainTypeCode }
            }
        }
    }

    /** FR-RES-03：每 60 秒依目前時間重算狀態並移動「現在」線；今天的查詢另更新即時資料 */
    fun tick() {
        now.update { clock() }
        refreshLive()
        // 首次安裝時車站可能還在背景下載，標題暫時是代碼：之後再查一次
        if (names.value.first == query.originId || names.value.second == query.destId) loadNames()
    }

    private fun loadNames() {
        viewModelScope.launch {
            names.value = transit.stationName(query.operator, query.originId) to transit.stationName(query.operator, query.destId)
        }
    }

    fun setSort(option: SortOption) {
        sort.value = option
    }

    /** 只有今天才查即時資料；高鐵沒有即時資料（E-3） */
    private fun refreshLive() {
        if (date != clock().toLocalDate() || query.operator == Operator.THSR || trains.value.isNullOrEmpty()) return
        viewModelScope.launch {
            (transit.live(query.operator, query.originId, query.destId) as? Result.Success)?.let { r ->
                live.value = r.data.associateBy { it.tripId }
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

