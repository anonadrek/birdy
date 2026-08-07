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
                    onDegrade = {},
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
                    onDegrade = { crashlyticsCalled = it },
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
                    onDegrade = {},
                )
            val (clf, _) = factory.create()
            // Should not throw
            clf.close()
        }

    @Test
    fun allowFallbackFalse_rethrowsRealLoadFailure() =
        runTest {
            val boom = IllegalStateException("native lib missing")
            var degraded: Throwable? = null
            val factory =
                AudioClassifierFactory(
                    createReal = { throw boom },
                    createFallback = { FakeAudioClassifier() },
                    onDegrade = { degraded = it },
                    allowFallback = false,
                )
            val thrown = kotlin.runCatching { factory.create() }.exceptionOrNull()
            assertEquals(boom, thrown)
            assertEquals(boom, degraded)
        }

    @Test
    fun allowFallbackFalse_successReturnsRealWithoutGuardWrap() =
        runTest {
            val real = FakeAudioClassifier()
            val factory =
                AudioClassifierFactory(
                    createReal = { real },
                    createFallback = { FakeAudioClassifier() },
                    onDegrade = {},
                    allowFallback = false,
                )
            val (clf, mode) = factory.create()
            assertEquals(AudioClassifierMode.REAL, mode)
            // Utan guard-wrap är det exakt real-instansen som returneras:
            assertEquals(real, clf)
        }
}
