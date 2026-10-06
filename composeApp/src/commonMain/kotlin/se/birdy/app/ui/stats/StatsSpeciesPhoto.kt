package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import se.birdy.app.ui.components.hasForcedMidWordBreak
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri

/** Initial letter size as a share of the photo circle (dp-pinned, it must fit the circle at any font scale). */
private const val INITIAL_SIZE_FACTOR = 0.42f
private const val NAME_FONT_STEP_SP = 1f

/**
 * A species' plate photo in a circle (release 1.3.0 Task 7c), for the timeline of first finds and
 * the "Mest sedda" seals. Until the photo has loaded, or for a species without one, the circle
 * shows the name's initial on sand, so an empty circle never looks like a broken image.
 * Decorative: the row or seal around it carries the name for TalkBack.
 */
@Composable
internal fun StatsSpeciesPhoto(
    name: String,
    heroImagePath: String?,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clip(CircleShape).background(SandCreme),
        contentAlignment = Alignment.Center,
    ) {
        val fontScale = LocalDensity.current.fontScale
        Text(
            text = name.take(1).uppercase(),
            fontFamily = rememberDmSerifDisplay(),
            fontSize = (maxWidth.value * INITIAL_SIZE_FACTOR / fontScale).sp,
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
 * A species name that steps its font size down (to [minFontSize]) rather than break a word in
 * half: long Swedish compound names ("Svarthakedopping") at a large system font would otherwise be
 * force-wrapped mid-word. Same step-down as PhotoHero's title, see [hasForcedMidWordBreak].
 */
@Suppress("LongParameterList") // a Text wrapper: style, sizes and layout knobs pass straight through
@Composable
internal fun FittedSpeciesName(
    text: String,
    style: TextStyle,
    fontSize: TextUnit,
    minFontSize: TextUnit,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    Text(
        text = text,
        style = style,
        fontSize = size,
        textAlign = textAlign,
        modifier = modifier,
        onTextLayout = { result ->
            val canShrink = size.value > minFontSize.value
            if (canShrink && (result.didOverflowHeight || result.hasForcedMidWordBreak(text))) {
                size = (size.value - NAME_FONT_STEP_SP).coerceAtLeast(minFontSize.value).sp
            }
        },
    )
}
