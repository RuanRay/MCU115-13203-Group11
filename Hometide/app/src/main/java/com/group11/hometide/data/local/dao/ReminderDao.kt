package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    /**
     * 開機重排（FR-RMD-04）：所有 SCHEDULED 且發車時間未過的提醒。
     * 由呼叫端依 trigger_at 決定重新排程（未過）或立即通知（已過）。
     */
    @Query("SELECT * FROM trip_reminder WHERE status = 'SCHEDULED' AND depart_at > :now ORDER BY trigger_at")
    suspend fun pendingForBoot(now: Long): List<TripReminder>

    @Query("UPDATE trip_reminder SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: ReminderStatus)
}
