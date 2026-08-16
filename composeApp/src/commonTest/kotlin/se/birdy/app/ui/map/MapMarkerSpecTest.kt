package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapMarkerSpecTest {
    @Test
    fun marker_canvas_dimensions_match_android_formula() {
        assertEquals(52f, MapMarkerSpec.markerWidth()) // 46 + 2*3
        assertEquals(61f, MapMarkerSpec.markerHeight()) // 46 + 9 + 2*3
    }
}
