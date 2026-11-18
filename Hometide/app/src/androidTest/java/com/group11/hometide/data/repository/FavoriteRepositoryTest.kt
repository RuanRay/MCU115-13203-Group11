package com.group11.hometide.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.remote.HometideApi
import com.group11.hometide.util.AppError
import com.group11.hometide.util.NextTrain
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * FavoriteRepository（FR-RES-05、FR-FAV-02、04）：in-memory Room；時刻表與票價由快取提供，
 * Worker 位址指向本機不存在的埠（需要網路的情況等同離線）。
 */
@RunWith(AndroidJUnit4::class)
class FavoriteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: FavoriteRepository
    private val now = LocalDateTime.of(2026, 11, 18, 14, 5)
    private val uid = "u1"

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val gson = Gson()
        val transit = TransitRepository(
            HometideApi.create("http://127.0.0.1:9/", gson),
            db.stationDao(), db.searchHistoryDao(), db.timetableDao(), db.fareDao(), gson,
        )
        repo = FavoriteRepository(db.favoriteDao(), transit, clock = { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(dest: String) =
        repo.toggle(uid, Operator.TRA, "1015", dest, "桃園 → $dest")

    /** FR-RES-05：切換新增／取消；上限 10 筆 */
    @Test
    fun toggle_addRemoveAndLimit() = runBlocking {
        val added = (add("4220") as Result.Success).data
        assertTrue(added is FavoriteRepository.Toggle.Added)
        assertTrue(repo.isFavorite(uid, Operator.TRA, "1015", "4220").first())
        assertTrue((add("4220") as Result.Success).data is FavoriteRepository.Toggle.Removed)
        repeat(10) { add("40$it") }
        assertEquals(Result.Failure(AppError.FavoriteLimit), add("4220"))
        assertEquals(10, repo.observe(uid).first().size)
    }

    /** TC-17（資料層）：刪除後復原，原排序不變 */
    @Test
    fun removeThenRestore_keepsOrder() = runBlocking {
        add("4220"); add("1000"); add("3300")
        val second = repo.observe(uid).first()[1]
        repo.remove(second)
        assertEquals(listOf("4220", "3300"), repo.observe(uid).first().map { it.destId })
        repo.restore(second)
        assertEquals(listOf("4220", "1000", "3300"), repo.observe(uid).first().map { it.destId })
    }

    @Test
    fun reorderAndRename() = runBlocking {
        add("4220"); add("1000")
        val ids = repo.observe(uid).first().map { it.id }
        repo.reorder(ids.reversed())
        repo.rename(ids[0], "  學校回家一二三四五六七  ")
        val list = repo.observe(uid).first()
        assertEquals(listOf("1000", "4220"), list.map { it.destId })
        assertEquals("學校回家一二三四五六", list[1].label) // 去頭尾空白、最多 10 字
    }

    private fun train(date: String, no: String, dep: String, type: String = "3") = TimetableCache(
        operator = Operator.TRA, originId = "1015", destId = "4220", travelDate = date, trainNo = no,
        trainTypeCode = type, trainTypeName = "自強", tripLine = null, departTime = dep, arriveTime = "23:00",
        durationMin = 60, reservedSeat = true, bikeAllowed = false, fetchedAt = System.currentTimeMillis(),
    )

    private suspend fun cache(date: String, vararg trains: TimetableCache) =
        db.timetableDao().replace(TimetableQuery(Operator.TRA, "1015", "4220", date), trains.toList())

    /** TC-16（資料層）：下一班＋N 分後＋以預設乘客計算的票價 */
    @Test
    fun nextTrain_pickNextWithFare() = runBlocking {
        cache("2026-11-18", train("2026-11-18", "125", "13:50"), train("2026-11-18", "139", "14:12"))
        val t = System.currentTimeMillis()
        db.fareDao().replace(
            Operator.TRA, "1015", "4220",
            listOf(FareCache(Operator.TRA, "1015", "4220", "3", FareClass.ADULT, 739, false, t)),
        )
        add("4220")
        val next = repo.nextTrain(repo.observe(uid).first()[0], Passengers.DEFAULT) as NextTrain.Next
        assertEquals("139", next.train.trainNo)
        assertEquals(7, next.minutes)
        assertEquals(739, next.fare?.total)
    }

    /** FR-FAV-04：今天已無班次 → 明天首班；明天也查不到 → 今日已無班次；今天無快取且無網路 → 離線 */
    @Test
    fun nextTrain_tomorrowNoneAndOffline() = runBlocking {
        add("4220")
        val route = repo.observe(uid).first()[0]
        assertEquals(NextTrain.Offline, repo.nextTrain(route, Passengers.DEFAULT))
        cache("2026-11-18", train("2026-11-18", "125", "08:00"))
        assertEquals(NextTrain.NoneToday, repo.nextTrain(route, Passengers.DEFAULT))
        cache("2026-11-19", train("2026-11-19", "111", "06:30"), train("2026-11-19", "101", "06:05"))
        assertEquals(NextTrain.Tomorrow("06:05"), repo.nextTrain(route, Passengers.DEFAULT))
    }
}
