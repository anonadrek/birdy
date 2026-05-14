package se.birdy.ml

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ClassifierBootstrapTest {
    @Test
    fun emits_initializing_then_ready_on_success() =
        runTest {
            val bootstrap =
                ClassifierBootstrap(
                    buildClassifier = { Triple(FakeBirdClassifier(), ClassifierMode.REAL, "v1.0") },
                )
            val states = bootstrap.state.take(2).toList()
            assertTrue(states[0] is ClassifierBootstrapState.Initializing)
            val ready = states[1] as ClassifierBootstrapState.Ready
            assertEquals(ClassifierMode.REAL, ready.mode)
            assertEquals("v1.0", ready.modelVersion)
        }

    @Test
    fun emits_failed_when_build_throws() =
        runTest {
            val bootstrap =
                ClassifierBootstrap(
                    buildClassifier = { throw RuntimeException("boom") },
                )
            val states = bootstrap.state.take(2).toList()
            val failed = states[1] as ClassifierBootstrapState.Failed
            assertEquals("boom", failed.cause.message)
        }
}
