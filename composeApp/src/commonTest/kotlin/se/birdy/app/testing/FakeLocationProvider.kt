package se.birdy.app.testing

import se.birdy.app.location.LatLng
import se.birdy.app.location.LocationProvider

class FakeLocationProvider(
    var next: LatLng? = null,
    var currentCalls: Int = 0,
) : LocationProvider {
    override suspend fun current(): LatLng? {
        currentCalls += 1
        return next
    }
}
