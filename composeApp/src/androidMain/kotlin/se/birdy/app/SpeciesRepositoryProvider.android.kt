package se.birdy.app

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import se.birdy.content.SpeciesRepository
import se.birdy.content.SqlDelightSpeciesRepository
import se.birdy.content.db.BirdyContent
import java.io.File
import java.io.FileOutputStream

actual object SpeciesRepositoryProvider {
    private var instance: SpeciesRepository? = null
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    actual fun get(): SpeciesRepository {
        instance?.let { return it }
        val dbFile = File(appContext.filesDir, "species.db")
        if (!dbFile.exists()) {
            appContext.assets.open("composeResources/composeApp.composeResources/files/species.db").use { input ->
                FileOutputStream(dbFile).use { output -> input.copyTo(output) }
            }
        }
        val driver = AndroidSqliteDriver(BirdyContent.Schema, appContext, "species.db")
        val repo = SqlDelightSpeciesRepository(BirdyContent(driver))
        instance = repo
        return repo
    }
}
