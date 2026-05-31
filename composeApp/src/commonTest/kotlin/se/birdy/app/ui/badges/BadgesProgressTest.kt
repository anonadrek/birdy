package se.birdy.app.ui.badges

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BadgesProgressTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test fun `non-rare badge with no progress is Locked`() =
        runTest {
            val vm =
                makeVm(
                    observations = emptyList(),
                    catalog =
                        BadgeCatalog(
                            version = 1,
                            badges =
                                listOf(
                                    Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(5)),
                                ),
                        ),
                )
            vm.state.test {
                val loaded = drainToLoaded()
                val first = loaded.locked.first()
                assertEquals("novice", first.badge.id)
                assertIs<BadgeGridState.Locked>(first.state)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `non-rare badge with partial progress is InProgress`() =
        runTest {
            val obs = listOf(observation("Q1"), observation("Q2"), observation("Q3"), observation("Q4"))
            val vm =
                makeVm(
                    observations = obs,
                    catalog =
                        BadgeCatalog(
                            version = 1,
                            badges =
                                listOf(
                                    Badge("novice", BadgeCategory.PROGRESSION, BadgeRule.CountUniqueSpecies(8)),
                                ),
                        ),
                )
            vm.state.test {
                val loaded = drainToLoaded()
                val first = loaded.locked.first()
                assertEquals("novice", first.badge.id)
                val state = assertIs<BadgeGridState.InProgress>(first.state)
                assertEquals(4, state.current)
                assertEquals(8, state.target)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test fun `redlisted badge is visible (not hidden)`() =
        runTest {
            val obs = listOf(observation("Q1"))
            val vm =
                makeVm(
                    observations = obs,
                    catalog =
                        BadgeCatalog(
                            version = 1,
                            badges =
                                listOf(
                                    Badge("rl-finder", BadgeCategory.REDLISTED, BadgeRule.CountUniqueSpecies(5)),
                                ),
                        ),
                )
            vm.state.test {
                val loaded = drainToLoaded()
                assertTrue(loaded.locked.none { it.state is BadgeGridState.Hidden })
                cancelAndIgnoreRemainingEvents()
            }
        }

    private suspend fun app.cash.turbine.ReceiveTurbine<BadgesUiState>.drainToLoaded(): BadgesUiState.Loaded {
        var item = awaitItem()
        while (item !is BadgesUiState.Loaded) item = awaitItem()
        return item
    }

    private fun observation(speciesId: String): Observation =
        Observation(
            id = "obs-$speciesId-${speciesId.hashCode()}",
            speciesId = speciesId,
            capturedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            savedAt = Instant.fromEpochMilliseconds(1_700_000_000_000L),
            photoPath = "/tmp/$speciesId.jpg",
            note = "",
            confidence = 0.9f,
            latitude = null,
            longitude = null,
            locationLabel = null,
            stampNumber = 0,
        )

    private fun makeVm(
        observations: List<Observation>,
        catalog: BadgeCatalog,
    ): BadgesViewModel {
        val obsRepo = FakeObservationRepository().apply { observations.forEach(::seedDirect) }
        val badgeRepo = FakeBadgeRepository()
        return BadgesViewModel(
            obsRepo = obsRepo,
            badgeRepo = badgeRepo,
            speciesByQid = { emptyMap() },
            speciesTotalCount = flowOf(700),
            catalog = catalog,
            recalc = RecalculateBadgesUseCase(zone = TimeZone.UTC),
            zone = TimeZone.UTC,
            locale = Locale.SV,
        )
    }
}
