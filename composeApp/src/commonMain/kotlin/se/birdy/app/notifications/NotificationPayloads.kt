package se.birdy.app.notifications

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_breeding
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_migrating
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body_present
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_title_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_title
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_body
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_title
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_title
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import se.birdy.app.badges.BadgeProgressItem
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.badges.TrophyProgress
import se.birdy.app.di.AppGraph
import se.birdy.app.recap.WeeklyRecapBuilder
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import se.birdy.domain.observation.ObservationRepository

/**
 * Platform-agnostic content for a single push notification. Android turns this
 * into a `NotificationCompat` build; iOS (i4) into a `UNMutableNotificationContent`.
 */
data class NotificationContent(
    val title: String,
    val body: String,
    val deepLink: String,
)

/**
 * Builds notification CONTENT (title/body/deep-link) for the three push types.
 *
 * Hoisted VERBATIM out of the three Android `CoroutineWorker`s (`DailyBirdWorker`,
 * `WeeklyRecapWorker`, `TrophyProgressWorker`) — same res keys, same decision order,
 * same deep links — so iOS (i4) can reuse the exact same decision logic via
 * `UNCalendarNotificationTrigger` instead of WorkManager. The workers now call
 * through [from] and stay thin shells around WorkManager/NotificationCompat plumbing.
 *
 * Null return = "no notification" — exactly the `Result.success()`-without-notify
 * paths the workers had before this hoist (disabled toggle, no candidate, quiet
 * week with no streak risk, nothing in progress toward a badge).
 */
class NotificationPayloads(
    private val prefs: UserPreferences,
    private val observationRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val badgeCatalog: BadgeCatalog,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val speciesNameFor: suspend (qid: String) -> String?,
    private val selectDailyBird: (suspend (LocalDate) -> DailyBird?)?,
    private val dailyBirdMatchCount: suspend () -> Int,
    private val timeZone: TimeZone,
    private val clock: Clock,
) {
    suspend fun dailyBird(date: LocalDate): NotificationContent? {
        if (!prefs.dailyBirdPushEnabled.first()) return null
        val selector = selectDailyBird ?: return null
        val bird = selector(date) ?: return null
        val displayName = speciesNameFor(bird.speciesId) ?: bird.speciesId
        return NotificationContent(
            title = getString(Res.string.notification_daily_bird_title_fmt, displayName),
            body = getString(seasonBodyRes(bird.seasonTag)),
            deepLink = "birdy://species/${bird.speciesId}",
        )
    }

    suspend fun weeklyRecap(forceForDev: Boolean = false): NotificationContent? {
        if (!forceForDev && !prefs.weeklyRecapPushEnabled.first()) return null
        val observations = observationRepo.observeAll().first()
        val unlocks = badgeRepo.observeUnlocks().first()
        val summary = WeeklyRecapBuilder(timeZone).summarize(observations, unlocks, clock.now())
        return when {
            !summary.isQuiet || forceForDev ->
                NotificationContent(
                    title = getString(Res.string.notification_recap_active_title),
                    body =
                        getString(
                            Res.string.notification_recap_active_body_fmt,
                            summary.observationCount.toString(),
                            summary.newSpeciesCount.toString(),
                        ),
                    deepLink = "birdy://recap",
                )
            summary.streakAtRisk ->
                NotificationContent(
                    title = getString(Res.string.notification_recap_streak_title),
                    body = getString(Res.string.notification_recap_streak_body),
                    deepLink = "birdy://recap",
                )
            // Quiet week with no streak at risk → no push (spec §3.6)
            else -> null
        }
    }

    suspend fun trophyProgress(forceForDev: Boolean = false): NotificationContent? {
        if (!forceForDev && !prefs.weeklyTrophyPushEnabled.first()) return null
        val observations = observationRepo.observeAll().first()
        val unlocked =
            badgeRepo
                .observeUnlocks()
                .first()
                .map { it.badgeId }
                .toSet()
        val species = speciesByQid()
        val matchCount = dailyBirdMatchCount()
        val recalc = RecalculateBadgesUseCase(zone = timeZone)
        val items =
            badgeCatalog.badges.map { badge ->
                BadgeProgressItem(
                    badgeId = badge.id,
                    current = recalc.currentValue(badge.rule, observations, species, matchCount),
                    target = badge.rule.target,
                    unlocked = badge.id in unlocked,
                )
            }
        val summary = TrophyProgress.summarize(items)
        // Quiet if there's nothing in progress to nudge toward (spec: stay silent).
        // In dev-force mode, fall back to any locked badge so the push is demoable.
        val closest =
            summary.closest
                ?: (if (forceForDev) items.firstOrNull { !it.unlocked } else null)
                ?: return null
        val closestName = getString(BadgeStringMap.nameFor(closest.badgeId))
        return NotificationContent(
            title = getString(Res.string.notification_trophy_title),
            body =
                getString(
                    Res.string.notification_trophy_body_fmt,
                    summary.unlockedCount.toString(),
                    summary.totalCount.toString(),
                    closestName,
                    closest.current.toString(),
                    closest.target.toString(),
                ),
            deepLink = "birdy://trophy",
        )
    }

    private fun seasonBodyRes(tag: SeasonTag) =
        when (tag) {
            SeasonTag.BREEDING -> Res.string.notification_daily_bird_body_breeding
            SeasonTag.PRESENT -> Res.string.notification_daily_bird_body_present
            SeasonTag.MIGRATING -> Res.string.notification_daily_bird_body_migrating
        }

    companion object {
        fun from(graph: AppGraph): NotificationPayloads =
            NotificationPayloads(
                prefs = graph.userPreferences,
                observationRepo = graph.observationRepository,
                badgeRepo = graph.badgeRepository,
                badgeCatalog = graph.badgeCatalog,
                speciesByQid = { graph.repository.allByQid(graph.defaultLocale) },
                speciesNameFor = { qid ->
                    graph.repository
                        .getById(SpeciesId(qid), graph.defaultLocale)
                        .first()
                        ?.name
                },
                selectDailyBird = graph.selectDailyBird,
                dailyBirdMatchCount = { graph.dailyBirdHistory?.totalMatchCount() ?: 0 },
                timeZone = graph.timeZone,
                clock = graph.clock,
            )

        /**
         * Prefer the live [AppGraph] when MainActivity has published one; otherwise
         * build a standalone instance. WorkManager workers must use this — a null
         * graph is the common overnight cold-start case, not a reason to skip the
         * notification.
         */
        suspend fun fromGraphOr(
            graph: AppGraph?,
            standalone: suspend () -> NotificationPayloads,
        ): NotificationPayloads = if (graph != null) from(graph) else standalone()
    }
}
