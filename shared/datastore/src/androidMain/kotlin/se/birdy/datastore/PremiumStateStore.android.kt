package se.birdy.datastore

import se.birdy.domain.premium.DebugPremiumOverrides
import se.birdy.domain.premium.PremiumRepository

actual class PremiumStateStore actual constructor(
    platformContext: Any?,
) {
    actual fun repository(): PremiumRepository =
        throw NotImplementedError("Android PremiumStateStore not implemented yet — replaced in Task 4")

    actual fun debugOverrides(): DebugPremiumOverrides =
        throw NotImplementedError("Android PremiumStateStore not implemented yet — replaced in Task 4")
}
