package se.birdy.ml

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TfLiteBirdClassifierTest {
    private val mapper =
        AiyLabelMapper.parse(
            """
            {
              "_meta": { "generated_for_model_version": "test_v1", "coverage_pct": 60.0,
                         "mapped_classes": 3, "total_classes": 5 },
              "mappings": { "0": "Q-talgoxe", "1": "Q-koltrast", "2": "Q-blames" }
            }
            """.trimIndent(),
        )

    private val info =
        BirdClassifierModelInfo(
            modelVersion = "test_v1",
            distribution = ModelDistribution.COMPOSE_RESOURCES,
            inputWidthPx = 4,
            inputHeightPx = 4,
            inputChannels = 3,
            inputDtype = TensorDtype.FLOAT32,
            normalizationMean = listOf(0f, 0f, 0f),
            normalizationStd = listOf(1f, 1f, 1f),
            outputClasses = 5,
            backgroundClassIndex = 4,
            outputDtype = TensorDtype.FLOAT32,
            outputScale = 1f,
            outputZeroPoint = 0,
            tfliteFileBytes = 0L,
            tfliteSha256 = "",
        )

    private val stubPreprocess: (ImageInput, BirdClassifierModelInfo) -> FloatArray =
        { _, info -> FloatArray(info.inputHeightPx * info.inputWidthPx * info.inputChannels) }

    private fun fakeInput() =
        ImageInput(
            bytes = ByteArray(4 * 4 * 3),
            widthPx = 4,
            heightPx = 4,
            rotationDegrees = 0,
            format = FrameFormat.RGBA_8888,
            timestampMillis = 0L,
        )

    private class ScriptedRunner(
        private val scores: FloatArray,
    ) : TfliteRunner {
        override fun run(
            input: FloatArray,
            output: FloatArray,
        ) {
            scores.copyInto(output)
        }

        override fun close() {}
    }

    @Test
    fun returns_top_3_above_threshold() =
        runTest {
            val runner = ScriptedRunner(floatArrayOf(0.1f, 0.6f, 0.2f, 0.05f, 0.05f))
            val classifier =
                TfLiteBirdClassifier(
                    info = info,
                    runner = runner,
                    preprocess = stubPreprocess,
                    mapper = mapper,
                    threshold = 0.08f,
                    topK = 3,
                )
            val result = classifier.classify(fakeInput())
            assertEquals(3, result.results.size)
            assertEquals("Q-koltrast", result.results[0].speciesId)
            assertEquals(0.6f, result.results[0].confidence)
            assertEquals("Q-blames", result.results[1].speciesId)
            assertEquals("Q-talgoxe", result.results[2].speciesId)
        }

    @Test
    fun filters_results_below_threshold() =
        runTest {
            val runner = ScriptedRunner(floatArrayOf(0.1f, 0.05f, 0.02f, 0.01f, 0.01f))
            val classifier =
                TfLiteBirdClassifier(
                    info = info,
                    runner = runner,
                    preprocess = stubPreprocess,
                    mapper = mapper,
                    threshold = 0.35f,
                    topK = 3,
                )
            val result = classifier.classify(fakeInput())
            assertEquals(0, result.results.size)
        }

    @Test
    fun drops_results_with_unknown_qid_mapping() =
        runTest {
            // Index 3 is unmapped (mapper has only 0,1,2). Score 0.7 at idx 3 → dropped.
            val runner = ScriptedRunner(floatArrayOf(0.1f, 0.1f, 0.1f, 0.7f, 0.05f))
            val classifier =
                TfLiteBirdClassifier(
                    info = info,
                    runner = runner,
                    preprocess = stubPreprocess,
                    mapper = mapper,
                    threshold = 0.0f,
                    topK = 3,
                )
            val result = classifier.classify(fakeInput())
            assertEquals(3, result.results.size)
            result.results.forEach {
                assertTrue(it.speciesId in setOf("Q-talgoxe", "Q-koltrast", "Q-blames"))
            }
        }

    @Test
    fun concurrent_calls_serialize_via_mutex() =
        runTest {
            val log = mutableListOf<String>()
            val runner =
                object : TfliteRunner {
                    override fun run(
                        input: FloatArray,
                        output: FloatArray,
                    ) {
                        log += "start"
                        for (i in output.indices) output[i] = 0.1f
                        log += "end"
                    }

                    override fun close() {}
                }
            val classifier =
                TfLiteBirdClassifier(
                    info = info,
                    runner = runner,
                    preprocess = stubPreprocess,
                    mapper = mapper,
                )
            repeat(10) { launch { classifier.classify(fakeInput()) } }
            // runTest auto-advances; wait for all child coroutines via testScheduler.
            testScheduler.advanceUntilIdle()
            assertEquals(20, log.size)
            // Every even idx is "start", every odd is "end" → strict serialization.
            for (i in 0 until 10) {
                assertEquals("start", log[i * 2])
                assertEquals("end", log[i * 2 + 1])
            }
        }
}
