package se.birdy.app.ui.diary

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.datastore.LifelistSort
import se.birdy.datastore.LifelistStat3Choice
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class LifelistViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `hero stats reflect observation counts`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.seed(listOf(obs("o1", "Q1", 1L, 1), obs("o2", "Q1", 2L, 2), obs("o3", "Q2", 3L, 3)))
            val vm =
                LifelistViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    prefs =
                        FakeUserPreferences().apply {
                            userNameValue = "Albin"
                            lifelistStat3Value = LifelistStat3Choice.STREAK
                            lifelistSortValue = LifelistSort.RECENT
                        },
                    zone = TimeZone.UTC,
                )
            vm.uiState.test {
                // Drain until we see Loaded — Loading may or may not be emitted depending on timing.
                var loaded: LifelistUiState? = null
                while (loaded !is LifelistUiState.Loaded) {
                    loaded = awaitItem()
                }
                assertIs<LifelistUiState.Loaded>(loaded)
                assertEquals(2, loaded.speciesCount)
                assertEquals(3, loaded.stampsCount)
                assertEquals("Albin", loaded.userName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `recap preview lists this week finds newest first`() =
        runTest(dispatcher) {
            val now = Instant.parse("2026-05-27T10:00:00Z") // ons, ISO-vecka 22 (mån 25 maj startar veckan)
            val repo = FakeObservationRepository()
            repo.seed(
                listOf(
                    obsAt("a", Instant.parse("2026-05-26T10:00:00Z"), "/p/a.jpg"), // denna vecka (nyare)
                    obsAt("b", Instant.parse("2026-05-25T10:00:00Z"), "/p/b.jpg"), // denna vecka (mån)
                    obsAt("old", Instant.parse("2026-05-18T10:00:00Z"), "/p/old.jpg"), // tidigare vecka
                ),
            )
            val vm =
                LifelistViewModel(
                    observationRepo = repo,
                    speciesRepo = FakeSpeciesRepository(),
                    prefs =
                        FakeUserPreferences().apply {
                            userNameValue = "Albin"
                            lifelistStat3Value = LifelistStat3Choice.STREAK
                            lifelistSortValue = LifelistSort.RECENT
                        },
                    zone = TimeZone.UTC,
                    now = { now },
                )
            vm.uiState.test {
                var loaded: LifelistUiState? = null
                while (loaded !is LifelistUiState.Loaded) loaded = awaitItem()
                assertIs<LifelistUiState.Loaded>(loaded)
                assertEquals(2, loaded.recapPreview.findCount)
                assertEquals(listOf("/p/a.jpg", "/p/b.jpg"), loaded.recapPreview.findPhotoPaths)
                assertEquals(true, loaded.recapPreview.isoWeek > 0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun obsAt(
        id: String,
        at: Instant,
        photo: String,
    ) = Observation(
        id = id,
        speciesId = "Q1",
        capturedAt = at,
        savedAt = at,
        photoPath = photo,
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = 0,
    )

    private fun obs(
        id: String,
        sp: String,
        ms: Long,
        stamp: Int,
    ) = Observation(
        id = id,
        speciesId = sp,
        capturedAt = Instant.fromEpochMilliseconds(ms),
        savedAt = Instant.fromEpochMilliseconds(ms),
        photoPath = "/tmp/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
        stampNumber = stamp,
    )
}
