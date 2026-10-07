package se.birdy.app.ui.settings.credits

import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 (review of Task 7e-2): every image the app bundles besides the species photos is
 * either Birdy's own or credited under "Övriga bilder" ([OTHER_IMAGES]). A new photo added to the
 * app's resources fails here until it is credited or listed as Birdy's own.
 */
class OtherImagesTest {
    private val repoRoot: File =
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }

    // Birdy's own artwork: the bird of the icon, the splash and the map pin (legal review §3).
    private val ownImages =
        setOf(
            "composeApp/src/commonMain/composeResources/files/branding/hero_bird.png",
            "composeApp/src/androidMain/res/drawable-nodpi/ic_launcher_monochrome.png",
            "androidApp/src/main/res/drawable-nodpi/ic_launcher_monochrome.png",
            "androidApp/src/main/res/drawable-nodpi/splash_icon.png",
        )

    // The app's own source sets and the shared modules' main source sets (not shared/content/images,
    // the species photos' source, which reach the app through the asset pack and the db's credits).
    private val bundled: List<File> =
        listOf("composeApp/src/commonMain", "composeApp/src/androidMain", "composeApp/src/iosMain", "androidApp/src/main")
            .map { File(repoRoot, it) } +
            File(repoRoot, "shared")
                .listFiles()
                .orEmpty()
                .flatMap { module -> File(module, "src").listFiles().orEmpty().filter { it.name.endsWith("Main") } }

    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")

    private fun bundledImages(): Set<String> =
        bundled
            .filter { it.exists() }
            .flatMap { dir ->
                dir
                    .walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in imageExtensions }
                    .toList()
            }.map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            // Launcher icons are Birdy's own, in every density.
            .filterNot { Regex("""/mipmap-[a-z]+/ic_launcher(_foreground)?\.png$""").containsMatchIn(it) }
            .toSet()

    @Test
    fun `every bundled image besides the species photos is credited or birdy's own`() {
        val credited = OTHER_IMAGES.map { it.file }.toSet()
        assertEquals(emptySet(), bundledImages() - credited - ownImages, "images without a credit")
    }

    @Test
    fun `the credited images are there, with commons credits`() {
        OTHER_IMAGES.forEach { image ->
            assertTrue(File(repoRoot, image.file).isFile, image.file)
            assertTrue(image.credit.filePageUrl.startsWith("https://commons.wikimedia.org/wiki/File:"))
            assertTrue(image.credit.author.isNotBlank() && image.credit.licenseUrl != null, image.file)
        }
    }
}
