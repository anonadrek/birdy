# Birdy iOS

Generated Xcode project — edit `project.yml`, then regenerate with `xcodegen generate`
(binary in `~/.local/bin`). The `Compile Kotlin Framework` build phase builds the
`ComposeApp` framework via Gradle (needs JDK 21 at `~/.local/java21`).

Open `Birdy.xcodeproj`, scheme `Birdy`, and run on a simulator (iOS 16+).
Copy `Local.xcconfig.sample` to `Local.xcconfig` and add the MapTiler key for the map.
The pre-build script `tools/fetch_ios_selectops.sh` fetches the SHA-pinned
`TensorFlowLiteSelectTfOps` 2.17.0 for sound ID. It is device only, so the simulator
shows an honest error state (or the DEBUG demo banner) instead of real audio inference.

Status (2026-09-30): plans i0 to i4 are code complete and reviewed: encyclopedia and
journal with persistent preferences, photo ID, live camera, sound ID, map (MapKit via
`BirdyTileOverlay.swift`), notifications and PDF export. Left: simulator and device
checks (`docs/ios-release-checklist.md`), i5 StoreKit 2 and i6 App Store.
See `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`.
