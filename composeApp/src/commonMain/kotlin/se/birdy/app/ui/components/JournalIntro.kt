package se.birdy.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Field Journal intro block — the red thread:
 *  1) MicroLabel (Inter caps copper)
 *  2) JournalHeadline (DM Serif italic + Caveat accent)
 *  3) JournalSubLine (Caveat mossgrön) — optional
 *  4) OrnamentRule (line · ❦ · line)
 *
 * Sits at the top of every redesigned screen. Default padding = 24dp horizontal,
 * 28dp vertical.
 */
@Composable
fun JournalIntro(
    label: String,
    headline: String,
    sub: String?,
    modifier: Modifier = Modifier,
    headlineFontSize: TextUnit = 30.sp,
    horizontalPadding: Int = 24,
    topPadding: Int = 28,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding.dp, vertical = topPadding.dp),
    ) {
        MicroLabel(label)
        Spacer(Modifier.height(6.dp))
        JournalHeadline(headline, fontSize = headlineFontSize)
        if (sub != null) {
            Spacer(Modifier.height(2.dp))
            JournalSubLine(sub)
        }
        OrnamentRule()
        trailingContent?.invoke()
    }
}
