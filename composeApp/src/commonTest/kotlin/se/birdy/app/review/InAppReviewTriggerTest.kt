package se.birdy.app.review

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import se.birdy.app.testing.FakeUserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InAppReviewTriggerTest {
    private fun trigger(
        prefs: FakeUserPreferences,
        onLaunch: () -> Unit,
    ) = InAppReviewTrigger(prefs = prefs, launchReview = onLaunch, threshold = 3)

    @Test
    fun below_threshold_does_not_launch() =
        runTest {
            val prefs = FakeUserPreferences()
            var launched = 0
            trigger(prefs) { launched++ }.onObservationSaved(2)
            assertEquals(0, launched)
            assertFalse(prefs.inAppReviewRequested.first())
        }

    @Test
    fun at_threshold_first_time_launches_and_sets_flag() =
        runTest {
            val prefs = FakeUserPreferences()
            var launched = 0
            trigger(prefs) { launched++ }.onObservationSaved(3)
            assertEquals(1, launched)
            assertTrue(prefs.inAppReviewRequested.first())
        }

    @Test
    fun does_not_launch_again_once_requested() =
        runTest {
            val prefs = FakeUserPreferences()
            prefs.setInAppReviewRequested(true)
            var launched = 0
            trigger(prefs) { launched++ }.onObservationSaved(5)
            assertEquals(0, launched)
        }

    @Test
    fun fires_only_once_across_many_saves() =
        runTest {
            val prefs = FakeUserPreferences()
            var launched = 0
            val t = trigger(prefs) { launched++ }
            t.onObservationSaved(3)
            t.onObservationSaved(4)
            t.onObservationSaved(9)
            assertEquals(1, launched)
        }
}
