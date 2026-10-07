package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.ui.match.DisambigView
import se.birdy.app.ui.match.MatchResultUiState
import se.birdy.app.ui.match.MatchView
import se.birdy.app.ui.match.ResolvedPrediction
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.WikipediaLinks
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.content.model.SpeciesTextSource
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource

/**
 * The credits of release 1.3.0 (legal review §2 and §4; Task 7e-2, 7i-fix A) on the real shipped
 * photos, with each photo's real credit from species.db: the species profile (the credit right
 * under the photo, the photo list with a credit beside each photo, the text credit at the end of
 * the species text), Match (one line under the photo) and Disambig (a line in each card). Stenfalk
 * has a public domain hero and CC BY 2.0 photos below; talgoxe a CC0 hero. "_full" captures are
 * one tall window, so the whole profile shows in one image; "_200" is 200% text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class CreditsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun image(
        id: String,
        role: String,
        file: String,
        license: String,
        author: String,
        commonsFileName: String,
    ) = SpeciesImage(
        role = role,
        path = "$id/$file",
        width = 1800,
        height = 1200,
        license = license,
        author = author,
        sourceUrl = "https://commons.wikimedia.org/wiki/File:$commonsFileName",
        commonsFileName = commonsFileName,
    )

    private fun source(
        language: Locale,
        revision: String,
    ) = SpeciesTextSource(language, revision, WikipediaLinks.articleUrl(language, revision, SpeciesId("Q131918")))

    private fun stenfalk(locale: Locale) =
        Species(
            id = SpeciesId("Q131918"),
            scientificName = "Falco columbarius",
            taxonomy = SpeciesTaxonomy(family = "Falconidae", familySv = "Falkfåglar", genus = "Falco", iocOrder = "Falconiformes"),
            name = if (locale == Locale.EN) "Merlin" else "Stenfalk",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE", "NO", "FI"),
            season = emptyMap(),
            description =
                if (locale == Locale.EN) {
                    "The merlin is a small, compact falcon with fairly broad but pointed wings and a long tail. It is " +
                        "more robust and heavily built than most other small falcons, and its pointed wings separate it " +
                        "from small hawks such as the sparrowhawk, with which it is sometimes confused."
                } else {
                    "Stenfalken är en kompakt liten falk med ganska breda vingar och lång stjärt. Den förväxlas ibland " +
                        "med småhökar som sparvhök, men har till skillnad från dem spetsiga vingar. En vuxen hane blir " +
                        "25 till 30 centimeter lång."
                },
            migration =
                if (locale == Locale.EN) {
                    "In Sweden the merlin breeds in coniferous forest from northern Dalarna northwards through most of " +
                        "Norrland, on bogs and in the mountains, with a few pairs on Öland."
                } else {
                    "I Sverige häckar stenfalken i barrskog från norra Dalarna och norrut genom större delen av " +
                        "Norrland, på myrar och i fjällen, och med några par på Öland."
                },
            images =
                listOf(
                    image("Q131918", "hero", "hero.webp", "Public domain", "Robert Burton / USFWS", "Falco columbarius FWS 14007.jpg"),
                    image(
                        "Q131918",
                        "secondary",
                        "secondary-1.webp",
                        "CC BY 2.0",
                        "Bear Golden Retriever",
                        "Falco columbarius Auburn NY.jpg",
                    ),
                    image(
                        "Q131918",
                        "secondary",
                        "secondary-2.webp",
                        "CC BY 2.0",
                        "Bear Golden Retriever",
                        "Falco columbarius pair Auburn NY 1.jpg",
                    ),
                ),
            textSources = listOf(if (locale == Locale.EN) source(Locale.EN, "1367745672") else source(Locale.SV, "59603908")),
        )

    // A Swedish description with the English fallback for the migration text: two articles.
    private val talgoxe =
        Species(
            id = SpeciesId("Q25485"),
            scientificName = "Parus major",
            taxonomy = SpeciesTaxonomy(family = "Paridae", familySv = "Mesar", genus = "Parus", iocOrder = "Passeriformes"),
            name = "Talgoxe",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description =
                "Talgoxen är en vanlig fågel som förekommer över stora delar av Europa, västra, centrala och norra " +
                    "Asien samt delar av Nordafrika. Den är mycket anpassningsbar och trivs i alla slags skogslandskap.",
            migration =
                "The Great Tit is a year-round resident throughout most of Scandinavia and Northern Europe, with " +
                    "populations remaining sedentary across their range.",
            images =
                listOf(
                    image(
                        "Q25485",
                        "hero",
                        "hero.webp",
                        "CC0",
                        "Hobbyfotowiki",
                        "Great tit (Parus major), North Rhine-Westphalia.jpg",
                    ),
                    image("Q25485", "secondary", "secondary-1.webp", "CC BY 2.0", "Anton Whoa", "Parus major, Omsk, Russia.jpg"),
                    image("Q25485", "secondary", "secondary-2.webp", "CC BY 2.0", "Luiz Lapa", "Chapim-real, Great Tit (54277592018).jpg"),
                ),
            textSources =
                listOf(
                    SpeciesTextSource(Locale.SV, "59064377", WikipediaLinks.articleUrl(Locale.SV, "59064377", SpeciesId("Q25485"))),
                    SpeciesTextSource(Locale.EN, "1334945574", WikipediaLinks.articleUrl(Locale.EN, "1334945574", SpeciesId("Q25485"))),
                ),
        )

    private fun koltrast(locale: Locale) =
        Species(
            id = SpeciesId("Q25234"),
            scientificName = "Turdus merula",
            taxonomy = SpeciesTaxonomy(family = "Turdidae", familySv = "Trastar", genus = "Turdus", iocOrder = "Passeriformes"),
            name = if (locale == Locale.EN) "Common Blackbird" else "Koltrast",
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description = null,
            migration = null,
            images = listOf(image("Q25234", "hero", "hero.webp", "CC BY-SA 4.0", "Musicaline", "Turdus merula 1294.jpg")),
        )

    private fun rodhake(locale: Locale) =
        koltrast(locale).copy(
            id = SpeciesId("Q25334"),
            scientificName = "Erithacus rubecula",
            name = if (locale == Locale.EN) "European Robin" else "Rödhake",
            images = listOf(image("Q25334", "hero", "hero.webp", "Public domain", "Rob Hille", "Robin - Erithacus rubecula 1.jpg")),
        )

    @Composable
    private fun profile(
        species: Species,
        locale: Locale,
    ) {
        val vm =
            remember {
                SpeciesProfileViewModel(
                    repo = FakeSpeciesRepository().apply { byId.value = mapOf(species.id to species) },
                    speciesId = species.id,
                    locale = locale,
                )
            }
        SpeciesProfileScreen(viewModel = vm, locale = locale, onBack = {}, onPremiumClick = {}, showPremiumTeaser = false)
    }

    private val classification = Classification(listOf(ClassificationResult("Q25234", 0.94f)))

    @Composable
    private fun match(locale: Locale) {
        MatchView(
            state =
                MatchResultUiState.Match(
                    species = koltrast(locale),
                    confidence = 0.94f,
                    isManualPick = false,
                    isFirstSighting = true,
                    prevObservedAt = null,
                    sightingCount = 1,
                    stampNumber = 12,
                    frameJpegPath = null,
                    capturedAtMs = 1_800_000_000_000L,
                    source = ScanSource.Image("/fake/frame.jpg", classification),
                ),
            onSave = {},
            onCancel = {},
            onDismissUnlock = {},
            locale = locale,
            zone = TimeZone.UTC,
        )
    }

    @Composable
    private fun disambig(locale: Locale) {
        DisambigView(
            state =
                MatchResultUiState.Disambig(
                    candidates =
                        listOf(
                            ResolvedPrediction(koltrast(locale), 0.55f),
                            ResolvedPrediction(rodhake(locale), 0.30f),
                            ResolvedPrediction(stenfalk(locale), 0.15f),
                        ),
                    stampNumber = 12,
                    frameJpegPath = null,
                    capturedAtMs = 1_800_000_000_000L,
                    source = ScanSource.Image("/fake/frame.jpg", classification),
                ),
            onPick = {},
            onSaveAsUnknown = {},
            onUnknownSaved = {},
            onCancel = {},
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun credits_profile_top_sv() =
        compose.captureScreen("credits_profile_top_sv") {
            WithRealPhotos { profile(stenfalk(Locale.SV), Locale.SV) }
        }

    @Test
    @Config(qualifiers = "sv-w411dp-h1500dp-xxhdpi")
    fun credits_profile_full_sv() =
        compose.captureScreen("credits_profile_full_sv") {
            WithRealPhotos { profile(stenfalk(Locale.SV), Locale.SV) }
        }

    @Test
    @Config(qualifiers = "en-w411dp-h1500dp-xxhdpi")
    fun credits_profile_full_en() =
        compose.captureScreen("credits_profile_full_en") {
            WithRealPhotos { profile(stenfalk(Locale.EN), Locale.EN) }
        }

    @Test
    @Config(qualifiers = "sv-w411dp-h1500dp-xxhdpi")
    fun credits_profile_two_articles_sv() =
        compose.captureScreen("credits_profile_two_articles_sv") { WithRealPhotos { profile(talgoxe, Locale.SV) } }

    @Test
    @Config(qualifiers = "sv-w360dp-h2400dp-xxhdpi")
    fun credits_profile_full_sv_200() {
        RuntimeEnvironment.setFontScale(2f)
        compose.captureScreen("credits_profile_full_sv_200") { WithRealPhotos { profile(stenfalk(Locale.SV), Locale.SV) } }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun credits_match_sv() = compose.captureScreen("credits_match_sv") { WithRealPhotos { match(Locale.SV) } }

    @Test
    @Config(qualifiers = "+en")
    fun credits_match_en() = compose.captureScreen("credits_match_en") { WithRealPhotos { match(Locale.EN) } }

    @Test
    @Config(qualifiers = "sv-w360dp-h800dp-xhdpi")
    fun credits_match_sv_200() {
        RuntimeEnvironment.setFontScale(2f)
        compose.captureScreen("credits_match_sv_200") { WithRealPhotos { match(Locale.SV) } }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun credits_disambig_sv() = compose.captureScreen("credits_disambig_sv") { WithRealPhotos { disambig(Locale.SV) } }
}
