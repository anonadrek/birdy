package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri

/** Initial letter size as a share of the photo circle (dp-pinned, it must fit the circle at any font scale). */
private const val INITIAL_SIZE_FACTOR = 0.42f
private const val NAME_FONT_STEP_SP = 1f

/**
 * A species' plate photo in a circle of [size] (release 1.3.0 Task 7c), for the timeline of first
 * finds and the "Mest sedda" seals. Until the photo has loaded, or for a species without one, the
 * circle shows the name's initial on sand, so an empty circle never looks like a broken image.
 * Decorative: the row or seal around it carries the name for TalkBack.
 */
@Composable
internal fun StatsSpeciesPhoto(
    name: String,
    heroImagePath: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(SandCreme),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = (size.value * INITIAL_SIZE_FACTOR / fontScale).sp,
            color = InkMuted,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (heroImagePath != null) {
            AsyncImage(
                model = speciesImageUri(heroImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/**
 * A species name at the largest size from [fontSize] down to [minFontSize] (1sp steps) at which
 * every word fits the available width, so long Swedish compound names ("Svarthakedopping") at a
 * large system font step down instead of being broken mid-word. The size is measured up front
 * ([wordFitFontSize]), not stepped down over layout passes, so it is stable from the first frame
 * and when a lazy row scrolls back in.
 */
@Composable
internal fun FittedSpeciesName(
    text: String,
    style: TextStyle,
    fontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val sizes =
            generateSequence(fontSize.value) { it - NAME_FONT_STEP_SP }
                .takeWhile { it >= minFontSize.value }
                .map { it.sp }
                .toList()
        val size = wordFitFontSize(listOf(text), style, sizes, maxWidth) ?: minFontSize
        Text(text = text, style = style, fontSize = size)
    }
}

/**
 * The first of [sizes] (largest first) at which every word of every one of [texts], set in
 * [style], fits [maxWidth] on its own; null when not even the last does. Shared by
 * [FittedSpeciesName] and the "Mest sedda" seals, which pick one size for all three names.
 */
@Composable
internal fun wordFitFontSize(
    texts: List<String>,
    style: TextStyle,
    sizes: List<TextUnit>,
    maxWidth: Dp,
): TextUnit? {
    val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
    return rememberTextMeasurer().firstSizeFittingWords(texts, style, sizes, maxWidthPx)
}

/**
 * The measuring behind [wordFitFontSize], outside composition, for a caller that keeps the result
 * in `remember` (the weekly recap's grid, release 1.3.0 Task 7j). Words are split at plain spaces
 * only, so a no-break space keeps its two words together as one.
 */
internal fun TextMeasurer.firstSizeFittingWords(
    texts: List<String>,
    style: TextStyle,
    sizes: List<TextUnit>,
    maxWidthPx: Float,
): TextUnit? {
    val words = texts.flatMap { it.split(' ') }.filter { it.isNotEmpty() }
    return sizes.firstOrNull { size ->
        words.all { word ->
            measure(word, style.copy(fontSize = size), maxLines = 1, softWrap = false).size.width <= maxWidthPx
        }
    }
}
