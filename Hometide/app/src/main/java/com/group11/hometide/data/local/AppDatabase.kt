package com.group11.hometide.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.group11.hometide.data.local.dao.DriveRouteDao
import com.group11.hometide.data.local.dao.FavoriteDao
import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.dao.HighwayCacheDao
import com.group11.hometide.data.local.dao.ReminderDao
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.dao.TimetableDao
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.entity.AiQueryLog
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.DriveRoute
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.HighwayCache
import com.group11.hometide.data.local.entity.PageVisit
import com.group11.hometide.data.local.entity.SearchHistory
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.data.local.entity.UserProfile

/** 13 張表（7.1）。user_profile、search_history、fare_cache、ai_query_log 的 DAO 於使用到的任務再加。 */
@Database(
    entities = [
        UserProfile::class, Station::class, SearchHistory::class, TimetableCache::class,
        FareCache::class, FavoriteRoute::class, TripReminder::class, AppSession::class,
        PageVisit::class, Feedback::class, AiQueryLog::class, DriveRoute::class, HighwayCache::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun timetableDao(): TimetableDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun usageDao(): UsageDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun driveRouteDao(): DriveRouteDao
    abstract fun highwayCacheDao(): HighwayCacheDao

    companion object {
        // 規格 7.1 寫 homebound.db，2026-11-10 實作者決定改為 hometide.db
        private const val NAME = "hometide.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
