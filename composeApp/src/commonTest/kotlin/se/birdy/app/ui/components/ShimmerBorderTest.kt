package se.birdy.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShimmerBorderTest {
    private val peak = 0.275f
    private val band = 0.2f

    @Test
    fun `alpha peaks at the progress position`() {
        assertEquals(peak, shimmerBorderAlpha(stopT = 0.5f, progress = 0.5f, peakAlpha = peak, bandFraction = band), 1e-4f)
    }

    @Test
    fun `alpha is zero far from the band`() {
        // d = 0.5, edge = 0.1 → outside band
        assertEquals(0f, shimmerBorderAlpha(stopT = 0.0f, progress = 0.5f, peakAlpha = peak, bandFraction = band), 1e-4f)
    }

    @Test
    fun `alpha fades linearly to half at half the edge distance`() {
        // edge = band/2 = 0.1; half-edge distance 0.05 → peak/2
        assertEquals(
            peak / 2f,
            shimmerBorderAlpha(stopT = 0.55f, progress = 0.5f, peakAlpha = peak, bandFraction = band),
            1e-4f,
        )
    }

    @Test
    fun `band wraps around the seam`() {
        // progress near 0, stop near 1 → small circular distance → still lit
        assertTrue(shimmerBorderAlpha(stopT = 0.98f, progress = 0.02f, peakAlpha = peak, bandFraction = band) > 0f)
    }
}
