package se.birdy.app.ui.audio

import androidx.compose.runtime.Composable
import se.birdy.app.di.AppGraph

@Composable
expect fun AudioScanScreenHost(
    graph: AppGraph,
    onNavigateToMatch: (String) -> Unit,
    onBack: () -> Unit,
)
