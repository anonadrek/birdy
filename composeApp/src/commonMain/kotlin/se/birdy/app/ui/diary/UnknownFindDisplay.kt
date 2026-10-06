package se.birdy.app.ui.diary

import se.birdy.domain.observation.Observation

/**
 * The second line of a Mina arter row: "Parus major · för 5 min sen", or only the time for a find
 * saved as unknown, which has no scientific name (it read " · för 2 min sen"). Release 1.3.0
 * Plan 3 Task 7.
 */
internal fun lifelistMetaLine(
    scientificName: String?,
    relativeTime: String,
): String = listOfNotNull(scientificName?.takeIf { it.isNotBlank() }, relativeTime).joinToString(" · ")

/**
 * Whether a find has a model confidence to show. A find saved with "Spara som okänd" was never
 * scored as any species (it is stored with confidence 0), so it showed a meaningless "0%".
 */
internal fun showsConfidence(observation: Observation): Boolean = observation.speciesId != null
