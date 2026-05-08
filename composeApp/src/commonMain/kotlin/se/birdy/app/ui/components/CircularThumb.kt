package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme

/**
 * Circular thumbnail with a warm-offwhite border ring, clipped to a circle.
 * Falls back to SandCreme background when the photo is loading or unavailable.
 * Sized by the [modifier] at call-site (typically 50dp).
 */
@Composable
fun CircularThumb(
    photoPath: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(SandCreme)
                .border(width = 2.dp, color = OffwhiteWarm, shape = CircleShape),
    ) {
        AsyncImage(
            model = "file://$photoPath",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().clip(CircleShape),
        )
    }
}
