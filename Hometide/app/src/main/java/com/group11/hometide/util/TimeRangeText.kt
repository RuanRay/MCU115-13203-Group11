package com.group11.hometide.util

import android.content.Context
import androidx.annotation.StringRes
import com.group11.hometide.R

/** 9.2 的時段名稱；起訖剛好符合時才顯示（6-4b 實作者確認） */
@StringRes
fun namedPeriodRes(from: String?, to: String?): Int? = when (from to to) {
    "06:00" to "12:00" -> R.string.period_morning
    "11:00" to "14:00" -> R.string.period_noon
    "12:00" to "18:00" -> R.string.period_afternoon
    "16:00" to "19:00" -> R.string.period_dusk
    "18:00" to "23:59" -> R.string.period_evening
    else -> null
}

/** 「12:00–18:00」「17:00 以後」；都沒有回 null（P4 時段晶片、確認卡） */
fun Context.timeRangeText(from: String?, to: String?): String? = when {
    from == null && to == null -> null
    to == null -> getString(R.string.time_from, from)
    else -> getString(R.string.time_range, from ?: START_OF_DAY, to)
}

/** 確認卡：「下午 12:00–18:00」，不是固定時段時只有時間 */
fun Context.periodTimeText(from: String?, to: String?): String? {
    val range = timeRangeText(from, to) ?: return null
    return namedPeriodRes(from, to)?.let { getString(R.string.period_time_range, getString(it), range) } ?: range
}

/** HH:mm 字串可直接比大小；[to] 為 null 表示不限 */
fun inTimeRange(departTime: String, from: String?, to: String?): Boolean =
    (from == null || departTime >= from) && (to == null || departTime <= to)

private const val START_OF_DAY = "00:00"
