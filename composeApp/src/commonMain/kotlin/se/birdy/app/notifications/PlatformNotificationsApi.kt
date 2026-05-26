package se.birdy.app.notifications

interface PlatformNotificationsApi {
    fun areNotificationsEnabled(): Boolean

    fun openAppNotificationSettings()

    /**
     * True on Android 13 (API 33) and above, where POST_NOTIFICATIONS is a
     * runtime permission that must be requested. False on older Android and
     * non-Android targets — in those cases notifications are granted at
     * install time and the runtime sheet should not be shown.
     */
    fun needsRuntimePermission(): Boolean
}
