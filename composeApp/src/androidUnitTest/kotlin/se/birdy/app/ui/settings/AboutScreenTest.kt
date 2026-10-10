package se.birdy.app.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.LinkAnnotation
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.about_photos_body
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.settings.credits.linkify
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0, legal review 7i-fix C: About's "Innehåll & data" names every source of the app's
 * content with its licence (the review's proposal, §5), its links open in the browser, and its two
 * rows open the photo credits (Task 7e-2) and the open-source licences (7i-fix B).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AboutScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var photoCreditsOpened = 0
    private var licensesOpened = 0

    private fun show(
        locale: Locale,
        photoCount: Int? = 2066,
    ) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                AboutScreen(
                    onBack = {},
                    version = "1.3.0",
                    photoCount = photoCount,
                    locale = locale,
                    onOpenPhotoCredits = { photoCreditsOpened++ },
                    onOpenLicenses = { licensesOpened++ },
                    onOpenUrl = { opened += it },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `swedish about names every source and licence`() {
        show(Locale.SV)
        listOf(
            "2\u00A0066 artfoton från Wikimedia Commons under CC0, public domain, CC BY eller CC BY-SA, nedskalade",
            "Claude (Anthropic)",
            "Wikipedia-artiklar på svenska och engelska och delas under CC BY-SA 4.0",
            "BirdLife Sveriges taxonomikommitté (Västpalearktis-listan)",
            "IOC World Bird List v14.1 (F. Gill, D. Donsker & P. Rasmussen, red.; CC BY 3.0) och Wikidata (CC0)",
            "IUCN:s globala rödlista",
            "© MapTiler",
            "© OpenStreetMap contributors (ODbL)",
            "AIY Birds V1, © Google, Apache License 2.0",
            "BirdNET-Lite, K. Lisa Yang Center for Conservation Bioacoustics, Cornell Lab of Ornithology, Cornell University",
            "CC BY-NC-SA 4.0, används oförändrad",
            "Kahl m.fl. (2021), Ecological Informatics 61: 101236",
        ).forEach { compose.onNodeWithText(it, substring = true).assertExists(it) }
        // The 1.2 text that said Wikidata was CC BY-SA is gone.
        compose.onNodeWithText("Fågeldata från Wikidata och Wikipedia", substring = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english about names every source and licence`() {
        show(Locale.EN)
        listOf(
            "2,066 species photos from Wikimedia Commons under CC0, public domain, CC BY or CC BY-SA, resized",
            "based on Wikipedia articles in Swedish and English and are shared under CC BY-SA 4.0",
            "IOC World Bird List v14.1 (F. Gill, D. Donsker & P. Rasmussen, eds.; CC BY 3.0) and Wikidata (CC0)",
            "© OpenStreetMap contributors (ODbL)",
            "License CC BY-NC-SA 4.0, used unchanged",
            "Kahl et al. (2021), Ecological Informatics 61: 101236",
        ).forEach { compose.onNodeWithText(it, substring = true).assertExists(it) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `before the number of photos is read the paragraph has no number`() {
        show(Locale.SV, photoCount = null)
        compose.onNodeWithText("Artfoton från Wikimedia Commons under CC0", substring = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `links open in the browser`() {
        show(Locale.SV)
        compose
            .onNodeWithText("BirdNET-Lite, K. Lisa Yang", substring = true)
            .performScrollTo()
            .performFirstLinkClick { (it.item as LinkAnnotation.Url).url == AboutCredits.CC_BY_NC_SA_4 }
        compose
            .onNodeWithText("Sammanfattningar som AI-modellen", substring = true)
            .performFirstLinkClick { (it.item as LinkAnnotation.Url).url == AboutCredits.CC_BY_SA_4 }
        compose.runOnIdle { assertEquals(listOf(AboutCredits.CC_BY_NC_SA_4, AboutCredits.CC_BY_SA_4), opened) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the two rows open the photo credits and the open-source licences`() {
        show(Locale.SV)
        compose.onNodeWithText("Bildkällor").performScrollTo().performClick()
        compose.onNodeWithText("Licenser för öppen källkod").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, photoCreditsOpened)
            assertEquals(1, licensesOpened)
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `every linked name is in the swedish text`() = assertEveryLinkIsInTheText()

    @Test
    @Config(qualifiers = "+en")
    fun `every linked name is in the english text`() = assertEveryLinkIsInTheText()

    /** A phrase a translation lost would silently stay unlinked; this keeps every link in place. */
    private fun assertEveryLinkIsInTheText() {
        attachComposeResourcesContext()
        val photos = runBlocking { getString(Res.string.about_photos_body, "2") }
        val paragraphs =
            listOf(photos to AboutCredits.photoLinks) +
                AboutCredits.paragraphs.map { runBlocking { getString(it.body) } to it.links }
        for ((text, links) in paragraphs) {
            val annotated = linkify(text, links)
            val urls = annotated.getLinkAnnotations(0, annotated.length).map { (it.item as LinkAnnotation.Url).url }
            assertEquals(links.map { it.url }.sorted(), urls.sorted(), "links in: $text")
            links.forEach { assertTrue(it.phrase in text, "'${it.phrase}' missing from: $text") }
        }
    }

    @Test
    fun `numbers are grouped as each language writes them`() {
        assertEquals("2\u00A0066", formatCount(2066, Locale.SV))
        assertEquals("2,066", formatCount(2066, Locale.EN))
        assertEquals("746", formatCount(746, Locale.SV))
        assertEquals("1\u00A0234\u00A0567", formatCount(1234567, Locale.SV))
    }
}
