package se.birdy.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot device location via the platform LocationManager — no Google Play Services.
 * Returns null when permission is missing, no provider is enabled, or no fix arrives in time.
 *
 * API 30+ callbacks run on [ContextCompat.getMainExecutor], not a fresh
 * `Executors.newSingleThreadExecutor()` per call. A per-call pool leaked a live thread
 * on every successful geotagged save: `shutdown()` ran only in `invokeOnCancellation`,
 * and a `ThreadPoolExecutor` core thread never times out by default. Active users could
 * accumulate hundreds of threads (~1 MB stack each) and OOM.
 */
class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {
    override suspend fun current(): LatLng? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider =
            when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return null
            }
        val location =
            withTimeoutOrNull(8_000L) {
                requestSingleFix(lm, provider)
            } ?: lastKnown(lm)
        return location?.let { LatLng(it.latitude, it.longitude) }
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleFix(
        lm: LocationManager,
        provider: String,
    ): Location? =
        suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                cont.invokeOnCancellation { signal.cancel() }
            } else {
                val listener =
                    object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            if (cont.isActive) cont.resume(loc)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(
                            p: String?,
                            s: Int,
                            e: android.os.Bundle?,
                        ) {}

                        override fun onProviderEnabled(p: String) {}

                        override fun onProviderDisabled(p: String) {}
                    }
                lm.requestSingleUpdate(provider, listener, null)
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }

    @Suppress("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
