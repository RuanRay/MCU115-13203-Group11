package com.group11.hometide.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.FavoriteRoute
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.PageVisit
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.local.entity.TripReminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DAO in-memory 測試（12 章：快取替換、收藏唯一、未關閉 session 修補） */
@RunWith(AndroidJUnit4::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    // ---- StationDao ----

    private fun station(id: String, name: String, op: Operator = Operator.TRA, sort: Int? = null) =
        Station(op, id, name, null, sort, updatedAt = 100)

    @Test
    fun searchCursor_matchesSubstringWithinOperator() = runBlocking {
        val dao = db.stationDao()
        dao.upsertAll(listOf(station("4220", "臺南", sort = 2), station("4210", "南科", sort = 1), station("1000", "臺北")))
        dao.upsertAll(listOf(station("1070", "臺南", Operator.THSR)))

        dao.searchCursor(Operator.TRA, "南").use { c ->
            assertEquals(2, c.count)
            val cols = listOf("_id", "station_id", "name_zh", "name_en", "operator")
            cols.forEach { assertTrue("missing $it", c.getColumnIndex(it) >= 0) }
            c.moveToFirst()
            assertEquals("南科", c.getString(c.getColumnIndexOrThrow("name_zh"))) // 依 sort_key
        }
    }

    @Test
    fun searchCursor_wildcardsAndQuotesAreLiteral() = runBlocking {
        val dao = db.stationDao()
        dao.upsertAll(listOf(station("1", "臺南"), station("2", "a%b")))
        dao.searchCursor(Operator.TRA, "%").use { assertEquals(1, it.count) }
        dao.searchCursor(Operator.TRA, "_").use { assertEquals(0, it.count) }
        dao.searchCursor(Operator.TRA, "' OR 1=1 --").use { assertEquals(0, it.count) }
    }

    @Test
    fun searchCursor_englishPrefixFirstAndLimit20() = runBlocking {
        val dao = db.stationDao()
        dao.upsertAll(
            listOf(
                Station(Operator.TRA, "1", "新臺南", "New Tainan", 1, 100),
                Station(Operator.TRA, "2", "臺南", "Tainan", 9, 100),
            ) + (10..40).map { Station(Operator.TRA, "$it", "南站$it", null, it, 100) },
        )
        fun names(k: String) = dao.searchCursor(Operator.TRA, k).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(c.getColumnIndexOrThrow("name_zh"))) }
        }
        // 前綴相符（臺南）排在只是包含（新臺南）前面，即使 sort_key 較大
        assertEquals(listOf("臺南", "新臺南"), names("臺南"))
        // 英文不分大小寫
        assertEquals(listOf("臺南", "新臺南"), names("tainan"))
        // 最多 20 筆
        assertEquals(20, names("南").size)
    }

    @Test
    fun upsertAll_replacesAndLastUpdated() = runBlocking {
        val dao = db.stationDao()
        assertNull(dao.lastUpdated(Operator.TRA))
        dao.upsertAll(listOf(station("1", "台南")))
        dao.upsertAll(listOf(station("1", "臺南").copy(updatedAt = 200)))
        assertEquals(200L, dao.lastUpdated(Operator.TRA))
        dao.searchCursor(Operator.TRA, "").use { assertEquals(1, it.count) }
    }

    // ---- TimetableDao ----

    private val q = TimetableQuery(Operator.TRA, "1000", "4220", "2026-11-07")

    private fun train(no: String, depart: String, query: TimetableQuery = q, fetched: Long = 100) =
        TimetableCache(
            operator = query.operator, originId = query.originId, destId = query.destId, travelDate = query.travelDate,
            trainNo = no, trainTypeCode = null, trainTypeName = null, tripLine = null,
            departTime = depart, arriveTime = "23:59", durationMin = 60,
            reservedSeat = true, bikeAllowed = false, fetchedAt = fetched,
        )

    @Test
    fun replace_deletesSameQueryOnly() = runBlocking {
        val dao = db.timetableDao()
        val other = q.copy(travelDate = "2026-11-08")
        dao.replace(q, listOf(train("1", "08:00"), train("2", "07:00")))
        dao.replace(other, listOf(train("9", "09:00", other)))
        dao.replace(q, listOf(train("3", "10:00")))

        assertEquals(listOf("3"), dao.get(q).map { it.trainNo })
        assertEquals(listOf("9"), dao.get(other).map { it.trainNo })
    }

    @Test
    fun get_ordersByDepartTime_andDeleteOlderThan() = runBlocking {
        val dao = db.timetableDao()
        dao.replace(q, listOf(train("1", "08:00", fetched = 50), train("2", "07:00", fetched = 150)))
        assertEquals(listOf("2", "1"), dao.get(q).map { it.trainNo })
        assertEquals(1, dao.deleteOlderThan(100))
        assertEquals(listOf("2"), dao.get(q).map { it.trainNo })
    }

    // ---- FavoriteDao ----

    private fun fav(origin: String, dest: String, uid: String = "u1") =
        FavoriteRoute(uid = uid, operator = Operator.TRA, originId = origin, destId = dest, label = "x", sortOrder = 0, createdAt = 1)

    @Test
    fun toggle_addsThenRemoves_andAppendsOrder() = runBlocking {
        val dao = db.favoriteDao()
        assertTrue(dao.toggle(fav("A", "B")))
        assertTrue(dao.toggle(fav("A", "C")))
        assertTrue(dao.toggle(fav("A", "B", uid = "u2")))
        assertEquals(listOf(0, 1), dao.observe("u1").first().map { it.sortOrder })

        assertFalse(dao.toggle(fav("A", "B")))
        assertEquals(listOf("C"), dao.observe("u1").first().map { it.destId })
        assertEquals(1, dao.observe("u2").first().size)
    }

    @Test(expected = SQLiteConstraintException::class)
    fun favorite_uniquePerUserRoute() = runBlocking<Unit> {
        db.favoriteDao().insert(fav("A", "B"))
        db.favoriteDao().insert(fav("A", "B"))
    }

    @Test
    fun updateOrder_followsIdList() = runBlocking {
        val dao = db.favoriteDao()
        dao.toggle(fav("A", "B")); dao.toggle(fav("A", "C")); dao.toggle(fav("A", "D"))
        val ids = dao.observe("u1").first().map { it.id }
        dao.updateOrder(ids.reversed())
        assertEquals(listOf("D", "C", "B"), dao.observe("u1").first().map { it.destId })
    }

    // ---- ReminderDao ----

    private fun reminder(departAt: Long, triggerAt: Long, status: ReminderStatus = ReminderStatus.SCHEDULED) =
        TripReminder(
            uid = "u1", operator = Operator.TRA, trainNo = "1", trainTypeName = "自強",
            originName = "臺北", destName = "臺南", departAt = departAt, remindBeforeMin = 15,
            triggerAt = triggerAt, status = status,
        )

    @Test
    fun pendingForBoot_scheduledAndNotDeparted() = runBlocking {
        val dao = db.reminderDao()
        val now = 1_000L
        val future = dao.insert(reminder(departAt = 2_000, triggerAt = 1_500))
        val triggerPassed = dao.insert(reminder(departAt = 1_200, triggerAt = 900))
        dao.insert(reminder(departAt = 800, triggerAt = 500)) // 已發車
        dao.insert(reminder(departAt = 3_000, triggerAt = 2_500, status = ReminderStatus.CANCELLED))

        assertEquals(listOf(triggerPassed, future), dao.pendingForBoot(now).map { it.id })

        dao.updateStatus(future, ReminderStatus.FIRED)
        assertEquals(listOf(triggerPassed), dao.pendingForBoot(now).map { it.id })
    }

    // ---- UsageDao ----

    @Test
    fun fixUnclosed_usesLastVisitOrStartAt() = runBlocking {
        val dao = db.usageDao()
        val withVisits = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 1_000))
        dao.insertVisit(PageVisit(sessionId = withVisits, uid = null, pageName = "A", enterAt = 1_000, leaveAt = 1_400, durationMs = 400))
        dao.insertVisit(PageVisit(sessionId = withVisits, uid = null, pageName = "B", enterAt = 1_400, leaveAt = 1_900, durationMs = 500))
        val noVisits = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 5_000))
        val closed = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 7_000))
        dao.closeSession(closed, 7_300)

        assertEquals(2, dao.fixUnclosed())

        val rows = db.query("SELECT id, end_at, duration_ms FROM app_session ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getLong(0), c.getLong(1), c.getLong(2))) }
        }
        assertEquals(
            listOf(Triple(withVisits, 1_900L, 900L), Triple(noVisits, 5_000L, 0L), Triple(closed, 7_300L, 300L)),
            rows,
        )
    }

    @Test(expected = SQLiteConstraintException::class)
    fun pageVisit_requiresExistingSession() = runBlocking<Unit> {
        db.usageDao().insertVisit(PageVisit(sessionId = 999, uid = null, pageName = "A", enterAt = 0, leaveAt = 1, durationMs = 1))
    }
}
