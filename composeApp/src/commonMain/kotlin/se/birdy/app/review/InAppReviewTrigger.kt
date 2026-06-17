package se.birdy.app.review

import kotlinx.coroutines.flow.first
import se.birdy.datastore.UserPreferences

/**
 * Decides whether to fire the Google Play in-app review prompt.
 *
 * Fires ONCE, at a proven success moment: when the user has saved at least
 * [threshold] finds and we have not asked before. The actual prompt launch is
 * platform-specific and injected as [launchReview] — a no-op on non-Android and
 * silently no-op when the app isn't installed from Play (debug/sideload).
 *
 * Privacy: uses only the local DataStore one-shot flag — no analytics, no backend.
 * The Play In-App Review API itself exposes no developer-visible data and is
 * rate-limited by Play, so it never breaks Birdy's "data stays on the phone" promise.
 */
class InAppReviewTrigger(
    private val prefs: UserPreferences,
    private val launchReview: () -> Unit,
    private val threshold: Int = 3,
) {
    suspend fun onObservationSaved(totalObservationCount: Int) {
        if (totalObservationCount < threshold) return
        if (prefs.inAppReviewRequested.first()) return
        prefs.setInAppReviewRequested(true)
        launchReview()
    }
}
