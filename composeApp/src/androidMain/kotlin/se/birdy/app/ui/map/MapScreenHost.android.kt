package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import se.birdy.app.BuildConfig
import java.io.File

private fun mapTilerSource(apiKey: String): OnlineTileSourceBase =
    object : XYTileSource(
        "MapTiler-Outdoor",
        0,
        20,
        256,
        ".png",
        arrayOf("https://api.maptiler.com/maps/outdoor-v2/256/"),
        "© MapTiler © OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            getBaseUrl() +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) +
                mImageFilenameEnding + "?key=" + apiKey
    }

@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView =
        remember {
            Configuration.getInstance().apply {
                userAgentValue = context.packageName // REQUIRED or tile servers return 403
                osmdroidBasePath = File(context.cacheDir, "osmdroid")
                osmdroidTileCache = File(osmdroidBasePath, "tiles")
            }
            MapView(context).apply {
                setTileSource(mapTilerSource(BuildConfig.MAPTILER_API_KEY))
                setMultiTouchControls(true)
                setUseDataConnection(true)
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(pins) {
        mapView.overlays.clear()
        val geoPoints =
            pins.map { pin ->
                val point = GeoPoint(pin.latitude, pin.longitude)
                val marker =
                    Marker(mapView).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "#${pin.stampNumber}"
                        setOnMarkerClickListener { _, _ ->
                            onPinClick(pin.observationId)
                            true
                        }
                    }
                mapView.overlays.add(marker)
                point
            }
        if (geoPoints.isNotEmpty()) {
            if (geoPoints.size == 1) {
                mapView.controller.setZoom(13.0)
                mapView.controller.setCenter(geoPoints.first())
            } else {
                mapView.post { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(geoPoints), false, 96) }
            }
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
