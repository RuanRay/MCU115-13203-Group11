package com.group11.hometide.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.group11.hometide.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** FR-RMD-03：點提醒通知（MainActivity.intentForReminders）→ 我的路線的「出發提醒」分頁 */
@RunWith(AndroidJUnit4::class)
class OpenRemindersTest {

    @Test
    fun notificationIntent_opensRemindersTab() {
        val intent = MainActivity.intentForReminders(ApplicationProvider.getApplicationContext())
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { a ->
                assertEquals(R.id.navRoutes, a.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId)
                assertEquals(1, a.findViewById<ViewPager2>(R.id.viewPager).currentItem)
            }
        }
    }

    /** App 已開在其他分頁時（onNewIntent）也會切過去 */
    @Test
    fun notificationIntent_whileOnSearchTab() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { a -> InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(a, MainActivity.intentForReminders(ctx)) }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { a ->
                assertEquals(R.id.navRoutes, a.findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId)
                assertEquals(1, a.findViewById<ViewPager2>(R.id.viewPager).currentItem)
            }
        }
    }
}
