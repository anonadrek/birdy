package se.birdy.app.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.domain.badge.BadgeCatalog
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SaveObservationStampNumberTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000L))

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `first save assigns stamp_number 1`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            val useCase = makeUseCase(repo)
            useCase.save(
                speciesId = "Q1",
                capturedAt = Instant.fromEpochMilliseconds(1000L),
                confidence = 0.9f,
                rawJpegBytes = byteArrayOf(0),
                note = "",
            )
            assertEquals(1, repo.lastInserted!!.stampNumber)
        }

    @Test
    fun `subsequent saves increment stamp_number`() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            val useCase = makeUseCase(repo)
            repeat(3) { i ->
                useCase.save(
                    speciesId = "Q$i",
                    capturedAt = Instant.fromEpochMilliseconds(1000L + i),
                    confidence = 0.9f,
                    rawJpegBytes = byteArrayOf(0),
                    note = "",
                )
            }
            assertEquals(listOf(1, 2, 3), repo.allInserted.map { it.stampNumber })
        }

    private fun makeUseCase(repo: FakeObservationRepository): SaveObservationUseCase =
        SaveObservationUseCase(
            repo = repo,
            badgeRepo = FakeBadgeRepository(),
            photoStorage = FakePhotoStorage(),
            clock = clock,
            catalog = BadgeCatalog(version = 1, badges = emptyList()),
            recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
            speciesByQid = { emptyMap() },
        )
}
