package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.1 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val uid: String,
    val email: String,
    val nickname: String, // 1–20 字
    @ColumnInfo(name = "default_operator") val defaultOperator: Operator, // TRA／THSR／BUS（實作者決定，規格 7.1 只列 TRA／THSR）
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
