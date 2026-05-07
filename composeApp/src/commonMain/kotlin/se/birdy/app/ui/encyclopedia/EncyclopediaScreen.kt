package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.filter_button
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.section_others
import birdy_bird_scanner.composeapp.generated.resources.title_encyclopedia
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.SpeciesFilter
import se.birdy.content.SpeciesId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncyclopediaScreen(
    viewModel: EncyclopediaViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
    showDebugMenu: Boolean = false,
    onDebugBenchmarkClick: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val activeFilterCount = filter.activeCount()
    var showSheet by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.title_encyclopedia), style = MaterialTheme.typography.titleLarge) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = HeroMossLight,
                        titleContentColor = TextOnHero,
                    ),
                actions = {
                    if (showDebugMenu) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Debug menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Run benchmark") },
                                onClick = {
                                    onDebugBenchmarkClick()
                                    menuExpanded = false
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.search_placeholder)) },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { showSheet = true }) {
                    Text(stringResource(Res.string.filter_button))
                    if (activeFilterCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentCopper)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                activeFilterCount.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                color = TextOnHero,
                            )
                        }
                    }
                }
            }

            if (showSheet) {
                val previewCount =
                    (state as? EncyclopediaUiState.Loaded)?.let {
                        it.grouped.common.size + it.grouped.others.size
                    } ?: 0
                FilterBottomSheet(
                    initial = filter,
                    previewCount = previewCount,
                    onApply = { newFilter ->
                        viewModel.onFilterChanged(newFilter)
                        showSheet = false
                    },
                    onDismiss = { showSheet = false },
                )
            }

            when (val s = state) {
                EncyclopediaUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.loading))
                    }
                }
                EncyclopediaUiState.Empty -> {
                    EmptyState(
                        title = stringResource(Res.string.search_empty_title),
                        body = stringResource(Res.string.search_empty_body),
                    )
                }
                is EncyclopediaUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        if (s.grouped.common.isNotEmpty()) {
                            stickyHeader {
                                SectionHeader("${s.sectionCommonHeader} (${s.grouped.common.size})")
                            }
                            items(s.grouped.common, key = { it.id.raw }) { sum ->
                                SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                            }
                        }
                        if (s.grouped.others.isNotEmpty()) {
                            stickyHeader {
                                SectionHeader("${stringResource(Res.string.section_others)} (${s.grouped.others.size})")
                            }
                            items(s.grouped.others, key = { it.id.raw }) { sum ->
                                SpeciesRow(sum, onClick = { onSpeciesClick(sum.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(SandCreme)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

private fun SpeciesFilter.activeCount(): Int {
    var n = 0
    if (abundance.isNotEmpty()) n++
    if (regions.isNotEmpty()) n++
    if (activeInMonth != null) n++
    return n
}
