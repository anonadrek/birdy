package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.filter_apply
import birdy_bird_scanner.composeapp.generated.resources.filter_label_abundance
import birdy_bird_scanner.composeapp.generated.resources.filter_label_month
import birdy_bird_scanner.composeapp.generated.resources.filter_label_region
import birdy_bird_scanner.composeapp.generated.resources.filter_reset
import birdy_bird_scanner.composeapp.generated.resources.filter_title
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Abundance
import se.birdy.content.SpeciesFilter

private val MONTHS = listOf("jan", "feb", "mar", "apr", "maj", "jun", "jul", "aug", "sep", "okt", "nov", "dec")
private val REGIONS = listOf("SE", "NO", "FI", "DK", "DE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    initial: SpeciesFilter,
    previewCount: Int,
    onApply: (SpeciesFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(Res.string.filter_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(Res.string.filter_label_abundance), style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Abundance.ALLMÄN, Abundance.OVANLIG).forEach { ab ->
                    FilterChip(
                        selected = ab in draft.abundance,
                        onClick = {
                            draft =
                                draft.copy(
                                    abundance =
                                        if (ab in draft.abundance) {
                                            draft.abundance - ab
                                        } else {
                                            draft.abundance + ab
                                        },
                                )
                        },
                        label = { Text(ab.code.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Text(stringResource(Res.string.filter_label_region), style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(REGIONS) { r ->
                    FilterChip(
                        selected = r in draft.regions,
                        onClick = {
                            draft =
                                draft.copy(
                                    regions =
                                        if (r in draft.regions) {
                                            draft.regions - r
                                        } else {
                                            draft.regions + r
                                        },
                                )
                        },
                        label = { Text(r) },
                    )
                }
            }

            Text(stringResource(Res.string.filter_label_month), style = MaterialTheme.typography.labelLarge, color = AccentCopper)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MONTHS) { m ->
                    FilterChip(
                        selected = draft.activeInMonth == m,
                        onClick = {
                            draft = draft.copy(activeInMonth = if (draft.activeInMonth == m) null else m)
                        },
                        label = { Text(m.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { draft = SpeciesFilter() },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(Res.string.filter_reset)) }
                Button(
                    onClick = { onApply(draft) },
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = TextOnHero),
                ) { Text(stringResource(Res.string.filter_apply, previewCount)) }
            }
        }
    }
}
