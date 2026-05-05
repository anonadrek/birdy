package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeBirdClassifierTest {
    private val sampleFrame =
        ImageInput(
            bytes = byteArrayOf(),
            widthPx = 224,
            heightPx = 224,
            format = FrameFormat.JPEG,
            timestampMillis = 0L,
        )

    @Test
    fun fake_cycles_through_six_entries_then_wraps() =
        runTest {
            val classifier = FakeBirdClassifier()
            val ids = (0 until 12).map { classifier.classify(sampleFrame).top()?.speciesId }
            assertEquals(
                listOf(
                    "Q25485",
                    "Q25234",
                    "Q25404",
                    "Q25402",
                    "Q26490",
                    "Q_LOW",
                    "Q25485",
                    "Q25234",
                    "Q25404",
                    "Q25402",
                    "Q26490",
                    "Q_LOW",
                ),
                ids,
            )
        }

    @Test
    fun fake_low_confidence_entry_is_below_threshold() =
        runTest {
            val classifier = FakeBirdClassifier()
            repeat(5) { classifier.classify(sampleFrame) }
            val sixth = classifier.classify(sampleFrame)
            assertTrue(sixth.top()!!.confidence < 0.35f)
        }

    @Test
    fun fake_top_entry_has_three_predictions_each_cycle() =
        runTest {
            val classifier = FakeBirdClassifier()
            repeat(5) {
                val c = classifier.classify(sampleFrame)
                assertEquals(3, c.results.size, "cycle index $it should have 3 predictions")
            }
        }

    @Test
    fun fake_propagates_frame_timestamp_to_classification() =
        runTest {
            val classifier = FakeBirdClassifier()
            val frame = sampleFrame.copy(timestampMillis = 1234L)
            val result = classifier.classify(frame)
            assertEquals(1234L, result.frameTimestampMillis)
        }

    @Test
    fun fake_is_deterministic_with_injected_cycle() =
        runTest {
            val customCycle =
                listOf(
                    listOf(ClassificationResult("Q1", 0.99f)),
                    listOf(ClassificationResult("Q2", 0.99f)),
                )
            val classifier = FakeBirdClassifier(cycle = customCycle)
            assertEquals("Q1", classifier.classify(sampleFrame).top()?.speciesId)
            assertEquals("Q2", classifier.classify(sampleFrame).top()?.speciesId)
            assertEquals("Q1", classifier.classify(sampleFrame).top()?.speciesId)
        }
}
