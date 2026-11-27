package com.group11.hometide.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.dao.PageTotal
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.PageVisit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FR-USG-04：區間、帳號（訪客為 NULL）、前 5 名 */
@RunWith(AndroidJUnit4::class)
class UsageStatsDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun stats_filterByRangeAndUid() = runBlocking {
        val dao = db.usageDao()
        val old = dao.openSession(AppSession(uid = "u1", deviceId = "d", startAt = 100))
        dao.closeSession(old, 700)
        val s = dao.openSession(AppSession(uid = "u1", deviceId = "d", startAt = 1_000))
        dao.closeSession(s, 5_000)
        val guest = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 2_000))
        dao.closeSession(guest, 2_500)
        fun visit(sid: Long, uid: String?, page: String, enter: Long, ms: Long) =
            PageVisit(sessionId = sid, uid = uid, pageName = page, enterAt = enter, leaveAt = enter + ms, durationMs = ms)
        dao.insertVisit(visit(old, "u1", "SearchFragment", 100, 500))
        dao.insertVisit(visit(s, "u1", "SearchFragment", 1_000, 1_000))
        dao.insertVisit(visit(s, "u1", "ResultActivity", 2_000, 2_000))
        dao.insertVisit(visit(s, "u1", "SearchFragment", 4_000, 500))
        dao.insertVisit(visit(guest, null, "NextTrainFragment", 2_000, 400))

        assertEquals(2, dao.sessionCount("u1", 0))
        assertEquals(1, dao.sessionCount("u1", 1_000))
        assertEquals(1, dao.sessionCount(null, 0))
        assertEquals(4_000L, dao.totalDuration("u1", 1_000))
        assertEquals(0L, dao.totalDuration("nobody", 0))
        assertEquals(listOf(PageTotal("ResultActivity", 2_000), PageTotal("SearchFragment", 1_500)), dao.topPages("u1", 1_000, 5))
        assertEquals(listOf(PageTotal("NextTrainFragment", 400)), dao.topPages(null, 0, 5))
        assertEquals(1, dao.topPages("u1", 0, 1).size)
    }
}
