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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.AndroidNotificationPayloads
import se.birdy.app.notifications.NotificationChannels
import se.birdy.app.notifications.NotificationPayloads

class DailyBirdWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val payloads =
                NotificationPayloads.fromGraphOr(AndroidAppGraphHolder.current) {
                    AndroidNotificationPayloads.fromContext(applicationContext)
                }
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            val content = payloads.dailyBird(today) ?: return Result.success()

            NotificationChannels.ensureCreated(applicationContext)

            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(content.deepLink))
                    .setPackage(applicationContext.packageName)
            val pi =
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID_DAILY_BIRD,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notif =
                NotificationCompat
                    .Builder(applicationContext, NotificationChannels.DAILY_BIRD)
                    .setSmallIcon(R.drawable.ic_launcher_monochrome)
                    .setContentTitle(content.title)
                    .setContentText(content.body)
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

    companion object {
        const val NOTIF_ID_DAILY_BIRD = 1001
    }
}
