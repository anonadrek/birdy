package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioSessionFailureGuardTest {
    private fun fakeInput() = AudioInput(FloatArray(144_000) { 0f }, 48_000, 3_000)

    @Test
    fun degrades_to_fallback_after_three_failures() =
        runTest {
            var fakeUsed = false
            val real = ThrowingAudioClassifier()
            val fake = MarkingAudioClassifier { fakeUsed = true }
            val guard = AudioSessionFailureGuard(real = real, fallback = fake, threshold = 3)

            // 1, 2, 3 — still tries real (failures <= threshold, not yet >)
            repeat(3) { runCatching { guard.classify(fakeInput()) } }
            assertEquals(AudioClassifierMode.REAL, guard.mode)

            // 4th call — guard crosses `failures > threshold`, degrades, returns fallback result
            guard.classify(fakeInput())
            assertTrue(fakeUsed)
            assertEquals(AudioClassifierMode.DEMO, guard.mode)
        }

    @Test
    fun resets_failure_count_on_successful_call() =
        runTest {
            // FakeAudioClassifier lets us inject failures on demand via throwOnNext,
            // so we can interleave failures and successes on the same guard instance.
            val real = FakeAudioClassifier()
            val fake = MarkingAudioClassifier {}
            val guard = AudioSessionFailureGuard(real = real, fallback = fake, threshold = 3)

            // First partial degrade cycle: 2 failures then a success → counter resets to 0.
            real.throwOnNext = RuntimeException("fail 1")
            runCatching { guard.classify(fakeInput()) } // failures = 1
            real.throwOnNext = RuntimeException("fail 2")
            runCatching { guard.classify(fakeInput()) } // failures = 2
            guard.classify(fakeInput()) // success → failures = 0
            assertEquals(AudioClassifierMode.REAL, guard.mode)

            // Second full degrade cycle on the same guard — proves counter is really 0,
            // not 1 (if it were 1, only 3 more failures would be needed instead of 4).
            real.throwOnNext = RuntimeException("fail A")
            runCatching { guard.classify(fakeInput()) } // failures = 1
            real.throwOnNext = RuntimeException("fail B")
            runCatching { guard.classify(fakeInput()) } // failures = 2
            real.throwOnNext = RuntimeException("fail C")
            runCatching { guard.classify(fakeInput()) } // failures = 3 — still REAL (3 not > 3)
            assertEquals(AudioClassifierMode.REAL, guard.mode)
            real.throwOnNext = RuntimeException("fail D")
            guard.classify(fakeInput()) // failures = 4 → crosses threshold, degrades
            assertEquals(AudioClassifierMode.DEMO, guard.mode)
        }

    @Test
    fun close_closes_both_real_and_fallback() =
        runTest {
            val real = FakeAudioClassifier()
            val fake = FakeAudioClassifier()
            val guard = AudioSessionFailureGuard(real = real, fallback = fake, threshold = 3)
            guard.close()
            assertTrue(real.isClosed, "real should be closed")
            assertTrue(fake.isClosed, "fallback should be closed")
        }
}

private class ThrowingAudioClassifier : BirdAudioClassifier {
    override val info: AudioModelInfo = AudioModelInfo("throwing_v0", listOf(1, 144_000), listOf(1, 6_362), 0.0)

    override suspend fun classify(input: AudioInput): AudioClassification = error("boom")

    override fun close() {}
}

private class MarkingAudioClassifier(
    val onClassify: () -> Unit,
) : BirdAudioClassifier {
    override val info: AudioModelInfo = AudioModelInfo("marking_v0", listOf(1, 144_000), listOf(1, 6_362), 0.0)

    override suspend fun classify(input: AudioInput): AudioClassification {
        onClassify()
        return AudioClassification(results = emptyList(), inferenceMs = 1L, modelVersion = info.modelVersion)
    }

    override fun close() {}
}
