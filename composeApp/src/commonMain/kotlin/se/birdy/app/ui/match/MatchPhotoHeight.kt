package se.birdy.app.ui.match

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// The Match photo sits above the name (PhotoHero's textBelowPhoto), which makes the hero taller,
// and "Spara observation" below it is this screen's main action: it must be in view without
// scrolling, also with larger text (2026-10-06). So the photo takes the room that is left above
// what follows it down to the bottom of the save button (photo credit, name, latin name, match
// bar, paper sheet with stamp, note field, button), between MATCH_PHOTO_MIN and MATCH_PHOTO_MAX.
// That height depends on the font, the text size, the name and the photo credit, so it is
// measured on screen ([BelowPhoto]); the estimate below only sizes the first frame. With large
// text on a small phone the floor wins and the button is a short scroll away: the bird is never
// cropped to a strip. MatchSaveButtonFoldTest checks this with real text metrics: on a 360x800dp
// phone the button is in view at 100% text and the photo keeps its floor at 130% and 200%; on a
// 412x915dp phone the button is in view at 130%.
internal val MATCH_PHOTO_MAX = 260.dp
internal val MATCH_PHOTO_MIN = 160.dp
internal val MATCH_FOLD_MARGIN = 8.dp

// What follows the photo before it has been measured, on a 360dp-wide phone (measured 2026-10-07
// with real text metrics: 443dp at 100% text with a one-line credit, 458dp with a two-line one,
// and about 250dp more per +1.0 of font scale, where the name, the credit and the stamp's
// caption wrap).
private val MATCH_BELOW_PHOTO_ESTIMATE = 450.dp
private val MATCH_BELOW_PHOTO_ESTIMATE_PER_FONT_SCALE = 250.dp

internal fun estimatedBelowPhoto(fontScale: Float): Dp =
    MATCH_BELOW_PHOTO_ESTIMATE + MATCH_BELOW_PHOTO_ESTIMATE_PER_FONT_SCALE * (fontScale - 1f).coerceAtLeast(0f)

/**
 * The Match photo's height for a screen whose visible area is [viewportHeight] tall (the hero
 * draws behind the [statusBar], so that height comes off the top), with [belowPhoto] of content
 * between the photo's bottom edge and the save button's.
 */
internal fun matchPhotoHeight(
    viewportHeight: Dp,
    statusBar: Dp,
    belowPhoto: Dp,
): Dp = (viewportHeight - statusBar - belowPhoto - MATCH_FOLD_MARGIN).coerceIn(MATCH_PHOTO_MIN, MATCH_PHOTO_MAX)

/**
 * What follows the Match photo down to the bottom of the save button, measured on screen: the
 * distance from the photo's bottom edge to the button's, both read in the scrolling content's own
 * coordinates, so scrolling doesn't change it. It doesn't depend on the photo's height either, so
 * sizing the photo from it settles in one frame. Each of the three places reports its coordinates
 * when it is laid out; the distance is taken once all three are attached. Without a photo, or
 * after saving (the button is gone), the last value stays.
 */
internal class BelowPhoto {
    var content: LayoutCoordinates? = null
    var photo: LayoutCoordinates? = null
    var button: LayoutCoordinates? = null
    var px: Float? by mutableStateOf(null)

    fun measure() {
        val content = content
        val photo = photo
        val button = button
        if (content?.isAttached != true || photo?.isAttached != true || button?.isAttached != true) return
        val photoBottom = content.localPositionOf(photo, Offset(0f, photo.size.height.toFloat())).y
        val buttonBottom = content.localPositionOf(button, Offset(0f, button.size.height.toFloat())).y
        val below = buttonBottom - photoBottom
        val current = px
        // Sub-pixel jitter must not resize the photo over and over.
        if (current == null || abs(current - below) > 1f) px = below
    }
}

@Composable
internal fun rememberMatchPhotoHeight(
    viewportHeight: Dp,
    belowPhotoPx: Float?,
): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    val below = belowPhotoPx?.let { with(density) { it.toDp() } } ?: estimatedBelowPhoto(density.fontScale)
    return matchPhotoHeight(viewportHeight, statusBar, below)
}
