package se.birdy.content.search

import java.text.Normalizer

internal actual fun composeCanonical(input: String): String = Normalizer.normalize(input, Normalizer.Form.NFC)
