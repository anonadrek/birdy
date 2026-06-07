package se.birdy.app.ui.map

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.domain.observation.Observation
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setMain() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun obs(
        id: String,
        lat: Double?,
        lng: Double?,
    ) = Observation(
        id = id,
        speciesId = "Q1",
        capturedAt = Instant.fromEpochMilliseconds(1),
        savedAt = Instant.fromEpochMilliseconds(1),
        photoPath = "/$id.jpg",
        note = "",
        confidence = 1f,
        latitude = lat,
        longitude = lng,
        locationLabel = null,
        stampNumber = 1,
    )

    @Test
    fun exposesOnlyLocatedPins() =
        runTest(dispatcher) {
            val repo = FakeObservationRepository()
            repo.insert(obs("a", 59.0, 18.0))
            repo.insert(obs("b", null, null))
            val vm = MapViewModel(repo)
            val state = vm.state.first { it.pins.isNotEmpty() || it.locatedCount == 0 }
            assertEquals(listOf("a"), state.pins.map { it.observationId })
            assertEquals(1, state.locatedCount)
        }
}
