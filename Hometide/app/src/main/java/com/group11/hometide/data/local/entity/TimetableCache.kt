package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 7.1.4；BUS 的 train_no 為「路線號-班次序」 */
@Entity(
    tableName = "timetable_cache",
    indices = [Index("operator", "origin_id", "dest_id", "travel_date")],
)
data class TimetableCache(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    @ColumnInfo(name = "travel_date") val travelDate: String, // yyyy-MM-dd
    @ColumnInfo(name = "train_no") val trainNo: String,
    @ColumnInfo(name = "train_type_code") val trainTypeCode: String?,
    @ColumnInfo(name = "train_type_name") val trainTypeName: String?,
    @ColumnInfo(name = "trip_line") val tripLine: Int?, // 台鐵 0 不經山海線、1 山線、2 海線
    @ColumnInfo(name = "depart_time") val departTime: String, // HH:mm
    @ColumnInfo(name = "arrive_time") val arriveTime: String,
    @ColumnInfo(name = "duration_min") val durationMin: Int,
    @ColumnInfo(name = "reserved_seat") val reservedSeat: Boolean,
    @ColumnInfo(name = "bike_allowed") val bikeAllowed: Boolean,
    @ColumnInfo(name = "arrive_estimated", defaultValue = "0") val arriveEstimated: Boolean = false,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
