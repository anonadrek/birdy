package se.birdy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.SectionBlock
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.model.Species

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeciesProfileScreen(
    viewModel: SpeciesProfileViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        SpeciesProfileUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Laddar…") }
        SpeciesProfileUiState.NotFound ->
            EmptyState(title = "Art saknas.", body = "Tryck tillbaka för att gå till listan.")
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    species: Species,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            species.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextOnHero,
                        )
                        Text(
                            species.scientificName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            color = TextOnHero.copy(alpha = 0.85f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Tillbaka",
                            tint = TextOnHero,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = HeroMossLight,
                        scrolledContainerColor = HeroMossLight,
                        navigationIconContentColor = TextOnHero,
                        titleContentColor = TextOnHero,
                    ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FactRow(species) }
            item {
                SectionBlock(
                    label = "BESKRIVNING",
                    isEmpty = species.description.isNullOrBlank(),
                    emptyMessage = "Beskrivning kommer i en framtida uppdatering.",
                ) {
                    Text(species.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionBlock(
                    label = "FLYTTNING",
                    isEmpty = species.migration.isNullOrBlank(),
                    emptyMessage = "Migrationsdata saknas för denna art.",
                ) {
                    Text(species.migration.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionBlock(
                    label = "FOTOGRAFIER",
                    isEmpty = species.images.isEmpty(),
                    emptyMessage = "Inga foton tillgängliga.",
                ) {
                    Text("${species.images.size} foton (Coil i Task 9).")
                }
            }
        }
    }
}

@Composable
private fun FactRow(species: Species) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(species.abundance.code.uppercase(), accent = true)
        species.taxonomy.familySv?.let { Chip(it) }
        Chip(species.iucnStatus)
    }
}

@Composable
private fun Chip(
    text: String,
    accent: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (accent) AccentCopper else SandCreme)
                .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (accent) TextOnHero else MaterialTheme.colorScheme.onBackground,
        )
    }
}
