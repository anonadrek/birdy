package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AudioClassifierBootstrapTest {
    @Test
    fun ensureReady_returnsClassifier_andTransitionsToReady() =
        runTest {
            val fake = FakeAudioClassifier()
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { fake to AudioClassifierMode.REAL },
                )
            val result = bootstrap.ensureReady()
            assertSame(fake, result)
            val state = bootstrap.state.value
            assertIs<AudioClassifierBootstrapState.Ready>(state)
            assertEquals(AudioClassifierMode.REAL, state.mode)
            assertSame(fake, state.classifier)
        }

    @Test
    fun ensureReady_secondCall_returnsCachedClassifier() =
        runTest {
            var buildCalls = 0
            val bootstrap =
                AudioClassifierBootstrap(
                    build = {
                        buildCalls++
                        FakeAudioClassifier() to AudioClassifierMode.REAL
                    },
                )
            val first = bootstrap.ensureReady()
            val second = bootstrap.ensureReady()
            assertEquals(1, buildCalls, "build lambda must be called exactly once")
            assertSame(first, second)
        }

    @Test
    fun ensureReady_propagatesBuildFailure_andTransitionsToFailed() =
        runTest {
            val boom = RuntimeException("model missing")
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { throw boom },
                )
            assertFails { bootstrap.ensureReady() }
            val state = bootstrap.state.value
            assertIs<AudioClassifierBootstrapState.Failed>(state)
            assertSame(boom, state.cause)
        }

    @Test
    fun release_fromReady_closesClassifier_andResetsToIdle() =
        runTest {
            val fake = FakeAudioClassifier()
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { fake to AudioClassifierMode.REAL },
                )
            bootstrap.ensureReady()
            assertIs<AudioClassifierBootstrapState.Ready>(bootstrap.state.value)

            bootstrap.release()

            assertTrue(fake.isClosed, "classifier must be closed after release()")
            assertIs<AudioClassifierBootstrapState.Idle>(bootstrap.state.value)
        }

    @Test
    fun release_fromIdle_isNoOp() =
        runTest {
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { FakeAudioClassifier() to AudioClassifierMode.REAL },
                )
            // State is Idle; release() must not throw.
            bootstrap.release()
            assertIs<AudioClassifierBootstrapState.Idle>(bootstrap.state.value)
        }

    @Test
    fun state_isHotStateFlow_reflectsTransitions() =
        runTest {
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { FakeAudioClassifier() to AudioClassifierMode.REAL },
                )
            assertIs<AudioClassifierBootstrapState.Idle>(bootstrap.state.value)

            bootstrap.ensureReady()
            assertIs<AudioClassifierBootstrapState.Ready>(bootstrap.state.value)

            bootstrap.release()
            assertIs<AudioClassifierBootstrapState.Idle>(bootstrap.state.value)
        }

    @Test
    fun release_fromFailed_resetsToIdle_withoutClose() =
        runTest {
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { error("fail") },
                )
            assertFails { bootstrap.ensureReady() }
            assertIs<AudioClassifierBootstrapState.Failed>(bootstrap.state.value)

            // release() from Failed should not throw and should reset to Idle.
            bootstrap.release()
            assertIs<AudioClassifierBootstrapState.Idle>(bootstrap.state.value)
        }

    @Test
    fun ensureReady_canRetryAfterFailure() =
        runTest {
            var shouldFail = true
            val fake = FakeAudioClassifier()
            val bootstrap =
                AudioClassifierBootstrap(
                    build = {
                        if (shouldFail) error("transient failure")
                        fake to AudioClassifierMode.REAL
                    },
                )
            assertFails { bootstrap.ensureReady() }
            assertIs<AudioClassifierBootstrapState.Failed>(bootstrap.state.value)

            shouldFail = false
            val result = bootstrap.ensureReady()
            assertSame(fake, result)
            assertIs<AudioClassifierBootstrapState.Ready>(bootstrap.state.value)
        }

    @Test
    fun release_closedFlagIsObservable() =
        runTest {
            val fake = FakeAudioClassifier()
            val bootstrap =
                AudioClassifierBootstrap(
                    build = { fake to AudioClassifierMode.DEMO },
                )
            bootstrap.ensureReady()
            assertFalse(fake.isClosed)
            bootstrap.release()
            assertTrue(fake.isClosed)
        }
}
