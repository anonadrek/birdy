package se.birdy.app.ui.photoanalyze

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.photo_analyzing
import birdy_bird_scanner.composeapp.generated.resources.photo_error_classifier
import birdy_bird_scanner.composeapp.generated.resources.photo_error_decode
import birdy_bird_scanner.composeapp.generated.resources.photo_error_io
import birdy_bird_scanner.composeapp.generated.resources.photo_error_too_small
import birdy_bird_scanner.composeapp.generated.resources.photo_loaded_navigating
import birdy_bird_scanner.composeapp.generated.resources.photo_pick_from_gallery
import birdy_bird_scanner.composeapp.generated.resources.photo_retry
import birdy_bird_scanner.composeapp.generated.resources.photo_take_photo
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.ml.Classification
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ScanSource
import se.birdy.ml.ScanSourceSerialization
import se.birdy.ml.toSerial

@Composable
fun PhotoAnalyzeScreen(
    viewModel: PhotoAnalyzeViewModel,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state) {
        val s = state
        if (s is PhotoAnalyzeUiState.Loaded) {
            val classification =
                Classification(
                    results = s.predictions.map { ClassificationResult(it.speciesId, it.confidence) },
                    frameTimestampMillis = s.capturedAtMs,
                )
            val scanSource =
                ScanSource.Image(
                    frameJpegPath = s.frameJpegPath,
                    classification = classification,
                    origin = s.origin,
                    exifLatitude = s.exifLatitude,
                    exifLongitude = s.exifLongitude,
                )
            val sourceJson =
                Json.encodeToString(ScanSourceSerialization.serializer(), scanSource.toSerial())
            onLoaded(sourceJson, s.capturedAtMs)
            viewModel.reset()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                PhotoAnalyzeUiState.Idle -> {
                    Button(
                        onClick = onPickFromGallery,
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AccentCopper,
                                contentColor = OffwhiteWarm,
                            ),
                    ) { Text(stringResource(Res.string.photo_pick_from_gallery)) }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onTakePhoto,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentCopper,
                            ),
                        border = BorderStroke(1.dp, AccentCopper),
                    ) { Text(stringResource(Res.string.photo_take_photo)) }
                }
                PhotoAnalyzeUiState.Analyzing -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(Res.string.photo_analyzing))
                }
                is PhotoAnalyzeUiState.Loaded -> {
                    Text(stringResource(Res.string.photo_loaded_navigating))
                }
                is PhotoAnalyzeUiState.Error -> {
                    val msg =
                        when (s.kind) {
                            PhotoAnalyzeUiState.Error.Kind.TooSmall ->
                                stringResource(Res.string.photo_error_too_small)
                            PhotoAnalyzeUiState.Error.Kind.DecodeFailure ->
                                stringResource(Res.string.photo_error_decode)
                            PhotoAnalyzeUiState.Error.Kind.ClassifierFailure ->
                                stringResource(Res.string.photo_error_classifier)
                            PhotoAnalyzeUiState.Error.Kind.IoFailure ->
                                stringResource(Res.string.photo_error_io)
                        }
                    Text(msg)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.reset() },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = AccentCopper,
                                contentColor = OffwhiteWarm,
                            ),
                    ) { Text(stringResource(Res.string.photo_retry)) }
                }
            }
        }
        BackButton(
            onClick = onBack,
            contentDescription = stringResource(Res.string.profile_back),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 12.dp, top = 8.dp),
        )
    }
}
