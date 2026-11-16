package com.group11.hometide.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.SearchHistory
import com.group11.hometide.data.local.entity.SearchSource
import com.group11.hometide.data.local.entity.Station
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 任務 4-1：最近查詢（FR-SRCH-07）、縣市建議（FR-BUS-01）、v1→v2 Migration */
@RunWith(AndroidJUnit4::class)
class SearchDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    private fun history(op: Operator, from: String, to: String, date: String, at: Long) =
        SearchHistory(0, null, op, from, to, date, SearchSource.MANUAL, at)

    @Test
    fun recent_distinctLatestFirst_withNames_limit5() = runBlocking {
        db.stationDao().upsertAll(listOf(Station(Operator.TRA, "4220", "臺南", null, 0, 1), Station(Operator.TRA, "1080", "桃園", null, 1, 1)))
        val dao = db.searchHistoryDao()
        dao.insert(history(Operator.TRA, "4220", "1080", "2026-11-08", 10))
        dao.insert(history(Operator.TRA, "4220", "1080", "2026-11-20", 30)) // 同路線較新：取這筆的乘車日期
        dao.insert(history(Operator.THSR, "1000", "1070", "2026-11-01", 20))
        for (i in 1..5) dao.insert(history(Operator.BUS, "A$i", "B", "2026-11-0$i", i.toLong()))

        val recent = dao.recent(5).first()
        assertEquals(5, recent.size)
        assertEquals("臺南" to "桃園", recent[0].originName to recent[0].destName)
        assertEquals("2026-11-20", recent[0].travelDate)
        assertEquals("1000", recent[1].originName) // station 表沒有時用代碼
        assertEquals(listOf("A5", "A4", "A3"), recent.drop(2).map { it.originId })
    }

    @Test
    fun deleteRoute_removesAllRecordsOfRoute() = runBlocking {
        val dao = db.searchHistoryDao()
        dao.insert(history(Operator.TRA, "4220", "1080", "2026-11-08", 10))
        dao.insert(history(Operator.TRA, "4220", "1080", "2026-11-09", 11))
        dao.insert(history(Operator.TRA, "1080", "4220", "2026-11-09", 12))
        dao.deleteRoute(Operator.TRA, "4220", "1080")
        assertEquals(listOf("1080"), dao.recent(5).first().map { it.originId })
    }

    /** FR-BUS-01：輸入縣市名列出該縣市站牌，排在站名相符者之後 */
    @Test
    fun searchCursor_cityMatchAfterNameMatch() = runBlocking {
        val dao = db.stationDao()
        dao.upsertAll(
            listOf(
                Station(Operator.BUS, "永康轉運站", "永康轉運站", null, 0, 1, "臺南市"),
                Station(Operator.BUS, "臺南轉運站", "臺南轉運站", null, 1, 1, "臺南市"),
                Station(Operator.BUS, "臺北轉運站", "臺北轉運站", null, 2, 1, "臺北市"),
            ),
        )
        dao.searchCursor(Operator.BUS, "臺南").use { c ->
            val names = generateSequence { if (c.moveToNext()) c.getString(c.getColumnIndexOrThrow("name_zh")) else null }.toList()
            assertEquals(listOf("臺南轉運站", "永康轉運站"), names)
        }
        assertEquals("臺南轉運站", dao.findByName(Operator.BUS, "臺南轉運站")?.stationId)
    }

    /** FR-SRCH-02：英文站名不分大小寫 */
    @Test
    fun searchCursor_matchesEnglishName() = runBlocking {
        db.stationDao().upsertAll(listOf(Station(Operator.TRA, "1210", "新竹", "Hsinchu", 0, 1)))
        db.stationDao().searchCursor(Operator.TRA, "hsinchu").use { assertEquals(1, it.count) }
        db.stationDao().searchCursor(Operator.TRA, "Hsin").use { assertEquals(1, it.count) }
    }

    @Test
    fun migration1to2_addsCityAndForcesRedownload() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.use { sql ->
            // v1 的 station 表（Room 1 版產生的 schema）
            sql.execSQL(
                "CREATE TABLE station (operator TEXT NOT NULL, station_id TEXT NOT NULL, name_zh TEXT NOT NULL, " +
                    "name_en TEXT, sort_key INTEGER, updated_at INTEGER NOT NULL, PRIMARY KEY(operator, station_id))",
            )
            sql.execSQL("INSERT INTO station VALUES ('TRA', '4220', '臺南', 'Tainan', 0, 999)")
            AppDatabase.MIGRATION_1_2.migrate(sql)
            sql.query("SELECT city, updated_at FROM station").use { c ->
                c.moveToFirst()
                assertEquals(true, c.isNull(0))
                assertEquals(0L, c.getLong(1))
            }
        }
    }
}
