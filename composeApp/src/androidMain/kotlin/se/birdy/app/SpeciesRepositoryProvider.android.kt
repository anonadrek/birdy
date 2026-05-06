package se.birdy.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import se.birdy.content.SpeciesRepository
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

private const val SPECIES_DB_ASSET_PATH =
    "composeResources/birdy_bird_scanner.composeapp.generated.resources/files/species.db"

private const val SQLITE_HEADER_BYTES = 100
private const val APPLICATION_ID_OFFSET = 68

actual object SpeciesRepositoryProvider {
    private var instance: SpeciesRepository? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun get(): SpeciesRepository {
        instance?.let { return it }
        val dbFile = appContext.getDatabasePath("species.db")
        if (needsCopy(dbFile)) {
            dbFile.parentFile?.mkdirs()
            appContext.assets.open(SPECIES_DB_ASSET_PATH).use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
        }
        val driver = AndroidSqliteDriver(BirdyContent.Schema, appContext, "species.db")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        instance = repo
        return repo
    }

    private fun needsCopy(dbFile: File): Boolean {
        if (!dbFile.exists()) return true
        val bundledId =
            runCatching {
                appContext.assets.open(SPECIES_DB_ASSET_PATH).use { readApplicationId(it) }
            }.getOrNull() ?: return false
        val cachedId =
            runCatching {
                dbFile.inputStream().use { readApplicationId(it) }
            }.getOrNull() ?: return true
        return bundledId != cachedId
    }

    private fun readApplicationId(stream: InputStream): Int {
        val header = ByteArray(SQLITE_HEADER_BYTES)
        var read = 0
        while (read < SQLITE_HEADER_BYTES) {
            val n = stream.read(header, read, SQLITE_HEADER_BYTES - read)
            if (n < 0) error("species.db is shorter than SQLite header")
            read += n
        }
        val o = APPLICATION_ID_OFFSET
        return ((header[o].toInt() and 0xFF) shl 24) or
            ((header[o + 1].toInt() and 0xFF) shl 16) or
            ((header[o + 2].toInt() and 0xFF) shl 8) or
            (header[o + 3].toInt() and 0xFF)
    }
}
