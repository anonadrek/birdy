package se.birdy.app.ui.match

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Match photo gives way so "Spara observation" stays in view (2026-10-06): full 260dp when
 * there is room, smaller with larger text or a shorter screen, never below the floor.
 */
class MatchPhotoHeightTest {
    private val statusBar = 32.dp

    @Test
    fun `a tall screen at normal text size gets the full photo`() {
        assertEquals(MATCH_PHOTO_MAX, matchPhotoHeight(viewportHeight = 800.dp, statusBar = statusBar, fontScale = 1f))
    }

    @Test
    fun `the photo shrinks step by step as the text grows`() {
        val heights = listOf(1f, 1.15f, 1.3f, 1.5f, 2f).map { matchPhotoHeight(680.dp, statusBar, it) }
        assertEquals(heights.sortedDescending(), heights, "not shrinking with font scale: $heights")
        assertTrue(heights.first() > heights.last(), "no change at all: $heights")
    }

    @Test
    fun `the photo shrinks on a shorter screen`() {
        assertTrue(matchPhotoHeight(600.dp, statusBar, 1f) < matchPhotoHeight(680.dp, statusBar, 1f))
    }

    @Test
    fun `the photo stays between the floor and the full height`() {
        assertEquals(MATCH_PHOTO_MIN, matchPhotoHeight(400.dp, statusBar, 2f))
        assertEquals(MATCH_PHOTO_MAX, matchPhotoHeight(2000.dp, statusBar, 1f))
    }
}
