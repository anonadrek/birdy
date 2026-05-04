package se.birdy.content

@JvmInline
value class SpeciesId(
    val raw: String,
) {
    init {
        require(raw.startsWith("Q") && raw.length > 1) { "Invalid SpeciesId: $raw" }
    }
}
