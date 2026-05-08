package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.menu_button
import birdy_bird_scanner.composeapp.generated.resources.search_empty_body
import birdy_bird_scanner.composeapp.generated.resources.search_empty_title
import birdy_bird_scanner.composeapp.generated.resources.search_placeholder
import birdy_bird_scanner.composeapp.generated.resources.settings_menu_item
import birdy_bird_scanner.composeapp.generated.resources.title_encyclopedia
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.SpeciesId

@OptIn(ExperimentalMaterial3Api::class)
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
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.menu_button))
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

            when (val s = state) {
                ArchiveUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(Res.string.loading))
                    }
                }
                ArchiveUiState.Empty -> {
                    EmptyState(
                        title = stringResource(Res.string.search_empty_title),
                        body = stringResource(Res.string.search_empty_body),
                    )
                }
                is ArchiveUiState.Loaded -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.rows, key = { it.summary.id.raw }) { row ->
                            SpeciesRow(row.summary, onClick = { onSpeciesClick(row.summary.id) })
                        }
                    }
                }
            }
        }
    }
}
