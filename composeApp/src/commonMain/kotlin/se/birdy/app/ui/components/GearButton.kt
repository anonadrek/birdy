package se.birdy.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper

/**
 * 32dp ⊙-cirkel med copper-outline och inlagd ⚙-glyph. Tap → onClick (typiskt
 * navigation till Settings).
 */
@Composable
fun GearButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(32.dp)
                .border(1.5.dp, AccentCopper, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = contentDescription,
            tint = AccentCopper,
            modifier = Modifier.size(18.dp),
        )
    }
}
