package com.group11.hometide.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 7.1.3 */
@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String?,
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    @ColumnInfo(name = "travel_date") val travelDate: String, // yyyy-MM-dd
    val source: SearchSource,
    @ColumnInfo(name = "searched_at") val searchedAt: Long,
    @ColumnInfo(name = "passengers_json") val passengersJson: String? = null, // CR-01，例 {"ADULT":2,"CHILD":1}；NULL 視為全票 1
)
