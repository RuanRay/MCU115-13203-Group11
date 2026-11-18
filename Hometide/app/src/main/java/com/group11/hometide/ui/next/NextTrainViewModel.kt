package com.group11.hometide.ui.next

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.FavoriteRepository
import com.group11.hometide.ui.result.TrainStatus
import com.group11.hometide.util.FareTotal
import com.group11.hometide.util.Passengers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * 下一班分頁（FR-NEXT-01～04、06；規格 4.5）。畫面在前景時每 60 秒呼叫 [tick] 重算倒數（FR-NEXT-03）；
 * 主車票開出後自動遞補，今天的班次都開出時重新查詢一次以取得明天首班。
 */
class NextTrainViewModel(
    private val auth: AuthRepository,
    private val favorites: FavoriteRepository,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {

    /**
     * [status] 為準點／誤點（台鐵）、依班表／預估到站（國道客運）；不含「N 分後」（FR-NEXT-04）。
     * 小車票另以 [minutes] 在 30 分鐘內並列「N 分後」（實作者確認）
     */
    data class Upcoming(val train: TimetableCache, val minutes: Int, val status: TrainStatus, val fare: FareTotal?) {
        val showMinutes: Boolean get() = minutes <= TrainStatus.SOON_MINUTES
    }

    data class UiState(
        val loading: Boolean = true,
        val isGuest: Boolean = true,
        val routes: List<FavoriteRoute> = emptyList(),
        val selected: FavoriteRoute? = null,
        val originName: String = "",
        val destName: String = "",
        val boardLoading: Boolean = true,
        /** 主車票（FR-NEXT-01） */
        val main: Upcoming? = null,
        /** 接下來 3 班（FR-NEXT-04） */
        val upcoming: List<Upcoming> = emptyList(),
        val tomorrowFirst: String? = null,
        /** 無網路且無快取 */
        val offline: Boolean = false,
        /** 非 null 表示顯示的是快取（離線橫幅） */
        val cachedAt: Long? = null,
        /** 預設乘客（主車票「票種」與票價，FR-SET-02） */
        val passengers: Passengers = Passengers.DEFAULT,
    ) {
        /** FR-NEXT-06：訪客或沒有常用路線 */
        val isEmpty: Boolean get() = !loading && (isGuest || routes.isEmpty())
    }

    private data class Loaded(val route: FavoriteRoute?, val board: FavoriteRepository.Board?, val pax: Passengers)

    private val now = MutableStateFlow(clock())
    private val reload = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var reloadedForTomorrow = false
    private var shownRouteId: Long? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val routes = auth.profile.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) flowOf<List<FavoriteRoute>?>(null) else favorites.observe(uid)
    }

    // FR-NEXT-02：沿用上次選擇；該路線被刪除時改選第一條
    private val selected = combine(routes, favorites.lastNextRoute) { list, last ->
        list?.let { l -> l.firstOrNull { it.id == last } ?: l.firstOrNull() }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val loaded = combine(selected, auth.defaultPassengers, reload.onStart { emit(Unit) }) { r, pax, _ -> r to pax }
        .transformLatest { (route, pax) ->
            // 只有切換路線時才回到「查詢中」；同一路線重查時保留上一份資料直到新資料回來
            if (route?.id != shownRouteId) {
                shownRouteId = route?.id
                emit(Loaded(route, null, pax))
            }
            if (route != null) emit(Loaded(route, favorites.board(route), pax))
        }

    val state: StateFlow<UiState> = combine(routes, loaded, now) { list, l, time ->
        val board = l.board
        if (list == null) return@combine UiState(loading = false, isGuest = true)
        if (board == null) {
            return@combine UiState(loading = false, isGuest = false, routes = list, selected = l.route, boardLoading = l.route != null)
        }
        val remaining = board.trains.mapNotNull { t ->
            val m = TrainStatus.minutesOf(t.departTime) - (time.hour * 60 + time.minute)
            if (m < 0) return@mapNotNull null
            // FR-NEXT-04：狀態只寫準點／誤點（倒數另外並列），所以把「N 分後」換回該運具的一般狀態
            val base = when (t.operator) {
                Operator.TRA -> TrainStatus.OnTime
                Operator.BUS -> TrainStatus.BySchedule
                Operator.THSR -> TrainStatus.None
            }
            val fares = board.fares.filter { it.trainTypeCode == t.trainTypeCode }
            Upcoming(t, m, TrainStatus.withLive(base, board.live[t.trainNo]), fares.takeIf { it.isNotEmpty() }?.let { FareTotal.of(it, l.pax) })
        }
        UiState(
            loading = false,
            isGuest = false,
            routes = list,
            selected = l.route,
            originName = board.originName,
            destName = board.destName,
            boardLoading = false,
            main = remaining.firstOrNull(),
            upcoming = remaining.drop(1).take(UPCOMING_COUNT),
            tomorrowFirst = board.tomorrowFirst,
            offline = board.offline,
            cachedAt = board.cachedAt,
            passengers = l.pax,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    fun select(route: FavoriteRoute) {
        reloadedForTomorrow = false
        viewModelScope.launch { favorites.setLastNextRoute(route.id) }
    }

    /** FR-NEXT-03：每 60 秒重算；今天的班次剛好都開出、還沒查過明天首班時重新查詢一次 */
    fun tick() {
        now.value = clock()
        val s = state.value
        if (!s.boardLoading && s.selected != null && s.main == null && s.tomorrowFirst == null && !s.offline && !reloadedForTomorrow) {
            reloadedForTomorrow = true
            reload.tryEmit(Unit)
        }
    }

    /** 回到分頁時重新查詢（時刻表 2 小時內讀快取） */
    fun refresh() {
        reloadedForTomorrow = false
        reload.tryEmit(Unit)
    }

    companion object {
        const val UPCOMING_COUNT = 3

        /** FR-NEXT-03：剩 5 分鐘以內數字變紅 */
        const val URGENT_MINUTES = 5

        /** FR-NEXT-01：60 分鐘以上改為「X 小時 Y 分」；回傳 null 表示用「N 分鐘」 */
        fun hoursMinutes(minutes: Int): Pair<Int, Int>? = if (minutes >= 60) minutes / 60 to minutes % 60 else null

        fun isUrgent(minutes: Int): Boolean = minutes <= URGENT_MINUTES

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
