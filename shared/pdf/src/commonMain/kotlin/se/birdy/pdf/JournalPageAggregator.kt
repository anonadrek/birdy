package se.birdy.pdf

/**
 * Pure-data aggregator for [JournalPdfRenderer]: groups observations by species, sorts by
 * frequency, truncates to [MAX_SPECIES_TOTAL], and paginates by [SPECIES_PER_PAGE].
 *
 * Kept in commonMain so it can be unit-tested without the Android Canvas runtime — real PDF
 * rendering itself is verified on-device because `android.graphics.pdf.PdfDocument` has no
 * JVM/Robolectric backend.
 */
object JournalPageAggregator {
    const val SPECIES_PER_PAGE = 25
    const val MAX_SPECIES_TOTAL = 50

    data class SpeciesRow(
        val qid: String,
        val nameLocalized: String,
        val scientificName: String,
        val count: Int,
        val firstSeenMs: Long,
    )

    fun computeSpeciesPages(input: JournalPdfInput): List<List<SpeciesRow>> {
        val rows =
            input.observations
                .groupBy { it.speciesId ?: "" }
                .filter { it.key.isNotBlank() }
                .map { (qid, obs) ->
                    val species = input.speciesByQid[qid]
                    val first = obs.minOf { it.capturedAt.toEpochMilliseconds() }
                    SpeciesRow(
                        qid = qid,
                        nameLocalized = species?.name ?: qid,
                        scientificName = species?.scientificName ?: "",
                        count = obs.size,
                        firstSeenMs = first,
                    )
                }.sortedByDescending { it.count }
                .take(MAX_SPECIES_TOTAL)
        return if (rows.isEmpty()) emptyList() else rows.chunked(SPECIES_PER_PAGE)
    }
}
