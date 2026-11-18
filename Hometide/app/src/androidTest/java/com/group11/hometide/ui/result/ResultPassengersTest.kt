package com.group11.hometide.ui.result

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.dao.TimetableQuery
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.util.Passengers
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * CR-01 票種與多人：TC-43～47、51（資料由 timetable_cache、fare_cache 提供，不連網，即 TC-51 的離線重算）。
 * 自強（3）有全票 739、孩童 370；區間快（10）只有全票 476。
 */
@RunWith(AndroidJUnit4::class)
class ResultPassengersTest {

    private val app = ApplicationProvider.getApplicationContext<HometideApp>()
    private val date = LocalDate.now().plusDays(1)
    private val q = TimetableQuery(Operator.TRA, "1015", "4220", date.toString())

    private fun row(no: String, type: String, name: String, dep: String) = TimetableCache(
        operator = q.operator, originId = q.originId, destId = q.destId, travelDate = q.travelDate,
        trainNo = no, trainTypeCode = type, trainTypeName = name, tripLine = null,
        departTime = dep, arriveTime = "23:00", durationMin = 60,
        reservedSeat = true, bikeAllowed = false, fetchedAt = System.currentTimeMillis(),
    )

    @Before
    fun seed() = runBlocking {
        val db = app.container.database
        val now = System.currentTimeMillis()
        db.timetableDao().replace(q, listOf(row("139", "3", "自強", "08:00"), row("2211", "10", "區間快", "09:00")))
        db.fareDao().replace(
            q.operator, q.originId, q.destId,
            listOf(
                FareCache(q.operator, q.originId, q.destId, "3", FareClass.ADULT, 739, false, now),
                FareCache(q.operator, q.originId, q.destId, "3", FareClass.CHILD, 370, false, now),
                FareCache(q.operator, q.originId, q.destId, "10", FareClass.ADULT, 476, false, now),
            ),
        )
    }

    private fun launch(pax: String, block: (ActivityScenario<ResultActivity>) -> Unit) {
        val intent = ResultActivity.intent(app, q.operator, q.originId, q.destId, date, Passengers.fromJson(pax))
        ActivityScenario.launch<ResultActivity>(intent).use { s ->
            s.onActivity { it.findViewById<RecyclerView>(R.id.rvTimeline).itemAnimator = null } // 讓展開立即反映
            idle()
            block(s)
        }
    }

    private fun idle() = InstrumentationRegistry.getInstrumentation().waitForIdleSync()

    private fun ResultActivity.item(pos: Int): View =
        findViewById<RecyclerView>(R.id.rvTimeline).findViewHolderForAdapterPosition(pos)!!.itemView

    private fun View.text(id: Int) = findViewById<TextView>(id).text.toString()

    /** TC-43：全票 2、孩童 1 → 總價＝739×2＋370；小字「全票2・孩童1」 */
    @Test
    fun tc43_totalForMultiplePassengers() = launch("""{"ADULT":2,"CHILD":1}""") { s ->
        s.onActivity { a ->
            assertEquals("👤 3 人 ▾", a.findViewById<TextView>(R.id.chipPassengers).text.toString())
            assertEquals("$1,848", a.item(0).text(R.id.tvFare))
            assertEquals("全票2・孩童1", a.item(0).text(R.id.tvFareMix))
            // 區間快沒有孩童票：總價「—」＋「孩童查無」
            assertEquals("—", a.item(1).text(R.id.tvFare))
            assertEquals("全票2・孩童1\n孩童查無", a.item(1).text(R.id.tvFareMix))
        }
    }

    /** TC-44：合計 6 人後所有「＋」停用；合計 1 人時該列「−」停用 */
    @Test
    fun tc44_sheetLimits() = launch("""{"ADULT":1}""") {
        onView(withId(R.id.chipPassengers)).perform(click())
        onView(allOf(withId(R.id.btnMinus), isDescendantOfA(withId(R.id.rowPaxAdult)))).check(matches(isNotEnabled()))
        val plus = allOf(withId(R.id.btnPlus), isDescendantOfA(withId(R.id.rowPaxAdult)))
        repeat(5) { onView(plus).perform(click()) }
        onView(withId(R.id.tvPaxTotal)).check(matches(withText("合計 6 人")))
        for (row in listOf(R.id.rowPaxAdult, R.id.rowPaxStudent, R.id.rowPaxChild, R.id.rowPaxSenior, R.id.rowPaxDisabled)) {
            onView(allOf(withId(R.id.btnPlus), isDescendantOfA(withId(row)))).check(matches(isNotEnabled()))
        }
        onView(allOf(withId(R.id.btnMinus), isDescendantOfA(withId(R.id.rowPaxAdult)))).check(matches(isEnabled()))
        pressBack()
        backToActivity()
    }

