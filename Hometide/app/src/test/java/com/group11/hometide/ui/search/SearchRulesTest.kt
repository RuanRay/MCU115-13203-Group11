package com.group11.hometide.ui.search

import com.group11.hometide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SearchRulesTest {

    @Test
    fun periodBoundaries() {
        assertEquals(R.string.period_evening, SearchRules.periodRes(4))
        assertEquals(R.string.period_morning, SearchRules.periodRes(5))
        assertEquals(R.string.period_morning, SearchRules.periodRes(10))
        assertEquals(R.string.period_afternoon, SearchRules.periodRes(11))
        assertEquals(R.string.period_afternoon, SearchRules.periodRes(16))
        assertEquals(R.string.period_evening, SearchRules.periodRes(17))
    }

    @Test
    fun fridayTitle() {
        assertEquals(R.string.search_title_friday, SearchRules.titleRes(LocalDate.of(2026, 11, 6)))
        assertEquals(R.string.search_title_default, SearchRules.titleRes(LocalDate.of(2026, 11, 7)))
    }

    @Test
    fun dateRange_todayTo60Days() {
        val today = LocalDate.of(2026, 11, 16)
        assertTrue(SearchRules.isSelectable(today, today))
        assertTrue(SearchRules.isSelectable(today.plusDays(60), today))
        assertFalse(SearchRules.isSelectable(today.plusDays(61), today))
        assertFalse(SearchRules.isSelectable(today.minusDays(1), today))
    }

    /** TC-07：起訖站相同 → 「起站與迄站不能相同」（台／臺、結尾「站」視為相同） */
    @Test
    fun precheck() {
        assertEquals(R.string.error_same_station, SearchRules.precheck("臺南", "台南站"))
        assertEquals(R.string.error_station_required, SearchRules.precheck(" ", "臺南"))
        assertEquals(R.string.error_station_required, SearchRules.precheck("桃園", ""))
        assertNull(SearchRules.precheck("桃園", "臺南"))
    }
}
