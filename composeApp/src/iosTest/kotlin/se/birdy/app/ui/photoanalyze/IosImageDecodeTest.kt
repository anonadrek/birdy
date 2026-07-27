package se.birdy.app.ui.photoanalyze

import kotlin.test.Test
import kotlin.test.assertNull

/**
 * i2c final review, Finding 2: K/N's `UIImage(data:)` constructor throws a raw
 * [NullPointerException] instead of yielding Kotlin null when the underlying ObjC failable
 * init returns nil (see [uiImageFromDataOrNull]'s doc — verified empirically in i2c T7 via
 * [se.birdy.app.photo.IosPhotoStorageTest]). Before the fix, [decodeForCrop] used the dead-elvis
 * `UIImage(data = ...) ?: return null` pattern, so undecodable bytes crashed instead of
 * reaching the designed decodeFailed() UX. These pin `decodeForCrop` returning null instead
 * of crashing.
 */
class IosImageDecodeTest {
    @Test
    fun garbage_bytes_decode_to_null_instead_of_crashing() {
        assertNull(decodeForCrop(ByteArray(64) { 1 }), "undecodable bytes must decode to null, not crash")
    }

    @Test
    fun empty_bytes_decode_to_null() {
        assertNull(decodeForCrop(ByteArray(0)), "empty bytes must decode to null (early-return path)")
    }
}
