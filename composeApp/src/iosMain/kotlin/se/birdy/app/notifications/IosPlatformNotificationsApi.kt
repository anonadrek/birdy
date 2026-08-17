package se.birdy.app.notifications

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import se.birdy.app.di.AppGraph
import kotlin.concurrent.Volatile

/**
 * iOS-actual för PlatformNotificationsApi. getNotificationSettings är async-callback
 * men interfacet är synkront → auth-statusen CACHAS och uppdateras vid init, vid
 * varje foreground (Task 10:s lifecycle-pass) och efter permission-request.
 * Default true = samma optimistiska fallback som SettingsViewModel redan använder.
 */
class IosPlatformNotificationsApi : PlatformNotificationsApi {
    @Volatile private var enabledCache: Boolean = true

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            enabledCache =
                settings?.authorizationStatus == UNAuthorizationStatusAuthorized ||
                settings?.authorizationStatus == UNAuthorizationStatusProvisional
        }
    }

    override fun areNotificationsEnabled(): Boolean = enabledCache

    override fun openAppNotificationSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
        }
    }

    override fun needsRuntimePermission(): Boolean = true
}

/**
 * Fyrar iOS-notis-permission-dialogen — spegel av `MainActivity`s `requestPermLauncher`-
 * callback (persist `pushPermissionAsked` + schemalägg vid grant). `graph.notificationScheduler`
 * är null på iOS fram till Task 10 wire:ar `IosNotificationScheduler` — de null-safe
 * `?.`-anropen nedan är ett avsiktligt mellanläge (pre-flight-tabellens T9↔T10-post),
 * inte en bugg att "fixa" här.
 *
 * `requestAuthorizationWithOptions`s completion handler anländer på en godtycklig kö
 * (inte huvudtråden) → [MainScope]`.launch` hoppar till main för prefs-skrivningen
 * (suspend) + graf-läsningen, precis som [se.birdy.app.location.IosLocationPermissionRequester]
 * gör för platsbehörigheten.
 *
 * Android har ytterligare ett `notificationScheduler?.cancelStreakRiskCheck()`-anrop i samma
 * callback-gren; det utelämnas medvetet här — Task 10:s `IosNotificationScheduler` definierar
 * `cancelStreakRiskCheck()` som en Unit-no-op eftersom streak-at-risk-notisen aldrig har haft
 * en iOS-motsvarighet (redan noterat som en känd, ofarlig asymmetri i Task 7/8:s ledger), så
 * mirroring den skulle bara vara brus.
 */
object IosNotificationPermission {
    private val scope = MainScope()

    fun request(graphAccessor: () -> AppGraph?) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                graph.userPreferences.setPushPermissionAsked(true)
                (graph.platformNotificationsApi as? IosPlatformNotificationsApi)?.refreshStatus()
                if (granted) {
                    graph.notificationScheduler?.scheduleDailyBird()
                    graph.notificationScheduler?.scheduleWeeklyRecap()
                    graph.notificationScheduler?.scheduleTrophyProgress()
                }
            }
        }
    }
}
