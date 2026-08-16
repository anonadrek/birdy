package se.birdy.app.ui.map

/** Samma URL-form som Androids XYTileSource i MapScreenHost.android.kt — ändra BÅDA eller ingen. */
fun mapTilerTileUrl(
    z: Long,
    x: Long,
    y: Long,
    apiKey: String,
): String = "https://api.maptiler.com/maps/toner-v2/$z/$x/$y@2x.png?key=$apiKey"
