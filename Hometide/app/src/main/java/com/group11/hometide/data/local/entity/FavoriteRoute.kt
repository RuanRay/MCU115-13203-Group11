package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 7.1.6 */
@Entity(
    tableName = "favorite_route",
    indices = [Index("uid", "operator", "origin_id", "dest_id", unique = true)],
)
data class FavoriteRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    val label: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
