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
}
