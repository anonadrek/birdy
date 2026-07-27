package se.birdy.app.photo

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import se.birdy.app.ui.photoanalyze.readJpegPixelSize
import se.birdy.app.ui.scan.encodeBgraFrameToJpeg
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** i2c: IosPhotoStorage måste nu uppfylla hela kontraktet — longest side ≤1024 @ q85. */
class IosPhotoStorageTest {
    private fun jpegOfSize(
        w: Int,
        h: Int,
    ): ByteArray {
        val bytes = ByteArray(w * h * 4) { i -> if (i % 4 == 3) 0xFF.toByte() else 0x80.toByte() }
        return assertNotNull(
            encodeBgraFrameToJpeg(ImageInput(bytes, w, h, format = FrameFormat.BGRA_8888)),
            "test fixture encode failed",
        )
    }

    @Test
    fun oversized_input_is_rescaled_to_1024_longest_side() =
        runTest {
            val storage = IosPhotoStorage()
            val path = storage.persistJpeg(jpegOfSize(1500, 900))
            try {
                val (w, h) = assertNotNull(readJpegPixelSize(path), "persisted file must decode")
                assertEquals(1024, w, "longest side must be exactly 1024")
                assertEquals(614, h, "short side must keep aspect (900*1024/1500 ≈ 614)")
            } finally {
                storage.delete(path)
            }
        }

    @Test
    fun small_input_keeps_its_dimensions() =
        runTest {
            val storage = IosPhotoStorage()
            val path = storage.persistJpeg(jpegOfSize(640, 480))
            try {
                val (w, h) = assertNotNull(readJpegPixelSize(path))
                assertEquals(640 to 480, w to h, "no upscaling")
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            } finally {
                storage.delete(path)
            }
        }

    @Test
    fun empty_and_undecodable_bytes_throw_frame_unavailable() =
        runTest {
            val storage = IosPhotoStorage()
            assertFailsWith<FrameUnavailableException> { storage.persistJpeg(ByteArray(0)) }
            assertFailsWith<FrameUnavailableException> { storage.persistJpeg(ByteArray(64) { 1 }) }
        }
}
