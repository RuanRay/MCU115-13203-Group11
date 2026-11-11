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
     * FR-SRCH-02：比對中文與英文站名（英文不分大小寫），前綴相符排前面，最多 20 筆。
     * keyword 須先經 StationNameNormalizer（台→臺）；%、_ 會被跳脫，只做字面比對。
     */
    fun searchCursor(operator: Operator, keyword: String): Cursor {
        val k = escapeLike(keyword)
        return searchCursorByPattern(operator, "%$k%", "$k%")
    }

    @Query(
        """SELECT rowid AS _id, station_id, name_zh, name_en, operator FROM station
           WHERE operator = :operator
             AND (name_zh LIKE :contains ESCAPE '\' OR name_en LIKE :contains ESCAPE '\')
           ORDER BY CASE WHEN name_zh LIKE :prefix ESCAPE '\' OR name_en LIKE :prefix ESCAPE '\' THEN 0 ELSE 1 END,
                    sort_key, name_zh
           LIMIT 20""",
    )
    fun searchCursorByPattern(operator: Operator, contains: String, prefix: String): Cursor

    @Upsert
    suspend fun upsertAll(stations: List<Station>)

    /** 該運具最後下載時間；沒有資料時為 null */
    @Query("SELECT MAX(updated_at) FROM station WHERE operator = :operator")
    suspend fun lastUpdated(operator: Operator): Long?

    private fun escapeLike(s: String) =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
