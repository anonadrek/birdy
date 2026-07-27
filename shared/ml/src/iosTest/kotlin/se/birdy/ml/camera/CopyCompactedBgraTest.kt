package se.birdy.ml.camera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

/**
 * CVPixelBuffer rows can be padded (bytesPerRow > width*4) for alignment; the
 * preprocessor requires exactly w*h*4 bytes. This pins the row-compaction contract —
 * i2c's sneaky-detail equivalent of i2b's lifetime-pin.
 */
@OptIn(ExperimentalForeignApi::class)
class CopyCompactedBgraTest {
    @Test
    fun compacts_padded_rows_to_exactly_w_h_4() {
        val width = 2
        val height = 2
        val bytesPerRow = 12 // 8 payload + 4 pad per row
        val src = ByteArray(bytesPerRow * height) { it.toByte() }
        val out =
            src.usePinned { pinned ->
                copyCompactedBgra(pinned.addressOf(0), bytesPerRow, width, height)
            }
        assertEquals(width * height * 4, out.size)
        assertContentEquals(src.copyOfRange(0, 8) + src.copyOfRange(12, 20), out)
    }

    @Test
    fun tight_rows_copy_through_unchanged() {
        val width = 3
        val height = 2
        val bytesPerRow = width * 4
        val src = ByteArray(bytesPerRow * height) { (it * 7).toByte() }
        val out =
            src.usePinned { pinned ->
                copyCompactedBgra(pinned.addressOf(0), bytesPerRow, width, height)
            }
        assertContentEquals(src, out)
    }
}
