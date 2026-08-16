package se.birdy.app.ui.map

import platform.MapKit.MKTileOverlay

/**
 * Bro åt andra hållet av [IosTileFetcher]: Swift äger `MKTileOverlay`-subklassen
 * (`BirdyTileOverlay`, K/N kan inte subklassa den — se [IosTileFetcher]-KDoc), men Kotlin-
 * hosten ([MapScreenHost] iOS-actual) behöver kunna instansiera en utan att själv känna till
 * Swift-typen. `iosApp/iosApp/iOSApp.swift` registrerar [overlayFactory] vid appstart
 * (`IosMapOverlayBridge.shared.overlayFactory = { BirdyTileOverlay() }`); hosten läser den
 * här. Null (aldrig registrerad, t.ex. om Swift-uppstarten hoppas över i en framtida
 * test-target) tolkas som "ingen overlay tillgänglig" — degrade till Apples baskarta,
 * aldrig en krasch.
 */
object IosMapOverlayBridge {
    var overlayFactory: (() -> MKTileOverlay)? = null
}
