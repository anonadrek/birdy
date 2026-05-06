package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.LabelOnCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Locale

@Composable
fun BadgeRecentCard(
    localizedName: String,
    unlockedAt: Instant,
    now: Instant,
    locale: Locale,
    zone: TimeZone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = localizedName
    Column(
        modifier =
            modifier
                .width(92.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentCopper)
                    .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "★", color = TextOnHero, fontSize = 22.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = localizedName,
            color = TextOnCreme,
            fontSize = 11.sp,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatRelativeBadgeDate(unlockedAt, now, zone, locale),
            color = LabelOnCreme,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
