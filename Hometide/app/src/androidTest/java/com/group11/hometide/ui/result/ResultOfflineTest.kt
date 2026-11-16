package com.group11.hometide.ui.result

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** TC-15、FR-RES-06：關閉模擬器網路後，有快取 → 快取＋離線橫幅；沒有快取 → 錯誤畫面＋重試 */
@RunWith(AndroidJUnit4::class)
class ResultOfflineTest {

    private val app get() = ApplicationProvider.getApplicationContext<HometideApp>()
    private val tomorrow = LocalDate.now().plusDays(1)

    private fun shell(cmd: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd).close()
        Thread.sleep(SHELL_SETTLE_MS)
    }

    @Before
    fun networkOff() {
        shell("svc wifi disable")
        shell("svc data disable")
    }

    @After
    fun networkOn() {
        shell("svc wifi enable")
        shell("svc data enable")
    }

    /** 等到條件成立（最多 15 秒），避免依賴網路逾時的確切時間 */
    private fun <T> waitFor(scenario: ActivityScenario<ResultActivity>, check: (ResultActivity) -> T?): T {
        repeat(POLL_TIMES) {
            var result: T? = null
            scenario.onActivity { result = check(it) }
            result?.let { return it }
            Thread.sleep(POLL_MS)
        }
        error("condition not met")
    }

    @Test
    fun tc15_offlineShowsCacheAndBanner() {
        val q = TimetableQuery(Operator.TRA, "1080", "4220", tomorrow.toString())
        val stale = System.currentTimeMillis() - 3 * 60 * 60 * 1000 // 超過 2 小時，會先嘗試連線
        runBlocking {
            app.container.database.timetableDao().replace(
                q,
                listOf(
                    TimetableCache(
                        operator = q.operator, originId = q.originId, destId = q.destId, travelDate = q.travelDate,
                        trainNo = "139", trainTypeCode = "3", trainTypeName = "自強", tripLine = 1,
                        departTime = "14:12", arriveTime = "17:20", durationMin = 188,
                        reservedSeat = true, bikeAllowed = false, fetchedAt = stale,
                    ),
                ),
            )
        }
        ActivityScenario.launch<ResultActivity>(ResultActivity.intent(app, q.operator, q.originId, q.destId, tomorrow)).use { s ->
            val banner = waitFor(s) { a -> a.findViewById<TextView>(R.id.tvOfflineBanner).takeIf { it.visibility == View.VISIBLE } }
            // 「目前為離線資料（最後更新 MM/dd HH:mm）」
            val prefix = app.getString(R.string.offline_banner, "X").substringBefore("X")
            assertTrue(banner.text.toString(), banner.text.startsWith(prefix) && banner.text.length > prefix.length + 1)
            s.onActivity { a -> assertEquals(1, a.findViewById<RecyclerView>(R.id.rvTimeline).adapter?.itemCount) }
        }
    }

    @Test
    fun noCache_showsErrorWithRetry() {
        val q = TimetableQuery(Operator.TRA, "0900", "7000", tomorrow.toString())
        runBlocking { app.container.database.timetableDao().delete(q.operator, q.originId, q.destId, q.travelDate) }
        ActivityScenario.launch<ResultActivity>(ResultActivity.intent(app, q.operator, q.originId, q.destId, tomorrow)).use { s ->
            val retry = waitFor(s) { a -> a.findViewById<View>(R.id.btnRetry).takeIf { it.visibility == View.VISIBLE } }
            assertTrue(retry.isShown)
            s.onActivity { a ->
                assertEquals(app.getString(R.string.trains_unavailable), a.findViewById<TextView>(R.id.tvEmptyTitle).text)
                assertEquals(app.getString(R.string.error_network), a.findViewById<TextView>(R.id.tvEmptyMessage).text)
                assertEquals(View.GONE, a.findViewById<View>(R.id.tvOfflineBanner).visibility)
            }
        }
    }

    private companion object {
        const val SHELL_SETTLE_MS = 1_500L
        const val POLL_MS = 500L
        const val POLL_TIMES = 30
    }
}
