package com.group11.hometide.usage

import androidx.lifecycle.LifecycleOwner
import com.group11.hometide.data.local.dao.PageTotal
import com.group11.hometide.data.local.dao.UsageDao
import com.group11.hometide.data.local.entity.AppSession
import com.group11.hometide.data.local.entity.PageVisit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-USG-01～03；TC-21（單元部分） */
class UsageTrackerTest {

    private var now = 10_000L
    private var uid: String? = "u1"

    private class FakeDao : UsageDao {
        val sessions = mutableListOf<AppSession>()
        val visits = mutableListOf<PageVisit>()
        var fixCalls = 0
        override suspend fun openSession(session: AppSession): Long {
            sessions += session.copy(id = sessions.size + 1L)
            return sessions.size.toLong()
        }
        override suspend fun closeSession(id: Long, endAt: Long) {
            val s = sessions[id.toInt() - 1]
            sessions[id.toInt() - 1] = s.copy(endAt = endAt, durationMs = endAt - s.startAt)
        }
        override suspend fun insertVisit(visit: PageVisit): Long {
            visits += visit
            return visits.size.toLong()
        }
        override suspend fun fixUnclosed(): Int = 0.also { fixCalls++ }
        override suspend fun sessionCount(uid: String?, from: Long) = 0
        override suspend fun totalDuration(uid: String?, from: Long) = 0L
        override suspend fun topPages(uid: String?, from: Long, limit: Int) = emptyList<PageTotal>()
    }

    private val dao = FakeDao()
    private val owner = object : LifecycleOwner {
        override val lifecycle get() = throw UnsupportedOperationException()
    }
    private val tracker = UsageTracker(dao, { "device" }, { uid }, CoroutineScope(SupervisorJob()), Dispatchers.Unconfined) { now }

    @Test
    fun session_openFixesFirst_thenCloses() {
        tracker.onStart(owner)
        assertEquals(1, dao.fixCalls)
        now += 5_000
        tracker.onStop(owner)
        assertEquals(AppSession(1, "u1", "device", 10_000, 15_000, 5_000), dao.sessions.single())
    }

    /** TC-21：依序瀏覽三頁 → 3 筆 page_visit；少於 300 ms 不記 */
    @Test
    fun pages_recorded_ignoresShortVisits() {
        tracker.onStart(owner)
        for ((page, ms) in listOf("SearchFragment" to 2_000L, "ResultActivity" to 299L, "ResultActivity" to 300L, "MyRoutesFragment" to 1_000L)) {
            tracker.enterPage(page)
            now += ms
            tracker.leavePage()
        }
        assertEquals(listOf("SearchFragment" to 2_000L, "ResultActivity" to 300L, "MyRoutesFragment" to 1_000L), dao.visits.map { it.pageName to it.durationMs })
        assertEquals(setOf(1L), dao.visits.map { it.sessionId }.toSet())
    }

    @Test
    fun liveSession_onlyForSameUidAndRange() {
        tracker.onStart(owner)
        now += 60_000
        assertEquals(60_000L, tracker.liveSessionMs("u1", 0))
        assertEquals(0L, tracker.liveSessionMs(null, 0))
        assertEquals(0L, tracker.liveSessionMs("u1", 20_000))
        tracker.onStop(owner)
        assertEquals(0L, tracker.liveSessionMs("u1", 0))
    }
}
