package se.birdy.datastore

import se.birdy.domain.premium.DebugPremiumOverrides
import se.birdy.domain.premium.PremiumRepository

actual class PremiumStateStore actual constructor(
    platformContext: Any?,
) {
    actual fun repository(): PremiumRepository = throw NotImplementedError("iOS PremiumStateStore not implemented in v1 (Android-only)")

    actual fun debugOverrides(): DebugPremiumOverrides =
        throw NotImplementedError("iOS PremiumStateStore not implemented in v1 (Android-only)")
}
