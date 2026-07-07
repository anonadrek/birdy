package se.birdy.content.search

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping

/**
 * iOS actual. Mirrors the JVM actual exactly: NFD-decompose (Foundation),
 * strip combining marks (Kotlin CharCategory = JVM \p{Mn}), strip apostrophes,
 * lowercase, collapse whitespace.
 */
@OptIn(BetaInteropApi::class)
actual fun normalizeSearch(input: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val decomposed = (NSString.create(string = input) as NSString).decomposedStringWithCanonicalMapping
    return decomposed
        .filterNot { it.category == CharCategory.NON_SPACING_MARK }
        .replace(Regex("['’ʼ`]"), "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
}
