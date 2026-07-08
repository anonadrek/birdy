package se.birdy.app.ui.photoanalyze

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.components.IosComingSoonPanel

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) = IosComingSoonPanel(onBack = onBack)
