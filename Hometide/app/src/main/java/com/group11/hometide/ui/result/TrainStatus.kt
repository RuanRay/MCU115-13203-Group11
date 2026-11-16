package com.group11.hometide.ui.result

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import java.time.LocalDate
import java.time.LocalTime

/** 班次狀態（FR-RES-03、FR-BUS-04）。誤點與預估到站於任務 4-3 接即時資料後覆蓋。 */
sealed interface TrainStatus {
    data object Departed : TrainStatus
    data class Soon(val minutes: Int) : TrainStatus
    data object OnTime : TrainStatus
    data object BySchedule : TrainStatus
    data object None : TrainStatus

    companion object {
        /** 30 分鐘內發車顯示「N 分後」 */
        const val SOON_MINUTES = 30

        /**
         * 以裝置目前時間判斷：
         * - 今天：已過 → 已開出；30 分內 → N 分後；其他 → 台鐵「準點」、國道客運「依班表」、高鐵不顯示
         * - 之後的日期：國道客運「依班表」，其他不顯示
         */
        fun of(operator: Operator, departTime: String, travelDate: LocalDate, today: LocalDate, now: LocalTime): TrainStatus {
            if (travelDate.isBefore(today)) return Departed
            if (travelDate.isAfter(today)) return if (operator == Operator.BUS) BySchedule else None
            val diff = minutesOf(departTime) - (now.hour * 60 + now.minute)
            return when {
                diff < 0 -> Departed
                diff <= SOON_MINUTES -> Soon(diff)
                operator == Operator.TRA -> OnTime
                operator == Operator.BUS -> BySchedule
                else -> None
            }
        }

        fun minutesOf(hhmm: String): Int = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
    }
}

@get:StringRes
val TrainStatus.labelRes: Int?
    get() = when (this) {
        TrainStatus.Departed -> R.string.status_departed
        is TrainStatus.Soon -> R.string.status_soon
        TrainStatus.OnTime -> R.string.status_on_time
        TrainStatus.BySchedule -> R.string.status_by_schedule
        TrainStatus.None -> null
    }
