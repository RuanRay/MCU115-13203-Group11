package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.group11.hometide.data.local.entity.HighwayCache

@Dao
interface HighwayCacheDao {

    @Query("SELECT * FROM highway_cache WHERE route_key = :routeKey")
    suspend fun get(routeKey: String): HighwayCache?

    suspend fun put(routeKey: String, json: String, updatedAt: Long) =
        upsert(HighwayCache(routeKey, json, updatedAt))

    @Upsert
    suspend fun upsert(cache: HighwayCache)
}
