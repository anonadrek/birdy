package se.birdy.domain.dailybird

/**
 * The bird suggested for a given local date.
 *
 * @param speciesId Wikidata QID (e.g., "Q25485").
 * @param seasonTag Raw season-tag from species.season[currentMonth]: "breeding" | "present" | "migrating".
 */
data class DailyBird(
    val speciesId: String,
    val seasonTag: SeasonTag,
)

enum class SeasonTag {
    PRESENT,
    BREEDING,
    MIGRATING,
}
