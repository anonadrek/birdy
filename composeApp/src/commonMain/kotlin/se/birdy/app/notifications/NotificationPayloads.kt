package se.birdy.app.notifications

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_listen_for_it
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_read_more
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_body
import birdy_bird_scanner.composeapp.generated.resources.notification_daily_bird_photo_credit
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
import se.birdy.app.badges.BadgeProgressItem
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.badges.TrophyProgress
import se.birdy.app.di.AppGraph
import se.birdy.app.i18n.AppStrings
import se.birdy.app.recap.WeeklyRecapBuilder
import se.birdy.app.recap.toIsoString
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.data.dailybird.DailyBirdHistoryRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.observation.ObservationRepository

/** `birdy://` deep links that AppScaffold routes. */
object BirdyDeepLinks {
    fun species(speciesId: String): String = "birdy://species/$speciesId"

    /** Audio ID (the Lyssna screen); added for the daily-bird notification in release 1.3.0. */
    const val AUDIO = "birdy://audio"

    /**
     * The weekly recap of [week] ("birdy://recap?week=2026-W41", release 1.3.0 Task 7j review): the
     * Sunday notification names the week it describes, so a tap after midnight still opens that
     * week. A link without the week (older notifications) opens the current week.
     */
    fun recap(week: WeekKey): String = "birdy://recap?week=${week.toIsoString()}"

    /** The `week` of a recap link, or null when the link has none. */
    fun recapWeek(uri: String): String? =
        uri
            .substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .firstOrNull { it.startsWith("week=") }
            ?.removePrefix("week=")
            ?.takeIf { it.isNotBlank() }
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
 * iOS still shows title and body only (a follow-up). [photoCredit] ("Foto: Derek Keats, CC BY 2.0")
 * goes with the photo wherever it is shown (Task 7e-2, legal review §2).
 */
data class NotificationContent(
    val title: String,
    val body: String,
    val deepLink: String,
    val imagePath: String? = null,
    val actions: List<NotificationAction> = emptyList(),
    val photoCredit: String? = null,
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
@Suppress("LongParameterList") // wired by three platforms; a holder object would only move the same list
class NotificationPayloads(
    private val prefs: UserPreferences,
    private val observationRepo: ObservationRepository,
    private val badgeRepo: BadgeRepository,
    private val badgeCatalog: BadgeCatalog,
    private val speciesByQid: suspend () -> Map<SpeciesId, Species>,
    private val speciesNameFor: suspend (qid: String) -> String?,
    private val selectDailyBird: (suspend (LocalDate) -> DailyBird?)?,
    /**
     * The daily-bird history the app records each day's bird in (DailyBirdTracker), so the
     * notification names the bird the app shows; see [dailyBird]. Null where there is none.
     */
    private val dailyBirdHistory: DailyBirdHistoryRepository?,
    private val dailyBirdMatchCount: suspend () -> Int,
    private val timeZone: TimeZone,
    private val clock: Clock,
    private val strings: AppStrings,
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
        val speciesId = dailyBirdSpeciesId(date) ?: return null
        val displayName = speciesNameFor(speciesId) ?: speciesId
        val speciesLink = BirdyDeepLinks.species(speciesId)
        // The photo through speciesByQid, which iOS memoises.
        val hero = heroOf(speciesByQid()[SpeciesId(speciesId)])
        return NotificationContent(
            title = strings.get(Res.string.notification_daily_bird_title_fmt, displayName),
            body = strings.get(Res.string.notification_daily_bird_body),
            deepLink = speciesLink,
            imagePath = hero?.path,
            photoCredit =
                hero?.let { strings.get(Res.string.notification_daily_bird_photo_credit, it.author, it.license) },
            actions =
                listOf(
                    NotificationAction(strings.get(Res.string.daily_bird_read_more), speciesLink),
                    NotificationAction(strings.get(Res.string.daily_bird_listen_for_it), BirdyDeepLinks.AUDIO),
                ),
        )
    }

    /**
     * The bird the app shows for [date]: the one recorded in the daily-bird history, else the
     * selector's. DailyBirdTracker records the first bird of a day and keeps it, and every save is
     * matched against it; on the day an update changes the selection (1.3.0 drops extinct species)
     * the selector alone could name another bird than the Identify hero. Nothing recorded yet (the
     * app not opened today) means the selector's bird, which the app then records too (the
     * selector is deterministic per date). Read only: recording the day stays with the tracker.
     */
    private suspend fun dailyBirdSpeciesId(date: LocalDate): String? =
        dailyBirdHistory?.speciesIdForDate(date) ?: selectDailyBird?.invoke(date)?.speciesId

    suspend fun weeklyRecap(forceForDev: Boolean = false): NotificationContent? {
        if (!forceForDev && !prefs.weeklyRecapPushEnabled.first()) return null
        val observations = observationRepo.observeAll().first()
        val unlocks = badgeRepo.observeUnlocks().first()
        val summary = WeeklyRecapBuilder(timeZone).summarize(observations, unlocks, clock.now())
        return when {
            !summary.isQuiet || forceForDev ->
                NotificationContent(
                    title = strings.get(Res.string.notification_recap_active_title),
                    body =
                        recapNotificationBody(
                            strings = strings,
                            finds = summary.observationCount,
                            newSpecies = summary.newSpeciesCount,
                        ),
                    deepLink = BirdyDeepLinks.recap(summary.week),
                )
            summary.streakAtRisk ->
                NotificationContent(
                    title = strings.get(Res.string.notification_recap_streak_title),
                    body = strings.get(Res.string.notification_recap_streak_body),
                    deepLink = BirdyDeepLinks.recap(summary.week),
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
        val closestName = strings.get(BadgeStringMap.nameFor(closest.badgeId))
        return NotificationContent(
            title = strings.get(Res.string.notification_trophy_title),
            body =
                strings.get(
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
                dailyBirdHistory = graph.dailyBirdHistory,
                dailyBirdMatchCount = { graph.dailyBirdHistory?.totalMatchCount() ?: 0 },
                timeZone = graph.timeZone,
                clock = graph.clock,
                strings = graph.strings,
            )

        /** The species' hero photo, as the hero and the strips use it. */
        fun heroOf(species: Species?): SpeciesImage? = species?.images?.firstOrNull { it.role == "hero" }
    }
}

/**
 * "3 fynd, 1 ny art. Se veckans uppslag." for the weekly recap push, with a plural per count
 * (release 1.3.0 Task 7g; the old text said "2 ny art" and "1 sightings").
 */
internal suspend fun recapNotificationBody(
    strings: AppStrings,
    finds: Int,
    newSpecies: Int,
): String =
    strings.get(
        Res.string.notification_recap_active_body_fmt,
        strings.plural(Res.plurals.recap_stats_finds, finds, finds),
        strings.plural(Res.plurals.recap_stats_new_species, newSpecies, newSpecies),
    )
