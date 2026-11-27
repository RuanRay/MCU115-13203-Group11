package com.group11.hometide.ui.profile

import com.group11.hometide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** FR-USG-04；TC-23（區間） */
class UsageRulesTest {

    private val zone = ZoneOffset.ofHours(8)
    private val fri = LocalDateTime.of(2026, 11, 27, 15, 30) // 週五

    @Test
    fun rangeStart_todayWeekAll() {
        assertEquals(LocalDateTime.of(2026, 11, 27, 0, 0).toInstant(zone).toEpochMilli(), UsageRules.rangeStart(StatRange.TODAY, fri, zone))
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0).toInstant(zone).toEpochMilli(), UsageRules.rangeStart(StatRange.WEEK, fri, zone))
        // 週一當天：本週從今天 00:00 起
        val mon = LocalDateTime.of(2026, 11, 23, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 23, 0, 0).toInstant(zone).toEpochMilli(), UsageRules.rangeStart(StatRange.WEEK, mon, zone))
        assertEquals(0L, UsageRules.rangeStart(StatRange.ALL, fri, zone))
    }

    @Test
    fun minutes_rounded() {
        assertEquals(0L, UsageRules.minutes(29_999))
        assertEquals(1L, UsageRules.minutes(30_000))
        assertEquals(46L, UsageRules.minutes(46 * 60_000L + 20_000))
    }

    @Test
    fun barPercent_relativeToLongest() {
        assertEquals(100, UsageRules.barPercent(21, 21))
        assertEquals(42, UsageRules.barPercent(9, 21))
        assertEquals(0, UsageRules.barPercent(5, 0))
    }

    @Test
    fun pageLabels() {
        assertEquals(R.string.nav_search, UsageRules.pageLabel("SearchFragment"))
        assertEquals(R.string.nav_highway, UsageRules.pageLabel("HighwayFragment"))
        assertEquals(R.string.page_result, UsageRules.pageLabel("ResultActivity"))
        assertNull(UsageRules.pageLabel("MainActivity"))
    }
}
