package se.birdy.app.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_confidence_format
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_button
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_confirm_body
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_confirm_no
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_confirm_title
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_confirm_yes
import birdy_bird_scanner.composeapp.generated.resources.diary_delete_failed
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_back
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_label_confidence
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_label_date
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_label_details
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_label_saved
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_load_error
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_not_found
import birdy_bird_scanner.composeapp.generated.resources.diary_detail_unknown_species
import birdy_bird_scanner.composeapp.generated.resources.diary_note_label
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save_error
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save_success
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObservationDetailScreen(
    viewModel: ObservationDetailViewModel,
    onBack: () -> Unit,
    onSpeciesClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val noteSavedLabel = stringResource(Res.string.diary_note_save_success)
    val noteErrorLabel = stringResource(Res.string.diary_note_save_error)
    val deleteFailedLabel = stringResource(Res.string.diary_delete_failed)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DetailEffect.NoteSaved -> {
                    val msg = if (effect.success) noteSavedLabel else noteErrorLabel
                    scope.launch { snackbarHost.showSnackbar(msg) }
                }
                DetailEffect.Deleted -> onBack()
                DetailEffect.DeleteFailed -> {
                    scope.launch { snackbarHost.showSnackbar(deleteFailedLabel) }
                }
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(MossCreme)) {
            when (val s = state) {
                ObservationDetailUiState.Loading ->
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = AccentCopper,
                    )
                ObservationDetailUiState.NotFound ->
                    Text(
                        stringResource(Res.string.diary_detail_not_found),
                        modifier = Modifier.align(Alignment.Center),
                        color = TextOnCreme,
                        textAlign = TextAlign.Center,
                    )
                is ObservationDetailUiState.Error ->
                    Text(
                        stringResource(Res.string.diary_detail_load_error),
                        modifier = Modifier.align(Alignment.Center),
                        color = TextOnCreme,
                    )
                is ObservationDetailUiState.Loaded ->
                    LoadedView(s, viewModel, onBack, onSpeciesClick)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadedView(
    state: ObservationDetailUiState.Loaded,
    viewModel: ObservationDetailViewModel,
    onBack: () -> Unit,
    onSpeciesClick: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var noteText by remember(state.observation.id) { mutableStateOf(state.observation.note) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val speciesName = state.species?.name ?: stringResource(Res.string.diary_detail_unknown_species)
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Box {
                AsyncImage(
                    model = "file://${state.observation.photoPath}",
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
                                    listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Black.copy(alpha = 0.65f),
                                    ),
                                ),
                            ),
                )
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(speciesName, style = MaterialTheme.typography.headlineMedium, color = TextOnHero)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(Res.string.diary_detail_back),
                                tint = TextOnHero,
                            )
                        }
                    },
                    actions = {
                        val canSave = noteText != state.observation.note
                        IconButton(
                            onClick = { viewModel.saveNote(noteText) },
                            enabled = canSave && !state.noteSaving,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = stringResource(Res.string.diary_note_save),
                                tint = if (canSave && !state.noteSaving) TextOnHero else TextOnHero.copy(alpha = 0.4f),
                            )
                        }
                    },
                    colors =
                        TopAppBarDefaults.largeTopAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            navigationIconContentColor = TextOnHero,
                            titleContentColor = TextOnHero,
                            actionIconContentColor = TextOnHero,
                        ),
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NoteSection(noteText = noteText, onNoteChange = { noteText = it }) }
            item { DetailsSection(state = state, onSpeciesClick = onSpeciesClick) }
            item {
                Spacer(modifier = Modifier.size(8.dp))
                HorizontalDivider(color = SandCreme)
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = { showDeleteDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = TextOnHero),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.diary_delete_button))
                }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.diary_delete_confirm_title)) },
            text = { Text(stringResource(Res.string.diary_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text(stringResource(Res.string.diary_delete_confirm_yes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.diary_delete_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun NoteSection(
    noteText: String,
    onNoteChange: (String) -> Unit,
) {
    Column {
        Text(
            stringResource(Res.string.diary_note_label),
            style = MaterialTheme.typography.labelLarge,
            color = HeroMossLight,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(4.dp))
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
        )
    }
}

@Composable
private fun DetailsSection(
    state: ObservationDetailUiState.Loaded,
    onSpeciesClick: (String) -> Unit,
) {
    val tz = TimeZone.currentSystemDefault()
    Column {
        Text(
            stringResource(Res.string.diary_detail_label_details),
            style = MaterialTheme.typography.labelLarge,
            color = HeroMossLight,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(8.dp))
        DetailRow(label = stringResource(Res.string.diary_detail_label_date)) {
            Text(formatFullDate(state.observation.capturedAt, tz), color = TextOnCreme)
        }
        DetailRow(label = stringResource(Res.string.diary_detail_label_confidence)) {
            Text(
                stringResource(Res.string.diary_confidence_format, (state.observation.confidence * 100f).toInt()),
                color = TextOnCreme,
            )
        }
        DetailRow(label = stringResource(Res.string.diary_detail_label_saved)) {
            Text(formatFullDate(state.observation.savedAt, tz), color = TextOnCreme)
        }
        if (state.species != null) {
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(
                onClick = { onSpeciesClick(state.observation.speciesId) },
            ) {
                Text(state.species.name + " — " + state.species.scientificName, color = AccentCopper)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HeroMossLight)
        content()
    }
}

@Composable
private fun formatFullDate(
    instant: Instant,
    tz: TimeZone,
): String {
    val ldt = instant.toLocalDateTime(tz)
    val month = monthShortLower(ldt.monthNumber)
    val hh = ldt.hour.toString().padStart(2, '0')
    val mm = ldt.minute.toString().padStart(2, '0')
    return "${ldt.dayOfMonth} $month ${ldt.year}, $hh:$mm"
}
