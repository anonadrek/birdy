@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.map

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIContextWorkingColorSpace
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSLog
import platform.Foundation.NSNull
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import platform.Foundation.setValue
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import se.birdy.app.ui.photoanalyze.uiImageFromDataOrNull
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
 * logik. All logik (URL-byggnad, nätverk, disk-cache, duotone-tint) lever här, oförändrad
 * i SEMANTIK från spikens ursprungliga `MapTilerTileOverlay.loadTileAtPath`-kod.
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

    // En delad CIContext — dyr att skapa, trådsäker att använda. `kCIContextWorkingColorSpace
    // to NSNull()` stänger av Core Images DEFAULT ljus-linjära arbetsfärgrymd: utan detta
    // konverterar CIColorMatrix-filtret till linjärt ljus FÖRE matrisen appliceras, medan
    // Androids ColorMatrixColorFilter (samma matris, se MapTileTheme) applicerar direkt på
    // gamma-kodade sRGB-byte — samma koefficienter gav därför synbart mörkare mellantoner på
    // iOS än på Android (T4-review, 2026-08-17). NSNull som värde är CoreImages dokumenterade
    // sätt att be om "ingen färgrymds-hantering, kör i tilens egen kodade rymd" — matchar nu
    // Android-gamma-parititeten. Vektorerna beror bara på MapTileTheme-konstanterna, så de
    // räknas ut en gång och återanvänds för varje tile.
    private val ciContext = CIContext.contextWithOptions(mapOf(kCIContextWorkingColorSpace to NSNull()))
    private val vectors = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))

    // Löses EN gång per process, inte per tile: MapTilerKey.value() gör en Info.plist-lookup
    // + loggar en "saknas"-varning per anrop, och en enda pan/zoom kan begära dussintals tiles
    // direkt (T4-review, 2026-08-17 — förra rundans skärmdump gav 24 identiska varningsrader
    // för EN statisk viewport).
    private val apiKey: String by lazy { MapTilerKey.value() }

    /**
     * Hämtar + tintar tilen på `z/x/y`. Anropas av `BirdyTileOverlay.swift`s
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
        val url = NSURL.URLWithString(mapTilerTileUrl(z = z, x = x, y = y, apiKey = apiKey))
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
                if (data == null) {
                    completion(null, error)
                } else {
                    completion(tinted(data) ?: data, null)
                }
            }.resume()
    }

    /** Duotone-tint; null vid dekodfel → råtilen används hellre än ett hål i kartan. */
    private fun tinted(data: NSData): NSData? {
        val source = uiImageFromDataOrNull(data)?.CIImage ?: CIImage.imageWithData(data) ?: return null
        val filter = CIFilter.filterWithName("CIColorMatrix") ?: return null
        filter.setValue(source, forKey = "inputImage")

        fun vec(v: FloatArray) = CIVector.vectorWithX(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), v[3].toDouble())
        filter.setValue(vec(vectors.r), forKey = "inputRVector")
        filter.setValue(vec(vectors.g), forKey = "inputGVector")
        filter.setValue(vec(vectors.b), forKey = "inputBVector")
        filter.setValue(vec(vectors.a), forKey = "inputAVector")
        filter.setValue(vec(vectors.bias), forKey = "inputBiasVector")
        val output = filter.outputImage ?: return null
        val cg = ciContext.createCGImage(output, fromRect = output.extent) ?: return null
        return UIImagePNGRepresentation(UIImage.imageWithCGImage(cg))
    }
}
