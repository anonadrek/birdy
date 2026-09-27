package se.birdy.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.Hairline

private val PillShape = RoundedCornerShape(percent = 50)

/**
 * Small bordered pill for compact toggle-style actions — Lifelist's sort chip today, Task 10's
 * Archive filters next. The visible [text] identifies the action, so a plain [Hairline] border
 * (not the copper/brass fills the app's CTAs use) reads fine here: this is never the one primary
 * action on a screen.
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
) {
    Text(
        text = text,
        color = AccentCopper,
        fontWeight = FontWeight.W600,
        fontSize = 12.sp,
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .clip(PillShape)
                .border(1.dp, Hairline, PillShape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    if (contentDescription != null) this.contentDescription = contentDescription
                }.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
