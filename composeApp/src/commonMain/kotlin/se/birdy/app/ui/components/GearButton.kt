package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.GlassOnPhoto
import se.birdy.app.ui.theme.TextOnHero

/**
 * 32dp ⊙-cirkel med copper-outline och inlagd ⚙-glyph, centrerad i ett 48dp
 * tryckmål (WCAG/Material-minimum). Tap → onClick (typiskt navigation till Settings).
 *
 * [onDark]: true on a dark surface (e.g. a [PhotoHero]) — swaps the rust ring for a
 * translucent [GlassOnPhoto] "dark glass" circle and tints the glyph [TextOnHero] instead of
 * rust-on-rust. A translucent WHITE glass was tried first and read ~2:1 over a bright photo
 * (both the icon and the glass are light) — [GlassOnPhoto] mirrors the Premium screen's close
 * button, which reads ~6.7:1 (see its KDoc in `Color.kt`). Default (false, paper background) is
 * unchanged.
 */
@Composable
fun GearButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .let { m ->
                        if (onDark) {
                            m.background(GlassOnPhoto, CircleShape)
                        } else {
                            m.border(1.5.dp, AccentCopper, CircleShape)
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = contentDescription,
                tint = if (onDark) TextOnHero else AccentCopper,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
