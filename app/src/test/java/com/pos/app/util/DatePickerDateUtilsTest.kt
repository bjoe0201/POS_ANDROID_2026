package com.pos.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DatePickerDateUtilsTest {

    @Test
    fun taipeiLocalStartOfDayConvertsToSameDateForDatePicker() {
        val taipei = ZoneId.of("Asia/Taipei")
        val localMay12Start = LocalDate.of(2026, 5, 12)
            .atStartOfDay(taipei)
            .toInstant()
            .toEpochMilli()

        val datePickerMillis = localDateMillisToDatePickerUtcMillis(localMay12Start, taipei)

        assertEquals(LocalDate.of(2026, 5, 12), utcDate(datePickerMillis))
    }

    @Test
    fun taipeiEarlyMorningTodayConvertsToSameDateForDatePicker() {
        val taipei = ZoneId.of("Asia/Taipei")
        val localMay12EarlyMorning = LocalDate.of(2026, 5, 12)
            .atTime(0, 30)
            .atZone(taipei)
            .toInstant()
            .toEpochMilli()

        val datePickerMillis = localDateMillisToDatePickerUtcMillis(localMay12EarlyMorning, taipei)

        assertEquals(LocalDate.of(2026, 5, 12), utcDate(datePickerMillis))
    }

    @Test
    fun datePickerDateConvertsToLocalStartOfDayInTaipei() {
        val taipei = ZoneId.of("Asia/Taipei")
        val datePickerMay12 = LocalDate.of(2026, 5, 12)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val localStartMillis = datePickerUtcMillisToLocalStartOfDayMillis(datePickerMay12, taipei)

        assertEquals(
            LocalDate.of(2026, 5, 12).atStartOfDay(taipei).toInstant().toEpochMilli(),
            localStartMillis
        )
    }

    @Test
    fun roundTripKeepsLocalDateAcrossTimeZones() {
        val zones = listOf(
            ZoneId.of("UTC"),
            ZoneId.of("Asia/Taipei"),
            ZoneId.of("America/Los_Angeles")
        )
        val dates = listOf(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 5, 12),
            LocalDate.of(2026, 12, 31)
        )

        zones.forEach { zone ->
            dates.forEach { date ->
                val localNoonMillis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
                val datePickerMillis = localDateMillisToDatePickerUtcMillis(localNoonMillis, zone)
                val localStartMillis = datePickerUtcMillisToLocalStartOfDayMillis(datePickerMillis, zone)

                assertEquals(date, utcDate(datePickerMillis))
                assertEquals(date, Instant.ofEpochMilli(localStartMillis).atZone(zone).toLocalDate())
            }
        }
    }

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
}
