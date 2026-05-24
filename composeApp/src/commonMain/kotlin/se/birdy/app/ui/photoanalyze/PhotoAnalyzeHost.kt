package se.birdy.app.ui.photoanalyze

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph

@Composable
expect fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
)
