package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** 7.1.5（CR-01 新結構，任務 4-3 先只寫入 ADULT；其他票種於 4-5） */
@Entity(
    tableName = "fare_cache",
    primaryKeys = ["operator", "origin_id", "dest_id", "train_type_code", "fare_class"],
)
data class FareCache(
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    @ColumnInfo(name = "train_type_code") val trainTypeCode: String, // BUS 為路線號
    @ColumnInfo(name = "fare_class") val fareClass: FareClass,
    val price: Int,
    @ColumnInfo(name = "is_approx", defaultValue = "0") val isApprox: Boolean = false, // 優惠價或分段吸附，顯示「約」
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
