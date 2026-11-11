package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.data.remote.apiCall
import com.group11.hometide.data.remote.dto.StationDto
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import com.group11.hometide.util.StationNameNormalizer
import kotlin.coroutines.cancellation.CancellationException

/** 車站、時刻、票價、即時（規格 6.1）。目前只有車站清單（FR-SRCH-02）。 */
class TransitRepository(
    private val api: HometideApi,
    private val stationDao: StationDao,
    private val gson: Gson,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * 本機沒有資料或超過 7 天（與 Worker 快取相同，8.5）才下載；成功回傳寫入筆數，未過期回傳 0。
     * BUS 站牌於第 3 週 Worker 支援後再加。
     */
    suspend fun refreshStations(operator: Operator, force: Boolean = false): Result<Int> {
        if (operator == Operator.BUS) return Result.Failure(AppError.NotFound)
        // 6.3：Repository 回傳 Result，資料庫等例外不拋到呼叫端
        return try {
            download(operator, force)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Station refresh failed: $operator ${e.javaClass.simpleName}")
            Result.Failure(AppError.Unknown)
        }
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
    }
}

/** 縣市（city）7.1.2 沒有欄位，不保存 */
internal fun StationDto.toEntity(operator: Operator, updatedAt: Long): Station? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val zh = nameZh?.takeIf { it.isNotBlank() } ?: return null
    return Station(operator, id, StationNameNormalizer.normalize(zh), nameEn, sortKey, updatedAt)
}
