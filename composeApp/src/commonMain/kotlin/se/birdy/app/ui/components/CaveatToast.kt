package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.rememberCaveat

/**
 * En anpassad snackbar-visual i Field Journal-stil. Används via
 * `SnackbarHost(state) { data -> CaveatToast(data) }`.
 */
@Composable
fun CaveatToast(data: SnackbarData) {
    Text(
        text = data.visuals.message,
        fontFamily = rememberCaveat(),
        fontSize = 16.sp,
        color = AccentCopper,
        fontWeight = FontWeight.W600,
        modifier =
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PaperTop)
                .border(1.dp, AccentCopper.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
