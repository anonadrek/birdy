package se.birdy.app.ui.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import se.birdy.app.ui.theme.paperBackground

@OptIn(ExperimentalResourceApi::class)
@Composable
fun BirdySplash(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().paperBackground(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Res.getUri("files/branding/wordmark.png"),
            contentDescription = "Birdy",
            modifier =
                Modifier
                    .fillMaxWidth(0.72f)
                    .padding(horizontal = 24.dp),
            contentScale = ContentScale.Fit,
        )
    }
}
