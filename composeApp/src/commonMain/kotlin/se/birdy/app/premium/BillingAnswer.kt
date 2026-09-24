package se.birdy.app.premium

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Waits until Play Billing has answered the purchase query. Automatic paywalls must never be
 * shown on a guess: a paying subscriber looks Free until Play answers. Returns false on timeout
 * (Play unreachable) so the caller skips the paywall for this launch.
 */
suspend fun awaitBillingAnswer(
    purchasesQueried: StateFlow<Boolean>,
    timeoutMs: Long,
): Boolean = withTimeoutOrNull(timeoutMs) { purchasesQueried.first { it } } != null
