package com.group11.hometide.ui.result

import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** FR-RES-04、FR-BUS-04（TC-14） */
class ResultSortTest {

    private fun item(no: String, dep: String, duration: Int, reserved: Boolean, price: Int?, status: TrainStatus = TrainStatus.OnTime) =
        ResultViewModel.Item(
            TimetableCache(
                operator = Operator.TRA, originId = "1080", destId = "4220", travelDate = "2026-11-16", trainNo = no,
                trainTypeCode = "3", trainTypeName = "自強", tripLine = null, departTime = dep, arriveTime = dep,
                durationMin = duration, reservedSeat = reserved, bikeAllowed = false, fetchedAt = 0,
            ),
            status,
            price?.let { FareCache(Operator.TRA, "1080", "4220", "3", FareClass.ADULT, it, false, 0) },
        )

    private val items = listOf(
        item("A", "06:00", 200, true, 739, TrainStatus.Departed),
        item("B", "07:00", 180, false, 476),
        item("C", "08:00", 190, true, null),
        item("D", "09:00", 170, true, 476),
    )

    private fun ids(option: SortOption) = ResultSort.apply(items, option).map { it.train.trainNo }

    @Test
    fun tc14_byFare_lowToHigh_missingLast_tiesKeepTimeOrder() = assertEquals(listOf("B", "D", "A", "C"), ids(SortOption.FARE))

    @Test
    fun otherOptions() {
        assertEquals(listOf("A", "B", "C", "D"), ids(SortOption.DEPART))
        assertEquals(listOf("D", "B", "C", "A"), ids(SortOption.DURATION))
        assertEquals(listOf("A", "C", "D"), ids(SortOption.RESERVED_ONLY))
        assertEquals(listOf("B", "C", "D"), ids(SortOption.UPCOMING_ONLY))
    }

    @Test
    fun busHidesReservedOnly() {
        assertFalse(SortOption.RESERVED_ONLY in SortOption.optionsFor(Operator.BUS))
        assertEquals(SortOption.entries, SortOption.optionsFor(Operator.TRA))
    }
}
