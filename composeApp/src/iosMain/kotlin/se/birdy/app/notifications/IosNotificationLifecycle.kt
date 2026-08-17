package se.birdy.app.notifications

import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import se.birdy.app.AppGraphHolderIos
import se.birdy.app.di.AppGraph
import se.birdy.app.iosNotificationPayloads
import kotlin.concurrent.Volatile
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Notis-livscykeln: TIDIG delegat-installation (kallstart-tap-krav) + foreground-
 * omschemaläggning (spec-beslut 3) + notis-tap → deepLinkFlow.
 *
 * Apple kräver att `UNUserNotificationCenter.delegate` sätts INNAN appen är klar med
 * att starta (före `application(_:didFinishLaunchingWithOptions:)` returnerar) för att
 * en kallstart-tap på en notis ska levereras till delegaten överhuvudtaget — sätts den
 * senare kommer tappet aldrig fram (ingen callback, ingen retry, tyst förlust).
 * `MainViewController`s `by lazy` (= FÖRSTA Compose-composition) triggas EFTER launch,
 * alltså för sent. Delegaten installeras därför separat och TIDIGT via
 * [installIosNotificationDelegate], anropad direkt från `iOSApp.swift`s `init()` —
 * innan [AppGraph] ens byggts.
 *
 * Det öppnar ett andra hål: fyras delegaten innan grafen finns, finns ingen
 * `deepLinkFlow` att skriva till än. [BirdyNotificationDelegate] löser därför grafen
 * PER CALLBACK via [AppGraphHolderIos] (tar ingen graf i konstruktorn) och stashar
 * deep-länken i fil-nivå-[pendingDeepLink] när grafen saknas; [installIosNotificationLifecycle]
 * drainar stashen (om någon) första gången grafen finns, ovanpå sina befintliga
 * observer- + reschedule-plikter.
 *
 * Observer + delegat hålls i globala vals. Delegaten MÅSTE strong-retainas här:
 * `UNUserNotificationCenter.delegate` är en weak-referens, så utan [retainedDelegate]
 * skulle instansen deallokeras direkt och ingen callback någonsin avfyras. Observer-
 * tokenet är annorlunda — `NSNotificationCenter.addObserverForName` returnerar ett
 * token som centret SJÄLVT retainer fram till en matchande `removeObserver`-anrop, så
 * [retainedObserver] behövs strikt inte för att hålla observationen vid liv; den sparas
 * ändå som belt-and-braces och som ett handtag att avregistrera med om vi någonsin behöver det.
 * [installIosNotificationDelegate] är idempotent (skapar ALDRIG en andra delegat-
 * instans, oavsett hur många gånger den anropas); [installIosNotificationLifecycle]
 * anropar den ändå defensivt som fallback ifall den tidiga Swift-installationen någon
 * gång hoppas över — normalfallet är att delegaten redan finns när denna körs.
 */
private var retainedDelegate: BirdyNotificationDelegate? = null
private var retainedObserver: Any? = null

// @Volatile: skrivs från BirdyNotificationDelegate på en GODTYCKLIG UNUserNotificationCenter-
// callback-kö (samma dokumenterade gotcha som IosPlatformNotificationsApi.enabledCache),
// läses/nollas från huvudtråden i installIosNotificationLifecycle — utan @Volatile finns
// ingen K/N-minnesmodell-garanti att skrivningen syns över tråd-gränsen.
@Volatile
private var pendingDeepLink: String? = null
private val lifecycleScope = MainScope()

/**
 * Skapar + strong-retainar [BirdyNotificationDelegate] och sätter den som
 * `UNUserNotificationCenter`s delegat. Gör INGET annat (ingen graf, ingen
 * omschemaläggning) — måste vara anropbar innan någon [AppGraph] existerar.
 * Idempotent: ett andra anrop är en no-op, aldrig en andra delegat-instans.
 */
fun installIosNotificationDelegate() {
    if (retainedDelegate != null) return
    val delegate = BirdyNotificationDelegate()
    retainedDelegate = delegate
    UNUserNotificationCenter.currentNotificationCenter().delegate = delegate
}

