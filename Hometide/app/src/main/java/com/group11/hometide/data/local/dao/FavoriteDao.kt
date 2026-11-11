package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Operator
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Query("SELECT * FROM favorite_route WHERE uid = :uid ORDER BY sort_order, id")
    fun observe(uid: String): Flow<List<FavoriteRoute>>

    /**
     * 已收藏同一路線（uid＋operator＋起迄）就移除，否則新增並排在最後。
     * route 的 id 與 sort_order 會被忽略。回傳 true 表示切換後為已收藏。
     */
    @Transaction
    suspend fun toggle(route: FavoriteRoute): Boolean {
        val existing = find(route.uid, route.operator, route.originId, route.destId)
        if (existing != null) {
            deleteById(existing.id)
            return false
        }
        insert(route.copy(id = 0, sortOrder = (maxSortOrder(route.uid) ?: -1) + 1))
        return true
    }

    /** 依 ids 的順序重設 sort_order（0 起算） */
    @Transaction
    suspend fun updateOrder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Query("SELECT * FROM favorite_route WHERE uid = :uid AND operator = :operator AND origin_id = :originId AND dest_id = :destId")
    suspend fun find(uid: String, operator: Operator, originId: String, destId: String): FavoriteRoute?

    @Query("SELECT MAX(sort_order) FROM favorite_route WHERE uid = :uid")
    suspend fun maxSortOrder(uid: String): Int?

    @Insert
    suspend fun insert(route: FavoriteRoute): Long

    @Query("DELETE FROM favorite_route WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE favorite_route SET sort_order = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)
}
