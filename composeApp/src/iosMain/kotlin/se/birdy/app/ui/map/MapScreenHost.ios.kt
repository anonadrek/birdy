@file:OptIn(ExperimentalForeignApi::class)

package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSLog
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKOverlayLevelAboveLabels
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPointOfInterestFilter
import platform.MapKit.MKTileOverlay
import platform.MapKit.MKTileOverlayRenderer
import platform.MapKit.addOverlay
import platform.darwin.NSObject

/**
 * MKMapView i en UIKitView med MapTiler-tiles (duotone-tintade, se [IosTileFetcher]) via
 * overlayn Swift-sidan registrerar i [IosMapOverlayBridge] vid appstart — K/N kan inte
 * subklassa `MKTileOverlay` själv, se [IosTileFetcher]-KDoc för den fulla bakgrunden. Utan
 * en registrerad overlay (t.ex. Swift-registreringen saknas) degraderas kartan tyst till
 * Apples baskarta i stället för att krascha; loggat via NSLog.
 *
 * `rendererForOverlay` krävs för att overlayn ska synas alls — utan en delegate som svarar
 * med en `MKTileOverlayRenderer` ritas ingenting. Delegaten hålls i en egen [remember] eftersom
 * `MKMapView.delegate` är weak (ObjC-konvention) — utan en stark Kotlin-referens skulle den
 * deallokeras direkt.
 *
 * **Rättelse (T4-review, 2026-08-17):** `addOverlay(overlay)` (utan `level`) lägger overlayn på
 * default-nivån `MKOverlayLevelAboveRoads`, som ligger UNDER Apples etikett-/POI-lager — den
 * första riskgrind-skärmdumpen visade device-lokaliserade stadsnamn/parkbadges/landsgränser
 * (Apples lager, inte vårt) rakt igenom de duotone-tintade tilesen, vilket lästes fel som
 * "tile-innehåll" i förra rapportrundan. Fixat med `MKOverlayLevelAboveLabels` (ritar overlayn
 * OVANPÅ Apples lager) + `pointOfInterestFilter = excludingAll` (bälte-och-hängslen: döljer
 * POI-lagret helt, oavsett nivå).
 */
@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) {
    val birdyDelegate = remember { BirdyMapDelegate() }
    val mapView =
        remember {
            MKMapView().apply {
                delegate = birdyDelegate
                // Bälte-och-hängslen mot Apples POI-lager (se klass-KDoc:en ovan) — oavsett
                // overlay-nivå ska Apple-badges/POI-ikoner aldrig synas på den här kartan.
                pointOfInterestFilter = MKPointOfInterestFilter.filterExcludingAllCategories
                val overlay = IosMapOverlayBridge.overlayFactory?.invoke()
                if (overlay == null) {
                    // Enda arg-formen (ingen "%@" + vararg) — K/N:s NSLog-vararg-marshaling av en
                    // rå Kotlin String kraschar (EXC_BAD_ACCESS i CFStringCreateWithFormat, verifierat
                    // via sim-crash i denna task); se motsvarande kommentar i IosTileFetcher.kt.
                    NSLog(
                        "Birdy/map: ingen tile-overlay registrerad (IosMapOverlayBridge.overlayFactory är null " +
                            "— saknas registreringen i iOSApp.swift?) — visar Apples baskarta",
                    )
                } else {
                    // AboveLabels (inte default AboveRoads) — se klass-KDoc:en ovan för varför.
                    addOverlay(overlay, level = MKOverlayLevelAboveLabels)
                }
            }
        }
    UIKitView(factory = { mapView }, modifier = modifier)
    // Spike-läge: systemets standardpins; vaxsigill + kamera + delegat kommer i nästa task.
    LaunchedEffect(pins) {
        mapView.removeAnnotations(mapView.annotations)
        pins.forEach { pin ->
            mapView.addAnnotation(
                MKPointAnnotation().apply { setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude)) },
            )
        }
    }
}

internal class BirdyMapDelegate :
    NSObject(),
    MKMapViewDelegateProtocol {
    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol,
    ): MKOverlayRenderer =
        (rendererForOverlay as? MKTileOverlay)?.let { MKTileOverlayRenderer(tileOverlay = it) }
            ?: MKOverlayRenderer(overlay = rendererForOverlay)
}
