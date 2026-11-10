package com.group11.hometide.data.local.dao

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station

@Dao
interface StationDao {

    /**
     * 供 StationProvider（10.3）使用：回傳 `_id`、station_id、name_zh、name_en、operator。
     * keyword 須先經 StationNameNormalizer（台→臺）；%、_ 會被跳脫，只做字面比對。
     */
    fun searchCursor(operator: Operator, keyword: String): Cursor =
        searchCursorByPattern(operator, "%" + escapeLike(keyword) + "%")

    @Query(
        """SELECT rowid AS _id, station_id, name_zh, name_en, operator FROM station
           WHERE operator = :operator AND name_zh LIKE :pattern ESCAPE '\'
           ORDER BY sort_key, name_zh""",
    )
    fun searchCursorByPattern(operator: Operator, pattern: String): Cursor

    @Upsert
    suspend fun upsertAll(stations: List<Station>)

    /** 該運具最後下載時間；沒有資料時為 null */
    @Query("SELECT MAX(updated_at) FROM station WHERE operator = :operator")
    suspend fun lastUpdated(operator: Operator): Long?

    private fun escapeLike(s: String) =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
