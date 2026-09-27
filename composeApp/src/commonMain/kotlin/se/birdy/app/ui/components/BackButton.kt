package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm

/**
 * Universell tillbaka-pil. Default (papper): en 40dp ⊙-bricka med solid off-white fyllning +
 * copper-ring + copper-pil — den fyllda behållaren bryter av mot pappersbakgrunden så pilen
 * läser som en knapp i stället för att smälta in i Field Journal-ornamentiken.
 *
 * [onDark]: true på en mörk yta (t.ex. en [PhotoHero]) — byts mot den delade [GlassIconButton]
 * (mörkt glas, samma mönster som Premiums stängknapp). Default (false) är oförändrat.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    if (onDark) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            onClick = onClick,
            modifier = modifier,
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier =
                modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(OffwhiteWarm)
                    .border(1.5.dp, AccentCopper, CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = contentDescription,
                tint = AccentCopper,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
