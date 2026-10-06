package se.birdy.app.screenshots

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.dailybird.DailyBirdToday
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.app.ui.diary.LifelistScreen
import se.birdy.app.ui.diary.LifelistViewModel
import se.birdy.app.ui.encyclopedia.ArchiveScreen
import se.birdy.app.ui.encyclopedia.ArchiveViewModel
import se.birdy.app.ui.scaffold.AppRoute
import se.birdy.app.ui.scaffold.BottomNavBar
import se.birdy.content.Abundance
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.observation.Observation
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Release 1.3.0 Task 7d (design option B): the Dagens fågel strip on Mina arter and Uppslagsverk
 * and the dot on the Identify tab. The hero itself is in [IdentifyScreenshotTest]. Coil loads no
 * images under Robolectric, so the strip's round photo shows its moss placeholder.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class DailyBirdScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun bird(
        caughtToday: Boolean = false,
        daysCaught: Int = 0,
    ) = DailyBirdToday(
        date = LocalDate(2026, 10, 6),
        speciesId = "Q25403",
        name = "Sävsångare",
        scientificName = "Acrocephalus schoenobaenus",
        heroImagePath = null,
        caughtToday = caughtToday,
        daysCaught = daysCaught,
    )

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
    private fun lifelist(bird: DailyBirdToday) {
        val vm =
            remember {
                val now = Clock.System.now()
                LifelistViewModel(
                    observationRepo =
                        FakeObservationRepository().apply {
                            seed(
                                listOf(
                                    observation("o1", "Q25485", stampNumber = 2, savedAt = now.minus(2.hours)),
                                    observation("o2", "Q25404", stampNumber = 1, savedAt = now.minus(40.days)),
                                ),
                            )
                        },
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    prefs =
                        FakeUserPreferences().apply {
                            userNameValue = "Albin"
                            lifelistStat3Value = LifelistStat3Choice.STREAK
                            lifelistSortValue = LifelistSort.RECENT
                        },
                )
            }
        LifelistScreen(
            viewModel = vm,
            onObservationClick = {},
            onScanCtaClick = {},
            onPremiumClick = {},
            dailyBird = bird,
        )
    }

    private fun summary(
        id: String,
        name: String,
        scientificName: String,
    ) = SpeciesSummary(
        id = SpeciesId(id),
        name = name,
        scientificName = scientificName,
        abundance = Abundance.ALLMÄN,
        heroImagePath = null,
        family = "Paridae",
        familySv = "Mesfåglar",
        group = "songbirds",
    )

    @Composable
    private fun archive(locale: Locale) {
        val vm =
            remember {
                ArchiveViewModel(
                    repo =
                        FakeSpeciesRepository().apply {
                            searchResults.value =
                                listOf(
                                    summary("Q25485", "Talgoxe", "Parus major"),
                                    summary("Q25404", "Blåmes", "Cyanistes caeruleus"),
                                    summary("Q25406", "Svartmes", "Periparus ater"),
                                )
                        },
                    observationRepo = FakeObservationRepository(),
                    prefs = FakeUserPreferences(),
                    locale = locale,
                    premiumActiveFlow = flowOf(false),
                )
            }
        ArchiveScreen(
            viewModel = vm,
            locale = locale,
            onSpeciesClick = {},
            onPremiumClick = {},
            dailyBird = bird(),
        )
    }

    // ArchiveViewModel debounces the search for 250 ms on a paused main Looper (see ArchiveScreenshotTest).
    private fun advanceMainLooper() {
        shadowOf(Looper.getMainLooper()).idleFor(400, TimeUnit.MILLISECONDS)
    }

    @Test
    @Config(qualifiers = "+sv")
    fun daily_bird_lifelist_sv() = compose.captureScreen("daily_bird_lifelist_sv") { lifelist(bird()) }

    @Test
    @Config(qualifiers = "+en")
    fun daily_bird_lifelist_en() = compose.captureScreen("daily_bird_lifelist_en") { lifelist(bird()) }

    @Test
    @Config(qualifiers = "+sv")
    fun daily_bird_lifelist_caught_sv() =
        compose.captureScreen("daily_bird_lifelist_caught_sv") { lifelist(bird(caughtToday = true, daysCaught = 1)) }

    @Test
    @Config(qualifiers = "+sv")
    fun daily_bird_lifelist_sv_200() {
        RuntimeEnvironment.setFontScale(2.0f)
        compose.captureScreen("daily_bird_lifelist_sv_200") { lifelist(bird(caughtToday = true, daysCaught = 1)) }
    }

    @Test
    @Config(qualifiers = "+sv")
    fun daily_bird_archive_sv() = compose.captureScreen("daily_bird_archive_sv", settle = ::advanceMainLooper) { archive(Locale.SV) }

    @Test
    @Config(qualifiers = "+en")
    fun daily_bird_archive_en() = compose.captureScreen("daily_bird_archive_en", settle = ::advanceMainLooper) { archive(Locale.EN) }

    // As in the mockup: on Mina arter, with the rust dot on the Identify tab.
    @Test
    @Config(qualifiers = "+sv")
    fun daily_bird_tab_dot_sv() =
        compose.captureScreen("daily_bird_tab_dot_sv") {
            Column(Modifier.fillMaxWidth()) {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = AppRoute.Lifelist) {
                    composable<AppRoute.Lifelist> {}
                }
                BottomNavBar(navController, dailyBirdDot = true)
            }
        }
}
