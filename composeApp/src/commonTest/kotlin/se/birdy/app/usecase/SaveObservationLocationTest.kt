package se.birdy.app.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.app.location.LatLng
import se.birdy.app.testing.FakeLocationProvider
import se.birdy.app.testing.FakeObservationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveObservationLocationTest {
    private val capturedAt = Instant.fromEpochMilliseconds(1000)

    private fun useCase(
        repo: FakeObservationRepository,
        provider: FakeLocationProvider,
        enabled: Boolean,
    ) = SaveObservationUseCase(
        repo = repo,
        badgeRepo = NoopBadgeRepository(),
        photoStorage = RecordingPhotoStorage(),
        clock = FixedClock(capturedAt),
        catalog = emptyBadgeCatalog(),
        recalculate = noopRecalculate(),
        speciesByQid = { emptyMap() },
        locationProvider = provider,
        locationEnabled = { enabled },
    )

    @Test
    fun attachesLocationWhenEnabledAndRequested() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            val row = repo.observeAll().first().single()
            assertEquals(59.3, row.latitude)
            assertEquals(18.0, row.longitude)
        }

    @Test
    fun noLocationWhenAttachFalse() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = false)
            assertNull(repo.observeAll().first().single().latitude)
            assertEquals(0, provider.currentCalls)
        }

    @Test
    fun noLocationWhenToggleDisabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = false)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            assertNull(repo.observeAll().first().single().latitude)
        }
}
