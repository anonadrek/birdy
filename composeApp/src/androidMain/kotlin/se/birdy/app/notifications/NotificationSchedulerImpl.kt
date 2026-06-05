package se.birdy.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.notifications.workers.DailyBirdWorker
import se.birdy.app.notifications.workers.WeeklyRecapWorker
import se.birdy.domain.notification.NotificationScheduler
import java.util.concurrent.TimeUnit

class NotificationSchedulerImpl(
    private val context: Context,
    private val clock: Clock = Clock.System,
    private val zone: TimeZone = TimeZone.currentSystemDefault(),
) : NotificationScheduler {
    private val workManager get() = WorkManager.getInstance(context)

    override fun scheduleDailyBird() {
        val request =
            PeriodicWorkRequestBuilder<DailyBirdWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(millisUntilNext(hour = 8, minute = 0), TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_DAILY_BIRD, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleWeeklyRecap() {
        val request =
            PeriodicWorkRequestBuilder<WeeklyRecapWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(millisUntilNextSunday(hour = 18, minute = 0), TimeUnit.MILLISECONDS)
                .build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WEEKLY_RECAP, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun cancelDailyBird() {
        workManager.cancelUniqueWork(UNIQUE_DAILY_BIRD)
    }

    override fun cancelStreakRiskCheck() {
        workManager.cancelUniqueWork(UNIQUE_STREAK_RISK)
    }

    override fun cancelWeeklyRecap() {
        workManager.cancelUniqueWork(UNIQUE_WEEKLY_RECAP)
    }

    private fun millisUntilNext(
        hour: Int,
        minute: Int,
    ): Long {
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val todayTarget = LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, hour, minute).toInstant(zone)
        val target =
            if (todayTarget > now) {
                todayTarget
            } else {
                val tomorrow = local.date.plus(1, DateTimeUnit.DAY)
                LocalDateTime(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth, hour, minute).toInstant(zone)
            }
        return (target - now).inWholeMilliseconds
    }

    private fun millisUntilNextSunday(
        hour: Int,
        minute: Int,
    ): Long {
        val now = clock.now()
        val local = now.toLocalDateTime(zone)
        val rawDays = (DayOfWeek.SUNDAY.isoDayNumber - local.dayOfWeek.isoDayNumber + 7) % 7
        val daysToSunday =
            if (rawDays == 0 && (local.hour > hour || (local.hour == hour && local.minute >= minute))) 7 else rawDays
        val targetDate = local.date.plus(daysToSunday, DateTimeUnit.DAY)
        val targetInstant =
            LocalDateTime(targetDate.year, targetDate.monthNumber, targetDate.dayOfMonth, hour, minute).toInstant(zone)
        return (targetInstant - now).inWholeMilliseconds
    }

    companion object {
        const val UNIQUE_DAILY_BIRD = "birdy_daily_bird_worker"
        const val UNIQUE_STREAK_RISK = "birdy_streak_risk_worker"
        const val UNIQUE_WEEKLY_RECAP = "birdy_weekly_recap_worker"
    }
}
