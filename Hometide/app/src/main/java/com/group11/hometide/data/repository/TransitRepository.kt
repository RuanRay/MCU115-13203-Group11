package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.RecentSearch
import com.group11.hometide.data.local.dao.SearchHistoryDao
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.dao.TimetableDao
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.remote.dto.TripDto
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.SearchHistory
import com.group11.hometide.data.local.entity.SearchSource
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.apiCall
import com.group11.hometide.data.remote.dto.StationDto
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import com.group11.hometide.util.StationNameNormalizer
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/** 車站、時刻、票價、即時（規格 6.1）。目前有車站清單（FR-SRCH-02）與查詢紀錄（FR-SRCH-01、07）。 */
class TransitRepository(
    private val api: HometideApi,
    private val stationDao: StationDao,
    private val historyDao: SearchHistoryDao,
    private val timetableDao: TimetableDao,
    private val gson: Gson,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** 本機沒有資料或超過 7 天（與 Worker 快取相同，8.5）才下載；成功回傳寫入筆數，未過期回傳 0。 */
    suspend fun refreshStations(operator: Operator, force: Boolean = false): Result<Int> =
        guarded("Station refresh failed: $operator") { download(operator, force) }

    /**
     * FR-SRCH-01：以使用者輸入的站名找該運具的車站；找不到時 Success(null)。
     * 先比對完整站名（國道客運站牌多以「站」結尾），再比對去掉結尾「站」的寫法（「臺南站」→ 臺南）。
     */
    suspend fun findStation(operator: Operator, input: String): Result<Station?> = guarded("Station lookup failed") {
        Result.Success(
            stationDao.findByName(operator, StationNameNormalizer.normalize(input))
                ?: stationDao.findByName(operator, StationNameNormalizer.normalizeQuery(input)),
        )
    }

    /** 該運具是否已下載過車站（沒有時「找不到車站」其實是尚未連線下載） */
    suspend fun hasStations(operator: Operator): Boolean =
        runCatching { stationDao.lastUpdated(operator) != null }.getOrDefault(false)

    /** P4 標題：站名，查不到時用代碼（國道客運代碼即站名） */
    suspend fun stationName(operator: Operator, stationId: String): String =
        runCatching { stationDao.nameOf(operator, stationId) }.getOrNull() ?: stationId

    /**
     * FR-RES-01：時刻表。同條件 2 小時內讀 timetable_cache 不重複呼叫（8.5），否則呼叫 Worker 並先刪後寫。
     * 離線時改讀過期快取於任務 4-4（FR-OFF-01）。
     */
    suspend fun timetable(query: TimetableQuery, force: Boolean = false): Result<List<TimetableCache>> =
        guarded("Timetable failed: ${query.operator}") {
            val cached = timetableDao.get(query)
            if (!force && cached.isNotEmpty() && now() - cached.first().fetchedAt < TIMETABLE_FRESH_MS) {
                return@guarded Result.Success(cached)
            }
            val q = query
            when (val res = apiCall(gson) { api.timetable(q.operator.name, q.originId, q.destId, q.travelDate) }) {
                is Result.Failure -> res
                is Result.Success -> {
                    val time = now()
                    val trips = res.data.data?.trips
                        ?.map { it.toEntity(q, time) ?: return@guarded timetableParseError(q.operator) }
                        ?: return@guarded timetableParseError(q.operator)
                    timetableDao.replace(q, trips)
                    Result.Success(trips)
                }
            }
        }

    private fun timetableParseError(operator: Operator): Result.Failure {
        Log.w(TAG, "Invalid timetable payload: $operator")
        return Result.Failure(AppError.ParseError)
    }

    /** FR-SRCH-07：最近 5 筆不重複查詢 */
    fun recentSearches(): Flow<List<RecentSearch>> = historyDao.recent(RECENT_LIMIT)

    /** FR-SRCH-01：查詢條件寫入 search_history */
    suspend fun saveSearch(
        uid: String?,
        operator: Operator,
        originId: String,
        destId: String,
        travelDate: String,
        source: SearchSource,
    ): Result<Unit> = guarded("Search history insert failed") {
        historyDao.insert(SearchHistory(0, uid, operator, originId, destId, travelDate, source, now()))
        Result.Success(Unit)
    }

    /** FR-SRCH-07：長按刪除該路線的紀錄 */
    suspend fun deleteRecent(item: RecentSearch): Result<Unit> = guarded("Search history delete failed") {
        historyDao.deleteRoute(item.operator, item.originId, item.destId)
        Result.Success(Unit)
    }

    /** 6.3：Repository 回傳 Result，資料庫等例外不拋到呼叫端 */
    private inline fun <T> guarded(log: String, block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$log ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    private suspend fun download(operator: Operator, force: Boolean): Result<Int> {
        val last = stationDao.lastUpdated(operator)
        if (!force && last != null && now() - last < STATIONS_STALE_MS) return Result.Success(0)

        return when (val res = apiCall(gson) { api.stations(operator.name) }) {
            is Result.Failure -> res
            is Result.Success -> {
                val time = now()
                val stations = res.data.data?.stations?.map { it.toEntity(operator, time) ?: return parseError(operator) }
                if (stations.isNullOrEmpty()) return parseError(operator)
                stationDao.upsertAll(stations)
                Result.Success(stations.size)
            }
        }
    }

    /** 11.2：ParseError 記錄 log（只記運具） */
    private fun parseError(operator: Operator): Result.Failure {
        Log.w(TAG, "Invalid stations payload: $operator")
        return Result.Failure(AppError.ParseError)
    }

    private companion object {
        const val TAG = "TransitRepository"
        const val STATIONS_STALE_MS = 7L * 24 * 60 * 60 * 1000
        const val RECENT_LIMIT = 5
        const val TIMETABLE_FRESH_MS = 2L * 60 * 60 * 1000
    }
}

internal fun StationDto.toEntity(operator: Operator, updatedAt: Long): Station? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val zh = nameZh?.takeIf { it.isNotBlank() } ?: return null
    val city = city?.takeIf { it.isNotBlank() }?.let(StationNameNormalizer::normalize)
    return Station(operator, id, StationNameNormalizer.normalize(zh), nameEn, sortKey, updatedAt, city)
}

private val HHMM = Regex("""([01]\d|2[0-3]):[0-5]\d""")

/** E-1／E-2 → timetable_cache；必要欄位缺少或時間格式不對回 null（整份視為 ParseError） */
internal fun TripDto.toEntity(q: TimetableQuery, fetchedAt: Long): TimetableCache? {
    val no = tripId?.takeIf { it.isNotBlank() } ?: return null
    val dep = departTime?.takeIf { HHMM.matches(it) } ?: return null
    val arr = arriveTime?.takeIf { HHMM.matches(it) } ?: return null
    return TimetableCache(
        operator = q.operator, originId = q.originId, destId = q.destId, travelDate = q.travelDate,
        trainNo = no, trainTypeCode = typeCode, trainTypeName = typeName, tripLine = line,
        departTime = dep, arriveTime = arr, durationMin = durationMin ?: return null,
        reservedSeat = reservedSeat ?: false, bikeAllowed = bikeAllowed ?: false,
        arriveEstimated = arriveEstimated ?: false, fetchedAt = fetchedAt,
    )
}
