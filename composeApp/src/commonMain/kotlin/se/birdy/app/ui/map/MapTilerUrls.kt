package se.birdy.app.ui.map

/*
 * MapTiler raster tiles, shown exactly as MapTiler serves them. MapTiler's terms §4.4 forbid
 * modifying map content "in the form of vectors, pixels or underlying metadata" on every plan, so
 * the map's look comes from the style alone (legal review 2026-10, 7i-fix G): Albin's own style
 * made in MapTiler Customize in the app's paper and sepia tones, by its id from the build
 * configuration (Android: the Gradle property MAPTILER_STYLE_ID, into BuildConfig; iOS:
 * MAPTILER_STYLE_ID in iosApp/Local.xcconfig, via Info.plist), else [DEFAULT_MAPTILER_STYLE_ID].
 * Both platforms build their tile URLs here, so they can't drift apart.
 */

/**
 * MapTiler's stock "Landscape" style, used when no style id is configured. Picked 2026-10-07 by
 * looking at unfiltered sample tiles over southern Sweden (z5 to z16) of toner-v2, toner-v2-lite,
 * dataviz-light, backdrop, landscape, topo-v2, outdoor-v2, basic-v2-light, streets-v2-light,
 * pastel, winter-v2 and aquarelle: its cream land, sage woods, pale blue water and brown labels
 * are the closest to the app's paper (MossCreme) and sepia ink, and the rust and cream wax-seal
 * pins stand out on it. Toner is black and white (a black sea), the light grey styles look cold
 * next to the paper, topo/outdoor/winter are saturated (outdoor's red trails compete with the
 * pins) and aquarelle has no place names and ~4x the tile size.
 */
const val DEFAULT_MAPTILER_STYLE_ID: String = "landscape"

// A bare style id: MapTiler's stock ids ("landscape", "toner-v2") and custom ones (a UUID).
private val STYLE_ID = Regex("[A-Za-z0-9_-]+")

/**
 * The style to load: [configured] when it is a bare style id, else [DEFAULT_MAPTILER_STYLE_ID]
 * (unset, blank, or something that would break every tile request, like a pasted style URL).
 */
fun mapTilerStyleId(configured: String?): String {
    val id = configured?.trim().orEmpty()
    return if (STYLE_ID.matches(id)) id else DEFAULT_MAPTILER_STYLE_ID
}

/** Where [styleId]'s raster tiles live; [mapTilerTileUrl] appends `z/x/y@2x.png?key=…`. */
fun mapTilerTileBaseUrl(styleId: String): String = "https://api.maptiler.com/maps/$styleId/"

/**
 * One tile: 512 px tiles at `@2x` (crisp on high-DPI phones, a quarter of the tile count of 256 px),
 * the same shape for Android's osmdroid tile source and iOS's tile overlay.
 */
fun mapTilerTileUrl(
    styleId: String,
    z: Long,
    x: Long,
    y: Long,
    apiKey: String,
): String = "${mapTilerTileBaseUrl(styleId)}$z/$x/$y@2x.png?key=$apiKey"

/**
 * osmdroid caches tiles on disk under the tile source's name, so the name carries the style id: a
 * new style never shows tiles cached from another one. (iOS caches by URL, which has the id.)
 */
fun mapTilerTileSourceName(styleId: String): String = "MapTiler-$styleId-Retina"
