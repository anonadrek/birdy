package se.birdy.app.bootstrap

import android.content.Context

class SharedPrefsBadgeVersionStore(
    context: Context,
) : BadgeVersionStore {
    private val prefs =
        context.getSharedPreferences(
            "birdy_badges",
            Context.MODE_PRIVATE,
        )
    override var lastSeen: Int
        get() = prefs.getInt(KEY_LAST_SEEN, 0)
        set(value) {
            prefs.edit().putInt(KEY_LAST_SEEN, value).apply()
        }

    private companion object {
        const val KEY_LAST_SEEN = "catalog_version_last_seen"
    }
}
