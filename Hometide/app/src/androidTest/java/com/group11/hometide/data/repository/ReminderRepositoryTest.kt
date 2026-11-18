package com.group11.hometide.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.reminder.ReminderScheduler
import com.group11.hometide.util.ReminderRules
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** ReminderRepository（FR-RMD-01～03、FR-FAV-05）：in-memory Room＋真的 AlarmManager（排程後立即取消，不會觸發） */
@RunWith(AndroidJUnit4::class)
class ReminderRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ReminderRepository
    private val tomorrow = LocalDate.now().plusDays(1).toString()
    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = ReminderRepository(db.reminderDao(), ReminderScheduler(ApplicationProvider.getApplicationContext()), now = { now })
    }

    @After
    fun tearDown() = runBlocking {
        db.reminderDao().active("u1").first().forEach { repo.cancel(it) }
        db.close()
    }

    private fun train(no: String, dep: String, date: String = tomorrow) = TimetableCache(
        operator = Operator.TRA, originId = "1015", destId = "4220", travelDate = date, trainNo = no,
        trainTypeCode = "3", trainTypeName = "自強", tripLine = 1, departTime = dep, arriveTime = "23:00",
        durationMin = 60, reservedSeat = true, bikeAllowed = false, fetchedAt = 0,
    )

    private suspend fun set(no: String, dep: String, min: Int) =
        repo.set("u1", train(no, dep), "自強", "桃園", "臺南", min)

    /** FR-RMD-01：同一車次同一天重複設定 → 更新原提醒（id 不變） */
    @Test
    fun set_sameTripUpdatesExisting() = runBlocking {
        val first = (set("139", "14:12", 15) as Result.Success).data
        val second = (set("139", "14:12", 60) as Result.Success).data
        assertEquals(first.id, second.id)
        val list = repo.observe("u1").first()
        assertEquals(1, list.size)
        assertEquals(60, list[0].remindBeforeMin)
        assertEquals(ReminderRules.triggerAt(first.departAt, 60), list[0].triggerAt)
        assertEquals(ReminderStatus.SCHEDULED, list[0].status)
        // 同車次但起訖站不同：更新成這次的站名（審查建議）
        repo.set("u1", train("139", "14:12"), "自強", "中壢", "臺南", 15)
        assertEquals(listOf(first.id to "中壢"), repo.observe("u1").first().map { it.id to it.originName })
    }

    /** 「發車 − 提前分鐘」已過 → 不建立（TC-18 情境：20 分鐘後的班次只能選 15 分） */
    @Test
    fun set_rejectsPassedTrigger() = runBlocking {
        val depart = ReminderRules.departAt(tomorrow, "14:12")
        val r20 = ReminderRepository(db.reminderDao(), ReminderScheduler(ApplicationProvider.getApplicationContext()), now = { depart - 20 * 60_000 })
        assertTrue(r20.set("u1", train("139", "14:12"), "自強", "桃園", "臺南", 30) is Result.Failure)
        assertTrue(r20.set("u1", train("139", "14:12"), "自強", "桃園", "臺南", 15) is Result.Success)
    }

    /** FR-RMD-03：觸發 → FIRED；已取消的不再觸發；FR-FAV-05 取消後不列出 */
    @Test
    fun fireAndCancel() = runBlocking {
        val a = (set("139", "14:12", 15) as Result.Success).data
        val b = (set("143", "14:30", 15) as Result.Success).data
        assertEquals(ReminderStatus.FIRED, repo.fire(a.id)?.let { db.reminderDao().get(it.id)?.status })
        assertNull(repo.fire(a.id)) // 已觸發過不再發通知
        repo.cancel(b)
        assertNull(repo.fire(b.id))
        assertEquals(listOf(a.id), repo.observe("u1").first().map { it.id })
    }

    /** FR-FAV-05：發車超過 7 天移除 */
    @Test
    fun purgeOld_removesAfterSevenDays() = runBlocking {
        val old = LocalDate.now().minusDays(8).toString()
        db.reminderDao().insert(
            com.group11.hometide.data.local.entity.TripReminder(
                0, "u1", Operator.TRA, "1", "自強", "桃園", "臺南",
                ReminderRules.departAt(old, "10:00"), 15, ReminderRules.departAt(old, "09:45"), ReminderStatus.FIRED,
            ),
        )
        set("139", "14:12", 15)
        assertEquals(Result.Success(1), repo.purgeOld())
        assertEquals(1, repo.observe("u1").first().size)
    }
}
