package se.birdy.app.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * DEBUG-only ML preprocessing diagnostics screen (Plan 6b1 T2).
 *
 * Runs three corpus images through the live BirdClassifier + ImagePreprocessor
 * pipeline and prints top-5 predictions plus sampled ARGB pixel values from
 * each decoded Bitmap. Output is intended to be compared against the desktop
 * eval run (tools/ml-eval/) to root-cause why field-photo top-1 hits ~10%
 * while corpus top-3 holds 72%.
 *
 * Wired via [se.birdy.app.di.AppGraph.diagnosticsScreen] — null in release
 * builds. Routed from Archive overflow menu when DEBUG.
 */
@Composable
fun DiagnosticsScreen(runDiagnostic: suspend () -> String) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Tap 'Run diagnostic' to begin.") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
