package se.birdy.content.search

/**
 * A key that sorts names in Swedish alphabetical order: a to z, then å, ä, ö (Unicode order puts
 * ä before å). Case does not matter, Danish/Norwegian æ and ø sort as ä and ö, and other accented
 * letters sort with their base letter (é with e, ü with u). Used for the encyclopedia's A-Ö sort
 * and as the last tie-break of [SearchRanking] (release 1.3.0 Task 7g review).
 */
fun swedishSortKey(text: String): String =
    buildString(text.length) {
        for (c in text.lowercase()) append(sortChar(c))
    }

// After 'z' in ASCII: '{' < '|' < '}'.
private const val AFTER_Z_1 = '{'
private const val AFTER_Z_2 = '|'
private const val AFTER_Z_3 = '}'

private fun sortChar(c: Char): Char =
    when (c) {
        'å' -> AFTER_Z_1
        'ä', 'æ' -> AFTER_Z_2
        'ö', 'ø' -> AFTER_Z_3
        in "àáâãā" -> 'a'
        in "çč" -> 'c'
        in "èéêëē" -> 'e'
        in "ìíîïī" -> 'i'
        in "ñń" -> 'n'
        in "òóôõō" -> 'o'
        in "ùúûüū" -> 'u'
        in "ýÿ" -> 'y'
        else -> c
    }