    /**
     * 面板關閉後等結果頁重新取得焦點再結束測試：太早關閉 Activity 時，Android 17 模擬器會在
     * TopResumedActivityChangeItem 拋 IllegalArgumentException（框架時序問題，非 App 程式）
     */
    private fun backToActivity() {
        onView(withId(R.id.rvTimeline)).check(matches(isDisplayed()))
    }

    /** TC-45：結果頁改成孩童 1 → 只重算票價，存根顯示孩童單價與「孩童」 */
    @Test
    fun tc45_changePassengersRecalculates() = launch("""{"ADULT":1}""") { s ->
        onView(withId(R.id.chipPassengers)).perform(click())
        onView(allOf(withId(R.id.btnPlus), isDescendantOfA(withId(R.id.rowPaxChild)))).perform(click())
        onView(allOf(withId(R.id.btnMinus), isDescendantOfA(withId(R.id.rowPaxAdult)))).perform(click())
        onView(withId(R.id.btnPaxConfirm)).perform(click())
        backToActivity()
        s.onActivity { a ->
            assertEquals("$370", a.item(0).text(R.id.tvFare))
            assertEquals("孩童", a.item(0).text(R.id.tvFareMix))
        }
    }

    /** TC-46：含學生 → 總價「—」＋「學生查無」；明細學生列「查無／—」、合計「—」並說明 */
    @Test
    fun tc46_missingClassInStubAndDetail() = launch("""{"ADULT":1,"STUDENT":1}""") { s ->
        s.onActivity { a ->
            assertEquals("—", a.item(0).text(R.id.tvFare))
            assertEquals("全票1・學生1\n學生查無", a.item(0).text(R.id.tvFareMix))
            a.item(0).findViewById<View>(R.id.tkTrain).performClick()
        }
        idle()
        s.onActivity { a ->
            val item = a.item(0)
            assertEquals(View.VISIBLE, item.findViewById<View>(R.id.layoutFareDetail).visibility)
            val rows = item.findViewById<RecyclerView>(R.id.rvFareRows)
            val student = rows.findViewHolderForAdapterPosition(1)!!.itemView
            assertEquals("學生 × 1", student.text(R.id.tvClass))
            assertEquals("查無", student.text(R.id.tvUnit))
            assertEquals("—", student.text(R.id.tvSubtotal))
            assertEquals("—", item.text(R.id.tvDetailTotal))
            assertEquals("自強的學生票價查無，無法計算合計", item.text(R.id.tvDetailNote))
        }
    }

    /** TC-47：兩張同時展開；再點其中一張收合；點「提醒」不展開 */
    @Test
    fun tc47_multipleExpandAndRemindDoesNotToggle() = launch("""{"ADULT":1}""") { s ->
        s.onActivity { a ->
            a.item(0).findViewById<View>(R.id.tkTrain).performClick()
            a.item(1).findViewById<View>(R.id.tkTrain).performClick()
        }
        idle()
        s.onActivity { a ->
            assertEquals(View.VISIBLE, a.item(0).findViewById<View>(R.id.layoutFareDetail).visibility)
            assertEquals(View.VISIBLE, a.item(1).findViewById<View>(R.id.layoutFareDetail).visibility)
            a.item(0).findViewById<View>(R.id.tkTrain).performClick()
        }
        idle()
        s.onActivity { a ->
            assertEquals(View.GONE, a.item(0).findViewById<View>(R.id.layoutFareDetail).visibility)
            assertEquals(View.VISIBLE, a.item(1).findViewById<View>(R.id.layoutFareDetail).visibility)
            a.item(0).findViewById<View>(R.id.btnRemind).performClick() // 訪客會出現登入提示
        }
        idle()
        s.onActivity { a -> assertEquals(View.GONE, a.item(0).findViewById<View>(R.id.layoutFareDetail).visibility) }
    }
}
