package com.group11.hometide.data.repository

import android.util.Log
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.data.local.dao.DriveRouteDao
import com.group11.hometide.data.local.entity.DriveRoute
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.cancellation.CancellationException

/** 開車路線（FR-HWY-01、06；7.1.12 drive_route、7.3 last_drive_route）：只存本機，不同步雲端 */
class DriveRouteRepository(
    private val dao: DriveRouteDao,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observe(uid: String): Flow<List<DriveRoute>> = dao.observe(uid)

    /** 國道分頁上次選的開車路線 id */
    val lastDriveRoute: Flow<Long?> = settings.lastDriveRoute

    suspend fun setLastDriveRoute(id: Long) {
        runCatching { settings.setLastDriveRoute(id) }
    }

    /**
     * 收藏（實作者確認：命名後收藏）；同一條路線已收藏時沿用原本那筆；超過 10 條回 FavoriteLimit。
     * [label] 由畫面保證不是空白（命名對話框空白時用預設名稱「開車回家」）。
     */
    suspend fun add(uid: String, q: HighwayQuery, label: String): Result<DriveRoute> = guarded("Drive route add failed") {
        dao.find(uid, q.freewayId, q.direction, q.entryIc, q.exitIc)?.let { return@guarded Result.Success(it) }
        if (dao.count(uid) >= MAX_ROUTES) return@guarded Result.Failure(AppError.FavoriteLimit)
        val r = DriveRoute(0, uid, q.freewayId, q.direction, q.entryIc, q.exitIc, label.trim().take(MAX_LABEL), dao.maxSortOrder(uid) + 1, now())
        Result.Success(r.copy(id = dao.insert(r)))
    }

    /** 改名（最多 [MAX_LABEL] 字；空白不儲存，同 5-1 車票夾） */
    suspend fun rename(route: DriveRoute, label: String): Result<Unit> = guarded("Drive route rename failed") {
        val clean = label.trim().take(MAX_LABEL)
        if (clean.isNotEmpty()) dao.rename(route.id, clean)
        Result.Success(Unit)
    }

    suspend fun remove(route: DriveRoute): Result<Unit> = guarded("Drive route delete failed") {
        dao.delete(route.id)
        Result.Success(Unit)
    }

    /** 「復原」：以原本的 id 與排序寫回；同一條路線已經又收藏了就不重複寫；期間收滿 10 條回 FavoriteLimit */
    suspend fun restore(route: DriveRoute): Result<Unit> = guarded("Drive route restore failed") {
        if (dao.find(route.uid, route.freewayId, route.direction, route.entryIc, route.exitIc) != null) {
            return@guarded Result.Success(Unit)
        }
        if (dao.count(route.uid) >= MAX_ROUTES) return@guarded Result.Failure(AppError.FavoriteLimit)
        dao.insert(route)
        Result.Success(Unit)
    }

    private inline fun <T> guarded(log: String, block: () -> Result<T>): Result<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "$log ${e.javaClass.simpleName}")
        Result.Failure(AppError.Unknown)
    }

    companion object {
        private const val TAG = "DriveRouteRepository"
        const val MAX_ROUTES = 10
        const val MAX_LABEL = 10
    }
}
