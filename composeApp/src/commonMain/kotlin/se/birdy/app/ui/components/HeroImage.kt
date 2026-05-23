package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.HeroMossMid
import se.birdy.app.util.speciesImageUri

@Composable
fun HeroImage(
    imagePath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(cornerRadius))
                .background(
                    Brush.linearGradient(colors = listOf(HeroMossLight, HeroMossMid, HeroMossDeep)),
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = speciesImageUri(imagePath),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("📷", style = MaterialTheme.typography.headlineLarge)
        }
    }
}
