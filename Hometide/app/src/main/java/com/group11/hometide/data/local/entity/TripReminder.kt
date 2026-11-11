package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.7；id 兼作 PendingIntent requestCode */
@Entity(tableName = "trip_reminder")
data class TripReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val operator: Operator,
    @ColumnInfo(name = "train_no") val trainNo: String,
    @ColumnInfo(name = "train_type_name") val trainTypeName: String,
    @ColumnInfo(name = "origin_name") val originName: String,
    @ColumnInfo(name = "dest_name") val destName: String,
    @ColumnInfo(name = "depart_at") val departAt: Long,
    @ColumnInfo(name = "remind_before_min") val remindBeforeMin: Int, // 15／30／60
    @ColumnInfo(name = "trigger_at") val triggerAt: Long, // depart_at − 提前分鐘
    val status: ReminderStatus,
)
