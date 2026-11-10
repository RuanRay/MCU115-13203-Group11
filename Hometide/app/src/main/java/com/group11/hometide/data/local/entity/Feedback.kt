package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.10 */
@Entity(tableName = "feedback")
data class Feedback(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String?,
    @ColumnInfo(name = "device_id") val deviceId: String,
    val rating: Int, // 1–5
    val category: FeedbackCategory,
    val content: String, // ≤ 500 字
    @ColumnInfo(name = "attach_usage") val attachUsage: Boolean,
    @ColumnInfo(name = "ai_category") val aiCategory: FeedbackCategory? = null,
    @ColumnInfo(name = "ai_sentiment") val aiSentiment: Sentiment? = null,
    @ColumnInfo(name = "ai_summary") val aiSummary: String? = null, // ≤ 30 字
    @ColumnInfo(name = "ai_status") val aiStatus: AiStatus,
    @ColumnInfo(name = "app_version") val appVersion: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val synced: Boolean = false,
)
