# Birdy iOS (v2) — Design

**Date:** 2026-07-07
**Status:** Approved in brainstorming session (Mac), pending implementation plans
**Owner:** Albin (solo dev, via Claude Code)

## 1. Goal & success criteria

Ship a **feature-identical** Birdy on the Apple App Store, built from the same repo and the
same shared Kotlin Multiplatform code as the Android app. "Identical" means feature parity
with Android v1.2 — no new features are added during this track.

- **End goal:** Birdy on the App Store with the full v1.2 feature set (scan photo/audio,
  encyclopedia, field journal, gamification, private finds map, premium tier).
- **Milestone 1 (first target):** core flows — encyclopedia + field journal + live photo
  scanning — running on Albin's physical iPhone (15/16/17-class device). Reached at the end
  of plan i2.
- **Premium:** real StoreKit purchases from day 1 of the App Store release (decision
  2026-07-07). iOS launches with the paywall active, unlike the Android launch period.

## 2. Current state (verified 2026-07-07)

- `iosApp/` is a 2-file Swift stub ("wired up in v2"). **No KMP module declares iOS targets
  yet** — `iosArm64`/`iosSimulatorArm64` are absent from every `build.gradle.kts`.
- Two iOS actuals already exist: `shared/datastore` `UserPreferencesStore.ios.kt` +
  `PremiumStateStore.ios.kt`.
- ~23 `expect` declarations need iOS actuals (see §5).
- All UI and business logic is in common Compose Multiplatform / Kotlin code and renders
  identically on iOS by construction.
- Mac dev environment is fresh: Node 22 present; **Xcode (only CLI tools), JDK, Android SDK
  and Homebrew are missing.** No Apple Developer account yet.
- Android pending debt that intersects this track: TFLite → **LiteRT** migration required
  before the next Android release (Google Play 16 KB page-size gate). Decision 2026-07-07:
  do that migration first, inside this track (plan i2), so iOS ML is built on the new
  library and Android becomes release-ready as a side effect.

## 3. Approach: vertical plans (i0–i6)

Same model as the Android v1 build: sequential plans, each leaving the repo buildable and
testable (`./gradlew build` green, Android untouched or intentionally improved). Hardware
and ML risks are taken early.

| # | Plan | Scope | Exit criteria |
|---|---|---|---|
| i0 | Environment + iOS ignition | Mac toolchain (Xcode, JDK 21, Android SDK); add iOS targets to all KMP modules; minimal `iosApp` Xcode project embedding the Compose framework; app boots in simulator with `FakeClassifier`; start Apple Developer enrollment (user) | Simulator boot; Android CI still green |
| i1 | Encyclopedia + journal on device | SQLDelight native driver; bundled species images; light actuals (share/mailto/openUrl/locale/back handler/photo storage/speciesImageUri); install on Albin's iPhone | Browse 839 species + save/browse journal entries on the physical iPhone |
| i2 | LiteRT migration + photo scanning | Migrate Android ML to LiteRT (16 KB fix, device-regression-tested); AVFoundation camera actual (3 fps, zoom 1–10×); photo-ID on iOS; photo upload + crop/rotate | **Milestone 1**: live camera scan → match flow on iPhone; Android vC126-ready |
| i3 | Audio-ID | Research spike: BirdNET Select-TF-ops packaging on iOS; AVAudioEngine capture (48 kHz mono, 3 s); BirdNET runner actual | Push-to-record audio ID on iPhone; license guard extended to iOS |
| i4 | Parity sweep | Map (MapLibre iOS + MapTiler tiles + Field Journal style); notifications (daily bird, weekly recap) via UNUserNotificationCenter/BGTaskScheduler; PDF export (UIGraphicsPDFRenderer); remaining odds and ends | Every v1.2 feature works on iPhone |
| i5 | StoreKit 2 | `PremiumBillingClient` iOS actual: monthly/yearly/lifetime products, purchase + restore + entitlement check; paywall active on iOS | Sandbox-verified purchases; premium gates behave as on Android |
| i6 | App Store release | Icons/launch screen; TestFlight; store listing (reuse SV/EN Play texts); privacy labels; screenshots on iPhone; review submission | App approved and live on the App Store |

## 4. Architecture

- **Build chain:** every shared module + `composeApp` gains `iosArm64` and
  `iosSimulatorArm64` targets. `composeApp` is exported as a single umbrella Apple
  framework which the `iosApp` Xcode project embeds (direct integration via
  `embedAndSignAppleFrameworkForXcode`; CocoaPods only if a dependency forces it).
- **Swift layer stays minimal** — the iOS counterpart of `MainActivity`: app entry,
  `ComposeUIViewController` host, platform wiring. Target: ≤ ~10 small Swift files.
- **UI is the shared Compose UI**, pixel-identical Field Journal theme. Platform
  conventions are respected where the OS owns the interaction: swipe-back
  (`PlatformBackHandler` actual), iOS share sheets, iOS permission dialogs.
- **Android is not functionally touched**, with one intentional exception: the LiteRT
  migration in i2. All other changes are new files in `iosMain`/`iosApp` or additive build
  config.
- **CI:** add a macOS job (free for public repos) that builds the iOS framework and runs
  common tests on the iOS simulator. Existing Android jobs unchanged.

## 5. Platform component map

