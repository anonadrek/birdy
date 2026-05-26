package se.birdy.app.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

class AndroidPlatformNotificationsApi(
    private val context: Context,
) : PlatformNotificationsApi {
    override fun areNotificationsEnabled(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    override fun needsRuntimePermission(): Boolean = android.os.Build.VERSION.SDK_INT >= 33

    override fun openAppNotificationSettings() {
        val intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(intent)
    }
}
