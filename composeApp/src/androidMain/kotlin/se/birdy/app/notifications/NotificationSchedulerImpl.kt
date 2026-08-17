package se.birdy.app.notifications

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import se.birdy.app.notifications.workers.DailyBirdWorker
import se.birdy.app.notifications.workers.TrophyProgressWorker
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
        val now = clock.now()
        val request =
            PeriodicWorkRequestBuilder<DailyBirdWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(
                    NotificationTimes.millisUntil(NotificationTimes.nextDaily(now, zone, hour = 8, minute = 0), now, zone),
                    TimeUnit.MILLISECONDS,
                ).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_DAILY_BIRD, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleWeeklyRecap() {
        val now = clock.now()
        val request =
            PeriodicWorkRequestBuilder<WeeklyRecapWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(
                    NotificationTimes.millisUntil(
                        NotificationTimes.nextWeekly(now, zone, DayOfWeek.SUNDAY, hour = 18, minute = 0),
                        now,
                        zone,
                    ),
                    TimeUnit.MILLISECONDS,
                ).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_WEEKLY_RECAP, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    override fun scheduleTrophyProgress() {
        val now = clock.now()
        val request =
            PeriodicWorkRequestBuilder<TrophyProgressWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(
                    NotificationTimes.millisUntil(
                        NotificationTimes.nextWeekly(now, zone, DayOfWeek.WEDNESDAY, hour = 9, minute = 0),
                        now,
                        zone,
                    ),
                    TimeUnit.MILLISECONDS,
                ).build()
        workManager.enqueueUniquePeriodicWork(UNIQUE_TROPHY_PROGRESS, ExistingPeriodicWorkPolicy.KEEP, request)
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

    override fun cancelTrophyProgress() {
        workManager.cancelUniqueWork(UNIQUE_TROPHY_PROGRESS)
    }

    companion object {
        const val UNIQUE_DAILY_BIRD = "birdy_daily_bird_worker"
        const val UNIQUE_STREAK_RISK = "birdy_streak_risk_worker"
        const val UNIQUE_WEEKLY_RECAP = "birdy_weekly_recap_worker"
        const val UNIQUE_TROPHY_PROGRESS = "birdy_trophy_progress_worker"
    }
}
