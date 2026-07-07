package se.birdy.domain.badge

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/**
 * ISO 8601-veckonyckel (måndag-baserad). v01 = veckan som innehåller årets första torsdag.
 * `next()` hanterar år-skifte (v53→v01-cross).
 */
data class WeekKey(
    val isoYear: Int,
    val isoWeek: Int,
) : Comparable<WeekKey> {
    override fun compareTo(other: WeekKey): Int = compareValuesBy(this, other, { it.isoYear }, { it.isoWeek })

    fun next(): WeekKey {
        val maxWeek = isoWeeksInYear(isoYear)
        return if (isoWeek >= maxWeek) {
            WeekKey(isoYear + 1, 1)
        } else {
            WeekKey(isoYear, isoWeek + 1)
        }
    }

    fun prev(): WeekKey =
        if (isoWeek > 1) {
            WeekKey(isoYear, isoWeek - 1)
        } else {
            WeekKey(isoYear - 1, isoWeeksInYear(isoYear - 1))
        }
}

data class MonthKey(
    val year: Int,
    val month: Int,
) : Comparable<MonthKey> {
    override fun compareTo(other: MonthKey): Int = compareValuesBy(this, other, { it.year }, { it.month })

    fun next(): MonthKey =
        if (month >= 12) {
            MonthKey(year + 1, 1)
        } else {
            MonthKey(year, month + 1)
        }
}

fun weekKey(
    instant: Instant,
    zone: TimeZone,
): WeekKey {
    val date = instant.toLocalDateTime(zone).date
    return weekKey(date)
}

internal fun weekKey(date: LocalDate): WeekKey {
    // ISO 8601: måndag = veckans start. Veckan tilldelas det år dess torsdag faller i.
    val weekStart = date.weekStartMonday()
    val thursday = weekStart.plusDays(3)
    val isoYear = thursday.year
    // Hitta första måndag i ISO-året (måndag i vecka 1, dvs. månd. före/på 4 jan).
    val jan4 = LocalDate(isoYear, 1, 4)
    val firstWeekStart = jan4.weekStartMonday()
    val daysFromFirst = firstWeekStart.daysUntil(weekStart)
    val isoWeek = (daysFromFirst / 7) + 1
    return WeekKey(isoYear, isoWeek)
}

fun monthKey(
    instant: Instant,
    zone: TimeZone,
): MonthKey {
    val ldt = instant.toLocalDateTime(zone)
    return MonthKey(ldt.year, ldt.monthNumber)
}

@Suppress("MagicNumber")
fun seasonOf(
    instant: Instant,
    zone: TimeZone,
): BadgeSeason {
    val month = instant.toLocalDateTime(zone).monthNumber
    return when (month) {
        12, 1, 2 -> BadgeSeason.WINTER
        3, 4, 5 -> BadgeSeason.SPRING
        6, 7, 8 -> BadgeSeason.SUMMER
        9, 10, 11 -> BadgeSeason.AUTUMN
        else -> error("unreachable month=$month")
    }
}

/**
 * Längsta consecutive-kedja i en sorted ascending sekvens. T är komparabel
 * och har en `next: (T) -> T`-funktion som genererar nästa förväntade nyckel.
 */
fun <T : Comparable<T>> longestConsecutive(
    sorted: List<T>,
    next: (T) -> T,
): Int {
    if (sorted.isEmpty()) return 0
    var longest = 1
    var current = 1
    for (i in 1 until sorted.size) {
        current = if (sorted[i] == next(sorted[i - 1])) current + 1 else 1
        if (current > longest) longest = current
    }
    return longest
}

fun longestWeeklyStreak(
    instants: List<Instant>,
    zone: TimeZone,
): Int {
    val keys = instants.map { weekKey(it, zone) }.toSet().sorted()
    return longestConsecutive(keys) { it.next() }
}

fun longestMonthlyStreak(
    instants: List<Instant>,
    zone: TimeZone,
): Int {
    val keys = instants.map { monthKey(it, zone) }.toSet().sorted()
    return longestConsecutive(keys) { it.next() }
}

/**
 * Nuvarande sammanhängande veckostreak: antal ISO-veckor i rad (bakåt) med minst en observation,
 * räknat från innevarande vecka om den har obs, annars från förra veckan om DEN har obs
 * (streaken lever men är "i fara"), annars 0.
 */
fun currentWeeklyStreak(
    instants: List<Instant>,
    zone: TimeZone,
    now: Instant,
): Int {
    if (instants.isEmpty()) return 0
    val weeks = instants.map { weekKey(it, zone) }.toSet()
    val current = weekKey(now, zone)
    var anchor =
        when {
            weeks.contains(current) -> current
            weeks.contains(current.prev()) -> current.prev()
            else -> return 0
        }
    var count = 0
    while (weeks.contains(anchor)) {
        count++
        anchor = anchor.prev()
    }
    return count
}

/**
 * Counts the longest run of consecutive Sundays (by local date) that each have at least one observation.
 * Sundays are determined by ISO weekday == DayOfWeek.SUNDAY.
 */
fun consecutiveSundaysWithObservations(
    instants: List<Instant>,
    zone: TimeZone,
): Int {
    val list =
        instants
            .map { it.toLocalDateTime(zone).date }
            .filter { it.dayOfWeek == DayOfWeek.SUNDAY }
            .distinct()
            .sorted()
    if (list.isEmpty()) return 0
    var best = 1
    var current = 1
    for (i in 1 until list.size) {
        val gap = list[i].toEpochDays() - list[i - 1].toEpochDays()
        if (gap == 7) {
            current++
            best = maxOf(best, current)
        } else {
            current = 1
        }
    }
    return best
}

// ===== Internal helpers =====

internal fun LocalDate.weekStartMonday(): LocalDate {
    val dow = dayOfWeek.isoDayNumber
    return plusDays(-(dow - 1))
}

@Suppress("MagicNumber")
internal fun LocalDate.plusDays(days: Int): LocalDate {
    val epoch = LocalDate(1970, 1, 1)
    val daysSinceEpoch = epoch.daysUntil(this) + days
    return LocalDate.fromEpochDays(daysSinceEpoch)
}

internal val DayOfWeek.isoDayNumber: Int
    get() =
        when (this) {
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
            DayOfWeek.SUNDAY -> 7
        }

/**
 * Antal ISO-veckor i ett år: 52 normalt, 53 när 1 jan är torsdag, eller om skottår med 1 jan onsdag.
 */
@Suppress("MagicNumber")
fun isoWeeksInYear(year: Int): Int {
    val jan1 = LocalDate(year, 1, 1).dayOfWeek
    val isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
    val long = jan1 == DayOfWeek.THURSDAY || (isLeap && jan1 == DayOfWeek.WEDNESDAY)
    return if (long) 53 else 52
}
