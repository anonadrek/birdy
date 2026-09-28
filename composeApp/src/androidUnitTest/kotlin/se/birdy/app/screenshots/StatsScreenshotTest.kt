package se.birdy.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.ui.components.hasForcedMidWordBreak
import se.birdy.app.ui.stats.SeasonStatsScreen
import se.birdy.app.ui.stats.SeasonStatsViewModel
import se.birdy.content.Locale

/**
 * Säsongsstatistik (spec 2026-09-24, Plan 2 T12): mässingspill i introns hörn, stora DM
 * Serif-siffror i totalraden, varje diagram i sitt eget [se.birdy.app.ui.components.SectionCard]
 * och en mässingsfärgad aktuell månad i månadsraden. Fem arter (samma fixtur som
 * [ArchiveScreenshotTest]/[LifelistScreenshotTest]: Talgoxe/Koltrast/Blåmes/Knölsvan/Tornfalk,
 * [FakeSpeciesRepository.withDefaults]) spridda över åtta av tolv månader 2026 med sinsemellan
 * olika antal (5/4/3/2/1) så månadsstaplarna, säsongsdonuten (alla fyra säsonger > 0), topplistans
 * mossfärgade stapel (moss only — mässing/brass finns bara i säsongsdonuten och den aktuella
 * månadens stapel/etikett) och den kumulativa linjen alla får meningsfull, olikstor data att
 * rita — se [seededObservationRepo] för exakt fördelning.
 *
 * Klockan är FAST (2026-08-20, inte [Clock.System]) eftersom "aktuell månad" annars hade drivit
 * i takt med riktig tid — samma skäl som varför den här skärmen redan tar in [Clock] som
 * konstruktorparameter i produktionskoden.
 *
 * [SeasonStatsViewModel.onEnter] kör sin `viewModelScope.launch` utan någon `delay()` — till
 * skillnad från [ArchiveScreenshotTest]s sökdebounce behövs ingen [captureScreen] `settle`-hook
 * här (samma icke-behov som [LifelistScreenshotTest]). [stats_sv_tall] finns eftersom
 * `LazyColumn` är virtualiserad: allt under den fasta w411dp-h891dp-vyn (topplistan, den
 * kumulativa linjen) komponeras aldrig alls annars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class StatsScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    /**
     * Talgoxe(Q25485)=5, Koltrast(Q25234)=4, Blåmes(Q25404)=3, Knölsvan(Q25402)=2,
     * Tornfalk(Q26490)=1 — 15 observationer, fem unika arter, spridda jan–okt 2026 så alla fyra
     * meteorologiska säsonger (vinter/vår/sommar/höst) och den kumulativa unika-arter-linjen
     * (2→3→4→4→4→5→…→5) får icke-trivial data.
     */
    private fun seededObservationRepo(): FakeObservationRepository =
        FakeObservationRepository().apply {
            seedObservation("Q25485", Instant.parse("2026-01-10T08:00:00Z"))
            seedObservation("Q25485", Instant.parse("2026-02-05T08:00:00Z"))
            seedObservation("Q25485", Instant.parse("2026-03-12T08:00:00Z"))
            seedObservation("Q25485", Instant.parse("2026-05-20T08:00:00Z"))
            seedObservation("Q25485", Instant.parse("2026-08-08T08:00:00Z"))
            seedObservation("Q25234", Instant.parse("2026-01-15T08:00:00Z"))
            seedObservation("Q25234", Instant.parse("2026-04-03T08:00:00Z"))
            seedObservation("Q25234", Instant.parse("2026-06-10T08:00:00Z"))
            seedObservation("Q25234", Instant.parse("2026-10-05T08:00:00Z"))
            seedObservation("Q25404", Instant.parse("2026-02-20T08:00:00Z"))
            seedObservation("Q25404", Instant.parse("2026-05-05T08:00:00Z"))
            seedObservation("Q25404", Instant.parse("2026-07-15T08:00:00Z"))
            seedObservation("Q25402", Instant.parse("2026-03-25T08:00:00Z"))
            seedObservation("Q25402", Instant.parse("2026-08-15T08:00:00Z"))
            seedObservation("Q26490", Instant.parse("2026-06-25T08:00:00Z"))
        }

    private fun fixedClock(iso: String): Clock {
        val instant = Instant.parse(iso)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }

    private fun viewModel(
        locale: Locale,
        observationRepo: FakeObservationRepository,
    ) = SeasonStatsViewModel(
        observationRepo = observationRepo,
        speciesRepo = FakeSpeciesRepository.withDefaults(),
        clock = fixedClock("2026-08-20T08:00:00Z"),
        zone = TimeZone.UTC,
        locale = locale,
    )

    @Composable
    private fun screen(
        locale: Locale,
        observationRepo: FakeObservationRepository = seededObservationRepo(),
    ) {
        val vm = remember { viewModel(locale, observationRepo) }
        SeasonStatsScreen(viewModel = vm, onBack = {})
    }

    /**
     * [se.birdy.app.ui.stats.SeasonStatsScreen]'s content is a `LazyColumn` taller than the
     * fixed w411dp-h891dp viewport — [ArchiveScreenshotTest]/[LifelistScreenshotTest]'s "assert
     * a name exists" pattern only works for content that fits above the fold. "15" (total
     * observations) is the first proof that `Loaded` (not `Loading`) actually rendered; it's
     * visible without scrolling in every variant below.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun stats_sv() {
        compose.captureScreen("stats_sv") { screen(Locale.SV) }
        compose.onNodeWithText("15").assertExists()
        // T12c I-A: MicroLabel's `heading()` modifier landed on a Row that doesn't merge its
        // children, so the node TalkBack would find via this exact text had no `heading()` on
        // it — the merged-tree node carrying "OBSERVATIONER PER MÅNAD" (MicroLabel's uppercased
        // stats_section_months) must itself report isHeading().
        compose.onNodeWithText("OBSERVATIONER PER MÅNAD").assert(isHeading())
    }

    /**
     * T12c M2: with the PREMIUM badge eating into the top bar's width, the single-word title
     * "Säsongsstatistik" wrapped mid-word at a narrow phone width under a large system font
     * scale before the top bar's title switched to `BasicText` + `TextAutoSize.StepBased`
     * (same pattern as [se.birdy.app.ui.premium.PremiumScreen]'s price line). "15" existing
     * without wrapping/clipping is the visual proof; there's no production testTag to assert
     * a single-line layout directly.
     */
    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun stats_w360_sv_150() {
        RuntimeEnvironment.setFontScale(1.5f)
        compose.captureScreen("stats_w360_sv_150") { screen(Locale.SV) }
        compose.onNodeWithText("15").assertExists()
        compose.assertNoForcedMidWordBreaks()
    }

    /**
     * T12d Important 1: the re-review measured that 3-letter month labels (MAR/MAY) and the
     * "OBSERVATIONER"/"OBSERVATIONS" totals label both force a mid-word break at large system
     * font scales on a narrow phone — [stats_w360_sv_150] asserted only that "15" rendered, so
     * the broken labels went uncaught. 2.0x is the largest scale the review measured (also the
     * first case that exercises the top bar title's own autosize floor) — this is the ceiling
     * this wave targets, not merely one more step past 1.5x.
     */
    @Test
    @Config(qualifiers = "+sv-w360dp")
    fun stats_w360_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("stats_w360_sv_200") { screen(Locale.SV) }
        compose.onNodeWithText("15").assertExists()
        compose.assertNoForcedMidWordBreaks()
    }

    @Test
    @Config(qualifiers = "+en")
    fun stats_en() {
        compose.captureScreen("stats_en") { screen(Locale.EN) }
        compose.onNodeWithText("15").assertExists()
    }

    /**
     * A tall-viewport variant so the whole screen composes in one go — [SeasonStatsScreen]'s
     * `LazyColumn` is virtualized, so a scroll-based capture at the normal w411dp-h891dp height
     * would need a real [androidx.compose.ui.test.performScrollToIndex] (which needs a
     * `testTag` this production screen doesn't otherwise need); a taller Robolectric qualifier
     * is a test-only knob and reaches the same goal without touching production code. This is
     * the only way to see "Mest sedda arter"'s new mossfärgade progress-bar rows and the
     * cumulative-line [se.birdy.app.ui.components.SectionCard] in a screenshot at all.
     */
    @Test
    @Config(qualifiers = "+sv-h2600dp")
    fun stats_sv_tall() {
        compose.captureScreen("stats_sv_tall") { screen(Locale.SV) }
        compose.onNodeWithText("Tornfalk").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun stats_empty_sv() {
        compose.captureScreen("stats_empty_sv") {
            screen(Locale.SV, observationRepo = FakeObservationRepository())
        }
        // T12b: "Talgoxe" absent would also pass while the screen is still Loading — assert the
        // empty-state headline itself ("Inget att *kartlägga* ännu.") to prove Empty actually
        // rendered. "kartlägga" is JournalHeadline's one accent segment (parseJournalHeadline
        // splits on the `*...*` markers with no surrounding whitespace inside the word itself).
        compose.onNodeWithText("kartlägga").assertExists()
        compose.onNodeWithText("Talgoxe").assertDoesNotExist()
    }

    /**
     * T12d Important 1: generic guard for the whole composed screen, not just the month
     * labels/totals label this wave fixes — walks every node that carries a
     * `GetTextLayoutResult` semantics action (i.e. every laid-out [Text]/[BasicText]),
     * fetches its real [TextLayoutResult] and reuses [hasForcedMidWordBreak] (promoted from
     * `private` to `internal` in `PhotoHero.kt` for this) to fail on any text whose wrap broke
     * mid-word rather than at a space. `useUnmergedTree = true` so a merged container (e.g. a
     * card `semantics(mergeDescendants = true)`) doesn't hide a child's own layout node.
     */
    private fun ComposeContentTestRule.assertNoForcedMidWordBreaks() {
        val layoutResults = mutableListOf<TextLayoutResult>()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action ?: return@forEach
                layoutResults.clear()
                action(layoutResults)
                layoutResults.forEach { result ->
                    val text = result.layoutInput.text.text
                    assertFalse(
                        "\"$text\" has a forced mid-word break",
                        result.hasForcedMidWordBreak(text),
                    )
                }
            }
    }
}
