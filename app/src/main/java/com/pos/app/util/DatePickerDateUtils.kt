package com.pos.app.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Material3 DatePicker 的 selectedDateMillis / initialSelectedDateMillis 代表「UTC 日期午夜」。
 * App 內部查詢與顯示則使用使用者裝置時區的本機日期日界線。
 */
fun localDateMillisToDatePickerUtcMillis(
    timeMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val localDate = Instant.ofEpochMilli(timeMillis)
        .atZone(zoneId)
        .toLocalDate()
    return localDate.toDatePickerUtcMillis()
}

fun localTodayToDatePickerUtcMillis(
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = LocalDate.now(zoneId).toDatePickerUtcMillis()

fun datePickerUtcMillisToLocalStartOfDayMillis(
    datePickerMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    val selectedDate = Instant.ofEpochMilli(datePickerMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
    return selectedDate
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
}

private fun LocalDate.toDatePickerUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()
