package se.birdy.content

data class SpeciesFilter(
    val abundance: Set<Abundance> = emptySet(),
    val regions: Set<String> = emptySet(),
    val activeInMonth: String? = null, // "jan".."dec" or null
)
