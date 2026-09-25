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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline

/**
 * Hairline · ❦-ornament · hairline.
 * Sits between the sub-line and content on every screen-intro.
 */
@Composable
fun OrnamentRule(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HairlineSegment(modifier = Modifier.weight(1f))
        Text(
            text = "❦",
            color = AccentCopper,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        HairlineSegment(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun HairlineSegment(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(1.dp).background(Hairline))
}
