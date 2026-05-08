package se.birdy.app.debug

import android.content.Intent
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
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import se.birdy.ml.BirdClassifier
import java.io.File

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
    var running by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Benchmark", style = MaterialTheme.typography.headlineMedium)
        Text("Model: $modelVersion")
        Button(
            enabled = !running,
            onClick = {
                scope.launch {
                    running = true
                    progressText = "Running…"
                    result = null
                    try {
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
                    } finally {
                        running = false
                    }
                }
            },
        ) { Text("Run benchmark") }

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
            savedPath?.let { path ->
                Text("Saved: $path", style = MaterialTheme.typography.bodySmall)
                Button(
                    onClick = {
                        val file = File(path)
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".fileprovider",
                                file,
                            )
                        val send =
                            Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Birdy benchmark $modelVersion")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(Intent.createChooser(send, "Share benchmark JSON"))
                    },
                ) { Text("Share JSON") }
            }
        }
    }
}
