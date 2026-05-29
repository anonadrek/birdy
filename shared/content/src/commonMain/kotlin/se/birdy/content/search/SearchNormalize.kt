package se.birdy.content.search

/**
 * Normalisera text för fritextsök: dekomponera + strippa diakriter, strippa
 * apostrof-varianter, lowercase, kollapsa whitespace. Samma funktion appliceras
 * på lagrad search_text (build-tid) och på query (runtime) → symmetrisk matchning.
 */
expect fun normalizeSearch(input: String): String
