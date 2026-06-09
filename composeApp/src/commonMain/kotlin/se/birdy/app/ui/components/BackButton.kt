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
 * Universell tillbaka-pil — samma utseende på ALLA del-skärmar (papper som mörk
 * kamera-bakgrund). En 40dp ⊙-bricka med solid off-white fyllning + copper-ring
 * + copper-pil. Den fyllda behållaren bryter av mot pappersbakgrunden så pilen
 * läser som en knapp i stället för att smälta in i Field Journal-ornamentiken.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
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
