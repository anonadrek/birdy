package se.birdy.app.notifications.workers

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.notification_streak_risk_body
import birdy_bird_scanner.composeapp.generated.resources.notification_streak_risk_title
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.NotificationChannels
import se.birdy.domain.badge.longestWeeklyStreak
import se.birdy.domain.badge.weekKey

class StreakRiskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            if (!graph.userPreferences.streakRiskPushEnabled.first()) return Result.success()

            val now = Clock.System.now()
            val zone = TimeZone.currentSystemDefault()

            val observations = graph.observationRepository.observeAll().first()

            val nowKey = weekKey(now, zone)
            val alreadyObservedThisWeek = observations.any { weekKey(it.savedAt, zone) == nowKey }
            if (alreadyObservedThisWeek) return Result.success()

            val streak = longestWeeklyStreak(observations.map { it.savedAt }, zone)
            if (streak < 2) return Result.success()

            NotificationChannels.ensureCreated(applicationContext)

            val title = getString(Res.string.notification_streak_risk_title)
            val body = getString(Res.string.notification_streak_risk_body)

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("birdy://identify"))
                .setPackage(applicationContext.packageName)
            val pi = PendingIntent.getActivity(
                applicationContext,
                NOTIF_ID_STREAK_RISK,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.STREAK_RISK)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_STREAK_RISK, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("StreakRiskWorker", "fail", t)
            Result.retry()
        }
    }

    companion object {
        const val NOTIF_ID_STREAK_RISK = 1002
    }
}
