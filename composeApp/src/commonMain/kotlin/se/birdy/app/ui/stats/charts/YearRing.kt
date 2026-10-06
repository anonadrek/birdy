package se.birdy.app.ui.stats.charts

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.stats.charts.YearRingGeometry.MONTHS
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Brass
import se.birdy.app.ui.theme.BrassInk
import se.birdy.app.ui.theme.BrassText
import se.birdy.app.ui.theme.CardPaper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.OutlineInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import kotlin.math.hypot

/**
 * The year as a ring (release 1.3.0 Task 7c, design option B): twelve month segments around a
 * circle like a clock face, January at the top, each segment as long as that month's share of
 * the busiest month. The year's total sits in the middle with the species count above it, the
 * month letters run around the outside.
 *
 * Colors: rust for every month, brass ONLY for [currentMonth] (seasons are named in the line under
 * the ring, never colored). A brass fill alone is ≈2.6:1 on paper, below WCAG 1.4.11's 3:1 for
 * graphics, so the current month's segment gets a [BrassText] edge (≥4.6:1). A month without
 * finds is a short hairline tick: lighter for months still to come. Only the best month and the
 * current month carry their count, because a ring compares months less exactly than bars.
 *
 * Text drawn on a canvas never reaches the semantics tree, so the whole ring carries one
 * [contentDescription] with every month's count in plain words (built by the caller from string
 * resources). Large system fonts shrink the ring and the center text instead of clipping: see
 * [YearRingGeometry.radii] and [YearRingGeometry.centerTextScale].
 */
@Suppress("LongParameterList") // ring data and resolved strings; a wrapper type would only rename them
@Composable
internal fun YearRing(
    monthCounts: List<Int>,
    currentMonth: Int?,
    bestMonth: Int?,
    monthLetters: List<String>,
    centerText: RingCenterText,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = TEXT_MEASURER_CACHE_SIZE)
    val fonts = RingFonts(caveat = rememberCaveat(), serif = rememberDmSerifDisplay())
    Canvas(modifier = modifier, contentDescription = contentDescription) {
        if (monthCounts.size != MONTHS || monthLetters.size != MONTHS) return@Canvas
        val letters =
            monthLetters.mapIndexed { index, letter ->
                val style = if (index + 1 == currentMonth) currentLetterStyle else letterStyle
                measurer.measure(letter, tight(style))
            }
        val radii =
            YearRingGeometry.radii(
                sizePx = size.minDimension,
                labelExtentPx = letters.maxOf { it.extent() },
                edgePaddingPx = EDGE_PADDING.toPx(),
                labelGapPx = LABEL_GAP.toPx(),
            )
        if (radii.inner <= 0f) return@Canvas

        drawGuideCircle(radii.maxOuter)
        val maxCount = monthCounts.maxOrNull() ?: 0
        val outers =
            monthCounts.map { count ->
                YearRingGeometry.outerRadius(count, maxCount, radii.inner, radii.maxOuter, MIN_SEGMENT_LENGTH.toPx())
            }
        for (month in 1..MONTHS) {
            if (monthCounts[month - 1] > 0) {
                drawMonthSegment(month, radii.inner, outers[month - 1], isCurrent = month == currentMonth)
            } else {
                drawEmptyMonthTick(month, radii.inner, currentMonth)
            }
        }
        drawCircle(color = CardPaper, radius = radii.inner)
        drawCircle(color = Hairline, radius = radii.inner, style = Stroke(width = 1.dp.toPx()))

        letters.forEachIndexed { index, layout ->
            val at = YearRingGeometry.pointAt(YearRingGeometry.centerAngle(index + 1), radii.letter, center)
            drawCentered(layout, at)
        }
        listOfNotNull(bestMonth, currentMonth).distinct().forEach { month ->
            val count = monthCounts.getOrNull(month - 1) ?: 0
            if (count > 0) {
                drawMonthCount(measurer, MonthCount(month, count, outers[month - 1], month == currentMonth), radii)
            }
        }
        drawCenterText(measurer, fonts, radii.inner, centerText)
    }
}

/** What the ring's middle says: [note] ("5 arter") above the big [total] ("15") above [label] ("FYND"). */
internal data class RingCenterText(
    val total: String,
    val label: String,
    val note: String?,
)

private class RingFonts(
    val caveat: FontFamily,
    val serif: FontFamily,
)

private class MonthCount(
    val month: Int,
    val count: Int,
    val outer: Float,
    val isCurrent: Boolean,
)

/** The busiest month's reach as a dashed hairline, so every segment's length reads against it. */
private fun DrawScope.drawGuideCircle(radius: Float) {
    val dashes = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))
    drawCircle(color = Hairline, radius = radius, style = Stroke(width = 1.dp.toPx(), pathEffect = dashes))
}

