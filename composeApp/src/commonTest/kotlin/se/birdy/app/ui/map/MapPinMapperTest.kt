package se.birdy.app.ui.map

import kotlinx.datetime.Instant
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPinMapperTest {
    private fun obs(
        id: String,
        lat: Double?,
        lng: Double?,
    ) = Observation(
        id = id,
        speciesId = "Q123",
        capturedAt = Instant.fromEpochMilliseconds(1000),
        savedAt = Instant.fromEpochMilliseconds(1000),
        photoPath = "/p/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = lat,
        longitude = lng,
        locationLabel = null,
        stampNumber = 1,
    )

    @Test
    fun dropsObservationsWithoutBothCoordinates() {
        val pins =
            MapPinMapper.toPins(
                listOf(
                    obs("a", 59.3, 18.0),
                    obs("b", null, 18.0),
                    obs("c", 59.3, null),
                    obs("d", null, null),
                ),
            )
        assertEquals(listOf("a"), pins.map { it.observationId })
    }

    @Test
    fun mapsCoordinatesAndPhoto() {
        val pins = MapPinMapper.toPins(listOf(obs("a", 59.3293, 18.0686)))
        val pin = pins.single()
        assertEquals(59.3293, pin.latitude)
        assertEquals(18.0686, pin.longitude)
        assertEquals("/p/a.jpg", pin.photoPath)
        assertEquals("Q123", pin.speciesId)
        assertEquals(1, pin.stampNumber)
    }
}
