package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/** 7.1.2；name_zh 為正規化後站名（臺） */
@Entity(
    tableName = "station",
    primaryKeys = ["operator", "station_id"],
    indices = [Index("name_zh")],
)
data class Station(
    val operator: Operator,
    @ColumnInfo(name = "station_id") val stationId: String,
    @ColumnInfo(name = "name_zh") val nameZh: String,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "sort_key") val sortKey: Int?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
