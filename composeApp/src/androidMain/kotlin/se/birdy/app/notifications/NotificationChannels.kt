package se.birdy.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_daily_bird_description
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_daily_bird_name
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_trophy_progress_description
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_trophy_progress_name
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_weekly_recap_description
import birdy_bird_scanner.composeapp.generated.resources.notification_channel_weekly_recap_name
import se.birdy.app.i18n.AppStrings

object NotificationChannels {
    const val DAILY_BIRD = "daily_bird"
    const val WEEKLY_RECAP = "weekly_recap"
    const val TROPHY_PROGRESS = "trophy_progress"

    /**
     * Creates the three channels, or renames them, in [strings]' language. On an existing id
     * Android updates only the name and description and keeps the user's own settings, so this
     * runs on every app start, after every language change and before every notification.
     */
    suspend fun createOrUpdate(
        context: Context,
        strings: AppStrings,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService<NotificationManager>() ?: return
        mgr.createNotificationChannels(
            listOf(
                channel(
                    DAILY_BIRD,
                    strings.get(Res.string.notification_channel_daily_bird_name),
                    strings.get(Res.string.notification_channel_daily_bird_description),
                ),
                channel(
                    WEEKLY_RECAP,
                    strings.get(Res.string.notification_channel_weekly_recap_name),
                    strings.get(Res.string.notification_channel_weekly_recap_description),
                ),
                channel(
                    TROPHY_PROGRESS,
                    strings.get(Res.string.notification_channel_trophy_progress_name),
                    strings.get(Res.string.notification_channel_trophy_progress_description),
                ),
            ),
        )
    }

    private fun channel(
        id: String,
        name: String,
        description: String,
    ): NotificationChannel =
        NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
            this.description = description
        }
}
