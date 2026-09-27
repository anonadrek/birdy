package se.birdy.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline
import se.birdy.app.ui.theme.HeroMossDeep
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * Small bordered pill, two variants sharing one visual footprint (same padding/size, so an
 * action pill and a filter pill sit at the same height in the same row):
 *
 * - **Action pill** ([selected] left `null`, the default) — Lifelist's and Archive's sort chip.
 *   Rust [AccentCopper] text on a plain [Hairline] border; a single tap toggles through states,
 *   so it's a [Role.Button] via [Modifier.clickable]. Pixel-identical to pre-T10b.
 * - **Filter pill** ([selected] `true`/`false`) — Archive's group-filter chips (T10b). Unselected:
 *   transparent + [Hairline] border + [TextOnCreme] text. Selected: [HeroMossDeep] fill +
 *   [TextOnHero] text, no border. One of a mutually-exclusive set, so it's a [Role.RadioButton]
 *   via [Modifier.selectable] — the caller's row should carry
 *   `Modifier.selectableGroup()` (see `ArchiveScreen.ChipBar`) so TalkBack announces the group.
 *
 * [contentDescription], when given, replaces what's announced for screen readers — useful when
 * the visible text alone ("Senaste") doesn't convey the control's purpose the way sighted
 * context does; leave it null when the visible text is already a sufficient label on its own.
 * `minimumInteractiveComponentSize()` (mirrors `ZoomChips.kt`) pads the touch target to Android's
 * 48dp minimum without growing the pill's visible size.
 */
@Composable
fun BirdyPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    selected: Boolean? = null,
) {
    val isFilterPill = selected != null
    val isSelected = selected == true
    val background = if (isFilterPill && isSelected) HeroMossDeep else Color.Transparent
    val border = if (isFilterPill && isSelected) Color.Transparent else Hairline
    val textColor =
        when {
            !isFilterPill -> AccentCopper
            isSelected -> TextOnHero
            else -> TextOnCreme
        }
    Text(
        text = text,
        color = textColor,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clip(PillShape)
                .background(background)
                .border(1.dp, border, PillShape)
                .let { m ->
                    if (isFilterPill) {
                        m.selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
                    } else {
                        m.clickable(role = Role.Button, onClick = onClick)
                    }
                }.semantics(mergeDescendants = true) {
                    if (contentDescription != null) this.contentDescription = contentDescription
                }.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
