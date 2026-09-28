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
        // Defensive (fix wave B8, corrected B9): M3's surfaceColorAtElevation does
        // surfaceTint.copy(alpha = a).compositeOver(surface) as elevation rises (e.g.
        // AppBar/Card/BottomSheet shadows). Color.Transparent is black at alpha 0, so
        // copy(alpha = a) on it produces a translucent BLACK, not "no tint" — tonally
        // elevated surfaces would turn grey. surfaceTint = MossCreme (== surface) instead:
        // compositing a color over itself is a no-op, so every surface stays flat paper.
        surfaceTint = MossCreme,
    )

@Composable
fun BirdyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BirdyLightColors,
        typography = birdyTypography(),
        content = content,
    )
}
