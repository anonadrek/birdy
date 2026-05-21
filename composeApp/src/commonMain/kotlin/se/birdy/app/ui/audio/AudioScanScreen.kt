package se.birdy.app.ui.audio

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_analyzing
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cancel
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cta_hold
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_headline
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_label
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_listening
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_marginalia_top
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_body
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_grant
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_open_settings
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_title
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_recording_failed
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_retry
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.JournalIntro
import se.birdy.app.ui.components.JournalScaffold
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.rememberCaveat
import se.birdy.app.ui.theme.rememberDmSerifDisplay

@Composable
fun AudioScanScreen(
    state: AudioScanState,
    permissionState: PermissionState,
    onStartHold: () -> Unit,
    onReleaseHold: () -> Unit,
    onCancel: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    JournalScaffold { _ ->
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            JournalIntro(
                label = stringResource(Res.string.audio_scan_journal_label),
                headline = stringResource(Res.string.audio_scan_headline),
                sub = stringResource(Res.string.audio_scan_journal_sub),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.audio_scan_marginalia_top),
                fontFamily = rememberCaveat(),
                fontStyle = FontStyle.Italic,
                color = MarginaliaInk,
            )
            Spacer(Modifier.height(32.dp))

            when (state) {
                is AudioScanState.Preparing ->
                    Text("…", fontFamily = rememberDmSerifDisplay(), fontStyle = FontStyle.Italic)
                is AudioScanState.PermissionNeeded ->
                    PermissionPrompt(onClick = onRequestPermission, openSettingsMode = false)
                is AudioScanState.Error.PermanentlyDenied ->
                    PermissionPrompt(onClick = onOpenSettings, openSettingsMode = true)
                is AudioScanState.Idle ->
                    IdleMic(onStartHold = onStartHold, onReleaseHold = onReleaseHold)
                is AudioScanState.Recording ->
                    RecordingView(state = state, onCancel = onCancel)
                is AudioScanState.Analyzing ->
                    AnalyzingView(state = state)
                is AudioScanState.Error.RecordingFailed ->
                    ErrorRetry(
                        message = stringResource(Res.string.audio_scan_recording_failed),
                        onRetry = onRetry,
                    )
                is AudioScanState.Error.BootstrapFailed ->
                    ErrorRetry(message = state.cause, onRetry = onRetry)
                is AudioScanState.NavigateToMatch -> {
                    // handled by host LaunchedEffect
                }
            }
        }
    }
}

@Composable
private fun IdleMic(
    onStartHold: () -> Unit,
    onReleaseHold: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onStartHold()
                                tryAwaitRelease()
                                onReleaseHold()
                            },
                        )
                    },
        ) {
            // 🎙 mic emoji as tap-target placeholder — T7 replaces with an icon
            Text(
                text = "🎙",
                modifier = Modifier.align(Alignment.Center),
                fontFamily = rememberDmSerifDisplay(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.audio_scan_cta_hold),
            color = AccentCopper,
            fontFamily = rememberCaveat(),
        )
    }
}

@Composable
private fun RecordingView(
    state: AudioScanState.Recording,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rms, frozen = false)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.audio_scan_listening),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
        val remaining = (3000 - state.elapsedMs).coerceAtLeast(0) / 1000.0
        Text(
            text = "%.1f".format(remaining) + "s",
            fontFamily = rememberCaveat(),
            color = MarginaliaInk,
        )
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text(
                text = stringResource(Res.string.audio_scan_cancel),
                fontFamily = rememberCaveat(),
            )
        }
    }
}

@Composable
private fun AnalyzingView(state: AudioScanState.Analyzing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rmsFrozen, frozen = true)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.audio_scan_analyzing),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun PermissionPrompt(
    onClick: () -> Unit,
    openSettingsMode: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(Res.string.audio_scan_permission_title),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.audio_scan_permission_body),
            fontFamily = rememberCaveat(),
            color = MarginaliaInk,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onClick) {
            Text(
                text =
                    stringResource(
                        if (openSettingsMode) {
                            Res.string.audio_scan_permission_open_settings
                        } else {
                            Res.string.audio_scan_permission_grant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun ErrorRetry(
    message: String,
    onRetry: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = message, fontFamily = rememberCaveat(), color = MarginaliaInk)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(Res.string.audio_scan_retry))
        }
    }
}
