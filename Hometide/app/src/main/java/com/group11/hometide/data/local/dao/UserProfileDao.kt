package com.group11.hometide.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.group11.hometide.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Upsert
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    suspend fun get(uid: String): UserProfile?

    @Query("SELECT * FROM user_profile WHERE uid = :uid")
    fun observe(uid: String): Flow<UserProfile?>
}
