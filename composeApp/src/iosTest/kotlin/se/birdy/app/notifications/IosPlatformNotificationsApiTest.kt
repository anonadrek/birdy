package se.birdy.app.notifications

import kotlin.test.Test
import kotlin.test.assertFalse

class IosPlatformNotificationsApiTest {
    @Test
    fun areNotificationsEnabled_isFalseUntilSettingsCallback() {
        // Fail-closed default: AppScaffold persists pushPermissionAsked on a true
        // reading. Construction starts an async getNotificationSettings fetch;
        // until that lands, unknown must not look like "already granted".
        assertFalse(IosPlatformNotificationsApi().areNotificationsEnabled())
    }
}
