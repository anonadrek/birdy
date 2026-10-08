package se.birdy.app.strings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.lifelist_section_recent
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_count
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_new
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.getPluralString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.notifications.recapNotificationBody
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.diary.lifelistJournalSub
import se.birdy.app.ui.recap.recapStatsLine
import kotlin.test.assertEquals

/**
 * Release 1.3.0 Task 7g item 1: every counted phrase the device walkthrough found ("1 dagar.
 * 1 funna.", "1 v streak", "1 sightings") reads naturally for one and for more, in both
 * languages. Plain plurals are read with getPluralString; the lines built from several counts
 * are rendered through the same helpers the screens use.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class CountCopyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun plural(
        resource: PluralStringResource,
        quantity: Int,
        arg: Any = quantity,
    ): String = runBlocking { getPluralString(resource, quantity, arg) }

    private fun showComposedLines() {
        attachComposeResourcesContext()
        compose.setContent {
            Column {
                Text(lifelistJournalSub(daysActive = 1, speciesCount = 1))
                Text(lifelistJournalSub(daysActive = 2, speciesCount = 2))
                Text(recapStatsLine(finds = 1, newSpecies = 1, weekStreak = 1))
                Text(recapStatsLine(finds = 2, newSpecies = 2, weekStreak = 2))
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `swedish plurals read naturally for one and two`() {
        attachComposeResourcesContext()
        assertEquals("*1* funnen.", plural(Res.plurals.badges_journal_headline, 1))
        assertEquals("*2* funna.", plural(Res.plurals.badges_journal_headline, 2))
        assertEquals("Senaste · 1 stämpel", plural(Res.plurals.lifelist_section_recent, 1))
        assertEquals("Senaste · 2 stämplar", plural(Res.plurals.lifelist_section_recent, 2))
        assertEquals("1 fynd den här veckan.", plural(Res.plurals.recap_summary_active_fmt, 1))
        assertEquals("2 fynd den här veckan.", plural(Res.plurals.recap_summary_active_fmt, 2))
        assertEquals("1 fynd den här veckan, och en ny bekantskap.", plural(Res.plurals.recap_summary_active_new, 1))
        assertEquals("2 fynd den här veckan, och en ny bekantskap.", plural(Res.plurals.recap_summary_active_new, 2))
        assertEquals("vs förra veckan: +1 fynd", plural(Res.plurals.recap_delta_fmt, 1, "+1"))
        assertEquals("vs förra veckan: -2 fynd", plural(Res.plurals.recap_delta_fmt, 2, "-2"))
        assertEquals("1 fynd med plats väntar på kartan", plural(Res.plurals.map_teaser_count, 1))
        assertEquals("2 fynd med plats väntar på kartan", plural(Res.plurals.map_teaser_count, 2))
        assertEquals("1 fynd, 1 ny art. Se veckans uppslag.", runBlocking { recapNotificationBody(finds = 1, newSpecies = 1) })
        assertEquals("2 fynd, 2 nya arter. Se veckans uppslag.", runBlocking { recapNotificationBody(finds = 2, newSpecies = 2) })
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english plurals read naturally for one and two`() {
        attachComposeResourcesContext()
        assertEquals("*1* found.", plural(Res.plurals.badges_journal_headline, 1))
        assertEquals("*2* found.", plural(Res.plurals.badges_journal_headline, 2))
        assertEquals("Recent · 1 stamp", plural(Res.plurals.lifelist_section_recent, 1))
        assertEquals("Recent · 2 stamps", plural(Res.plurals.lifelist_section_recent, 2))
        assertEquals("1 sighting this week.", plural(Res.plurals.recap_summary_active_fmt, 1))
        assertEquals("2 sightings this week.", plural(Res.plurals.recap_summary_active_fmt, 2))
        assertEquals("1 sighting this week, and a new acquaintance.", plural(Res.plurals.recap_summary_active_new, 1))
        assertEquals("2 sightings this week, and a new acquaintance.", plural(Res.plurals.recap_summary_active_new, 2))
        assertEquals("vs last week: +1 sighting", plural(Res.plurals.recap_delta_fmt, 1, "+1"))
        assertEquals("vs last week: -2 sightings", plural(Res.plurals.recap_delta_fmt, 2, "-2"))
        assertEquals("1 located find waiting on the map", plural(Res.plurals.map_teaser_count, 1))
        assertEquals("2 located finds waiting on the map", plural(Res.plurals.map_teaser_count, 2))
        assertEquals("1 sighting, 1 new species. See this week's page.", runBlocking { recapNotificationBody(finds = 1, newSpecies = 1) })
        assertEquals("2 sightings, 2 new species. See this week's page.", runBlocking { recapNotificationBody(finds = 2, newSpecies = 2) })
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `swedish lines with several counts agree with each count`() {
        showComposedLines()
        compose.onNodeWithText("1 dag. 1 art funnen.").assertExists()
        compose.onNodeWithText("2 dagar. 2 arter funna.").assertExists()
        compose.onNodeWithText("1 fynd · 1 ny art · 1 vecka i rad").assertExists()
        compose.onNodeWithText("2 fynd · 2 nya arter · 2 veckor i rad").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english lines with several counts agree with each count`() {
        showComposedLines()
        compose.onNodeWithText("1 day. 1 species found.").assertExists()
        compose.onNodeWithText("2 days. 2 species found.").assertExists()
        compose.onNodeWithText("1 sighting · 1 new species · 1 week in a row").assertExists()
        compose.onNodeWithText("2 sightings · 2 new species · 2 weeks in a row").assertExists()
    }
}
