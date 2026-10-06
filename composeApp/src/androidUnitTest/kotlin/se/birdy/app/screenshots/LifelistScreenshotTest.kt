package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.LifelistViewModel
import se.birdy.app.ui.stats.SeasonStatsUiState
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.observation.Observation
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Mina arter (spec 2026-09-24 §4.3): stora DM Serif-siffror i statraden, veckans foto-uppslag
 * (recap-kortet) och lugna hårlinjerader per fynd. Coil laddar inga bilder under Robolectric
 * (se [ComponentsScreenshotTest]) — recap-kortets bakgrundsfoto och MiniStamp-plåtfotona
 * renderas som sina tomma platshållarfyllningar, vilket är förväntat.
 *
 * Datumen är relativa till [Clock.System.now] (inte fasta datum) eftersom [LifelistScreen]s
 * relativtids-tickare och månadsgruppering läser den riktiga klockan (se `produceState` i
 * `LoadedLifelist`) — ett fast datum hade blivit ett allt äldre "för N dagar sedan" i taget.
 * o1/o2 hamnar normalt i samma kalendermånad (timmar isär) och o3 40 dagar bak i en annan
 * (ingen månad har fler än 31 dagar) — övar flera `stickyHeader`-rubriker. Två körningsberoende
 * kanter, ofarliga för testet men värda att känna till: körs det före kl 20 lokal tid den 1:a i
 * månaden hamnar o1/o2 i olika månader (o2 = now-20h faller då på förra månadens sista dag); och
 * recap-kortets "N fynd den här veckan" kan visa 1 i stället för 2 tidigt en måndagmorgon (o2
 * kan då falla i föregående ISO-vecka).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LifelistScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun observation(
        id: String,
        speciesId: String,
        confidence: Float,
        stampNumber: Int,
        savedAt: Instant,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = savedAt,
        savedAt = savedAt,
        photoPath = "/fake/$id.jpg",
        note = "",
        confidence = confidence,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = stampNumber,
    )

    private fun loadedRepo(): FakeObservationRepository {
        val now = Clock.System.now()
        val repo = FakeObservationRepository()
        repo.seed(
            listOf(
                // MatchHigh (>=80%), denna månad.
                observation("o1", "Q25485", confidence = 0.94f, stampNumber = 3, savedAt = now.minus(2.hours)),
                // MatchMid (60-79%), samma månad som o1.
                observation("o2", "Q25234", confidence = 0.72f, stampNumber = 2, savedAt = now.minus(20.hours)),
                // MatchLow (<60%), en annan månad (40 dagar bak).
                observation("o3", "Q25404", confidence = 0.45f, stampNumber = 1, savedAt = now.minus(40.days)),
            ),
        )
        return repo
    }

    private fun viewModel(
        repo: FakeObservationRepository,
        userName: String = "Albin",
    ) = LifelistViewModel(
        observationRepo = repo,
        speciesRepo = FakeSpeciesRepository.withDefaults(),
        prefs =
            FakeUserPreferences().apply {
                userNameValue = userName
                lifelistStat3Value = LifelistStat3Choice.STREAK
                lifelistSortValue = LifelistSort.RECENT
            },
    )

    @Composable
    private fun loadedScreen(userName: String = "Albin") {
        val vm = remember { viewModel(loadedRepo(), userName) }
        LifelistScreen(
            viewModel = vm,
            onObservationClick = {},
            onScanCtaClick = {},
            onPremiumClick = {},
        )
    }

    @Composable
    private fun emptyScreen() {
        val vm = remember { viewModel(FakeObservationRepository()) }
        LifelistScreen(
            viewModel = vm,
            onObservationClick = {},
            onScanCtaClick = {},
            onPremiumClick = {},
        )
    }

    /**
     * T12b: minimal [SeasonStatsUiState.Loaded] fixture for [LiveStatsPreview] — only
     * `totalSpeciesThisYear` and `monthBars` are read by that composable; the rest are
     * present only because the sealed state requires them.
     *
     * T12d Important 2: `label` used to be the raw `month.toString()` ("6"), which is never
     * what the real app shows — [se.birdy.app.ui.stats.SeasonStatsViewModel] always resolves a
     * three-letter abbreviation (`SV_MONTHS`/`EN_MONTHS`). This screen is captured with
     * `@Config(qualifiers = "+sv")`, so the fixture uses the same Swedish abbreviations.
     */
    private fun livePreviewFixture(): SeasonStatsUiState.Loaded {
        val svMonths = listOf("JAN", "FEB", "MAR", "APR", "MAJ", "JUN", "JUL", "AUG", "SEP", "OKT", "NOV", "DEC")
        val bars =
            (1..12).map { month ->
                SeasonStatsUiState.MonthBar(
                    month = month,
                    label = svMonths[month - 1],
                    observationCount = if (month <= 6) month else 0,
                    isCurrent = month == 6,
                )
            }
        return SeasonStatsUiState.Loaded(
            totalSpeciesThisYear = 3,
            totalObservationsThisYear = 21,
            monthBars = bars,
            seasons = SeasonStatsUiState.SeasonBreakdown(winter = 3, spring = 6, summer = 9, autumn = 3),
            topSpecies = emptyList(),
            firstSightings = emptyList(),
            year = 2026,
            bestMonth = 6,
        )
    }

    @Composable
    private fun loadedScreenWithLivePreview() {
        val vm = remember { viewModel(loadedRepo()) }
        LifelistScreen(
            viewModel = vm,
            onObservationClick = {},
            onScanCtaClick = {},
            onPremiumClick = {},
            showPremiumTeaser = false,
            livePreviewState = livePreviewFixture(),
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_sv() = compose.captureScreen("lifelist_sv") { loadedScreen() }

    @Test
    @Config(qualifiers = "+en")
    fun lifelist_en() = compose.captureScreen("lifelist_en") { loadedScreen() }

    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_empty_sv() = compose.captureScreen("lifelist_empty_sv") { emptyScreen() }

    /**
     * Regression for the T8d CRITICAL fix: onboarding builds before 1.3.0 persisted the literal
     * fallback word "Min" as `userName` for anyone who skipped the name field (from 1.3.0 it
     * stores "" instead — T8f — but already-installed users can still have "Min" on disk) — this
     * must render as the anonymous "*Min* dagbok." headline (`displayNameOrNull`), not the
     * genitive "*Mins* dagbok." that a plain `possessive("Min", ...)` would have produced.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_skipped_name_sv() = compose.captureScreen("lifelist_skipped_name_sv") { loadedScreen(userName = "Min") }

    /**
     * T8f: "My" is a real Swedish given name — unlike "Min", it must NOT be masked in the
     * Swedish UI (it's only ever onboarding's fallback word in English). A Swedish user actually
     * named My gets the normal genitive headline "*Mys* dagbok.", not the anonymous one.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_name_my_sv() = compose.captureScreen("lifelist_name_my_sv") { loadedScreen(userName = "My") }

    /**
     * T9d: onboarding stores "" (not a fallback word) when the name field is skipped, from
     * 1.3.0 on — and [LifelistViewModel] no longer invents "Min"/"My" for a blank stored name
     * either (that fallback was removed as redundant with `displayNameOrNull`'s own blank check,
     * and only worked by coincidence of its hardcoded words matching the masked set). A blank
     * `userName` must still land on the same anonymous headline as a masked historical value.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_blank_name_sv() = compose.captureScreen("lifelist_blank_name_sv") { loadedScreen(userName = "") }

    @Test
    @Config(qualifiers = "+en")
    fun lifelist_blank_name_en() = compose.captureScreen("lifelist_blank_name_en") { loadedScreen(userName = "") }

    /**
     * T12b review fix: [se.birdy.app.ui.stats.LiveStatsPreview] had no screenshot coverage at
     * all before this — the SandCreme-surface regression (invisible axis, sub-3:1 current-month
     * bar) went unnoticed because nothing ever rendered it. Asserts the preview's own content
     * (the "Open stats" link) actually rendered before trusting the capture.
     *
     * T12c I-B: the un-scrolled capture cropped the card at its first bars (it starts around
     * 775dp into the fixed 891dp viewport) — the axis, zero-observation months and the
     * current-month label (the whole point of T12b's CardPaper/OutlineInk fix) were below the
     * fold and invisible in the PNG. Scrolling the LazyColumn to the card before capturing shows
     * the whole thing, real phone frame and all.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun lifelist_live_preview_sv() {
        compose.captureScreen(
            "lifelist_live_preview_sv",
            settle = {
                compose
                    .onNode(hasScrollToIndexAction())
                    .performScrollToNode(hasText("Öppna statistik", substring = true))
            },
        ) { loadedScreenWithLivePreview() }
        compose.onNodeWithText("Öppna statistik", substring = true).assertExists()
    }
}
