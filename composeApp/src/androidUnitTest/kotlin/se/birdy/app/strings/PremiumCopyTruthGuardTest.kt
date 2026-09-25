package se.birdy.app.strings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Once people pay, every Premium promise must be true (spec 2026-09-24 §5.4). Premium unlocks
 * the finds map, PDF export, season statistics and 7 premium badges — nothing else. Audio-ID is
 * free forever (BirdNET CC BY-NC-SA) and must never be sold as Premium.
 *
 * The forbidden terms are per language, not one shared list, on purpose: Swedish compounds glue
 * words together (e.g. "säsongsstatistik" — a true Premium feature — contains the English
 * substring "songs"), so checking "songs" against Swedish text would false-positive. Each list
 * also uses stems ("säkerhetskopi" catches -kopiera/-kopiering/-kopia) so plain substring
 * matching still catches inflections and compounds within its own language.
 */
class PremiumCopyTruthGuardTest {
    private val forbiddenSwedish =
        listOf(
            "molnsynk",
            "flera foton",
            "migrationskarta",
            "läten",
            "säkerhetskopi",
            "ljud-id",
        )

    private val forbiddenEnglish =
        listOf(
            "cloud sync",
            "multiple photos",
            "migration map",
            "songs",
            "back up",
            "backup",
            "audio",
        )

    @Test
    fun `premium strings never promise features that premium does not unlock`() {
        val offenders =
            listOf(
                StringsXml.swedish() to forbiddenSwedish,
                StringsXml.english() to forbiddenEnglish,
            ).flatMap { (file, forbidden) ->
                StringsXml
                    .strings(file)
                    .filterKeys { it.startsWith("premium_") }
                    .flatMap { (key, value) ->
                        forbidden.filter { value.lowercase().contains(it) }.map { "${file.parentFile?.name}/$key: '$it'" }
                    }
            }
        assertEquals("Untrue Premium promises", emptyList<String>(), offenders)
    }
}
