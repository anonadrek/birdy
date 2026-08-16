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
 * Input/output [ByteBuffer]s are allocated once at construction time and reused across
 * [classify] calls (mirroring [AndroidTfliteRunner]) to avoid ~600 KB direct-memory
 * allocation per inference.
 *
 * Built via [AndroidTfliteAudioRunner.load] which must be called from a suspend context
 * (IO dispatcher recommended). The [Context] is only used during loading to open the
 * asset file descriptor — it is NOT retained by the runner.
 */
class AndroidTfliteAudioRunner private constructor(
    private val interpreter: Interpreter,
    // Retained on purpose: the Interpreter points into this mapped buffer's native memory
    // and does not copy it. Dropping the reference lets GC unmap the model mid-lifetime ->
    // dangling pointer (same defect class as the photo first-run bug, fixed 2026-07-16, i2a).
    private val modelBuffer: MappedByteBuffer,
    private val mapper: BirdNetLabelMapper,
    override val info: AudioModelInfo,
    /** Number of float samples the model expects per call — read from inputShape at load time. */
    private val expectedSamples: Int,
) : BirdAudioClassifier {
    init {
        require(modelBuffer.capacity() > 0) { "Empty model buffer — model file failed to map" }
    }

    private val mutex = Mutex()
    private var closed = false

    // Allocated once and reused across classify() calls (Fix #3 — sister-pattern parity with
    // AndroidTfliteRunner). rewind() is called at the top of each classify() invocation.
    private val inputBuf: ByteBuffer =
        ByteBuffer
            .allocateDirect(expectedSamples * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
    private val outputClasses: Int = info.outputShape.last()
    private val outputBuf: ByteBuffer =
        ByteBuffer
            .allocateDirect(outputClasses * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

    override suspend fun classify(input: AudioInput): AudioClassification =
        mutex.withLock {
            check(!closed) { "AndroidTfliteAudioRunner closed" }
            require(input.waveform.size == expectedSamples) {
                "Expected $expectedSamples samples (from model inputShape), got ${input.waveform.size}"
            }

            inputBuf.rewind()
            input.waveform.forEach { inputBuf.putFloat(it) }
            inputBuf.rewind()
            outputBuf.rewind()

            val t0 = System.currentTimeMillis()
            interpreter.run(inputBuf, outputBuf)
            val inferenceMs = System.currentTimeMillis() - t0

            outputBuf.rewind()
            // BirdNET-Lite emits pre-sigmoid logits. Map to [0, 1] confidences via flat sigmoid
            // (clip to [-15, 15] to avoid overflow on unusually large logits).
            // Mirrors BirdNET-Analyzer's `flat_sigmoid` post-processing.
            val scores = FloatArray(outputClasses) { flatSigmoid(outputBuf.float) }

            val top3 = rankMappedScores(scores, mapper::lookup)

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
            // Fix #1: wrap post-construction steps in try/catch so the native TFLite
            // handle is always closed if anything throws before ownership transfers.
            val interpreter = Interpreter(model, options)
            try {
                val inputShape = interpreter.getInputTensor(0).shape().toList()
                val outputShape = interpreter.getOutputTensor(0).shape().toList()

                // Loaded here (before the shape checks) because the output-shape guard below
                // needs totalBirdnetClasses to detect a model/mapping mismatch.
                val mapper = loadBirdNetLabelMapper()
                check(outputShape.last() == mapper.totalBirdnetClasses) {
                    "Model emits ${outputShape.last()} classes but birdnet_lite_to_qid.json " +
                        "maps ${mapper.totalBirdnetClasses} — model/mapping mismatch would mis-index species."
                }

                check(inputShape.size == 2 && inputShape[0] == 1) {
                    "Unexpected inputShape $inputShape — expected [1, N] waveform tensor. " +
                        "This may indicate the wrong model file was bundled (T1 regression)."
                }
                val expectedSamples = inputShape.last()

                val info =
                    AudioModelInfo(
                        modelVersion = mapper.modelVersion,
                        inputShape = inputShape,
                        outputShape = outputShape,
                        coveragePct = mapper.coveragePct,
                    )
                return AndroidTfliteAudioRunner(interpreter, model, mapper, info, expectedSamples)
            } catch (t: Throwable) {
                runCatching { interpreter.close() }
                throw t
            }
        }

        // Fix #7: wrap AssetFileDescriptor in .use { } so the fd is closed even if
        // FileInputStream construction or channel.map() throws.
        private fun loadModelFile(
            context: Context,
            assetPath: String,
        ): MappedByteBuffer =
            context.assets.openFd(assetPath).use { afd ->
                FileInputStream(afd.fileDescriptor).use { stream ->
                    stream.channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
                }
            }
    }
}
