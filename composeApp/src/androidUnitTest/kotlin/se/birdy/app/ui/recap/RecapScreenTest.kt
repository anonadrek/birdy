package se.birdy.app.ui.recap

import androidx.compose.runtime.remember
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import kotlinx.datetime.Instant
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.assertNoTextLayoutRegressions
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7j: the weekly recap as "Uppslag 1" (Albin's choice 2026-10-07), in the normal
 * test gate. The header numbers and their plurals in both languages, the Monday-to-Sunday strip
 * (Monday first, local days across a clock change), new species with their life-list numbers, the
 * week's stamp or its empty state, the grid of finds and what a tap opens, the quiet week, and the
 * large-text guard at 1.3x/1.5x/2.0x on a narrow phone. A tall viewport composes the whole page.
 * The opt-in RecapScreenshotTest renders the same states to PNG.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2600dp-xxhdpi")
class RecapScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var cameraOpened = 0

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun show(
        observations: FakeObservationRepository = RecapFixtures.mockupWeekRepo(),
        badges: FakeBadgeRepository = RecapFixtures.weekStampRepo(),
        species: Map<SpeciesId, SpeciesSummary> = RecapFixtures.species(),
        now: Instant = RecapFixtures.sundayEvening,
    ) {
        compose.setContent {
            BirdyTheme {
                val vm = remember { RecapFixtures.viewModel(observations, badges, species, now) }
                RecapScreen(
                    viewModel = vm,
                    onOpenCamera = { cameraOpened++ },
                    onObservationClick = { opened += it },
                    onBack = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun SemanticsNodeInteraction.left(): Dp = getUnclippedBoundsInRoot().left

    private fun SemanticsNodeInteraction.top(): Dp = getUnclippedBoundsInRoot().top

    // ── The header ───────────────────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "+sv")
    fun `the header names the week, its dates and the days out`() {
        show()
        compose.onNodeWithText("FÄLTRAPPORT · VECKA\u00A041").assertExists()
        // JournalHeadline sets the accent word as its own text: the merged headline holds "fält".
        compose.onNodeWithText("fält").assert(isHeading())
        compose.onNodeWithText("5 till 11\u00A0oktober. 5\u00A0dagar ute.").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the three numbers are read as one sentence`() {
        show()
        compose.onNodeWithContentDescription("7 fynd, 3 nya arter, 4 veckor i rad.").assertExists()
        compose.onNodeWithText("+3 fynd mot förra veckan").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the header in English`() {
        show(species = RecapFixtures.species(english = true))
        compose.onNodeWithText("FIELD REPORT · WEEK\u00A041").assertExists()
        compose.onNodeWithText("5 to 11\u00A0October. Out on 5\u00A0days.").assertExists()
        compose.onNodeWithContentDescription("7 sightings, 3 new species, 4 weeks in a row.").assertExists()
        compose.onNodeWithText("+3 sightings compared with last week").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `one of everything reads in the singular`() {
        show(observations = RecapFixtures.firstFindRepo(), badges = FakeBadgeRepository())
        compose.onNodeWithContentDescription("1 fynd, 1 ny art, 1 vecka i rad.").assertExists()
        compose.onNodeWithText("5 till 11\u00A0oktober. 1\u00A0dag ute.").assertExists()
        compose.onNodeWithText("+1 fynd mot förra veckan").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `one of everything in English`() {
        show(observations = RecapFixtures.firstFindRepo(), badges = FakeBadgeRepository(), species = RecapFixtures.species(english = true))
        compose.onNodeWithContentDescription("1 sighting, 1 new species, 1 week in a row.").assertExists()
        compose.onNodeWithText("5 to 11\u00A0October. Out on 1\u00A0day.").assertExists()
        compose.onNodeWithText("+1 sighting compared with last week").assertExists()
    }

    // ── The Monday-to-Sunday strip ───────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "+sv")
    fun `the strip runs Monday to Sunday and says each day's finds`() {
        show()
        val days =
            listOf(
                "måndag 5 oktober, 1 fynd",
                "tisdag 6 oktober, 1 fynd",
                "onsdag 7 oktober, inga fynd",
                "torsdag 8 oktober, 1 fynd",
                "fredag 9 oktober, inga fynd",
                "lördag 10 oktober, 2 fynd",
                "söndag 11 oktober, 2 fynd",
            ).map { compose.onNodeWithContentDescription(it) }
        val lefts = days.map { it.left() }
        assertEquals(lefts.sorted(), lefts, "Monday first, Sunday last")
        assertTrue(lefts.zipWithNext().all { (a, b) -> a < b }, "seven separate days")
    }

    @Test
    @Config(qualifiers = "+en")
    fun `the strip in English`() {
        show(species = RecapFixtures.species(english = true))
        compose.onNodeWithContentDescription("Monday 5 October, 1 sighting").assertExists()
        compose.onNodeWithContentDescription("Saturday 10 October, 2 sightings").assertExists()
        compose.onNodeWithContentDescription("Wednesday 7 October, no sightings").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `days still to come are said as such`() {
        // Wednesday 7 October at noon: Thursday to Sunday haven't happened yet.
        show(observations = RecapFixtures.firstFindRepo(), badges = FakeBadgeRepository(), now = Instant.parse("2026-10-07T10:00:00Z"))
        compose.onNodeWithContentDescription("onsdag 7 oktober, 1 fynd").assertExists()
        compose.onNodeWithContentDescription("tisdag 6 oktober, inga fynd").assertExists()
        compose.onNodeWithContentDescription("torsdag 8 oktober, inte än").assertExists()
        compose.onNodeWithContentDescription("söndag 11 oktober, inte än").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the week the clocks go back keeps its local days`() {
        // Week 43: Monday 19 to Sunday 25 October 2026, winter time from 03:00 on the Sunday.
        val observations =
            FakeObservationRepository().apply {
                seed(
                    listOf(
                        // Monday 00:15 in Stockholm (still Sunday 18 October in UTC).
                        RecapFixtures.observation("monday-night", RecapFixtures.TALGOXE, "2026-10-18T22:15:00Z"),
                        // Sunday 23:30 CET, after the change (22:30 UTC).
                        RecapFixtures.observation("sunday-late", RecapFixtures.RODHAKE, "2026-10-25T22:30:00Z"),
                    ),
                )
            }
        show(observations = observations, badges = FakeBadgeRepository(), now = Instant.parse("2026-10-25T22:45:00Z"))
        compose.onNodeWithText("FÄLTRAPPORT · VECKA\u00A043").assertExists()
        compose.onNodeWithText("19 till 25\u00A0oktober. 2\u00A0dagar ute.").assertExists()
        compose.onNodeWithContentDescription("måndag 19 oktober, 1 fynd").assertExists()
        compose.onNodeWithContentDescription("söndag 25 oktober, 1 fynd").assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a week across two months names both`() {
        // Week 40: Monday 28 September to Sunday 4 October 2026.
        show(observations = FakeObservationRepository(), badges = FakeBadgeRepository(), now = Instant.parse("2026-10-04T10:00:00Z"))
        compose.onNodeWithText("28\u00A0september till 4\u00A0oktober.").assertExists()
        compose.onNodeWithContentDescription("måndag 28 september, inga fynd").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `a week across two months in English`() {
        show(observations = FakeObservationRepository(), badges = FakeBadgeRepository(), now = Instant.parse("2026-10-04T10:00:00Z"))
        compose.onNodeWithText("28\u00A0September to 4\u00A0October.").assertExists()
    }

    // ── New species, the stamp, the grid ─────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "+sv")
    fun `new species are listed with their place in the life list, latest first`() {
        show()
        compose.onNodeWithText("NYA I LIVSLISTAN").assert(isHeading())
        compose.onNodeWithText("№\u00A031 i livslistan · lör 10\u00A0okt").assertExists()
        compose.onNodeWithText("№\u00A030 i livslistan · tors 8\u00A0okt").assertExists()
        compose.onNodeWithText("№\u00A029 i livslistan · tis 6\u00A0okt").assertExists()
        val sidensvans = compose.onNodeWithContentDescription("Sidensvans, nummer 31 i livslistan, först sedd lördag 10 oktober")
        val stjartmes = compose.onNodeWithContentDescription("Stjärtmes, nummer 30 i livslistan, först sedd torsdag 8 oktober")
        val domherre = compose.onNodeWithContentDescription("Domherre, nummer 29 i livslistan, först sedd tisdag 6 oktober")
        assertTrue(sidensvans.top() < stjartmes.top() && stjartmes.top() < domherre.top())
        // Talgoxe, Rödhake and Större hackspett were on the list before this week.
        compose.onAllNodesWithText("i livslistan", substring = true).assertCountEquals(3)
    }

    @Test
    @Config(qualifiers = "+en")
    fun `new species in English`() {
        show(species = RecapFixtures.species(english = true))
        compose.onNodeWithText("NEW ON YOUR LIFE LIST").assertExists()
        compose.onNodeWithText("№\u00A031 on your life list · Sat 10\u00A0Oct").assertExists()
        compose
            .onNodeWithContentDescription("Bohemian Waxwing, number 31 on your life list, first seen Saturday 10 October")
            .assertExists()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a new species opens the find that put it on the list`() {
        show()
        compose.onNodeWithContentDescription("Stjärtmes, nummer 30 i livslistan, först sedd torsdag 8 oktober").performClick()
        assertEquals(listOf("thu-stjartmes"), opened)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the week's stamp is shown with its number`() {
        show()
        val number = RecapFixtures.stampNumberOf("weekly_streak_4")
        compose.onNodeWithText("Månads-rytm").assertExists()
        compose.onNodeWithContentDescription("Ny stämpel: Månads-rytm, nummer $number.", substring = true).assertExists()
        compose.onNodeWithText("Ingen ny stämpel den här veckan.").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a week without a new stamp says so`() {
        show(badges = FakeBadgeRepository())
        compose.onNodeWithText("Ingen ny stämpel den här veckan.").assertExists()
        compose.onNodeWithText("VECKANS STÄMPEL").assertExists()
        compose.onNodeWithText("Månads-rytm").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `every find is a square in the grid, labelled with its species and day`() {
        show()
        compose.onNodeWithText("ALLA FYND · 7").assert(isHeading())
        // TalkBack reads the heading as words, not the caps with their middle dot.
        compose.onNodeWithContentDescription("Alla fynd, 7").assert(isHeading())
        listOf(
            "Rödhake, söndag 11 oktober",
            "Talgoxe, söndag 11 oktober",
            "Sidensvans, lördag 10 oktober",
            "Större hackspett, lördag 10 oktober",
            "Stjärtmes, torsdag 8 oktober",
            "Domherre, tisdag 6 oktober",
            "Talgoxe, måndag 5 oktober",
        ).forEach { compose.onNodeWithContentDescription(it).assert(hasClickAction()) }
        // Seven grid squares plus three new species rows can be tapped.
        compose
            .onAllNodes(hasClickAction() and hasContentDescription("oktober", substring = true))
            .assertCountEquals(10)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a square opens its find`() {
        show()
        compose.onNodeWithContentDescription("Rödhake, söndag 11 oktober").performClick()
        compose.onNodeWithContentDescription("Talgoxe, måndag 5 oktober").performClick()
        assertEquals(listOf("sun-rodhake", "mon-talgoxe"), opened)
    }

    // ── The quiet week ───────────────────────────────────────────────────────────────────────────

    @Test
    @Config(qualifiers = "+sv")
    fun `a quiet week keeps the strip and asks for the first find`() {
        show(observations = RecapFixtures.quietWeekRepo(), badges = FakeBadgeRepository())
        compose.onNodeWithText("lugn").assert(isHeading())
        compose.onNodeWithText("5 till 11\u00A0oktober.").assertExists()
        compose.onNodeWithContentDescription("måndag 5 oktober, inga fynd").assertExists()
        compose.onNodeWithText("Ingen brådska. Tio minuter i parken räcker för veckans första rad.").assertExists()
        // Weeks 38 to 40 had finds: the streak is alive but needs this week.
        compose.onNodeWithText("DIN STREAK").assertExists()
        compose
            .onNodeWithText("3 veckor i rad. Håll den vid liv innan söndagen är slut: en talgoxe i parken räcker.")
            .assertExists()
        // No numbers, no stamp card and no grid on an empty page.
        compose.onNodeWithContentDescription("0 fynd", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Ingen ny stämpel den här veckan.").assertDoesNotExist()
        compose.onNodeWithText("ALLA FYND", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Öppna kameran").performClick()
        assertEquals(1, cameraOpened)
    }

    @Test
    @Config(qualifiers = "+en")
    fun `a quiet week in English`() {
        show(observations = FakeObservationRepository(), badges = FakeBadgeRepository())
        compose.onNodeWithText("quiet").assert(isHeading())
        compose.onNodeWithText("5 to 11\u00A0October.").assertExists()
        compose.onNodeWithText("Open the camera").assertExists()
        compose.onNodeWithText("YOUR STREAK").assertDoesNotExist()
    }

    // ── Large text ───────────────────────────────────────────────────────────────────────────────

    /** Long one-word Swedish names among the week's species, to test the grid and the rows. */
    private fun longNames() =
        RecapFixtures.species(
            rename =
                mapOf(
                    RecapFixtures.DOMHERRE to "Svarthakedopping",
                    RecapFixtures.STJARTMES to "Trädgårdssångare",
                ),
        )

    @Test
    @Config(qualifiers = "+sv-w320dp")
    fun `no text is clipped or broken mid-word at 1_3x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.3f)
        show(species = longNames())
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w320dp")
    fun `no text is clipped or broken mid-word at 1_5x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.5f)
        show(species = longNames())
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w320dp")
    fun `no text is clipped or broken mid-word at 2x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(species = longNames())
        // In the new species rows and under its square in the grid.
        compose.onAllNodesWithText("Trädgårdssångare", useUnmergedTree = true).assertCountEquals(2)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+en-w320dp")
    fun `no text is clipped or broken mid-word at 2x in English`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(species = RecapFixtures.species(english = true))
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w320dp")
    fun `a quiet week at 2x on a narrow phone`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(observations = RecapFixtures.quietWeekRepo(), badges = FakeBadgeRepository())
        compose.assertNoTextLayoutRegressions()
    }
}
