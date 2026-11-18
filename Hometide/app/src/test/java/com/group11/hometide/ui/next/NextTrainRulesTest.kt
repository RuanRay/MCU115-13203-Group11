package com.group11.hometide.ui.next

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** FR-NEXT-01 標題「還有 N 分鐘／X 小時 Y 分」、FR-NEXT-03 剩 5 分鐘內變紅（TC-29 的判斷部分） */
class NextTrainRulesTest {

    @Test
    fun title_switchesToHoursAt60() {
        assertNull(NextTrainViewModel.hoursMinutes(59))
        assertEquals(1 to 0, NextTrainViewModel.hoursMinutes(60))
        assertEquals(2 to 5, NextTrainViewModel.hoursMinutes(125))
    }

    @Test
    fun urgent_withinFiveMinutes() {
        assertTrue(NextTrainViewModel.isUrgent(0))
        assertTrue(NextTrainViewModel.isUrgent(5))
        assertFalse(NextTrainViewModel.isUrgent(6))
    }
}
