package se.birdy.app.ui.stats.charts

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YearRingGeometryTest {
    private fun assertNear(
        expected: Float,
        actual: Float,
        tolerance: Float = 0.01f,
    ) = assertTrue(abs(expected - actual) <= tolerance, "expected $expected but was $actual")

    @Test
    fun `January starts just after the top and the months run clockwise`() {
        assertNear(15f, YearRingGeometry.centerAngle(1))
        assertNear(105f, YearRingGeometry.centerAngle(4))
        assertNear(345f, YearRingGeometry.centerAngle(12))
        assertTrue(YearRingGeometry.segmentStartAngle(1) > 0f)
        assertTrue(YearRingGeometry.segmentStartAngle(1) + YearRingGeometry.SEGMENT_SWEEP < 30f)
        // December ends before it reaches January again.
        assertTrue(YearRingGeometry.segmentStartAngle(12) + YearRingGeometry.SEGMENT_SWEEP < 360f)
    }

    @Test
    fun `an angle of zero points straight up and ninety points right`() {
        val center = Offset(100f, 100f)
        val up = YearRingGeometry.pointAt(0f, 50f, center)
        assertNear(100f, up.x)
        assertNear(50f, up.y)
        val right = YearRingGeometry.pointAt(90f, 50f, center)
        assertNear(150f, right.x)
        assertNear(100f, right.y)
    }

    @Test
    fun `the compose arc angle is the clock angle turned a quarter back`() {
        assertNear(-90f, YearRingGeometry.toComposeArcAngle(0f))
        assertNear(0f, YearRingGeometry.toComposeArcAngle(90f))
    }

    @Test
    fun `segment length grows with the count up to the busiest month`() {
        val inner = 60f
        val maxOuter = 120f
        assertNear(inner, YearRingGeometry.outerRadius(0, 4, inner, maxOuter, minLength = 3f))
        assertNear(maxOuter, YearRingGeometry.outerRadius(4, 4, inner, maxOuter, minLength = 3f))
        assertNear(90f, YearRingGeometry.outerRadius(2, 4, inner, maxOuter, minLength = 3f))
    }

    @Test
    fun `a single find in a busy year still gets a visible segment`() {
        val outer = YearRingGeometry.outerRadius(1, 200, inner = 60f, maxOuter = 120f, minLength = 4f)
        assertNear(64f, outer)
    }

    @Test
    fun `month letters and the ring fit inside the canvas`() {
        val radii = YearRingGeometry.radii(sizePx = 600f, labelExtentPx = 14f, edgePaddingPx = 4f, labelGapPx = 12f)
        assertTrue(radii.letter + 14f <= 300f, "letters leave the canvas: $radii")
        assertTrue(radii.maxOuter + 12f + 14f <= radii.letter + 0.01f, "segments run into the letters: $radii")
        assertNear(radii.maxOuter * YearRingGeometry.INNER_TO_MAX_OUTER, radii.inner)
        assertTrue(radii.inner > 0f)
    }

    @Test
    fun `larger month letters shrink the ring instead of leaving the canvas`() {
        val normal = YearRingGeometry.radii(sizePx = 600f, labelExtentPx = 14f, edgePaddingPx = 4f, labelGapPx = 12f)
        val large = YearRingGeometry.radii(sizePx = 600f, labelExtentPx = 28f, edgePaddingPx = 4f, labelGapPx = 12f)
        assertTrue(large.maxOuter < normal.maxOuter)
        assertTrue(large.letter + 28f <= 300f)
        assertTrue(large.maxOuter + 12f + 28f <= large.letter + 0.01f)
    }

    @Test
    fun `a month number goes inside its segment when there is room`() {
        val placement = numberAt(inner = 60f, outer = 120f, maxOuter = 120f, extent = 10f, padding = 4f)
        assertTrue(placement.inside)
        assertNear(106f, placement.radius)
        assertEquals(1f, placement.scale)
        assertTrue(placement.radius - 10f >= 60f && placement.radius + 10f <= 120f)
    }

    @Test
    fun `a month number goes just outside a short segment`() {
        val placement = numberAt(inner = 60f, outer = 70f, maxOuter = 120f, extent = 10f, padding = 4f)
        assertTrue(!placement.inside)
        assertNear(84f, placement.radius)
        assertEquals(1f, placement.scale)
        // Clear of its own segment and still inside the busiest month's reach.
        assertTrue(placement.radius - 10f >= 70f)
        assertTrue(placement.radius + 10f <= 120f)
    }

    @Test
    fun `a number with no room outside shrinks to fit on its segment`() {
        // The busiest month at a large font: its segment reaches the guide circle, so there is no
        // "outside" left, and the number at full size is too big for the segment.
        val placement = numberAt(inner = 60f, outer = 80f, maxOuter = 80f, extent = 12f, padding = 3f)
        assertTrue(placement.inside)
        assertNear(70f, placement.radius)
        assertTrue(placement.scale < 1f)
        val scaledExtent = 12f * placement.scale
        assertTrue(placement.radius - scaledExtent >= 60f + 3f - 0.01f)
        assertTrue(placement.radius + scaledExtent <= 80f - 3f + 0.01f)
    }

    @Test
    fun `a number that fits nowhere is left out of the drawing`() {
        assertNull(YearRingGeometry.numberPlacement(inner = 60f, outer = 66f, maxOuter = 70f, numberExtent = 10f, paddingPx = 3f))
    }

    @Test
    fun `the best month and the current month carry their counts`() {
        val counts = listOf(1, 1, 2, 1, 4, 2, 1, 1, 0, 2, 0, 0)
        assertEquals(listOf(5, 10), YearRingGeometry.labeledMonths(counts, bestMonth = 5, currentMonth = 10))
    }

    @Test
    fun `when months tie for the most finds every one of them carries its count`() {
        val counts = listOf(2, 0, 1, 0, 2, 0, 0, 0, 2, 1, 0, 0)
        assertEquals(listOf(1, 5, 9, 10), YearRingGeometry.labeledMonths(counts, bestMonth = null, currentMonth = 10))
    }

    @Test
    fun `a month without finds carries no count`() {
        assertEquals(emptyList(), YearRingGeometry.labeledMonths(List(12) { 0 }, bestMonth = null, currentMonth = 10))
        assertEquals(listOf(3), YearRingGeometry.labeledMonths(listOf(0, 0, 1) + List(9) { 0 }, bestMonth = 3, currentMonth = 10))
    }

    private fun numberAt(
        inner: Float,
        outer: Float,
        maxOuter: Float,
        extent: Float,
        padding: Float,
    ): YearRingGeometry.NumberPlacement =
        assertNotNull(YearRingGeometry.numberPlacement(inner, outer, maxOuter, numberExtent = extent, paddingPx = padding))

    @Test
    fun `the center text keeps its size when it fits`() {
        val scale =
            YearRingGeometry.centerTextScale(innerRadius = 100f, gapPx = 4f) { s ->
                listOf(40f * s to 20f * s, 60f * s to 50f * s)
            }
        assertEquals(1f, scale)
    }

    @Test
    fun `the center text shrinks until it fits inside the inner circle`() {
        val lines: (Float) -> List<Pair<Float, Float>> = { s -> listOf(80f * s to 30f * s, 120f * s to 90f * s, 60f * s to 20f * s) }
        val scale = YearRingGeometry.centerTextScale(innerRadius = 70f, gapPx = 4f, measure = lines)
        assertTrue(scale < 1f)
        assertTrue(YearRingGeometry.centerTextFits(70f, 4f, lines(scale)), "scale $scale still does not fit")
    }
}
