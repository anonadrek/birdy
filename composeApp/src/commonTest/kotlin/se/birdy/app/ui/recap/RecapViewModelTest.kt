package se.birdy.app.ui.recap

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.observation.FileCleanupRequest
import se.birdy.domain.observation.Observation
import se.birdy.domain.observation.ObservationRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RecapViewModelTest {
    @BeforeTest fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun resetMain() = Dispatchers.resetMain()

    private val fixedNow = Instant.parse("2026-05-30T12:00:00Z")

    private fun vm(
        obs: FakeObservationRepository = FakeObservationRepository(),
        badges: FakeBadgeRepository = FakeBadgeRepository(),
        speciesByQid: suspend () -> Map<SpeciesId, SpeciesSummary> = { emptyMap() },
        badgeNameFor: suspend (String) -> String = { it },
    ) = RecapViewModel(
        obsRepo = obs,
        badgeRepo = badges,
        speciesByQid = speciesByQid,
        badgeNameFor = badgeNameFor,
        zone = TimeZone.UTC,
        now = { fixedNow },
    )

    @Test
    fun `quiet week emits Loaded with quiet summary`() =
        runTest {
            val vm = vm()
            vm.state.test {
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                val loaded = item as RecapUiState.Loaded
                assertTrue(loaded.recap.summary.isQuiet)
            }
        }

    @Test
    fun `hero species name resolved from this week observation`() =
        runTest {
            val obsRepo = FakeObservationRepository()
            // fixedNow is 2026-05-30 (Saturday, ISO week 22 of 2026) — seed obs in the same week
            obsRepo.seedObservation(
                speciesId = "Q25485",
                capturedAt = Instant.parse("2026-05-28T10:00:00Z"), // Thursday same week
            )
            val talgoxe =
                SpeciesSummary(
                    id = SpeciesId("Q25485"),
                    name = "Talgoxe",
                    scientificName = "Parus major",
                    abundance = Abundance.ALLMÄN,
                    heroImagePath = null,
                )
            val vm =
                vm(
                    obs = obsRepo,
                    speciesByQid = { mapOf(SpeciesId("Q25485") to talgoxe) },
                    badgeNameFor = { "Badge $it" },
                )
            vm.state.test {
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                val loaded = item as RecapUiState.Loaded
                assertEquals("Talgoxe", loaded.heroSpeciesName)
                assertTrue(loaded.recap.summary.observationCount >= 1)
            }
        }

    @Test
    fun `error state when observe flow throws`() =
        runTest {
            val throwingObsRepo =
                object : ObservationRepository {
                    override fun observeAll(): Flow<List<Observation>> = flow { throw RuntimeException("boom") }

                    override fun observeAllByStampNumber(): Flow<List<Observation>> = flowOf(emptyList())

                    override fun observeById(id: String): Flow<Observation?> = flowOf(null)

                    override suspend fun insert(observation: Observation) {}

                    override suspend fun updateNote(
                        id: String,
                        note: String,
                    ) {}

                    override suspend fun delete(id: String): FileCleanupRequest = FileCleanupRequest(null, null)

                    override suspend fun nextStampNumber(): Int = 1

                    override suspend fun countByQid(speciesId: String): Int = 0

                    override suspend fun firstByQid(speciesId: String): Instant? = null
                }
            val vm =
                RecapViewModel(
                    obsRepo = throwingObsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    speciesByQid = { emptyMap() },
                    badgeNameFor = { it },
                    zone = TimeZone.UTC,
                    now = { fixedNow },
                )
            vm.state.test {
                // Skip past Loading
                var item = awaitItem()
                while (item is RecapUiState.Loading) item = awaitItem()
                assertTrue(item is RecapUiState.Error, "expected Error, got $item")
                assertEquals(RecapErrorKind.LoadFailed, (item as RecapUiState.Error).kind)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
