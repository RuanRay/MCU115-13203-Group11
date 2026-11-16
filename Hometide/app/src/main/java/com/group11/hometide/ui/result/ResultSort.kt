package com.group11.hometide.ui.result

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator

/** FR-RES-04 排序與篩選；國道客運隱藏「僅對號列車」（FR-BUS-04） */
enum class SortOption(@param:StringRes val label: Int) {
    DEPART(R.string.sort_by_depart),
    DURATION(R.string.sort_by_duration),
    FARE(R.string.sort_by_fare),
    RESERVED_ONLY(R.string.filter_reserved_only),
    UPCOMING_ONLY(R.string.filter_upcoming_only),
    ;

    /** 仍依出發時間排列（「現在」線才有意義） */
    val keepsTimeOrder: Boolean get() = this != DURATION && this != FARE

    companion object {
        fun optionsFor(operator: Operator): List<SortOption> =
            if (operator == Operator.BUS) entries - RESERVED_ONLY else entries
    }
}

object ResultSort {
    /** items 需已依出發時間排序；排序為穩定排序，同值維持時間順序。依票價時沒有票價者排最後。 */
    fun apply(items: List<ResultViewModel.Item>, option: SortOption): List<ResultViewModel.Item> = when (option) {
        SortOption.DEPART -> items
        SortOption.DURATION -> items.sortedBy { it.train.durationMin }
        SortOption.FARE -> items.sortedWith(compareBy(nullsLast()) { it.fare?.price })
        SortOption.RESERVED_ONLY -> items.filter { it.train.reservedSeat }
        SortOption.UPCOMING_ONLY -> items.filter { it.status != TrainStatus.Departed }
    }
}
