import SwiftUI
import ComposeApp

@main
struct BirdyApp: App {
    init() {
        // K/N kan inte subklassa MKTileOverlay (se BirdyTileOverlay.swift) — registrera
        // Swift-subklassens factory så Kotlin-hosten (MapScreenHost.ios.kt) kan skapa en
        // utan att själv känna till Swift-typen.
        IosMapOverlayBridge.shared.overlayFactory = { BirdyTileOverlay() }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
