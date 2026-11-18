package com.group11.hometide.util

import com.group11.hometide.data.local.entity.TimetableCache
import java.time.LocalTime

/** 車票夾的下一班（FR-FAV-04、FR-SRCH-04） */
sealed interface NextTrain {
    data object Loading : NextTrain

    /** [fare] 以預設乘客計算的總價（FR-SET-02）；票價取不到時為 null */
    data class Next(val train: TimetableCache, val minutes: Int, val fare: FareTotal?) : NextTrain

    /** 今日已無班次，明天首班 [departTime] */
    data class Tomorrow(val departTime: String) : NextTrain

    /** 今日已無班次，也查不到明天的班次 */
    data object NoneToday : NextTrain

    /** 無網路且無快取 */
    data object Offline : NextTrain

    companion object {
        /** 第一班出發時間不早於 [now] 的班次與剩餘分鐘；[trains] 需依出發時間排序 */
        fun pick(trains: List<TimetableCache>, now: LocalTime): Pair<TimetableCache, Int>? {
            val nowMin = now.hour * 60 + now.minute
            return trains.firstNotNullOfOrNull { t ->
                val dep = runCatching { LocalTime.parse(t.departTime) }.getOrNull() ?: return@firstNotNullOfOrNull null
                val m = dep.hour * 60 + dep.minute - nowMin
                if (m >= 0) t to m else null
            }
        }

        /** 明天首班（依出發時間最早者） */
        fun first(trains: List<TimetableCache>): TimetableCache? = trains.minByOrNull { it.departTime }
    }
}
