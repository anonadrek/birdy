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
            val real = FlakyAudioClassifier(failuresBeforeSuccess = 2)
            val fake = MarkingAudioClassifier {}
            val guard = AudioSessionFailureGuard(real = real, fallback = fake, threshold = 3)
            runCatching { guard.classify(fakeInput()) } // 1 fail
            runCatching { guard.classify(fakeInput()) } // 2 fail
            guard.classify(fakeInput()) // success → reset
            runCatching { guard.classify(fakeInput()) } // 1 fail again — under threshold
            assertEquals(AudioClassifierMode.REAL, guard.mode)
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
        return AudioClassification(top = emptyList(), inferenceMs = 1L, modelVersion = info.modelVersion)
    }

    override fun close() {}
}

private class FlakyAudioClassifier(
    private var failuresBeforeSuccess: Int,
) : BirdAudioClassifier {
    override val info: AudioModelInfo = AudioModelInfo("flaky_v0", listOf(1, 144_000), listOf(1, 6_362), 0.0)

    override suspend fun classify(input: AudioInput): AudioClassification {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            error("flaky")
        }
        return AudioClassification(top = emptyList(), inferenceMs = 1L, modelVersion = info.modelVersion)
    }

    override fun close() {}
}
