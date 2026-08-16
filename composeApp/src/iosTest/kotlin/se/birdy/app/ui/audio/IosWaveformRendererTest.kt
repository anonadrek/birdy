@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.audio

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.dataWithContentsOfFile
import platform.UIKit.UIImage
import se.birdy.app.ui.photoanalyze.uiImageFromDataOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun imageWidth(image: UIImage): Double = image.size.useContents { width }

private fun imageHeight(image: UIImage): Double = image.size.useContents { height }

class IosWaveformRendererTest {
    @Test
    fun rendersA600x200PngToDisk() =
        runTest {
            val out = NSTemporaryDirectory() + "i3-waveform-test.png"
            NSFileManager.defaultManager.removeItemAtPath(out, error = null)
            val pcm = ShortArray(48_000) { ((it % 100) * 300 - 15_000).toShort() }

            val path = IosWaveformRenderer().renderWaveformPng(pcm, out)

            assertEquals(out, path)
            val data = NSData.dataWithContentsOfFile(out)
            assertNotNull(data)
            val image = uiImageFromDataOrNull(data)
            assertNotNull(image)
            // Dimensioner verifieras via UIImage.size (points, scale 1.0 för fil-PNG).
            assertTrue(imageWidth(image) == 600.0 && imageHeight(image) == 200.0)
        }

    @Test
    fun encodeOpusIsDocumentedNullDegrade() =
        runTest {
            assertNull(IosWaveformRenderer().encodeOpus(ShortArray(4), NSTemporaryDirectory() + "x.opus"))
        }
}
