package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomTest {
    @Test
    fun presets_filtered_to_device_max_and_clamped_to_ten() {
        // S23-klass kamera: max 12x → bara 1/2/5/10 visas (10 är taket).
        assertEquals(listOf(1f, 2f, 5f, 10f), zoomPresets(maxRatio = 12f))
    }

    @Test
    fun presets_drop_values_above_device_max_and_add_max_as_top() {
        // Enhet med max 6x → 1/2/5 + 6 som översta chip.
        assertEquals(listOf(1f, 2f, 5f, 6f), zoomPresets(maxRatio = 6f))
    }

    @Test
    fun presets_exactly_on_a_preset_do_not_duplicate() {
        assertEquals(listOf(1f, 2f, 5f), zoomPresets(maxRatio = 5f))
    }

    @Test
    fun no_zoom_capability_returns_empty() {
        assertEquals(emptyList(), zoomPresets(maxRatio = 1f))
    }

    @Test
    fun none_constant_is_identity() {
        assertEquals(ZoomState(1f, 1f, 1f), ZoomState.NONE)
    }
}
