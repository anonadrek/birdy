package se.birdy.app.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import se.birdy.ml.BirdClassifier

@Composable
fun BenchmarkScreen(
    classifier: BirdClassifier,
    modelVersion: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progressText by remember { mutableStateOf("Ready") }
    var lastMs by remember { mutableStateOf<Long?>(null) }
    var result by remember { mutableStateOf<BenchmarkRun?>(null) }
    var savedPath by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Benchmark", style = MaterialTheme.typography.headlineMedium)
        Text("Model: $modelVersion")
        Button(onClick = {
            scope.launch {
                progressText = "Running…"
                result = null
                BenchmarkRunner(context, classifier, modelVersion).run().collect { p ->
                    when (p) {
                        is BenchmarkProgress.Tick -> {
                            progressText = "${p.photo}: ${p.iteration}/${p.total}"
                            lastMs = p.lastMs
                        }
                        is BenchmarkProgress.Done -> {
                            result = p.run
                            savedPath = p.outputPath
                            progressText = "Done"
                        }
                    }
                }
            }
        }) { Text("Run benchmark") }

        Text(progressText)
        lastMs?.let { Text("Last ms: $it") }
        result?.let { run ->
            HorizontalDivider()
            Text("Device: ${run.device}", style = MaterialTheme.typography.labelSmall)
            run.results.forEach { r ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.photoLabel, style = MaterialTheme.typography.titleMedium)
                        Text("n=${r.n}  p50=${r.p50}ms  p90=${r.p90}ms  p95=${r.p95}ms  p99=${r.p99}ms")
                        Text("mean=${"%.1f".format(r.mean)}ms")
                    }
                }
            }
            savedPath?.let { Text("Saved: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
