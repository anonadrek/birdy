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
    // Defaults to Swedish only so fixtures that don't care about language (pagination, early-
    // return tests) don't all need updating. The real production path, ExportJournalUseCase,
    // always passes this explicitly from the already-resolved app locale — never relies on the
    // default.
    val strings: JournalPdfStrings = JournalPdfStrings.SV,
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
