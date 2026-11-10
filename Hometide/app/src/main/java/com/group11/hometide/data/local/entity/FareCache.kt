package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** 7.1.5 */
@Entity(
    tableName = "fare_cache",
    primaryKeys = ["operator", "origin_id", "dest_id", "train_type_code"],
)
data class FareCache(
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    @ColumnInfo(name = "train_type_code") val trainTypeCode: String,
    @ColumnInfo(name = "adult_price") val adultPrice: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
