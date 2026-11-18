package com.group11.hometide.ui.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.repository.FavoriteRepository
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import com.group11.hometide.util.messageRes
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** P5 我的路線（規格 4.7；FR-FAV-01～04）。分頁內的 Fragment 共用這個 ViewModel（owner 為 MyRoutesFragment） */
class MyRoutesViewModel(
    private val cards: FavoriteCards,
    private val favorites: FavoriteRepository,
) : ViewModel() {

    private val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val deletedEvents = Channel<FavoriteRoute>(Channel.BUFFERED)
    private val openEvents = Channel<Pair<FavoriteRoute, Passengers>>(Channel.BUFFERED)
    private val errorEvents = Channel<Int>(Channel.BUFFERED)

    /** FR-FAV-03：以今天開啟 P4（帶預設乘客） */
    val open = openEvents.receiveAsFlow()

    /** 資料庫錯誤（11.2 未知錯誤） */
    val errors = errorEvents.receiveAsFlow()

    val state: StateFlow<FavoritesState> = cards.state(refresh)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesState())

    /** FR-FAV-02：刪除成功（畫面顯示「已刪除」＋「復原」） */
    val deleted = deletedEvents.receiveAsFlow()

    /** FR-FAV-04：進入 P5 時重算下一班（時刻表 2 小時內讀快取） */
    fun refresh() {
        refresh.tryEmit(Unit)
    }

    fun delete(route: FavoriteRoute) {
        viewModelScope.launch {
            when (val r = favorites.remove(route)) {
                is Result.Success -> deletedEvents.send(route)
                is Result.Failure -> errorEvents.send(r.error.messageRes)
            }
        }
    }

    fun openToday(route: FavoriteRoute, today: LocalDate = LocalDate.now()) {
        viewModelScope.launch { openEvents.send(route to cards.prepareOpen(route, today.toString())) }
    }

    fun restore(route: FavoriteRoute) {
        viewModelScope.launch { favorites.restore(route) }
    }

    fun reorder(ids: List<Long>) {
        viewModelScope.launch { favorites.reorder(ids) }
    }

    fun rename(route: FavoriteRoute, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { favorites.rename(route.id, label) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
