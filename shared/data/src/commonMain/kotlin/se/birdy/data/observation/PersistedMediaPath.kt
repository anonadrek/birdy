package se.birdy.data.observation

/**
 * iOS app-container UUID changes on every install/restore (iCloud Backup, "Move to
 * New iPhone", erase-and-restore). Photo persist and `audioStorageDirPath` write
 * absolute `NSURL.path` / `NSSearchPath` strings into `observation.photo_path` /
 * `audio_path`, so those columns still point at the *old* sandbox after restore even
 * though the JPEG/Opus files themselves come back under the new Documents tree.
 *
 * [currentDocumentsDirectory] is the live Documents dir on iOS and empty on
 * Android/JVM (those platforms' filesDir paths survive restore). [resolvePersistedMediaPath]
 * re-roots any stored `…/Documents/<relative>` onto that live dir; other paths pass through.
 */
internal expect fun currentDocumentsDirectory(): String

/**
 * Re-root an absolute sandbox path onto [currentDocumentsDir].
 *
 * Looks for the `/Documents/` marker iOS always inserts, then joins the suffix
 * (`observations/<uuid>.jpg`, `audio/<ts>.opus`, …) onto the live Documents dir.
 * Returns null when [stored] is not a Documents-absolute path, or when
 * [currentDocumentsDir] is blank (Android/JVM).
 */
internal fun rebaseDocumentsPath(
    stored: String,
    currentDocumentsDir: String,
): String? {
    val marker = "/Documents/"
    val idx = stored.indexOf(marker)
    val relative = if (idx >= 0) stored.substring(idx + marker.length) else ""
    return if (currentDocumentsDir.isEmpty() || relative.isEmpty()) {
        null
    } else {
        "${currentDocumentsDir.trimEnd('/')}/$relative"
    }
}

internal fun resolvePersistedMediaPath(stored: String): String {
    val docs = currentDocumentsDirectory()
    val rebased = rebaseDocumentsPath(stored, docs)
    return when {
        docs.isEmpty() -> stored
        rebased != null -> rebased
        !stored.startsWith("/") -> "${docs.trimEnd('/')}/$stored"
        else -> stored
    }
}
