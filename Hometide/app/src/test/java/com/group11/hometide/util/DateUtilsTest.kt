package com.group11.hometide.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class DateUtilsTest {
    @Test
    fun rocDate_convertsYearAndPadsMonthDay() {
        assertEquals("115.11.07", DateUtils.rocDate(LocalDate.of(2026, 11, 7)))
        assertEquals("115.01.05", DateUtils.rocDate(LocalDate.of(2026, 1, 5)))
        assertEquals("100.12.31", DateUtils.rocDate(LocalDate.of(2011, 12, 31)))
    }

    @Test
    fun weekdayNarrow_returnsSingleChineseCharacter() {
        assertEquals("六", DateUtils.weekdayNarrow(LocalDate.of(2026, 11, 7)))
        assertEquals("日", DateUtils.weekdayNarrow(LocalDate.of(2026, 11, 8)))
    }
}
