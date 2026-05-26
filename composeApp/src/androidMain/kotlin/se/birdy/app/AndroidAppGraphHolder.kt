package se.birdy.app

import se.birdy.app.di.AppGraph
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-global handle so WorkManager workers can access AppGraph.
 * Set from MainActivity.onCreate; cleared in MainActivity.onDestroy.
 */
object AndroidAppGraphHolder {
    private val ref = AtomicReference<AppGraph?>(null)
    val current: AppGraph? get() = ref.get()
    fun set(graph: AppGraph) = ref.set(graph)
    fun clear() = ref.set(null)
}
