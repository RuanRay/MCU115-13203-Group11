package com.group11.hometide.data.repository

import android.util.Log
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.data.local.dao.FavoriteDao
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.util.AppError
import com.group11.hometide.util.FareTotal
import com.group11.hometide.util.NextTrain
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

/** 常用路線（FR-RES-05、FR-FAV-01～04、FR-SRCH-04、FR-BUS-05）：Room favorite_route＋下一班（timetable_cache） */
class FavoriteRepository(
    private val dao: FavoriteDao,
    private val transit: TransitRepository,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) {

    sealed interface Toggle {
        data class Added(val route: FavoriteRoute) : Toggle
        data class Removed(val route: FavoriteRoute) : Toggle
    }

    fun observe(uid: String): Flow<List<FavoriteRoute>> = dao.observe(uid)

    /** FR-NEXT-02：下一班分頁上次選的路線（7.3 last_next_route） */
    val lastNextRoute: Flow<Long?> = settings.lastNextRoute

    suspend fun setLastNextRoute(id: Long) {
        runCatching { settings.setLastNextRoute(id) }
    }

    /**
     * 下一班分頁的資料（FR-NEXT-01～04、06）：今天的時刻、票價、即時誤點；今天已無班次時另查明天首班。
     * [Board.cachedAt] 非 null 表示查詢失敗、目前顯示快取（離線橫幅）；[Board.offline] 為無網路且無快取。
     */
    suspend fun board(route: FavoriteRoute): Board {
        val time = clock()
        val today = time.toLocalDate().toString()
        val names = transit.stationName(route.operator, route.originId) to transit.stationName(route.operator, route.destId)
        val q = TimetableQuery(route.operator, route.originId, route.destId, today)
        val (trains, cachedAt) = when (val r = transit.timetable(q)) {
            is Result.Success -> r.data.trains to (if (r.data.error != null) r.data.cachedAt else null)
            is Result.Failure ->
                if (r.error == AppError.NotFound) emptyList<TimetableCache>() to null
                else return Board(names.first, names.second, offline = true)
        }
        val fares = (transit.fares(route.operator, route.originId, route.destId) as? Result.Success)?.data.orEmpty()
        // 即時資料只在線上查（高鐵沒有，E-3）；失敗時維持依班表狀態
        val live = if (cachedAt == null && route.operator != Operator.THSR) {
            (transit.live(route.operator, route.originId, route.destId) as? Result.Success)?.data.orEmpty().associateBy { it.tripId }
        } else {
            emptyMap()
        }
        val tomorrow = if (NextTrain.pick(trains, time.toLocalTime()) == null) {
            trains(route, time.toLocalDate().plusDays(1).toString())?.let(NextTrain::first)?.departTime
        } else {
            null
        }
        return Board(names.first, names.second, trains, fares, live, cachedAt, tomorrow)
    }

    data class Board(
        val originName: String,
        val destName: String,
        val trains: List<TimetableCache> = emptyList(),
        val fares: List<FareCache> = emptyList(),
        val live: Map<String, LiveInfo> = emptyMap(),
        val cachedAt: Long? = null,
        val tomorrowFirst: String? = null,
        val offline: Boolean = false,
    )

    fun isFavorite(uid: String, operator: Operator, originId: String, destId: String): Flow<Boolean> =
        dao.observeExists(uid, operator, originId, destId)

    /** FR-RES-05：已收藏就取消，否則新增並排在最後；上限 10 筆。[label] 預設「起站 → 迄站」 */
    suspend fun toggle(uid: String, operator: Operator, originId: String, destId: String, label: String): Result<Toggle> =
        guarded("Favorite toggle failed") {
            dao.find(uid, operator, originId, destId)?.let {
                dao.deleteById(it.id)
                return@guarded Result.Success(Toggle.Removed(it))
            }
            if (dao.count(uid) >= MAX_FAVORITES) return@guarded Result.Failure(AppError.FavoriteLimit)
            val route = FavoriteRoute(0, uid, operator, originId, destId, label, (dao.maxSortOrder(uid) ?: -1) + 1, now())
            Result.Success(Toggle.Added(route.copy(id = dao.insert(route))))
        }

    /** FR-FAV-02 左滑刪除 */
    suspend fun remove(route: FavoriteRoute): Result<Unit> = guarded("Favorite delete failed") {
        dao.deleteById(route.id)
        Result.Success(Unit)
    }

    /** FR-FAV-02「復原」：原樣插回（含 id 與排序） */
    suspend fun restore(route: FavoriteRoute): Result<Unit> = guarded("Favorite restore failed") {
        dao.restore(route)
        Result.Success(Unit)
    }

    /** FR-FAV-02 拖曳排序：依 [ids] 的順序寫回 sort_order */
    suspend fun reorder(ids: List<Long>): Result<Unit> = guarded("Favorite reorder failed") {
        dao.updateOrder(ids)
        Result.Success(Unit)
    }

    /** 改名（7.1.6 label；最多 [MAX_LABEL] 字，實作者確認） */
    suspend fun rename(id: Long, label: String): Result<Unit> = guarded("Favorite rename failed") {
        dao.rename(id, label.trim().take(MAX_LABEL))
        Result.Success(Unit)
    }

    /**
     * FR-FAV-04：今天的下一班＋以 [passengers] 計算的票價。時刻表由 TransitRepository 處理 2 小時快取與離線快取；
     * 今天已無班次時查明天首班；無網路且無快取為 [NextTrain.Offline]。
     */
    suspend fun nextTrain(route: FavoriteRoute, passengers: Passengers): NextTrain {
        val time = clock()
        val today = trains(route, time.toLocalDate().toString()) ?: return NextTrain.Offline
        val (train, minutes) = NextTrain.pick(today, time.toLocalTime())
            ?: return trains(route, time.toLocalDate().plusDays(1).toString())
                ?.let(NextTrain::first)?.let { NextTrain.Tomorrow(it.departTime) } ?: NextTrain.NoneToday
        val fare = (transit.fares(route.operator, route.originId, route.destId) as? Result.Success)?.data
            ?.filter { it.trainTypeCode == train.trainTypeCode }?.takeIf { it.isNotEmpty() }
            ?.let { FareTotal.of(it, passengers) }
        return NextTrain.Next(train, minutes, fare)
    }

    /** NOT_FOUND（查無班次）視為空清單；其他錯誤且無快取回 null */
    private suspend fun trains(route: FavoriteRoute, date: String) =
        when (val r = transit.timetable(TimetableQuery(route.operator, route.originId, route.destId, date))) {
            is Result.Success -> r.data.trains
            is Result.Failure -> if (r.error == AppError.NotFound) emptyList() else null
        }

    private inline fun <T> guarded(log: String, block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$log: ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    companion object {
        const val MAX_FAVORITES = 10
        const val MAX_LABEL = 10
        private const val TAG = "FavoriteRepository"
    }
}
