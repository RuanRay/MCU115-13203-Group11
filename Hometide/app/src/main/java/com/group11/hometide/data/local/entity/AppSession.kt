package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.8 */
@Entity(tableName = "app_session")
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long? = null,
    @ColumnInfo(name = "duration_ms", defaultValue = "0") val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)
