package com.group11.hometide.util

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object DateUtils {
    private const val ROC_YEAR_OFFSET = 1911

    /** 民國年日期，例：2026-11-07 → "115.11.07"。 */
    fun rocDate(date: LocalDate): String =
        "%d.%02d.%02d".format(Locale.ROOT, date.year - ROC_YEAR_OFFSET, date.monthValue, date.dayOfMonth)

    /** 星期單字，例：週六 → "六"。 */
    fun weekdayNarrow(date: LocalDate): String =
        date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.TRADITIONAL_CHINESE)
}
