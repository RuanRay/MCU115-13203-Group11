package com.group11.hometide.ui.routes

import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.SearchSource
import com.group11.hometide.util.Passengers
import com.group11.hometide.data.repository.AuthRepository
import com.group11.hometide.data.repository.FavoriteRepository
import com.group11.hometide.data.repository.TransitRepository
import com.group11.hometide.util.NextTrain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/** 車票夾一張車票（FR-FAV-01、04；FR-SRCH-04） */
data class FavoriteCard(val route: FavoriteRoute, val originName: String, val destName: String, val next: NextTrain) {
    /**
     * label 仍是預設「起站 → 迄站」（7.1.6；與 strings.xml 的 result_route 同格式）。
     * 收藏當下站名可能還沒下載而存成代碼，所以代碼版本也算未改名
     */
    val isDefaultLabel: Boolean
        get() = route.label == "$originName → $destName" || route.label == "${route.originId} → ${route.destId}"
}

data class FavoritesState(val isGuest: Boolean = true, val loading: Boolean = true, val cards: List<FavoriteCard> = emptyList())

/**
 * P3、P5 共用：目前會員的收藏＋站名＋下一班（票價以預設乘客計算，FR-SET-02）。
 * 收藏或預設乘客改變、[refresh] 發出時重算下一班；計算期間沿用上一次的結果，避免拖曳排序後閃成「查詢中」。
 */
class FavoriteCards(
    private val auth: AuthRepository,
    private val favorites: FavoriteRepository,
    private val transit: TransitRepository,
) {

    /**
     * P3 存根、P5 卡片共用的查詢入口（FR-SRCH-04、FR-FAV-03）：以預設乘客查詢（卡片票價也是預設乘客），
     * 寫入 search_history（source=FAVORITE）；寫入失敗不影響查詢。回傳開啟 P4 用的乘客組合
     */
    suspend fun prepareOpen(route: FavoriteRoute, date: String): Passengers {
        val pax = auth.defaultPassengers.first()
        transit.saveSearch(auth.currentUid, route.operator, route.originId, route.destId, date, SearchSource.FAVORITE, pax)
        return pax
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun state(refresh: Flow<Unit>): Flow<FavoritesState> =
        auth.profile.map { it?.uid }.distinctUntilChanged().flatMapLatest { uid ->
            if (uid == null) return@flatMapLatest flowOf(FavoritesState(isGuest = true, loading = false))
            val known = mutableMapOf<Long, NextTrain>()
            combine(favorites.observe(uid), auth.defaultPassengers, refresh.onStart { emit(Unit) }) { routes, pax, _ -> routes to pax }
                .transformLatest { (routes, pax) ->
                    val named = routes.map { r ->
                        FavoriteCard(
                            r,
                            transit.stationName(r.operator, r.originId),
                            transit.stationName(r.operator, r.destId),
                            known[r.id] ?: NextTrain.Loading,
                        )
                    }
                    emit(FavoritesState(isGuest = false, loading = false, cards = named))
                    val next = coroutineScope { named.map { c -> async { favorites.nextTrain(c.route, pax) } }.awaitAll() }
                    named.forEachIndexed { i, c -> known[c.route.id] = next[i] }
                    emit(FavoritesState(isGuest = false, loading = false, cards = named.mapIndexed { i, c -> c.copy(next = next[i]) }))
                }
        }
}
