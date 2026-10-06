package se.birdy.content.search

import se.birdy.content.Abundance

/**
 * The names a species can be found by, in the order they count for ranking: [primary] is the name
 * in the app's language, [other] the name in the other language, then the [scientific] name.
 */
data class SearchNames(
    val primary: String,
    val other: String?,
    val scientific: String,
)

/**
 * Orders the encyclopedia's search results by how well each species matches (release 1.3.0
 * Task 7g item 3; "tal" used to put Talgoxe 13th of 18, alphabetically). From best to worst:
 *
 * 1. how the query sits in a name: the whole name, the start of a word (the name's start or after a
 *    space or hyphen), the middle of a word, or in none of the names (the search also covers the
 *    family and genus);
 * 2. which name it sits in: [SearchNames.primary], then [SearchNames.other], then the scientific;
 * 3. whether it matched as typed or only once accents were folded ("blå" finds Blåmes as typed and
 *    Bläsand only as "bla");
 * 4. how common the species is in Sweden ([Abundance], allmän first);
 * 5. the shorter matched name, which the query covers more of ("blå" gives Blåmes before Blåhake);
 * 6. the alphabet, by the primary name.
 *
 * A blank query returns the list untouched.
 */
object SearchRanking {
    fun <T> rank(
        query: String,
        items: List<T>,
        names: (T) -> SearchNames,
        abundance: (T) -> Abundance,
    ): List<T> {
        val typed = typedForm(query)
        if (typed.isEmpty()) return items
        val folded = normalizeSearch(query)
        return items
            .map { item -> item to keyFor(typed, folded, names(item), abundance(item)) }
            .sortedWith(compareBy(KEY_ORDER) { it.second })
            .map { it.first }
    }

    private enum class Level { EXACT, WORD_START, MIDDLE, NOT_IN_A_NAME }

    private data class FieldMatch(
        val level: Level,
        val field: Int,
        val foldedOnly: Boolean,
        val nameLength: Int,
    )

    private data class Key(
        val match: FieldMatch,
        val abundance: Int,
        val name: String,
    )

    private val MATCH_ORDER: Comparator<FieldMatch> = compareBy({ it.level }, { it.field }, { it.foldedOnly })

    private val KEY_ORDER: Comparator<Key> =
        compareBy<Key, FieldMatch>(MATCH_ORDER) { it.match }
            .thenBy { it.abundance }
            .thenBy { it.match.nameLength }
            .thenBy { it.name }

    private fun keyFor(
        typed: String,
        folded: String,
        names: SearchNames,
        abundance: Abundance,
    ): Key {
        val fields = listOf(names.primary, names.other, names.scientific)
        val best =
            fields
                .mapIndexedNotNull { index, name -> name?.let { matchIn(it, index, typed, folded) } }
                .minWithOrNull(MATCH_ORDER)
                ?: FieldMatch(Level.NOT_IN_A_NAME, fields.size, foldedOnly = true, nameLength = names.primary.length)
        return Key(match = best, abundance = abundance.ordinal, name = names.primary.lowercase())
    }

    private fun matchIn(
        name: String,
        field: Int,
        typed: String,
        folded: String,
    ): FieldMatch? {
        val asTyped = levelOf(typedForm(name), typed)
        val asFolded = levelOf(normalizeSearch(name), folded)
        return when {
            asTyped != null && (asFolded == null || asTyped <= asFolded) ->
                FieldMatch(asTyped, field, foldedOnly = false, nameLength = name.length)
            asFolded != null -> FieldMatch(asFolded, field, foldedOnly = true, nameLength = name.length)
            else -> null
        }
    }

    private fun levelOf(
        name: String,
        query: String,
    ): Level? =
        when {
            query.isEmpty() || query !in name -> null
            name == query -> Level.EXACT
            startsAWord(name, query) -> Level.WORD_START
            else -> Level.MIDDLE
        }

    /** True if [query] occurs at the start of [name] or right after a space or hyphen in it. */
    private fun startsAWord(
        name: String,
        query: String,
    ): Boolean =
        generateSequence(name.indexOf(query)) { previous -> name.indexOf(query, previous + 1).takeIf { it >= 0 } }
            .any { it == 0 || name[it - 1] == ' ' || name[it - 1] == '-' }

    /** Lowercase with runs of whitespace collapsed, accents kept: the text as the user typed it. */
    private fun typedForm(text: String): String = text.trim().lowercase().replace(WHITESPACE, " ")

    private val WHITESPACE = Regex("\\s+")
}
