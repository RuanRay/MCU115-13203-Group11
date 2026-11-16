package com.group11.hometide.ui.result

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.repository.LiveInfo
import java.time.LocalDate
import java.time.LocalTime

/** 班次狀態（FR-RES-03、FR-BUS-02、FR-BUS-04）。依時間判斷後，今天的查詢再以即時資料覆蓋（[withLive]）。 */
sealed interface TrainStatus {
    data object Departed : TrainStatus
    data class Soon(val minutes: Int) : TrainStatus
    data object OnTime : TrainStatus
    data object BySchedule : TrainStatus
    data object None : TrainStatus
    data class Delayed(val minutes: Int) : TrainStatus
    data class Arriving(val minutes: Int) : TrainStatus

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

        /**
         * 即時資料覆蓋：台鐵延誤 > 0 →「誤點 N 分」（優先於 N 分後）；國道客運有預估 →「N分後到站」
         * （取代依時間算的 N分後，實作者確認）。已開出維持已開出（依表定時間判斷，FR-RES-03）。
         */
        fun withLive(base: TrainStatus, live: LiveInfo?): TrainStatus = when {
            base == Departed || live == null -> base
            (live.delayMin ?: 0) > 0 -> Delayed(live.delayMin!!)
            live.etaMin != null -> Arriving(live.etaMin)
            else -> base
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
        is TrainStatus.Delayed -> R.string.status_delayed
        is TrainStatus.Arriving -> R.string.status_arriving
    }

/** 標籤上的分鐘數（沒有時為 null） */
val TrainStatus.minutes: Int?
    get() = when (this) {
        is TrainStatus.Soon -> minutes
        is TrainStatus.Delayed -> minutes
        is TrainStatus.Arriving -> minutes
        else -> null
    }
