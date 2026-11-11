package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.group11.hometide.data.local.entity.Feedback

@Dao
interface FeedbackDao {

    @Insert
    suspend fun insert(feedback: Feedback): Long

    @Query("SELECT * FROM feedback WHERE ai_status = 'PENDING' ORDER BY created_at")
    suspend fun pendingAi(): List<Feedback>

    @Query("SELECT * FROM feedback WHERE synced = 0 ORDER BY created_at LIMIT :limit")
    suspend fun unsynced(limit: Int): List<Feedback>

    @Query("UPDATE feedback SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
