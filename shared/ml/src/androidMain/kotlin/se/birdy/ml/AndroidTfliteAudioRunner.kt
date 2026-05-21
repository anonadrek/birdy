package se.birdy.ml

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Wraps a TFLite [Interpreter] for the BirdNET-Lite model (float32 input, float32 output).
 *
 * The expected input shape [1, N] is read at load-time from the model's tensor metadata
 * so the runner works with BirdNET-Lite 6K Global (and any future model that differs from
 * the v2.4 hardcoded 144 000 samples). See [expectedSamples].
 *
 * Inference is Mutex-serialized to prevent concurrent TFLite state corruption. The
 * [close] method is idempotent and safe to call multiple times.
 *
 * Built via [AndroidTfliteAudioRunner.load] which must be called from a suspend context
 * (IO dispatcher recommended). The [Context] is only used during loading to open the
 * asset file descriptor — it is NOT retained by the runner.
 */
class AndroidTfliteAudioRunner private constructor(
    private val interpreter: Interpreter,
    private val mapper: BirdNetLabelMapper,
    override val info: AudioModelInfo,
    /** Number of float samples the model expects per call — read from inputShape at load time. */
    private val expectedSamples: Int,
) : BirdAudioClassifier {
    private val mutex = Mutex()
    private var closed = false

    override suspend fun classify(input: AudioInput): AudioClassification =
        mutex.withLock {
            check(!closed) { "AndroidTfliteAudioRunner closed" }
            require(input.waveform.size == expectedSamples) {
                "Expected $expectedSamples samples (from model inputShape), got ${input.waveform.size}"
            }

            val inputBuf =
                ByteBuffer
                    .allocateDirect(expectedSamples * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())
            input.waveform.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()

            val outputClasses = info.outputShape.last()
            val outputBuf =
                ByteBuffer
                    .allocateDirect(outputClasses * Float.SIZE_BYTES)
                    .order(ByteOrder.nativeOrder())

            val t0 = System.currentTimeMillis()
            interpreter.run(inputBuf, outputBuf)
            val inferenceMs = System.currentTimeMillis() - t0

            outputBuf.rewind()
            val scores = FloatArray(outputClasses) { outputBuf.float }

            val top3 =
                scores
                    .mapIndexed { idx, score -> idx to score }
                    .sortedByDescending { it.second }
                    .take(3)
                    .mapNotNull { (idx, score) ->
                        mapper.lookup(idx)?.let { qid -> ClassificationResult(qid, score) }
                    }

            AudioClassification(results = top3, inferenceMs = inferenceMs, modelVersion = info.modelVersion)
        }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { interpreter.close() }
    }

    companion object {
        /**
         * Loads the TFLite model from [modelAssetPath] in the app's assets, initialises an
         * [Interpreter], reads input/output tensor shapes, and returns a ready
         * [AndroidTfliteAudioRunner].
         *
         * Adaptive sample count: rather than hardcoding 144 000 (BirdNET-Lite v2.4), the
         * expected number of samples is taken from `inputShape.last()` so the runner works
         * with the 6K Global model or any future variant without a rebuild.
         *
         * @throws IllegalStateException if the input tensor shape is not `[1, N]` (i.e. does
         *   not look like a waveform model), to catch T1 regressions (e.g. wrong model file).
         */
        suspend fun load(
            context: Context,
            modelAssetPath: String = "models/birdnet_lite_v2.tflite",
        ): AndroidTfliteAudioRunner {
            val model = loadModelFile(context, modelAssetPath)
            val options = Interpreter.Options().apply { numThreads = 4 }
            val interpreter = Interpreter(model, options)

            val inputShape = interpreter.getInputTensor(0).shape().toList()
            val outputShape = interpreter.getOutputTensor(0).shape().toList()

            check(inputShape.size == 2 && inputShape[0] == 1) {
                "Unexpected inputShape $inputShape — expected [1, N] waveform tensor. " +
                    "This may indicate the wrong model file was bundled (T1 regression)."
            }
            val expectedSamples = inputShape.last()

            val mapper = loadBirdNetLabelMapper()
            val info =
                AudioModelInfo(
                    modelVersion = mapper.modelVersion,
                    inputShape = inputShape,
                    outputShape = outputShape,
                    coveragePct = mapper.coveragePct,
                )
            return AndroidTfliteAudioRunner(interpreter, mapper, info, expectedSamples)
        }

        private fun loadModelFile(
            context: Context,
            assetPath: String,
        ): MappedByteBuffer {
            val fd = context.assets.openFd(assetPath)
            FileInputStream(fd.fileDescriptor).use { stream ->
                val channel = stream.channel
                return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
