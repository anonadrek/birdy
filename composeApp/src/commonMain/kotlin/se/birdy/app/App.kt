package se.birdy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.scaffold.AppGate
import se.birdy.app.ui.theme.BirdyTheme

@Composable
fun App(graph: AppGraph) {
    LaunchedEffect(Unit) {
        runCatching { graph.badgeBackfill.runIfNeeded() }
            .onFailure { if (it is CancellationException) throw it }
    }
    BirdyTheme {
        AppGate(graph)
    }
}
