@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.map

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform

/**
 * Kotlin-halvan av MapKit-tile-bron. K/N kan INTE subklassa `MKTileOverlay` —
 * `loadTileAtPath` OCH `URLForTilePath` är båda `final` i den ObjC-plattformsbindning som
 * länks för Kotlin 2.1.20 (verifierat via `klib dump-metadata` mot den faktiskt länkade
 * platform-klib:en i task-4-rapportens första försök: ingen `open`-variant existerar för
 * någon av de två override-punkterna på `MKTileOverlay`, till skillnad från t.ex.
 * `MKOverlayRenderer.drawMapRect` som är `open`). Lösningen (ruling i task-4-briefen,
 * 2026-08-16): `iosApp/iosApp/BirdyTileOverlay.swift` äger den faktiska
 * `MKTileOverlay`-subklassen (Swift KAN override:a där K/N inte kan) och vidarebefordrar
 * varje `loadTile(at:result:)`-anrop hit via [fetch] — Swift-filen innehåller ingen egen
 * logik. All logik (URL-byggnad, nätverk, disk-cache) lever här.
 *
 * Rutorna lämnas exakt som MapTiler levererar dem (villkoren §4.4 förbjuder att ändra pixlar,
 * vektorer eller metadata, juridikgenomgången 2026-10, 7i-fix G); färgerna kommer från stilen,
 * se MapTilerUrls.kt. Stilens id ingår i URL:en, så NSURLCache blandar aldrig två stilars rutor.
 *
 * Exponeras som `IosTileFetcher.shared` i Swift (K/N `object` → ObjC-singleton-property).
 */
object IosTileFetcher {
    private val session: NSURLSession =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration.apply {
                val cacheDir =
                    (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) +
                        "/map_tiles"
                URLCache =
                    NSURLCache(
                        memoryCapacity = 20uL * 1024uL * 1024uL,
                        diskCapacity = 100uL * 1024uL * 1024uL,
                        directoryURL = NSURL.fileURLWithPath(cacheDir),
                    )
                HTTPAdditionalHeaders = mapOf("User-Agent" to "se.birdy.ios")
            },
        )

    // Löses EN gång per process, inte per tile: MapTilerKey.value() gör en Info.plist-lookup
    // + loggar en "saknas"-varning per anrop, och en enda pan/zoom kan begära dussintals tiles
    // direkt (T4-review, 2026-08-17 — förra rundans skärmdump gav 24 identiska varningsrader
    // för EN statisk viewport).
    private val apiKey: String by lazy { MapTilerKey.value() }

    // Albins egen stil (MAPTILER_STYLE_ID i Local.xcconfig), annars MapTilers standardstil.
    private val styleId: String by lazy { MapTilerKey.styleId() }

    /**
     * Hämtar tilen på `z/x/y`. Anropas av `BirdyTileOverlay.swift`s
     * `loadTile(at:result:)` — samma completion-kontrakt som `MKTileOverlay` förväntar sig
     * (`Data?`/`Error?`), så Swift-sidan kan vidarebefordra `completion` rakt av.
     */
    @OptIn(ExperimentalNativeApi::class)
    fun fetch(
        z: Long,
        x: Long,
        y: Long,
        completion: (NSData?, NSError?) -> Unit,
    ) {
        val url = NSURL.URLWithString(mapTilerTileUrl(styleId = styleId, z = z, x = x, y = y, apiKey = apiKey))
        if (url == null) {
            completion(null, null)
            return
        }
        if (Platform.isDebugBinary) {
            // Enda arg-formen (ingen "%@" + vararg) — se MapTilerKey.ios.kt-kommentaren för
            // varför. Debug-gate:ad (samma mönster som IosAppGraph.kt:s audio-degrade-logg):
            // release ska inte logga VARJE tile av VARJE pan/zoom för alltid — det läcker
            // viewport-koordinater till systemloggen utan att fylla något syfte i produktion
            // (T4-review, 2026-08-17). Sim/device-debugging behåller bevisspåret.
            NSLog("Birdy/map: fetching tile z=$z x=$x y=$y")
        }
        session
            .dataTaskWithURL(url) { data, _, error ->
                // Oförändrad, som MapTiler levererar den (villkoren §4.4).
                completion(data, if (data == null) error else null)
            }.resume()
    }
}
