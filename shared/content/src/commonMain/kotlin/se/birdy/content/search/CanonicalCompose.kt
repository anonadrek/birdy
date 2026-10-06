package se.birdy.content.search

/**
 * Unicode NFC: "a" + U+030A COMBINING RING ABOVE becomes "å". Search ranking compares names and the
 * query "as typed" (accents kept), and a keyboard or a paste can deliver the decomposed form.
 */
internal expect fun composeCanonical(input: String): String
