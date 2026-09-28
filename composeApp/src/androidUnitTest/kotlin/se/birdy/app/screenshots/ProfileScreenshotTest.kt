package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy

/**
 * Artprofilen (spec 2026-09-24 §4.3, Task 11): foto-hero med familje-kicker, latinskt namn och en
 * piller-rad (allmän/familj/rödlista), följt av en arksida (beskrivning med droppat versal,
 * marginalia, Premium-teaser, flyttning, foton). Coil laddar inga bilder under Robolectric (se
 * [ComponentsScreenshotTest]) — hero-fotot och foto-radens tumnaglar renderar sina tomma
 * platshållarfyllningar, vilket är förväntat; en riktig bild kan inte matas in här eftersom
 * skärmen själv äger sitt `AsyncImage`-anrop (ingen bild-slot att peta in en avkodad bitmap i,
 * till skillnad från [ComponentsScreenshotTest.hero_photo_sv], som testar [se.birdy.app.ui.components.PhotoHero]
 * direkt och redan täcker ett riktigt inbäddat foto bakom pillren/scrimmen på komponentnivå).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun heroImage(id: String) =
        SpeciesImage(
            role = "hero",
            path = "$id/hero.webp",
            width = 800,
            height = 600,
            license = "CC0",
            author = "Test",
            sourceUrl = "https://example.com",
        )

    private fun talgoxe(locale: Locale) =
        Species(
            id = SpeciesId("Q25485"),
            scientificName = "Parus major",
            taxonomy =
                SpeciesTaxonomy(
                    family = "Paridae",
                    familySv = "Mesfåglar",
                    genus = "Parus",
                    iocOrder = "Passeriformes",
                ),
            name = if (locale == Locale.EN) "Great Tit" else "Talgoxe",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE", "NO", "FI", "DK", "DE"),
            season = emptyMap(),
            description =
                if (locale == Locale.EN) {
                    "The great tit is one of Sweden's most common songbirds, a familiar visitor at feeders " +
                        "and gardens all year round."
                } else {
                    "Talgoxen är en av Sveriges vanligaste tättingar, en flitig gäst vid fågelbordet och i " +
                        "trädgården hela året."
                },
            migration = if (locale == Locale.EN) "Mostly a year-round resident." else "Mestadels stannfågel.",
            marginalia =
                if (locale == Locale.EN) {
                    "Recognizable by its black head-cap and bright yellow breast with a dark stripe."
                } else {
                    "Känns igen på sin svarta hätta och gula bröst med ett mörkt bröstband."
                },
            images = listOf(heroImage("Q25485")),
        )

    // Stress case (matches ComponentsScreenshotTest.hero_long_en_130): a long English name + a
    // long (fixture-only, not the real taxonomy) family name, at 130% font scale — checks that
    // ProfilePillRow's FlowRow wraps instead of overflowing the hero's width.
    private fun woodpecker() =
        Species(
            id = SpeciesId("Q210418"),
            scientificName = "Picoides tridactylus",
            taxonomy =
                SpeciesTaxonomy(
                    family = "Phalacrocoracidae",
                    familySv = "Storskarvar",
                    genus = "Picoides",
                    iocOrder = "Piciformes",
                ),
            name = "Eurasian Three-toed Woodpecker",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "NT",
            regions = listOf("SE", "NO", "FI"),
            season = emptyMap(),
            description = "A boreal-forest specialist, quiet and easy to overlook among the spruces and pines.",
            migration = "Mostly a year-round resident, with some post-breeding dispersal to lower ground.",
            marginalia = "Listen for its soft, irregular tapping high up on dead spruce trunks.",
            images = listOf(heroImage("Q210418")),
        )

    private fun viewModel(
        species: Species,
        locale: Locale,
    ) = SpeciesProfileViewModel(
        repo = FakeSpeciesRepository().apply { byId.value = mapOf(species.id to species) },
        speciesId = species.id,
        locale = locale,
    )

    @Composable
    private fun screen(
        species: Species,
        locale: Locale,
        showPremiumTeaser: Boolean,
    ) {
        val vm = remember { viewModel(species, locale) }
        SpeciesProfileScreen(
            viewModel = vm,
            locale = locale,
            onBack = {},
            onPremiumClick = {},
            showPremiumTeaser = showPremiumTeaser,
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun profile_sv() {
        compose.captureScreen("profile_sv") { screen(talgoxe(Locale.SV), Locale.SV, showPremiumTeaser = false) }
        compose.onNodeWithText("Talgoxe").assertExists()
        // T11c gap (a) regression proof: the IUCN pill's contentDescription must land on the
        // SAME merged semantics node as the abundance pill's text ("ALLMÄN, Global rödlista
        // (IUCN): Livskraftig (LC)" as one TalkBack stop), not on a node of its own. Before the
        // fix, JournalPill set its contentDescription via `semantics(mergeDescendants = true)`,
        // which made the pill its OWN merge boundary — a node that is itself a merge boundary is
        // NOT absorbed by an ancestor's mergeDescendants (ProfilePillRow's FlowRow here), so it
        // stayed a second, separate stop. This combined matcher only finds a node when both
        // properties live together on the one merged FlowRow node.
        compose
            .onNode(
                hasContentDescription("Global rödlista (IUCN): Livskraftig (LC)", substring = true) and
                    hasText("ALLMÄN", substring = true),
            ).assertExists()
        // And the literal, un-augmented IUCN text must not surface as its own queryable node at
        // all: `clearAndSetSemantics` removes it, replacing it with the description above —
        // that's what makes it "not separately focusable" rather than merely duplicated.
        compose.onAllNodesWithText("Livskraftig (LC)").assertCountEquals(0)
    }

    @Test
    @Config(qualifiers = "+en")
    fun profile_en() {
        compose.captureScreen("profile_en") { screen(talgoxe(Locale.EN), Locale.EN, showPremiumTeaser = false) }
        compose.onNodeWithText("Great Tit").assertExists()
    }

    // Free-user teaser variant — the other two profile_* screenshots show the premium (no-teaser)
    // view instead, so between them all three PaperSheet states are covered.
    @Test
    @Config(qualifiers = "+sv")
    fun profile_teaser_sv() {
        compose.captureScreen("profile_teaser_sv") { screen(talgoxe(Locale.SV), Locale.SV, showPremiumTeaser = true) }
        compose.onNodeWithText("Talgoxe").assertExists()
        compose.onNodeWithText("Lås upp").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun profile_long_en_130() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.captureScreen("profile_long_en_130") { screen(woodpecker(), Locale.EN, showPremiumTeaser = false) }
        compose.onNodeWithText("Eurasian Three-toed Woodpecker").assertExists()
    }

    // Wrap stress case (T11c gap (c)): 130% system font scale, EN, with the longest realistic
    // pill pairing — "UNCOMMON" (the longer of the two abundance labels) plus "Critically
    // endangered (CR)" (the longest mapped IUCN word). ProfilePillRow's FlowRow must wrap these
    // onto a second line instead of overflowing the hero's width or clipping either pill's text.
    // Width 300dp, narrower than the class default 411dp AND narrower than a real phone's
    // smallest-width bucket (320dp is Android's practical floor): measured first at 360dp per the
    // quality review's suggestion, then 320dp — both fit this exact pairing on one line with a
    // few px to spare (pill text is short; unlike `hero_long_en_130`'s fictional family name,
    // there's no real content lever left to pull once abundance + IUCN are both maxed out), so
    // narrowed further to actually exercise the wrap this test exists to prove.
    private fun wrapStressSpecies() =
        woodpecker().copy(
            abundance = Abundance.OVANLIG,
            iucnStatus = "CR",
        )

    @Test
    @Config(qualifiers = "en-w300dp-h1400dp")
    fun profile_wrap_en_130() {
        RuntimeEnvironment.setFontScale(1.3f)
        compose.captureScreen("profile_wrap_en_130") {
            screen(wrapStressSpecies(), Locale.EN, showPremiumTeaser = false)
        }
        compose.onNodeWithText("Eurasian Three-toed Woodpecker").assertExists()
    }
}
