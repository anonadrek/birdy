package se.birdy.app.ui.badges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.assertNoTextLayoutRegressions
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7k (design "Bild 2"): the entry card to Troférummet shows your own latest
 * stamps, fanned like stamps on a desk, instead of the stock photo of unknown origin.
 * The rendered pictures are in the opt-in screenshot suite (BadgesScreenshotTest).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class TrophyRoomEntryCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = TimeZone.of("Europe/Stockholm")
    private val now = Instant.parse("2026-10-12T10:00:00Z")
    private var opened = 0

    private fun stamp(
        id: String,
        number: Int,
        unlockedAt: String,
        category: BadgeCategory = BadgeCategory.PROGRESSION,
        premium: Boolean = false,
    ) = BadgeWithUnlock(
        badge = Badge(id = id, category = category, rule = BadgeRule.CountUniqueSpecies(1), isPremium = premium),
        unlockedAt = Instant.parse(unlockedAt),
        stampNumber = number,
    )

    // The design's three stamps: №2 Skådare, №17 Rödlistad, №23 Månads-rytm (the newest, 11 okt).
    private val novice = stamp("novice", 1, "2026-05-30T08:00:00Z")
    private val bronze = stamp("birder_bronze", 2, "2026-06-02T08:00:00Z")
    private val paridae = stamp("family_paridae", 11, "2026-08-15T08:00:00Z", BadgeCategory.FAMILY)
    private val redlisted = stamp("redlisted_1", 17, "2026-09-20T08:00:00Z", BadgeCategory.REDLISTED)
    private val monthlyRhythm = stamp("weekly_streak_4", 23, "2026-10-11T08:00:00Z", BadgeCategory.STREAK_WEEKLY)

    private fun show(
        recent: List<BadgeWithUnlock>,
        count: Int = recent.size,
        locale: Locale = Locale.SV,
    ) {
        attachComposeResourcesContext()
        compose.setContent {
            BirdyTheme {
                TrophyRoomEntryCard(
                    recent = recent,
                    unlockedCount = count,
                    locale = locale,
                    zone = zone,
                    now = now,
                    onClick = { opened++ },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        compose.waitForIdle()
    }

    private fun fanSeals(): List<SemanticsNode> =
        compose
            .onAllNodes(
                SemanticsMatcher("a fanned seal") {
                    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("$TROPHY_FAN_SEAL_TAG:") == true
                },
                useUnmergedTree = true,
            ).fetchSemanticsNodes()

    private fun fanNumbers(): List<String> = fanSeals().map { it.config[SemanticsProperties.TestTag].substringAfter(':') }

    private fun hasTag(tag: String) =
        compose
            .onAllNodes(SemanticsMatcher("tag $tag") { it.config.getOrNull(SemanticsProperties.TestTag) == tag }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .isNotEmpty()

    @Test
    @Config(qualifiers = "+sv")
    fun `no stamps shows one waiting seal and the first stamp line`() {
        show(emptyList())
        compose.onNodeWithText("DITT TROFÉRUM", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Din första stämpel väntar.", useUnmergedTree = true).assertExists()
        assertEquals(emptyList(), fanNumbers())
        assertTrue(hasTag(TROPHY_FAN_EMPTY_TAG), "the empty card shows one dashed seal")
        compose.onNodeWithText("Senast", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose
            .onNodeWithContentDescription("Ditt troférum. Din första stämpel väntar. Öppna.")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `one stamp reads in the singular and names the stamp and its date`() {
        show(listOf(monthlyRhythm))
        compose.onNodeWithText("1 stämpel", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Senast: Månads-rytm, 11 okt", useUnmergedTree = true).assertExists()
        assertEquals(listOf("23"), fanNumbers())
        assertFalse(hasTag(TROPHY_FAN_EMPTY_TAG))
        compose
            .onNodeWithContentDescription("Ditt troférum, 1 stämpel, senast Månads-rytm, 11 okt. Öppna.")
            .assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `two stamps fan out with the newest on top`() {
        show(listOf(monthlyRhythm, redlisted))
        compose.onNodeWithText("2 stämplar", useUnmergedTree = true).assertExists()
        assertEquals(listOf("17", "23"), fanNumbers())
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `three stamps fan out oldest to newest with the newest on top and largest`() {
        show(listOf(monthlyRhythm, redlisted, bronze))
        compose.onNodeWithText("3 stämplar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Senast: Månads-rytm, 11 okt", useUnmergedTree = true).assertExists()
        // Drawn in this order, so the last one lies on top.
        assertEquals(listOf("2", "17", "23"), fanNumbers())
        val sizes = fanSeals().map { it.size.width }
        assertTrue(sizes.last() > sizes.first() && sizes.last() > sizes[1], "the newest seal is the largest: $sizes")
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `five stamps show only the three most recent whatever order they arrive in`() {
        show(listOf(bronze, monthlyRhythm, novice, redlisted, paridae))
        compose.onNodeWithText("5 stämplar", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Senast: Månads-rytm, 11 okt", useUnmergedTree = true).assertExists()
        assertEquals(listOf("11", "17", "23"), fanNumbers())
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `the count is the Märken count and talkback reads the card as one sentence`() {
        show(listOf(monthlyRhythm, redlisted, bronze), count = 8)
        compose.onNodeWithText("8 stämplar", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithContentDescription("Ditt troférum, 8 stämplar, senast Månads-rytm, 11 okt. Öppna.")
            .assertHasClickAction()
            .performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun `a stamp from an earlier year carries the year`() {
        show(listOf(stamp("weekly_streak_4", 23, "2025-10-11T08:00:00Z", BadgeCategory.STREAK_WEEKLY)))
        compose.onNodeWithText("Senast: Månads-rytm, 11 okt 2025", useUnmergedTree = true).assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english singular and plural`() {
        show(listOf(monthlyRhythm), locale = Locale.EN)
        compose.onNodeWithText("YOUR TROPHY ROOM", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("1 stamp", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Latest: Monthly rhythm, Oct 11", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithContentDescription("Your trophy room, 1 stamp, latest Monthly rhythm, Oct 11. Open.")
            .assertHasClickAction()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english with five stamps`() {
        show(listOf(bronze, monthlyRhythm, novice, redlisted, paridae), locale = Locale.EN)
        compose.onNodeWithText("5 stamps", useUnmergedTree = true).assertExists()
        assertEquals(listOf("11", "17", "23"), fanNumbers())
        compose.onNodeWithContentDescription("Your trophy room, 5 stamps, latest Monthly rhythm, Oct 11. Open.").assertExists()
    }

    @Test
    @Config(qualifiers = "+en")
    fun `english empty card`() {
        show(emptyList(), locale = Locale.EN)
        compose.onNodeWithText("Your first stamp awaits.", useUnmergedTree = true).assertExists()
        compose
            .onNodeWithContentDescription("Your trophy room. Your first stamp awaits. Open.")
            .assertHasClickAction()
    }

    // The longest single words among the badge names (Rödlistemästare, Rovfågelskådare,
    // Fältjournalist, Quarter-faithful) on a narrow phone: no clipped line, no word broken in two.
    private fun showLongNames(locale: Locale) {
        attachComposeResourcesContext()
        val longest =
            listOf(
                stamp("redlisted_15", 19, "2026-10-11T08:00:00Z", BadgeCategory.REDLISTED),
                stamp("family_accipitridae", 9, "2026-10-11T08:00:00Z", BadgeCategory.FAMILY),
                stamp("premium_field_journalist", 31, "2026-10-11T08:00:00Z", premium = true),
                stamp("weekly_streak_12", 24, "2026-10-11T08:00:00Z", BadgeCategory.STREAK_WEEKLY),
            )
        compose.setContent {
            BirdyTheme {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    longest.forEach { latest ->
                        TrophyRoomEntryCard(
                            recent = listOf(latest, redlisted, bronze),
                            unlockedCount = 34,
                            locale = locale,
                            zone = zone,
                            now = now,
                            onClick = {},
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    TrophyRoomEntryCard(emptyList(), 0, locale, zone, now, onClick = {}, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp-h2000dp")
    fun `large text 1_3x keeps every line whole on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.3f)
        showLongNames(Locale.SV)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp-h2000dp")
    fun `large text 1_5x keeps every line whole on a narrow phone`() {
        RuntimeEnvironment.setFontScale(1.5f)
        showLongNames(Locale.SV)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+sv-w360dp-h2000dp")
    fun `large text 2_0x keeps every line whole on a narrow phone in swedish`() {
        RuntimeEnvironment.setFontScale(2.0f)
        showLongNames(Locale.SV)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "+en-w360dp-h2000dp")
    fun `large text 2_0x keeps every line whole on a narrow phone in english`() {
        RuntimeEnvironment.setFontScale(2.0f)
        showLongNames(Locale.EN)
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    fun `the stock trophy photo of unknown origin is gone from the app`() {
        val src = composeAppSrc()
        val photo = "trophy_hero" + ".webp" // split so this file never matches its own search
        assertFalse(
            File(src, "commonMain/composeResources/files/branding/$photo").exists(),
            "files/branding/$photo has no recorded source or licence and must not ship",
        )
        val references =
            src
                .listFiles()
                .orEmpty()
                .filter { it.isDirectory && it.name.endsWith("Main") }
                .flatMap { set -> set.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "xml", "swift") }.toList() }
                .filter { it.readText().contains(photo) }
                .map { it.path }
        assertEquals(emptyList(), references)
    }

    private fun composeAppSrc(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            listOf(File(dir, "src"), File(dir, "composeApp/src")).forEach { candidate ->
                if (File(candidate, "commonMain/composeResources/values/strings.xml").isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("composeApp/src not found from ${System.getProperty("user.dir")}")
    }
}
