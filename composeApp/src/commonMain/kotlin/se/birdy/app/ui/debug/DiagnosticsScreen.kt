package se.birdy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * DEBUG-only diagnostics screen.
 *
 * Hosts two debug affordances:
 *  1. **Billing verify (runbook §1):** a "Skip premium override" toggle. When on,
 *     [se.birdy.android.MainActivity] skips the premium override at app start so the
 *     real `NotActive → purchase → Active` flow is testable even while
 *     `PREMIUM_OPEN_FOR_LAUNCH=true`. Read once at startup — restart to apply.
 *  2. ML preprocessing diagnostic (Plan 6b1 T2) — runs corpus images through the
 *     live BirdClassifier + ImagePreprocessor pipeline and prints predictions +
 *     sampled ARGB pixels, for comparison against the desktop eval (tools/ml-eval/).
 *
 * Wired via [se.birdy.app.di.AppGraph.diagnosticsScreen] — null in release builds.
 * Routed from the Archive overflow menu when DEBUG. The whole screen (and therefore
 * the toggle) never exists in a release build.
 */
@Composable
fun DiagnosticsScreen(
    runDiagnostic: suspend () -> String,
    skipPremiumOverride: Flow<Boolean> = flowOf(false),
    onSetSkipPremiumOverride: suspend (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Tap 'Run diagnostic' to begin.") }
    val skip by skipPremiumOverride.collectAsState(initial = false)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Billing verify", style = MaterialTheme.typography.headlineMedium)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Skip premium override", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Forces NotActive so the purchase flow is testable. " +
                        "Restart the app to apply.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = skip,
                onCheckedChange = { value -> scope.launch { onSetSkipPremiumOverride(value) } },
            )
        }

        HorizontalDivider()

        Text("ML preprocessing diagnostic", style = MaterialTheme.typography.headlineMedium)
        Button(
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    log = "Running…"
                    try {
                        log = runDiagnostic()
                    } catch (t: Throwable) {
                        log = "ERROR: ${t.message}\n\n${t.stackTraceToString()}"
                    } finally {
                        running = false
                    }
                }
            },
        ) { Text(if (running) "Running…" else "Run diagnostic") }

        HorizontalDivider()
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}
