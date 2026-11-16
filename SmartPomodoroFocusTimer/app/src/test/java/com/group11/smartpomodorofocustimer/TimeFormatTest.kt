package com.group11.smartpomodorofocustimer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TimeFormatTest {
    @Test
    fun formatsAndRoundsMinutes() {
        assertEquals("25:00", TimerService.formatTime(1500))
        assertEquals("18:42", TimerService.formatTime(18 * 60 + 42))
        assertEquals("00:00", TimerService.formatTime(0))
        assertEquals(19, TimerService.minutesLeft(18 * 60 + 59)) // 18:59 -> "19 mins"
        assertEquals(1, TimerService.minutesLeft(1))
        assertEquals(0, TimerService.minutesLeft(0))
    }

    @Test
    fun remindsEveryTwoMinutesButNotAtStartOrEnd() {
        val total = 25 * 60
        val ticks = (0..total).filter { TimerService.isReminderTick(it, total) }
        assertEquals(12, ticks.size)          // 23:00, 21:00 ... 01:00
        assertEquals(60, ticks.first())       // 01:00 left
        assertEquals(1380, ticks.last())
        assertFalse(TimerService.isReminderTick(1500, total)) // not at 25:00 (session start)
        assertFalse(TimerService.isReminderTick(0, total))
    }
}
