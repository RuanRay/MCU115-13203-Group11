package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.TripReminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    /** 回傳 id，兼作 PendingIntent requestCode */
    @Insert
    suspend fun insert(reminder: TripReminder): Long

    @Query("SELECT * FROM trip_reminder WHERE uid = :uid AND status = 'SCHEDULED' ORDER BY trigger_at")
    fun scheduled(uid: String): Flow<List<TripReminder>>

    /** FR-FAV-05：提醒分頁與 P4 存根（已預約、已提醒），依發車時間排序；已取消的不列 */
    @Query("SELECT * FROM trip_reminder WHERE uid = :uid AND status != 'CANCELLED' ORDER BY depart_at")
    fun active(uid: String): Flow<List<TripReminder>>

    /**
     * 開機重排（FR-RMD-04）：所有 SCHEDULED 且發車時間未過的提醒。
     * 由呼叫端依 trigger_at 決定重新排程（未過）或立即通知（已過）。
     */
    @Query("SELECT * FROM trip_reminder WHERE status = 'SCHEDULED' AND depart_at > :now ORDER BY trigger_at")
    suspend fun pendingForBoot(now: Long): List<TripReminder>

    @Query("UPDATE trip_reminder SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReminderStatus)

    @Query("SELECT * FROM trip_reminder WHERE id = :id")
    suspend fun get(id: Long): TripReminder?

    /** FR-RMD-01：同一車次同一天（以發車時間落在 [dayStart, dayEnd)）且未取消的提醒 */
    @Query(
        """SELECT * FROM trip_reminder WHERE uid = :uid AND operator = :operator AND train_no = :trainNo
           AND depart_at >= :dayStart AND depart_at < :dayEnd AND status != 'CANCELLED' LIMIT 1""",
    )
    suspend fun findSameTrip(uid: String, operator: Operator, trainNo: String, dayStart: Long, dayEnd: Long): TripReminder?

    /** 更新原提醒（整列，含站名與發車時間） */
    @Update
    suspend fun update(reminder: TripReminder)

    /** FR-FAV-05：發車超過 7 天的提醒自動移除（含已取消） */
    @Query("DELETE FROM trip_reminder WHERE depart_at < :before")
    suspend fun deleteDepartedBefore(before: Long): Int
}
