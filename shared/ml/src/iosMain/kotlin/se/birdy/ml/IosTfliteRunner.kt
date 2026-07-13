package se.birdy.ml

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pin
import kotlinx.cinterop.usePinned
import tflitec.TfLiteInterpreterAllocateTensors
import tflitec.TfLiteInterpreterCreate
import tflitec.TfLiteInterpreterDelete
import tflitec.TfLiteInterpreterGetInputTensor
import tflitec.TfLiteInterpreterGetOutputTensor
import tflitec.TfLiteInterpreterInvoke
import tflitec.TfLiteInterpreterOptionsCreate
import tflitec.TfLiteInterpreterOptionsDelete
import tflitec.TfLiteInterpreterOptionsSetNumThreads
import tflitec.TfLiteModelCreate
import tflitec.TfLiteModelDelete
import tflitec.TfLiteTensorCopyFromBuffer
import tflitec.TfLiteTensorCopyToBuffer
import tflitec.kTfLiteOk
import kotlin.math.roundToInt

/**
 * iOS actual of [TfliteRunner], mirroring [se.birdy.ml.AndroidTfliteRunner] exactly:
 * wraps the vendored TensorFlowLiteC C-API (via the `tflitec` cinterop, i2b T1) for the
 * AIY Birds V1 quantized model. [TfLiteBirdClassifier] calls [run] with FloatArray
 * buffers; this runner quantizes the input float→uint8, invokes the interpreter, then
 * dequantizes the uint8 output back to FloatArray.
 *
 * Input quantization parameters are hardcoded for AIY V1 (scale = 1/128, zeroPoint = 128),
 * identical to the Android runner, because [BirdClassifierModelInfo] does not surface input
 * quantization. Output dequantization uses [BirdClassifierModelInfo.outputScale] /
 * [BirdClassifierModelInfo.outputZeroPoint].
 *
 * **Lifetime rule (i2b T1):** `TfLiteModelCreate` does NOT copy the FlatBuffer, so
 * [modelBytes] must stay pinned for the ENTIRE interpreter lifetime (not just during
 * creation). We hold a lifetime pin ([pinnedModel]) as a field and release it in [close].
 *
 * @param modelBytes the .tflite model contents (loaded via [ModelArtifactProvider]).
 * @param info the model metadata used for shape + output dequantization.
 */
@OptIn(ExperimentalForeignApi::class)
class IosTfliteRunner(
    modelBytes: ByteArray,
    private val info: BirdClassifierModelInfo,
) : TfliteRunner {
    private val inputSize: Int = info.inputHeightPx * info.inputWidthPx * info.inputChannels

    // Lifetime pin: the FlatBuffer must stay valid for as long as the interpreter lives.
    private val pinnedModel = modelBytes.pin()

    private val model =
        checkNotNull(TfLiteModelCreate(pinnedModel.addressOf(0), modelBytes.size.convert())) {
            "TfLiteModelCreate returned null"
        }

    private val options =
        checkNotNull(TfLiteInterpreterOptionsCreate()) {
            "TfLiteInterpreterOptionsCreate returned null"
        }.also { TfLiteInterpreterOptionsSetNumThreads(it, NUM_THREADS) }

    private val interpreter =
        checkNotNull(TfLiteInterpreterCreate(model, options)) {
            "TfLiteInterpreterCreate returned null"
        }.also {
            check(TfLiteInterpreterAllocateTensors(it) == kTfLiteOk) {
                "TfLiteInterpreterAllocateTensors failed"
            }
        }

    // Reusable quant/dequant scratch buffers, allocated once (mirrors Android's direct ByteBuffers).
    private val inputBuffer: UByteArray = UByteArray(inputSize)
    private val outputBuffer: UByteArray = UByteArray(info.outputClasses)

    private var closed = false

    override fun run(
        input: FloatArray,
        output: FloatArray,
    ) {
        require(input.size == inputSize) {
            "input.size=${input.size} != H*W*C=$inputSize"
        }
        require(output.size == info.outputClasses) {
            "output.size=${output.size} != info.outputClasses=${info.outputClasses}"
        }
        check(!closed) { "IosTfliteRunner is closed" }

        // Quantize float input → uint8 (identical formula to AndroidTfliteRunner).
        for (i in input.indices) {
            val quantized =
                (input[i] / AIY_INPUT_SCALE + AIY_INPUT_ZERO_POINT)
                    .roundToInt()
                    .coerceIn(0, 255)
            inputBuffer[i] = quantized.toUByte()
        }

        val inputTensor =
            checkNotNull(TfLiteInterpreterGetInputTensor(interpreter, 0)) { "input tensor was null" }
        inputBuffer.usePinned { pinned ->
            check(
                TfLiteTensorCopyFromBuffer(inputTensor, pinned.addressOf(0), inputSize.convert()) == kTfLiteOk,
            ) { "TfLiteTensorCopyFromBuffer failed" }
        }

        check(TfLiteInterpreterInvoke(interpreter) == kTfLiteOk) { "TfLiteInterpreterInvoke failed" }

        val outputTensor =
            checkNotNull(TfLiteInterpreterGetOutputTensor(interpreter, 0)) { "output tensor was null" }
        outputBuffer.usePinned { pinned ->
            check(
                TfLiteTensorCopyToBuffer(outputTensor, pinned.addressOf(0), outputBuffer.size.convert()) == kTfLiteOk,
            ) { "TfLiteTensorCopyToBuffer failed" }
        }

        // Dequantize uint8 output → float (identical formula to AndroidTfliteRunner).
        val scale = info.outputScale
        val zeroPoint = info.outputZeroPoint
        for (i in output.indices) {
            val q = outputBuffer[i].toInt() and 0xFF
            output[i] = (q - zeroPoint) * scale
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        TfLiteInterpreterDelete(interpreter)
        TfLiteInterpreterOptionsDelete(options)
        TfLiteModelDelete(model)
        pinnedModel.unpin()
    }

    private companion object {
        // AIY V1 input quantization — hardcoded, matching AndroidTfliteRunner.
        private const val AIY_INPUT_SCALE: Float = 1f / 128f // 0.0078125
        private const val AIY_INPUT_ZERO_POINT: Int = 128
        private const val NUM_THREADS: Int = 4
    }
}
