package se.birdy.data.observation

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Same Documents directory the iOS photo persist writes into (`NSURL.path` of
 * `NSDocumentDirectory`). Used to re-root stale container-UUID prefixes after restore.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun currentDocumentsDirectory(): String {
    val url =
        NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .first() as NSURL
    return url.path.orEmpty()
}
