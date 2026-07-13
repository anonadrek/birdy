package se.birdy.ml

import birdy_bird_scanner.shared.ml.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Whole-classifier numeric-parity check (i2b T3): builds the full iOS classifier
 * (CoreGraphics [ImagePreprocessor] + [IosTfliteRunner] + [AiyLabelMapper]) and
 * classifies a fixed real bird photo, asserting it picks the correct species with a
 * confidence that matches the desktop `ai-edge-litert` reference on the same bytes.
 *
 * Reference (measured on the desktop LiteRT runtime on these exact fixture bytes):
 *   top-1 species = Q180991, confidence ≈ 0.9531.
 *
 * The fixture lives in **commonMain** composeResources (not commonTest) because the
 * Compose resources plugin does not expose test `composeResources` to Kotlin/Native test
 * binaries — the same reason [ImagePreprocessorIosTest] embeds its PNG bytes inline.
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)
class IosClassifierParityTest {
    @Test
    fun classifies_fixture_with_android_reference_species_and_confidence() =
        runTest {
            val info = loadModelMetadata()
            val modelBytes = ModelArtifactProvider().loadModelBytes(info)
            val mapper = loadAiyLabelMapper()
            val mean = info.normalizationMean.toFloatArray()
            val std = info.normalizationStd.toFloatArray()

            val fixtureBytes = Res.readBytes("files/testdata/parity_Q180991.jpg")
            val image =
                ImageInput(
                    bytes = fixtureBytes,
                    widthPx = 512,
                    heightPx = 341,
                    format = FrameFormat.JPEG,
                )

            val runner = IosTfliteRunner(modelBytes, info)
            val classification =
                try {
                    val classifier =
                        TfLiteBirdClassifier(
                            info = info,
                            runner = runner,
                            preprocess = { img, mi ->
                                ImagePreprocessor().preprocess(img, mi.inputHeightPx, mi.inputWidthPx, mean, std)
                            },
                            mapper = mapper,
                        )
                    classifier.classify(image)
                } finally {
                    runner.close()
                }

            val top = classification.results.firstOrNull()
            assertNotNull(top, "classification produced no results above threshold")

            assertEquals(
                EXPECTED_SPECIES_ID,
                top.speciesId,
                "top-1 species mismatch (got ${top.speciesId} @ ${top.confidence})",
            )

            val delta = abs(top.confidence - REFERENCE_CONFIDENCE)
            assertTrue(
                delta <= CONFIDENCE_TOLERANCE,
                "top-1 confidence ${top.confidence} is off reference $REFERENCE_CONFIDENCE by " +
                    "$delta (> $CONFIDENCE_TOLERANCE)",
            )
        }

    private companion object {
        const val EXPECTED_SPECIES_ID = "Q180991"

        // Measured on the desktop ai-edge-litert runtime on these exact fixture bytes.
        const val REFERENCE_CONFIDENCE = 0.9531f
        const val CONFIDENCE_TOLERANCE = 0.05f
    }
}
