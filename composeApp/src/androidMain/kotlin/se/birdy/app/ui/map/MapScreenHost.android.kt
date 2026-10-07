package se.birdy.app.ui.map

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import birdy_bird_scanner.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
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

// 512px @2x ("retina") tiles render crisp on high-DPI phones and cut the tile
// count ~4× vs 256px, so the map is sharper AND fills faster. MapTiler exposes
// HiDPI via the "@2x.png" suffix. The tiles are drawn exactly as MapTiler serves them
// (terms §4.4): the look is the style's, see MapTilerUrls.kt. The source name carries the
// style id, so osmdroid's on-disk cache never mixes tiles from two styles.
private const val MAPTILER_TILE_SIZE = 512

/** Space kept free around the pins when several finds are fitted on screen. */
private const val FIT_BORDER_PX = 96

/** Alpha byte for the 24-bit RGB [MapTileTheme.PAPER]. */
private const val OPAQUE: Int = 0xFF shl 24

private fun mapTilerSource(
    styleId: String,
    apiKey: String,
): OnlineTileSourceBase =
    object : XYTileSource(
        mapTilerTileSourceName(styleId),
        0,
        MAP_TILE_MAX_ZOOM.toInt(),
        MAPTILER_TILE_SIZE,
        "@2x.png",
        arrayOf(mapTilerTileBaseUrl(styleId)),
        "© MapTiler © OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            mapTilerTileUrl(
                styleId = styleId,
                z = MapTileIndex.getZoom(pMapTileIndex).toLong(),
                x = MapTileIndex.getX(pMapTileIndex).toLong(),
                y = MapTileIndex.getY(pMapTileIndex).toLong(),
                apiKey = apiKey,
            )
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
                tileDownloadThreads = 8 // default 2 — parallel fetch for faster cold-cache fill
            }
            MapView(context).apply {
                setTileSource(
                    mapTilerSource(
                        styleId = mapTilerStyleId(BuildConfig.MAPTILER_STYLE_ID),
                        apiKey = BuildConfig.MAPTILER_API_KEY,
                    ),
                )
                // Never zoom past the tiles (osmdroid's own limit is 29, from its tile
                // approximator), neither by pinching nor by a fit (Plan 3 Task 7 review).
                maxZoomLevel = MAP_TILE_MAX_ZOOM
                setMultiTouchControls(true)
                setUseDataConnection(true)
                // Paper, not osmdroid's grey grid, where a tile hasn't loaded yet. Our own
                // placeholder: the tiles themselves are never touched.
                val paper = MapTileTheme.PAPER or OPAQUE
                overlayManager.tilesOverlay.loadingBackgroundColor = paper
                overlayManager.tilesOverlay.loadingLineColor = paper
            }
        }

    var sealIcon by remember { mutableStateOf<BitmapDrawable?>(null) }
    @OptIn(ExperimentalResourceApi::class)
    LaunchedEffect(Unit) {
        sealIcon =
            withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/branding/hero_bird.png")
                val bird = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null
                buildBirdySealMarker(context.resources, bird)
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

    LaunchedEffect(pins, sealIcon) {
        mapView.overlays.clear()
        val icon = sealIcon
        pins.forEach { pin ->
            val marker =
                Marker(mapView).apply {
                    position = GeoPoint(pin.latitude, pin.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "#${pin.stampNumber}"
                    if (icon != null) this.icon = icon
                    setOnMarkerClickListener { _, _ ->
                        onPinClick(pin.observationId)
                        true
                    }
                }
            mapView.overlays.add(marker)
        }
        // Finds at one spot are centred, never fitted: a zero-size box made osmdroid zoom to 29,
        // far past the tiles, and the map opened empty (see MapStartView).
        when (val start = mapStartView(pins)) {
            null -> Unit
            is MapStartView.Centre -> {
                mapView.controller.setZoom(SINGLE_SPOT_ZOOM)
                mapView.controller.setCenter(GeoPoint(start.latitude, start.longitude))
            }
            is MapStartView.Fit -> {
                val box = BoundingBox(start.north, start.east, start.south, start.west)
                mapView.post { mapView.zoomToBoundingBox(box, false, FIT_BORDER_PX, FIT_MAX_ZOOM, null) }
            }
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
