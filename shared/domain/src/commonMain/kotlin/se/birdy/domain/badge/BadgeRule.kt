package se.birdy.domain.badge

sealed interface BadgeRule {
    val target: Int

    data class CountUniqueSpecies(
        override val target: Int,
    ) : BadgeRule

    data class WeeklyStreak(
        override val target: Int,
    ) : BadgeRule

    data class MonthlyStreak(
        override val target: Int,
    ) : BadgeRule

    data class ObservedInSeason(
        val season: BadgeSeason,
        override val target: Int,
    ) : BadgeRule

    data class ObservedInFamily(
        val family: String,
        override val target: Int,
    ) : BadgeRule

    data class ObservedWithAbundance(
        val abundance: BadgeAbundance,
        override val target: Int,
    ) : BadgeRule

    data class ObservedBeforeHour(
        val hour: Int,
        override val target: Int,
    ) : BadgeRule

    data class SpeciesAcrossSeasons(
        val seasons: Int,
        override val target: Int,
    ) : BadgeRule

    data class AudioObservationCount(
        override val target: Int,
    ) : BadgeRule

    data class ObservationsWithNote(
        val minLength: Int,
        override val target: Int,
    ) : BadgeRule

    data class ObservedInAllSeasons(
        override val target: Int,
    ) : BadgeRule

    data object Manual : BadgeRule {
        override val target: Int = 1
    }
}
