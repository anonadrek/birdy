package se.birdy.app.ui.stats.charts

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geometry for [YearRing] (release 1.3.0 Task 7c), kept apart from the drawing so it can be
 * tested without a canvas.
 *
 * Angles are "clock" angles: degrees clockwise from twelve o'clock, so January's segment starts
 * just right of the top and December's ends just left of it (the ring reads like a clock face,
 * winter at the top, summer at the bottom). [toComposeArcAngle] converts to Compose's arc angles,
 * which start at three o'clock.
 */
internal object YearRingGeometry {
    const val MONTHS = 12
    const val DEGREES_PER_MONTH = 360f / MONTHS

    /** Gap on each side of a month's segment, so neighbouring months read as separate petals. */
    const val SEGMENT_GAP_DEGREES = 3f
    const val SEGMENT_SWEEP = DEGREES_PER_MONTH - 2 * SEGMENT_GAP_DEGREES

    /** The empty middle as a share of the busiest month's reach (48 / 94 in the approved mockup). */
    const val INNER_TO_MAX_OUTER = 48f / 94f

    private const val QUARTER_TURN = 90f
    private const val HALF_TURN_DEGREES = 180f

    /** Smallest center text scale before the text is left to overflow (it only gets there at absurd font sizes). */
    private const val MIN_CENTER_TEXT_SCALE = 0.4f
    private const val CENTER_TEXT_SCALE_STEP = 0.05f

    /** Smallest a month count may shrink to on its segment before it is left to the description. */
    private const val MIN_NUMBER_SCALE = 0.5f

    fun segmentStartAngle(month: Int): Float = (month - 1) * DEGREES_PER_MONTH + SEGMENT_GAP_DEGREES

    fun centerAngle(month: Int): Float = (month - 1) * DEGREES_PER_MONTH + DEGREES_PER_MONTH / 2

    fun toComposeArcAngle(clockAngle: Float): Float = clockAngle - QUARTER_TURN

    fun pointAt(
        clockAngle: Float,
        radius: Float,
        center: Offset,
    ): Offset {
        val radians = clockAngle * PI.toFloat() / HALF_TURN_DEGREES
        return Offset(center.x + radius * sin(radians), center.y - radius * cos(radians))
    }

    /**
     * How far a month's segment reaches: [inner] for no finds, [maxOuter] for the busiest month,
     * linear in between, and at least [minLength] past [inner] for any month with a find (one
     * find in a year of hundreds would otherwise be a sliver nobody can see).
     */
    fun outerRadius(
        count: Int,
        maxCount: Int,
        inner: Float,
        maxOuter: Float,
        minLength: Float,
    ): Float {
        if (count <= 0 || maxCount <= 0) return inner
        val length = (maxOuter - inner) * count.coerceAtMost(maxCount) / maxCount
        return inner + length.coerceAtLeast(minLength).coerceAtMost(maxOuter - inner)
    }

    data class Radii(
        /** Where the month letters' centers sit. */
        val letter: Float,
        /** How far the busiest month reaches. */
        val maxOuter: Float,
        /** The empty middle that holds the totals. */
        val inner: Float,
    )

    /**
     * Fits letters and ring into a square canvas of [sizePx]: the letters (each reaching
     * [labelExtentPx] from its center in any direction) sit just inside the edge, the busiest
     * month stops [labelGapPx] short of them. Larger letters (large system font) shrink the ring
     * rather than leaving the canvas.
     */
    fun radii(
        sizePx: Float,
        labelExtentPx: Float,
        edgePaddingPx: Float,
        labelGapPx: Float,
    ): Radii {
        val letter = sizePx / 2 - edgePaddingPx - labelExtentPx
        val maxOuter = (letter - labelExtentPx - labelGapPx).coerceAtLeast(0f)
        return Radii(letter = letter, maxOuter = maxOuter, inner = maxOuter * INNER_TO_MAX_OUTER)
    }

    data class NumberPlacement(
        /** On the segment (drawn in the on-fill color) or just past its tip (drawn on paper). */
        val inside: Boolean,
        /** Distance of the number's center from the ring's center. */
        val radius: Float,
        /** Below 1 when the number had to shrink to fit on its segment. */
        val scale: Float = 1f,
    )

    /**
     * Where a month's count goes, so it never overlaps its own segment in the wrong color:
     * 1. on the segment near its tip when the segment holds it at full size;
     * 2. else just past the tip, when there is room before the busiest month's reach (the month
     *    letters live beyond it);
     * 3. else (a long segment at a large font, e.g. the busiest month itself) shrunk to fit in the
     *    middle of the segment, down to [MIN_NUMBER_SCALE];
     * 4. else null: the count is left to the ring's content description.
     */
    fun numberPlacement(
        inner: Float,
        outer: Float,
        maxOuter: Float,
        numberExtent: Float,
        paddingPx: Float,
    ): NumberPlacement? {
        val length = outer - inner
        val justOutside = outer + paddingPx + numberExtent
        val shrunk = (length / 2 - paddingPx) / numberExtent
        return when {
            length >= 2 * (numberExtent + paddingPx) ->
                NumberPlacement(inside = true, radius = outer - paddingPx - numberExtent)
            justOutside + numberExtent <= maxOuter -> NumberPlacement(inside = false, radius = justOutside)
            shrunk >= MIN_NUMBER_SCALE -> NumberPlacement(inside = true, radius = (inner + outer) / 2, scale = shrunk)
            else -> null
        }
    }

    /**
     * Which months show their count on the ring: the best month (or, when months tie for the most
     * finds, every month sharing the top count) and the current month, if they have finds.
     */
    fun labeledMonths(
        monthCounts: List<Int>,
        bestMonth: Int?,
        currentMonth: Int?,
    ): List<Int> {
        val top = monthCounts.maxOrNull() ?: 0
        val best =
            when {
                bestMonth != null -> listOf(bestMonth)
                top > 0 -> monthCounts.indices.filter { monthCounts[it] == top }.map { it + 1 }
                else -> emptyList()
            }
        return (best + listOfNotNull(currentMonth))
            .distinct()
            .filter { (monthCounts.getOrNull(it - 1) ?: 0) > 0 }
            .sorted()
    }

    /**
     * True when [lines] (width to height, top to bottom, [gapPx] apart) stacked and centered fit
     * inside a circle of [innerRadius]: every line's corners must stay inside it.
     */
    fun centerTextFits(
        innerRadius: Float,
        gapPx: Float,
        lines: List<Pair<Float, Float>>,
    ): Boolean {
        val total = lines.sumOf { it.second.toDouble() }.toFloat() + gapPx * (lines.size - 1).coerceAtLeast(0)
        var top = -total / 2
        return lines.all { (width, height) ->
            val bottom = top + height
            val farthest = maxOf(-top, bottom)
            top = bottom + gapPx
            farthest < innerRadius && width / 2 <= sqrt(innerRadius * innerRadius - farthest * farthest)
        }
    }

    /**
     * The largest scale (1.0 down in 5 % steps) at which the center text, as [measure] reports it
     * for that scale, fits inside [innerRadius]. Large system fonts shrink the totals instead of
     * letting them spill over the segments.
     */
    fun centerTextScale(
        innerRadius: Float,
        gapPx: Float,
        measure: (scale: Float) -> List<Pair<Float, Float>>,
    ): Float {
        var scale = 1f
        while (scale > MIN_CENTER_TEXT_SCALE) {
            if (centerTextFits(innerRadius, gapPx, measure(scale))) return scale
            scale -= CENTER_TEXT_SCALE_STEP
        }
        return MIN_CENTER_TEXT_SCALE
    }
}
