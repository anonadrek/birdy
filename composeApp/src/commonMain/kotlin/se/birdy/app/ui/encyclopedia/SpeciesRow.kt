package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_common
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.HeroImage
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Abundance
import se.birdy.content.model.SpeciesSummary

@Composable
fun SpeciesRow(
    summary: SpeciesSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HeroImage(
            imagePath = summary.heroImagePath,
            modifier = Modifier.size(36.dp),
            cornerRadius = 8.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary.scientificName,
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        if (summary.abundance == Abundance.ALLMÄN) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentCopper)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    stringResource(Res.string.badge_common),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextOnHero,
                )
            }
        }
    }
}
