package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk

/**
 * Gradient-line · ❦-ornament · gradient-line.
 * Sits between the sub-line and content on every screen-intro.
 */
@Composable
fun OrnamentRule(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradientLine(modifier = Modifier.weight(1f))
        Text(
            text = "❦",
            color = AccentCopper,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        GradientLine(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GradientLine(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            MarginaliaInk.copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
    )
}
