package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph

/**
 * KONTRAKT för varje plattforms-actual (iOS i3 inkluderad): hosten MÅSTE
 * stoppa inspelningen när skärmen lämnar kompositionen (DisposableEffect →
 * `vm.cancelRecording()`), annars läcker mikrofonen upp till 60s.
 */
@Composable
expect fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
)
