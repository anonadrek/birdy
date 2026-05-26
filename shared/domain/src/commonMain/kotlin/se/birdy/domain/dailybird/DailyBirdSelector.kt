package se.birdy.domain.dailybird

import kotlinx.datetime.LocalDate
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import kotlin.random.Random

class DailyBirdSelector(
    private val regionBucket: Set<String> = NORDIC_BUCKET,
    private val regionSeed: String = "NORDIC",
    private val speciesProvider: suspend () -> Map<SpeciesId, Species>,
) {
    suspend fun selectFor(date: LocalDate): DailyBird? {
        val all = speciesProvider()
        val monthKey = date.month.name.take(3).lowercase()
        val candidates =
            all.values
                .mapNotNull { species ->
                    val rawTag = species.season[monthKey] ?: return@mapNotNull null
                    val tag = rawTag.toSeasonTag() ?: return@mapNotNull null
                    val nordic = species.regions.any { it in regionBucket }
                    if (!nordic) return@mapNotNull null
                    species to tag
                }

        if (candidates.isEmpty()) return null

        val (common, rare) =
            candidates.partition { (s, _) ->
                s.abundance == Abundance.ALLMÄN || s.abundance == Abundance.MINDRE_ALLMÄN
            }

        val seed = "${date.year}-${date.monthNumber}-${date.dayOfMonth}-$regionSeed".hashCode().toLong()
        val rng = Random(seed)

        val pickCommon = rng.nextDouble() < COMMON_WEIGHT
        val bucket =
            when {
                pickCommon && common.isNotEmpty() -> common
                !pickCommon && rare.isNotEmpty() -> rare
                common.isNotEmpty() -> common
                else -> rare
            }
        val (picked, tag) = bucket[rng.nextInt(bucket.size)]
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
        const val COMMON_WEIGHT = 0.75
    }
}
