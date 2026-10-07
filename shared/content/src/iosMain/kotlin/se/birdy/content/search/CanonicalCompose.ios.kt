package se.birdy.content.search

import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.precomposedStringWithCanonicalMapping

/** Foundation's NFC, the counterpart of the decomposition [normalizeSearch] uses on iOS. */
@OptIn(BetaInteropApi::class)
internal actual fun composeCanonical(input: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return (NSString.create(string = input) as NSString).precomposedStringWithCanonicalMapping
}
