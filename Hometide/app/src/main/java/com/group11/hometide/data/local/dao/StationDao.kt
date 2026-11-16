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
     * FR-BUS-01：輸入縣市名（例：臺南）時，該縣市的站排在站名相符者之後（任務 4-1，實作者確認）。
     * keyword 須先經 StationNameNormalizer（台→臺）；%、_ 會被跳脫，只做字面比對。
     */
    fun searchCursor(operator: Operator, keyword: String): Cursor {
        val k = escapeLike(keyword)
        return searchCursorByPattern(operator, "%$k%", "$k%")
    }

    @Query(
        """SELECT rowid AS _id, station_id, name_zh, name_en, operator FROM station
           WHERE operator = :operator
             AND (name_zh LIKE :contains ESCAPE '\' OR name_en LIKE :contains ESCAPE '\' OR city LIKE :prefix ESCAPE '\')
           ORDER BY CASE WHEN name_zh LIKE :prefix ESCAPE '\' OR name_en LIKE :prefix ESCAPE '\' THEN 0
                         WHEN name_zh LIKE :contains ESCAPE '\' OR name_en LIKE :contains ESCAPE '\' THEN 1
                         ELSE 2 END,
                    sort_key, name_zh
           LIMIT 20""",
    )
    fun searchCursorByPattern(operator: Operator, contains: String, prefix: String): Cursor

    /** P4 標題用（FR-RES-01） */
    @Query("SELECT name_zh FROM station WHERE operator = :operator AND station_id = :stationId")
    suspend fun nameOf(operator: Operator, stationId: String): String?

    /** 手動查詢驗證（FR-SRCH-01）：name 須先正規化 */
    @Query("SELECT * FROM station WHERE operator = :operator AND name_zh = :name ORDER BY sort_key LIMIT 1")
    suspend fun findByName(operator: Operator, name: String): Station?

    @Upsert
    suspend fun upsertAll(stations: List<Station>)

    /** 該運具最後下載時間；沒有資料時為 null */
    @Query("SELECT MAX(updated_at) FROM station WHERE operator = :operator")
    suspend fun lastUpdated(operator: Operator): Long?

    private fun escapeLike(s: String) =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
