package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapTilerUrlsTest {
    @Test
    fun builds_same_shape_as_android_xy_tile_source() {
        assertEquals(
            "https://api.maptiler.com/maps/toner-v2/13/4400/2686@2x.png?key=abc123",
            mapTilerTileUrl(z = 13, x = 4400, y = 2686, apiKey = "abc123"),
        )
    }
}
