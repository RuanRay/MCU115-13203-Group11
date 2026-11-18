package com.group11.hometide.util

import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

/** FR-FAV-04：下一班的挑選 */
class NextTrainTest {

    private fun t(no: String, dep: String) = TimetableCache(
        operator = Operator.TRA, originId = "1015", destId = "4220", travelDate = "2026-11-18", trainNo = no,
        trainTypeCode = "3", trainTypeName = "自強", tripLine = null, departTime = dep, arriveTime = "23:00",
        durationMin = 60, reservedSeat = true, bikeAllowed = false, fetchedAt = 0,
    )

    private val trains = listOf(t("125", "13:50"), t("139", "14:12"), t("147", "15:20"))

    @Test
    fun pick_firstNotYetDeparted_withMinutes() {
        assertEquals(trains[1] to 7, NextTrain.pick(trains, LocalTime.of(14, 5)))
        assertEquals(trains[1] to 0, NextTrain.pick(trains, LocalTime.of(14, 12, 30))) // 同一分鐘仍算下一班
        assertNull(NextTrain.pick(trains, LocalTime.of(15, 21)))
    }

    @Test
    fun pick_skipsMalformedTime() {
        assertEquals(trains[2] to 10, NextTrain.pick(listOf(t("x", "25:99"), trains[2]), LocalTime.of(15, 10)))
    }

    @Test
    fun first_isEarliest() {
        assertEquals("125", NextTrain.first(trains.reversed())?.trainNo)
        assertNull(NextTrain.first(emptyList()))
    }
}
