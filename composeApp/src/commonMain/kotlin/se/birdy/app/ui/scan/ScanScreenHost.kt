package se.birdy.app.ui.scan

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph

@Composable
expect fun ScanScreenHost(
    graph: AppGraph,
    onPhotoAnalyzeClick: () -> Unit,
    onFrozen: (predictionsCsv: String, frameJpegPath: String) -> Unit,
)
