package se.birdy.app.util

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

internal actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

internal actual fun classifyReadFailure(t: Throwable): ReadFailureKind =
    when (t) {
        is FileNotFoundException -> ReadFailureKind.NOT_FOUND
        is IOException -> ReadFailureKind.IO_ERROR
        else -> ReadFailureKind.OTHER
    }
