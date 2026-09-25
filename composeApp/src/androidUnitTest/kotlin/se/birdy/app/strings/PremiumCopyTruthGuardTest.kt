package se.birdy.app.strings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Once people pay, every Premium promise must be true (spec 2026-09-24 §5.4). Premium unlocks
 * the finds map, PDF export, season statistics and 7 premium badges — nothing else. Audio-ID is
 * free forever (BirdNET CC BY-NC-SA) and must never be sold as Premium.
 */
class PremiumCopyTruthGuardTest {
    private val forbidden =
        listOf(
            "molnsynk",
            "cloud sync",
            "flera foton",
            "multiple photos",
            "migrationskarta",
            "migration map",
            "läten",
            "songs",
            "säkerhetskopi",
            "back up",
            "backup",
            "ljud-id",
            "audio",
        )

    /**
     * Whole-phrase match: a forbidden phrase only counts if it is not glued to a letter/digit on
     * either side. Plain substring matching false-positives on "songs" inside the Swedish word
     * "säsongsstatistik" (season statistics — one of the four things Premium actually unlocks);
     * Java's default `\b`/`\w` are ASCII-only and treat "ä" as a non-word char, which does not
     * help here, so this checks Unicode letter/digit categories directly.
     */
    private fun containsForbiddenPhrase(
        value: String,
        phrase: String,
    ): Boolean {
        val boundary = "(?<![\\p{L}\\p{N}])${Regex.escape(phrase)}(?![\\p{L}\\p{N}])"
        return Regex(boundary, RegexOption.IGNORE_CASE).containsMatchIn(value)
    }

    @Test
    fun `premium strings never promise features that premium does not unlock`() {
        val offenders =
            listOf(StringsXml.swedish(), StringsXml.english()).flatMap { file ->
                StringsXml
                    .strings(file)
                    .filterKeys { it.startsWith("premium_") }
                    .flatMap { (key, value) ->
                        forbidden.filter { containsForbiddenPhrase(value, it) }.map { "${file.parentFile?.name}/$key: '$it'" }
                    }
            }
        assertEquals("Untrue Premium promises", emptyList<String>(), offenders)
    }
}
