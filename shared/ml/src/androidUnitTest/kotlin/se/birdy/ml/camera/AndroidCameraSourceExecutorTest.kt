package se.birdy.ml.camera

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AndroidCameraSourceExecutorTest {
    @Test
    fun stop_shutsDownTheAnalysisExecutor() {
        val executor = Executors.newSingleThreadExecutor()
        val source =
            AndroidCameraSource(
                context = RuntimeEnvironment.getApplication(),
                lifecycleOwner = CreatedLifecycleOwner(),
                executor = executor,
            )
        assertFalse(executor.isShutdown)
        runBlocking { source.stop() }
        assertTrue(executor.isShutdown, "stop() must shut the per-session analysis thread down")
    }

    @Test
    fun stop_isIdempotentOnTheExecutor() =
        runBlocking {
            val executor = Executors.newSingleThreadExecutor()
            val source =
                AndroidCameraSource(
                    context = RuntimeEnvironment.getApplication(),
                    lifecycleOwner = CreatedLifecycleOwner(),
                    executor = executor,
                )
            source.stop()
            source.stop()
            assertTrue(executor.isShutdown)
        }
}

private class CreatedLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
}
