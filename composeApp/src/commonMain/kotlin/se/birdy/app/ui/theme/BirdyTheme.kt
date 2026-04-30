package se.birdy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BirdyLightColors =
    lightColorScheme(
        primary = AccentCopper,
        onPrimary = TextOnHero,
        secondary = HeroMossMid,
        onSecondary = TextOnHero,
        background = MossCreme,
        onBackground = TextOnCreme,
        surface = MossCreme,
        onSurface = TextOnCreme,
        surfaceVariant = SandCreme,
        onSurfaceVariant = TextOnCreme,
    )

@Composable
fun BirdyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BirdyLightColors,
        typography = BirdyTypography,
        content = content,
    )
}
