import SwiftUI
import ComposeApp

@main
struct BirdyApp: App {
    init() {
        // K/N kan inte subklassa MKTileOverlay (se BirdyTileOverlay.swift) — registrera
        // Swift-subklassens factory så Kotlin-hosten (MapScreenHost.ios.kt) kan skapa en
        // utan att själv känna till Swift-typen.
        IosMapOverlayBridge.shared.overlayFactory = { BirdyTileOverlay() }

        // MÅSTE köras HÄR, inte i MainViewController (som byggs vid FÖRSTA Compose-
        // composition, dvs efter launch): Apple kräver att UNUserNotificationCenter.delegate
        // är satt innan appen är klar med att starta för att en kallstart-tap på en notis
        // ska levereras alls. Grafen finns inte än — delegaten löser den senare per callback
        // (se IosNotificationLifecycle.kt:s KDoc för hela stash-mekaniken).
        IosNotificationLifecycleKt.installIosNotificationDelegate()
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
