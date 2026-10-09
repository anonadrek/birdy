package se.birdy.app.notifications

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import se.birdy.app.AndroidAppGraphHolder
import se.birdy.app.i18n.AppStrings

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
        renameChannels(context, payloads.strings)
        use(payloads)
    } else {
        AndroidNotificationPayloads.fromContext(context) { payloads ->
            renameChannels(context, payloads.strings)
            use(payloads)
        }
    }
}

/**
 * [NotificationChannels.createOrUpdate], the same way MainActivity.onCreate wraps it: a rename
 * failure (binder/OEM hiccup, a future empty translation) is cosmetic and must never fail this
 * run. Without this, each worker's own outer catch would turn it into a `Result.retry()` that
 * holds back the very notification this run was for.
 */
@Suppress("TooGenericExceptionCaught") // A channel rename is cosmetic; it must never fail the worker's own run.
internal suspend fun renameChannels(
    context: Context,
    strings: AppStrings,
) {
    try {
        NotificationChannels.createOrUpdate(context, strings)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Birdy", "Notification channel rename failed", e)
    }
}
