package se.birdy.app.notifications

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionGateTest {
    @Test
    fun unknownStatus_doesNotSkipSheet() {
        assertFalse(
            skipNotificationPermissionSheet(
                alreadyAsked = false,
                systemReportsEnabled = false,
            ),
        )
    }

    @Test
    fun alreadyAsked_skipsSheet() {
        assertTrue(
            skipNotificationPermissionSheet(
                alreadyAsked = true,
                systemReportsEnabled = false,
            ),
        )
    }

    @Test
    fun systemGranted_skipsSheet() {
        assertTrue(
            skipNotificationPermissionSheet(
                alreadyAsked = false,
                systemReportsEnabled = true,
            ),
        )
    }
}
