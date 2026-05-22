package se.birdy.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import se.birdy.app.di.AppGraph
import se.birdy.app.ui.scaffold.AppGate
import se.birdy.app.ui.scaffold.BirdySplash
import se.birdy.app.ui.theme.BirdyTheme

private const val SPLASH_MIN_MS = 1800L

@Composable
fun App(graph: AppGraph) {
    LaunchedEffect(Unit) {
        runCatching { graph.badgeBackfill.runIfNeeded() }
            .onFailure { if (it is CancellationException) throw it }
    }
    LaunchedEffect(graph) {
        graph.effectivePremiumActive
            .filter { it }
            .collect {
                runCatching { graph.badgeBackfill.runIfPremiumNewlyActive() }
                    .onFailure { if (it is CancellationException) throw it }
            }
    }
    var splashDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_MS)
        splashDone = true
    }
    BirdyTheme {
        if (splashDone) AppGate(graph) else BirdySplash()
    }
}
