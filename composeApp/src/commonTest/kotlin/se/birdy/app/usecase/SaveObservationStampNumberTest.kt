package se.birdy.app.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.domain.badge.BadgeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveObservationStampNumberTest {
    private val clock = FakeClock(Instant.fromEpochMilliseconds(1_000_000L))

    @Test
    fun `first save assigns stamp_number 1`() =
        runTest {
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
        runTest {
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
