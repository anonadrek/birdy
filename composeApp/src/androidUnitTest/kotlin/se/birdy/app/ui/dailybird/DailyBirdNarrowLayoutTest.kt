package se.birdy.app.ui.dailybird

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.dailybird.challenge
import se.birdy.app.testing.assertNoTextLayoutRegressions
import se.birdy.app.testing.attachComposeResourcesContext
import se.birdy.app.ui.theme.BirdyTheme

/**
 * The Dagens fågel challenge row and strip with the "Premium-märke" tag on the narrowest phones
 * (320dp) at 200 % text: no word may be broken in the middle and no single-line text clipped
 * (assertNoTextLayoutRegressions, the guard the statistics screen uses). The hero row reads out as
 * one sentence, so its lines are checked through [DailyBirdHeroChallengeContent], inside the 22dp
 * side padding PhotoHero gives its text.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DailyBirdNarrowLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bird(
        caughtToday: Boolean,
        daysCaught: Int,
    ) = DailyBirdToday(
        date = LocalDate(2026, 10, 6),
        speciesId = "Q25403",
        name = "Sävsångare",
        scientificName = "Acrocephalus schoenobaenus",
        heroImagePath = null,
        caughtToday = caughtToday,
        daysCaught = daysCaught,
        showPremiumBadgeTag = true,
    )

    // Every state the row can be in: not caught, one or two days left, badge complete.
    private val birds = listOf(bird(false, 0), bird(true, 1), bird(true, 2), bird(true, 4))

    private fun showHeroRows() {
        attachComposeResourcesContext()
        RuntimeEnvironment.setFontScale(2.0f)
        compose.setContent {
            BirdyTheme {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    birds.forEach { bird ->
                        val challenge = bird.challenge()
                        DailyBirdHeroChallengeContent(challenge = challenge, texts = challengeTexts(challenge))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun showStrips() {
        attachComposeResourcesContext()
        RuntimeEnvironment.setFontScale(2.0f)
        compose.setContent {
            BirdyTheme {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    birds.forEach { bird -> DailyBirdStrip(bird = bird, onClick = {}) }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    @Config(qualifiers = "sv-w320dp-h2000dp")
    fun `the hero challenge row with the premium tag breaks no word at 320dp and 200 percent in swedish`() {
        showHeroRows()
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h2000dp")
    fun `the hero challenge row with the premium tag breaks no word at 320dp and 200 percent in english`() {
        showHeroRows()
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "sv-w320dp-h2000dp")
    fun `the strip with the premium tag breaks no word at 320dp and 200 percent in swedish`() {
        showStrips()
        compose.assertNoTextLayoutRegressions()
    }

    @Test
    @Config(qualifiers = "en-w320dp-h2000dp")
    fun `the strip with the premium tag breaks no word at 320dp and 200 percent in english`() {
        showStrips()
        compose.assertNoTextLayoutRegressions()
    }
}
