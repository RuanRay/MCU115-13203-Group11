package com.group11.hometide.ui.search

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.ui.main.MainActivity
import com.group11.hometide.util.Passengers
import kotlinx.coroutines.runBlocking
import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** P3 手動查詢（任務 4-1）：TC-07、FR-SRCH-01 AC、TC-06 */
@RunWith(AndroidJUnit4::class)
class SearchFragmentTest {

    @Before
    fun seedStations() = runBlocking {
        // updated_at 用現在時間，啟動時不會重新下載覆蓋
        val now = System.currentTimeMillis()
        ApplicationProvider.getApplicationContext<HometideApp>().container.database.stationDao().upsertAll(
            listOf(
                Station(Operator.TRA, "4220", "臺南", "Tainan", 0, now),
                Station(Operator.TRA, "1210", "新竹", "Hsinchu", 1, now),
                Station(Operator.TRA, "3360", "新烏日", "Xinwuri", 2, now),
            ),
        )
    }

    /** TC-49（部分）：預設乘客存在本機 DataStore，P3 乘客列以預設乘客為初始值；車票夾、下一班於各自任務驗證 */
    @Test
    fun tc49_defaultPassengersShownOnSearch() {
        val auth = ApplicationProvider.getApplicationContext<HometideApp>().container.authRepository
        runBlocking { auth.setDefaultPassengers(Passengers.fromJson("""{"ADULT":1,"SENIOR":1}""")) }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.tvPassengers)).check(matches(withText("👤 乘客：全票 1・敬老 1 ▾")))
            }
        } finally {
            runBlocking { auth.setDefaultPassengers(Passengers.DEFAULT) }
        }
    }

    /** TC-07：起訖站相同按查詢 → 「起站與迄站不能相同」 */
    @Test
    fun tc07_sameStationShowsError() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.actvOrigin)).perform(replaceText("臺南"))
            onView(withId(R.id.actvDest)).perform(replaceText("台南"))
            onView(withId(R.id.btnSearch)).perform(scrollTo(), click())
            onView(withText(R.string.error_same_station)).check(matches(isDisplayed()))
        }
    }

    /** FR-SRCH-01 AC：輸入「新」出現新竹、新烏日；TC-06：輸入「台南」出現臺南 */
    @Test
    fun autocomplete_suggestsStations() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.actvOrigin)).perform(click(), replaceText("新"))
            onView(first(withText("新竹"))).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            onView(first(withText("新烏日"))).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
            onView(withId(R.id.actvDest)).perform(click(), replaceText("台南"))
            onView(first(withText("臺南"))).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
        }
    }

    /** FR-SRCH-02：英文站名也能比對，下拉顯示中文站名 */
    @Test
    fun autocomplete_englishName() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.actvOrigin)).perform(click(), replaceText("hsinchu"))
            onView(first(withText("新竹"))).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
        }
    }

    /** 裝置上可能已有真實車站資料（代碼不同、站名相同），只檢查第一個符合者 */
    private fun <T> first(matcher: Matcher<T>): Matcher<T> = object : BaseMatcher<T>() {
        private var found = false
        override fun matches(item: Any?): Boolean = !found && matcher.matches(item) && true.also { found = true }
        override fun describeTo(description: Description) {
            description.appendText("first ").appendDescriptionOf(matcher)
        }
    }
}
