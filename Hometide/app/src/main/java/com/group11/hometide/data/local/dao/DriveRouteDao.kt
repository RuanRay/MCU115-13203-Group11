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

    /** 重複路線違反 UNIQUE 時拋出 SQLiteConstraintException */
    @Insert
    suspend fun insert(route: DriveRoute): Long

    @Query("DELETE FROM drive_route WHERE id = :id")
    suspend fun delete(id: Long)

    /** 依 ids 的順序重設 sort_order（0 起算） */
    @Transaction
    suspend fun updateOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("UPDATE drive_route SET sort_order = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}
