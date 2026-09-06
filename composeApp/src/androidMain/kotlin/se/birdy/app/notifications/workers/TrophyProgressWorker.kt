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
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.R
import se.birdy.app.notifications.AndroidNotificationPayloads
import se.birdy.app.notifications.NotificationChannels
import se.birdy.app.notifications.NotificationPayloads

class TrophyProgressWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val payloads =
                NotificationPayloads.fromGraphOr(AndroidAppGraphHolder.current) {
                    AndroidNotificationPayloads.fromContext(applicationContext)
                }
            val forceForDev = inputData.getBoolean(KEY_FORCE_FOR_DEV, false)
            val content = payloads.trophyProgress(forceForDev) ?: return Result.success()

            NotificationChannels.ensureCreated(applicationContext)

            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(content.deepLink))
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
                    .setContentTitle(content.title)
                    .setContentText(content.body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content.body))
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
