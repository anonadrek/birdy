package se.birdy.app.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_unknown_species
import birdy_bird_scanner.composeapp.generated.resources.recap_find_a11y_fmt
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.dailybird.dailyBirdDateA11yLabel
import se.birdy.app.ui.stats.firstSizeFittingWords
import se.birdy.app.ui.theme.InkMuted
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.app.util.speciesImageUri

/** Columns as designed; the grid tries fewer, down to one, when a name can't fit at a large font. */
private const val GRID_COLUMNS = 4

/** Caption sizes tried per column count, largest first. */
private val CaptionSizes: List<TextUnit> = listOf(15.sp, 14.sp, 13.sp, 12.sp)
private val GridGap = 8.dp
private val GridCorner = 8.dp

/** Initial letter size as a share of the photo (dp-pinned: it must fit the photo at any font scale). */
private const val INITIAL_SIZE_FACTOR = 0.42f

/**
 * "Alla fynd" (release 1.3.0 Task 7j): every find of the week as a grid of square photos with the
 * species in Caveat under each, newest first. Four columns as designed; with a large system font
 * the grid drops to fewer columns until every word of every name fits, so no name is broken
 * mid-word. A square opens the find.
 */
@Composable
internal fun FindsGrid(
    finds: List<RecapFindItem>,
    onObservationClick: (String) -> Unit,
) {
    val unknown = stringResource(Res.string.diary_detail_unknown_species)
    val names = finds.map { it.speciesName ?: unknown }
    val captionStyle = TextStyle(fontFamily = rememberCaveat(), color = MarginaliaInk, lineHeight = 1.1.em)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Measured once per set of names, width and text size, not on every recomposition.
        val layout =
            remember(names, maxWidth, density.fontScale, captionStyle) {
                with(density) { gridLayout(names, captionStyle, measurer, maxWidth.toPx(), GridGap.toPx()) }
            }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            finds.zip(names).chunked(layout.columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GridGap)) {
                    row.forEach { (find, name) ->
                        GridCell(
                            find = find,
                            name = name,
                            captionStyle = captionStyle.copy(fontSize = layout.captionSize),
                            onClick = { onObservationClick(find.observationId) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(layout.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private data class GridLayout(
    val columns: Int,
    val captionSize: TextUnit,
)

/** The most columns (and the largest caption size for them) at which every word of every name fits a cell. */
private fun gridLayout(
    names: List<String>,
    style: TextStyle,
    measurer: TextMeasurer,
    maxWidthPx: Float,
    gapPx: Float,
): GridLayout {
    for (columns in GRID_COLUMNS downTo 1) {
        val cellWidthPx = (maxWidthPx - gapPx * (columns - 1)) / columns
        val size = measurer.firstSizeFittingWords(names, style, CaptionSizes, cellWidthPx)
        if (size != null) return GridLayout(columns, size)
    }
    return GridLayout(columns = 1, captionSize = CaptionSizes.last())
}

@Composable
private fun GridCell(
    find: RecapFindItem,
    name: String,
    captionStyle: TextStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(Res.string.recap_find_a11y_fmt, name, dailyBirdDateA11yLabel(find.date))
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(GridCorner))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        RecapPhoto(
            find = find,
            shape = RoundedCornerShape(GridCorner),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Spacer(Modifier.height(4.dp))
        Text(text = name, style = captionStyle)
    }
}

/**
 * A find's photo, never tinted: the user's own photo, or for a heard find (whose own image is a
 * waveform) the species' plate photo first. Until it has loaded, or for a find without either, the
 * shape shows the name's initial on sand, so an empty frame never looks like a broken image.
 * Decorative: the row, cell or day around it carries the words for TalkBack.
 */
@Composable
internal fun RecapPhoto(
    find: RecapFindItem,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.clip(shape).background(SandCreme),
        contentAlignment = Alignment.Center,
    ) {
        val fontScale = LocalDensity.current.fontScale
        Text(
            text = find.speciesName?.take(1)?.uppercase() ?: "?",
            fontFamily = rememberDmSerifDisplay(),
            fontSize = (minOf(maxWidth, maxHeight).value * INITIAL_SIZE_FACTOR / fontScale).sp,
            color = InkMuted,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        val model = recapImageModel(find)
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** What [RecapPhoto] loads: the find's own photo first, the plate photo first for a heard find. */
internal fun recapImageModel(find: RecapFindItem): String? {
    val own = find.photoPath.takeIf { it.isNotBlank() }?.let { "file://$it" }
    val plate = find.heroImagePath?.let { speciesImageUri(it) }
    return if (find.isHeard) plate ?: own else own ?: plate
}
