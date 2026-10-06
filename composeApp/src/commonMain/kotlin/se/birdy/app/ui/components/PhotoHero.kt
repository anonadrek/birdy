@file:Suppress("TooManyFunctions") // Plan 3 Task 6 added rememberStatusBarTracking to this
// already-dense shared header file (PhotoHero + PaperSheet + their private helpers, spec
// §4.3); splitting it further would scatter one component's internals across files for no
// readability gain.

package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
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
import se.birdy.app.ui.theme.PhotoBand
import se.birdy.app.ui.theme.PhotoLoading
import se.birdy.app.ui.theme.PhotoScrim
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Full-bleed photo header (spec 2026-09-24 §4.3): photo, a neutral dark scrim that follows the
 * bottom-aligned text block itself (a modifier on the text Column, [drawTextFollowingScrim]),
 * kicker + serif title (+ optional italic latin name, apricot subtitle and a hairline meta
 * row). Since 2026-10-06 nothing with a hue is drawn over the photo (Albin: the moss green over
 * the bird made the species hard to see): the bird shows in its true colors, darkened only
 * right behind the text and, when the hero sits behind the status bar, in a thin strip under
 * the status-bar icons ([StatusBarScrim]).
 *
 * [drawBehindStatusBar]: false (default) = the hero starts below the status bar (AppScaffold's
 * BelowStatusBar or the screen's own padding). true = the route draws behind the status bar
 * (Plan 3 Task 6): the photo grows by the status bar's height, [topBar] gets
 * statusBarsPadding(), and the hero reports to [LocalStatusBarBackdrop] whether it is still
 * under the status bar.
 *
 * [topBar] is drawn LAST (declared after the text Column below), so the gear/back button it
 * hosts is never dimmed by any scrim (fix wave A2c, finding 1: the text-following scrim's
 * fade used to paint over it whenever the text block started high enough to reach the topBar's
 * row).
 *
 * The hero's height is a MINIMUM, not a fixed size (fix wave A2, finding I2): long species
 * names can push the title to 2 lines even after [HeroTitle]'s auto-shrink, so the box grows
 * to fit the text block instead of clipping the meta row / [bottomContent] to nothing.
 *
 * @param image caller-supplied photo (e.g. an AsyncImage with ContentScale.Crop and
 *   Modifier.fillMaxSize()); a neutral [PhotoLoading] gray shows behind it while it loads.
 *   Null → the moss gradient only, with no scrims (the text clears AA on it by itself).
 * @param latinName optional scientific name, set in italic serif (NOT uppercased, unlike
 *   [metaStart]/[metaEnd] — binomial names must keep their case). Renders under the title.
 * @param bottomPadding extra space under the text, e.g. for a [PaperSheet] overlapping it —
 *   when this hero is followed by one, use `PaperSheetOverlap + 18.dp` so the sheet's rounded
 *   top doesn't sit flush against the last line of text.
 * @param textBelowPhoto false (default) = the photo fills the whole hero behind the text (the
 *   Identify tab's daily bird). true (Match, species profile; 2026-10-06, Albin: the species
 *   must be clearly visible) = the photo keeps [height] at the top (plus the status bar) and the
 *   text block starts at its bottom edge, on the plain [PhotoBand] with no scrim. The photo's
 *   last [PHOTO_FADE_OUT] fades out into the band (opacity only), so nothing at all is drawn
 *   over the bird; the hero grows to photo + text. No effect without a photo.
 * @param bottomContent extra content under the meta row, drawn over the same text-following
 *   scrim as the rest of the text block — it must be light-on-dark, like the rest of this
 *   header. A translucent LIGHT fill (e.g. a glass pill in `White.copy(alpha = 0.16f)`)
 *   lightens the backdrop under itself instead of darkening it, so [PhotoHeroContrastTest]'s
 *   text-scrim-alone premise doesn't cover it — such content needs its own contrast check.
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
    textBelowPhoto: Boolean = false,
    image: (@Composable BoxScope.() -> Unit)? = null,
    topBar: (@Composable BoxScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val serif = rememberDmSerifDisplay()
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val statusBarTracking = rememberStatusBarTracking(enabled = drawBehindStatusBar)
    // Behind the status bar the photo grows by its height, so the part below it keeps `height`.
    val photoHeight = if (drawBehindStatusBar) height + statusBarTop else height
    val photoAbove = textBelowPhoto && image != null
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = photoHeight)
                .then(statusBarTracking)
                .heroBackdrop(hasPhoto = image != null, band = photoAbove),
    ) {
        // A caller's image uses fillMaxSize(), which can't size this (possibly taller
        // than `height`) Box by itself — matchParentSize() defers it until this Box's size is
        // resolved from the Column below, then stretches it to match (see photoArea).
        Box(photoArea(photoAbove, photoHeight)) {
            if (image != null) {
                image()
                if (drawBehindStatusBar) StatusBarScrim(statusBarTop)
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    // Text below the photo: it starts at the photo's bottom edge, on the band.
                    .padding(top = if (photoAbove) photoHeight else 0.dp)
                    .fillMaxWidth()
                    // Only text drawn over the photo needs the scrim; on the band it has none.
                    .drawTextFollowingScrim(enabled = image != null && !photoAbove) // before padding: see its KDoc.
                    .padding(start = 22.dp, end = 22.dp, bottom = bottomPadding),
        ) {
            MicroLabel(kicker, color = AccentCopperLight)
            Spacer(Modifier.height(8.dp))
            HeroTitle(title = title, titleAccent = titleAccent, serif = serif)
            if (latinName != null) {
                HeroLatinName(latinName = latinName, serif = serif)
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
        // Declared LAST (after the text Column) so it draws — and hit-tests — on top: the
        // text-following scrim's fade can reach this high when the text block is short, and
        // this must never be dimmed by it (fix wave A2c, finding 1).
        topBar?.let { bar ->
            val barModifier = if (drawBehindStatusBar) Modifier.statusBarsPadding() else Modifier.padding(top = 8.dp)
            Box(Modifier.fillMaxWidth().then(barModifier)) { bar() }
        }
    }
}

/**
 * For a hero drawn behind the status bar: reports to [LocalStatusBarBackdrop] whether the photo
 * still reaches under the status bar (light icons) and returns the modifier that tracks it. The
 * state only flips at the threshold, so scrolling doesn't recompose the hero every frame.
 */
@Composable
private fun rememberStatusBarTracking(enabled: Boolean): Modifier {
    if (!enabled) return Modifier
    val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current).toFloat()
    var underStatusBar by remember { mutableStateOf(true) }
    ReportStatusBarBackdrop(isDark = underStatusBar)
    return Modifier.onGloballyPositioned { coords ->
        val under = coords.boundsInRoot().bottom > statusBarPx
        if (under != underStatusBar) underStatusBar = under
    }
}

// Text alpha over the scrim — shared with PhotoHeroContrastTest, which proves these (plus
// TEXT_SCRIM_ALPHA below) clear WCAG AA over a worst-case (blown-out white) photo. Change all
// three together.
internal const val LATIN_NAME_TEXT_ALPHA = 0.85f
internal const val META_TEXT_ALPHA = 0.75f

// Status-bar scrim (2026-10-06, replaces the full-photo moss ramp of the 2026-09-24 mockups,
// which tinted the whole lower half of the photo green and hid the bird). Only for a hero drawn
// behind the status bar: the system draws light icons there (see rememberStatusBarTracking),
// and over a bright sky or snow they need a little darkening. Flat STATUS_BAR_SCRIM_ALPHA black
// under the icons, fading to nothing STATUS_BAR_SCRIM_TAIL below them, so the bird itself is
// untouched. The back/gear buttons don't rely on it: they have their own GlassOnPhoto discs
// (GlassOnPhotoContrastTest).
private const val STATUS_BAR_SCRIM_ALPHA = 0.25f
private val STATUS_BAR_SCRIM_TAIL = 24.dp

@Composable
private fun StatusBarScrim(statusBarTop: Dp) {
    if (statusBarTop <= 0.dp) return
    val total = statusBarTop + STATUS_BAR_SCRIM_TAIL
    Box(
        Modifier.fillMaxWidth().height(total).background(
            Brush.verticalGradient(
                0f to PhotoScrim.copy(alpha = STATUS_BAR_SCRIM_ALPHA),
                (statusBarTop / total) to PhotoScrim.copy(alpha = STATUS_BAR_SCRIM_ALPHA),
                1f to PhotoScrim.copy(alpha = 0f),
            ),
        ),
    )
}

// Text-following scrim (fix wave A2b, 2026-09-27; neutral since 2026-10-06): painted behind the
// bottom-aligned text Column itself (see PhotoHero), so it covers only the part of the photo the
// text actually sits on, whatever the block's height (1- or 2-line title, font scale). It fades
// in over TEXT_SCRIM_FADE above the column's top edge (eased, so the edge doesn't read as a band),
// then stays FLAT at TEXT_SCRIM_ALPHA down to the column's bottom. It is PhotoScrim (black), not
// moss: the photo under the text is darkened, never tinted. This is what carries WCAG AA.
// TEXT_SCRIM_ALPHA is the smallest value (steps of 0.05) that clears WCAG AA in
// PhotoHeroContrastTest (the apricot kicker is the limiting line, 4.64:1 over pure white) —
// change both together.
private val TEXT_SCRIM_FADE = 48.dp
internal const val TEXT_SCRIM_ALPHA = 0.70f

// The fade is eased (smoothstep, sampled in TEXT_SCRIM_FADE_STEPS even steps): it starts and
// ends flat, so neither the top of the fade nor the start of the flat part shows an edge.
private const val TEXT_SCRIM_FADE_STEPS = 8

private fun smoothstep(t: Float): Float = t * t * (1 + 2 * (1 - t))

// When the text sits below the photo (PhotoHero's textBelowPhoto): how far up from its bottom
// edge the photo fades out into the band, so it has no hard edge against the kicker below it.
private val PHOTO_FADE_OUT = 32.dp

/**
 * Where the photo is drawn: behind the whole hero, or (photoAbove) at the top in its own
 * [height], with the text block starting below it. In that case the photo's last
 * [PHOTO_FADE_OUT] fades out into the [PhotoBand] behind it; the fade only lowers the photo's own
 * opacity (no color is added), and no text or scrim is ever drawn over the photo.
 */
private fun BoxScope.photoArea(
    photoAbove: Boolean,
    height: Dp,
): Modifier =
    if (photoAbove) {
        Modifier.fillMaxWidth().height(height).fadeOutBottom(PHOTO_FADE_OUT)
    } else {
        Modifier.matchParentSize()
    }

private fun Modifier.fadeOutBottom(fade: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val fadePx = fade.toPx()
            val mask =
                Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - fadePx,
                    endY = size.height,
                )
            onDrawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
        }

