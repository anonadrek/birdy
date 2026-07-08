package se.birdy.app.util

/**
 * Reads the full contents of a file at [path]. `java.io.File` does not exist on Native,
 * so this is a platform actual: Android reads via `java.io.File`, iOS via `NSData`.
 */
internal expect fun readFileBytes(path: String): ByteArray

/** Coarse classification of a [readFileBytes] failure, used to pick a user-facing error kind. */
internal enum class ReadFailureKind { NOT_FOUND, IO_ERROR, OTHER }

/**
 * Classifies a [Throwable] caught around [readFileBytes]. Platform actual because the
 * underlying exception types (`java.io.FileNotFoundException` / `java.io.IOException`)
 * only exist on Android — Native failures collapse to [ReadFailureKind.OTHER].
 */
internal expect fun classifyReadFailure(t: Throwable): ReadFailureKind
