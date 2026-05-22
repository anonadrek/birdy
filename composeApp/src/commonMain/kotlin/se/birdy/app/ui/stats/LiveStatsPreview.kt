package se.birdy.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.stats_open_link
import birdy_bird_scanner.composeapp.generated.resources.stats_section_top
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.stats.charts.JournalBarChart
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

/**
 * Plan 6b3 T12: replaces [LockedStatsPreview] when premium is active. Shows a
 * mini bar-chart of the current calendar year + a species-count chip and a
 * tappable "Open stats →" link that navigates to [SeasonStatsScreen].
 */
@Composable
fun LiveStatsPreview(
    state: SeasonStatsUiState.Loaded,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SandCreme)
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.totalSpeciesThisYear.toString(),
                fontFamily = rememberDmSerifDisplay(),
                fontStyle = FontStyle.Italic,
                fontSize = 26.sp,
                color = AccentCopper,
            )
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text(
                text = stringResource(Res.string.stats_section_top),
                fontFamily = rememberCaveat(),
                fontSize = 14.sp,
                color = MarginaliaInk,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.stats_open_link),
                fontFamily = rememberCaveat(),
                fontSize = 14.sp,
                color = AccentCopper,
                fontWeight = FontWeight.W600,
            )
        }
        Spacer(Modifier.height(8.dp))
        JournalBarChart(
            bars = state.monthBars,
            modifier = Modifier.fillMaxWidth(),
            height = 70.dp,
        )
        val currentLabel = state.monthBars.firstOrNull { it.isCurrent }?.label
        if (currentLabel != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = currentLabel,
                fontFamily = rememberCaveat(),
                fontSize = 12.sp,
                color = TextOnCreme,
            )
        }
    }
}
