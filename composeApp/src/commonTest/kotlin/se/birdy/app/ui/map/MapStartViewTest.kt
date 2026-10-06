package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): two finds saved at the same
 * spot gave the map a bounding box of zero size. osmdroid fitted it at its own maximum zoom (29;
 * the tile source stops at 20), so the map opened with no tiles at all, only the loading grid.
 */
class MapStartViewTest {
    private fun pin(
        lat: Double,
        lon: Double,
        id: String = "obs-$lat-$lon",
    ) = MapPin(observationId = id, latitude = lat, longitude = lon, speciesId = null, stampNumber = 1, photoPath = "")

    @Test
    fun `no pins means no start view`() {
        assertNull(mapStartView(emptyList()))
    }

    @Test
    fun `one pin is centred`() {
        assertEquals(MapStartView.Centre(59.3293, 18.0686), mapStartView(listOf(pin(59.3293, 18.0686))))
    }

    @Test
    fun `several pins at the same spot are centred like one pin and not fitted`() {
        val pins = listOf(pin(59.3293, 18.0686, id = "a"), pin(59.3293, 18.0686, id = "b"))

        assertEquals(MapStartView.Centre(59.3293, 18.0686), mapStartView(pins))
    }

    @Test
    fun `pins at different spots are fitted`() {
        val pins = listOf(pin(59.0, 18.0), pin(60.0, 17.0), pin(59.5, 18.5))

        assertEquals(MapStartView.Fit(north = 60.0, east = 18.5, south = 59.0, west = 17.0), mapStartView(pins))
    }

    @Test
    fun `a fit never zooms past the zoom the map tiles exist for`() {
        assertTrue(FIT_MAX_ZOOM <= MAP_TILE_MAX_ZOOM)
        assertTrue(SINGLE_SPOT_ZOOM <= MAP_TILE_MAX_ZOOM)
    }
}
