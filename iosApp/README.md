# Birdy iOS

Generated Xcode project — edit `project.yml`, then regenerate with `xcodegen generate`
(binary in `~/.local/bin`). The `Compile Kotlin Framework` build phase builds the
`ComposeApp` framework via Gradle (needs JDK 21 at `~/.local/java21`).

Open `Birdy.xcodeproj`, scheme `Birdy`, and run on a simulator (iOS 16+).
Status: plan i0 — boots with real encyclopedia data, FakeClassifier scanning,
in-memory preferences. See `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`.
