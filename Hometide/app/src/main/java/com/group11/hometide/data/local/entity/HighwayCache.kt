package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.13；route_key = freeway_id|direction|entry_ic|exit_ic */
@Entity(tableName = "highway_cache")
data class HighwayCache(
    @PrimaryKey @ColumnInfo(name = "route_key") val routeKey: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
