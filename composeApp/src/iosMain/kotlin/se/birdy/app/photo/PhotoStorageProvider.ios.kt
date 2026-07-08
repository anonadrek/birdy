package se.birdy.app.photo

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import se.birdy.app.toNSData

actual object PhotoStorageProvider {
    private val storage = IosPhotoStorage()

    actual fun get(): PhotoStorage = storage
}

/**
 * iOS impl. Persists JPEG bytes as-is (the Android contract's 1024px rescale is
 * deferred to plan i2 — nothing on iOS produces photos until the camera lands there).
 */
@OptIn(ExperimentalForeignApi::class)
class IosPhotoStorage : PhotoStorage {
    override suspend fun persistJpeg(bytes: ByteArray): String {
        if (bytes.isEmpty()) throw FrameUnavailableException("Empty JPEG bytes")
        val docs =
            NSFileManager.defaultManager
                .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as NSURL
        val dir = docs.path + "/observations"
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/${NSUUID().UUIDString}.jpg"
        bytes.toNSData().writeToFile(path, true)
        return path
    }

    override suspend fun delete(path: String) {
        NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
