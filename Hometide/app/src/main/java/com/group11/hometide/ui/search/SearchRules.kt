package com.group11.hometide.ui.search

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.util.StationNameNormalizer
import java.time.DayOfWeek
import java.time.LocalDate

/** P3 的純規則（可直接單元測試）：問候語時段、標題、日期範圍、送出前檢查 */
object SearchRules {

    /** FR-SRCH-01：可選今天起 60 天內 */
    const val MAX_DAYS_AHEAD = 60L

    /** FR-SRCH-05 的時段劃分：05–11 早上、11–17 下午、17–05 晚上（文字依 home.png，實作者確認） */
    @StringRes
    fun periodRes(hour: Int): Int = when (hour) {
        in 5..10 -> R.string.period_morning
        in 11..16 -> R.string.period_afternoon
        else -> R.string.period_evening
    }

    /** FR-SRCH-05：週五「今天要回家嗎？」，其他日子「要去哪裡？」 */
    @StringRes
    fun titleRes(date: LocalDate): Int =
        if (date.dayOfWeek == DayOfWeek.FRIDAY) R.string.search_title_friday else R.string.search_title_default

    fun isSelectable(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(today) && !date.isAfter(today.plusDays(MAX_DAYS_AHEAD))

    /** FR-SRCH-01 送出前檢查（不查資料庫）：空白回 [R.string.error_station_required]，同站回 [R.string.error_same_station]，通過回 null */
    @StringRes
    fun precheck(origin: String, dest: String): Int? {
        val o = StationNameNormalizer.normalizeQuery(origin)
        val d = StationNameNormalizer.normalizeQuery(dest)
        return when {
            o.isEmpty() || d.isEmpty() -> R.string.error_station_required
            o == d -> R.string.error_same_station
            else -> null
        }
    }
}
