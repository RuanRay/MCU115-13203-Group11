package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.group11.hometide.data.local.entity.DriveRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveRouteDao {

    @Query("SELECT * FROM drive_route WHERE uid = :uid ORDER BY sort_order, id")
    fun observe(uid: String): Flow<List<DriveRoute>>

    /** 重複路線違反 UNIQUE 時拋出 SQLiteConstraintException；復原刪除時帶原本的 id 寫回 */
    @Insert
    suspend fun insert(route: DriveRoute): Long

    @Query("DELETE FROM drive_route WHERE id = :id")
    suspend fun delete(id: Long)

    /** FR-HWY-01：最多 10 條 */
    @Query("SELECT COUNT(*) FROM drive_route WHERE uid = :uid")
    suspend fun count(uid: String): Int

    /** 同一 uid＋國道＋方向＋起訖交流道唯一（7.1.12） */
    @Query(
        """SELECT * FROM drive_route WHERE uid = :uid AND freeway_id = :freewayId AND direction = :direction
           AND entry_ic = :entryIc AND exit_ic = :exitIc LIMIT 1""",
    )
    suspend fun find(uid: String, freewayId: String, direction: String, entryIc: String, exitIc: String): DriveRoute?

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM drive_route WHERE uid = :uid")
    suspend fun maxSortOrder(uid: String): Int

    @Query("UPDATE drive_route SET label = :label WHERE id = :id")
    suspend fun rename(id: Long, label: String)

    /** 依 ids 的順序重設 sort_order（0 起算） */
    @Transaction
    suspend fun updateOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE drive_route SET sort_order = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}
