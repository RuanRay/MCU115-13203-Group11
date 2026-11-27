package com.group11.hometide.util

import com.group11.hometide.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-AI-04：確認卡時段名稱（9.2）與 P4 時段篩選 */
class TimeRangeTest {

    @Test
    fun namedPeriod_onlyExactMatch() {
        assertEquals(R.string.period_afternoon, namedPeriodRes("12:00", "18:00"))
        assertEquals(R.string.period_evening, namedPeriodRes("18:00", "23:59"))
        assertNull(namedPeriodRes("17:00", null))
        assertNull(namedPeriodRes("12:00", "17:00"))
    }

    @Test
    fun inTimeRange_inclusive_openEnded() {
        assertTrue(inTimeRange("12:00", "12:00", "18:00"))
        assertTrue(inTimeRange("18:00", "12:00", "18:00"))
        assertFalse(inTimeRange("18:01", "12:00", "18:00"))
        assertTrue(inTimeRange("23:30", "17:00", null))
        assertFalse(inTimeRange("16:59", "17:00", null))
        assertTrue(inTimeRange("05:00", null, null))
    }
}
