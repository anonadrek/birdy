package se.birdy.domain.dailybird

import kotlinx.datetime.LocalDate
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.isExtinctIucnStatus
import se.birdy.content.model.Species
import kotlin.random.Random

/**
 * Picks the day's bird, the same for everyone on a given date.
 *
 * Only species reviewed as regular in Sweden can be picked: abundance "allmän" or "mindre
 * allmän" (the species curated for the app: breeders, passage migrants and winter visitors, 177
 * in release 1.3.0). The 659 species the content pipeline left at its default "ovanlig" are not
 * reviewed, and most of them never come to Sweden (8 October 2026 picked Östlig klippuggla, a bird
 * of the Middle East). The species' regions can't tell them apart: the pipeline writes the same
 * placeholder regions for every species. Never an extinct species.
 */
class DailyBirdSelector(
    private val regionBucket: Set<String> = NORDIC_BUCKET,
    private val regionSeed: String = "NORDIC",
    private val speciesProvider: suspend () -> Map<SpeciesId, Species>,
) {
    suspend fun selectFor(date: LocalDate): DailyBird? {
        val all = speciesProvider()
        val monthKey =
            date.month.name
                .take(3)
                .lowercase()
        val candidates =
            all.values
                .mapNotNull { species ->
                    // Never an extinct species: the daily bird is one to go out and find. Filtered
                    // before the pick: an extinct species in the data changes no other species' days.
                    if (isExtinctIucnStatus(species.iucnStatus)) return@mapNotNull null
                    if (species.abundance !in REGULAR_IN_SWEDEN) return@mapNotNull null
                    val rawTag = species.season[monthKey] ?: return@mapNotNull null
                    val tag = rawTag.toSeasonTag() ?: return@mapNotNull null
                    val nordic = species.regions.any { it in regionBucket }
                    if (!nordic) return@mapNotNull null
                    species to tag
                }
                // By id, so the pick doesn't depend on the order the database returns rows in (a
                // rebuilt species.db can list them in another order).
                .sortedBy { (species, _) -> species.id.raw }

        if (candidates.isEmpty()) return null

        val seed = "${date.year}-${date.monthNumber}-${date.dayOfMonth}-$regionSeed".hashCode().toLong()
        val (picked, tag) = candidates[Random(seed).nextInt(candidates.size)]
        return DailyBird(speciesId = picked.id.raw, seasonTag = tag)
    }

    private fun String.toSeasonTag(): SeasonTag? =
        when (this.lowercase()) {
            "breeding" -> SeasonTag.BREEDING
            "present" -> SeasonTag.PRESENT
            "migrating" -> SeasonTag.MIGRATING
            else -> null
        }

    companion object {
        val NORDIC_BUCKET = setOf("SE", "NO", "FI", "DK")

        /** Abundances given only to species reviewed as regular in Sweden. */
        val REGULAR_IN_SWEDEN = setOf(Abundance.ALLMÄN, Abundance.MINDRE_ALLMÄN)
    }
}
