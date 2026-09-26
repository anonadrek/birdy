package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Full-bleed photo header (spec 2026-09-24 §4.3): photo, dark-moss scrim from the bottom,
 * kicker + serif title (+ optional italic latin name, apricot subtitle and a hairline meta
 * row).
 *
 * [drawBehindStatusBar]: false (default) = the app's outer Scaffold already pads the status
 * bar, so the hero starts below it. true = only for routes that let it draw behind the
 * status bar (a later, device-verified task); then [topBar] gets statusBarsPadding().
 *
 * The hero's height is a MINIMUM, not a fixed size (fix wave A2, finding I2): long species
 * names can push the title to 2 lines even after [HeroTitle]'s auto-shrink, so the box grows
 * to fit the text block instead of clipping the meta row / [bottomContent] to nothing.
 *
 * @param image caller-supplied photo (e.g. an AsyncImage with ContentScale.Crop and
 *   Modifier.fillMaxSize()). Null → moss gradient only.
 * @param latinName optional scientific name, set in italic serif (NOT uppercased, unlike
 *   [metaStart]/[metaEnd] — binomial names must keep their case). Renders under the title.
 * @param bottomPadding extra space under the text, e.g. for a [PaperSheet] overlapping it —
 *   when this hero is followed by one, use `PaperSheetOverlap + 18.dp` so the sheet's rounded
 *   top doesn't sit flush against the last line of text.
 */
@Suppress("LongParameterList") // shared header for 5 screens (spec §4.3); the wide slot count is deliberate.
@Composable
fun PhotoHero(
    kicker: String,
    title: String,
    modifier: Modifier = Modifier,
    titleAccent: String? = null,
    latinName: String? = null,
    subtitle: String? = null,
    metaStart: String? = null,
    metaEnd: String? = null,
    height: Dp = 300.dp,
    bottomPadding: Dp = 18.dp,
    drawBehindStatusBar: Boolean = false,
    image: (@Composable BoxScope.() -> Unit)? = null,
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = height)
                .background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep))),
    ) {
        // A caller's image/HeroScrim use fillMaxSize(), which can't size this (possibly taller
        // than `height`) Box by itself — matchParentSize() defers them until this Box's size is
        // resolved from the Column below, then stretches them to match it.
        Box(Modifier.matchParentSize()) {
            image?.invoke(this)
            HeroScrim()
        }
        topBar?.let { bar ->
            val barModifier = if (drawBehindStatusBar) Modifier.statusBarsPadding() else Modifier.padding(top = 8.dp)
            Box(Modifier.fillMaxWidth().then(barModifier)) { bar() }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = bottomPadding),
        ) {
            MicroLabel(kicker, color = AccentCopperLight)
            Spacer(Modifier.height(8.dp))
            HeroTitle(title = title, titleAccent = titleAccent, serif = serif)
            if (latinName != null) {
                Text(
                    text = latinName,
                    color = TextOnHero.copy(alpha = LATIN_NAME_TEXT_ALPHA),
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = HeroTextShadow),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AccentCopperLight,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 20.sp,
                    style = TextStyle(shadow = HeroTextShadow),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (metaStart != null || metaEnd != null) {
                HeroMetaRow(metaStart = metaStart, metaEnd = metaEnd)
            }
            bottomContent?.invoke(this)
        }
    }
}

// Text alpha over the scrim — shared with PhotoHeroContrastTest, which proves these (plus the
// scrim stops below) clear WCAG AA over a worst-case light photo. Change all three together.
internal const val LATIN_NAME_TEXT_ALPHA = 0.85f
internal const val META_TEXT_ALPHA = 0.75f

// Scrim stops (spec 2026-09-24 §4.3; strengthened 2026-09-26 fix wave A2, finding I3: the
// kicker/latinName/subtitle/meta text only cleared 1.5-4.6:1 over a light photo before this).
// A single HeroMossDeep alpha ramp — light at the very top (the photo still reads as a photo),
// strong by SCRIM_STRONG_FRACTION, where the bottom-aligned text block can start even in the
// worst case this suite screenshots (2-line title, 130% font scale — see
// ComponentsScreenshotTest.hero_long_en_130). See PhotoHeroContrastTest for the numeric proof;
// change these four numbers together with it.
private const val SCRIM_TOP_ALPHA = 0.18f
private const val SCRIM_STRONG_FRACTION = 0.16f
private const val SCRIM_STRONG_ALPHA = 0.92f
private const val SCRIM_BOTTOM_ALPHA = 0.97f

/** HeroMossDeep's scrim alpha at [fraction] (0f = hero top, 1f = hero bottom). */
internal fun heroScrimAlphaAt(fraction: Float): Float {
    val clamped = fraction.coerceIn(0f, 1f)
    return if (clamped <= SCRIM_STRONG_FRACTION) {
        lerp(SCRIM_TOP_ALPHA, SCRIM_STRONG_ALPHA, clamped / SCRIM_STRONG_FRACTION)
    } else {
        lerp(SCRIM_STRONG_ALPHA, SCRIM_BOTTOM_ALPHA, (clamped - SCRIM_STRONG_FRACTION) / (1f - SCRIM_STRONG_FRACTION))
    }
}

private fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction

@Composable
private fun HeroScrim() {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to HeroMossDeep.copy(alpha = heroScrimAlphaAt(0f)),
                SCRIM_STRONG_FRACTION to HeroMossDeep.copy(alpha = heroScrimAlphaAt(SCRIM_STRONG_FRACTION)),
                1f to HeroMossDeep.copy(alpha = heroScrimAlphaAt(1f)),
            ),
        ),
    )
}