private fun DrawScope.drawMonthSegment(
    month: Int,
    inner: Float,
    outer: Float,
    isCurrent: Boolean,
) {
    val start = YearRingGeometry.segmentStartAngle(month)
    val sweep = YearRingGeometry.SEGMENT_SWEEP
    val path =
        Path().apply {
            val innerStart = YearRingGeometry.pointAt(start, inner, center)
            val outerStart = YearRingGeometry.pointAt(start, outer, center)
            val innerEnd = YearRingGeometry.pointAt(start + sweep, inner, center)
            moveTo(innerStart.x, innerStart.y)
            lineTo(outerStart.x, outerStart.y)
            arcTo(Rect(center, outer), YearRingGeometry.toComposeArcAngle(start), sweep, forceMoveTo = false)
            lineTo(innerEnd.x, innerEnd.y)
            arcTo(Rect(center, inner), YearRingGeometry.toComposeArcAngle(start + sweep), -sweep, forceMoveTo = false)
            close()
        }
    drawPath(path, color = if (isCurrent) Brass else AccentCopper)
    if (isCurrent) {
        drawPath(path, color = BrassText, style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))
    }
}

private fun DrawScope.drawEmptyMonthTick(
    month: Int,
    inner: Float,
    currentMonth: Int?,
) {
    val angle = YearRingGeometry.centerAngle(month)
    val isCurrent = month == currentMonth
    val isFuture = currentMonth != null && month > currentMonth
    val color =
        when {
            isCurrent -> BrassText
            isFuture -> Hairline
            else -> OutlineInk.copy(alpha = PAST_EMPTY_TICK_ALPHA)
        }
    drawLine(
        color = color,
        start = YearRingGeometry.pointAt(angle, inner + 2.dp.toPx(), center),
        end = YearRingGeometry.pointAt(angle, inner + 7.dp.toPx(), center),
        strokeWidth = (if (isCurrent) 2.dp else 1.5.dp).toPx(),
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawMonthCount(
    measurer: TextMeasurer,
    month: MonthCount,
    radii: YearRingGeometry.Radii,
) {
    val text = month.count.toString()
    val placement =
        YearRingGeometry.numberPlacement(
            inner = radii.inner,
            outer = month.outer,
            maxOuter = radii.maxOuter,
            numberExtent = measurer.measure(text, tight(numberStyle)).extent(),
            paddingPx = NUMBER_PADDING.toPx(),
        )
    val color =
        when {
            placement.inside && month.isCurrent -> BrassInk
            placement.inside -> TextOnHero
            month.isCurrent -> BrassText
            else -> AccentCopper
        }
    val layout = measurer.measure(text, tight(numberStyle.copy(color = color)))
    drawCentered(layout, YearRingGeometry.pointAt(YearRingGeometry.centerAngle(month.month), placement.radius, center))
}

private fun DrawScope.drawCenterText(
    measurer: TextMeasurer,
    fonts: RingFonts,
    inner: Float,
    text: RingCenterText,
) {
    fun layouts(scale: Float): List<TextLayoutResult> {
        val noteStyle = TextStyle(fontFamily = fonts.caveat, fontWeight = FontWeight.W600, color = AccentCopper)
        val totalStyle = TextStyle(fontFamily = fonts.serif, color = TextOnCreme)
        return listOfNotNull(
            text.note?.let { measurer.measure(it, tight(noteStyle.copy(fontSize = NOTE_SIZE * scale))) },
            measurer.measure(text.total, tight(totalStyle.copy(fontSize = TOTAL_SIZE * scale))),
            measurer.measure(text.label.uppercase(), tight(totalLabelStyle.copy(fontSize = TOTAL_LABEL_SIZE * scale))),
        )
    }
    val gap = CENTER_LINE_GAP.toPx()
    val scale =
        YearRingGeometry.centerTextScale(inner - CENTER_PADDING.toPx(), gap) { s ->
            layouts(s).map { it.size.width.toFloat() to it.size.height.toFloat() }
        }
    val lines = layouts(scale)
    var top = center.y - (lines.sumOf { it.size.height } + gap * (lines.size - 1)) / 2
    lines.forEach { layout ->
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, top))
        top += layout.size.height + gap
    }
}

private fun DrawScope.drawCentered(
    layout: TextLayoutResult,
    at: Offset,
) = drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))

/** How far a laid-out label reaches from its center in any direction (half its box diagonal). */
private fun TextLayoutResult.extent(): Float = hypot(size.width.toFloat(), size.height.toFloat()) / 2

/** Line box as tall as the font itself, so labels sit where their glyphs are (no extra leading). */
private fun tight(style: TextStyle): TextStyle =
    style.copy(
        lineHeight = 1.em,
        lineHeightStyle =
            LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both),
    )

private val letterStyle =
    TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.W600, fontSize = 11.sp, color = InkMuted)
private val currentLetterStyle = letterStyle.copy(fontWeight = FontWeight.W700, color = TextOnCreme)
private val numberStyle =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W700,
        fontSize = 12.sp,
        color = Color.Unspecified,
    )
private val totalLabelStyle =
    TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        letterSpacing = 0.15.em,
        color = InkMuted,
    )

private val NOTE_SIZE: TextUnit = 17.sp
private val TOTAL_SIZE: TextUnit = 44.sp
private val TOTAL_LABEL_SIZE: TextUnit = 9.sp

private val EDGE_PADDING = 2.dp
private val LABEL_GAP = 6.dp
private val MIN_SEGMENT_LENGTH = 4.dp
private val NUMBER_PADDING = 3.dp
private val CENTER_PADDING = 8.dp
private val CENTER_LINE_GAP = 3.dp

private const val PAST_EMPTY_TICK_ALPHA = 0.45f
private const val TEXT_MEASURER_CACHE_SIZE = 40
