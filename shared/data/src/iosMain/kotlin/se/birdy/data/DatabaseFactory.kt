package se.birdy.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import se.birdy.data.db.BirdyData

/**
 * iOS actual. No-arg constructor (Android's takes a Context). The native driver
 * runs BirdyData.Schema.create/migrate automatically, matching AndroidSqliteDriver —
 * the committed .sqm migrations (1–4) apply on version bumps exactly as on Android.
 */
actual class DatabaseFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(BirdyData.Schema, "birdy-observations.db")
}
