package com.group11.hometide.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.PageVisit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** FR-SYNC-02：只取已結束且未上傳的 session；session 與頁面一起標記 */
@RunWith(AndroidJUnit4::class)
class UsageSyncDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun unsyncedClosedSessions_andMarkSynced() = runBlocking {
        val dao = db.usageDao()
        val closed = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 1))
        dao.closeSession(closed, 100)
        val open = dao.openSession(AppSession(uid = null, deviceId = "d", startAt = 200))
        dao.insertVisit(PageVisit(sessionId = closed, uid = null, pageName = "A", enterAt = 1, leaveAt = 50, durationMs = 49))
        dao.insertVisit(PageVisit(sessionId = open, uid = null, pageName = "B", enterAt = 200, leaveAt = 900, durationMs = 700))

        assertEquals(listOf(closed), dao.unsyncedSessions(200).map { it.id })
        assertEquals(listOf("A"), dao.visitsOf(listOf(closed)).map { it.pageName })
        dao.markSynced(listOf(closed))
        assertEquals(emptyList<Long>(), dao.unsyncedSessions(200).map { it.id })
        val synced = db.query("SELECT page_name, synced FROM page_visit ORDER BY id", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to c.getInt(1)) }
        }
        assertEquals(listOf("A" to 1, "B" to 0), synced)
    }
}
