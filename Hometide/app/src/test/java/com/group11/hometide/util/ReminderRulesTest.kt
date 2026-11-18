package com.group11.hometide.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** FR-RMD-01：提醒時間、停用規則、同一天範圍、保留 7 天 */
class ReminderRulesTest {

    private val tw = ZoneId.of("Asia/Taipei")
    private val depart = ReminderRules.departAt("2026-11-18", "14:12", tw)

    @Test
    fun triggerAt_isDepartMinusMinutes() {
        assertEquals(depart - 15 * 60_000, ReminderRules.triggerAt(depart, 15))
    }

    /** TC-18 前提：20 分鐘後的班次只能選 15 分 */
    @Test
    fun options_disabledWhenTriggerPassed() {
        val now = depart - 20 * 60_000
        assertTrue(ReminderRules.isAvailable(depart, 15, now))
        assertFalse(ReminderRules.isAvailable(depart, 30, now))
        assertFalse(ReminderRules.isAvailable(depart, 60, now))
        assertFalse(ReminderRules.isAvailable(depart, 15, depart - 15 * 60_000)) // 剛好到點也算已過
    }

    @Test
    fun dayRange_coversWholeLocalDay() {
        val (start, end) = ReminderRules.dayRange(depart, tw)
        assertEquals(ReminderRules.departAt("2026-11-18", "00:00", tw), start)
        assertEquals(ReminderRules.departAt("2026-11-19", "00:00", tw), end)
    }

    @Test
    fun purgeBefore_isSevenDays() {
        assertEquals(depart - 7L * 24 * 60 * 60_000, ReminderRules.purgeBefore(depart))
    }
}
