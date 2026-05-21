package se.birdy.pdf

import kotlinx.datetime.Instant
import se.birdy.content.model.Species
import se.birdy.domain.observation.Observation

data class JournalPdfInput(
    val displayName: String,
    val generatedAtMs: Long,
    val observations: List<Observation>,
    val speciesByQid: Map<String, Species>,
    val stats: Stats,
    val unlockedPremiumBadges: List<BadgeRef>,
) {
    data class Stats(
        val speciesSeenThisYear: Int,
        val totalObservationsThisYear: Int,
        val topSpecies: List<Pair<String, Int>>,
    )

    data class BadgeRef(
        val id: String,
        val nameLocalized: String,
        val descriptionLocalized: String,
        val unlockedAt: Instant,
    )
}
