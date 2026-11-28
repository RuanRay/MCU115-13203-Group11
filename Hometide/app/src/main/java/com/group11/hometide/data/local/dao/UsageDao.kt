package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    /** FR-USG-04：開啟次數（[uid] 為 null 時統計訪客的紀錄；IS 可比對 NULL） */
    @Query("SELECT COUNT(*) FROM app_session WHERE start_at >= :from AND uid IS :uid")
    suspend fun sessionCount(uid: String?, from: Long): Int

    /** FR-USG-04：已結束 session 的總使用時間（使用中的這次由 UsageTracker 另外加上） */
    @Query("SELECT COALESCE(SUM(duration_ms), 0) FROM app_session WHERE start_at >= :from AND uid IS :uid")
    suspend fun totalDuration(uid: String?, from: Long): Long

    /** FR-USG-04：各頁停留時間前 [limit] 名 */
    @Query(
        """SELECT page_name AS pageName, SUM(duration_ms) AS durationMs FROM page_visit
           WHERE enter_at >= :from AND uid IS :uid GROUP BY page_name ORDER BY durationMs DESC LIMIT :limit""",
    )
    suspend fun topPages(uid: String?, from: Long, limit: Int): List<PageTotal>

    /** FR-SYNC-02：已結束、尚未上傳的 session（使用中的那筆不傳，實作者確認） */
    @Query("SELECT * FROM app_session WHERE synced = 0 AND end_at IS NOT NULL ORDER BY start_at LIMIT :limit")
    suspend fun unsyncedSessions(limit: Int): List<AppSession>

    @Query("SELECT * FROM page_visit WHERE session_id IN (:sessionIds) ORDER BY enter_at")
    suspend fun visitsOf(sessionIds: List<Long>): List<PageVisit>

    /** session 與其頁面一起標記已上傳 */
    @Transaction
    suspend fun markSynced(sessionIds: List<Long>) {
        markSessionsSynced(sessionIds)
        markVisitsSynced(sessionIds)
    }

    @Query("UPDATE app_session SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSessionsSynced(ids: List<Long>)

    @Query("UPDATE page_visit SET synced = 1 WHERE session_id IN (:sessionIds)")
    suspend fun markVisitsSynced(sessionIds: List<Long>)
}

data class PageTotal(val pageName: String, val durationMs: Long)
