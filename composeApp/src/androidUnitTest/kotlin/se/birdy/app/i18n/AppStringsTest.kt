package se.birdy.app.i18n

import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.daily_bird_read_more
import birdy_bird_scanner.composeapp.generated.resources.recap_stats_finds
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.resolveBadgeString
import se.birdy.content.Locale
import kotlin.test.assertEquals

/**
 * QA 2026-10-07: strings read outside composition (Dagens fågel's notification, the weekly
 * recap's stamps, the PDF export) followed the phone's language instead of the one picked in
 * Birdy. The phone here is English; [AppStrings] reads the app's language either way and leaves
 * the process default alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "+en")
class AppStringsTest {
    @Test
    fun `a string is read in the app's language, not the phone's`() {
        attachComposeResourcesContext()
        assertEquals("Läs om arten", runBlocking { AppStrings(Locale.SV).get(Res.string.daily_bird_read_more) })
        assertEquals("Read about it", runBlocking { AppStrings(Locale.EN).get(Res.string.daily_bird_read_more) })
        assertEquals(
            "en",
            java.util.Locale
                .getDefault()
                .language,
        )
    }

    @Test
    fun `plurals follow the app's language`() {
        attachComposeResourcesContext()
        assertEquals("2 fynd", runBlocking { AppStrings(Locale.SV).plural(Res.plurals.recap_stats_finds, 2, 2) })
        assertEquals("2 sightings", runBlocking { AppStrings(Locale.EN).plural(Res.plurals.recap_stats_finds, 2, 2) })
    }

    @Test
    fun `the weekly recap's stamp name and description follow the app's language`() {
        attachComposeResourcesContext()
        val swedish = AppStrings(Locale.SV)
        assertEquals("Nybörjare", runBlocking { resolveBadgeString("novice", swedish) { BadgeStringMap.nameFor("novice") } })
        assertEquals(
            "Grunden allt vilar på — de första arterna du lär dig känna igen.",
            runBlocking { swedish.get(BadgeStringMap.descriptionFor("novice")) },
        )
        assertEquals(
            "en",
            java.util.Locale
                .getDefault()
                .language,
        )
    }
}
