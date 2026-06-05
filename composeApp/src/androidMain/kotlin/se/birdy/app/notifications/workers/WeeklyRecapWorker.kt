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
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_title
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_body
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_title
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import org.jetbrains.compose.resources.getString
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.NotificationChannels
import se.birdy.app.recap.WeeklyRecapBuilder

class WeeklyRecapWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            val forceForDev = inputData.getBoolean(KEY_FORCE_FOR_DEV, false)
            if (!forceForDev && !graph.userPreferences.weeklyRecapPushEnabled.first()) return Result.success()

            val observations = graph.observationRepository.observeAll().first()
            val unlocks = graph.badgeRepository.observeUnlocks().first()
            val builder = WeeklyRecapBuilder(graph.timeZone)
            val summary = builder.summarize(observations, unlocks, Clock.System.now())

            val (title, body) =
                when {
                    !summary.isQuiet || forceForDev ->
                        getString(Res.string.notification_recap_active_title) to
                            getString(
                                Res.string.notification_recap_active_body_fmt,
                                summary.observationCount.toString(),
                                summary.newSpeciesCount.toString(),
                            )
                    summary.streakAtRisk ->
                        getString(Res.string.notification_recap_streak_title) to
                            getString(Res.string.notification_recap_streak_body)
                    // Quiet week with no streak at risk → no push (spec §3.6)
                    else -> return Result.success()
                }

            NotificationChannels.ensureCreated(applicationContext)

            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("birdy://recap"))
                    .setPackage(applicationContext.packageName)
            val pi =
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID_WEEKLY_RECAP,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notif =
                NotificationCompat
                    .Builder(applicationContext, NotificationChannels.WEEKLY_RECAP)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_WEEKLY_RECAP, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("WeeklyRecapWorker", "fail", t)
            Result.retry()
        }
    }

    companion object {
        const val NOTIF_ID_WEEKLY_RECAP = 1003
        const val KEY_FORCE_FOR_DEV = "force_for_dev"
    }
}
