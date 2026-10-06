package se.birdy.app.ui.theme

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7g item 6: on API 30 the very first frame (the launch window, drawn from
 * `Theme.Birdy.Starting` before any app code runs) had white status bar and navigation bar icons
 * on the paper background. The app's own window settings only apply after `onCreate`, so the
 * launch theme has to ask for dark icons itself: `windowLightStatusBar` (API 23+) and
 * `windowLightNavigationBar` (API 27+), for every API level the app runs on (minSdk 24).
 *
 * The androidApp module has no unit tests, so this reads its theme files the way Android picks
 * them: for each API level, the `values-vN` folder with the highest N not above it.
 */
class LaunchThemeSystemBarsTest {
    private val resDir: File by lazy { findResDir() }

    private val styleRegex = Regex("""<style name="Theme\.Birdy\.Starting"[^>]*>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)

    private fun itemRegex(name: String) = Regex("""<item name="android:$name">\s*(\w+)\s*</item>""")

    /** The folders with a launch theme, keyed by the lowest API level they apply to. */
    private fun launchThemes(): Map<Int, String> =
        resDir
            .listFiles { f -> f.isDirectory && (f.name == "values" || f.name.matches(Regex("values-v\\d+"))) }
            .orEmpty()
            .mapNotNull { dir ->
                val themes = File(dir, "themes.xml").takeIf { it.isFile } ?: return@mapNotNull null
                val style = styleRegex.find(themes.readText())?.groupValues?.get(1) ?: return@mapNotNull null
                val minApi =
                    dir.name
                        .removePrefix("values")
                        .removePrefix("-v")
                        .toIntOrNull() ?: 0
                minApi to style
            }.toMap()

    private fun styleFor(api: Int): String {
        val themes = launchThemes()
        return themes.getValue(themes.keys.filter { it <= api }.max())
    }

    private fun item(
        style: String,
        name: String,
    ): String? = itemRegex(name).find(style)?.groupValues?.get(1)

    @Test
    fun `the launch window asks for dark status bar icons on every api level`() {
        for (api in MIN_SDK..TARGET_SDK) {
            assertEquals("true", item(styleFor(api), "windowLightStatusBar"), "API $api")
        }
    }

    @Test
    fun `the launch window asks for dark navigation bar icons from api 27`() {
        for (api in 27..TARGET_SDK) {
            assertEquals("true", item(styleFor(api), "windowLightNavigationBar"), "API $api")
        }
    }

    @Test
    fun `api 24 to 26 never see the api 27 attribute`() {
        for (api in MIN_SDK..26) {
            assertTrue(item(styleFor(api), "windowLightNavigationBar") == null, "API $api")
        }
    }

    private fun findResDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "androidApp/src/main/res")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("androidApp/src/main/res not found from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val MIN_SDK = 24
        const val TARGET_SDK = 36
    }
}
