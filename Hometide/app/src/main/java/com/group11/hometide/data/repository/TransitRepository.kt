package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.FareDao
import com.group11.hometide.data.local.dao.RecentSearch
import com.group11.hometide.data.local.dao.SearchHistoryDao
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.dao.TimetableDao
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.remote.dto.TripDto
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
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
    private val fareDao: FareDao,
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

    /** 收到 QUOTA 後的冷卻期（11.2：60 秒內只讀快取） */
    private var quotaUntil = 0L

    /**
     * FR-RES-01、FR-OFF-01：時刻表。
     * - 同條件 2 小時內讀 timetable_cache 不重複呼叫（8.5）；[force]（下拉重新整理）略過這個判斷
     * - 呼叫 Worker 成功：先刪後寫
     * - 呼叫失敗或 QUOTA 冷卻中：有 3 天內的快取就回傳快取並附上錯誤（畫面顯示離線橫幅或限流訊息），沒有才回 Failure
     */
    suspend fun timetable(query: TimetableQuery, force: Boolean = false): Result<Timetable> =
        guarded("Timetable failed: ${query.operator}") {
            val cached = timetableDao.get(query).filter { now() - it.fetchedAt < CACHE_KEEP_MS }
            val cachedAt = cached.firstOrNull()?.fetchedAt
            if (!force && cachedAt != null && now() - cachedAt < TIMETABLE_FRESH_MS) {
                return@guarded Result.Success(Timetable(cached))
            }
            val fallback = { e: AppError -> if (cachedAt != null) Result.Success(Timetable(cached, cachedAt, e)) else Result.Failure(e) }
            if (now() < quotaUntil) return@guarded fallback(AppError.QuotaExceeded)
            val q = query
            when (val res = apiCall(gson) { api.timetable(q.operator.name, q.originId, q.destId, q.travelDate) }) {
                is Result.Failure -> {
                    if (res.error == AppError.QuotaExceeded) quotaUntil = now() + QUOTA_COOLDOWN_MS
                    // NOT_FOUND＝查無班次（11.2），不改讀快取
                    if (res.error == AppError.NotFound) res else fallback(res.error)
                }
                is Result.Success -> {
                    val time = now()
                    val trips = res.data.data?.trips
                        ?.map { it.toEntity(q, time) ?: return@guarded fallback(timetableParseError(q.operator).error) }
                        ?: return@guarded fallback(timetableParseError(q.operator).error)
                    timetableDao.replace(q, trips)
                    Result.Success(Timetable(trips))
                }
            }
        }

    /** FR-OFF-01、7.1.4：啟動時清除超過 3 天的時刻快取，回傳刪除筆數 */
    suspend fun purgeOldTimetables(): Result<Int> = guarded("Timetable purge failed") {
        Result.Success(timetableDao.deleteOlderThan(now() - CACHE_KEEP_MS))
    }

    private fun timetableParseError(operator: Operator): Result.Failure {
        Log.w(TAG, "Invalid timetable payload: $operator")
        return Result.Failure(AppError.ParseError)
    }

    /** FR-RES-02、FR-BUS-03：各車種（國道客運為路線）的全票；依起訖站快取 7 天（7.1.5）。票種與多人於 4-5 */
    suspend fun fares(operator: Operator, originId: String, destId: String): Result<List<FareCache>> =
        guarded("Fare failed: $operator") {
            val cached = fareDao.get(operator, originId, destId)
            if (cached.isNotEmpty() && now() - cached.first().fetchedAt < FARE_FRESH_MS) return@guarded Result.Success(cached)
            when (val res = apiCall(gson) { api.fare(operator.name, originId, destId) }) {
                is Result.Failure -> if (cached.isNotEmpty()) Result.Success(cached) else res // 過期快取仍比「—」好
                is Result.Success -> {
                    val time = now()
                    val fares = res.data.data?.fares?.mapNotNull { f ->
                        val code = f.typeCode?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        val price = f.adultPrice?.takeIf { it > 0 } ?: return@mapNotNull null
                        FareCache(operator, originId, destId, code, FareClass.ADULT, price, f.approximate ?: false, time)
                    } ?: return@guarded Result.Failure(AppError.ParseError)
                    fareDao.replace(operator, originId, destId, fares)
                    Result.Success(fares)
                }
            }
        }

    /** FR-RES-03、FR-BUS-02：今天的即時資料（Worker 快取 60 秒，App 不另存） */
    suspend fun live(operator: Operator, originId: String, destId: String): Result<List<LiveInfo>> =
        guarded("Live failed: $operator") {
            when (val res = apiCall(gson) { api.live(operator.name, originId, destId) }) {
                is Result.Failure -> res
                is Result.Success -> Result.Success(
                    res.data.data?.items.orEmpty().mapNotNull { i ->
                        i.tripId?.takeIf { it.isNotBlank() }?.let { LiveInfo(it, i.delayMin, i.etaMin) }
                    },
                )
            }
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
        const val FARE_FRESH_MS = 7L * 24 * 60 * 60 * 1000
        const val CACHE_KEEP_MS = 3L * 24 * 60 * 60 * 1000
        const val QUOTA_COOLDOWN_MS = 60_000L
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

/** E-3 一筆：台鐵 delayMin、國道客運 etaMin */
data class LiveInfo(val tripId: String, val delayMin: Int?, val etaMin: Int?)

/** 時刻表結果：[cachedAt] 與 [error] 不為 null 表示查詢失敗、改用快取（FR-OFF-01） */
data class Timetable(val trains: List<TimetableCache>, val cachedAt: Long? = null, val error: AppError? = null)
