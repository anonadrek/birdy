package se.birdy.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.badge_common
import birdy_bird_scanner.composeapp.generated.resources.badge_uncommon
import birdy_bird_scanner.composeapp.generated.resources.empty_description
import birdy_bird_scanner.composeapp.generated.resources.empty_migration
import birdy_bird_scanner.composeapp.generated.resources.empty_photos
import birdy_bird_scanner.composeapp.generated.resources.loading
import birdy_bird_scanner.composeapp.generated.resources.not_found_body
import birdy_bird_scanner.composeapp.generated.resources.not_found_title
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import birdy_bird_scanner.composeapp.generated.resources.profile_label_description
import birdy_bird_scanner.composeapp.generated.resources.profile_label_migration
import birdy_bird_scanner.composeapp.generated.resources.profile_label_photos
import coil3.compose.AsyncImage
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.EmptyState
import se.birdy.app.ui.components.HeroImage
import se.birdy.app.ui.components.SectionBlock
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.content.Abundance
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(Res.string.loading)) }
        SpeciesProfileUiState.NotFound ->
            EmptyState(
                title = stringResource(Res.string.not_found_title),
                body = stringResource(Res.string.not_found_body),
            )
        is SpeciesProfileUiState.Loaded -> ProfileContent(s.species, onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
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
            val heroImage = species.images.firstOrNull { it.role == "hero" } ?: species.images.firstOrNull()
            Box {
                if (heroImage != null) {
                    AsyncImage(
                        model = Res.getUri("files/images/${heroImage.path}"),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                Color.Black.copy(alpha = 0.25f),
                                                Color.Black.copy(alpha = 0.65f),
                                            ),
                                    ),
                                ),
                    )
                }
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
                                contentDescription = stringResource(Res.string.profile_back),
                                tint = TextOnHero,
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = if (heroImage == null) HeroMossLight else Color.Transparent,
                            scrolledContainerColor = if (heroImage == null) HeroMossLight else Color.Transparent,
                            navigationIconContentColor = TextOnHero,
                            titleContentColor = TextOnHero,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
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
                    label = stringResource(Res.string.profile_label_description),
                    isEmpty = species.description.isNullOrBlank(),
                    emptyMessage = stringResource(Res.string.empty_description),
                ) {
                    Text(species.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionBlock(
                    label = stringResource(Res.string.profile_label_migration),
                    isEmpty = species.migration.isNullOrBlank(),
                    emptyMessage = stringResource(Res.string.empty_migration),
                ) {
                    Text(species.migration.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                SectionBlock(
                    label = stringResource(Res.string.profile_label_photos),
                    isEmpty = species.images.isEmpty(),
                    emptyMessage = stringResource(Res.string.empty_photos),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for (img in species.images.take(3)) {
                            HeroImage(
                                imagePath = img.path,
                                modifier = Modifier.weight(1f).height(64.dp),
                                cornerRadius = 8.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FactRow(species: Species) {
    val abundanceLabel =
        when (species.abundance) {
            Abundance.ALLMÄN -> stringResource(Res.string.badge_common)
            else -> stringResource(Res.string.badge_uncommon)
        }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(abundanceLabel, accent = true)
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