/**
 * Anropas EN gång från `MainViewController` efter att grafen byggts: säkerställer
 * delegaten (fallback, se filens KDoc), drainar en ev. kallstart-stashad deep-link,
 * registrerar foreground-observern och kör en initial omschemaläggning.
 */
fun installIosNotificationLifecycle(graph: AppGraph) {
    installIosNotificationDelegate()

    retainedObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            reschedule(graph)
        }

    pendingDeepLink?.let { deepLink ->
        graph.deepLinkFlow?.tryEmit(deepLink)
        pendingDeepLink = null
    }

    reschedule(graph) // även vid kallstart
}

private fun reschedule(graph: AppGraph) {
    (graph.platformNotificationsApi as? IosPlatformNotificationsApi)?.refreshStatus()
    lifecycleScope.launch {
        if (!graph.userPreferences.pushPermissionAsked.first()) return@launch
        // schedule* är konvergenta (payload-null ⇒ pending städas) → alltid alla tre.
        graph.notificationScheduler?.scheduleDailyBird()
        graph.notificationScheduler?.scheduleWeeklyRecap()
        graph.notificationScheduler?.scheduleTrophyProgress()
    }
}

/**
 * Tar INGEN graf i konstruktorn (se filens KDoc: skapas innan grafen kan finnas) —
 * löser den vid callback-tid via [AppGraphHolderIos]. Notis-tap innan grafen finns
 * stashas i [pendingDeepLink]; [installIosNotificationLifecycle] drainar den.
 */
internal class BirdyNotificationDelegate :
    NSObject(),
    UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val deepLink =
            didReceiveNotificationResponse.notification.request.content.userInfo[IosNotificationScheduler.USERINFO_DEEP_LINK] as? String
        if (deepLink != null) {
            val graph = AppGraphHolderIos.current
            if (graph != null) {
                graph.deepLinkFlow?.tryEmit(deepLink)
            } else {
                pendingDeepLink = deepLink
                // TOCTOU: grafen kan ha landat i mikrosekund-fönstret precis ovan — läs om och drena direkt.
                val lateGraph = AppGraphHolderIos.current
                if (lateGraph != null) {
                    lateGraph.deepLinkFlow?.tryEmit(deepLink)
                    pendingDeepLink = null
                }
            }
        }
        withCompletionHandler()
    }
}

/**
 * DEBUG-only: bygger färskt payload-innehåll och avfyrar det som en lokal notis ~2s
 * senare (kort nog för Albins sim-check, lång nog för appen att hinna backgroundas om
 * han vill se banner-vägen). `Platform.isDebugBinary` speglar Androids `BuildConfig.DEBUG`
 * — null i release ⇒ Settings-skärmens devTrigger-rader döljs helt (samma kontrakt som
 * Android). `produce` får både [NotificationPayloads] och grafen så anroparen (T10:s
 * wiring i `IosAppGraph.kt`) kan välja vilken payload-metod som ska demoas.
 */
@OptIn(ExperimentalNativeApi::class)
internal fun devNotifTrigger(produce: suspend (NotificationPayloads, AppGraph) -> NotificationContent?): (() -> Unit)? {
    if (!Platform.isDebugBinary) return null
    return {
        lifecycleScope.launch {
            val graph = AppGraphHolderIos.current ?: return@launch
            val content = produce(iosNotificationPayloads(graph), graph) ?: return@launch
            val unContent =
                UNMutableNotificationContent().apply {
                    setTitle(content.title)
                    setBody(content.body)
                    setUserInfo(mapOf(IosNotificationScheduler.USERINFO_DEEP_LINK to content.deepLink))
                }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(2.0, repeats = false)
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("birdy_dev_trigger", content = unContent, trigger = trigger),
                withCompletionHandler = null,
            )
        }
    }
}

/** Dagens lokala datum — [NotificationPayloads.dailyBird]s dev-trigger behöver ett konkret datum. */
internal fun todayLocalDate(): LocalDate =
    Clock.System
        .now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
