package se.birdy.app.strings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badges_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_date_fmt
import birdy_bird_scanner.composeapp.generated.resources.lifelist_section_recent
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_count
import birdy_bird_scanner.composeapp.generated.resources.recap_dates_one_month_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_dates_two_months_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_day_a11y
import birdy_bird_scanner.composeapp.generated.resources.recap_delta_line
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_finds
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_new_species
import birdy_bird_scanner.composeapp.generated.resources.recap_ledger_weeks
import birdy_bird_scanner.composeapp.generated.resources.recap_new_lifelist_fmt
import birdy_bird_scanner.composeapp.generated.resources.recap_sub_active
import birdy_bird_scanner.composeapp.generated.resources.recap_summary_active_fmt
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.notifications.recapNotificationBody
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.diary.lifelistJournalSub
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

    private fun pluralWith(
        resource: PluralStringResource,
        quantity: Int,
        vararg args: Any,
    ): String = runBlocking { getPluralString(resource, quantity, *args) }

    /** A plural whose words carry no number (the caps under the recap's header row). */
    private fun pluralWord(
        resource: PluralStringResource,
        quantity: Int,
    ): String = runBlocking { getPluralString(resource, quantity) }

    private fun showComposedLines() {
        attachComposeResourcesContext()
        compose.setContent {
            Column {
                Text(lifelistJournalSub(daysActive = 1, speciesCount = 1))
                Text(lifelistJournalSub(daysActive = 2, speciesCount = 2))
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
        assertEquals("+1 fynd mot förra veckan", plural(Res.plurals.recap_delta_line, 1, "+1"))
        assertEquals("-2 fynd mot förra veckan", plural(Res.plurals.recap_delta_line, 2, "-2"))
        assertEquals("5 till 11 oktober. 1\u00A0dag ute.", pluralWith(Res.plurals.recap_sub_active, 1, "5 till 11 oktober", 1))
        assertEquals("5 till 11 oktober. 2\u00A0dagar ute.", pluralWith(Res.plurals.recap_sub_active, 2, "5 till 11 oktober", 2))
        assertEquals("fynd", pluralWord(Res.plurals.recap_ledger_finds, 1))
        assertEquals("ny art", pluralWord(Res.plurals.recap_ledger_new_species, 1))
        assertEquals("nya arter", pluralWord(Res.plurals.recap_ledger_new_species, 2))
        // A no-break space keeps "i rad" together when the caps wrap at a large text size.
        assertEquals("vecka i\u00A0rad", pluralWord(Res.plurals.recap_ledger_weeks, 1))
        assertEquals("veckor i\u00A0rad", pluralWord(Res.plurals.recap_ledger_weeks, 2))
        assertEquals("måndag 5 oktober, 1 fynd", pluralWith(Res.plurals.recap_day_a11y, 1, "måndag 5 oktober", 1))
        assertEquals("måndag 5 oktober, 2 fynd", pluralWith(Res.plurals.recap_day_a11y, 2, "måndag 5 oktober", 2))
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
        assertEquals("+1 sighting compared with last week", plural(Res.plurals.recap_delta_line, 1, "+1"))
        assertEquals("-2 sightings compared with last week", plural(Res.plurals.recap_delta_line, 2, "-2"))
        assertEquals("5 to 11 October. Out on 1\u00A0day.", pluralWith(Res.plurals.recap_sub_active, 1, "5 to 11 October", 1))
        assertEquals("5 to 11 October. Out on 2\u00A0days.", pluralWith(Res.plurals.recap_sub_active, 2, "5 to 11 October", 2))
        assertEquals("sighting", pluralWord(Res.plurals.recap_ledger_finds, 1))
        assertEquals("sightings", pluralWord(Res.plurals.recap_ledger_finds, 2))
        assertEquals("week in a\u00A0row", pluralWord(Res.plurals.recap_ledger_weeks, 1))
        assertEquals("weeks in a\u00A0row", pluralWord(Res.plurals.recap_ledger_weeks, 2))
        assertEquals("Monday 5 October, 1 sighting", pluralWith(Res.plurals.recap_day_a11y, 1, "Monday 5 October", 1))
        assertEquals("Monday 5 October, 2 sightings", pluralWith(Res.plurals.recap_day_a11y, 2, "Monday 5 October", 2))
        assertEquals("1 located find waiting on the map", plural(Res.plurals.map_teaser_count, 1))
        assertEquals("2 located finds waiting on the map", plural(Res.plurals.map_teaser_count, 2))
        assertEquals("1 sighting, 1 new species. See this week's page.", runBlocking { recapNotificationBody(finds = 1, newSpecies = 1) })
        assertEquals("2 sightings, 2 new species. See this week's page.", runBlocking { recapNotificationBody(finds = 2, newSpecies = 2) })
    }

    /**
     * Release 1.3.0 Task 7j review: `&#160;` in strings.xml reaches the screen as a no-break space
     * (U+00A0), so a number stays with its month or its "№" when text wraps at 2.0x.
     */
    @Test
    @Config(qualifiers = "+sv")
    fun `no-break spaces come through compose-resources in swedish`() {
        attachComposeResourcesContext()
        val nbsp = '\u00A0'
        assertEquals("tis 6${nbsp}okt", runBlocking { getString(Res.string.daily_bird_date_fmt, "tis", 6, "okt") })
        assertEquals("5 till 11${nbsp}oktober", runBlocking { getString(Res.string.recap_dates_one_month_fmt, 5, 11, "oktober") })
        assertEquals(
            "28${nbsp}september till 4${nbsp}oktober",
            runBlocking { getString(Res.string.recap_dates_two_months_fmt, 28, "september", 4, "oktober") },
        )
        assertEquals(
            "№${nbsp}31 i livslistan · lör 10${nbsp}okt",
            runBlocking { getString(Res.string.recap_new_lifelist_fmt, 31, "lör 10${nbsp}okt") },
        )
    }

    @Test
    @Config(qualifiers = "+en")
    fun `no-break spaces come through compose-resources in english`() {
        attachComposeResourcesContext()
        val nbsp = '\u00A0'
        assertEquals("Tue 6${nbsp}Oct", runBlocking { getString(Res.string.daily_bird_date_fmt, "Tue", 6, "Oct") })
        assertEquals("5 to 11${nbsp}October", runBlocking { getString(Res.string.recap_dates_one_month_fmt, 5, 11, "October") })
        assertEquals(
            "№${nbsp}31 on your life list · Sat 10${nbsp}Oct",
            runBlocking { getString(Res.string.recap_new_lifelist_fmt, 31, "Sat 10${nbsp}Oct") },
        )
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `swedish lines with several counts agree with each count`() {
        showComposedLines()
        compose.onNodeWithText("1 dag. 1 art funnen.").assertExists()
        compose.onNodeWithText("2 dagar. 2 arter funna.").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english lines with several counts agree with each count`() {
        showComposedLines()
        compose.onNodeWithText("1 day. 1 species found.").assertExists()
        compose.onNodeWithText("2 days. 2 species found.").assertExists()
    }
}
