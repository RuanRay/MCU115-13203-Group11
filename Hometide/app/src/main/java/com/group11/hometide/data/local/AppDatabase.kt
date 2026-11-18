package com.group11.hometide.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.group11.hometide.data.local.dao.DriveRouteDao
import com.group11.hometide.data.local.dao.FareDao
import com.group11.hometide.data.local.dao.FavoriteDao
import com.group11.hometide.data.local.dao.FeedbackDao
import com.group11.hometide.data.local.dao.HighwayCacheDao
import com.group11.hometide.data.local.dao.ReminderDao
import com.group11.hometide.data.local.dao.SearchHistoryDao
import com.group11.hometide.data.local.dao.StationDao
import com.group11.hometide.data.local.dao.TimetableDao
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.dao.UserProfileDao
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

/** 13 張表（7.1）。ai_query_log 的 DAO 於使用到的任務再加。 */
@Database(
    entities = [
        UserProfile::class, Station::class, SearchHistory::class, TimetableCache::class,
        FareCache::class, FavoriteRoute::class, TripReminder::class, AppSession::class,
        PageVisit::class, Feedback::class, AiQueryLog::class, DriveRoute::class, HighwayCache::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun stationDao(): StationDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun timetableDao(): TimetableDao
    abstract fun fareDao(): FareDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun usageDao(): UsageDao
    abstract fun feedbackDao(): FeedbackDao
    abstract fun driveRouteDao(): DriveRouteDao
    abstract fun highwayCacheDao(): HighwayCacheDao

    companion object {
        // 規格 7.1 寫 homebound.db，2026-11-10 實作者決定改為 hometide.db
        private const val NAME = "hometide.db"

        /** v2（任務 4-1）：station 加 city；把 updated_at 歸零，讓啟動時重新下載以補上縣市 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE station ADD COLUMN city TEXT")
                db.execSQL("UPDATE station SET updated_at = 0")
            }
        }

        /** v3（任務 4-3）：fare_cache 改為 CR-01 結構（fare_class、price、is_approx）；快取可直接重建 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS fare_cache")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `fare_cache` (`operator` TEXT NOT NULL, `origin_id` TEXT NOT NULL, " +
                        "`dest_id` TEXT NOT NULL, `train_type_code` TEXT NOT NULL, `fare_class` TEXT NOT NULL, " +
                        "`price` INTEGER NOT NULL, `is_approx` INTEGER NOT NULL DEFAULT 0, `fetched_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`operator`, `origin_id`, `dest_id`, `train_type_code`, `fare_class`))",
                )
            }
        }

        /**
         * v4（任務 4-5）：search_history 加 passengers_json（NULL 視為全票 1）；
         * 清空 fare_cache：v3 期間只寫入全票，7 天內不會重抓其他票種
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE search_history ADD COLUMN passengers_json TEXT")
                db.execSQL("DELETE FROM fare_cache")
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
