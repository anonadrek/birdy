package se.birdy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
        // Defensive (fix wave B8): M3's tonal-elevation overlay tints a surface toward
        // surfaceTint as elevation rises (e.g. AppBar/Card/BottomSheet shadows). Left at the
        // scheme default it's `primary` = AccentCopper, which would rust-tint paper surfaces
        // that use tonal elevation — transparent keeps every surface reading as flat paper.
        surfaceTint = Color.Transparent,
    )

@Composable
fun BirdyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BirdyLightColors,
        typography = birdyTypography(),
        content = content,
    )
}
