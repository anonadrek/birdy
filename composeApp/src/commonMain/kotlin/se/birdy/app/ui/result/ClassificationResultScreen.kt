package se.birdy.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_db
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_frame_unavailable
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_photo
import birdy_bird_scanner.composeapp.generated.resources.diary_save_error_storage
import birdy_bird_scanner.composeapp.generated.resources.diary_save_success
import birdy_bird_scanner.composeapp.generated.resources.diary_saved_indicator
import birdy_bird_scanner.composeapp.generated.resources.result_no_matches
import birdy_bird_scanner.composeapp.generated.resources.result_no_predictions
import birdy_bird_scanner.composeapp.generated.resources.result_save_hint
import birdy_bird_scanner.composeapp.generated.resources.result_save_observation
import birdy_bird_scanner.composeapp.generated.resources.result_unresolved_count
import birdy_bird_scanner.composeapp.generated.resources.result_your_scan
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.HeroMossLight
import se.birdy.app.ui.theme.MossCreme
import se.birdy.app.ui.theme.SandCreme
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero

@Composable
fun ClassificationResultScreen(
    viewModel: ClassificationResultViewModel,
    onSpeciesClick: (speciesId: String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Box(modifier = Modifier.fillMaxSize().background(MossCreme)) {
        when (val s = state) {
            ClassificationResultUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = AccentCopper,
                )
            }
            is ClassificationResultUiState.Error -> {
                val msg =
                    when (s.kind) {
                        ClassificationResultUiState.Error.Kind.NoPredictions ->
                            stringResource(Res.string.result_no_predictions)
                        ClassificationResultUiState.Error.Kind.NoMatches ->
                            stringResource(Res.string.result_no_matches)
                    }
                Text(msg, modifier = Modifier.align(Alignment.Center), color = TextOnCreme)
            }
            is ClassificationResultUiState.Loaded ->
                LoadedView(s, onSpeciesClick = onSpeciesClick, onSave = { viewModel.saveToDiary() })
        }
    }
}

@Composable
private fun LoadedView(
    state: ClassificationResultUiState.Loaded,
    onSpeciesClick: (String) -> Unit,
    onSave: () -> Unit,
) {
    val snackbarHost = remember { SnackbarHostState() }
    val successLabel = stringResource(Res.string.diary_save_success)
    val errorPhotoLabel = stringResource(Res.string.diary_save_error_photo)
    val errorStorageLabel = stringResource(Res.string.diary_save_error_storage)
    val errorDbLabel = stringResource(Res.string.diary_save_error_db)
    val errorFrameLabel = stringResource(Res.string.diary_save_error_frame_unavailable)

    LaunchedEffect(state.saveStatus) {
        when (val s = state.saveStatus) {
            ClassificationResultUiState.SaveStatus.Saved -> snackbarHost.showSnackbar(successLabel)
            is ClassificationResultUiState.SaveStatus.Failed ->
                snackbarHost.showSnackbar(
                    when (s.kind) {
                        ClassificationResultUiState.SaveStatus.Failed.Kind.PhotoEncodeFailed -> errorPhotoLabel
                        ClassificationResultUiState.SaveStatus.Failed.Kind.StorageFull -> errorStorageLabel
                        ClassificationResultUiState.SaveStatus.Failed.Kind.DatabaseFailed -> errorDbLabel
                        ClassificationResultUiState.SaveStatus.Failed.Kind.FrameUnavailable -> errorFrameLabel
                    },
                )
            else -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
        ) {
            // Hero top-1
            HeroCard(state.top1, onClick = { onSpeciesClick(state.top1.species.id.raw) })

            // Frozen-frame banner
            if (state.frozenFramePath != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(80.dp)
                                .background(SandCreme, RoundedCornerShape(12.dp)),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(stringResource(Res.string.result_your_scan), color = TextOnCreme)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Top-2/3 row
            if (state.runnerUps.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.runnerUps.forEach { rp ->
                        RunnerUpCard(
                            prediction = rp,
                            modifier = Modifier.weight(1f),
                            onClick = { onSpeciesClick(rp.species.id.raw) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Unresolved pill
            if (state.unresolved.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.result_unresolved_count, state.unresolved.size),
                    color = HeroMossLight,
                    style = MaterialTheme.typography.labelSmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val isSaved = state.saveStatus == ClassificationResultUiState.SaveStatus.Saved
            val isSaving = state.saveStatus == ClassificationResultUiState.SaveStatus.Saving
            Button(
                onClick = { onSave() },
                enabled = !isSaving && !isSaved,
                colors = ButtonDefaults.buttonColors(containerColor = AccentCopper, contentColor = TextOnHero),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (isSaved) {
                        stringResource(Res.string.diary_saved_indicator)
                    } else {
                        stringResource(Res.string.result_save_observation)
                    },
                )
            }
            Text(
                text = stringResource(Res.string.result_save_hint),
                color = HeroMossLight,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HeroCard(
    prediction: ResolvedPrediction,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(SandCreme, RoundedCornerShape(16.dp))
                .padding(16.dp),
    ) {
        Text(
            text = prediction.species.name,
            style = MaterialTheme.typography.headlineSmall,
            color = TextOnCreme,
        )
        Text(
            text = prediction.species.scientificName,
            style = MaterialTheme.typography.bodySmall,
            color = HeroMossLight,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { prediction.confidence },
            modifier = Modifier.fillMaxWidth(),
            color = AccentCopper,
            trackColor = MossCreme,
        )
        Text(
            text = (prediction.confidence * 100).toInt().toString() + "%",
            style = MaterialTheme.typography.labelMedium,
            color = AccentCopper,
        )
    }
}

@Composable
private fun RunnerUpCard(
    prediction: ResolvedPrediction,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .clickable(onClick = onClick)
                .background(SandCreme, RoundedCornerShape(12.dp))
                .padding(12.dp),
    ) {
        Text(
            text = prediction.species.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextOnCreme,
        )
        Text(
            text = (prediction.confidence * 100).toInt().toString() + "%",
            style = MaterialTheme.typography.labelSmall,
            color = AccentCopper,
        )
    }
}
