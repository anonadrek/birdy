package se.birdy.app.usecase

import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository
import se.birdy.datastore.UserPreferences
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeRepository
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import se.birdy.pdf.JournalPdfInput
import se.birdy.pdf.JournalPdfRenderResult

/**
 * Orchestrates "export Field Journal as PDF":
 *
 * 1. Snapshot observations + species catalog + premium badge unlocks
 * 2. Compute Stats (species-this-year, total-this-year, top-5)
 * 3. Resolve localized badge name/description via injected resolvers
 * 4. Hand the assembled [JournalPdfInput] to the platform renderer
 *
 * Premium badges are identified by the `premium_` id-prefix convention. Plan 6b3 T13
 * adds a proper `Badge.isPremium` flag; this filter is forward-compatible since the
 * canonical premium IDs (`premium_field_member` etc.) already share the prefix.
 *
 * `badgeNameResolver` / `badgeDescriptionResolver` are suspend lambdas so the Android
 * wiring can call `getString(StringResource)` from `compose-resources` without leaking
 * that dependency into commonMain.
 */
class ExportJournalUseCase(
    private val observationRepo: ObservationRepository,
    private val speciesRepo: SpeciesRepository,
    private val badgeRepo: BadgeRepository,
    private val catalog: BadgeCatalog,
    private val render: suspend (JournalPdfInput, String) -> JournalPdfRenderResult,
    private val userPreferences: UserPreferences,
    private val outputPathFactory: (timestampMs: Long) -> String,
    private val clock: Clock,
    private val timeZone: TimeZone,
    private val locale: Locale,
    private val badgeNameResolver: suspend (badgeId: String) -> String,
    private val badgeDescriptionResolver: suspend (badgeId: String) -> String,
    private val fallbackDisplayName: String = "Birdy",
) {
    suspend fun run(): JournalExportResult {
        val observations = observationRepo.observeAll().first()
        if (observations.isEmpty()) return JournalExportResult.NothingToExport

        val speciesByQid = speciesRepo.allByQid(locale)
        val now = clock.now()
        val currentYear = now.toLocalDateTime(timeZone).year

        val displayName =
            userPreferences.userName
                .first()
                .takeIf { it.isNotBlank() } ?: fallbackDisplayName

        val stats =
            JournalPdfInput.Stats(
                speciesSeenThisYear = countSpeciesThisYear(observations, currentYear),
                totalObservationsThisYear = countObservationsThisYear(observations, currentYear),
                topSpecies = topSpecies(observations, speciesByQid, limit = TOP_SPECIES_LIMIT),
            )

        val unlocks = badgeRepo.observeUnlocks().first()
        val premiumBadges =
            unlocks
                .filter { it.badgeId.startsWith(PREMIUM_BADGE_PREFIX) }
                .filter { catalog.findById(it.badgeId) != null || it.badgeId == FIELD_MEMBER_BADGE_ID }
                .map { unlock ->
                    JournalPdfInput.BadgeRef(
                        id = unlock.badgeId,
                        nameLocalized = badgeNameResolver(unlock.badgeId),
                        descriptionLocalized = badgeDescriptionResolver(unlock.badgeId),
                        unlockedAt = unlock.unlockedAt,
                    )
                }

        val input =
            JournalPdfInput(
                displayName = displayName,
                generatedAtMs = now.toEpochMilliseconds(),
                observations = observations,
                speciesByQid = speciesByQid.mapKeys { it.key.raw },
                stats = stats,
                unlockedPremiumBadges = premiumBadges,
            )

        val outputPath = outputPathFactory(now.toEpochMilliseconds())
        return when (val result = render(input, outputPath)) {
            is JournalPdfRenderResult.Success ->
                JournalExportResult.Success(
                    pdfPath = outputPath,
                    pageCount = result.pageCount,
                    sizeBytes = result.sizeBytes,
                )
            JournalPdfRenderResult.Empty -> JournalExportResult.NothingToExport
            is JournalPdfRenderResult.Failed -> JournalExportResult.Failed(result.message)
        }
    }

    private fun countSpeciesThisYear(
        observations: List<Observation>,
        year: Int,
    ): Int =
        observations
            .asSequence()
            .filter { it.capturedAt.toLocalDateTime(timeZone).year == year }
            .mapNotNull { it.speciesId }
            .toSet()
            .size

    private fun countObservationsThisYear(
        observations: List<Observation>,
        year: Int,
    ): Int = observations.count { it.capturedAt.toLocalDateTime(timeZone).year == year }

    private fun topSpecies(
        observations: List<Observation>,
        speciesByQid: Map<se.birdy.content.SpeciesId, se.birdy.content.model.Species>,
        limit: Int,
    ): List<Pair<String, Int>> =
        observations
            .asSequence()
            .mapNotNull { obs -> obs.speciesId?.takeIf { it.isNotBlank() }?.let { it to obs } }
            .groupBy({ it.first }, { it.second })
            .map { (qid, list) ->
                val name = speciesByQid[se.birdy.content.SpeciesId(qid)]?.name ?: qid
                name to list.size
            }.sortedByDescending { it.second }
            .take(limit)

    companion object {
        const val TOP_SPECIES_LIMIT = 5
        private const val PREMIUM_BADGE_PREFIX = "premium_"
        private const val FIELD_MEMBER_BADGE_ID = "premium_field_member"
    }
}
