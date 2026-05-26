package se.birdy.app.notifications

interface PlatformNotificationsApi {
    fun areNotificationsEnabled(): Boolean
    fun openAppNotificationSettings()
}
