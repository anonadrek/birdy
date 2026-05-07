package se.birdy.ml

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Wraps a TFLite [Interpreter] for the AIY Birds V1 quantized model. The classifier
 * pipeline ([TfLiteBirdClassifier]) calls [run] with FloatArray buffers; this runner
 * quantizes the input to uint8, invokes the interpreter, then dequantizes the output
 * back to FloatArray.
 *
 * Input quantization parameters are hardcoded for AIY V1 (scale = 1/128, zeroPoint = 128)
 * because [BirdClassifierModelInfo] does not yet surface input quantization. Output
 * dequantization uses [BirdClassifierModelInfo.outputScale] / [BirdClassifierModelInfo.outputZeroPoint].
 *
 * This runner is **not** integration-tested in commonTest/jvmTest/androidUnitTest;
 * end-to-end binding correctness is verified by Task 16 BenchmarkScreen and Task 17
 * device-verify against the real AIY V1 model on a physical device. See plan-doc
 * `2026-05-07-v1-04b-real-tflite.md` Task 8 for the full rationale.
 *
 * @param modelBytes the .tflite model contents (loaded via [ModelArtifactProvider]).
 * @param info the model metadata used for shape + output dequantization.
 * @param options Interpreter configuration; defaults to 4 threads.
 */
class AndroidTfliteRunner(
    modelBytes: ByteArray,
    private val info: BirdClassifierModelInfo,
    options: Interpreter.Options = Interpreter.Options().apply { numThreads = 4 },
) : TfliteRunner {
    private val inputBuffer: ByteBuffer =
        ByteBuffer
            .allocateDirect(info.inputHeightPx * info.inputWidthPx * info.inputChannels)
            .apply { order(ByteOrder.nativeOrder()) }

    private val outputBuffer: ByteBuffer =
        ByteBuffer
            .allocateDirect(info.outputClasses)
            .apply { order(ByteOrder.nativeOrder()) }

    private val interpreter: Interpreter =
        run {
            val buffer =
                ByteBuffer.allocateDirect(modelBytes.size).apply {
                    order(ByteOrder.nativeOrder())
                    put(modelBytes)
                    rewind()
                }
            Interpreter(buffer, options)
        }

    private var closed = false

    override fun run(
        input: FloatArray,
        output: FloatArray,
    ) {
        require(input.size == info.inputHeightPx * info.inputWidthPx * info.inputChannels) {
            "input.size=${input.size} != H*W*C=${info.inputHeightPx * info.inputWidthPx * info.inputChannels}"
        }
        require(output.size == info.outputClasses) {
            "output.size=${output.size} != info.outputClasses=${info.outputClasses}"
        }

        inputBuffer.rewind()
        for (v in input) {
            val quantized =
                (v / AIY_INPUT_SCALE + AIY_INPUT_ZERO_POINT)
                    .roundToInt()
                    .coerceIn(0, 255)
            inputBuffer.put(quantized.toByte())
        }

        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val scale = info.outputScale
        val zeroPoint = info.outputZeroPoint
        for (i in output.indices) {
            val q = outputBuffer.get().toInt() and 0xFF
            output[i] = (q - zeroPoint) * scale
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    private companion object {
        // AIY V1 input quantization — hardcoded because not in BirdClassifierModelInfo.
        // Source: aiy_birds_v1.tflite metadata; matches MobileNetV2 [-1, 1] normalization.
        private const val AIY_INPUT_SCALE: Float = 1f / 128f // 0.0078125
        private const val AIY_INPUT_ZERO_POINT: Int = 128
    }
}
