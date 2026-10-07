package se.birdy.app.ui.credits

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.LinkAnnotation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.content.model.SpeciesTextSource
import kotlin.test.assertEquals

/**
 * Release 1.3.0, legal review §2 and §4 (Task 7e-2, 7i-fix A), with the real string resources: the
 * species profile credits every photo it shows (under the photo at the top, and beside each photo
 * in the photo list), and ends the species text with where it comes from and its licence. Each
 * credit is one text node (TalkBack reads it whole) whose links are focusable nodes of their own.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileCreditsTest {
    @get:Rule
    val compose = createComposeRule()

    private val ccBySaHero =
        SpeciesImage("hero", "Q25234/hero.webp", 1, 1, "CC BY-SA 4.0", "Musicaline", "", "Turdus merula 1294.jpg")
    private val publicDomainSecondary =
        SpeciesImage(
            "secondary",
            "Q25234/secondary-1.webp",
            1,
            1,
            "Public domain",
            "U.S. Fish and Wildlife Service",
            "",
            "Blackbird FWS.jpg",
        )
    private val svSource = SpeciesTextSource(Locale.SV, "57446811", "https://sv.wikipedia.org/w/index.php?oldid=57446811")
    private val enSource = SpeciesTextSource(Locale.EN, "1351287395", "https://en.wikipedia.org/w/index.php?oldid=1351287395")

    private fun koltrast(
        name: String = "Koltrast",
        description: String? = "Koltrasten är en av våra vanligaste fåglar.",
        migration: String? = "Stannfågel i södra Sverige.",
        textSources: List<SpeciesTextSource> = listOf(svSource),
    ) = Species(
        id = SpeciesId("Q25234"),
        scientificName = "Turdus merula",
        taxonomy = SpeciesTaxonomy(family = "Turdidae", familySv = "Trastar", genus = "Turdus", iocOrder = "Passeriformes"),
        name = name,
        abundance = Abundance.ALLMÄN,
        iucnStatus = "LC",
        regions = listOf("SE"),
        season = emptyMap(),
        description = description,
        migration = migration,
        images = listOf(ccBySaHero, publicDomainSecondary),
        textSources = textSources,
    )

    private fun show(
        species: Species,
        locale: Locale,
    ) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                SpeciesProfileScreen(
                    viewModel =
                        remember {
                            SpeciesProfileViewModel(
                                repo = FakeSpeciesRepository().apply { byId.value = mapOf(species.id to species) },
                                speciesId = species.id,
                                locale = locale,
                            )
                        },
                    locale = locale,
                    onBack = {},
                    onPremiumClick = {},
                    showPremiumTeaser = false,
                )
            }
        }
        compose.waitForIdle()
    }

    /** The links in a text node, as (linked words, url), in order. */
    private fun SemanticsNodeInteraction.links(): List<Pair<String, String>> {
        val text = fetchSemanticsNode().config[SemanticsProperties.Text].single()
        return text.getLinkAnnotations(0, text.length).map {
            text.substring(it.start, it.end).replace(NO_BREAK_SPACE, ' ') to (it.item as LinkAnnotation.Url).url
        }
    }

    /** One text node with [count] focusable links inside it. */
    private fun SemanticsNodeInteraction.assertLinks(count: Int) {
        val children = onChildren()
        children.assertCountEquals(count)
        for (i in 0 until count) children[i].assertHasClickAction()
    }

    private val heroCreditSv = nb("Foto: Musicaline · CC BY-SA 4.0 · Wikimedia Commons · nedskalad")
    private val textCreditSv =
        nb("Texten bygger på Wikipedia-artikeln och har sammanfattats och ändrats. Den får delas under CC BY-SA 4.0.")

    @Test
    @Config(qualifiers = "+sv")
    fun `every photo is credited, the hero under the photo and again beside it in the photo list`() {
        show(koltrast(), Locale.SV)
        compose.onAllNodesWithText(heroCreditSv).assertCountEquals(2)
        val hero = compose.onAllNodesWithText(heroCreditSv)[0]
        assertEquals(
            listOf(
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
                "Wikimedia Commons" to "https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg",
            ),
            hero.links(),
        )
        hero.assertLinks(2)
        val publicDomain = compose.onNodeWithText(nb("Foto: U.S. Fish and Wildlife Service · public domain · Wikimedia Commons"))
        assertEquals(listOf("Wikimedia Commons" to "https://commons.wikimedia.org/wiki/File:Blackbird_FWS.jpg"), publicDomain.links())
        publicDomain.assertLinks(1)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the species text ends with its article version and licence`() {
        show(koltrast(), Locale.SV)
        val credit = compose.onNodeWithText(textCreditSv)
        assertEquals(
            listOf(
                "Wikipedia-artikeln" to "https://sv.wikipedia.org/w/index.php?oldid=57446811",
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/deed.sv",
            ),
            credit.links(),
        )
        credit.assertLinks(2)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a swedish user shown the english fallback is pointed at the english article`() {
        show(koltrast(textSources = listOf(enSource)), Locale.SV)
        val credit =
            compose.onNodeWithText(
                nb(
                    "Texten bygger på den engelska Wikipedia-artikeln och har sammanfattats och ändrats. " +
                        "Den får delas under CC BY-SA 4.0.",
                ),
            )
        assertEquals("den engelska Wikipedia-artikeln" to enSource.articleUrl, credit.links().first())
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the english app credits in english`() {
        show(koltrast(name = "Common Blackbird", textSources = listOf(enSource)), Locale.EN)
        compose.onAllNodesWithText(nb("Photo: Musicaline · CC BY-SA 4.0 · Wikimedia Commons · resized")).assertCountEquals(2)
        val credit =
            compose.onNodeWithText(
                nb(
                    "The text is based on the Wikipedia article and has been summarised and changed. " +
                        "It may be shared under CC BY-SA 4.0.",
                ),
            )
        assertEquals(
            listOf(
                "the Wikipedia article" to enSource.articleUrl,
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
            ),
            credit.links(),
        )
    }

    // The empty-state lines are the app's own words: nothing of Wikipedia's to credit.
    @Test
    @Config(qualifiers = "+sv")
    fun `a species without text has no text credit but its photos are still credited`() {
        show(koltrast(description = null, migration = null, textSources = emptyList()), Locale.SV)
        compose.onNodeWithText("Texten bygger på", substring = true).assertDoesNotExist()
        compose.onAllNodesWithText(heroCreditSv).assertCountEquals(2)
    }
}

/**
 * [s] with the no-break spaces the credits put inside licence names, "Wikimedia Commons" and
 * "public domain" (see keepTogether), so a test can write the credit as it reads.
 */
internal fun nb(s: String): String =
    listOf(
        "CC BY-SA 4.0",
        "CC BY 2.0",
        "Wikimedia Commons",
        "public domain",
    ).fold(s) { acc, term -> acc.replace(term, term.keepTogether()) }
