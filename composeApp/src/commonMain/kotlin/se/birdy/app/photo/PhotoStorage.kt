package se.birdy.app.photo

interface PhotoStorage {
    /**
     * Skala JPEG-bytes till longestSide=1024px (quality 85), persistera till
     * filesDir/observations/{uuid}.jpg, returnera absolute path.
     *
     * Kastar IOException vid full disk; FrameUnavailableException om input-bytes
     * är tomma eller går inte att decoda.
     */
    suspend fun persistJpeg(bytes: ByteArray): String

    /** Best-effort delete. Sväljer fel tyst om filen inte finns eller delete failar. */
    suspend fun delete(path: String)
}

class FrameUnavailableException(
    message: String,
) : RuntimeException(message)
