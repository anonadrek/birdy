package se.birdy.app.ui.audio

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Plan 3 Task 7 (device walkthrough, API 36 emulator): Listen showed its marginalia
 * line with the accent markup's asterisks, "Håll telefonen stilla och *låt den sjunga*".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class AudioScanMarkupTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    @Test
    fun `the Listen screen shows no accent markup asterisks`() {
        compose.setContent {
            AudioScanScreen(
                state = AudioScanState.PermissionNeeded,
                permissionState = PermissionState.Unknown,
                demoMode = false,
                onStartRecording = {},
                onStopRecording = {},
                onCancelAnalyzing = {},
                onRequestPermission = {},
                onOpenSettings = {},
                onRetry = {},
                onBack = {},
            )
        }
        compose.waitForIdle()

        compose.onNodeWithText("Håll telefonen stilla och låt den sjunga").assertExists()
        val texts =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes()
                .flatMap { node ->
                    node.config
                        .getOrNull(SemanticsProperties.Text)
                        .orEmpty()
                        .map { it.text }
                }
        assertTrue(texts.none { '*' in it }, "visible text with asterisks: ${texts.filter { '*' in it }}")
    }
}
