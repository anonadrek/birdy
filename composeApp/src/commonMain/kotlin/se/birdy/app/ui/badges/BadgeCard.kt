package se.birdy.app.ui.badges

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.LabelOnCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeProgress

@Composable
fun BadgeCard(
    progress: BadgeProgress,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnlocked = progress.isUnlocked
    val bg = if (isUnlocked) AccentCopper else SandCreme
    val fg = if (isUnlocked) TextOnHero else LabelOnCreme
    val description = contentDescription

    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .clickable(onClick = onClick)
                .semantics {
                    this.contentDescription = description
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badgeGlyph(progress.badge, isUnlocked),
            color = fg,
            fontSize = if (isUnlocked) 22.sp else 18.sp,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

private fun badgeGlyph(
    badge: Badge,
    unlocked: Boolean,
): String {
    if (!unlocked) return "?"
    return when (badge.category) {
        BadgeCategory.PROGRESSION -> "★"
        BadgeCategory.STREAK_WEEKLY -> "▲"
        BadgeCategory.STREAK_MONTHLY -> "▼"
        BadgeCategory.SEASON -> "❅"
        BadgeCategory.FAMILY -> "●"
        BadgeCategory.RARE -> "◆"
    }
}