// A practical legibility aid on top of the scrim, not counted in PhotoHeroContrastTest.
private val HeroTextShadow = Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 6f)

private val HeroTitleMaxFontSize = 42.sp
private val HeroTitleMinFontSize = 28.sp
private const val HERO_TITLE_FONT_STEP_SP = 2f
private const val HERO_TITLE_MAX_LINES = 2

/**
 * True if any non-last line of [text] (as laid out by this result) ends WITHOUT consuming a
 * whitespace character — i.e. the wrap broke a single unbroken run (a compound word with no
 * spaces) rather than a natural word boundary.
 *
 * [TextAutoSize.StepBased] does NOT catch this: a name like "Halsbandsflugsnappare" (one
 * 22-char word, no spaces) still measures as "2 lines, no overflow" at 42sp — Compose's line
 * breaker force-splits an unbreakable run rather than let it overflow the width, and
 * hasVisualOverflow is false either way — so auto-size never tries a smaller size where the
 * whole word would fit on one line. Hence the manual step-down below instead of autoSize.
 */
private fun TextLayoutResult.hasForcedMidWordBreak(text: String): Boolean {
    for (line in 0 until lineCount - 1) {
        val end = getLineEnd(line, visibleEnd = false)
        val brokeAtWhitespace = end in 1..text.length && text[end - 1].isWhitespace()
        if (!brokeAtWhitespace) return true
    }
    return false
}

@Composable
private fun HeroTitle(
    title: String,
    titleAccent: String?,
    serif: FontFamily,
) {
    if (titleAccent == null) {
        // Long species names need up to 2 lines (84/828 Swedish, 239/839 English at 100% font
        // scale; more at larger accessibility scales) — shrink instead of clipping or
        // hyphenating mid-word. See hasForcedMidWordBreak's KDoc for why this is a manual
        // onTextLayout step-down rather than TextAutoSize.
        var fontSize by remember(title) { mutableStateOf(HeroTitleMaxFontSize) }
        BasicText(
            text = title,
            modifier = Modifier.semantics { heading() },
            style =
                TextStyle(
                    color = TextOnHero,
                    fontFamily = serif,
                    fontSize = fontSize,
                    letterSpacing = (-0.02).em,
                    shadow = HeroTextShadow,
                ),
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            maxLines = HERO_TITLE_MAX_LINES,
            onTextLayout = { result ->
                val tooSmallAlready = fontSize <= HeroTitleMinFontSize
                if (!tooSmallAlready && (result.hasVisualOverflow || result.hasForcedMidWordBreak(title))) {
                    fontSize = (fontSize.value - HERO_TITLE_FONT_STEP_SP).coerceAtLeast(HeroTitleMinFontSize.value).sp
                }
            },
        )
    } else {
        Text(
            text =
                buildAnnotatedString {
                    append(title)
                    append(" ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = AccentCopperLight)) {
                        append(titleAccent)
                    }
                },
            color = TextOnHero,
            fontFamily = serif,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.02).em,
            maxLines = HERO_TITLE_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = HeroTextShadow),
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun HeroMetaRow(
    metaStart: String?,
    metaEnd: String?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .drawBehind {
                    val line = TextOnHero.copy(alpha = 0.3f)
                    drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }.padding(top = 8.dp),
    ) {
        MetaText(metaStart.orEmpty(), Modifier.weight(1f))
        MetaText(metaEnd.orEmpty())
    }
}

@Composable
private fun MetaText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        color = TextOnHero.copy(alpha = META_TEXT_ALPHA),
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.W600,
        fontSize = 9.5.sp,
        // Explicit, tight line height — otherwise this inherits the theme's bodyLarge 22sp
        // line height, making the hero's text block ~20dp taller than the mockup.
        lineHeight = 12.sp,
        letterSpacing = 0.12.em,
        style = TextStyle(shadow = HeroTextShadow),
        modifier = modifier,
    )
}

/** Extra bottom space a [PhotoHero] needs before a [PaperSheet] with the default overlap
 * follows it, e.g. `bottomPadding = PaperSheetOverlap + 18.dp`. */
val PaperSheetOverlap = 24.dp

/**
 * Paper sheet that slides up over a [PhotoHero] (24dp rounded top). Place it directly after
 * the hero; [overlap] pulls it up over the photo. Spec §4.3 "Arksida".
 *
 * The screen root under a [PaperSheet] must share its [color] (e.g. `Modifier.background(MossCreme)`
 * on the screen/Scaffold root) — otherwise a hairline seam of the screen's own background shows
 * through the sheet's rounded top corners, above the fold.
 */
@Composable
fun PaperSheet(
    modifier: Modifier = Modifier,
    overlap: Dp = PaperSheetOverlap,
    color: Color = MossCreme,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .overlapUpward(overlap)
                // background(color, shape) paints only within the rounded top — unlike clip(),
                // it doesn't cut content that overshoots it (the Match stamp's "slam" animation
                // and its shadow).
                .background(color, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 18.dp, vertical = 18.dp),
        content = content,
    )
}

/**
 * Pulls the content up by [overlap] without leaving a gap the size of [overlap] in the
 * parent's layout. A plain `Modifier.offset(y = -overlap)` moves the drawing but not the
 * measured size, so the parent still reserves the un-overlapped height and a strip of
 * whatever's behind it (the page background) shows below the sheet.
 */
private fun Modifier.overlapUpward(overlap: Dp): Modifier =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val overlapPx = overlap.roundToPx()
        layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
            placeable.placeRelative(0, -overlapPx)
        }
    }
