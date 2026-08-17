package se.birdy.app.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSLog
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import se.birdy.app.di.AppGraph
import se.birdy.domain.notification.NotificationScheduler
import kotlin.coroutines.resume

/**
 * iOS-schemaläggaren (spec §D): UNCalendarNotificationTrigger kräver innehåll VID
 * schemaläggning → varje schedule* räknar innehållet färskt via NotificationPayloads
 * och skrivs om vid varje foreground (installIosNotificationLifecycle). Dagens fågel
 * förschemaläggs DAILY_WINDOW dagar (deterministisk per datum → exakt innehåll);
 * recap/trofé en förekomst framåt. Payload-null ⇒ pending för den typen städas —
 * schedule* är alltså konvergent oavsett toggle-läge.
 *
 * Jobb per typ hålls så cancel* kan avbryta en pågående beräkning; ensureActive()
 * före add förhindrar att en hunnen-toggla-av-race lämnar en stale notis.
 */
class IosNotificationScheduler(
    private val graphAccessor: () -> AppGraph?,
    private val scope: CoroutineScope = MainScope(),
) : NotificationScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private var dailyJob: Job? = null
    private var recapJob: Job? = null
    private var trophyJob: Job? = null

    override fun scheduleDailyBird() {
        dailyJob?.cancel()
        dailyJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val payloads = NotificationPayloads.from(graph)
                val slots =
                    NotificationTimes.upcomingDaily(graph.clock.now(), graph.timeZone, hour = 8, minute = 0, count = DAILY_WINDOW)
                removePendingWithPrefix(ID_DAILY_PREFIX)
                slots.forEach { slot ->
                    val content = payloads.dailyBird(slot.date) ?: return@forEach
                    ensureActive()
                    add(id = "$ID_DAILY_PREFIX${slot.date}", content = content, at = slot)
                }
            }
    }

    override fun scheduleWeeklyRecap() {
        recapJob?.cancel()
        recapJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val content = NotificationPayloads.from(graph).weeklyRecap()
                if (content == null) {
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_RECAP))
                    return@launch
                }
                ensureActive()
                add(ID_RECAP, content, NotificationTimes.nextWeekly(graph.clock.now(), graph.timeZone, DayOfWeek.SUNDAY, 18, 0))
            }
    }

    override fun scheduleTrophyProgress() {
        trophyJob?.cancel()
        trophyJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val content = NotificationPayloads.from(graph).trophyProgress()
                if (content == null) {
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TROPHY))
                    return@launch
                }
                ensureActive()
                add(ID_TROPHY, content, NotificationTimes.nextWeekly(graph.clock.now(), graph.timeZone, DayOfWeek.WEDNESDAY, 9, 0))
            }
    }

    override fun cancelDailyBird() {
        dailyJob?.cancel()
        scope.launch { removePendingWithPrefix(ID_DAILY_PREFIX) }
    }

    override fun cancelStreakRiskCheck() = Unit // Android-legacy-id; har aldrig funnits på iOS

    override fun cancelWeeklyRecap() {
        recapJob?.cancel()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_RECAP))
    }

    override fun cancelTrophyProgress() {
        trophyJob?.cancel()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TROPHY))
    }

    private fun add(
        id: String,
        content: NotificationContent,
        at: LocalDateTime,
    ) {
        val unContent =
            UNMutableNotificationContent().apply {
                setTitle(content.title)
                setBody(content.body)
                setSound(UNNotificationSound.defaultSound)
                setUserInfo(mapOf(USERINFO_DEEP_LINK to content.deepLink))
            }
        val components =
            NSDateComponents().apply {
                year = at.year.toLong()
                month = at.monthNumber.toLong()
                day = at.dayOfMonth.toLong()
                hour = at.hour.toLong()
                minute = at.minute.toLong()
            }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content = unContent, trigger = trigger)
        center.addNotificationRequest(request) { error ->
            if (error != null) {
                // Enkel-arg-formen (ingen "%@" + vararg-elementet) — den 2-arg-formen är en
                // BEVISAD K/N EXC_BAD_ACCESS-krasch, se trap-kommentarerna i MapTilerKey.ios.kt
                // och IosAppGraph.kt (IosAudioBootstrap.build). localizedDescription är fri text
                // (kan innehålla "%") → %%-escapas innan den blir NSLogs format-sträng.
                NSLog("Birdy/notif: add $id failed: ${error.localizedDescription}".replace("%", "%%"))
            }
        }
    }

    private suspend fun removePendingWithPrefix(prefix: String) {
        val ids =
            suspendCancellableCoroutine<List<String>> { cont ->
                center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                    cont.resume(
                        requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(prefix) },
                    )
                }
            }
        if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    companion object {
        const val DAILY_WINDOW = 7
        const val ID_DAILY_PREFIX = "birdy_daily_bird_"
        const val ID_RECAP = "birdy_weekly_recap"
        const val ID_TROPHY = "birdy_trophy_progress"
        const val USERINFO_DEEP_LINK = "deepLink"
    }
}
