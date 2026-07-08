package se.birdy.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import birdy_bird_scanner.composeapp.generated.resources.Res
import co.touchlab.sqliter.DatabaseConfiguration
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy
import se.birdy.content.SpeciesRepository
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent

private const val APPLICATION_ID_OFFSET = 68

/**
 * iOS actual. Copies the prebuilt species.db compose-resource into Documents/databases
 * on first launch (or when the bundled SQLite application_id differs — same re-copy
 * heuristic as the Android actual) and opens it with the native driver.
 */
@OptIn(ExperimentalResourceApi::class, ExperimentalForeignApi::class)
actual object SpeciesRepositoryProvider {
    private var instance: SpeciesRepository? = null

    actual fun get(): SpeciesRepository {
        instance?.let { return it }
        val dir = databasesDir()
        val dbPath = "$dir/species.db"
        val bundled = runBlocking { Res.readBytes("files/species.db") }
        if (needsCopy(dbPath, bundled)) {
            NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
            bundled.toNSData().writeToFile(dbPath, true)
        }
        val driver: SqlDriver =
            NativeSqliteDriver(
                DatabaseConfiguration(
                    name = "species.db",
                    version = BirdyContent.Schema.version.toInt(),
                    create = { conn ->
                        app.cash.sqldelight.driver.native
                            .wrapConnection(conn) { }
                    },
                    upgrade = { _, _, _ -> },
                    extendedConfig = DatabaseConfiguration.Extended(basePath = dir),
                ),
            )
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        instance = repo
        return repo
    }

    private fun databasesDir(): String {
        val docs =
            NSFileManager.defaultManager
                .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as platform.Foundation.NSURL
        return docs.path + "/databases"
    }

    private fun needsCopy(
        dbPath: String,
        bundled: ByteArray,
    ): Boolean {
        val existing = NSData.dataWithContentsOfFile(dbPath) ?: return true
        if (existing.length.toInt() < APPLICATION_ID_OFFSET + 4) return true
        val existingBytes = existing.toByteArray()
        return applicationId(existingBytes) != applicationId(bundled)
    }

    private fun applicationId(header: ByteArray): Int {
        val o = APPLICATION_ID_OFFSET
        return ((header[o].toInt() and 0xFF) shl 24) or
            ((header[o + 1].toInt() and 0xFF) shl 16) or
            ((header[o + 2].toInt() and 0xFF) shl 8) or
            (header[o + 3].toInt() and 0xFF)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val out = ByteArray(length.toInt())
    if (out.isNotEmpty()) {
        out.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return out
}
