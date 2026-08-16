package se.birdy.ml

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IosTfliteAudioRunnerTest {
    // Sim-slicen saknar Flex, så en LYCKAD load kan inte testas här (i3-spec §Test).
    // Dessa tester pinnar att felvägen är ett kastat undantag — inte en K/N-krasch
    // (samma ärlighetskrav som AudioClassifierFactory bygger på).

    @Test
    fun garbageModelBytesThrowInsteadOfCrashing() =
        runTest {
            val mapper = loadBirdNetLabelMapper()
            assertFailsWith<IllegalStateException> {
                IosTfliteAudioRunner(ByteArray(64) { 0x42 }, mapper)
            }
        }

    @Test
    fun emptyModelBytesThrowRequire() =
        runTest {
            val mapper = loadBirdNetLabelMapper()
            assertFailsWith<IllegalArgumentException> {
                IosTfliteAudioRunner(ByteArray(0), mapper)
            }
        }

    @Test
    fun loadWithMissingFileThrows() =
        runTest {
            assertFailsWith<IllegalStateException> {
                IosTfliteAudioRunner.load("/nonexistent/birdnet.tflite")
            }
        }
}
