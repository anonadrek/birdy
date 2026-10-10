package se.birdy.app.ui.match

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Match photo gives way so "Spara observation" stays in view (2026-10-06): full 260dp when
 * there is room, smaller when more follows it (larger text, a longer credit) or the screen is
 * shorter, never below the floor. What follows it is measured on screen (MatchSaveButtonFoldTest);
 * the estimate only sizes the first frame.
 */
class MatchPhotoHeightTest {
    private val statusBar = 32.dp

    @Test
    fun `a tall screen gets the full photo`() {
        assertEquals(MATCH_PHOTO_MAX, matchPhotoHeight(viewportHeight = 891.dp, statusBar = statusBar, belowPhoto = 443.dp))
    }

    @Test
    fun `the photo shrinks as more follows it`() {
        val heights = listOf(1f, 1.15f, 1.3f, 1.5f, 2f).map { matchPhotoHeight(800.dp, statusBar, estimatedBelowPhoto(it)) }
        assertEquals(heights.sortedDescending(), heights, "not shrinking with font scale: $heights")
        assertTrue(heights.first() > heights.last(), "no change at all: $heights")
    }

    @Test
    fun `the photo shrinks on a shorter screen`() {
        assertTrue(matchPhotoHeight(680.dp, statusBar, 443.dp) < matchPhotoHeight(740.dp, statusBar, 443.dp))
    }

    @Test
    fun `the photo stays between the floor and the full height`() {
        assertEquals(MATCH_PHOTO_MIN, matchPhotoHeight(400.dp, statusBar, estimatedBelowPhoto(2f)))
        assertEquals(MATCH_PHOTO_MAX, matchPhotoHeight(2000.dp, statusBar, estimatedBelowPhoto(1f)))
        assertEquals(160.dp, MATCH_PHOTO_MIN)
    }
}