/**
 * What shows behind the photo: the [PhotoBand] the name sits on when the text is below the photo
 * ([band]), a neutral [PhotoLoading] gray while a full-hero photo loads (never a green flash
 * before the bird), or the designed moss gradient when there is no photo at all.
 */
private fun Modifier.heroBackdrop(
    hasPhoto: Boolean,
    band: Boolean,
): Modifier =
    if (band) {
        background(PhotoBand)
    } else if (hasPhoto) {
        background(PhotoLoading)
    } else {
        background(Brush.verticalGradient(listOf(HeroMossLight, HeroMossMid, HeroMossDeep)))
    }

/**
 * Must be applied BEFORE any `.padding(...)` on the text Column: `DrawScope.size` here needs to
 * be the column's full width + full height (incl. bottomPadding), not just its inner content
 * box, and the fade drawn above y=0 relies on Compose not clipping drawBehind to its own
 * bounds. Uses [drawWithCache] so the [Brush] is rebuilt only when the column's size actually
 * changes (relayout), not on every frame the way a plain `drawBehind` block would.
 * [enabled] false (no photo) → draws nothing.
 */
private fun Modifier.drawTextFollowingScrim(enabled: Boolean): Modifier {
    if (!enabled) return this
    return drawWithCache {
        val fadePx = TEXT_SCRIM_FADE.toPx()
        val fade =
            Brush.verticalGradient(
                colors =
                    (0..TEXT_SCRIM_FADE_STEPS).map { step ->
                        PhotoScrim.copy(alpha = TEXT_SCRIM_ALPHA * smoothstep(step.toFloat() / TEXT_SCRIM_FADE_STEPS))
                    },
                startY = -fadePx,
                endY = 0f,
            )
        val flat = PhotoScrim.copy(alpha = TEXT_SCRIM_ALPHA)
        onDrawBehind {
            drawRect(brush = fade, topLeft = Offset(0f, -fadePx), size = Size(size.width, fadePx))
            drawRect(color = flat, size = size)
        }
    }
}

