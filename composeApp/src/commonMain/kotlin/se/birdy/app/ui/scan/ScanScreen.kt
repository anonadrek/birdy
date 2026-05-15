package se.birdy.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.demo_mode_banner
import birdy_bird_scanner.composeapp.generated.resources.demo_sheet_body
import birdy_bird_scanner.composeapp.generated.resources.demo_sheet_close
import birdy_bird_scanner.composeapp.generated.resources.demo_sheet_report
import birdy_bird_scanner.composeapp.generated.resources.demo_sheet_title
import birdy_bird_scanner.composeapp.generated.resources.permission_denied_photo_fallback
import birdy_bird_scanner.composeapp.generated.resources.permission_grant_cta
import birdy_bird_scanner.composeapp.generated.resources.permission_hero_caveat
import birdy_bird_scanner.composeapp.generated.resources.scan_error_classifier_failed
import birdy_bird_scanner.composeapp.generated.resources.scan_freeze_hint
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_denied_body
import birdy_bird_scanner.composeapp.generated.resources.scan_permission_open_settings
import birdy_bird_scanner.composeapp.generated.resources.scan_photo_analyze
import birdy_bird_scanner.composeapp.generated.resources.scan_top1_searching
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalHeadline
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.PaperTop
import se.birdy.app.ui.theme.TextOnCreme
import se.birdy.app.ui.theme.TextOnHero
import se.birdy.app.ui.theme.paperBackground
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.ml.CameraSource
import se.birdy.ml.ClassifierMode

@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    cameraSource: CameraSource,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String, capturedAtMs: Long) -> Unit,
    onPermissionRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    onCaptureJpeg: () -> ByteArray,
    persistFrame: (ByteArray) -> String,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        val s = state
        if (s is ScanUiState.FrozenAt) {
            val csv =
                s.predictions.joinToString(",") { p ->
                    p.speciesId + ":" + (p.confidence * 100).toInt() + "/100"
                }
            onFrozen(csv, s.frameJpegPath, s.timestampMillis)
            viewModel.onResumeAfterFreeze()
        }
    }

    var showDemoSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ScanUiState.PermissionRequired -> PermissionRequiredView(onAllow = onPermissionRequest)
            is ScanUiState.PermissionDenied ->
                PermissionDeniedView(
                    onOpenSettings = onOpenSettings,
                    onAnalyzePhotoInstead = onPhotoAnalyzeClick,
                )
            is ScanUiState.Error -> ErrorView(s.kind)
            else -> {
                CameraPreviewHost(cameraSource = cameraSource, modifier = Modifier.fillMaxSize())
                Crosshair(
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    viewModel.onFreeze(onCaptureJpeg(), persistFrame)
                                }
                            },
                )
                if (s is ScanUiState.Scanning) {
                    val pct = s.top1?.confidence?.let { (it * 100).toInt() }
                    val name = s.top1?.speciesId ?: stringResource(Res.string.scan_top1_searching)
                    TopChip(
                        speciesName = name,
                        confidencePct = pct,
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 60.dp, end = 14.dp),
                    )
                }
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = stringResource(Res.string.scan_freeze_hint), color = TextOnHero)
                    OutlinedButton(onClick = onPhotoAnalyzeClick) {
                        Text(stringResource(Res.string.scan_photo_analyze))
                    }
                }
            }
        }
        // DEMO banner is session-wide truth — show across all states (PermissionRequired,
        // Idle, Scanning, FrozenAt, Error) so the user always knows they are in fallback mode.
        if (viewModel.classifierMode == ClassifierMode.DEMO) {
            DemoBanner(
                onClick = { showDemoSheet = true },
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp),
            )
        }
    }
    if (showDemoSheet) {
        DemoModeBottomSheet(onDismiss = { showDemoSheet = false })
    }
}

@Composable
private fun DemoBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AccentCopper,
        contentColor = TextOnHero,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = stringResource(Res.string.demo_mode_banner),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoModeBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val caveat = rememberCaveat()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PaperTop,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            JournalHeadline(
                text = stringResource(Res.string.demo_sheet_title),
                fontSize = 28.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.demo_sheet_body),
                color = MarginaliaInk,
                fontFamily = caveat,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = AccentCopper,
                            contentColor = OffwhiteWarm,
                        ),
                ) {
                    Text(stringResource(Res.string.demo_sheet_close))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.demo_sheet_report),
                        color = AccentCopper,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRequiredView(onAllow: () -> Unit) {
    val caveat = rememberCaveat()
    Column(
        modifier = Modifier.fillMaxSize().paperBackground().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .border(2.dp, AccentCopper, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = AccentCopper,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.permission_hero_caveat),
            color = MarginaliaInk,
            fontFamily = caveat,
            fontStyle = FontStyle.Italic,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAllow,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentCopper,
                    contentColor = OffwhiteWarm,
                ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(Res.string.permission_grant_cta))
        }
    }
}

@Composable
private fun PermissionDeniedView(
    onOpenSettings: () -> Unit,
    onAnalyzePhotoInstead: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().paperBackground().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.scan_permission_denied_body),
            color = TextOnCreme,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onOpenSettings,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = AccentCopper,
                    contentColor = OffwhiteWarm,
                ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(Res.string.scan_permission_open_settings))
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onAnalyzePhotoInstead,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                text = stringResource(Res.string.permission_denied_photo_fallback),
                color = AccentCopper,
            )
        }
    }
}

@Composable
private fun ErrorView(kind: ScanErrorKind) {
    val message =
        when (kind) {
            ScanErrorKind.ClassifierFailed -> stringResource(Res.string.scan_error_classifier_failed)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = TextOnHero)
    }
}
