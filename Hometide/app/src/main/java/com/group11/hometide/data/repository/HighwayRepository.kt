package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.HighwayCacheDao
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.QuotaGate
import com.group11.hometide.data.remote.dto.CompareData
import com.group11.hometide.data.remote.dto.FreewayMetaData
import com.group11.hometide.data.remote.dto.TrafficData
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/** 國道（FR-HWY-01～05）：交流道清單、路況＋回家方式比較、highway_cache 離線快取 */
class HighwayRepository(
    private val api: HometideApi,
    private val cacheDao: HighwayCacheDao,
    private val gson: Gson,
    private val gate: QuotaGate,
    private val now: () -> Long = System::currentTimeMillis,
) {

    @Volatile private var meta: List<Freeway>? = null

    /** 最後一次查看的路線：在同一次執行中回到國道分頁時直接顯示（不跨重啟保存） */
    @Volatile var lastQuery: HighwayQuery? = null
        private set

    /**
     * E-9：交流道清單（Worker 快取 7 天，App 在記憶體保留到程序結束）。
     * 另存一份在 highway_cache（route_key「meta」，實作者確認不新增資料表），App 重開後離線仍可顯示上次的路線。
     */
    suspend fun freeways(): Result<List<Freeway>> = guarded("Freeway meta failed") {
        meta?.let { return@guarded Result.Success(it) }
        when (val res = gate.call { api.freewayMeta() }) {
            is Result.Failure -> readMetaCache()?.let { Result.Success(it.also { list -> meta = list }) } ?: res
            is Result.Success -> toFreeways(res.data.data)?.let { list ->
                meta = list
                runCatching { cacheDao.put(META_KEY, gson.toJson(MetaCache(list)), now()) }
                Result.Success(list)
            } ?: Result.Failure(AppError.ParseError)
        }
    }

    private suspend fun readMetaCache(): List<Freeway>? = runCatching {
        cacheDao.get(META_KEY)?.let { gson.fromJson(it.payloadJson, MetaCache::class.java)?.freeways }
            ?.takeIf { it.isNotEmpty() && it.all { f -> f.directions.isNotEmpty() } }
    }.getOrNull()

    /** Gson 無法直接還原 List 的泛型，包一層 */
    private data class MetaCache(val freeways: List<Freeway>)

    /**
     * FR-HWY-02～05：同時取路況與比較；成功時寫入 highway_cache。
     * - 路況失敗：有快取就回快取並附上錯誤（畫面顯示離線橫幅與最後更新時間），沒有才回 Failure
     * - 只有比較失敗：照常顯示路況，比較沿用快取（沒有就不顯示）
     */
    suspend fun load(q: HighwayQuery): Result<HighwayResult> = guarded("Highway load failed") {
        lastQuery = q
        val (traffic, compare) = coroutineScope {
            val t = async { gate.call { api.freewayTraffic(q.freewayId, q.direction, q.entryIc, q.exitIc) } }
            val c = async { gate.call { api.compare(q.freewayId, q.direction, q.entryIc, q.exitIc) } }
            t.await() to c.await()
        }
        val cached = readCache(q)
        when (traffic) {
            is Result.Failure ->
                if (cached != null && traffic.error != AppError.NotFound) {
                    Result.Success(cached.copy(error = traffic.error))
                } else {
                    traffic
                }
            is Result.Success -> {
                val t = toTraffic(traffic.data.data) ?: return@guarded Result.Failure(AppError.ParseError)
                val c = (compare as? Result.Success)?.let { toCompare(it.data.data) } ?: cached?.compare
                val result = HighwayResult(t, c, now(), null)
                cacheDao.put(q.routeKey, gson.toJson(result.copy(error = null)), result.updatedAt)
                Result.Success(result)
            }
        }
    }

    private suspend fun readCache(q: HighwayQuery): HighwayResult? = runCatching {
        cacheDao.get(q.routeKey)?.let { gson.fromJson(it.payloadJson, HighwayResult::class.java) }
            ?.takeIf { it.traffic.segments.isNotEmpty() } // Gson 不檢查非 null 欄位，損壞的快取當作沒有
    }.getOrNull()

    private inline fun <T> guarded(log: String, block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$log ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    companion object {
        private const val TAG = "HighwayRepository"

        /** 路線的 route_key 含「|」，不會與此衝突 */
        private const val META_KEY = "meta"

        /** Gson 不保證非 null：缺欄位的國道、方向、交流道略過；整份不可用時回 null */
        fun toFreeways(d: FreewayMetaData?): List<Freeway>? = d?.freeways?.mapNotNull { f ->
            val dirs = f.directions.orEmpty().mapNotNull { dir ->
                val ics = dir.interchanges.orEmpty().mapNotNull { it.name }
                if (dir.dir == null || dir.name == null || ics.size < 2) null else FreewayDirection(dir.dir, dir.name, ics)
            }
            if (f.id == null || f.name == null || dirs.isEmpty()) null else Freeway(f.id, f.name, dirs)
        }?.takeIf { it.isNotEmpty() }

        fun toTraffic(d: TrafficData?): Traffic? {
            d ?: return null
            val segments = d.segments?.map { s ->
                RoadSegment(
                    fromIc = s.fromIc ?: return null,
                    toIc = s.toIc ?: return null,
                    speedKmh = s.speedKmh,
                    level = RoadLevel.entries.firstOrNull { it.name == s.level } ?: RoadLevel.UNKNOWN,
                    travelMin = s.travelMin,
                    closed = s.closed ?: false,
                )
            }
            return Traffic(
                freewayName = d.freewayName ?: return null,
                directionName = d.directionName ?: return null,
                from = d.from ?: return null,
                to = d.to ?: return null,
                estimatedMin = d.estimatedMin ?: return null,
                normalMin = d.normalMin ?: return null,
                congestedCount = d.congestedCount ?: 0,
                segments = segments?.takeIf { it.isNotEmpty() } ?: return null,
            )
        }

        fun toCompare(d: CompareData?): Compare? {
            d ?: return null
            val options = d.options?.mapNotNull { o ->
                val mode = CompareMode.entries.firstOrNull { it.name == o.operator } ?: return@mapNotNull null
                CompareOption(mode, o.label ?: return@mapNotNull null, o.departTime, o.arriveTime, o.totalMin, o.note.orEmpty())
            }
            return Compare(
                fromCity = d.fromCity ?: return null,
                toCity = d.toCity ?: return null,
                driveMin = d.drive?.totalMin ?: return null,
                options = options?.takeIf { it.isNotEmpty() } ?: return null,
                fastest = d.fastest ?: return null,
            )
        }
    }
}

data class Freeway(val id: String, val name: String, val directions: List<FreewayDirection>)

/** [interchanges] 依行車方向排序（E-9） */
data class FreewayDirection(val dir: String, val name: String, val interchanges: List<String>)

data class HighwayQuery(val freewayId: String, val direction: String, val entryIc: String, val exitIc: String) {
    /** 7.1.13：route_key = freeway_id|direction|entry_ic|exit_ic */
    val routeKey: String get() = "$freewayId|$direction|$entryIc|$exitIc"
}

enum class RoadLevel { SMOOTH, BUSY, JAM, UNKNOWN }

data class RoadSegment(
    val fromIc: String,
    val toIc: String,
    val speedKmh: Int?,
    val level: RoadLevel,
    val travelMin: Int?,
    /** 道路封閉：level 為 JAM，時速位置顯示「封閉」 */
    val closed: Boolean,
)

data class Traffic(
    val freewayName: String,
    val directionName: String,
    val from: String,
    val to: String,
    val estimatedMin: Int,
    val normalMin: Int,
    val congestedCount: Int,
    val segments: List<RoadSegment>,
)

enum class CompareMode { THSR, TRA, BUS }

/** [departTime] 為 null：今天沒有班次或縣市沒有這種運具（[note] 為「今日已無班次」或「—」） */
data class CompareOption(
    val mode: CompareMode,
    val label: String,
    val departTime: String?,
    val arriveTime: String?,
    val totalMin: Int?,
    val note: String,
)

/** [fastest]：THSR／TRA／BUS／DRIVE */
data class Compare(
    val fromCity: String,
    val toCity: String,
    val driveMin: Int,
    val options: List<CompareOption>,
    val fastest: String,
)

/** [error] 不為 null 表示顯示的是快取（[updatedAt] 為快取時間） */
data class HighwayResult(
    val traffic: Traffic,
    val compare: Compare?,
    val updatedAt: Long,
    val error: AppError?,
)
