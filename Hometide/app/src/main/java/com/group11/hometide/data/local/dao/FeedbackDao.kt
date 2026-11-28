package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.group11.hometide.data.local.entity.AiStatus
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.local.entity.Sentiment

@Dao
interface FeedbackDao {

    @Insert
    suspend fun insert(feedback: Feedback): Long

    @Query("SELECT * FROM feedback WHERE id = :id")
    suspend fun get(id: Long): Feedback?

    /** FR-FB-03：寫回 AI 結果（失敗時只改 ai_status） */
    @Query("UPDATE feedback SET ai_category = :category, ai_sentiment = :sentiment, ai_summary = :summary, ai_status = :status WHERE id = :id")
    suspend fun updateAi(id: Long, category: FeedbackCategory?, sentiment: Sentiment?, summary: String?, status: AiStatus)

    @Query("SELECT * FROM feedback WHERE ai_status = 'PENDING' ORDER BY created_at")
    suspend fun pendingAi(): List<Feedback>

    @Query("SELECT * FROM feedback WHERE synced = 0 ORDER BY created_at LIMIT :limit")
    suspend fun unsynced(limit: Int): List<Feedback>

    @Query("UPDATE feedback SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)
}