| Capability | Android today | iOS solution |
|---|---|---|
| Live camera scan (3 fps, zoom 1–10×) | CameraX | AVFoundation (`AVCaptureSession`), same throttle + zoom presets |
| Photo ML (AIY Birds V1, 965 classes) | TFLite → LiteRT (i2) | LiteRT (or TFLite iOS pod if LiteRT iOS packaging lags — expect/actual isolates the choice); **same model file** |
| Audio ML (BirdNET-Lite, FlexRFFT) | TFLite + select-tf-ops | Select-TF-ops pod for iOS; packaging confirmed in i3 spike |
| Audio capture (48 kHz mono, 3 s) | AudioRecord | AVAudioEngine, same PCM format |
| Database | SQLDelight Android driver | SQLDelight native driver, same queries/migrations |
| Species images (2 060 WebP, ~326 MB) | Install-time Play asset pack | Bundled in app resources (WebP native since iOS 14); `speciesImageUri` actual → bundle path |
| PDF export | Android `PdfDocument` | `UIGraphicsPDFRenderer`, same page layout |
| Finds map | osmdroid + MapTiler + runtime `ColorMatrix` duotone | MapLibre Native iOS + **same MapTiler tiles**; paper/sepia duotone rebuilt in map style (not pixel-guaranteed; pins/UI identical) |
| Purchases | Play Billing v8 | StoreKit 2 (`Transaction.currentEntitlements`), same 3 products |
| Notifications | WorkManager | UNUserNotificationCenter + BGTaskScheduler |
| Share / mailto / open URL / locale / store listing link | Android intents | UIActivityViewController, `mailto:` URL, `UIApplication.open`, App Store link |

## 6. Hard constraints (inherited — apply equally on iOS)

1. **BirdNET-Lite is CC BY-NC-SA 4.0** — audio-ID stays free on iOS forever.
   `BirdNetLicenseGuardTest` is extended to cover iOS gating.
2. **Privacy promise** — no telemetry, all data on device. App Store privacy labels:
   "Data Not Collected".
3. **Parity, not parity-plus** — no new features in this track; ideas go to the backlog.
4. **Android stays shippable** — every plan ends with Android tests green.
5. **Deliberate platform difference at launch:** iOS ships with the paywall active
   (StoreKit day 1); Android's billing flip is a separate track (existing runbook
   `2026-05-26-billing-verify-and-go-live.md`), recommended around the same time to avoid
   free-premium on one platform and paid on the other.
6. **Minimum iOS version: 16.0** (StoreKit 2 needs 15+, effectively all active iPhones
   covered, test device far above).

## 7. Error handling

- **Permissions** (camera, microphone, location, notifications): `Info.plist` usage
  strings + graceful denial states mirroring the Android runtime-permission flows.
- **ML failures / low confidence** → existing NoBird / disambiguation flows (common code).
- **StoreKit errors** → the error + Restore Purchases states already designed for Android
  billing.
- **Map/tile network failures** → same cached-tiles/empty-state behavior as Android.

## 8. Testing & verification

- Existing JVM/Android unit tests unchanged; common tests additionally run on
  `iosSimulatorArm64` where practical.
- Per-plan **device verify on Albin's iPhone** (protocol mirroring the SM-S918B one) +
  two-step review (spec conformance → quality) between tasks, as always.
- i2 includes Android device regression of the LiteRT migration (16 KB alignment verified
  on the built artifacts).

## 9. Environment prerequisites (i0 detail)

User actions (cannot be automated):
1. Install **Xcode** from the App Store (Apple ID) + accept license.
2. Admin password when the JDK (Temurin 21) is installed.
3. **Apple Developer Program enrollment** (99 USD/yr). Recommendation: enroll as an
   individual now (mirrors the Play Console strategy); Apple supports app transfer to the
   AB later. A free Apple ID suffices for on-device installs until StoreKit work begins.

Agent actions: JDK 21, Android SDK cmdline-tools (keep Android builds working on this Mac),
iOS simulator runtime, project bootstrap per `docs/mac-bootstrap.md` (adapted — no Homebrew
on this machine; install tools directly as was done for `gh`).

## 10. Risks & mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| BirdNET Select-TF-ops packaging on iOS (with LiteRT) | **High** | Dedicated spike first in i3; fallback: iOS uses the TFLite select-ops pod while Android uses LiteRT — expect/actual isolates the dependency |
| LiteRT iOS distribution format unclear (pods/SPM/XCFramework) | Medium | Confirm during i2 research; same fallback as above |
| MapLibre duotone ≠ pixel-identical to Android ColorMatrix | Low | Rebuild look in MapTiler style JSON; accept "very close"; verify side by side on devices |
| App download size ~450 MB with bundled images | Medium | Acceptable for launch; On-Demand Resources as a later optimization if App Store data or reviews push back |
| Compose-on-iOS behavior gaps (scroll feel, text input, a11y) | Medium | Device verify per plan; fix per screen; min iOS 16 keeps API surface modern |
| LiteRT regression on Android | Medium | i2 runs the full Android device-verify + 16 KB alignment check before any Android release |

## 11. Out of scope

- Flipping Android billing on (separate runbook/track).
- Geographic content expansion (Asia etc.) — separate v2 track.
- Cloud accounts/sync, community features, quiz mode.
- Any new feature not in Android v1.2.
