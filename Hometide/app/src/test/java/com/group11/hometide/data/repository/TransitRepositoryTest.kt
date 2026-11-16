package com.group11.hometide.data.repository

import android.database.Cursor
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.StationDao
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
        override suspend fun findByName(operator: Operator, name: String): Station? =
            saved.firstOrNull { it.operator == operator && it.nameZh == name }
    }

    private object NoHistory : SearchHistoryDao {
        override suspend fun insert(history: SearchHistory) = Unit
        override fun recent(limit: Int): Flow<List<RecentSearch>> = emptyFlow()
        override suspend fun deleteRoute(operator: Operator, originId: String, destId: String) = Unit
    }

    private class FakeApi(val respond: () -> ApiEnvelope<StationsData>) : HometideApi {
        var calls = 0
        override suspend fun stations(operator: String): ApiEnvelope<StationsData> {
            calls++
            return respond()
        }
    }

    private fun ok(vararg s: StationDto) = ApiEnvelope(1L, "TDX", StationsData("TRA", s.toList()))
    private val tainan = StationDto("4220", "台南", "Tainan", "臺南市", 0)

    private fun repo(api: HometideApi, dao: StationDao) = TransitRepository(api, dao, NoHistory, Gson()) { now }

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
