package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
