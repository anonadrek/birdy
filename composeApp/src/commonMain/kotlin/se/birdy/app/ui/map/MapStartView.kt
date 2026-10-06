package se.birdy.app.ui.map

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

/**
 * How the finds map frames its pins when it opens: one spot is centred at [SINGLE_SPOT_ZOOM],
 * several spots are fitted, never closer than [FIT_MAX_ZOOM].
 *
 * Release 1.3.0 Plan 3 Task 7 (API 36 emulator): two finds saved at the same spot gave a bounding
 * box of zero size, which osmdroid fitted at its own maximum zoom (29, from its tile approximator;
 * the MapTiler source stops at [MAP_TILE_MAX_ZOOM]). The map opened with no tiles at all, only the
 * loading grid. Finds a few metres apart had the same problem, hence the cap on fits too.
 */
internal sealed interface MapStartView {
    data class Centre(
        val latitude: Double,
        val longitude: Double,
    ) : MapStartView

    data class Fit(
        val north: Double,
        val east: Double,
        val south: Double,
        val west: Double,
    ) : MapStartView
}

/** Highest zoom the MapTiler tile source is set up for (MapScreenHost.android). */
internal const val MAP_TILE_MAX_ZOOM = 20.0

/** Zoom for a single spot: a few kilometres around the find. */
internal const val SINGLE_SPOT_ZOOM = 13.0

/** Closest a fit around several spots may zoom: street level, well inside the tile range. */
internal const val FIT_MAX_ZOOM = 16.0

internal fun mapStartView(pins: List<MapPin>): MapStartView? {
    if (pins.isEmpty()) return null
    val north = pins.maxOf { it.latitude }
    val south = pins.minOf { it.latitude }
    val east = pins.maxOf { it.longitude }
    val west = pins.minOf { it.longitude }
    return if (north == south && east == west) {
        MapStartView.Centre(latitude = north, longitude = east)
    } else {
        MapStartView.Fit(north = north, east = east, south = south, west = west)
    }
}

/**
 * Smallest north-south and east-west span (metres) a fit around several finds shows on iOS, about
 * street level like Android's [FIT_MAX_ZOOM]. iOS frames with MKCoordinateRegionMakeWithDistance,
 * and finds a few metres apart used to zoom MapKit to its maximum (Plan 3 Task 7 review).
 */
internal const val FIT_MIN_SPAN_METERS = 1_000.0

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

private const val DEGREES_TO_RADIANS = PI / 180

// Room around the outermost pins, like Android's 96 px border.
private const val FIT_SPAN_MARGIN = 1.3

internal data class FitSpan(
    val centreLatitude: Double,
    val centreLongitude: Double,
    val latitudeMeters: Double,
    val longitudeMeters: Double,
)

/** The centre and span (with margin, never below [FIT_MIN_SPAN_METERS]) that show [fit]. */
internal fun fitSpan(fit: MapStartView.Fit): FitSpan {
    val centreLatitude = (fit.north + fit.south) / 2
    val centreLongitude = (fit.east + fit.west) / 2
    val latitudeMeters = (fit.north - fit.south) * METERS_PER_DEGREE_LATITUDE * FIT_SPAN_MARGIN
    val longitudeMeters =
        (fit.east - fit.west) * METERS_PER_DEGREE_LATITUDE * cos(centreLatitude * DEGREES_TO_RADIANS) * FIT_SPAN_MARGIN
    return FitSpan(
        centreLatitude = centreLatitude,
        centreLongitude = centreLongitude,
        latitudeMeters = max(latitudeMeters, FIT_MIN_SPAN_METERS),
        longitudeMeters = max(longitudeMeters, FIT_MIN_SPAN_METERS),
    )
}
