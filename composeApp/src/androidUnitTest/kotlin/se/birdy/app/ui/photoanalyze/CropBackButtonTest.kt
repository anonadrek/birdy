package se.birdy.app.ui.photoanalyze

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import se.birdy.app.testing.attachComposeResourcesContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7b: the crop screen had only "Avbryt" at the bottom. It now also has a back
 * button at the top, which cancels like "Avbryt" and sits on the paper above the crop area, so a
 * corner drag can never start on it (Plan 3 Task 7 kept the corners clear of the screen edges).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "sv-w411dp-h891dp-xxhdpi")
class CropBackButtonTest {
    @get:Rule
    val compose = createComposeRule()

    private val cropHintSv = "Dra i hörnen för att beskära bilden"

    @Before
    fun setUp() {
        attachComposeResourcesContext()
    }

    private fun tallPhoto(): ImageBitmap {
        val photo = ImageBitmap(300, 900)
        Canvas(photo).drawRect(Rect(0f, 0f, 300f, 900f), Paint().apply { color = Color.Red })
        return photo
    }

    @Test
    fun `the back button at the top cancels the crop and stays clear of the crop area`() {
        var cancels = 0
        compose.setContent {
            CropAdjustScreen(image = tallPhoto(), onRotate = {}, onConfirm = {}, onCancel = { cancels++ })
        }
        compose.waitForIdle()

        val backButton = compose.onNodeWithContentDescription("Tillbaka").assertIsDisplayed()
        val crop = compose.onNodeWithContentDescription(cropHintSv).getUnclippedBoundsInRoot()
        assertTrue(backButton.getUnclippedBoundsInRoot().bottom <= crop.top, "the back button overlaps the crop area")
        // "Avbryt" is still there at the bottom.
        compose.onNodeWithText("Avbryt").assertIsDisplayed()

        backButton.performClick()
        compose.waitForIdle()
        assertEquals(1, cancels)
    }
}
