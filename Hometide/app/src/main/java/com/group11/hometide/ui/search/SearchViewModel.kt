package com.group11.hometide.ui.search

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.R
import com.group11.hometide.data.local.dao.RecentSearch
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.SearchSource
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.ui.routes.FavoriteCard
import com.group11.hometide.ui.routes.FavoriteCards
import com.group11.hometide.ui.routes.FavoritesState
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** P3 查詢（規格 4.4；FR-SRCH-01、03、07、FR-BUS-01）。起訖站文字留在 View，送出時才傳進來驗證。 */
class SearchViewModel(
    private val transit: TransitRepository,
    private val auth: AuthRepository,
    private val favoriteCards: FavoriteCards,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {

    private val favoritesRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** FR-SRCH-04：首頁車票夾（會員的收藏，含下一班） */
    val favorites: StateFlow<FavoritesState> = favoriteCards.state(favoritesRefresh)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesState())

    fun refreshFavorites() {
        favoritesRefresh.tryEmit(Unit)
    }

    /** FR-SRCH-04：點存根以「今天」查詢（AN-05、AN-03 於 7-3）；乘客用預設乘客 */
    fun searchFavorite(card: FavoriteCard) {
        viewModelScope.launch {
            val r = card.route
            val date = today()
            events.send(Event.OpenResult(r.operator, r.originId, r.destId, date, favoriteCards.prepareOpen(r, date.toString())))
        }
    }

    data class UiState(
        val operator: Operator = Operator.TRA,
        val travelDate: LocalDate,
        val defaultOperator: Operator = Operator.TRA,
        val isGuest: Boolean = true,
        val recent: List<RecentSearch> = emptyList(),
        val passengers: Passengers = Passengers.DEFAULT,
    )

    sealed interface Event {
        data class Message(@param:StringRes val res: Int, val arg: String? = null) : Event
        data class OpenResult(
            val operator: Operator,
            val originId: String,
            val destId: String,
            val date: LocalDate,
            val passengers: Passengers,
        ) : Event
        data class ClearStations(val origin: Boolean, val dest: Boolean) : Event
    }

    /** passengers 為 null 表示沿用預設乘客（FR-SRCH-08） */
    private data class Form(
        val operator: Operator,
        val travelDate: LocalDate,
        val operatorChosen: Boolean = false,
        val passengers: Passengers? = null,
    )

    private val form = MutableStateFlow(Form(Operator.TRA, today()))
    private val events = Channel<Event>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<UiState> = combine(form, auth.profile, transit.recentSearches(), auth.defaultPassengers) { f, profile, recent, pax ->
        UiState(f.operator, f.travelDate, profile?.defaultOperator ?: Operator.TRA, profile == null, recent, f.passengers ?: pax)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState(travelDate = today()))

    init {
        // 會員的預設運具（使用者尚未手動切換時才套用）
        viewModelScope.launch {
            val op = auth.profile.filterNotNull().first().defaultOperator
            form.update { if (it.operatorChosen) it else it.copy(operator = op) }
        }
    }

    /** 4.4：切換運具時清空不屬於該運具的站名 */
    fun selectOperator(op: Operator, originText: String, destText: String) {
        if (op == form.value.operator) return
        form.update { it.copy(operator = op, operatorChosen = true) }
        viewModelScope.launch {
            val clearOrigin = originText.isNotBlank() && missing(op, originText)
            val clearDest = destText.isNotBlank() && missing(op, destText)
            if (clearOrigin || clearDest) events.send(Event.ClearStations(clearOrigin, clearDest))
        }
    }

    /** FR-SRCH-08：只影響這次查詢 */
    fun setPassengers(p: Passengers) {
        form.update { it.copy(passengers = p) }
    }

    /** FR-SRCH-08「設為我的預設」→ FR-SET-02 */
    fun setDefaultPassengers(p: Passengers) {
        viewModelScope.launch { runCatching { auth.setDefaultPassengers(p) } }
    }

    fun setDate(date: LocalDate) {
        if (SearchRules.isSelectable(date, today())) form.update { it.copy(travelDate = date) }
    }

    /** 跨過午夜後乘車日期可能早於今天：改回今天（FR-SRCH-01 今天起 60 天內） */
    fun ensureValidDate() {
        val now = today()
        form.update { if (SearchRules.isSelectable(it.travelDate, now)) it else it.copy(travelDate = now) }
    }

    /** FR-SRCH-01：檢查 → 找車站 → 寫入 search_history → 開啟 P4 */
    fun submit(originText: String, destText: String) {
        SearchRules.precheck(originText, destText)?.let { return send(Event.Message(it)) }
        ensureValidDate()
        val f = form.value
        viewModelScope.launch {
            val origin = find(f.operator, originText) ?: return@launch
            val dest = find(f.operator, destText) ?: return@launch
            if (origin.stationId == dest.stationId) return@launch events.send(Event.Message(R.string.error_same_station))
            open(f.operator, origin.stationId, dest.stationId, f.travelDate, state.value.passengers)
        }
    }

    /** FR-SRCH-07：點一筆以「今天」查詢；帶回當時的乘客（FR-SRCH-08） */
    fun searchRecent(item: RecentSearch) {
        viewModelScope.launch { open(item.operator, item.originId, item.destId, today(), Passengers.fromJson(item.passengersJson)) }
    }

    fun deleteRecent(item: RecentSearch) {
        viewModelScope.launch { transit.deleteRecent(item) }
    }

    private suspend fun open(
        op: Operator,
        originId: String,
        destId: String,
        date: LocalDate,
        pax: Passengers,
        source: SearchSource = SearchSource.MANUAL,
    ) {
        // 紀錄寫入失敗不影響查詢（Repository 已記 log）
        transit.saveSearch(auth.currentUid, op, originId, destId, date.toString(), source, pax)
        events.send(Event.OpenResult(op, originId, destId, date, pax))
    }

    /** 找不到或資料庫錯誤時送出訊息並回 null */
    private suspend fun find(op: Operator, text: String): Station? = when (val r = transit.findStation(op, text)) {
        // 該運具尚未下載過車站（例：首次開啟就離線）時，提示網路錯誤（11.2 NetworkError）
        is Result.Success -> r.data ?: null.also {
            events.send(
                if (transit.hasStations(op)) Event.Message(R.string.error_station_not_found, text.trim()) else Event.Message(R.string.error_network),
            )
        }
        is Result.Failure -> null.also { events.send(Event.Message(R.string.error_unknown)) }
    }

    /** 確定查無此站才算（資料庫錯誤時不清空使用者輸入） */
    private suspend fun missing(op: Operator, text: String): Boolean =
        (transit.findStation(op, text) as? Result.Success)?.let { it.data == null } ?: false

    private fun send(e: Event) {
        viewModelScope.launch { events.send(e) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
