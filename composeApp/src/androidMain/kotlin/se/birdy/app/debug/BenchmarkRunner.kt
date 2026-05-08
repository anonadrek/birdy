package se.birdy.app.debug

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import se.birdy.ml.BirdClassifier
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.File
import kotlin.system.measureTimeMillis

private val benchmarkJson = Json { prettyPrint = true }

@Serializable
data class BenchmarkResult(
    val photoLabel: String,
    val n: Int,
    val p50: Long,
    val p90: Long,
    val p95: Long,
    val p99: Long,
    val mean: Double,
    val timestampMillis: Long,
)

@Serializable
data class BenchmarkRun(
    val modelVersion: String,
    val device: String,
    val results: List<BenchmarkResult>,
)

class BenchmarkRunner(
    private val context: Context,
    private val classifier: BirdClassifier,
    private val modelVersion: String,
    private val photos: List<String> = listOf("talgoxe.jpg", "koltrast.jpg", "blames.jpg"),
    private val iterationsPerPhoto: Int = 100,
    private val warmupIterations: Int = 5,
) {
    fun run(): Flow<BenchmarkProgress> =
        flow {
            val results = mutableListOf<BenchmarkResult>()
            for (photo in photos) {
                val bytes = context.assets.open("benchmark/$photo").use { it.readBytes() }
                val input =
                    ImageInput(
                        bytes = bytes,
                        widthPx = 0,
                        heightPx = 0,
                        rotationDegrees = 0,
                        format = FrameFormat.JPEG,
                        timestampMillis = 0L,
                    )
                // Warmup
                repeat(warmupIterations) { classifier.classify(input) }
                // Measure
                val timings = LongArray(iterationsPerPhoto)
                for (i in 0 until iterationsPerPhoto) {
                    val ms = measureTimeMillis { classifier.classify(input) }
                    timings[i] = ms
                    emit(BenchmarkProgress.Tick(photo, i + 1, iterationsPerPhoto, ms))
                }
                timings.sort()
                results +=
                    BenchmarkResult(
                        photoLabel = photo,
                        n = iterationsPerPhoto,
                        p50 = timings[(iterationsPerPhoto * 50) / 100],
                        p90 = timings[(iterationsPerPhoto * 90) / 100],
                        p95 = timings[(iterationsPerPhoto * 95) / 100],
                        p99 = timings[(iterationsPerPhoto * 99) / 100],
                        mean = timings.average(),
                        timestampMillis = System.currentTimeMillis(),
                    )
            }
            val run =
                BenchmarkRun(
                    modelVersion = modelVersion,
                    device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                    results = results,
                )
            val json = benchmarkJson.encodeToString(BenchmarkRun.serializer(), run)
            val dir = File(context.filesDir, "benchmarks").apply { mkdirs() }
            val out = File(dir, "benchmark_${System.currentTimeMillis()}.json")
            out.writeText(json)
            emit(BenchmarkProgress.Done(run, out.absolutePath))
        }.flowOn(Dispatchers.IO)
}

sealed class BenchmarkProgress {
    data class Tick(
        val photo: String,
        val iteration: Int,
        val total: Int,
        val lastMs: Long,
    ) : BenchmarkProgress()

    data class Done(
        val run: BenchmarkRun,
        val outputPath: String,
    ) : BenchmarkProgress()
}
