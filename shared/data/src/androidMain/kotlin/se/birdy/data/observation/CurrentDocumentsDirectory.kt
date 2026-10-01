package se.birdy.data.observation

/**
 * Android `filesDir` (`/data/user/0/<pkg>/files/…`) is stable across Auto Backup
 * restore for the primary user, so observation media paths do not need re-rooting.
 */
internal actual fun currentDocumentsDirectory(): String = ""
