package se.birdy.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/**
 * WCAG 2.1 relative luminance + contrast ratio, shared by [ColorContrastTest] and
 * [se.birdy.app.ui.components.PhotoHeroContrastTest] — pure color math, no compose layout
 * (there is no measurer available in a commonTest).
 */
internal fun srgbChannel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

internal fun relativeLuminance(c: Color): Double =
    0.2126 * srgbChannel(c.red) + 0.7152 * srgbChannel(c.green) + 0.0722 * srgbChannel(c.blue)

internal fun contrastRatio(
    a: Color,
    b: Color,
): Double {
    val (hi, lo) = listOf(relativeLuminance(a), relativeLuminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}

/** Composites a semi-transparent [foreground] (at [alpha]) over an opaque [background] ("over" blend, straight alpha). */
internal fun compositeOver(
    foreground: Color,
    alpha: Float,
    background: Color,
): Color =
    Color(
        red = foreground.red * alpha + background.red * (1 - alpha),
        green = foreground.green * alpha + background.green * (1 - alpha),
        blue = foreground.blue * alpha + background.blue * (1 - alpha),
    )
