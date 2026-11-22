package com.group11.hometide.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.AppDatabase
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** DriveRouteRepository（FR-HWY-01、7.1.12）：in-memory Room；不寫 DataStore（不碰實際 App 的 last_drive_route） */
@RunWith(AndroidJUnit4::class)
class DriveRouteRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: DriveRouteRepository
    private val q = HighwayQuery("000010", "S", "桃園", "永康")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repo = DriveRouteRepository(db.driveRouteDao(), SettingsStore(ApplicationProvider.getApplicationContext())) { 1L }
    }

    @After
    fun tearDown() = db.close()

    /** TC-34 前半：收藏後出現在清單；同一條路線再收藏沿用原本那筆 */
    @Test
    fun add_sameRouteReturnsExisting(): Unit = runBlocking {
        val a = (repo.add("u1", q, " 開車回家 ") as Result.Success).data
        val b = (repo.add("u1", q, "另一個名字") as Result.Success).data
        assertEquals(a.id, b.id)
        assertEquals(listOf("開車回家"), repo.observe("u1").first().map { it.label })
        // 不同帳號互不影響
        assertTrue(repo.add("u2", q, "開車回家") is Result.Success)
        assertEquals(1, repo.observe("u1").first().size)
    }

    @Test
    fun add_limit10_andSortOrderAppends() = runBlocking {
        repeat(10) { i -> repo.add("u1", q.copy(exitIc = "交流道$i"), "路線$i") }
        assertEquals(Result.Failure(AppError.FavoriteLimit), repo.add("u1", q, "第 11 條"))
        assertEquals((0 until 10).toList(), repo.observe("u1").first().map { it.sortOrder })
    }

    @Test
    fun rename_trimsAndIgnoresBlank() = runBlocking {
        val r = (repo.add("u1", q, "開車回家") as Result.Success).data
        repo.rename(r, "  回學校的路線很長很長很長  ")
        assertEquals("回學校的路線很長很長", repo.observe("u1").first().single().label)
        repo.rename(r, "   ")
        assertEquals("回學校的路線很長很長", repo.observe("u1").first().single().label)
    }

    @Test
    fun removeAndRestore_keepsIdAndOrder() = runBlocking {
        val a = (repo.add("u1", q, "A") as Result.Success).data
        val b = (repo.add("u1", q.copy(exitIc = "臺南"), "B") as Result.Success).data
        repo.remove(a)
        assertEquals(listOf(b.id), repo.observe("u1").first().map { it.id })
        repo.restore(a)
        assertEquals(listOf(a.id, b.id), repo.observe("u1").first().map { it.id })
        // 已經又收藏了同一條：不重複寫
        assertEquals(Result.Success(Unit), repo.restore(a))
        assertEquals(2, repo.observe("u1").first().size)
    }

    /** 刪 1 條、又新增 1 條（回到 10 條）後按「復原」：不超過上限 */
    @Test
    fun restore_respectsLimit() = runBlocking {
        repeat(10) { i -> repo.add("u1", q.copy(exitIc = "交流道$i"), "路線$i") }
        val first = repo.observe("u1").first().first()
        repo.remove(first)
        repo.add("u1", q.copy(exitIc = "新的"), "新")
        assertEquals(Result.Failure(AppError.FavoriteLimit), repo.restore(first))
        assertEquals(10, repo.observe("u1").first().size)
    }
}
