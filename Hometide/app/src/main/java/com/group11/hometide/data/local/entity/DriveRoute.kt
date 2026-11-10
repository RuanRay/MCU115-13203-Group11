package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 7.1.12 */
@Entity(
    tableName = "drive_route",
    indices = [Index("uid", "freeway_id", "direction", "entry_ic", "exit_ic", unique = true)],
)
data class DriveRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    @ColumnInfo(name = "freeway_id") val freewayId: String,
    val direction: String, // 例：S（南下）
    @ColumnInfo(name = "entry_ic") val entryIc: String,
    @ColumnInfo(name = "exit_ic") val exitIc: String,
    val label: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
