package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.Operator

/** 7.1.5 fare_cache：依起訖站快取 7 天 */
@Dao
interface FareDao {

    @Query("SELECT * FROM fare_cache WHERE operator = :operator AND origin_id = :originId AND dest_id = :destId")
    suspend fun get(operator: Operator, originId: String, destId: String): List<FareCache>

    /** 同起訖站先刪後寫 */
    @Transaction
    suspend fun replace(operator: Operator, originId: String, destId: String, fares: List<FareCache>) {
        delete(operator, originId, destId)
        insertAll(fares)
    }

    @Query("DELETE FROM fare_cache WHERE operator = :operator AND origin_id = :originId AND dest_id = :destId")
    suspend fun delete(operator: Operator, originId: String, destId: String)

    @Insert
    suspend fun insertAll(fares: List<FareCache>)
}
