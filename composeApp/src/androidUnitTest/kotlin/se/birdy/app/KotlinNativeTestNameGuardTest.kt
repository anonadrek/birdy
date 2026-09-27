package se.birdy.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kotlin/Native (used for the iOS targets) rejects backtick-quoted test names that contain
 * characters it can't turn into a valid Objective-C selector — `,` most commonly, since English
 * prose test names read naturally with a comma. Gradle on Windows never compiles the iOS
 * targets, so this slips through silently until CI's macOS job runs
 * `:composeApp:compileTestKotlinIosSimulatorArm64` and fails (see CLAUDE.md trap catalog "T4a").
 *
 * This JVM test re-implements that check so Windows catches it too, by scanning every
 * `commonTest`/`iosTest` source set that composeApp and the shared modules compile for iOS.
 */
class KotlinNativeTestNameGuardTest {
    @Test
    fun `no backtick test name uses a character Kotlin-Native forbids`() {
        val root = projectRoot()
        val dirs = testSourceDirs(root)
        assertTrue("Found no commonTest/iosTest source dirs from $root", dirs.isNotEmpty())

        val violations =
            dirs
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" } }
                .flatMap { file -> violationsIn(file, root) }
                .sorted()

        assertEquals("Backtick test names with characters Kotlin/Native forbids", emptyList<String>(), violations)
    }

    private fun violationsIn(
        file: File,
        root: File,
    ): List<String> {
        val relativePath = file.relativeTo(root).path.replace(File.separatorChar, '/')
        val violations = mutableListOf<String>()
        file.readLines().forEachIndexed { index, line ->
            backtickFunNameRegex.findAll(line).forEach { match ->
                val name = match.groupValues[1]
                val bad = name.filter { it in forbiddenChars }
                if (bad.isNotEmpty()) {
                    violations += "$relativePath:${index + 1}: contains ${bad.toSet()} in `$name`"
                }
            }
        }
        return violations
    }

    /** All `src/commonTest/kotlin` and `src/iosTest/kotlin` dirs of composeApp and each shared module, that exist. */
    private fun testSourceDirs(root: File): List<File> {
        val modules = listOf(File(root, "composeApp")) + (File(root, "shared").listFiles { it.isDirectory } ?: emptyArray())
        return modules.flatMap { module ->
            listOf("commonTest", "iosTest").mapNotNull { srcSet ->
                File(module, "src/$srcSet/kotlin").takeIf { it.isDirectory }
            }
        }
    }

    /** Walk up from the working dir to find the repo root (marked by the root `settings.gradle.kts`). */
    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile
        }
        error("settings.gradle.kts not found from ${System.getProperty("user.dir")}")
    }

    private companion object {
        val backtickFunNameRegex = Regex("""fun\s+`([^`]+)`""")

        // Kotlin/Native's FirNativeIdentifierChecker forbids these in identifiers (they can't
        // become a valid Objective-C selector piece); `'` and `-` are explicitly allowed.
        val forbiddenChars: Set<Char> =
            setOf(
                '.',
                ',',
                ';',
                '(',
                ')',
                '[',
                ']',
                '{',
                '}',
                '/',
                '<',
                '>',
                ':',
                '\\',
                '$',
                '&',
                '~',
                '*',
                '?',
                '#',
                '|',
                '§',
                '%',
                '@',
            )
    }
}
