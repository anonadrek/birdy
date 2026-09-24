package se.birdy.domain.premium

/** Thrown by [PremiumRepository.restore] when the platform billing service could not be
 * reached (e.g. Play unreachable) — as opposed to a successful query that simply found nothing.
 */
class BillingUnavailableException : Exception("Google Play billing unavailable")
