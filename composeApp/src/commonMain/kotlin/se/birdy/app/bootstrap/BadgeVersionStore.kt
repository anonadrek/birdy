package se.birdy.app.bootstrap

/** Persists the last `BadgeCatalog.version` we successfully ran a backfill for. */
interface BadgeVersionStore {
    var lastSeen: Int
}
