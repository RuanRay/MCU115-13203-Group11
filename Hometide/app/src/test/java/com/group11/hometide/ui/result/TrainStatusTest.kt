package com.group11.hometide.ui.result

import com.group11.hometide.data.local.entity.Operator
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/** FR-RES-03、FR-BUS-04 狀態判斷（TC-12 的邏輯部分） */
class TrainStatusTest {

    private val today = LocalDate.of(2026, 11, 16)
    private val now = LocalTime.of(14, 5)

    private fun of(op: Operator, dep: String, date: LocalDate = today) = TrainStatus.of(op, dep, date, today, now)

    @Test
    fun today_departedSoonOnTime() {
        assertEquals(TrainStatus.Departed, of(Operator.TRA, "13:05"))
        assertEquals(TrainStatus.Departed, of(Operator.TRA, "14:04"))
        assertEquals(TrainStatus.Soon(0), of(Operator.TRA, "14:05"))
        assertEquals(TrainStatus.Soon(7), of(Operator.TRA, "14:12"))
        assertEquals(TrainStatus.Soon(30), of(Operator.TRA, "14:35"))
        assertEquals(TrainStatus.OnTime, of(Operator.TRA, "14:36"))
    }

    @Test
    fun operatorDifferences() {
        assertEquals(TrainStatus.None, of(Operator.THSR, "15:00")) // 高鐵不顯示準點
        assertEquals(TrainStatus.BySchedule, of(Operator.BUS, "15:00"))
        assertEquals(TrainStatus.Soon(25), of(Operator.BUS, "14:30"))
    }

    @Test
    fun otherDates() {
        val tomorrow = today.plusDays(1)
        assertEquals(TrainStatus.None, of(Operator.TRA, "00:10", tomorrow))
        assertEquals(TrainStatus.BySchedule, of(Operator.BUS, "00:10", tomorrow))
        assertEquals(TrainStatus.Departed, of(Operator.TRA, "23:00", today.minusDays(1)))
    }
}
