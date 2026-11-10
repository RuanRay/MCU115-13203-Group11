package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache

/** 查詢條件（timetable_cache 的複合索引） */
data class TimetableQuery(
    val operator: Operator,
    val originId: String,
    val destId: String,
    val travelDate: String, // yyyy-MM-dd
)

@Dao
interface TimetableDao {

    /** 同條件先刪後寫（7.2） */
    @Transaction
    suspend fun replace(query: TimetableQuery, trains: List<TimetableCache>) {
        delete(query.operator, query.originId, query.destId, query.travelDate)
        insertAll(trains)
    }

    suspend fun get(query: TimetableQuery): List<TimetableCache> =
        get(query.operator, query.originId, query.destId, query.travelDate)

    @Query(
        """SELECT * FROM timetable_cache
           WHERE operator = :operator AND origin_id = :originId AND dest_id = :destId AND travel_date = :travelDate
           ORDER BY depart_time""",
    )
    suspend fun get(operator: Operator, originId: String, destId: String, travelDate: String): List<TimetableCache>

    /** 清理快取（7.1.4：超過 3 天刪除），回傳刪除筆數 */
    @Query("DELETE FROM timetable_cache WHERE fetched_at < :time")
    suspend fun deleteOlderThan(time: Long): Int

    @Query(
        """DELETE FROM timetable_cache
           WHERE operator = :operator AND origin_id = :originId AND dest_id = :destId AND travel_date = :travelDate""",
    )
    suspend fun delete(operator: Operator, originId: String, destId: String, travelDate: String)

    @Insert
    suspend fun insertAll(trains: List<TimetableCache>)
}
