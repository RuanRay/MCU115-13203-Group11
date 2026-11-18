package com.group11.hometide.ui.next

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.R
import com.group11.hometide.ui.main.MainActivity
import org.junit.Test
import org.junit.runner.RunWith

/** TC-30：以訪客身分進入「下一班」→ 問號車票與「登入／註冊」「先去查詢」（測試安裝為全新資料，未登入） */
@RunWith(AndroidJUnit4::class)
class NextTrainGuestTest {

    @Test
    fun tc30_guestSeesEmptyState() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.navNext)).perform(click())
            onView(withId(R.id.layoutNextEmpty)).check(matches(isDisplayed()))
            onView(withId(R.id.tvNextTitle)).check(matches(withText(R.string.next_title_guest)))
            onView(withId(R.id.btnLogin)).check(matches(withText(R.string.btn_member_login)))
            onView(withId(R.id.btnGoSearch)).check(matches(withText(R.string.btn_go_search)))
        }
    }
}
