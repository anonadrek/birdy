package se.birdy.content.search

import java.text.Normalizer

// Compiled once, not on every keystroke.
private val COMBINING_MARKS = Regex("\\p{Mn}+") // diakriter
private val APOSTROPHES = Regex("['’ʼ`]")
private val WHITESPACE = Regex("\\s+")

actual fun normalizeSearch(input: String): String =
    Normalizer
        .normalize(input, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace(APOSTROPHES, "") // apostrofer strippas
        .lowercase()
        .replace(WHITESPACE, " ")
        .trim()
