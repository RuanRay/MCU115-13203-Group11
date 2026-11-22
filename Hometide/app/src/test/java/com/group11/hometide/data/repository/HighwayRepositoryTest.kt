package com.group11.hometide.data.repository

import com.google.gson.Gson
import com.group11.hometide.data.local.dao.HighwayCacheDao
import com.group11.hometide.data.local.entity.HighwayCache
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.QuotaGate
import com.group11.hometide.data.remote.dto.ApiEnvelope
import com.group11.hometide.data.remote.dto.CompareData
import com.group11.hometide.data.remote.dto.CompareOptionDto
import com.group11.hometide.data.remote.dto.DriveDto
import com.group11.hometide.data.remote.dto.FareData
import com.group11.hometide.data.remote.dto.FreewayDirectionDto
import com.group11.hometide.data.remote.dto.FreewayDto
import com.group11.hometide.data.remote.dto.FreewayMetaData
import com.group11.hometide.data.remote.dto.InterchangeDto
import com.group11.hometide.data.remote.dto.LiveData
import com.group11.hometide.data.remote.dto.SegmentDto
import com.group11.hometide.data.remote.dto.StationsData
import com.group11.hometide.data.remote.dto.TimetableData
import com.group11.hometide.data.remote.dto.TrafficData
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class HighwayRepositoryTest {

    private var now = 1_000_000L
    private val q = HighwayQuery("000010", "S", "桃園", "永康")

    private val trafficOk = TrafficData(
        "國道 1 號", "南下", "桃園", "永康", 185, 160, 1,
        listOf(SegmentDto("桃園", "機場系統", 98, "SMOOTH", 4, false), SegmentDto("機場系統", "中壢", null, "JAM", null, true)),
    )
    private val compareOk = CompareData(
        "桃園市", "臺南市", DriveDto(185),
        listOf(
            CompareOptionDto("THSR", "高鐵・桃園站", "14:31", "15:36", 26, 50, 160, "含轉乘"),
            CompareOptionDto("TRA", "台鐵", null, null, null, null, null, "今日已無班次"),
        ),
        "THSR",
    )

    private class FakeCache : HighwayCacheDao {
        val rows = mutableMapOf<String, HighwayCache>()
        override suspend fun get(routeKey: String) = rows[routeKey]
        override suspend fun upsert(cache: HighwayCache) {
            rows[cache.routeKey] = cache
        }
    }

    private inner class FakeApi : HometideApi {
        var traffic: () -> ApiEnvelope<TrafficData> = { ApiEnvelope(1L, "TDX", trafficOk) }
        var compare: () -> ApiEnvelope<CompareData> = { ApiEnvelope(1L, "TDX", compareOk) }
        var metaCalls = 0
        var trafficCalls = 0
        override suspend fun freewayMeta(): ApiEnvelope<FreewayMetaData> {
            metaCalls++
            return ApiEnvelope(
                1L, "TDX",
                FreewayMetaData(
                    listOf(
                        FreewayDto("000010", "國道 1 號", listOf(FreewayDirectionDto("S", "南下", listOf(InterchangeDto("基隆端", 0.0), InterchangeDto("基隆", 1.1))))),
                        FreewayDto(null, "缺代碼", emptyList()),
                    ),
                ),
            )
        }
        override suspend fun freewayTraffic(freeway: String, dir: String, from: String, to: String): ApiEnvelope<TrafficData> {
            trafficCalls++
            return traffic()
        }
        override suspend fun compare(freeway: String, dir: String, from: String, to: String) = compare()
        override suspend fun stations(operator: String): ApiEnvelope<StationsData> = TODO()
        override suspend fun timetable(operator: String, from: String, to: String, date: String): ApiEnvelope<TimetableData> = TODO()
        override suspend fun fare(operator: String, from: String, to: String): ApiEnvelope<FareData> = TODO()
        override suspend fun live(operator: String, from: String, to: String): ApiEnvelope<LiveData> = TODO()
    }

    private val api = FakeApi()
    private val cache = FakeCache()
    private val gson = Gson()
    private fun repo(gate: QuotaGate = QuotaGate(gson) { now }) = HighwayRepository(api, cache, gson, gate) { now }
    private val quota = HttpException(Response.error<Any>(429, "{}".toResponseBody()))

    @Test
    fun freeways_skipsInvalid_andCachesInMemory() = runBlocking {
        val r = repo()
        val list = (r.freeways() as Result.Success).data
        assertEquals(listOf("000010"), list.map { it.id })
        assertEquals(listOf("基隆端", "基隆"), list[0].directions[0].interchanges)
        r.freeways()
        assertEquals(1, api.metaCalls)
    }

    @Test
    fun load_mapsTrafficAndCompare_andWritesCache() = runBlocking {
        val res = (repo().load(q) as Result.Success).data
        assertEquals(185, res.traffic.estimatedMin)
        assertEquals(listOf(RoadLevel.SMOOTH to false, RoadLevel.JAM to true), res.traffic.segments.map { it.level to it.closed })
        assertEquals("THSR", res.compare?.fastest)
        assertEquals(listOf(CompareMode.THSR, CompareMode.TRA), res.compare?.options?.map { it.mode })
        assertNull(res.error)
        assertEquals(now, cache.rows[q.routeKey]?.updatedAt)
    }

    /** FR-HWY-05／TC-38：離線時顯示快取與最後更新時間 */
    @Test
    fun load_offlineFallsBackToCache() = runBlocking {
        repo().load(q)
        val cachedAt = now
        now += 10 * 60_000
        api.traffic = { throw IOException("offline") }
        api.compare = { throw IOException("offline") }
        val res = (repo().load(q) as Result.Success).data
        assertEquals(AppError.NetworkError, res.error)
        assertEquals(cachedAt, res.updatedAt)
        assertEquals(185, res.traffic.estimatedMin)
        assertEquals("THSR", res.compare?.fastest)
    }

    @Test
    fun load_offlineWithoutCache_fails() = runBlocking {
        api.traffic = { throw IOException("offline") }
        assertEquals(Result.Failure(AppError.NetworkError), repo().load(q))
    }

    @Test
    fun load_onlyCompareFails_keepsTraffic_andUsesCachedCompare() = runBlocking {
        repo().load(q)
        api.compare = { throw IOException("offline") }
        val res = (repo().load(q) as Result.Success).data
        assertNull(res.error)
        assertEquals("THSR", res.compare?.fastest)
    }

    @Test
    fun load_invalidTraffic_isParseError() = runBlocking {
        api.traffic = { ApiEnvelope(1L, "TDX", trafficOk.copy(segments = emptyList())) }
        assertEquals(Result.Failure(AppError.ParseError), repo().load(q))
    }

    /** 11.2：QUOTA 後 60 秒內不再呼叫 Worker；冷卻與 TransitRepository 共用同一個 gate */
    @Test
    fun load_quotaCooldownSharedGate() = runBlocking {
        val gate = QuotaGate(gson) { now }
        api.traffic = { throw quota }
        api.compare = { throw quota }
        val r = repo(gate)
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.load(q))
        assertEquals(Result.Failure(AppError.QuotaExceeded), r.load(q))
        assertEquals(1, api.trafficCalls)
        assertTrue(gate.call { "x" } is Result.Failure)
        now += QuotaGate.COOLDOWN_MS
        assertEquals(Result.Success("x"), gate.call { "x" })
    }

    /** App 重開後離線：交流道清單讀 highway_cache 的「meta」 */
    @Test
    fun freeways_offlineAfterRestart_readsCachedMeta() = runBlocking {
        repo().freeways() // 寫入快取
        val fresh = HighwayRepository(object : HometideApi by api {
            override suspend fun freewayMeta(): ApiEnvelope<FreewayMetaData> = throw IOException("offline")
        }, cache, gson, QuotaGate(gson) { now }) { now }
        assertEquals(listOf("000010"), (fresh.freeways() as Result.Success).data.map { it.id })
    }

    @Test
    fun freeways_offlineWithoutCache_fails() = runBlocking {
        val fresh = HighwayRepository(object : HometideApi by api {
            override suspend fun freewayMeta(): ApiEnvelope<FreewayMetaData> = throw IOException("offline")
        }, cache, gson, QuotaGate(gson) { now }) { now }
        assertEquals(Result.Failure(AppError.NetworkError), fresh.freeways())
    }

    @Test
    fun load_remembersLastQuery() = runBlocking {
        val r = repo()
        r.load(q)
        assertEquals(q, r.lastQuery)
    }
}
