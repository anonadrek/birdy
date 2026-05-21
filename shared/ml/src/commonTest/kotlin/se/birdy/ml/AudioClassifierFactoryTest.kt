package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AudioClassifierFactoryTest {
    @Test
    fun returns_real_classifier_when_init_succeeds() =
        runTest {
            val factory =
                AudioClassifierFactory(
                    createReal = { FakeAudioClassifier() },
                    createFallback = { FakeAudioClassifier() },
                    onCrashlytics = {},
                )
            val (_, mode) = factory.create()
            assertEquals(AudioClassifierMode.REAL, mode)
        }

    @Test
    fun falls_back_to_fake_when_real_init_throws() =
        runTest {
            var crashlyticsCalled: Throwable? = null
            val factory =
                AudioClassifierFactory(
                    createReal = { error("model missing") },
                    createFallback = { FakeAudioClassifier() },
                    onCrashlytics = { crashlyticsCalled = it },
                )
            val (_, mode) = factory.create()
            assertEquals(AudioClassifierMode.DEMO, mode)
            assertEquals("model missing", crashlyticsCalled?.message)
        }

    @Test
    fun real_classifier_is_closeable_without_error() =
        runTest {
            val factory =
                AudioClassifierFactory(
                    createReal = { FakeAudioClassifier() },
                    createFallback = { FakeAudioClassifier() },
                    onCrashlytics = {},
                )
            val (clf, _) = factory.create()
            // Should not throw
            clf.close()
        }
}
