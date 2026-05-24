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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm

/**
 * 32dp ⊙-cirkel med copper-outline och en tillbaka-pil. Variant.OnPaper passar
 * Field Journal-bakgrund; Variant.OnDark läggs ovanpå mörk kamera-bakgrund och
 * får en off-white fyllning för kontrast.
 */
@Composable
fun BackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: BackButtonVariant = BackButtonVariant.OnPaper,
) {
    val background =
        when (variant) {
            BackButtonVariant.OnPaper -> Color.Transparent
            BackButtonVariant.OnDark -> OffwhiteWarm
        }
    IconButton(
        onClick = onClick,
        modifier =
            modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.25.dp, AccentCopper, CircleShape),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = AccentCopper,
            modifier = Modifier.size(14.dp),
        )
    }
}

enum class BackButtonVariant { OnPaper, OnDark }
