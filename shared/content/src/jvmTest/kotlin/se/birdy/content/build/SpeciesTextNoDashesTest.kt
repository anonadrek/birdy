package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import se.birdy.content.cleanSpeciesText
import java.nio.file.Path

/**
 * Release 1.3.1 part 7 (Albin 2026-10-08): no em dash (U+2014) or en dash (U+2013) in any species text the app
 * shows, read exactly as the app reads it (description, migration and marginalia through [cleanSpeciesText], which
 * hides a first-line heading). A pipeline run that brings dashes back fails here instead of reaching the app; fix
 * new texts with `birdy-fetcher app-dashes` (tools/content-pipeline).
 */
class SpeciesTextNoDashesTest {
    private val dash = Regex("[\u2013\u2014]")

    @Test
    fun `no species text the app shows has a dash`() {
        val all = SpeciesYamlParser().parseAll(Path.of("species")).map { it.second }
        assertTrue(all.size > 800, "Parsed only ${all.size} species")
        val offenders =
            all.flatMap { yaml ->
                listOf("description" to yaml.description, "migration" to yaml.migration, "marginalia" to yaml.marginalia)
                    .flatMap { (field, texts) ->
                        texts.mapNotNull { (lang, raw) ->
                            val shown = raw?.let { cleanSpeciesText(it) } ?: return@mapNotNull null
                            val line = shown.lineSequence().firstOrNull { dash.containsMatchIn(it) } ?: return@mapNotNull null
                            "${yaml.id} $field.$lang: ${line.take(120)}"
                        }
                    }
            }
        assertTrue(offenders.isEmpty(), "${offenders.size} species texts with a dash:\n" + offenders.joinToString("\n"))
    }
}
