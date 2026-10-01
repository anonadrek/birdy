package se.birdy.data.observation

/** JVM tests persist `/tmp/…` paths that must round-trip unchanged. */
internal actual fun currentDocumentsDirectory(): String = ""
