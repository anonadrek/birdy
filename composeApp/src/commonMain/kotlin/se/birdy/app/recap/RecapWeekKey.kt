package se.birdy.app.recap

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.badge.isoWeeksInYear

internal const val DAYS_IN_WEEK = 7

/** ISO week 1 is the week with 4 January in it. */
private const val ISO_WEEK_ONE_DAY = 4

private val IsoWeekPattern = Regex("""(\d{4})-W(\d{2})""")

/**
 * "2026-W41": the ISO week a recap describes, as the Sunday notification's link carries it
 * (release 1.3.0 Task 7j review). The notification describes the week it was sent in; opened after
 * midnight the recap must still show that week, not the new and empty one.
 */
fun WeekKey.toIsoString(): String = "$isoYear-W${isoWeek.toString().padStart(2, '0')}"

/** The week in an ISO week key ("2026-W41"), or null for anything else (no key, an old or broken link). */
fun parseIsoWeekKey(text: String?): WeekKey? {
    val match = text?.let { IsoWeekPattern.matchEntire(it) } ?: return null
    val year = match.groupValues[1].toInt()
    val week = match.groupValues[2].toInt()
    return WeekKey(year, week).takeIf { week in 1..isoWeeksInYear(year) }
}

/** The Monday of this ISO week (week 1 is the week with 4 January in it). */
fun WeekKey.monday(): LocalDate {
    val jan4 = LocalDate(isoYear, 1, ISO_WEEK_ONE_DAY)
    val firstMonday = jan4.minus(jan4.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
    return firstMonday.plus((isoWeek - 1) * DAYS_IN_WEEK, DateTimeUnit.DAY)
}
