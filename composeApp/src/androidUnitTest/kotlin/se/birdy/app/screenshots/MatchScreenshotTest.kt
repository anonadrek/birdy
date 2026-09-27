package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.ui.match.DisambigView
import se.birdy.app.ui.match.MatchResultUiState
import se.birdy.app.ui.match.MatchView
import se.birdy.app.ui.match.NoBirdView
import se.birdy.app.ui.match.ResolvedPrediction
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource

/**
 * Result screens (spec 2026-09-24 §4.3): [MatchView] (photo hero + paper sheet + embossed
 * stamp), [DisambigView] (candidate cards) and [NoBirdView] (rotated frame + retry). Coil can't
 * load images under Robolectric (see [ComponentsScreenshotTest]) — the fake hero/frame paths
 * below render as their empty placeholder fills, which is expected.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class MatchScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun classification(
        speciesId: String,
        confidence: Float,
    ) = Classification(results = listOf(ClassificationResult(speciesId = speciesId, confidence = confidence)))

    private fun species(
        id: String,
        scientificName: String,
        nameSv: String,
        nameEn: String,
        locale: Locale,
    ): Species =
        Species(
            id = SpeciesId(id),
            scientificName = scientificName,
            taxonomy =
                SpeciesTaxonomy(
                    family = "Muscicapidae",
                    familySv = "Flugsnappare",
                    genus = scientificName.substringBefore(" "),
                    iocOrder = "Passeriformes",
                ),
            name = if (locale == Locale.EN) nameEn else nameSv,
            abundance = Abundance.ALLMÄN,
            iucnStatus = "LC",
            regions = listOf("SE"),
            season = emptyMap(),
            description = null,
            migration = null,
            images =
                listOf(
                    SpeciesImage(
                        role = "hero",
                        path = "$id/hero.webp",
                        width = 800,
                        height = 600,
                        license = "CC0",
                        author = "Test",
                        sourceUrl = "https://example.com",
                    ),
                ),
        )

    private fun robin(locale: Locale) = species("Q25998", "Erithacus rubecula", "Rödhake", "European Robin", locale)

    private fun blackbird(locale: Locale) = species("Q25234", "Turdus merula", "Koltrast", "Common Blackbird", locale)

    private fun blueTit(locale: Locale) = species("Q25404", "Cyanistes caeruleus", "Blåmes", "Eurasian Blue Tit", locale)

    private fun matchState(locale: Locale) =
        MatchResultUiState.Match(
            species = robin(locale),
            confidence = 0.94f,
            isManualPick = false,
            isFirstSighting = true,
            prevObservedAt = null,
            sightingCount = 1,
            stampNumber = 12,
            frameJpegPath = null,
            capturedAtMs = 1_800_000_000_000L,
            source = ScanSource.Image(frameJpegPath = "/fake/frame.jpg", classification = classification("Q25998", 0.94f)),
        )

    private fun disambigState(locale: Locale) =
        MatchResultUiState.Disambig(
            candidates =
                listOf(
                    ResolvedPrediction(species = robin(locale), confidence = 0.55f),
                    ResolvedPrediction(species = blackbird(locale), confidence = 0.30f),
                    ResolvedPrediction(species = blueTit(locale), confidence = 0.15f),
                ),
            stampNumber = 12,
            frameJpegPath = "/fake/frame.jpg",
            capturedAtMs = 1_800_000_000_000L,
            source = ScanSource.Image(frameJpegPath = "/fake/frame.jpg", classification = classification("Q25998", 0.55f)),
        )

    private fun noBirdState() =
        MatchResultUiState.NoBird(
            frameJpegPath = "/fake/frame.jpg",
            capturedAtMs = 1_800_000_000_000L,
            source = ScanSource.Image(frameJpegPath = "/fake/frame.jpg", classification = classification("Q25998", 0.2f)),
        )

    @Composable
    private fun match(locale: Locale) {
        MatchView(
            state = matchState(locale),
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
            state = disambigState(locale),
            onPick = {},
            onSaveAsUnknown = {},
            onUnknownSaved = {},
            onCancel = {},
        )
    }

    @Composable
    private fun nobird() {
        NoBirdView(
            state = noBirdState(),
            onRetry = {},
            zone = TimeZone.UTC,
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun match_sv() = compose.captureScreen("match_sv") { match(Locale.SV) }

    @Test
    @Config(qualifiers = "+en")
    fun match_en() = compose.captureScreen("match_en") { match(Locale.EN) }

    @Test
    @Config(qualifiers = "+sv")
    fun disambig_sv() = compose.captureScreen("disambig_sv") { disambig(Locale.SV) }

    @Test
    @Config(qualifiers = "+en")
    fun disambig_en() = compose.captureScreen("disambig_en") { disambig(Locale.EN) }

    @Test
    @Config(qualifiers = "+sv")
    fun nobird_sv() = compose.captureScreen("nobird_sv") { nobird() }

    @Test
    @Config(qualifiers = "+en")
    fun nobird_en() = compose.captureScreen("nobird_en") { nobird() }
}
