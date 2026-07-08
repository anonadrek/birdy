package se.birdy.app.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import se.birdy.app.toByteArray

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFileBytes(path: String): ByteArray {
    val data = NSData.dataWithContentsOfFile(path) ?: throw IllegalStateException("Could not read $path")
    return data.toByteArray()
}

internal actual fun classifyReadFailure(t: Throwable): ReadFailureKind = ReadFailureKind.OTHER
