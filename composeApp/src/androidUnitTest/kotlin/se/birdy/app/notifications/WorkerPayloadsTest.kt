package se.birdy.app.notifications

import android.content.Context
import android.content.ContextWrapper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import se.birdy.app.i18n.AppStrings
import se.birdy.content.Locale
import kotlin.test.assertTrue

/**
 * 1.3.1 final-review fix: [renameChannels] must swallow a channel rename failure the same way
 * MainActivity.onCreate's own try/catch around [NotificationChannels.createOrUpdate] already
 * does, instead of letting it escape `withWorkerPayloads`. Before this fix the call was bare,
 * so a rare rename failure (binder/OEM hiccup, a future empty translation) would surface as
 * this worker run's own failure and trip its outer catch into `Result.retry()`, holding back
 * the very notification the run was for.
 *
 * A [ContextWrapper] whose `getSystemService` throws for the notification service stands in
 * for "a NotificationManager that throws": [NotificationChannels.createOrUpdate] fetches the
 * manager before it ever touches a string resource, so no compose-resources setup is needed
 * here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WorkerPayloadsTest {
    @Test
    fun `a channel rename failure is swallowed instead of failing the worker run`() {
        var askedForNotificationService = false
        val throwingContext =
            object : ContextWrapper(RuntimeEnvironment.getApplication()) {
                override fun getSystemService(name: String): Any? {
                    if (name == Context.NOTIFICATION_SERVICE) {
                        askedForNotificationService = true
                        error("boom: simulated binder/OEM failure")
                    }
                    return super.getSystemService(name)
                }
            }

        // Must not throw: a thrown exception here would fail this test, exactly mirroring what
        // would otherwise happen to the worker's own doWork() without this fix.
        runBlocking { renameChannels(throwingContext, AppStrings(Locale.EN)) }

        assertTrue(askedForNotificationService, "the throwing override was never reached; this test proved nothing")
    }
}
