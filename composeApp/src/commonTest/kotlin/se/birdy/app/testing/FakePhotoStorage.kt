package se.birdy.app.testing

import se.birdy.app.photo.FrameUnavailableException
import se.birdy.app.photo.PhotoStorage

class FakePhotoStorage : PhotoStorage {
    val persisted: MutableMap<String, ByteArray> = mutableMapOf()

    /** When non-null, persistJpeg throws this. */
    var failOnPersist: Throwable? = null

    /** When true, delete throws (test that delete-failure is swallowed). */
    var deleteThrows: Boolean = false

    private var counter = 0

    override suspend fun persistJpeg(bytes: ByteArray): String {
        failOnPersist?.let { throw it }
        if (bytes.isEmpty()) throw FrameUnavailableException("empty bytes")
        val path = "/fake/observations/photo-${counter++}.jpg"
        persisted[path] = bytes
        return path
    }

    override suspend fun delete(path: String) {
        if (deleteThrows) throw RuntimeException("delete failed")
        persisted.remove(path)
    }
}
