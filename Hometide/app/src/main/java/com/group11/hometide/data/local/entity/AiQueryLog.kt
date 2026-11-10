package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.11 */
@Entity(tableName = "ai_query_log")
data class AiQueryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String?,
    @ColumnInfo(name = "input_text") val inputText: String,
    @ColumnInfo(name = "parsed_json") val parsedJson: String?,
    val result: AiQueryResult,
    @ColumnInfo(name = "user_edited", defaultValue = "0") val userEdited: Boolean = false,
    @ColumnInfo(name = "latency_ms") val latencyMs: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
