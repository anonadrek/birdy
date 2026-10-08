package se.birdy.app.notifications.workers

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.app.notifications.DailyBirdNotification
import se.birdy.app.notifications.withWorkerPayloads

class DailyBirdWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val today =
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .date
            val content =
                withWorkerPayloads(applicationContext) { it.dailyBird(today) } ?: return Result.success()

            // Release 1.3.0 Task 7d: photo + "Läs om arten" / "Lyssna efter den" (DailyBirdNotification).
            val picture = content.imagePath?.let { DailyBirdNotification.loadPicture(applicationContext, it) }
            val notif = DailyBirdNotification.build(applicationContext, content, picture)

            if (NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
                NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID_DAILY_BIRD, notif)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w("DailyBirdWorker", "fail", t)
            Result.retry()
        }
    }

    companion object {
        const val NOTIF_ID_DAILY_BIRD = DailyBirdNotification.NOTIFICATION_ID
    }
}
