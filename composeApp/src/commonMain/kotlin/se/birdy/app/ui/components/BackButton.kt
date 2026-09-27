package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.GlassOnPhoto
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.TextOnHero

/**
 * Universell tillbaka-pil — samma utseende på ALLA del-skärmar (papper som mörk
 * kamera-bakgrund). En 40dp ⊙-bricka med solid off-white fyllning + copper-ring
 * + copper-pil. Den fyllda behållaren bryter av mot pappersbakgrunden så pilen
 * läser som en knapp i stället för att smälta in i Field Journal-ornamentiken.
 *
 * [onDark]: true on a dark surface (e.g. a [PhotoHero]) — a real ≥48dp touch target holding a
 * 36dp [GlassOnPhoto] glass disc + a [TextOnHero]-tinted arrow, mirroring the Premium screen's
 * close button (`PremiumScreen.kt`). Default (false, off-white ring on paper) is unchanged.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    if (onDark) {
        // A local val, not the parameter name itself, on the RHS below: `contentDescription`
        // inside `semantics { }` would otherwise shadow the extension property with this same-
        // named function parameter (the pattern PremiumScreen's `closeLabel` also avoids).
        val backLabel = contentDescription
        Box(
            modifier =
                modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics { this.contentDescription = backLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.size(36.dp).background(GlassOnPhoto, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextOnHero,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
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
