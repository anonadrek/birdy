package se.birdy.content.search

import java.text.Normalizer

actual fun normalizeSearch(input: String): String =
    Normalizer
        .normalize(input, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "") // combining marks (diakriter)
        .replace(Regex("['’ʼ`]"), "") // apostrofer strippas
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .trim()
