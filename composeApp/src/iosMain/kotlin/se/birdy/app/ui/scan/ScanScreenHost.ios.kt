package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
