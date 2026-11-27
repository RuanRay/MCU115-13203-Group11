package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.group11.hometide.data.local.entity.AiQueryLog

/** 7.1.11（FR-AI-05） */
@Dao
interface AiQueryLogDao {
    @Insert
    suspend fun insert(log: AiQueryLog): Long

    /** 確認卡按「修改」（9.5） */
    @Query("UPDATE ai_query_log SET user_edited = 1 WHERE id = :id")
    suspend fun markEdited(id: Long)
}
