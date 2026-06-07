package se.birdy.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val DAILY_BIRD = "daily_bird"
    const val WEEKLY_RECAP = "weekly_recap"
    const val TROPHY_PROGRESS = "trophy_progress"

    fun ensureCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        if (mgr.getNotificationChannel(DAILY_BIRD) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    DAILY_BIRD,
                    "Dagens fågel",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Daily curated bird suggestion." },
            )
        }
        if (mgr.getNotificationChannel(WEEKLY_RECAP) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    WEEKLY_RECAP,
                    "Veckans recap",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Sunday-evening recap of your week." },
            )
        }
        if (mgr.getNotificationChannel(TROPHY_PROGRESS) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    TROPHY_PROGRESS,
                    "Märkesprogression",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Weekly nudge toward your next badge." },
            )
        }
    }
}
