package se.birdy.app.strings

import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.1 part 7 (Albin 2026-10-08): no em dash (U+2014) or en dash (U+2013) in anything a user reads.
 * Hyphens in compounds ("fält-prospekt") are fine. Covers both compose string files (strings, plurals, arrays),
 * the Android launcher label, and Kotlin string literals that are only a dash (the old "no value" placeholder).
 * The species texts have their own guard (shared/content SpeciesTextNoDashesTest) and the PDF footer is tested
 * in shared/pdf JournalPdfMetricsTest.
 *
 * Review fix round (1.3.1 del 7): the dash pattern for the string-resource checks also catches an XML character
 * reference (`&#8212;`, `&#x2013;`) and a literal `\u2014`-style escape written out as text — both are ways a
 * dash can survive compose-resources' own unescaping and still reach a user.
 */
class NoDashesInAppTextTest {
    private val dash = Regex("[\u2013\u2014]|&#(8211|8212|x201[34]);|\\\\u201[34]", RegexOption.IGNORE_CASE)

    @Test
    fun `dash pattern catches character references and escapes, not hyphens`() {
        assertTrue(dash.containsMatchIn("a — b"))
        assertTrue(dash.containsMatchIn("a – b"))
        assertTrue(dash.containsMatchIn("a &#8212; b"))
        assertTrue(dash.containsMatchIn("a &#X2013; b"))
        assertTrue(dash.containsMatchIn("a \\u2014 b"))
        assertFalse(dash.containsMatchIn("fält-prospekt"))
        assertFalse(dash.containsMatchIn("&#8217;"))
    }

    @Test
    fun `no string, plural or array item has a dash`() {
        val files = StringsXml.allLanguages()
        val present = files.map { it.parentFile?.name ?: it.path }
        assertTrue("values" in present, "values/strings.xml not found among $present")
        assertTrue("values-en" in present, "values-en/strings.xml not found among $present")
        val offenders = mutableListOf<String>()
        var scanned = 0
        for (file in files) {
            val where = file.parentFile?.name ?: file.path
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
        val labels = StringsXml.stringsXmlFiles(res)
        val present = labels.map { it.parentFile?.name ?: it.path }
        assertTrue("values" in present, "androidApp values/strings.xml not found under $res")
        val offenders =
            labels.flatMap { file ->
                StringsXml
                    .strings(file)
                    .filterValues { dash.containsMatchIn(it) }
                    .map { (name, text) -> "${file.parentFile?.name ?: file.path}/$name: $text" }
            }
        assertTrue(offenders.isEmpty(), offenders.joinToString("\n"))
    }

    @Test
    fun `no kotlin string literal in the app is only a dash`() {
        val sources =
            listOf("commonMain", "androidMain", "iosMain").map { File(repoRoot(), "composeApp/src/$it/kotlin") } +
                File(repoRoot(), "androidApp/src/main/kotlin")
        sources.forEach { dir -> assertTrue(dir.isDirectory, "Missing source folder: $dir") }
        val dashOnly = Regex("\"\\s*[\u2013\u2014]\\s*\"|'[\u2013\u2014]'")
        var scanned = 0
        val offenders =
            sources
                .flatMap { dir -> dir.walkTopDown().filter { it.extension == "kt" }.toList() }
                .onEach { scanned++ }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { i, line ->
                        val trimmed = line.trim()
                        val isComment = trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")
                        if (!isComment && dashOnly.containsMatchIn(line)) "${file.name}:${i + 1}: ${line.trim()}" else null
                    }
                }
        assertTrue(scanned > 100, "Scanned only $scanned .kt files, path broken")
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
