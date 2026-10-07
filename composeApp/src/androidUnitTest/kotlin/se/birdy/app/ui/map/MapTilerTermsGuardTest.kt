package se.birdy.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * MapTiler's terms §4.4 (all plans): "It is expressly prohibited to manipulate or modify map
 * content, in the form of vectors, pixels or underlying metadata." The map shows MapTiler's tiles
 * exactly as served; the app's colours come from the style (Albin's own style made in MapTiler
 * Customize, see MapTilerUrls.kt), never from a filter on the tiles. Until 1.3.0 a duotone
 * ColorMatrix recoloured every tile on Android and a CIColorMatrix did the same on iOS (legal
 * review 2026-10, 7i-fix G).
 *
 * This tripwire fails when a colour filter or pixel recolouring creeps back into the map code on
 * either platform, including the Swift half of the iOS tile bridge. Drawing our own things on top
 * (the pins, the attribution chip) or a paper colour where no tile has loaded yet is fine; the two
 * files that only draw our own wax-seal pin (MapMarkerIcon.android.kt, IosSealMarker.kt) may colour that artwork as they like.
 */
class MapTilerTermsGuardTest {
    @Test
    fun `the map never recolours MapTiler's tiles`() {
        val moduleRoot = resolveComposeAppRoot()
        val guarded =
            listOf(
                File(moduleRoot, "src/commonMain/kotlin/se/birdy/app/ui/map"),
                File(moduleRoot, "src/androidMain/kotlin/se/birdy/app/ui/map"),
                File(moduleRoot, "src/iosMain/kotlin/se/birdy/app/ui/map"),
                File(moduleRoot, "../iosApp/iosApp"),
            )
        val forbiddenTokens =
            listOf(
                "ColorFilter",
                "ColorMatrix",
                "PorterDuff",
                "CIFilter",
                "CIColor",
                "duotone",
                "tinted(",
            )

        val offenders = mutableListOf<String>()
        var scannedFiles = 0
        val pinArtwork = setOf("MapMarkerIcon.android.kt", "IosSealMarker.kt")
        guarded.forEach { dir ->
            assertTrue("Guarded directory must exist (renamed or moved?): ${dir.absolutePath}", dir.isDirectory)
            dir
                .walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "swift") && it.name !in pinArtwork }
                .forEach { file ->
                    scannedFiles++
                    val text = file.readText()
                    forbiddenTokens.forEach { token ->
                        if (text.contains(token, ignoreCase = true)) offenders += "${file.name} contains '$token'"
                    }
                }
        }

        assertTrue("Guard scanned zero files: path resolution is broken.", scannedFiles > 0)
        assertEquals(
            "MapTiler terms §4.4: the map must show the tiles unmodified.\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    private fun resolveComposeAppRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/commonMain/kotlin/se/birdy/app/ui/map").isDirectory) return dir
            val composeApp = File(dir, "composeApp")
            if (File(composeApp, "src/commonMain/kotlin/se/birdy/app/ui/map").isDirectory) return composeApp
            dir = dir.parentFile
        }
        error("Could not locate composeApp module from working dir ${System.getProperty("user.dir")}")
    }
}
