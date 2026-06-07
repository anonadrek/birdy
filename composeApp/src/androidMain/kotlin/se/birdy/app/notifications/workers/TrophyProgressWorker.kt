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
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_title
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.badges.BadgeProgressItem
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.badges.TrophyProgress
import se.birdy.app.notifications.NotificationChannels
import se.birdy.app.ui.badges.BadgeStringMap

class TrophyProgressWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            val forceForDev = inputData.getBoolean(KEY_FORCE_FOR_DEV, false)
            if (!forceForDev && !graph.userPreferences.weeklyTrophyPushEnabled.first()) return Result.success()

            val observations = graph.observationRepository.observeAll().first()
            val unlocked =
                graph.badgeRepository
                    .observeUnlocks()
                    .first()
                    .map { it.badgeId }
                    .toSet()
            val species = graph.repository.allByQid(graph.defaultLocale)
            val matchCount = graph.dailyBirdHistory?.totalMatchCount() ?: 0
            val recalc = RecalculateBadgesUseCase(zone = graph.timeZone)

            val items =
                graph.badgeCatalog.badges.map { badge ->
                    BadgeProgressItem(
                        badgeId = badge.id,
                        current = recalc.currentValue(badge.rule, observations, species, matchCount),
                        target = badge.rule.target,
                        unlocked = badge.id in unlocked,
                    )
                }
            val summary = TrophyProgress.summarize(items)
            // Quiet if there's nothing in progress to nudge toward (spec: stay silent).
            // In dev-force mode, fall back to any locked badge so the push is demoable.
            val closest =
                summary.closest
                    ?: (if (forceForDev) items.firstOrNull { !it.unlocked } else null)
                    ?: return Result.success()

            NotificationChannels.ensureCreated(applicationContext)
            val closestName = getString(BadgeStringMap.nameFor(closest.badgeId))
            val title = getString(Res.string.notification_trophy_title)
            val body =
                getString(
                    Res.string.notification_trophy_body_fmt,
                    summary.unlockedCount.toString(),
                    summary.totalCount.toString(),
                    closestName,
                    closest.current.toString(),
                    closest.target.toString(),
                )

            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("birdy://trophy"))
                    .setPackage(applicationContext.packageName)
            val pi =
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID_TROPHY_PROGRESS,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notif =
                NotificationCompat
                    .Builder(applicationContext, NotificationChannels.TROPHY_PROGRESS)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_TROPHY_PROGRESS, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("TrophyProgressWorker", "fail", t)
            Result.retry()
        }
    }

    companion object {
        const val NOTIF_ID_TROPHY_PROGRESS = 1004
        const val KEY_FORCE_FOR_DEV = "force_for_dev"
    }
}
