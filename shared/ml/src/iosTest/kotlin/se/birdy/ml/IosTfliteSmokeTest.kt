package se.birdy.ml

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.test.runTest
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
import tflitec.TfLiteTensorByteSize
import tflitec.TfLiteTensorCopyFromBuffer
import tflitec.kTfLiteOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * De-risking spike (i2b T1): proves the vendored static TensorFlowLiteC.xcframework +
 * Kotlin/Native cinterop links and runs one real inference of the bundled AIY Birds V1
 * model on the iOS simulator. Loads the model bytes via the same commonMain
 * [ModelArtifactProvider]/[loadModelMetadata] the Android runner uses, builds an
 * interpreter through the generated `tflitec` C-API bindings, invokes once on a
 * zero-filled uint8 input, and asserts the output tensor is 965 bytes ([1, 965] uint8).
 */
@OptIn(ExperimentalForeignApi::class)
class IosTfliteSmokeTest {
    @Test
    fun loads_model_and_invokes_once() =
        runTest {
            val info = loadModelMetadata()
            val bytes = ModelArtifactProvider().loadModelBytes(info)

            // TfLiteModelCreate does NOT copy the FlatBuffer — the model data must stay
            // valid and pinned for the whole interpreter lifetime, so pin across every call.
            bytes.usePinned { pinnedBytes ->
                val model = TfLiteModelCreate(pinnedBytes.addressOf(0), bytes.size.convert())
                assertNotNull(model, "TfLiteModelCreate returned null")

                val opts = TfLiteInterpreterOptionsCreate()
                assertNotNull(opts, "TfLiteInterpreterOptionsCreate returned null")
                TfLiteInterpreterOptionsSetNumThreads(opts, 4)

                val interp = TfLiteInterpreterCreate(model, opts)
                assertNotNull(interp, "TfLiteInterpreterCreate returned null")
                assertEquals(kTfLiteOk, TfLiteInterpreterAllocateTensors(interp))

                val inTensor = TfLiteInterpreterGetInputTensor(interp, 0)
                assertNotNull(inTensor, "input tensor was null")
                val inSize = 224 * 224 * 3
                val input = UByteArray(inSize) // zeros
                input.usePinned { pinnedInput ->
                    assertEquals(
                        kTfLiteOk,
                        TfLiteTensorCopyFromBuffer(inTensor, pinnedInput.addressOf(0), inSize.convert()),
                    )
                }

                assertEquals(kTfLiteOk, TfLiteInterpreterInvoke(interp))

                val outTensor = TfLiteInterpreterGetOutputTensor(interp, 0)
                assertNotNull(outTensor, "output tensor was null")
                assertEquals(965uL, TfLiteTensorByteSize(outTensor))

                TfLiteInterpreterDelete(interp)
                TfLiteInterpreterOptionsDelete(opts)
                TfLiteModelDelete(model)
            }
        }
}
