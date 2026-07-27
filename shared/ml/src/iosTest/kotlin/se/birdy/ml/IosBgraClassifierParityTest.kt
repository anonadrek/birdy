package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * BGRA parity guard (i2c): proves the [FrameFormat.BGRA_8888] branch feeds the REAL
 * classifier the same pixels as the JPEG/RGBA reference path. Reuses the i2b parity
 * fixture: decode it to raw pixel values via the JPEG path with identity normalization
 * (mean=0, std=1/255 → out == pixel byte values), repack as BGRA, classify. A channel
 * swap in the BGRA branch would misclassify or crater the confidence — mechanically
 * impossible to reintroduce unnoticed (this runs in CI's macOS job).
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
class IosBgraClassifierParityTest {
    @Test
    fun classifies_bgra_repacked_fixture_with_reference_species_and_confidence() =
        runTest {
            val fixtureBytes = Res.readBytes("files/testdata/parity_Q180991.jpg")
            val pixels =
                ImagePreprocessor().preprocess(
                    ImageInput(fixtureBytes, widthPx = 512, heightPx = 341, format = FrameFormat.JPEG),
                    outHeight = 341,
                    outWidth = 512,
                    normalizationMean = floatArrayOf(0f, 0f, 0f),
                    normalizationStd = floatArrayOf(1 / 255f, 1 / 255f, 1 / 255f),
                )
            val bgra = ByteArray(512 * 341 * 4)
            var p = 0
            for (i in 0 until 512 * 341) {
                val r = pixels[p++].roundToInt()
                val g = pixels[p++].roundToInt()
                val b = pixels[p++].roundToInt()
                bgra[i * 4] = b.toByte()
                bgra[i * 4 + 1] = g.toByte()
                bgra[i * 4 + 2] = r.toByte()
                bgra[i * 4 + 3] = 0xFF.toByte()
            }
            val image = ImageInput(bgra, widthPx = 512, heightPx = 341, format = FrameFormat.BGRA_8888)

            val info = loadModelMetadata()
            val modelBytes = ModelArtifactProvider().loadModelBytes(info)
            val mapper = loadAiyLabelMapper()
            val mean = info.normalizationMean.toFloatArray()
            val std = info.normalizationStd.toFloatArray()
            val runner = IosTfliteRunner(modelBytes, info)
            val classification =
                try {
                    TfLiteBirdClassifier(
                        info = info,
                        runner = runner,
                        preprocess = { img, mi ->
                            ImagePreprocessor().preprocess(img, mi.inputHeightPx, mi.inputWidthPx, mean, std)
                        },
                        mapper = mapper,
                    ).classify(image)
                } finally {
                    runner.close()
                }

            val top = classification.results.firstOrNull()
            assertNotNull(top, "classification produced no results above threshold")
            assertEquals("Q180991", top.speciesId, "top-1 species mismatch (got ${top.speciesId} @ ${top.confidence})")
            val delta = abs(top.confidence - REFERENCE_CONFIDENCE)
            assertTrue(delta <= TOLERANCE, "confidence ${top.confidence} off reference by $delta (> $TOLERANCE)")
        }

    private companion object {
        // Same desktop ai-edge-litert reference as IosClassifierParityTest.
        const val REFERENCE_CONFIDENCE = 0.9531f
        const val TOLERANCE = 0.05f
    }
}