// A practical legibility aid on top of the scrim, not counted in PhotoHeroContrastTest.
private val HeroTextShadow = Shadow(color = Color.Black.copy(alpha = 0.35f), offset = Offset(0f, 1f), blurRadius = 6f)

@Composable
private fun HeroLatinName(
    latinName: String,
    serif: FontFamily,
) {
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
 *
 * T12d: made `internal` (was `private`) so [se.birdy.app.screenshots.StatsScreenshotTest]'s
 * generic mid-word-break guard can reuse it across every laid-out text on the stats screen,
 * instead of re-deriving the same check.
 */
internal fun TextLayoutResult.hasForcedMidWordBreak(text: String): Boolean {
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
 * Just the rounded top edge of a [PaperSheet], for a screen whose content after the [PhotoHero]
 * is a LazyColumn of separate items rather than one sheet (Premium and the thank-you screen):
 * it rides up over the hero by [overlap] exactly like a sheet does, so the photo ends under a
 * rounded edge instead of on a hard line, and it takes no height of its own, so the items below
 * keep their places. Nothing is drawn over the photo outside the edge itself. The hero above
 * needs `bottomPadding = PaperSheetOverlap + 18.dp`, and the screen behind must be [color].
 */
@Composable
fun PaperSheetTop(
    color: Color,
    modifier: Modifier = Modifier,
    overlap: Dp = PaperSheetOverlap,
) {
    Box(
        modifier
            .fillMaxWidth()
            .overlapUpward(overlap)
            .height(overlap)
            .background(color, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
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
