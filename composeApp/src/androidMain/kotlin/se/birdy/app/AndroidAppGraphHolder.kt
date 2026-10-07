package se.birdy.app

import se.birdy.app.di.AppGraph
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-global handle so WorkManager workers can reuse the live [se.birdy.app.di.AppGraph].
 * Set from MainActivity.onCreate; cleared in MainActivity.onDestroy.
 *
 * A null [current] is expected on a WorkManager cold start (Application only — no
 * Activity). Workers must build [se.birdy.app.notifications.NotificationPayloads]
 * standalone in that case, not treat null as "skip the notification".
 */
object AndroidAppGraphHolder {
    private val ref = AtomicReference<AppGraph?>(null)
    val current: AppGraph? get() = ref.get()

    fun set(graph: AppGraph) = ref.set(graph)

    fun clear() = ref.set(null)
}
