package com.group11.hometide.util

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 出發提醒的時間規則（FR-RMD-01、FR-FAV-05） */
object ReminderRules {

    /** 提前 15／30／60 分鐘 */
    val OPTIONS = listOf(15, 30, 60)

    private const val MINUTE_MS = 60_000L
    const val KEEP_DAYS = 7L

    /** 發車時刻（裝置時區）；travelDate 為 yyyy-MM-dd、depart 為 HH:mm */
    fun departAt(travelDate: String, depart: String, zone: ZoneId = ZoneId.systemDefault()): Long =
        LocalDate.parse(travelDate).atTime(LocalTime.parse(depart)).atZone(zone).toInstant().toEpochMilli()

    fun triggerAt(departAt: Long, beforeMin: Int): Long = departAt - beforeMin * MINUTE_MS

    /** 「發車時間 − 提前分鐘」已過的選項停用 */
    fun isAvailable(departAt: Long, beforeMin: Int, now: Long): Boolean = triggerAt(departAt, beforeMin) > now

    /** 同一天的範圍 [start, end)，用來判斷「同一車次同一天」 */
    fun dayRange(departAt: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val day = java.time.Instant.ofEpochMilli(departAt).atZone(zone).toLocalDate()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** 發車超過 7 天的提醒移除 */
    fun purgeBefore(now: Long): Long = now - KEEP_DAYS * 24 * 60 * MINUTE_MS
}
