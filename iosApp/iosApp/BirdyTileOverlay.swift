import MapKit
import ComposeApp

/// Swift-halvan av MapKit-tile-bron. Kotlin/Native kan INTE subklassa `MKTileOverlay` —
/// `loadTile(at:result:)` och `url(forTilePath:)` är båda `final` i den ObjC-plattforms-
/// bindning K/N 2.1.20 länkar (klib-verifierat, se `IosTileFetcher`s KDoc i ComposeApp för
/// den fulla bakgrunden). Den här filen äger därför den faktiska subklassen och gör
/// ingenting annat än att vidarebefordra varje tile-laddning till `IosTileFetcher` — all
/// logik (URL, nätverk, disk-cache, duotone-tint) lever på Kotlin-sidan.
final class BirdyTileOverlay: MKTileOverlay {
    init() {
        super.init(urlTemplate: nil)
        canReplaceMapContent = true
        tileSize = CGSize(width: 512, height: 512)
        minimumZ = 0
        maximumZ = 20
    }

    override func loadTile(at path: MKTileOverlayPath, result: @escaping (Data?, Error?) -> Void) {
        IosTileFetcher.shared.fetch(z: Int64(path.z), x: Int64(path.x), y: Int64(path.y)) { data, error in
            result(data, error)
        }
    }
}
