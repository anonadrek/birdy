package se.birdy.app.notifications

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_listen_for_it
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_read_more
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_title_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_active_title
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_body
import birdy_bird_scanner.composeapp.generated.resources.notification_recap_streak_title
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_body_fmt
import birdy_bird_scanner.composeapp.generated.resources.notification_trophy_title
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_finds
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_new_species
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getPluralString
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
import se.birdy.domain.observation.ObservationRepository

/** `birdy://` deep links that AppScaffold routes. */
object BirdyDeepLinks {
    fun species(speciesId: String): String = "birdy://species/$speciesId"

    /** Audio ID (the Lyssna screen); added for the daily-bird notification in release 1.3.0. */
    const val AUDIO = "birdy://audio"
}

/** A notification button: its label and the deep link it opens. */
data class NotificationAction(
    val label: String,
    val deepLink: String,
)

/**
 * Platform-agnostic content for a single push notification. Android turns this
 * into a `NotificationCompat` build; iOS (i4) into a `UNMutableNotificationContent`.
 *
 * [imagePath] (relative to the bundled species images, see `speciesImageUri`) and [actions] are
 * the daily-bird notification's photo and buttons (release 1.3.0 Task 7d). Android shows them;
 * iOS still shows title and body only (a follow-up).
 */
data class NotificationContent(
    val title: String,
    val body: String,
    val deepLink: String,
    val imagePath: String? = null,
    val actions: List<NotificationAction> = emptyList(),
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
    /**
     * 08:00 "Dagens fågel: Sävsångare". Release 1.3.0 Task 7d: the body invites a catch instead of
     * the season line ("Här just nu." read the same nearly every day) and the notification carries
     * the species photo plus "Läs om arten" and "Lyssna efter den". A find saved from the camera, a
     * photo or a recording all count: every save goes through SaveObservationUseCase, which marks
     * the catch when the saved species is that day's bird.
     */
    suspend fun dailyBird(date: LocalDate): NotificationContent? {
        if (!prefs.dailyBirdPushEnabled.first()) return null
        val selector = selectDailyBird ?: return null
        val bird = selector(date) ?: return null
        val displayName = speciesNameFor(bird.speciesId) ?: bird.speciesId
        val speciesLink = BirdyDeepLinks.species(bird.speciesId)
        return NotificationContent(
            title = getString(Res.string.notification_daily_bird_title_fmt, displayName),
            body = getString(Res.string.notification_daily_bird_body),
            deepLink = speciesLink,
            // Through speciesByQid (memoised on iOS) rather than a new constructor parameter, so the
            // three platform wirings of this class stay as they are.
            imagePath = heroPathOf(speciesByQid()[SpeciesId(bird.speciesId)]),
            actions =
                listOf(
                    NotificationAction(getString(Res.string.daily_bird_read_more), speciesLink),
                    NotificationAction(getString(Res.string.daily_bird_listen_for_it), BirdyDeepLinks.AUDIO),
                ),
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
                        recapNotificationBody(
                            finds = summary.observationCount,
                            newSpecies = summary.newSpeciesCount,
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

        /** The species' hero photo path, as the hero and the strips use it. */
        fun heroPathOf(species: Species?): String? = species?.images?.firstOrNull { it.role == "hero" }?.path
    }
}

/**
 * "3 fynd, 1 ny art. Se veckans uppslag." for the weekly recap push, with a plural per count
 * (release 1.3.0 Task 7g; the old text said "2 ny art" and "1 sightings").
 */
internal suspend fun recapNotificationBody(
    finds: Int,
    newSpecies: Int,
): String =
    getString(
        Res.string.notification_recap_active_body_fmt,
        getPluralString(Res.plurals.recap_stats_finds, finds, finds),
        getPluralString(Res.plurals.recap_stats_new_species, newSpecies, newSpecies),
    )
