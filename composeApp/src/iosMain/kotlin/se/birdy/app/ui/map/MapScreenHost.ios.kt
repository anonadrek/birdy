@file:OptIn(ExperimentalForeignApi::class, ExperimentalResourceApi::class)

package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import birdy_bird_scanner.composeapp.generated.resources.Res
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.CoreGraphics.CGPointMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSLog
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapPointForCoordinate
import platform.MapKit.MKMapRectMake
import platform.MapKit.MKMapRectNull
import platform.MapKit.MKMapRectUnion
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
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIImage
import platform.darwin.NSObject
import se.birdy.app.toNSData
import se.birdy.app.ui.photoanalyze.uiImageFromDataOrNull

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
 *
 * **T5 (vaxsigill-pins + kamera):** varje [MapPin] blir en [BirdyPinAnnotation] vars
 * `MKAnnotationView` ritas med [buildBirdySealMarkerImage] — samma [MapMarkerSpec]-geometri/
 * palett som Android (`MapMarkerIcon.android.kt`), så plattformarna inte kan divergera. Bilden
 * laddas asynkront (bundlad `hero_bird.png` avkodad + rendrerad på `Dispatchers.Default`);
 * eftersom MapKit cachar annotation-vyer per annotation-OBJEKT och bara frågar delegaten på
 * nytt när en annotation LÄGGS TILL — inte när [BirdyMapDelegate.sealImage] muterar — tvingas
 * en omritning genom att nollställa + återlägga ALLA annotationer så fort bilden blir klar
 * (`LaunchedEffect(pins, sealImage)`, speglar Androids `LaunchedEffect(pins, sealIcon)`-mönster
 * i `MapScreenHost.android.kt`). Kameran centrerar på en ~4 km region för en enda pin, eller
 * unions-rektangeln (+48pt padding, ≈ Androids 96px @2x) för flera.
 */
@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) {
    var sealImage by remember { mutableStateOf<UIImage?>(null) }
    val birdyDelegate = remember { BirdyMapDelegate(sealImage = null, onPinClick = onPinClick) }
    // onPinClick kan byta identitet mellan recompositions (ny lambda-instans från hosten) —
    // delegaten själv skapas bara en gång (remember), så fältet synkas imperativt här.
    SideEffect { birdyDelegate.onPinClick = onPinClick }

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

    // Fågelsilhuetten är en bundlad PNG (samma resurs som Android-markören) — avkodas +
    // rendreras till en färdig vaxsigill-bild en gång per host-instans, inte per pin.
    LaunchedEffect(Unit) {
        sealImage =
            withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/branding/hero_bird.png")
                val bird = uiImageFromDataOrNull(bytes.toNSData()) ?: return@withContext null
                buildBirdySealMarkerImage(bird)
            }
    }

    // Nyckad på BÅDE pins och sealImage (speglar Androids LaunchedEffect(pins, sealIcon)):
    // sealImage är null vid första körningen (bilden laddar fortfarande asynkront ovan) — se
    // klass-KDoc:en för varför nollställning + återläggning krävs när den blir klar.
    LaunchedEffect(pins, sealImage) {
        birdyDelegate.sealImage = sealImage
        mapView.removeAnnotations(mapView.annotations)
        pins.forEach { pin ->
            mapView.addAnnotation(
                BirdyPinAnnotation(observationId = pin.observationId, stampNumber = pin.stampNumber).apply {
                    setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                },
            )
        }
        if (pins.size == 1) {
            val c = CLLocationCoordinate2DMake(pins[0].latitude, pins[0].longitude)
            mapView.setRegion(MKCoordinateRegionMakeWithDistance(c, 4000.0, 4000.0), animated = false)
        } else if (pins.size > 1) {
            var rect = MKMapRectNull.readValue()
            pins.forEach { pin ->
                val point = MKMapPointForCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
                rect = MKMapRectUnion(rect, point.useContents { MKMapRectMake(x, y, 0.1, 0.1) })
            }
            // 48pt ≈ Androids 96px @2x-padding (zoomToBoundingBox(..., 96) i MapScreenHost.android.kt).
            mapView.setVisibleMapRect(rect, edgePadding = UIEdgeInsetsMake(48.0, 48.0, 48.0, 48.0), animated = false)
        }
    }

    // Delegaten släpps med composablen; MKMapView själv ägs av `remember` och dör med den.
    DisposableEffect(Unit) {
        onDispose { mapView.delegate = null }
    }
}

internal class BirdyPinAnnotation(
    val observationId: String,
    val stampNumber: Int,
) : MKPointAnnotation()

// Top-level, INTE i en companion object: K/N tillåter inte fält i companion object för en
// subklass av en ObjC-typ ("Fields are not supported for Companion of subclass of ObjC type",
// kompilatorfel verifierat mot BirdyMapDelegate : NSObject() i denna task).
private const val SEAL_PIN_REUSE_ID = "birdySealPin"

internal class BirdyMapDelegate(
    var sealImage: UIImage?,
    var onPinClick: (String) -> Unit,
) : NSObject(),
    MKMapViewDelegateProtocol {
    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol,
    ): MKOverlayRenderer =
        (rendererForOverlay as? MKTileOverlay)?.let { MKTileOverlayRenderer(tileOverlay = it) }
            ?: MKOverlayRenderer(overlay = rendererForOverlay)

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol,
    ): MKAnnotationView? {
        val annotation = viewForAnnotation as? BirdyPinAnnotation ?: return null
        val view =
            mapView.dequeueReusableAnnotationViewWithIdentifier(SEAL_PIN_REUSE_ID)
                ?: MKAnnotationView(annotation = annotation, reuseIdentifier = SEAL_PIN_REUSE_ID)
        view.annotation = annotation
        val image = sealImage
        if (image != null) {
            view.image = image
            // Ankra bottom-center: vyn centreras på koordinaten som default; skjut upp halva höjden
            // så spetsens topp hamnar på fyndet (Androids ANCHOR_CENTER/ANCHOR_BOTTOM).
            view.centerOffset = CGPointMake(0.0, -image.size.useContents { height } / 2.0)
        }
        return view
    }

    override fun mapView(
        mapView: MKMapView,
        didSelectAnnotationView: MKAnnotationView,
    ) {
        val annotation = didSelectAnnotationView.annotation as? BirdyPinAnnotation ?: return
        mapView.deselectAnnotation(annotation, animated = false)
        onPinClick(annotation.observationId)
    }
}
