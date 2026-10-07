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
 * The androidApp module has no unit tests, so this resolves its theme files the way Android does:
 * for each API level a style comes from the `values-vN` folder with the highest N not above it
 * that defines it, and inherits the items of its parent (our own `Theme.Birdy.*` parents only;
 * the library's Theme.SplashScreen sets neither attribute).
 */
class LaunchThemeSystemBarsTest {
    private data class Style(
        val parent: String?,
        val items: Map<String, String>,
    )

    private val styleRegex =
        Regex("""<style name="([^"]+)"(?:\s+parent="([^"]*)")?\s*(?:/>|>(.*?)</style>)""", RegexOption.DOT_MATCHES_ALL)
    private val itemRegex = Regex("""<item name="([^"]+)">\s*([^<]*?)\s*</item>""")

    /** Each values folder's styles, keyed by the lowest API level the folder applies to. */
    private val folders: Map<Int, Map<String, Style>> by lazy {
        findResDir()
            .listFiles { f -> f.isDirectory && (f.name == "values" || f.name.matches(Regex("values-v\\d+"))) }
            .orEmpty()
            .mapNotNull { dir ->
                val themes = File(dir, "themes.xml").takeIf { it.isFile } ?: return@mapNotNull null
                val minApi =
                    dir.name
                        .removePrefix("values")
                        .removePrefix("-v")
                        .toIntOrNull() ?: 0
                minApi to
                    styleRegex.findAll(themes.readText()).associate { m ->
                        m.groupValues[1] to
                            Style(
                                parent = m.groupValues[2].ifEmpty { null },
                                items = itemRegex.findAll(m.groupValues[3]).associate { it.groupValues[1] to it.groupValues[2] },
                            )
                    }
            }.toMap()
    }

    private fun resolve(
        name: String,
        api: Int,
    ): Map<String, String> {
        val folder = folders.keys.filter { it <= api && name in folders.getValue(it) }.maxOrNull() ?: return emptyMap()
        val style = folders.getValue(folder).getValue(name)
        val inherited =
            style.parent
                ?.takeIf { it.startsWith("Theme.Birdy") }
                ?.let { resolve(it, api) }
                .orEmpty()
        return inherited + style.items
    }

    private fun launchTheme(api: Int) = resolve("Theme.Birdy.Starting", api)

    @Test
    fun `the launch window asks for dark status bar icons on every api level`() {
        for (api in MIN_SDK..TARGET_SDK) {
            assertEquals("true", launchTheme(api)["android:windowLightStatusBar"], "API $api")
        }
    }

    @Test
    fun `the launch window asks for dark navigation bar icons from api 27`() {
        for (api in 27..TARGET_SDK) {
            assertEquals("true", launchTheme(api)["android:windowLightNavigationBar"], "API $api")
        }
    }

    @Test
    fun `api 24 to 26 never see the api 27 attribute`() {
        for (api in MIN_SDK..26) {
            assertTrue("android:windowLightNavigationBar" !in launchTheme(api), "API $api")
        }
    }

    @Test
    fun `the launch window keeps its splash on every api level`() {
        for (api in MIN_SDK..TARGET_SDK) {
            val theme = launchTheme(api)
            assertEquals("@color/paper_bg", theme["windowSplashScreenBackground"], "API $api")
            assertEquals("@style/Theme.Birdy", theme["postSplashScreenTheme"], "API $api")
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
