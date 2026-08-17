@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * One-shot plats via CoreLocation — spegel av AndroidLocationProvider-kontraktet:
 * null vid nekad behörighet, timeout (8 s) eller fel; KASTAR ALDRIG (utom
 * [CancellationException], som alltid återkastas — husregeln i CLAUDE.md:s trap-katalog:
 * varje `runCatching` runt ett suspend-anrop måste släppa igenom cancellation, annars äter
 * den tyst en cancellation-signal den inte äger; samma idiom som t.ex. SaveObservationUseCase
 * och App.kt använder redan).
 *
 * Gotchas som styr formen:
 * - CLLocationManager MÅSTE skapas på en tråd med runloop → allt sker på Dispatchers.Main.
 * - manager.delegate är weak → delegaten hålls vid liv av coroutine-closuren tills resume.
 */
class IosLocationProvider : LocationProvider {
    override suspend fun current(): LatLng? =
        runCatching {
            withContext(Dispatchers.Main) {
                val manager = CLLocationManager()
                if (!isAuthorized(manager)) return@withContext null
                manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
                val fix =
                    withTimeoutOrNull(8_000L) { requestSingleFix(manager) }
                        ?: manager.location // lastKnown-fallback, som Android
                fix?.coordinate?.useContents { LatLng(latitude, longitude) }
            }
        }.onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    private fun isAuthorized(manager: CLLocationManager): Boolean =
        manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways

    private suspend fun requestSingleFix(manager: CLLocationManager): CLLocation? =
        suspendCancellableCoroutine { cont ->
            val delegate =
                object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManager(
                        manager: CLLocationManager,
                        didUpdateLocations: List<*>,
                    ) {
                        if (cont.isActive) cont.resume(didUpdateLocations.firstOrNull() as? CLLocation)
                    }

                    override fun locationManager(
                        manager: CLLocationManager,
                        didFailWithError: NSError,
                    ) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            manager.delegate = delegate
            manager.requestLocation()
            cont.invokeOnCancellation {
                // Referera delegate + manager så closuren retainar dem till cancel/resume.
                manager.stopUpdatingLocation()
                manager.delegate = null
                delegate.hashCode()
            }
        }
}

/**
 * Fyrar iOS-platsdialogen. Ingen result-hantering — capture degraderar graciöst
 * (Android-paritet: requestLocationPermLauncher har tom callback).
 * Managern är en retained singleton: släpps den medan dialogen visas fyras callbacken aldrig.
 */
object IosLocationPermissionRequester {
    private val scope = MainScope()
    private var manager: CLLocationManager? = null

    fun request() {
        scope.launch {
            val m = manager ?: CLLocationManager().also { manager = it }
            m.requestWhenInUseAuthorization()
        }
    }
}
