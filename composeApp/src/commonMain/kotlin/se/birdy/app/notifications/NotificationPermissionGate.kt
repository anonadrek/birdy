package se.birdy.app.notifications

/**
 * First-launch gate used by AppScaffold.
 *
 * A `true` result skips the in-app permission sheet. If the skip is because the
 * OS already granted ([systemReportsEnabled]), the caller persists
 * `pushPermissionAsked = true` — a one-way door. [systemReportsEnabled] must
 * therefore be fail-closed: an unread/unknown OS status is `false`. A true
 * default on iOS raced `getNotificationSettings` and permanently skipped
 * `requestAuthorization` on first launch.
 */
internal fun skipNotificationPermissionSheet(
    alreadyAsked: Boolean,
    systemReportsEnabled: Boolean,
): Boolean = alreadyAsked || systemReportsEnabled
