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
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** TC-12：查詢今天且有已過班次 → 已過班次變淡、標示「已開出」、無提醒按鈕（資料由 timetable_cache 提供，不連網） */
@RunWith(AndroidJUnit4::class)
class ResultActivityTest {

    private val fmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun row(q: TimetableQuery, no: String, dep: LocalTime, arr: LocalTime, type: String = "3") = TimetableCache(
        operator = q.operator, originId = q.originId, destId = q.destId, travelDate = q.travelDate,
        trainNo = no, trainTypeCode = type, trainTypeName = "自強", tripLine = 1,
        departTime = dep.format(fmt), arriveTime = arr.format(fmt), durationMin = 60,
        reservedSeat = true, bikeAllowed = false, fetchedAt = System.currentTimeMillis(),
    )

    @Test
    fun tc12_departedTrainIsFadedStampedAndHasNoReminder() {
        val now = LocalTime.now()
        assumeTrue("避開午夜前後", now.isAfter(LocalTime.of(1, 0)) && now.isBefore(LocalTime.of(22, 0)))
        val today = LocalDate.now()
        val q = TimetableQuery(Operator.TRA, "1080", "4220", today.toString())
        runBlocking {
            ApplicationProvider.getApplicationContext<HometideApp>().container.database.timetableDao().replace(
                q,
                listOf(row(q, "125", now.minusMinutes(60), now), row(q, "139", now.plusMinutes(90), now.plusMinutes(150))),
            )
        }
        val ctx = ApplicationProvider.getApplicationContext<HometideApp>()
        ActivityScenario.launch<ResultActivity>(ResultActivity.intent(ctx, q.operator, q.originId, q.destId, today)).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val rv = activity.findViewById<RecyclerView>(R.id.rvTimeline)
                assertEquals(2, rv.adapter?.itemCount)
                val departed = rv.findViewHolderForAdapterPosition(0)!!.itemView
                val upcoming = rv.findViewHolderForAdapterPosition(1)!!.itemView
                assertEquals(activity.getString(R.string.status_departed), departed.findViewById<TextView>(R.id.chipStatus).text)
                assertEquals(View.GONE, departed.findViewById<View>(R.id.btnRemind).visibility)
                assertEquals(View.VISIBLE, departed.findViewById<View>(R.id.vDepartedStamp).visibility)
                assertEquals(0.45f, departed.findViewById<View>(R.id.tkTrain).alpha, 0.001f)
                assertEquals(activity.getString(R.string.status_on_time), upcoming.findViewById<TextView>(R.id.chipStatus).text)
                assertEquals(View.VISIBLE, upcoming.findViewById<View>(R.id.btnRemind).visibility)
            }
        }
    }

    /** TC-13：每列顯示全票票價；約略值為「約$1,350」；取不到時「—」 */
    @Test
    fun tc13_eachRowShowsAdultFare() {
        val tomorrow = LocalDate.now().plusDays(1)
        val q = TimetableQuery(Operator.TRA, "1080", "4220", tomorrow.toString())
        val db = ApplicationProvider.getApplicationContext<HometideApp>().container.database
        val now = System.currentTimeMillis()
        runBlocking {
            db.timetableDao().replace(
                q,
                listOf(
                    row(q, "139", LocalTime.of(8, 0), LocalTime.of(11, 0), "3"),
                    row(q, "611", LocalTime.of(9, 0), LocalTime.of(12, 0), "4"),
                    row(q, "2211", LocalTime.of(10, 0), LocalTime.of(13, 0), "99"),
                ),
            )
            db.fareDao().replace(
                q.operator, q.originId, q.destId,
                listOf(
                    FareCache(q.operator, q.originId, q.destId, "3", FareClass.ADULT, 739, false, now),
                    FareCache(q.operator, q.originId, q.destId, "4", FareClass.ADULT, 1350, true, now),
                ),
            )
        }
        val ctx = ApplicationProvider.getApplicationContext<HometideApp>()
        ActivityScenario.launch<ResultActivity>(ResultActivity.intent(ctx, q.operator, q.originId, q.destId, tomorrow)).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val rv = activity.findViewById<RecyclerView>(R.id.rvTimeline)
                val fares = (0 until 3).map { rv.findViewHolderForAdapterPosition(it)!!.itemView.findViewById<TextView>(R.id.tvFare).text.toString() }
                assertEquals(listOf("$739", "約$1,350", "—"), fares)
            }
        }
    }
}
