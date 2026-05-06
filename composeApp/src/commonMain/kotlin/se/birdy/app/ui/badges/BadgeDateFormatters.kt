package se.birdy.app.ui.badges

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale

private val swedishMonths =
    listOf(
        "jan",
        "feb",
        "mar",
        "apr",
        "maj",
        "jun",
        "jul",
        "aug",
        "sep",
        "okt",
        "nov",
        "dec",
    )
private val englishMonths =
    listOf(
        "Jan",
        "Feb",
        "Mar",
        "Apr",
        "May",
        "Jun",
        "Jul",
        "Aug",
        "Sep",
        "Oct",
        "Nov",
        "Dec",
    )

fun formatBadgeShortDate(
    instant: Instant,
    zone: TimeZone,
    locale: Locale,
): String {
    val ldt: LocalDateTime = instant.toLocalDateTime(zone)
    val months = if (locale == Locale.SV) swedishMonths else englishMonths
    val month = months[ldt.monthNumber - 1]
    return when (locale) {
        Locale.SV -> "${ldt.dayOfMonth} $month"
        Locale.EN -> "$month ${ldt.dayOfMonth}"
    }
}

fun formatBadgeFullDate(
    instant: Instant,
    zone: TimeZone,
    locale: Locale,
): String {
    val ldt = instant.toLocalDateTime(zone)
    val months = if (locale == Locale.SV) swedishMonths else englishMonths
    val month = months[ldt.monthNumber - 1]
    return when (locale) {
        Locale.SV -> "${ldt.dayOfMonth} $month ${ldt.year}"
        Locale.EN -> "$month ${ldt.dayOfMonth}, ${ldt.year}"
    }
}
