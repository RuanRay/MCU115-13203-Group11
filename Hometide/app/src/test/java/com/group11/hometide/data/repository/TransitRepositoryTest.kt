package com.group11.hometide.data.repository

import android.database.Cursor
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.dao.FareDao
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.remote.dto.FareData
import com.group11.hometide.data.remote.dto.FareDto
import com.group11.hometide.data.remote.dto.LiveData
import com.group11.hometide.data.remote.dto.LiveItemDto
import com.group11.hometide.data.local.dao.TimetableDao
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.remote.dto.TimetableData
import com.group11.hometide.data.remote.dto.TripDto
import com.group11.hometide.data.local.dao.RecentSearch
import com.group11.hometide.data.local.dao.SearchHistoryDao
import com.group11.hometide.data.local.entity.SearchHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.dto.ApiEnvelope
import com.group11.hometide.data.remote.dto.StationDto
import com.group11.hometide.data.remote.dto.StationsData
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** refreshStations：7 天過期、force、資料驗證、錯誤對應、資料庫例外不外拋 */
class TransitRepositoryTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day

    private class FakeDao(var last: Long? = null, val failUpsert: Boolean = false) : StationDao {
        val saved = mutableListOf<Station>()
        override fun searchCursorByPattern(operator: Operator, contains: String, prefix: String): Cursor = TODO()
        override suspend fun upsertAll(stations: List<Station>) {
            if (failUpsert) throw IllegalStateException("disk full")
            saved += stations
        }
        override suspend fun lastUpdated(operator: Operator): Long? = last
        override suspend fun nameOf(operator: Operator, stationId: String): String? =
            saved.firstOrNull { it.operator == operator && it.stationId == stationId }?.nameZh
        override suspend fun findByName(operator: Operator, name: String): Station? =
            saved.firstOrNull { it.operator == operator && it.nameZh == name }
    }

    private object NoHistory : SearchHistoryDao {
        override suspend fun insert(history: SearchHistory) = Unit
        override fun recent(limit: Int): Flow<List<RecentSearch>> = emptyFlow()
        override suspend fun deleteRoute(operator: Operator, originId: String, destId: String) = Unit
    }

    private class FakeApi(
        val trips: () -> ApiEnvelope<TimetableData> = { ApiEnvelope(1L, "TDX", TimetableData("TRA", emptyList())) },
        val respond: () -> ApiEnvelope<StationsData>,
    ) : HometideApi {
        var calls = 0
        var timetableCalls = 0
        override suspend fun stations(operator: String): ApiEnvelope<StationsData> {
            calls++
            return respond()
        }
        override suspend fun timetable(operator: String, from: String, to: String, date: String): ApiEnvelope<TimetableData> {
            timetableCalls++
            return trips()
        }
        var fareCalls = 0
        var fareResponse: () -> ApiEnvelope<FareData> = { ApiEnvelope(1L, "TDX", FareData("TRA", emptyList())) }
        override suspend fun fare(operator: String, from: String, to: String): ApiEnvelope<FareData> {
            fareCalls++
            return fareResponse()
        }
        var liveResponse: () -> ApiEnvelope<LiveData> = { ApiEnvelope(1L, "TDX", LiveData(emptyList())) }
        override suspend fun live(operator: String, from: String, to: String): ApiEnvelope<LiveData> = liveResponse()
        override suspend fun freewayMeta() = TODO()
        override suspend fun freewayTraffic(freeway: String, dir: String, from: String, to: String) = TODO()
        override suspend fun compare(freeway: String, dir: String, from: String, to: String) = TODO()
    }

    private class FakeFareDao : FareDao {
        val rows = mutableListOf<FareCache>()
        override suspend fun get(operator: Operator, originId: String, destId: String) =
            rows.filter { it.operator == operator && it.originId == originId && it.destId == destId }
        override suspend fun delete(operator: Operator, originId: String, destId: String) {
            rows.removeAll { it.operator == operator && it.originId == originId && it.destId == destId }
        }
        override suspend fun insertAll(fares: List<FareCache>) {
            rows += fares
        }
    }

    private class FakeTimetableDao : TimetableDao {
        val rows = mutableListOf<TimetableCache>()
        override suspend fun get(operator: Operator, originId: String, destId: String, travelDate: String) =
            rows.filter { it.operator == operator && it.originId == originId && it.destId == destId && it.travelDate == travelDate }
        var purgedBefore: Long? = null
        override suspend fun deleteOlderThan(time: Long): Int {
            purgedBefore = time
            return 0
        }
        override suspend fun delete(operator: Operator, originId: String, destId: String, travelDate: String) {
            rows.removeAll { it.operator == operator && it.originId == originId && it.destId == destId && it.travelDate == travelDate }
        }
        override suspend fun insertAll(trains: List<TimetableCache>) {
            rows += trains
        }
    }

    private fun ok(vararg s: StationDto) = ApiEnvelope(1L, "TDX", StationsData("TRA", s.toList()))
    private val tainan = StationDto("4220", "台南", "Tainan", "臺南市", 0)

    private val timetableDao = FakeTimetableDao()
    private val fareDao = FakeFareDao()

    private fun repo(api: HometideApi, dao: StationDao) = TransitRepository(api, dao, NoHistory, timetableDao, fareDao, Gson()) { now }

    @Test
    fun downloadsWhenEmpty_andNormalizes() = runBlocking {
        val dao = FakeDao()
        assertEquals(Result.Success(1), repo(FakeApi { ok(tainan) }, dao).refreshStations(Operator.TRA))
        assertEquals("臺南", dao.saved.single().nameZh)
        assertEquals(now, dao.saved.single().updatedAt)
    }

    @Test
    fun skipsWithin7Days_unlessForced() = runBlocking {
        val api = FakeApi { ok(tainan) }
        val dao = FakeDao(last = now - 6 * day)
        assertEquals(Result.Success(0), repo(api, dao).refreshStations(Operator.TRA))
        assertEquals(0, api.calls)
        assertEquals(Result.Success(1), repo(api, dao).refreshStations(Operator.TRA, force = true))
        assertEquals(1, api.calls)
    }

    @Test
    fun downloadsAfter7Days() = runBlocking {
        val api = FakeApi { ok(tainan) }
        repo(api, FakeDao(last = now - 8 * day)).refreshStations(Operator.TRA)
        assertEquals(1, api.calls)
    }

    @Test
    fun invalidPayload_isParseError() = runBlocking {
        assertEquals(Result.Failure(AppError.ParseError), repo(FakeApi { ok() }, FakeDao()).refreshStations(Operator.TRA))
        assertEquals(
            Result.Failure(AppError.ParseError),
            repo(FakeApi { ok(StationDto(null, "臺北", null, null, null)) }, FakeDao()).refreshStations(Operator.TRA),
        )
    }

    @Test
    fun workerErrorEnvelope_isMapped() = runBlocking {
        val body = """{"error":{"code":"QUOTA","message":"TDX rate limited"}}""".toResponseBody()
        val api = FakeApi { throw HttpException(Response.error<Any>(429, body)) }
        assertEquals(Result.Failure(AppError.QuotaExceeded), repo(api, FakeDao()).refreshStations(Operator.TRA))
    }

    @Test
    fun httpStatusFallback_whenNoEnvelope() = runBlocking {
        val api = FakeApi { throw HttpException(Response.error<Any>(502, "<html>".toResponseBody())) }
        assertEquals(Result.Failure(AppError.AuthError), repo(api, FakeDao()).refreshStations(Operator.TRA))
    }

    @Test
    fun ioException_isNetworkError() = runBlocking {
        assertEquals(
            Result.Failure(AppError.NetworkError),
            repo(FakeApi { throw IOException("offline") }, FakeDao()).refreshStations(Operator.TRA),
        )
    }

    @Test
    fun dbFailure_isReturnedNotThrown() = runBlocking {
        assertEquals(
            Result.Failure(AppError.Unknown),
            repo(FakeApi { ok(tainan) }, FakeDao(failUpsert = true)).refreshStations(Operator.TRA),
        )
    }

    private val query = TimetableQuery(Operator.TRA, "1080", "4220", "2026-11-16")
    private val trip = TripDto("139", "3", "自強", 1, "14:12", "17:20", 188, false, true, false)
    private fun trips(vararg t: TripDto) = ApiEnvelope(1L, "TDX", TimetableData("TRA", t.toList()))

    @Test
    fun timetable_fetchesAndCaches_thenReusesWithin2Hours() = runBlocking {
        val api = FakeApi({ trips(trip) }) { ok(tainan) }
        val r = repo(api, FakeDao())
        val first = (r.timetable(query) as Result.Success).data
        assertEquals(listOf("139"), first.trains.map { it.trainNo })
        assertEquals(1, first.trains.single().tripLine)
        assertEquals(null, first.error)
        assertEquals(1, (r.timetable(query) as Result.Success).data.trains.size)
        assertEquals(1, api.timetableCalls) // 2 小時內不重複呼叫（8.5）
        r.timetable(query, force = true)
        assertEquals(2, api.timetableCalls) // 下拉重新整理略過 2 小時快取（FR-RES-06）
    }

    @Test
    fun timetable_invalidPayloadIsParseError_withoutCache() = runBlocking {
        val api = FakeApi({ trips(trip.copy(departTime = "25:00")) }) { ok(tainan) }
        assertEquals(Result.Failure(AppError.ParseError), repo(api, FakeDao()).timetable(query))
        assertEquals(1, api.timetableCalls)
    }

    /** FR-OFF-01：查詢失敗時讀快取（3 天內），附上最後更新時間與錯誤 */
    @Test
    fun timetable_offlineFallsBackToStaleCache() = runBlocking {
        val fetched = now - 5 * 60 * 60 * 1000
        timetableDao.rows += first(trip).copy(fetchedAt = fetched)
        val api = FakeApi({ throw IOException("offline") }) { ok(tainan) }
        val t = (repo(api, FakeDao()).timetable(query) as Result.Success).data
        assertEquals(listOf("139"), t.trains.map { it.trainNo })
        assertEquals(fetched, t.cachedAt)
        assertEquals(AppError.NetworkError, t.error)
    }

    @Test
    fun timetable_offlineWithoutUsableCache_fails_andNotFoundIgnoresCache() = runBlocking {
        timetableDao.rows += first(trip).copy(fetchedAt = now - 4 * day) // 超過 3 天不用
        assertEquals(Result.Failure(AppError.NetworkError), repo(FakeApi({ throw IOException("x") }) { ok(tainan) }, FakeDao()).timetable(query))
        timetableDao.rows.clear()
        timetableDao.rows += first(trip).copy(fetchedAt = now - 5 * 60 * 60 * 1000)
        val notFound = HttpException(Response.error<Any>(404, "{}".toResponseBody()))
        assertEquals(Result.Failure(AppError.NotFound), repo(FakeApi({ throw notFound }) { ok(tainan) }, FakeDao()).timetable(query))
    }

    /** 11.2 QuotaExceeded：60 秒內只讀快取，不再呼叫 Worker */
    @Test
    fun timetable_quotaCooldown60s() = runBlocking {
        timetableDao.rows += first(trip).copy(fetchedAt = now - 5 * 60 * 60 * 1000)
        val quota = HttpException(Response.error<Any>(429, "{}".toResponseBody()))
        val api = FakeApi({ throw quota }) { ok(tainan) }
        val r = repo(api, FakeDao())
        assertEquals(AppError.QuotaExceeded, (r.timetable(query) as Result.Success).data.error)
        assertEquals(AppError.QuotaExceeded, (r.timetable(query, force = true) as Result.Success).data.error)
        assertEquals(1, api.timetableCalls)
    }

    /** 11.2：票價收到 QUOTA 後 60 秒內不再呼叫 Worker，有快取就用快取（TC-51 不受影響） */
    @Test
    fun fares_quotaCooldown60s_usesCache() = runBlocking {
        val quota = HttpException(Response.error<Any>(429, "{}".toResponseBody()))
        val api = FakeApi { ok(tainan) }
        api.fareResponse = { throw quota }
        val r = repo(api, FakeDao())
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.fares(Operator.TRA, "1000", "4220"))
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.fares(Operator.TRA, "1000", "4220"))
        assertEquals(1, api.fareCalls)
        fareDao.rows += FareCache(Operator.TRA, "1080", "4220", "3", FareClass.ADULT, 891, false, now - 8 * day)
        assertEquals(891, (r.fares(Operator.TRA, "1080", "4220") as Result.Success).data.single().price)
        assertEquals(1, api.fareCalls)
    }

    /** 冷卻共用：時刻表被限流後，票價、即時、站名 60 秒內也不呼叫 Worker */
    @Test
    fun quotaCooldown_sharedAcrossCalls() = runBlocking {
        val quota = HttpException(Response.error<Any>(429, "{}".toResponseBody()))
        var stationCalls = 0
        val api = FakeApi({ throw quota }) { stationCalls++; ok(tainan) }
        val r = repo(api, FakeDao())
        r.timetable(query)
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.fares(Operator.TRA, "1080", "4220"))
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.live(Operator.TRA, "1080", "4220"))
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.refreshStations(Operator.TRA))
        assertEquals(listOf(1, 0, 0), listOf(api.timetableCalls, api.fareCalls, stationCalls))
    }

    @Test
    fun purgeOldTimetables_deletesOlderThan3Days() = runBlocking {
        assertEquals(Result.Success(0), repo(FakeApi { ok(tainan) }, FakeDao()).purgeOldTimetables())
        assertEquals(now - 3 * day, timetableDao.purgedBefore)
    }

    private fun first(t: TripDto) = t.toEntity(query, now)!!

    @Test
    fun fares_cachedFor7Days_skipsInvalid_keepsApprox() = runBlocking {
        val api = FakeApi { ok(tainan) }
        api.fareResponse = {
            ApiEnvelope(1L, "TDX", FareData("TRA", listOf(
                FareDto("3", "ADULT", 891, false), FareDto("4", "CHILD", 344, true),
                FareDto(null, "ADULT", 1, false), FareDto("6", "ADULT", 0, false), FareDto("6", "GROUP", 500, false),
            )))
        }
        val r = repo(api, FakeDao())
        val fares = (r.fares(Operator.TRA, "1000", "4220") as Result.Success).data
        assertEquals(listOf("3" to 891, "4" to 344), fares.map { it.trainTypeCode to it.price })
        assertEquals(listOf(false, true), fares.map { it.isApprox })
        assertEquals(listOf(FareClass.ADULT, FareClass.CHILD), fares.map { it.fareClass }) // 未知票種捨棄
        r.fares(Operator.TRA, "1000", "4220")
        assertEquals(1, api.fareCalls) // 7 天內讀 fare_cache
    }

    @Test
    fun fares_staleCacheUsedWhenOffline() = runBlocking {
        fareDao.rows += FareCache(Operator.TRA, "1000", "4220", "3", FareClass.ADULT, 891, false, now - 8 * day)
        val api = FakeApi { ok(tainan) }
        api.fareResponse = { throw IOException("offline") }
        assertEquals(891, (repo(api, FakeDao()).fares(Operator.TRA, "1000", "4220") as Result.Success).data.single().price)
    }

    @Test
    fun live_mapsDelayAndEta_dropsBlankIds() = runBlocking {
        val api = FakeApi { ok(tainan) }
        api.liveResponse = { ApiEnvelope(1L, "TDX", LiveData(listOf(LiveItemDto("143", 5, null), LiveItemDto("1610-14:30", null, 25), LiveItemDto("", 3, null)))) }
        assertEquals(
            listOf(LiveInfo("143", 5, null), LiveInfo("1610-14:30", null, 25)),
            (repo(api, FakeDao()).live(Operator.TRA, "1080", "4220") as Result.Success).data,
        )
    }

    @Test
    fun bus_downloadsStopsWithCity() = runBlocking {
        val dao = FakeDao()
        val stop = StationDto("臺北轉運站", "台北轉運站", "Taipei Bus Station", "台北市", 0)
        assertEquals(Result.Success(1), repo(FakeApi { ok(stop) }, dao).refreshStations(Operator.BUS))
        assertEquals("臺北市", dao.saved.single().city)
    }

    @Test
    fun findStation_triesFullNameThenWithoutTrailingStation() = runBlocking {
        val dao = FakeDao()
        val r = repo(FakeApi { ok(tainan) }, dao)
        dao.saved += Station(Operator.BUS, "臺北轉運站", "臺北轉運站", null, 0, 0)
        assertEquals("臺北轉運站", (r.findStation(Operator.BUS, "台北轉運站") as Result.Success).data?.stationId)
        r.refreshStations(Operator.TRA)
        assertEquals("4220", (r.findStation(Operator.TRA, "臺南站") as Result.Success).data?.stationId)
        assertEquals(null, (r.findStation(Operator.TRA, "火星") as Result.Success).data)
    }
}
