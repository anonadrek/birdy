package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun StampNumberBadge(
    number: Int,
    modifier: Modifier = Modifier,
) {
    val serif = rememberDmSerifDisplay()
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(AccentCopper)
                .border(width = 1.5.dp, color = OffwhiteWarm, shape = RoundedCornerShape(percent = 50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "#$number",
            color = OffwhiteWarm,
            fontFamily = serif,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
        )
    }
}
