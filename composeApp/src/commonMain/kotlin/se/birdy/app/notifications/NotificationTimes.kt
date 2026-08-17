package se.birdy.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Nästa-förekomst-matte för notisscheman, delad mellan Androids WorkManager-delays
 * (via [millisUntil]) och iOS UNCalendarNotificationTrigger (LocalDateTime direkt).
 * Semantik hoistad oförändrad från NotificationSchedulerImpl (androidMain):
 * "nu == måltid" rullar framåt (>= på minuten).
 */
object NotificationTimes {
    fun nextDaily(
        now: Instant,
        zone: TimeZone,
        hour: Int,
        minute: Int,
    ): LocalDateTime {
        val local = now.toLocalDateTime(zone)
        val today = LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, hour, minute)
        return if (today.toInstant(zone) > now) {
            today
        } else {
            val tomorrow = local.date.plus(1, DateTimeUnit.DAY)
            LocalDateTime(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth, hour, minute)
        }
    }

    fun nextWeekly(
        now: Instant,
        zone: TimeZone,
        day: DayOfWeek,
        hour: Int,
        minute: Int,
    ): LocalDateTime {
        val local = now.toLocalDateTime(zone)
        val rawDays = (day.isoDayNumber - local.dayOfWeek.isoDayNumber + 7) % 7
        val days =
            if (rawDays == 0 && (local.hour > hour || (local.hour == hour && local.minute >= minute))) 7 else rawDays
        val date = local.date.plus(days, DateTimeUnit.DAY)
        return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
    }

    /** [count] på varandra följande dagliga förekomster, med start i [nextDaily]. */
    fun upcomingDaily(
        now: Instant,
        zone: TimeZone,
        hour: Int,
        minute: Int,
        count: Int,
    ): List<LocalDateTime> {
        val first = nextDaily(now, zone, hour, minute)
        return (0 until count).map { offset ->
            val date = first.date.plus(offset, DateTimeUnit.DAY)
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
        }
    }

    fun millisUntil(
        target: LocalDateTime,
        now: Instant,
        zone: TimeZone,
    ): Long = (target.toInstant(zone) - now).inWholeMilliseconds
}
