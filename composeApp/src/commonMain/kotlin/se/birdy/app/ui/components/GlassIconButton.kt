package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import se.birdy.app.ui.theme.GlassOnPhoto
import se.birdy.app.ui.theme.TextOnHero

/**
 * Shared "dark glass" icon button for controls drawn on a photo or dark-moss surface (a
 * [PhotoHero] top bar, Premium's close button): a real ≥48dp touch target (WCAG/Material
 * minimum) holding a 36dp [GlassOnPhoto] disc with a [TextOnHero]-tinted icon. Extracted (Task
 * 11c) from three near-identical copies that had drifted slightly (32dp vs 36dp discs, 18/20/24dp
 * icons) in [BackButton], [GearButton] and `PremiumScreen`'s close button.
 *
 * `contentDescription` lives on this outer clickable node (the [Icon]'s own is null) so TalkBack
 * announces exactly this tap target, not a separate nested one; `traversalIndex = -1f` keeps it
 * first in reading order regardless of where it's placed in the layout (mirrors the close
 * button's original T9c #2 reasoning).
 *
 * [iconSize] defaults to ~20dp per spec; [GearButton] passes 18dp so its glyph stays the exact
 * size it was before this extraction — only its glass disc grew, from 32dp to this shared 36dp.
 */
@Composable
internal fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 20.dp,
) {
    val label = contentDescription
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    this.contentDescription = label
                    traversalIndex = -1f
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(GlassOnPhoto, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextOnHero,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
