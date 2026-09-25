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
 *     [se.birdy.android.MainActivity] skips every premium override at app start —
 *     including a grandfathered user's (spec §5.1) — so the real
 *     `NotActive → purchase → Active` flow is testable. Read once at startup — restart to apply.
 *     Also hosts the grandfather-thanks QA controls (spec §5.2): force this install to look
 *     early, and reset the one-shot "shown" flag so the thank-you screen re-triggers.
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
    grandfatherDebug: GrandfatherDebugControls = GrandfatherDebugControls(),
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Tap 'Run diagnostic' to begin.") }
    val skip by skipPremiumOverride.collectAsState(initial = false)
    val forceGf by grandfatherDebug.forceGrandfathered.collectAsState(initial = false)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BillingVerifySection(
            skip = skip,
            onSetSkip = { value -> scope.launch { onSetSkipPremiumOverride(value) } },
            forceGrandfathered = forceGf,
            onSetForceGrandfathered = { value -> scope.launch { grandfatherDebug.onSetForceGrandfathered(value) } },
            onResetGrandfatherThanks = { scope.launch { grandfatherDebug.onResetGrandfatherThanks() } },
        )

        HorizontalDivider()

        MlDiagnosticSection(
            running = running,
            log = log,
            onRun = {
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
        )
    }
}

@Composable
private fun BillingVerifySection(
    skip: Boolean,
    onSetSkip: (Boolean) -> Unit,
    forceGrandfathered: Boolean,
    onSetForceGrandfathered: (Boolean) -> Unit,
    onResetGrandfatherThanks: () -> Unit,
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
        Switch(checked = skip, onCheckedChange = onSetSkip)
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Simulate early user (restart to apply)", style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = forceGrandfathered, onCheckedChange = onSetForceGrandfathered)
    }
    Button(onClick = onResetGrandfatherThanks) {
        Text("Show thank-you screen again (restart)")
    }
}

@Composable
private fun MlDiagnosticSection(
    running: Boolean,
    log: String,
    onRun: () -> Unit,
) {
    Text("ML preprocessing diagnostic", style = MaterialTheme.typography.headlineMedium)
    Button(enabled = !running, onClick = onRun) {
        Text(if (running) "Running…" else "Run diagnostic")
    }

    HorizontalDivider()
    Text(log, style = MaterialTheme.typography.bodySmall)
}
