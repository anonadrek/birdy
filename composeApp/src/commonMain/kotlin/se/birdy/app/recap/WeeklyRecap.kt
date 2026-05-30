package se.birdy.app.recap

import se.birdy.domain.badge.WeekKey

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

/** Veckans fynd — hjälte i PlateFrame. */
data class HeroFind(
    val observationId: String,
    val speciesId: String?,
    val photoPath: String,
    val heroImagePath: String?,
    val isNewSpecies: Boolean,
)

/** Full recap för skärmen. */
data class WeeklyRecap(
    val summary: WeeklyRecapSummary,
    val hero: HeroFind?,
)
