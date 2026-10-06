package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.data.premium.FormattedPrices
import se.birdy.app.data.premium.PurchaseResult
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePremiumRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.LifelistViewModel
import se.birdy.app.ui.listen.ListenLauncherScreen
import se.birdy.app.ui.listen.ListenLauncherViewModel
import se.birdy.app.ui.match.MatchResultUiState
import se.birdy.app.ui.match.MatchView
import se.birdy.app.ui.premium.PremiumScreen
import se.birdy.app.ui.premium.PremiumThankYouScreen
import se.birdy.app.ui.premium.PremiumViewModel
import se.birdy.app.ui.profile.SpeciesProfileScreen
import se.birdy.app.ui.profile.SpeciesProfileViewModel
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTaxonomy
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.dailybird.DailyBird
import se.birdy.domain.dailybird.SeasonTag
import se.birdy.domain.observation.Observation
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Every screen that shows a bird photo, with the REAL shipped photo behind it (see
 * [WithRealPhotos]) — the other screenshot tests only show the empty placeholder, so they can't
 * show what an overlay does to the bird. Added 2026-10-06 for "fågelfotona i sina riktiga
 * färger" (Albin: the green over the bird made the species hard to see). Covers a light photo
 * (Talgoxe) and a dark one (Koltrast) on the species profile, the Identify tab's daily bird,
 * Match, Premium + the thank-you screen, and Mina arter (the weekly recap card's blurred find
 * photos + the stamp photos).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class RealPhotoScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun image(
        id: String,
        role: String,
        file: String,
    ) = SpeciesImage(
        role = role,
        path = "$id/$file",
        width = 800,
        height = 600,
        license = "CC BY-SA 4.0",
        author = "Test",
        sourceUrl = "https://example.com",
    )

    private fun species(
        id: String,
        scientificName: String,
        name: String,
        family: String,
        familySv: String,
        description: String,
    ) = Species(
        id = SpeciesId(id),
        scientificName = scientificName,
        taxonomy =
            SpeciesTaxonomy(
                family = family,
                familySv = familySv,
                genus = scientificName.substringBefore(" "),
                iocOrder = "Passeriformes",
            ),
        name = name,
        abundance = Abundance.ALLMÄN,
        iucnStatus = "LC",
        regions = listOf("SE"),
        season = emptyMap(),
        description = description,
        migration = "Mestadels stannfågel.",
        images =
            listOf(
                image(id, "hero", "hero.webp"),
                image(id, "secondary", "secondary-1.webp"),
                image(id, "secondary", "secondary-2.webp"),
            ),
    )

    private val talgoxe =
        species(
            "Q25485",
            "Parus major",
            "Talgoxe",
            "Paridae",
            "Mesar",
            "Talgoxen är en av Sveriges vanligaste tättingar, en flitig gäst vid fågelbordet och i trädgården hela året.",
        )

    private val koltrast =
        species(
            "Q25234",
            "Turdus merula",
            "Koltrast",
            "Turdidae",
            "Trastar",
            "Koltrasthanen är helsvart med gul näbb, honan mörkbrun. Sången hörs från takåsar en vårkväll.",
        )

    private val rodhake =
        species(
            "Q25334",
            "Erithacus rubecula",
            "Rödhake",
            "Muscicapidae",
            "Flugsnappare",
            "Rödhaken känns igen på sitt orangeröda bröst.",
        )

    @Composable
    private fun profile(species: Species) {
        val vm =
            remember {
                SpeciesProfileViewModel(
                    repo = FakeSpeciesRepository().apply { byId.value = mapOf(species.id to species) },
                    speciesId = species.id,
                    locale = Locale.SV,
                )
            }
        SpeciesProfileScreen(viewModel = vm, locale = Locale.SV, onBack = {}, onPremiumClick = {}, showPremiumTeaser = false)
    }

    @Composable
    private fun identify() {
        val vm =
            remember {
                ListenLauncherViewModel(
                    selectDailyBird = { DailyBird(speciesId = "Q27236", seasonTag = SeasonTag.PRESENT) },
                    getSpeciesName = { "Sävsångare" },
                    getSpeciesHeroPath = { "Q27236/hero.webp" },
                    recordDailyBirdShown = { _, _ -> },
                    dailyBirdMatchCount = { 0 },
                    isDailyBirdCaught = { false },
                    huntTarget = 3,
                )
            }
        ListenLauncherScreen(
            viewModel = vm,
            onCameraClick = {},
            onPhotoClick = {},
            onSettingsClick = {},
            onNavigateToAudioScan = {},
            onSpeciesProfileClick = {},
        )
    }

    @Composable
    private fun match() {
        val classification = Classification(results = listOf(ClassificationResult(speciesId = "Q25334", confidence = 0.94f)))
        MatchView(
            state =
                MatchResultUiState.Match(
                    species = rodhake,
                    confidence = 0.94f,
                    isManualPick = false,
                    isFirstSighting = true,
                    prevObservedAt = null,
                    sightingCount = 1,
                    stampNumber = 12,
                    frameJpegPath = null,
                    capturedAtMs = 1_800_000_000_000L,
                    source = ScanSource.Image(frameJpegPath = "/fake/frame.jpg", classification = classification),
                ),
            onSave = {},
            onCancel = {},
            onDismissUnlock = {},
            locale = Locale.SV,
            zone = TimeZone.UTC,
        )
    }

    @Composable
    private fun premium() {
        val vm =
            remember {
                PremiumViewModel(
                    FakePremiumRepository(),
                    launchPurchase = { PurchaseResult.UserCancelled },
                    formattedPricesFlow = MutableStateFlow(FormattedPrices("199 kr", "499 kr")),
                )
            }
        PremiumScreen(viewModel = vm, onClose = {}, onPurchaseComplete = {})
    }

    private fun observation(
        id: String,
        speciesId: String,
        stampNumber: Int,
        savedAt: Instant,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = savedAt,
        savedAt = savedAt,
        photoPath = "/fake/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = stampNumber,
    )

    @Composable
    private fun lifelist() {
        val vm =
            remember {
                val now = Clock.System.now()
                val repo = FakeObservationRepository()
                repo.seed(
                    listOf(
                        observation("o1", "Q25485", stampNumber = 3, savedAt = now.minus(2.hours)),
                        observation("o2", "Q25234", stampNumber = 2, savedAt = now.minus(20.hours)),
                        observation("o3", "Q25404", stampNumber = 1, savedAt = now.minus(40.days)),
                    ),
                )
                LifelistViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    prefs =
                        FakeUserPreferences().apply {
                            userNameValue = "Albin"
                            lifelistStat3Value = LifelistStat3Choice.STREAK
                            lifelistSortValue = LifelistSort.RECENT
                        },
                )
            }
        LifelistScreen(viewModel = vm, onObservationClick = {}, onScanCtaClick = {}, onPremiumClick = {}, showPremiumTeaser = false)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_identify_sv() = compose.captureScreen("foto_identify_sv") { WithRealPhotos { identify() } }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_profile_talgoxe_sv() = compose.captureScreen("foto_profile_talgoxe_sv") { WithRealPhotos { profile(talgoxe) } }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_profile_koltrast_sv() = compose.captureScreen("foto_profile_koltrast_sv") { WithRealPhotos { profile(koltrast) } }

    // Robolectric reports no status bar, so the other captures never show the thin scrim under
    // the status-bar icons. This one hands the view a 32dp status-bar inset (96px at xxhdpi; a
    // real phone's is ~24 to 40dp) so that strip can be seen over a bright photo (Koltrast:
    // sunlit grass).
    @Test
    @Config(qualifiers = "+sv")
    fun foto_profile_koltrast_statusbar_sv() {
        val inset = StatusBarInset(topPx = 96)
        compose.captureScreen(
            "foto_profile_koltrast_statusbar_sv",
            settle = {
                compose.waitForIdle()
                compose.runOnIdle { inset.apply() }
            },
        ) {
            inset.Capture()
            WithRealPhotos { profile(koltrast) }
        }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_match_sv() = compose.captureScreen("foto_match_sv") { WithRealPhotos { match() } }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_premium_sv() = compose.captureScreen("foto_premium_sv") { WithRealPhotos { premium() } }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_thanks_sv() = compose.captureScreen("foto_thanks_sv") { WithRealPhotos { PremiumThankYouScreen(onClose = {}) } }

    @Test
    @Config(qualifiers = "+sv")
    fun foto_lifelist_sv() =
        compose.captureScreen("foto_lifelist_sv") {
            WithRealPhotos(findPhotos = mapOf("o1.jpg" to "talgoxe.jpg", "o2.jpg" to "koltrast.jpg", "o3.jpg" to "blames.jpg")) {
                lifelist()
            }
        }
}
