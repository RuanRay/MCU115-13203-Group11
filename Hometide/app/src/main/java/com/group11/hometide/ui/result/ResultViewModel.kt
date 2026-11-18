package com.group11.hometide.ui.result

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.LiveInfo
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.util.AppError
import com.group11.hometide.util.FareTotal
import com.group11.hometide.util.Passengers
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
 * P4 查詢結果（FR-RES-01～04、06、FR-OFF-01、FR-BUS-02～04）。狀態以裝置時間計算，畫面每 60 秒呼叫 [tick]：
 * 重算狀態，今天的查詢另更新即時誤點／預估到站（即時資料失敗時不提示，維持依班表狀態；離線時不查）。
 */
class ResultViewModel(
    private val transit: TransitRepository,
    private val auth: AuthRepository,
    private val query: TimetableQuery,
    initialPassengers: Passengers = Passengers.DEFAULT,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    /**
     * [fare] 依本次乘客計算的總價與明細；[faresLoaded] 為 false 表示票價尚未取得或取不到（存根只顯示「—」，不標查無）。
     * [expanded] 為票價明細是否展開（FR-RES-07）
     */
    data class Item(
        val train: TimetableCache,
        val status: TrainStatus,
        val fare: FareTotal,
        val faresLoaded: Boolean = true,
        val expanded: Boolean = false,
    )

    /** 規格 6.3 的 UiState 狀態 */
    enum class Phase { LOADING, SUCCESS, EMPTY, ERROR, OFFLINE }

    data class UiState(
        val operator: Operator,
        val date: LocalDate,
        val originName: String = "",
        val destName: String = "",
        val phase: Phase = Phase.LOADING,
        val items: List<Item> = emptyList(),
        val isToday: Boolean = false,
        val now: LocalDateTime,
        val isGuest: Boolean = true,
        val sort: SortOption = SortOption.DEPART,
        /** 離線時快取的最後更新時間（FR-OFF-01） */
        val cachedAt: Long? = null,
        /** 錯誤畫面下方的 11.2 說明 */
        @param:StringRes val errorRes: Int? = null,
        val refreshing: Boolean = false,
        val passengers: Passengers = Passengers.DEFAULT,
    ) {
        val hasList: Boolean get() = phase == Phase.SUCCESS || phase == Phase.OFFLINE

        /** 「現在」線插在第一個尚未開出的班次之前；全部已開出時在最後。依行駛時間、票價排序時不顯示 */
        val nowLineIndex: Int?
            get() = if (!isToday || !hasList || !sort.keepsTimeOrder) null
            else items.indexOfFirst { it.status != TrainStatus.Departed }.let { if (it < 0) items.size else it }

        val sortOptions: List<SortOption> get() = SortOption.optionsFor(operator)
    }

    private data class Load(
        val phase: Phase = Phase.LOADING,
        val trains: List<TimetableCache> = emptyList(),
        val cachedAt: Long? = null,
        val errorRes: Int? = null,
        val refreshing: Boolean = false,
    )

    private data class Data(
        val load: Load,
        val fares: Map<String, List<FareCache>>?,
        val live: Map<String, LiveInfo>,
        val passengers: Passengers,
        val expanded: Set<String>,
    )

    private val date = LocalDate.parse(query.travelDate)
    private val load = MutableStateFlow(Load())
    private val fares = MutableStateFlow<Map<String, List<FareCache>>?>(null)
    private val passengers = MutableStateFlow(initialPassengers)
    private val expanded = MutableStateFlow<Set<String>>(emptySet())
    private val live = MutableStateFlow<Map<String, LiveInfo>>(emptyMap())
    private val sort = MutableStateFlow(SortOption.DEPART)
    private val names = MutableStateFlow("" to "")
    private val now = MutableStateFlow(clock())
    private val events = Channel<Int>(Channel.BUFFERED)

    /** 一次性訊息（Snackbar）：有快取但被限流時的 11.2 訊息 */
    val messages = events.receiveAsFlow()

    private val data = combine(load, fares, live, passengers, expanded, ::Data)

    val state: StateFlow<UiState> = combine(data, names, now, auth.profile.map { it == null }, sort) { d, n, nowTime, guest, option ->
        val today = nowTime.toLocalDate()
        val items = d.load.trains.map { t ->
            val base = TrainStatus.of(query.operator, t.departTime, date, today, nowTime.toLocalTime())
            // 國道客運 typeCode 為路線號；台鐵、高鐵為車種代碼（E-8）。FR-RES-02：依本次乘客組合計算總價
            val fare = FareTotal.of(t.trainTypeCode?.let { d.fares?.get(it) }.orEmpty(), d.passengers)
            Item(t, TrainStatus.withLive(base, d.live[t.trainNo]), fare, d.fares != null, t.trainNo in d.expanded)
        }
        UiState(
            operator = query.operator,
            date = date,
            originName = n.first,
            destName = n.second,
            phase = d.load.phase,
            items = ResultSort.apply(items, option),
            isToday = date == today,
            now = nowTime,
            isGuest = guest,
            sort = option,
            cachedAt = d.load.cachedAt,
            errorRes = d.load.errorRes,
            refreshing = d.load.refreshing,
            passengers = d.passengers,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState(query.operator, date, now = clock()))

    init {
        loadNames()
        load(force = false)
    }

    /** FR-RES-06：下拉重新整理（略過 2 小時快取，列表保留到新資料回來） */
    fun refresh() {
        if (load.value.refreshing) return
        load.update { it.copy(refreshing = true) }
        load(force = true)
    }

    /** FR-RES-06：錯誤畫面的「重試」 */
    fun retry() {
        load.value = Load()
        load(force = true)
    }

    private fun load(force: Boolean) {
        viewModelScope.launch {
            load.value = when (val r = transit.timetable(query, force)) {
                is Result.Success -> {
                    val (trains, cachedAt, error) = r.data
                    when {
                        // 11.2 QuotaExceeded：有快取時照常顯示，另提示「查詢太頻繁」
                        error == AppError.QuotaExceeded -> {
                            events.send(error.messageRes)
                            Load(Phase.SUCCESS, trains) // 有快取才會走到這裡（沒有快取時為 Failure）
                        }
                        // FR-OFF-01：查詢失敗時讀快取＋離線橫幅
                        error != null -> Load(Phase.OFFLINE, trains, cachedAt)
                        trains.isEmpty() -> Load(Phase.EMPTY)
                        else -> Load(Phase.SUCCESS, trains)
                    }
                }
                // 11.2 NotFound＝無班次；其他錯誤且無快取 → 錯誤畫面＋重試
                is Result.Failure ->
                    if (r.error == AppError.NotFound) Load(Phase.EMPTY) else Load(Phase.ERROR, errorRes = r.error.messageRes)
            }
            refreshLive()
        }
        viewModelScope.launch {
            // 票價取不到時存根顯示「—」，不影響列表（FR-RES-02）；Worker 失敗時 Repository 會用快取（TC-51）
            (transit.fares(query.operator, query.originId, query.destId) as? Result.Success)?.let { r ->
                fares.value = r.data.groupBy { it.trainTypeCode }
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

    /** FR-RES-02：只重算票價，不重新查時刻表 */
    fun setPassengers(p: Passengers) {
        passengers.value = p
    }

    fun setDefaultPassengers(p: Passengers) {
        viewModelScope.launch { runCatching { auth.setDefaultPassengers(p) } }
    }

    /** FR-RES-07：點車票本體展開／收合票價明細，可同時展開多張；重新整理與改乘客後保留 */
    fun toggleDetail(trainNo: String) {
        expanded.update { if (trainNo in it) it - trainNo else it + trainNo }
    }

    fun setSort(option: SortOption) {
        sort.value = option
    }

    /** 只有今天、有班次且非離線時才查即時資料；高鐵沒有即時資料（E-3） */
    private fun refreshLive() {
        val l = load.value
        if (date != clock().toLocalDate() || query.operator == Operator.THSR || l.phase != Phase.SUCCESS) {
            if (l.phase == Phase.OFFLINE) live.value = emptyMap()
            return
        }
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
