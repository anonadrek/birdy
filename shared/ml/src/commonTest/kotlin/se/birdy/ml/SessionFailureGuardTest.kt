package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionFailureGuardTest {
    @Test
    fun degrades_to_fallback_after_three_failures() =
        runTest {
            var fakeUsed = false
            val real = ThrowingClassifier()
            val fake = MarkingClassifier { fakeUsed = true }
            val guard = SessionFailureGuard(real = real, fallback = fake, threshold = 3)

            // 1, 2, 3 — försöker still real
            repeat(3) { runCatching { guard.classify(fakeInput()) } }
            assertEquals(ClassifierMode.REAL, guard.mode)

            // 4:e call — guard ska nu ha degraderat och anropa fake istället
            guard.classify(fakeInput())
            assertTrue(fakeUsed)
            assertEquals(ClassifierMode.DEMO, guard.mode)
        }

    @Test
    fun resets_failure_count_on_successful_call() =
        runTest {
            val real = FlakyClassifier(failuresBeforeSuccess = 2)
            val fake = MarkingClassifier {}
            val guard = SessionFailureGuard(real = real, fallback = fake, threshold = 3)
            runCatching { guard.classify(fakeInput()) } // 1 fail
            runCatching { guard.classify(fakeInput()) } // 2 fail
            guard.classify(fakeInput()) // success → reset
            runCatching { guard.classify(fakeInput()) } // 1 fail again — under threshold
            assertEquals(ClassifierMode.REAL, guard.mode)
        }

    private fun fakeInput() =
        ImageInput(
            bytes = ByteArray(0),
            widthPx = 1,
            heightPx = 1,
            rotationDegrees = 0,
            format = FrameFormat.JPEG,
            timestampMillis = 0L,
        )
}

private class ThrowingClassifier : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification = error("boom")

    override fun close() {}
}

private class MarkingClassifier(
    val onClassify: () -> Unit,
) : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification {
        onClassify()
        return Classification(results = emptyList())
    }

    override fun close() {}
}

private class FlakyClassifier(
    private var failuresBeforeSuccess: Int,
) : BirdClassifier {
    override suspend fun classify(image: ImageInput): Classification {
        if (failuresBeforeSuccess > 0) {
            failuresBeforeSuccess--
            error("flaky")
        }
        return Classification(results = emptyList())
    }

    override fun close() {}
}
