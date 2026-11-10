package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 7.1.9 */
@Entity(
    tableName = "page_visit",
    foreignKeys = [ForeignKey(entity = AppSession::class, parentColumns = ["id"], childColumns = ["session_id"])],
    indices = [Index("session_id")],
)
data class PageVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val uid: String?,
    @ColumnInfo(name = "page_name") val pageName: String,
    @ColumnInfo(name = "enter_at") val enterAt: Long,
    @ColumnInfo(name = "leave_at") val leaveAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)
