package se.birdy.app.usecase

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.RecalculateBadgesUseCase
import se.birdy.app.location.LatLng
import se.birdy.app.location.LocationProvider
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeClock
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakePhotoStorage
import se.birdy.domain.badge.BadgeCatalog
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * CancellationException får aldrig sväljas av runCatching-block i save-vägen
 * (trap-katalogens CE-rethrow-regel): en cancellad coroutine ska avbryta save,
 * inte fortsätta med degraderat resultat.
 */
class SaveObservationCancellationTest {
    private val capturedAt = Instant.fromEpochMilliseconds(1000)

    private fun useCase(
        locationProvider: LocationProvider? = null,
        dailyBirdMatchCount: suspend () -> Int = { 0 },
    ) = SaveObservationUseCase(
        repo = FakeObservationRepository(),
        badgeRepo = FakeBadgeRepository(),
        photoStorage = FakePhotoStorage(),
        clock = FakeClock(capturedAt),
        catalog = BadgeCatalog(version = 1, badges = emptyList()),
        recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = FakeClock(capturedAt)),
        speciesByQid = { emptyMap() },
        dailyBirdMatchCount = dailyBirdMatchCount,
        locationProvider = locationProvider,
        locationEnabled = { true },
    )

    @Test
    fun rethrowsCancellationFromLocationProvider() =
        runTest {
            val cancelling =
                object : LocationProvider {
                    override suspend fun current(): LatLng? = throw CancellationException("cancelled")
                }
            assertFailsWith<CancellationException> {
                useCase(locationProvider = cancelling)
                    .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            }
        }

    @Test
    fun rethrowsCancellationFromDailyBirdMatchCount() =
        runTest {
            assertFailsWith<CancellationException> {
                useCase(dailyBirdMatchCount = { throw CancellationException("cancelled") })
                    .save("Q1", capturedAt, 0.9f, ByteArray(4), "")
            }
        }
}
