package se.birdy.app.strings

import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * Release 1.3.1 part 7 (Albin 2026-10-08): no em dash (U+2014) or en dash (U+2013) in anything a user reads.
 * Hyphens in compounds ("fält-prospekt") are fine. Covers both compose string files (strings, plurals, arrays),
 * the Android launcher label, and Kotlin string literals that are only a dash (the old "no value" placeholder).
 * The species texts have their own guard (shared/content SpeciesTextNoDashesTest) and the PDF footer is tested
 * in shared/pdf JournalPdfMetricsTest.
 */
class NoDashesInAppTextTest {
    private val dash = Regex("[\u2013\u2014]")

    @Test
    fun `no string, plural or array item has a dash`() {
        val offenders = mutableListOf<String>()
        var scanned = 0
        for (file in listOf(StringsXml.swedish(), StringsXml.english())) {
            val where = file.parentFile.name
            StringsXml.strings(file).forEach { (name, text) ->
                scanned++
                if (dash.containsMatchIn(text)) offenders += "$where/$name: $text"
            }
            StringsXml.plurals(file).forEach { (name, quantities) ->
                quantities.forEach { (quantity, text) ->
                    scanned++
                    if (dash.containsMatchIn(text)) offenders += "$where/$name[$quantity]: $text"
                }
            }
            StringsXml.arrays(file).forEach { (name, items) ->
                items.forEachIndexed { i, text ->
                    scanned++
                    if (dash.containsMatchIn(text)) offenders += "$where/$name[$i]: $text"
                }
            }
        }
        assertTrue(scanned > 200, "Parsed only $scanned texts, path or regex broken")
        assertTrue(offenders.isEmpty(), "Dashes in app strings:\n" + offenders.joinToString("\n"))
    }

    @Test
    fun `the launcher label has no dash`() {
        val res = File(repoRoot(), "androidApp/src/main/res")
        val labels = listOf("values", "values-sv").map { File(res, "$it/strings.xml") }.filter { it.isFile }
        assertTrue(labels.size == 2, "androidApp strings.xml not found under $res")
        val offenders =
            labels.flatMap { file ->
                StringsXml
                    .strings(file)
                    .filterValues { dash.containsMatchIn(it) }
                    .map { (name, text) -> "${file.parentFile.name}/$name: $text" }
            }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun `no kotlin string literal in the app is only a dash`() {
        val sources = listOf("commonMain", "androidMain", "iosMain").map { File(repoRoot(), "composeApp/src/$it/kotlin") }
        val dashOnly = Regex("\"[\u2013\u2014]\"")
        val offenders =
            sources
                .filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { i, line ->
                        if (dashOnly.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
                    }
                }
        assertTrue(offenders.isEmpty(), "Dash placeholders:\n" + offenders.joinToString("\n"))
    }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "androidApp").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repo root not found from ${System.getProperty("user.dir")}")
    }
}
