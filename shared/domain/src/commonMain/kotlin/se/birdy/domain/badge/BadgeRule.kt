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

    data class ObservedBeforeHour(
        val hour: Int,
        override val target: Int,
    ) : BadgeRule

    /** Observation captured locally within [startHour, endHourExclusive). */
    data class ObservedInHourRange(
        val startHour: Int,
        val endHourExclusive: Int,
        override val target: Int,
    ) : BadgeRule

    /** N consecutive Sundays with at least one observation each. */
    data class SundayStreak(
        override val target: Int,
    ) : BadgeRule

    /** N distinct dates where the saved observation matched that day's Daily Bird. */
    data class DailyBirdMatches(
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

    /** Distinct species observed whose taxonomy.family is in [families]. */
    data class ObservedInFamilyGroup(
        val families: Set<String>,
        override val target: Int,
    ) : BadgeRule

    /** Number of distinct taxonomy.family among observed species. */
    data class CountDistinctFamilies(
        override val target: Int,
    ) : BadgeRule

    /** Number of distinct taxonomy.iocOrder among observed species. */
    data class CountDistinctOrders(
        override val target: Int,
    ) : BadgeRule

    /** Distinct species observed whose iucnStatus is red-listed (NT/VU/CR). */
    data class ObservedRedListed(
        override val target: Int,
    ) : BadgeRule

    data object Manual : BadgeRule {
        override val target: Int = 1
    }
}
