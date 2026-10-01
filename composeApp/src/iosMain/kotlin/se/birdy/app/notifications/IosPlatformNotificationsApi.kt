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
 *
 * Default **false** (fail-closed). AppScaffold tolkar `areNotificationsEnabled() == true`
 * som "OS har redan beviljat" och persisterar `pushPermissionAsked = true` UTAN att
 * anropa `requestAuthorization`. Default true racade settings-callbacken vid första
 * start och hoppade permanent över permission-dialogen — appen syns då aldrig under
 * Inställningar → Notiser, så in-app-hjälplinjen är en återvändsgränd.
 * SettingsViewModel:s `?: true` är en annan fallback (null API, inte okänd status).
 */
class IosPlatformNotificationsApi : PlatformNotificationsApi {
    @Volatile private var enabledCache: Boolean = false

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
 * är LIVE sedan Task 10 wire:ade `IosNotificationScheduler` i `buildIosAppGraph()` — de
 * null-safe `?.`-anropen nedan är kvar som harmlös null-säkerhet mot den delade, nullable
 * `NotificationScheduler`-interfacetypen (samma kontrakt som Android deklarerar), inte en
 * signal om att fältet faktiskt kan vara null på iOS längre.
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
