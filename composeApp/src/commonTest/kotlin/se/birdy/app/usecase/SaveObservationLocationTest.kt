package se.birdy.app.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.location.LatLng
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeLocationProvider
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.domain.badge.BadgeCatalog
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
        badgeRepo = FakeBadgeRepository(),
        photoStorage = FakePhotoStorage(),
        clock = FakeClock(capturedAt),
        catalog = BadgeCatalog(version = 1, badges = emptyList()),
        recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = FakeClock(capturedAt)),
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
            assertNull(
                repo
                    .observeAll()
                    .first()
                    .single()
                    .latitude,
            )
            assertEquals(0, provider.currentCalls)
        }

    @Test
    fun noLocationWhenToggleDisabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = false)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            assertNull(
                repo
                    .observeAll()
                    .first()
                    .single()
                    .latitude,
            )
        }

    @Test
    fun usesPresetLocationWhenEnabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save(
                    "Q1",
                    capturedAt,
                    0.9f,
                    ByteArray(4),
                    "",
                    attachLocation = false,
                    presetLocation = LatLng(40.0, -3.0),
                )
            val row = repo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(-3.0, row.longitude)
            assertEquals(0, provider.currentCalls)
        }

    @Test
    fun ignoresPresetLocationWhenToggleDisabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = false)
                .save(
                    "Q1",
                    capturedAt,
                    0.9f,
                    ByteArray(4),
                    "",
                    attachLocation = false,
                    presetLocation = LatLng(40.0, -3.0),
                )
            assertNull(
                repo
                    .observeAll()
                    .first()
                    .single()
                    .latitude,
            )
        }

    @Test
    fun presetLocationTakesPrecedenceOverCurrent() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save(
                    "Q1",
                    capturedAt,
                    0.9f,
                    ByteArray(4),
                    "",
                    attachLocation = true,
                    presetLocation = LatLng(40.0, -3.0),
                )
            val row = repo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(0, provider.currentCalls)
        }
}
