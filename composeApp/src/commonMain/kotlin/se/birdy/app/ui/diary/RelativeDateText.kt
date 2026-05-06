package se.birdy.app.ui.diary

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed interface RelativeDateText {
    val timeOfDay: String

    data class Today(
        override val timeOfDay: String,
    ) : RelativeDateText

    data class Yesterday(
        override val timeOfDay: String,
    ) : RelativeDateText

    data class WithinWeek(
        val dayOfMonth: Int,
        val month1Based: Int,
        override val timeOfDay: String,
    ) : RelativeDateText

    data class Full(
        val dayOfMonth: Int,
        val month1Based: Int,
        val year: Int,
        override val timeOfDay: String,
    ) : RelativeDateText
}

fun relativeDate(
    instant: Instant,
    now: Instant,
    tz: TimeZone,
): RelativeDateText {
    val date = instant.toLocalDateTime(tz)
    val today = now.toLocalDateTime(tz)
    val time = formatHm(date)
    val daysAgo = daysBetween(date, today)
    return when {
        daysAgo == 0L -> RelativeDateText.Today(time)
        daysAgo == 1L -> RelativeDateText.Yesterday(time)
        daysAgo in 2L..7L ->
            RelativeDateText.WithinWeek(
                dayOfMonth = date.dayOfMonth,
                month1Based = date.monthNumber,
                timeOfDay = time,
            )
        else ->
            RelativeDateText.Full(
                dayOfMonth = date.dayOfMonth,
                month1Based = date.monthNumber,
                year = date.year,
                timeOfDay = time,
            )
    }
}

private fun formatHm(dt: LocalDateTime): String {
    val h = dt.hour.toString().padStart(2, '0')
    val m = dt.minute.toString().padStart(2, '0')
    return "$h:$m"
}

private fun daysBetween(
    earlier: LocalDateTime,
    later: LocalDateTime,
): Long {
    // Negative values not expected (later >= earlier in normal use).
    val earlierEpochDay = earlier.date.toEpochDays().toLong()
    val laterEpochDay = later.date.toEpochDays().toLong()
    return laterEpochDay - earlierEpochDay
}
