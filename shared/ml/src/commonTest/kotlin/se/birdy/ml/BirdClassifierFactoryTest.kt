package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BirdClassifierFactoryTest {
    @Test
    fun returns_real_classifier_when_init_succeeds() =
        runTest {
            val factory =
                BirdClassifierFactory(
                    createReal = { FakeBirdClassifier() },
                    createFallback = { FakeBirdClassifier() },
                    onCrashlytics = {},
                )
            val (_, mode) = factory.create()
            assertEquals(ClassifierMode.REAL, mode)
        }

    @Test
    fun falls_back_to_fake_when_real_init_throws() =
        runTest {
            var crashlyticsCalled: Throwable? = null
            val factory =
                BirdClassifierFactory(
                    createReal = { error("model missing") },
                    createFallback = { FakeBirdClassifier() },
                    onCrashlytics = { crashlyticsCalled = it },
                )
            val (_, mode) = factory.create()
            assertEquals(ClassifierMode.DEMO, mode)
            assertEquals("model missing", crashlyticsCalled?.message)
        }

    @Test
    fun allowFallbackFalse_rethrowsRealLoadFailure() =
        runTest {
            val boom = IllegalStateException("native lib missing")
            var crashlyticsCalled: Throwable? = null
            val factory =
                BirdClassifierFactory(
                    createReal = { throw boom },
                    createFallback = { FakeBirdClassifier() },
                    onCrashlytics = { crashlyticsCalled = it },
                    allowFallback = false,
                )
            val thrown = kotlin.runCatching { factory.create() }.exceptionOrNull()
            assertEquals(boom, thrown)
            assertEquals(boom, crashlyticsCalled)
        }

    @Test
    fun allowFallbackFalse_successReturnsRealWithoutGuardWrap() =
        runTest {
            val real = FakeBirdClassifier()
            val factory =
                BirdClassifierFactory(
                    createReal = { real },
                    createFallback = { FakeBirdClassifier() },
                    onCrashlytics = {},
                    allowFallback = false,
                )
            val (clf, mode) = factory.create()
            assertEquals(ClassifierMode.REAL, mode)
            // Utan guard-wrap är det exakt real-instansen som returneras:
            assertEquals(real, clf)
        }
}
