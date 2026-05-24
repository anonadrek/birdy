package se.birdy.app.ui.audio

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_analyzing
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cta_idle
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_cta_recording
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_headline
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_label
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_journal_sub
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_marginalia_top
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_mic_cd_analyzing
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_mic_cd_idle
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_mic_cd_recording
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_body
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_grant
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_open_settings
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_permission_title
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_recording_failed
import birdy_bird_scanner.composeapp.generated.resources.audio_scan_retry
import birdy_bird_scanner.composeapp.generated.resources.profile_back
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.components.BackButton
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
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    JournalScaffold { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
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
                        IdleView(onStart = onStartRecording)
                    is AudioScanState.Recording ->
                        RecordingView(state = state, onStop = onStopRecording)
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
}

@Composable
private fun IdleView(onStart: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = 0f, frozen = false)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(
            state = MicButtonState.Idle,
            onClick = onStart,
            contentDescription = stringResource(Res.string.audio_scan_mic_cd_idle),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.audio_scan_cta_idle),
            color = AccentCopper,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun RecordingView(
    state: AudioScanState.Recording,
    onStop: () -> Unit,
) {
    val micState =
        if (state.elapsedMs < AudioScanViewModel.MIN_RECORD_MS) {
            MicButtonState.RecordingDisabled
        } else {
            MicButtonState.Recording
        }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rms, frozen = false)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(
            state = micState,
            onClick = onStop,
            contentDescription = stringResource(Res.string.audio_scan_mic_cd_recording),
        )
        Spacer(Modifier.height(12.dp))
        RecordingTimer(elapsedMs = state.elapsedMs)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.audio_scan_cta_recording),
            color = AccentCopper,
            fontFamily = rememberCaveat(),
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun AnalyzingView(state: AudioScanState.Analyzing) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        WaveformBars(rms = state.rmsFrozen, frozen = true)
        Spacer(Modifier.height(24.dp))
        RecordingMicButton(
            state = MicButtonState.Analyzing,
            onClick = {},
            contentDescription = stringResource(Res.string.audio_scan_mic_cd_analyzing),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.audio_scan_analyzing),
            fontFamily = rememberDmSerifDisplay(),
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun RecordingTimer(elapsedMs: Long) {
    val seconds = (elapsedMs / 1000L).toInt()
    val mm = seconds / 60
    val ss = seconds % 60
    Text(
        text = "$mm:${ss.toString().padStart(2, '0')}",
        fontFamily = rememberCaveat(),
        color = MarginaliaInk,
        fontSize = 14.sp,
    )
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
        Button(onClick = onRetry) { Text(text = stringResource(Res.string.audio_scan_retry)) }
    }
}
