package se.birdy.app.ui.diary

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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
import birdy_bird_scanner.composeapp.generated.resources.diary_full_date_format
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_apr
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_aug
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_dec
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_feb
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jan
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jul
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_jun
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_mar
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_may
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_nov
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_oct
import birdy_bird_scanner.composeapp.generated.resources.diary_month_short_sep
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save_error
import birdy_bird_scanner.composeapp.generated.resources.diary_note_save_success
import birdy_bird_scanner.composeapp.generated.resources.observation_journal_label
import birdy_bird_scanner.composeapp.generated.resources.observation_note_caveat_prompt
import birdy_bird_scanner.composeapp.generated.resources.observation_visit_profile
import birdy_bird_scanner.composeapp.generated.resources.profile_journal_headline
import birdy_bird_scanner.composeapp.generated.resources.profile_plate_caption
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.PlateFrame
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaBorder
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat

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
        Box(modifier = Modifier.fillMaxSize().padding(padding).paperBackground()) {
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

@Composable
private fun LoadedView(
    state: ObservationDetailUiState.Loaded,
    viewModel: ObservationDetailViewModel,
    onBack: () -> Unit,
    onSpeciesClick: (String) -> Unit,
) {
    var noteText by remember(state.observation.id) { mutableStateOf(state.observation.note) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val unknownLabel = stringResource(Res.string.diary_detail_unknown_species)
    val speciesName = state.species?.name ?: unknownLabel
    val tz = TimeZone.currentSystemDefault()
    val capturedSub = formatFullDate(state.observation.capturedAt, tz)
    val plateLabel = state.observation.stampNumber.toString()
    val captionPart = stringResource(Res.string.profile_plate_caption)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box {
                JournalIntro(
                    label = stringResource(Res.string.observation_journal_label, plateLabel),
                    headline = stringResource(Res.string.profile_journal_headline, speciesName),
                    sub = capturedSub,
                    headlineFontSize = 32.sp,
                )
                IconButton(
                    onClick = onBack,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 24.dp, start = 12.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.diary_detail_back),
                        tint = AccentCopper,
                    )
                }
            }
        }

        item {
            PlateFrame(
                plateLabel = plateLabel,
                captionLine = "$speciesName, $captionPart",
            ) {
                AsyncImage(
                    model = "file://${state.observation.photoPath}",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        item {
            NoteSection(
                noteText = noteText,
                onNoteChange = { noteText = it },
                canSave = noteText != state.observation.note && !state.noteSaving,
                onSave = { viewModel.saveNote(noteText) },
            )
        }

        item {
            DetailsSection(state = state)
        }

        if (state.species != null) {
            item {
                ProfileLink(
                    scientificName = state.species.scientificName,
                    onClick = { onSpeciesClick(state.observation.speciesId) },
                )
            }
        }

        item {
            Spacer(modifier = Modifier.size(8.dp))
            HorizontalDivider(
                color = AccentCopper.copy(alpha = 0.3f),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                Text(
                    stringResource(Res.string.diary_delete_button),
                    color = AccentCopper,
                    fontStyle = FontStyle.Italic,
                )
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
                }) { Text(stringResource(Res.string.diary_delete_confirm_yes), color = AccentCopper) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.diary_delete_confirm_no), color = TextOnCreme)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteSection(
    noteText: String,
    onNoteChange: (String) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    val caveat = rememberCaveat()
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        OutlinedTextField(
            value = noteText,
            onValueChange = onNoteChange,
            placeholder = {
                Text(
                    stringResource(Res.string.observation_note_caveat_prompt),
                    fontFamily = caveat,
                    fontSize = 16.sp,
                    color = MarginaliaInk.copy(alpha = 0.6f),
                )
            },
            textStyle =
                MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = caveat,
                    fontSize = 16.sp,
                    color = MarginaliaInk,
                ),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 6,
            shape = RoundedCornerShape(8.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCopper.copy(alpha = 0.6f),
                    unfocusedBorderColor = AccentCopper.copy(alpha = 0.3f),
                    focusedContainerColor = Color.White.copy(alpha = 0.4f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.4f),
                ),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
        )
        if (canSave) {
            Spacer(modifier = Modifier.size(6.dp))
            TextButton(
                onClick = onSave,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    stringResource(Res.string.diary_note_save),
                    color = AccentCopper,
                    fontFamily = caveat,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun DetailsSection(state: ObservationDetailUiState.Loaded) {
    val tz = TimeZone.currentSystemDefault()
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        SectionLabel(stringResource(Res.string.diary_detail_label_details))
        Spacer(modifier = Modifier.size(6.dp))
        DetailRow(
            label = stringResource(Res.string.diary_detail_label_date),
            value = formatFullDate(state.observation.capturedAt, tz),
        )
        DetailRow(
            label = stringResource(Res.string.diary_detail_label_confidence),
            value =
                stringResource(
                    Res.string.diary_confidence_format,
                    "${(state.observation.confidence * 100f).toInt()}%",
                ),
        )
        DetailRow(
            label = stringResource(Res.string.diary_detail_label_saved),
            value = formatFullDate(state.observation.savedAt, tz),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = MarginaliaInk,
        fontSize = 9.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.22.em,
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MarginaliaInk,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = TextOnCreme,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun ProfileLink(
    scientificName: String,
    onClick: () -> Unit,
) {
    val caveat = rememberCaveat()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(MarginaliaBorder),
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scientificName,
                color = MarginaliaInk,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
            )
            TextButton(
                onClick = onClick,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    text = stringResource(Res.string.observation_visit_profile),
                    color = AccentCopper,
                    fontFamily = caveat,
                    fontSize = 16.sp,
                )
            }
        }
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
    return stringResource(
        Res.string.diary_full_date_format,
        ldt.dayOfMonth,
        month,
        ldt.year,
        "$hh:$mm",
    )
}

@Composable
private fun monthShortLower(month1Based: Int): String =
    stringResource(
        when (month1Based) {
            1 -> Res.string.diary_month_short_jan
            2 -> Res.string.diary_month_short_feb
            3 -> Res.string.diary_month_short_mar
            4 -> Res.string.diary_month_short_apr
            5 -> Res.string.diary_month_short_may
            6 -> Res.string.diary_month_short_jun
            7 -> Res.string.diary_month_short_jul
            8 -> Res.string.diary_month_short_aug
            9 -> Res.string.diary_month_short_sep
            10 -> Res.string.diary_month_short_oct
            11 -> Res.string.diary_month_short_nov
            12 -> Res.string.diary_month_short_dec
            else -> error("Invalid month $month1Based")
        },
    )
