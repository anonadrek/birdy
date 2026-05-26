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
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_breeding
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_migrating
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_present
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_title_fmt
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import se.birdy.app.R
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.notifications.NotificationChannels
import se.birdy.content.SpeciesId
import se.birdy.domain.dailybird.SeasonTag

class DailyBirdWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val graph = AndroidAppGraphHolder.current ?: return Result.success()
            if (!graph.userPreferences.dailyBirdPushEnabled.first()) return Result.success()

            val selector = graph.selectDailyBird ?: return Result.success()
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val bird = selector(today) ?: return Result.success()

            NotificationChannels.ensureCreated(applicationContext)

            val species = graph.repository
                .getById(SpeciesId(bird.speciesId), graph.defaultLocale)
                .first()
            val displayName = species?.name ?: bird.speciesId

            val title = getString(Res.string.notification_daily_bird_title_fmt, displayName)
            val body = getString(seasonBodyRes(bird.seasonTag))

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("birdy://species/${bird.speciesId}"))
                .setPackage(applicationContext.packageName)
            val pi = PendingIntent.getActivity(
                applicationContext,
                NOTIF_ID_DAILY_BIRD,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.DAILY_BIRD)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_DAILY_BIRD, notif)
            }
            Result.success()
        } catch (t: Throwable) {
            Log.w("DailyBirdWorker", "fail", t)
            Result.retry()
        }
    }

    private fun seasonBodyRes(tag: SeasonTag) = when (tag) {
        SeasonTag.BREEDING -> Res.string.notification_daily_bird_body_breeding
        SeasonTag.PRESENT -> Res.string.notification_daily_bird_body_present
        SeasonTag.MIGRATING -> Res.string.notification_daily_bird_body_migrating
    }

    companion object {
        const val NOTIF_ID_DAILY_BIRD = 1001
    }
}
