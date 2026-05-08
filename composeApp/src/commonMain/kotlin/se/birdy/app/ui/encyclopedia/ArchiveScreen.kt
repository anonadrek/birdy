package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.archive_breadcrumb
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_water
import birdy_bird_scanner.composeapp.generated.resources.archive_headline
import birdy_bird_scanner.composeapp.generated.resources.archive_section_count
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_alpha
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_family
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.archive_sub
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.AccentCopperLight
import se.birdy.app.ui.theme.HeroZone
import se.birdy.app.ui.theme.ItalicMixedText
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.content.SpeciesId
import se.birdy.datastore.ArchiveSort

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(containerColor = MossCreme) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroZone {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = stringResource(Res.string.archive_breadcrumb),
                            color = AccentCopperLight,
                            fontSize = 11.sp,
                            letterSpacing = 0.32.em,
                            fontWeight = FontWeight.W600,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(Res.string.archive_headline),
                            color = OffwhiteWarm,
                            fontFamily = FontFamily.Serif,
                            fontStyle = FontStyle.Italic,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.W700,
                        )
                        Spacer(Modifier.height(6.dp))
                        ItalicMixedText(
                            text = stringResource(Res.string.archive_sub),
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    color = OffwhiteWarm.copy(alpha = 0.86f),
                                    fontSize = 14.sp,
                                ),
                            italicAccent = AccentCopperLight,
                        )
                    }
                    IconButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onClick = { menuExpanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.menu_button),
                            tint = OffwhiteWarm,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.settings_menu_item)) },
                            onClick = {
                                onSettingsClick()
                                menuExpanded = false
                            },
                        )
                        if (showDebugMenu) {
                            DropdownMenuItem(
                                text = { Text("Run benchmark") },
                                onClick = {
                                    onDebugBenchmarkClick()
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            )

            ChipBar(selected = chip, onSelect = viewModel::onChipSelected)

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SortChip(sort = sort, onClick = viewModel::onSortToggle)
            }

            when (val s = state) {
                ArchiveUiState.Loading ->
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(Res.string.loading)) }
                ArchiveUiState.Empty ->
                    EmptyState(
                        title = stringResource(Res.string.search_empty_title),
                        body = stringResource(Res.string.search_empty_body),
                    )
                is ArchiveUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text =
                                    stringResource(
                                        Res.string.archive_section_count,
                                        s.rows.size.toString(),
                                    ),
                                color = TextOnCreme.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.W600,
                                letterSpacing = 0.18.em,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(s.rows, key = { it.summary.id.raw }) { row ->
                            SpeciesRow(
                                summary = row.summary,
                                isStamped = row.isStamped,
                                onClick = { onSpeciesClick(row.summary.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipBar(
    selected: ArchiveChip,
    onSelect: (ArchiveChip) -> Unit,
) {
    val labels =
        listOf(
            ArchiveChip.ALL to stringResource(Res.string.archive_chip_all),
            ArchiveChip.SONGBIRDS to stringResource(Res.string.archive_chip_songbirds),
            ArchiveChip.WATER to stringResource(Res.string.archive_chip_water),
            ArchiveChip.RAPTORS to stringResource(Res.string.archive_chip_raptors),
            ArchiveChip.OWLS to stringResource(Res.string.archive_chip_owls),
            ArchiveChip.WADERS to stringResource(Res.string.archive_chip_waders),
        )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (chipValue, label) ->
            FilterChip(
                selected = selected == chipValue,
                onClick = { onSelect(chipValue) },
                label = { Text(label) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentCopper,
                        selectedLabelColor = OffwhiteWarm,
                        containerColor = SandCreme,
                        labelColor = TextOnCreme,
                    ),
            )
        }
    }
}

@Composable
private fun SortChip(
    sort: ArchiveSort,
    onClick: () -> Unit,
) {
    val label =
        when (sort) {
            ArchiveSort.ALPHA -> stringResource(Res.string.archive_sort_alpha)
            ArchiveSort.FAMILY -> stringResource(Res.string.archive_sort_family)
            ArchiveSort.RECENT -> stringResource(Res.string.archive_sort_recent)
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(SandCreme)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Sort,
            contentDescription = null,
            tint = AccentCopper,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, color = TextOnCreme, fontSize = 12.sp, fontWeight = FontWeight.W600)
    }
}
