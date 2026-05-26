package se.birdy.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

object NotificationChannels {
    const val DAILY_BIRD = "daily_bird"
    const val STREAK_RISK = "streak_risk"

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
        if (mgr.getNotificationChannel(STREAK_RISK) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    STREAK_RISK,
                    "Streak-risk",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "Sunday evening nudge when your streak is at risk." },
            )
        }
    }
}
