package com.group11.hometide.ui.profile

import androidx.annotation.StringRes
import com.group11.hometide.R
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToLong

/** FR-USG-04 統計區間（P6 spRange 的順序） */
enum class StatRange(@param:StringRes val label: Int) {
    TODAY(R.string.stat_range_today),
    WEEK(R.string.stat_range_week),
    ALL(R.string.stat_range_all),
}

/** FR-USG-04 的純規則（可直接單元測試） */
object UsageRules {

    /** 區間起點：今天 00:00、本週一 00:00、全部為 0 */
    fun rangeStart(range: StatRange, now: LocalDateTime, zone: ZoneId): Long = when (range) {
        StatRange.TODAY -> now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        StatRange.WEEK -> now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
        StatRange.ALL -> 0L
    }

    /** 分鐘，四捨五入 */
    fun minutes(ms: Long): Long = (ms / MS_PER_MIN.toDouble()).roundToLong()

    /** 長條比例＝該頁時間／最長頁時間（0～100） */
    fun barPercent(ms: Long, maxMs: Long): Int = if (maxMs <= 0) 0 else (ms * PERCENT / maxMs).toInt()

    /** 頁面名稱對照（FR-USG-04；分頁改為 Fragment，國道為實作者確認）；對照不到回 null，畫面直接顯示類別名稱 */
    @StringRes
    fun pageLabel(pageName: String): Int? = when (pageName) {
        "SearchFragment", "SearchActivity" -> R.string.nav_search
        "NextTrainFragment", "NextTrainActivity" -> R.string.nav_next
        "HighwayFragment" -> R.string.nav_highway
        "ResultActivity" -> R.string.page_result
        "MyRoutesFragment", "MyRoutesActivity" -> R.string.nav_routes
        "ProfileFragment", "ProfileActivity" -> R.string.nav_profile
        "FeedbackActivity" -> R.string.page_feedback
        "LoginActivity" -> R.string.page_login
        "RegisterActivity" -> R.string.page_register
        else -> null
    }

    private const val MS_PER_MIN = 60_000L
    private const val PERCENT = 100
}
