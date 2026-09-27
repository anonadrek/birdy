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
        tertiary = Brass,
        onTertiary = BrassInk,
        background = MossCreme,
        onBackground = TextOnCreme,
        surface = MossCreme,
        onSurface = TextOnCreme,
        surfaceVariant = SandCreme,
        onSurfaceVariant = InkMuted,
        outline = OutlineInk,
        outlineVariant = Hairline,
        surfaceContainerLowest = CardPaper,
        surfaceContainerLow = PaperTop,
        surfaceContainer = MossCreme,
        surfaceContainerHigh = PaperBottom,
        surfaceContainerHighest = SandCreme,
        surfaceBright = CardPaper,
        surfaceDim = SandCreme,
        inverseSurface = HeroMossDeep,
        inverseOnSurface = TextOnHero,
        inversePrimary = AccentCopperLight,
    )

@Composable
fun BirdyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BirdyLightColors,
        typography = birdyTypography(),
        content = content,
    )
}
