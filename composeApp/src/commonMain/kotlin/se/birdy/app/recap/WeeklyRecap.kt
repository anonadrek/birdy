package se.birdy.app.recap

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import se.birdy.domain.badge.WeekKey
import se.birdy.domain.observation.ObservationSource

/** Räknbar veckosummering (push + skärm). Beräknas utan species-data. */
data class WeeklyRecapSummary(
    val week: WeekKey,
    val observationCount: Int,
    val newSpeciesCount: Int,
    val newBadgeIds: List<String>,
    val weeklyStreak: Int,
    val deltaVsLastWeek: Int,
    val streakAtRisk: Boolean,
) {
    val isQuiet: Boolean get() = observationCount == 0
}

/** Ett enskilt fynd i veckan: en ruta i rutnätet "Alla fynd". */
data class HeroFind(
    val observationId: String,
    val speciesId: String?,
    val photoPath: String,
    val heroImagePath: String?,
    val isNewSpecies: Boolean,
    val capturedAt: Instant,
    /** Heard finds show the species' plate photo first (their own image is a waveform). */
    val source: ObservationSource = ObservationSource.Photo,
)

/** One day of the Monday-to-Sunday strip (release 1.3.0 Task 7j, "Uppslag 1"). */
data class RecapDay(
    /** The local date in the user's zone. */
    val date: LocalDate,
    val findCount: Int,
    /** The day's latest find, whose photo the strip shows; null on a day without finds. */
    val cover: HeroFind?,
    /** A day of the current week that hasn't come yet (the recap is opened before Sunday). */
    val isFuture: Boolean,
)

/** A species first found this week, with its place in the life list (release 1.3.0 Task 7j). */
data class NewSpecies(
    val speciesId: String,
    /** 1 for the first species the user ever found, 2 for the second, and so on. */
    val lifeListNumber: Int,
    /** The find that put the species on the life list (its first find, which is this week). */
    val firstFind: HeroFind,
)

/**
 * Full recap för skärmen. `finds` = veckans alla fynd, nyaste först (tom vid tyst vecka); `days` =
 * veckans sju dagar, måndag först; `newSpecies` = veckans nya arter, senast tillkomna först.
 */
data class WeeklyRecap(
    val summary: WeeklyRecapSummary,
    val finds: List<HeroFind>,
    val days: List<RecapDay> = emptyList(),
    val newSpecies: List<NewSpecies> = emptyList(),
)
