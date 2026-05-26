package se.birdy.app.ui.listen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * BirdNET-Lite is CC BY-NC-SA 4.0 (NonCommercial). Audio identification MUST
 * remain free for all users — gating any audio-ID code path behind premium
 * is a license violation. This test fails the build if any premium-state
 * reference creeps into the listen/ or audio/ source trees.
 *
 * If this test fails: the offending file references PremiumState,
 * effectivePremiumActive, or isPremiumActive in a path that must not gate.
 * Either move the gate elsewhere or ask whether the change is legal.
 */
class BirdNetLicenseGuardTest {
    @Test
    fun `listen and audio source trees never reference premium gating`() {
        val guardedDirs =
            listOf(
                "src/commonMain/kotlin/se/birdy/app/ui/listen",
                "src/commonMain/kotlin/se/birdy/app/ui/audio",
            )
        val forbiddenTokens = listOf("PremiumState", "effectivePremiumActive", "isPremiumActive")
        val moduleRoot = resolveComposeAppRoot()

        val offenders = mutableListOf<String>()
        var scannedFiles = 0
        for (relativeDir in guardedDirs) {
            val dir = File(moduleRoot, relativeDir)
            assertTrue(
                "Guarded directory must exist (probably renamed/moved): ${dir.absolutePath}",
                dir.isDirectory,
            )
            dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
                scannedFiles++
                val text = file.readText()
                forbiddenTokens.forEach { token ->
                    if (text.contains(token)) {
                        offenders += "${file.relativeTo(moduleRoot)} contains '$token'"
                    }
                }
            }
        }

        assertTrue("Guard scanned zero files — path resolution is broken.", scannedFiles > 0)
        assertEquals(
            "BirdNET license guard tripwire: audio/listen paths must never gate on premium.\n" +
                offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    private fun resolveComposeAppRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "src/commonMain/kotlin/se/birdy/app/ui/listen").isDirectory) {
                return dir
            }
            val composeApp = File(dir, "composeApp")
            if (File(composeApp, "src/commonMain/kotlin/se/birdy/app/ui/listen").isDirectory) {
                return composeApp
            }
            dir = dir.parentFile
        }
        error("Could not locate composeApp module from working dir ${System.getProperty("user.dir")}")
    }
}
