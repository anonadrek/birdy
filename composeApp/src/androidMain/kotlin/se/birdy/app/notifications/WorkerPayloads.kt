package se.birdy.app.notifications

import android.content.Context
import se.birdy.app.AndroidAppGraphHolder

/**
 * The payloads for a notification worker: the running app's, or a standalone set when
 * WorkManager started a fresh process. The channels are created or renamed in the same language
 * as the notification before [use] runs. Return only plain data from [use]: in a fresh process
 * the payloads' database closes when [use] returns.
 */
internal suspend fun <T> withWorkerPayloads(
    context: Context,
    use: suspend (NotificationPayloads) -> T,
): T {
    val graph = AndroidAppGraphHolder.current
    return if (graph != null) {
        val payloads = NotificationPayloads.from(graph)
        NotificationChannels.createOrUpdate(context, payloads.strings)
        use(payloads)
    } else {
        AndroidNotificationPayloads.fromContext(context) { payloads ->
            NotificationChannels.createOrUpdate(context, payloads.strings)
            use(payloads)
        }
    }
}
