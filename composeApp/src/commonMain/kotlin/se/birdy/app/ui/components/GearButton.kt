package se.birdy.app.ui.components

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

/**
 * Default (papper): en 32dp ⊙-cirkel med copper-outline och inlagd ⚙-glyph, centrerad i ett 48dp
 * tryckmål (WCAG/Material-minimum). Tap → onClick (typiskt navigation till Settings).
 *
 * [onDark]: true på en mörk yta (t.ex. en [PhotoHero]) — byts mot den delade [GlassIconButton]
 * (36dp mörkt glas i stället för kopparringen); glyfen behåller sin nuvarande 18dp-storlek
 * (endast glaset växte, från 32dp till [GlassIconButton]s delade 36dp). Default (false) är
 * oförändrat.
 */
@Composable
fun GearButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    if (onDark) {
        GlassIconButton(
            icon = Icons.Outlined.Settings,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
            iconSize = 18.dp,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.size(48.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp).border(1.5.dp, AccentCopper, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = contentDescription,
                    tint = AccentCopper,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
