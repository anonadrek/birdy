package se.birdy.content

/**
 * Links for a species photo's credit (release 1.3.0, legal review §2, Task 7e-2): the licence deed
 * for each licence a photo in species.db can carry, and the photo's file page on Wikimedia Commons.
 *
 * [DEED_URLS] has exactly the licences the build allows (`SpeciesValidator.ALLOWED_LICENSES`, a
 * jvmTest keeps the two in step) and the same deeds as the pipeline's
 * `tools/content-pipeline/src/birdy_fetcher/web/licenses.py` (`LICENSE_URLS`, compared by
 * `test_credits.py`). CC0 links its deed here although the pipeline's web credits leave it
 * unlinked; "Public domain" has no single deed (the file page names the exact reason, for example
 * a U.S. federal work), so it stays unlinked.
 */
object PhotoLicenses {
    /** The licence string of a public domain photo; the app shows it in the reader's language. */
    const val PUBLIC_DOMAIN = "Public domain"

    val DEED_URLS: Map<String, String?> =
        mapOf(
            "CC0" to "https://creativecommons.org/publicdomain/zero/1.0/",
            PUBLIC_DOMAIN to null,
            "CC BY 2.0" to "https://creativecommons.org/licenses/by/2.0/",
            "CC BY 3.0" to "https://creativecommons.org/licenses/by/3.0/",
            "CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/",
            "CC BY-SA 2.0" to "https://creativecommons.org/licenses/by-sa/2.0/",
            "CC BY-SA 3.0" to "https://creativecommons.org/licenses/by-sa/3.0/",
            "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
        )

    /** The deed for [license], or null when it has none (public domain) or is not a known licence. */
    fun deedUrl(license: String): String? = DEED_URLS[license]

    private const val COMMONS_FILE_PAGE = "https://commons.wikimedia.org/wiki/File:"

    /**
     * The Commons file page for [fileName] ("Parus major, Omsk, Russia.jpg"), encoded so that every
     * name opens: spaces become underscores as on Commons itself, and characters that would end or
     * split the URL ("&", "?", "#", quotes) and every non-ASCII character are percent-encoded as
     * UTF-8. Six file names in species.db contain "&", "?" or quotes; 221 are not plain ASCII.
     */
    fun commonsFilePageUrl(fileName: String): String {
        val title = fileName.trim().replace(' ', '_')
        return COMMONS_FILE_PAGE + encodeTitle(title)
    }

    private fun encodeTitle(title: String): String =
        buildString {
            for (byte in title.encodeToByteArray()) {
                val code = byte.toInt() and BYTE_MASK
                val char = code.toChar()
                if (code < ASCII_LIMIT && (char.isLetterOrDigit() || char in UNENCODED)) {
                    append(char)
                } else {
                    append('%')
                    append(HEX[code shr NIBBLE_BITS])
                    append(HEX[code and NIBBLE_MASK])
                }
            }
        }

    // Unreserved characters plus the ones MediaWiki itself leaves as they are in a title URL.
    private const val UNENCODED = "-._~(),:!*"
    private const val HEX = "0123456789ABCDEF"
    private const val BYTE_MASK = 0xFF
    private const val ASCII_LIMIT = 0x80
    private const val NIBBLE_BITS = 4
    private const val NIBBLE_MASK = 0x0F
}
