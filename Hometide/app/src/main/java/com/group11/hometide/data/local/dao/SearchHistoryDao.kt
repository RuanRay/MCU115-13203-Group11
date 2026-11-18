package com.group11.hometide.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.SearchHistory
import kotlinx.coroutines.flow.Flow

/** 最近查詢一列（FR-SRCH-07）：站名由 station 表帶出，查不到時用代碼 */
data class RecentSearch(
    val operator: Operator,
    @ColumnInfo(name = "origin_id") val originId: String,
    @ColumnInfo(name = "dest_id") val destId: String,
    @ColumnInfo(name = "origin_name") val originName: String,
    @ColumnInfo(name = "dest_name") val destName: String,
    @ColumnInfo(name = "travel_date") val travelDate: String,
    @ColumnInfo(name = "passengers_json") val passengersJson: String? = null, // 重新查詢時帶回當時的乘客（FR-SRCH-08）
)

@Dao
interface SearchHistoryDao {

    @Insert
    suspend fun insert(history: SearchHistory)

    /** 依「運具＋起訖站」去重，每條取最近一次查詢，最新的在前 */
    @Query(
        """SELECT h.operator, h.origin_id, h.dest_id, h.travel_date, h.passengers_json,
                  COALESCE(o.name_zh, h.origin_id) AS origin_name, COALESCE(d.name_zh, h.dest_id) AS dest_name
           FROM search_history h
           JOIN (SELECT operator, origin_id, dest_id, MAX(searched_at) AS last_at FROM search_history
                 GROUP BY operator, origin_id, dest_id) g
             ON h.operator = g.operator AND h.origin_id = g.origin_id AND h.dest_id = g.dest_id AND h.searched_at = g.last_at
           LEFT JOIN station o ON o.operator = h.operator AND o.station_id = h.origin_id
           LEFT JOIN station d ON d.operator = h.operator AND d.station_id = h.dest_id
           GROUP BY h.operator, h.origin_id, h.dest_id
           ORDER BY g.last_at DESC
           LIMIT :limit""",
    )
    fun recent(limit: Int): Flow<List<RecentSearch>>

    /** 長按刪除：刪掉這條路線的所有紀錄，清單才不會再出現 */
    @Query("DELETE FROM search_history WHERE operator = :operator AND origin_id = :originId AND dest_id = :destId")
    suspend fun deleteRoute(operator: Operator, originId: String, destId: String)
}
