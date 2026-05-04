package se.birdy.app.ui.encyclopedia

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.SpeciesId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EncyclopediaScreen(
    viewModel: EncyclopediaViewModel,
    onSpeciesClick: (SpeciesId) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("UPPSLAGSVERK", style = MaterialTheme.typography.titleLarge) },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = HeroMossLight,
                        titleContentColor = TextOnHero,
                    ),
            )
        },
    ) { padding ->
        when (val s = state) {
            EncyclopediaUiState.Loading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Laddar…")
                }
            }
            EncyclopediaUiState.Empty -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Inga arter att visa.")
                }
            }
            is EncyclopediaUiState.Loaded -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                ) {
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
                            SectionHeader("Övriga (${s.grouped.others.size})")
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
