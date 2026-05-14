package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_all
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_owls
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_raptors
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_songbirds
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_waders
import birdy_bird_scanner.composeapp.generated.resources.archive_chip_water
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_label
import birdy_bird_scanner.composeapp.generated.resources.archive_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.archive_section_count
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_alpha
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_family
import birdy_bird_scanner.composeapp.generated.resources.archive_sort_recent
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.premium_archive_subtitle
import birdy_bird_scanner.composeapp.generated.resources.premium_archive_title
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_corner
import birdy_bird_scanner.composeapp.generated.resources.premium_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.components.MiniStamp
import se.birdy.app.ui.components.PremiumTeaserCard
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperBottom
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay
import se.birdy.content.SpeciesId
import se.birdy.datastore.ArchiveSort

@Composable
fun ArchiveScreen(
    viewModel: ArchiveViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
    onPremiumClick: () -> Unit,
    showPremiumTeaser: Boolean = true,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val chip by viewModel.chip.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    var menuExpanded by remember { mutableStateOf(false) }

    JournalScaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") {
                Box(modifier = Modifier.fillMaxWidth()) {
                    JournalIntro(
                        label = stringResource(Res.string.archive_journal_label),
                        headline = stringResource(Res.string.archive_journal_headline),
                        sub = stringResource(Res.string.archive_journal_sub),
                        headlineFontSize = 36.sp,
                    )
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 16.dp)) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.menu_button),
                                tint = AccentCopper,
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
            }

            if (showPremiumTeaser) {
                item(key = "premium") {
                    PremiumTeaserCard(
                        title = stringResource(Res.string.premium_archive_title),
                        subtitle = stringResource(Res.string.premium_archive_subtitle),
                        cornerLabel = stringResource(Res.string.premium_teaser_corner),
                        ctaLabel = stringResource(Res.string.premium_teaser_cta),
                        onClick = onPremiumClick,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item(key = "search") {
                JournalSearchField(
                    value = query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = stringResource(Res.string.search_placeholder),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            item(key = "chips") {
                ChipBar(selected = chip, onSelect = viewModel::onChipSelected)
            }

            item(key = "sort") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortChip(sort = sort, onClick = viewModel::onSortToggle)
                }
            }

            when (val s = state) {
                ArchiveUiState.Loading ->
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                            Text(stringResource(Res.string.loading))
                        }
                    }
                ArchiveUiState.Empty ->
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(Res.string.search_empty_title),
                            body = stringResource(Res.string.search_empty_body),
                        )
                    }
                is ArchiveUiState.Loaded -> {
                    item(key = "count") {
                        Text(
                            text = stringResource(Res.string.archive_section_count, s.rows.size.toString()),
                            color = MarginaliaInk.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.W700,
                            letterSpacing = 0.22.em,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    items(s.rows, key = { it.summary.id.raw }) { row ->
                        SpeciesRow(
                            summary = row.summary,
                            isStamped = row.isStamped,
                            stampNumber = row.stampNumber,
                            onClick = { onSpeciesClick(row.summary.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val caveat = rememberCaveat()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        placeholder = {
            Text(
                text = placeholder,
                color = MarginaliaInk.copy(alpha = 0.5f),
                fontFamily = caveat,
                fontSize = 14.sp,
            )
        },
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.4f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                focusedIndicatorColor = AccentCopper,
                unfocusedIndicatorColor = AccentCopper.copy(alpha = 0.3f),
            ),
    )
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
    val serif = rememberDmSerifDisplay()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(labels) { (chipValue, label) ->
            val isSelected = selected == chipValue
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) AccentCopper else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = AccentCopper.copy(alpha = if (isSelected) 0f else 0.5f),
                            shape = RoundedCornerShape(50),
                        ).clickable { onSelect(chipValue) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) OffwhiteWarm else TextOnCreme,
                    fontFamily = serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = 11.sp,
                )
            }
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
    val serif = rememberDmSerifDisplay()
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(50))
                .background(PaperBottom.copy(alpha = 0.6f))
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
        Text(label, color = TextOnCreme, fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 11.sp)
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
private fun SpeciesRow(
    summary: se.birdy.content.model.SpeciesSummary,
    isStamped: Boolean,
    stampNumber: Int?,
    onClick: () -> Unit,
) {
    val serif = rememberDmSerifDisplay()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumbModifier =
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MarginaliaInk.copy(alpha = 0.1f))
        if (summary.heroImagePath != null) {
            AsyncImage(
                model = Res.getUri("files/images/${summary.heroImagePath}"),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = thumbModifier,
            )
        } else {
            Box(modifier = thumbModifier)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = summary.name,
                color = TextOnCreme,
                fontFamily = serif,
                fontStyle = FontStyle.Italic,
                fontSize = 16.sp,
            )
            Text(
                text = summary.scientificName,
                color = MarginaliaInk.copy(alpha = 0.65f),
                fontStyle = FontStyle.Italic,
                fontSize = 11.sp,
            )
        }
        if (isStamped && stampNumber != null) {
            MiniStamp(number = stampNumber)
        }
    }
}
