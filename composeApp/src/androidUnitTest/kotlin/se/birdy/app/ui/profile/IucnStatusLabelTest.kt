package se.birdy.app.ui.profile

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.birdy.app.testing.attachComposeResourcesContext
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7g: the profile's IUCN pill showed a bare "NE" (and would have shown a bare
 * "EX" for the extinct species) because only LC to DD had words. Every status the species data
 * uses now has a word in both languages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IucnStatusLabelTest {
    private fun label(code: String): String = runBlocking { getString(checkNotNull(iucnStatusLabel(code)) { code }) }

    @Test
    fun `every status in the species data has a label`() {
        val statuses =
            speciesDir()
                .walkTopDown()
                .filter { it.extension == "yaml" }
                .mapNotNull { file -> file.readLines().firstOrNull { it.startsWith("iucn_status:") } }
                .map { it.removePrefix("iucn_status:").trim() }
                .toSet()
        assertTrue(statuses.size >= 5, "parsed $statuses")
        assertEquals(emptySet(), statuses.filter { iucnStatusLabel(it) == null }.toSet())
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `swedish words for not evaluated and extinct`() {
        attachComposeResourcesContext()
        assertEquals("Ej bedömd", label("NE"))
        assertEquals("Utdöd", label("EX"))
        assertEquals("Utdöd i vilt tillstånd", label("EW"))
        assertEquals("Starkt hotad", label("EN"))
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english words for not evaluated and extinct`() {
        attachComposeResourcesContext()
        assertEquals("Not evaluated", label("NE"))
        assertEquals("Extinct", label("EX"))
        assertEquals("Extinct in the wild", label("EW"))
    }

    private fun speciesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "shared/content/species")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("shared/content/species not found from ${System.getProperty("user.dir")}")
    }
}
