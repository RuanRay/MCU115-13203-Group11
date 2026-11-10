package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.PageVisit

@Dao
interface UsageDao {

    /** FR-USG-01 onStart；回傳 session id */
    @Insert
    suspend fun openSession(session: AppSession): Long

    /** FR-USG-01 onStop */
    @Query("UPDATE app_session SET end_at = :endAt, duration_ms = :endAt - start_at WHERE id = :id")
    suspend fun closeSession(id: Long, endAt: Long)

    /** FR-USG-02 */
    @Insert
    suspend fun insertVisit(visit: PageVisit): Long

    /**
     * FR-USG-03：end_at 為空的 session，以最後一筆 page_visit 的 leave_at 補上；
     * 沒有 page_visit 時以 start_at 補上（duration 為 0）。須在開新 session 前呼叫。回傳修補筆數。
     */
    @Query(
        """UPDATE app_session SET
             end_at = COALESCE((SELECT MAX(leave_at) FROM page_visit WHERE session_id = app_session.id), start_at),
             duration_ms = COALESCE((SELECT MAX(leave_at) FROM page_visit WHERE session_id = app_session.id), start_at) - start_at
           WHERE end_at IS NULL""",
    )
    suspend fun fixUnclosed(): Int

    // TODO(第 7 週，FR-USG-04)：stats(uid, from, to)，回傳格式依個人統計規格決定
}
