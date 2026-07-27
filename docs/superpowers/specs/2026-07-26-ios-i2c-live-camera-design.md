# Birdy iOS i2c — Live camera + take-photo (Milestone 1 code) — Design

**Date:** 2026-07-26
**Status:** Approved in brainstorming session (Mac)
**Owner:** Albin (solo dev, via Claude Code)
**Parent spec:** `2026-07-07-birdy-ios-v2-design.md` (§3, plan i2c)
**Builds on:** i2b's ML runtime (`IosTfliteRunner`, `ImagePreprocessor.ios`, vendored TensorFlowLiteC 2.17.0)

## 1. Goal & success criteria

Give iOS a real live-camera scan (AVCaptureSession, 3 fps effective, zoom 1–10×) and a
working "Take photo" button in the photo-ID flow, wired into the existing shared
scan/match pipeline. This is the code for **Milestone 1** (live scan on iPhone).

- **Exit criteria (this plan):** code complete, all sim-runnable tests green (including a
  BGRA parity guard against the real model), full Android regression gate green, app
  boots in the simulator with the scan tab rendering the permission flow without crashing.
- **Milestone 1 closes later:** Albin currently has no iPhone available. The physical
  device verify (real camera frames, FOV zoom, freeze→match→save, take-photo,
  permission round-trips, background/foreground) is an **open gate** performed when the
  device exists — same model as i2b's interactive verify. The plan maximizes
  sim-verifiable work and leaves that gate honestly open.

## 2. Current state (verified 2026-07-26)

- `ScanViewModel` (commonMain) is fully platform-agnostic: 3 fps sampling
  (`initialSamplePeriodMs = 333`), auto-throttle to 666 ms at p95 > 333 ms, the
  freeze pair-model (`ClassifiedFrame`), the 2 s freshness guard, and
  `consecutiveErrors > 5 → Error`. **Zero changes needed.**
- The classifier is live since i2b (`buildIosAppGraph().ClassifierBootstrap` →
  `IosTfliteRunner`); `TfLiteBirdClassifier`'s mutex already serializes `classify()`, so a
  frame pump can share the runner instance without refactoring.
- Exactly four iOS stubs stand in for the camera:
  `IosNoopCameraSource` (`IosAppGraph.kt`), `CameraPreviewHost.ios.kt` (`= Unit`),
  `ScanScreenHost.ios.kt` (`IosComingSoonPanel`), and `onTakePhoto = {}` in
  `PhotoAnalyzeHost.ios.kt`.
- `Info.plist` has no usage-description keys; `iosApp/project.yml` is the xcodegen source
  of truth.
- Known traps carried from the i2b final review:
  - **BGRA vs RGBA:** `AVCaptureVideoDataOutput`'s natural pixel format is
    `kCVPixelFormatType_32BGRA` (RGBA output is not supported); `ImagePreprocessor.ios`'s
    `decodeRgba` assumes R,G,B,A order → raw BGRA gives silently channel-swapped
    inference.
  - **`persistFrame` assumes JPEG bytes:** `ScanViewModel.onFreeze` persists
    `snap.frame.bytes` verbatim as a `.jpg`; Android satisfies this by encoding YUV→JPEG
    on *every* frame.
  - `ImageInput.timestampMillis` must be **wall clock** or the 2 s freshness guard marks
    every freeze stale (the Android `imageInfo.timestamp` trap).
- Deferred debt that comes due now: `IosPhotoStorage` persists bytes as-is with the
  comment "deferred to plan i2 — nothing on iOS produces photos until the camera lands
  there". The camera is the first finds-producer on iOS.

## 3. Decisions made in brainstorming (2026-07-26)

1. **Scope = spec core + the `IosPhotoStorage` debt** (1024 px longest-side @ q85 on
   save, same contract as Android). None of i2b's 13 deferred minors are pulled in.
2. **Take-photo = system camera** via `UIImagePickerController(sourceType = .camera)` —
   the iOS analogue of Android's `TakePicture()` intent. Parity, not parity-plus; reuses
   i2b's retained-delegate + `keyWindowRootViewController()` patterns.
3. **Frame pipeline = BGRA end-to-end (approach A).** New `FrameFormat.BGRA_8888`;
   the preprocessor reads B,G,R byte order directly (zero per-frame conversion; the
   channel-swap trap becomes impossible by construction — the code switches on an
   explicit format enum). JPEG encoding happens **only at freeze**, via CGBitmapContext
   (BGRA is CoreGraphics' native layout: `byteOrder32Little + premultipliedFirst`).
   Rejected: per-frame vImage swizzle to RGBA (pays a pass per frame and leaves the trap
   latent) and per-frame JPEG encode à la Android (double JPEG cost on the hot path).
4. **Shared signature change (the only one):** `persistFrame: (ByteArray) -> String`
   becomes `(ImageInput) -> String` in `ScanScreen` + `ScanViewModel.onFreeze`. The
   Android host adapts trivially (`input.bytes` — its frames are already JPEG).
5. **No iPhone available for now** → plan structured for sim verification; the device
   gate stays open (see §1).

## 4. Components

New code mirrors the Android counterpart file-for-file where one exists.

| # | Component | Location | Contents |
|---|---|---|---|
| 1 | `IosCameraSource` | `shared/ml/src/iosMain/kotlin/se/birdy/ml/camera/IosCameraSource.kt` | `CameraSource` impl. AVCaptureSession + `AVCaptureVideoDataOutput` (BGRA, `alwaysDiscardsLateVideoFrames = true` = CameraX `KEEP_ONLY_LATEST`), serial dispatch queue. `start()` configures the back wide-angle camera, portrait-locks connections, starts off-main; `stop()` mirrors Android (`ZoomState.NONE`, teardown). Zoom: `videoZoomFactor` under `lockForConfiguration`, `ZoomState(1f, 1f, maxFromActiveFormat)` — shared `zoomPresets()` already clamps display to 10×. Exposes the session for the preview layer (the `bindPreview` pattern). |
| 2 | `FrameFormat.BGRA_8888` | `shared/ml/src/commonMain/.../BirdClassifier.kt` | Additive enum value. Android never emits it. |
| 3 | BGRA branch in preprocessor | `shared/ml/src/iosMain/.../ImagePreprocessor.ios.kt` | Same center-crop-to-224 + quantization math (`scale=1/128, zp=128`); only the per-pixel byte read order differs. The `NotImplementedError` YUV stub text updates to reflect the real format switch. |
| 4 | `CameraPreviewHost.ios` | `composeApp/src/iosMain/.../ui/scan/CameraPreviewHost.ios.kt` | `UIKitView` wrapping a `UIView` backed by `AVCaptureVideoPreviewLayer` (`videoGravity = resizeAspectFill` = Android's `FILL_CENTER`). Downcast `cameraSource as? IosCameraSource ?: return` — same pattern as the Android actual. |
| 5 | `IosCameraPermission` | `composeApp/src/iosMain/.../permissions/` | Mirror of Android's `CameraPermissionState`: `AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)` → `Granted/Denied/NotAsked` (`.authorized→Granted`, `.denied/.restricted→Denied`, `.notDetermined→NotAsked`), `requestAccessForMediaType` as `launchRequest`, Settings deep-link via `UIApplicationOpenSettingsURLString`, and re-check on `UIApplicationDidBecomeActiveNotification` (= Android's ON_RESUME observer: catches the user returning from Settings). |
| 6 | `ScanScreenHost.ios` | `composeApp/src/iosMain/.../ui/scan/ScanScreenHost.ios.kt` | Replaces `IosComingSoonPanel`. Mirror of the Android host: permission state → `viewModel { graph.scanViewModel() }` → `ScanScreen(...)`; `persistFrame` encodes BGRA→JPEG q85 and writes `NSCachesDirectory/scan-frames/<uuid>.jpg`. |
| 7 | Shared `persistFrame` change | `composeApp/src/commonMain/.../ui/scan/{ScanScreen,ScanViewModel}.kt` + Android host | `(ByteArray) -> String` → `(ImageInput) -> String`. Android host passes `input.bytes`. `ScanViewModelTest` updated accordingly. |
| 8 | Take-photo | `composeApp/src/iosMain/.../ui/photoanalyze/IosCameraCapture.kt` (new sibling of `IosImageDecode.kt`) | `UIImagePickerController(sourceType = .camera)`, retained delegate (weak-`.delegate` trap), presented via `keyWindowRootViewController()`. Resulting image → JPEG bytes → existing `pendingBytes` state → same decode→crop→analyze path. Guarded by `isSourceTypeAvailable(.camera)` (simulator → no-op). |
| 9 | `IosPhotoStorage` rescale | `composeApp/src/iosMain/.../photo/PhotoStorageProvider.ios.kt` | Implements the contract: longest side ≤ 1024 @ q85 on `persistJpeg`, reusing `IosImageDecode`'s CG helpers (same module). |
| 10 | Wiring | `IosAppGraph.kt`, `iosApp/project.yml` (+ InfoPlist.strings) | `cameraSourceFactory = { IosCameraSource() }`; delete `IosNoopCameraSource`; `NSCameraUsageDescription` (EN + SV via `InfoPlist.strings`); `versionName` → `1.2.0-ios-i2c`; regenerate the Xcode project with xcodegen. |

## 5. Data flow

**Capture ceiling (battery/CPU):** `sessionPreset = .hd1280x720` + frame-rate cap
~15 fps via `activeVideoMinFrameDuration`. Rationale: on iOS the session preset governs
both preview and data output (unlike CameraX's independent use cases) — 720p gives a
crisp preview *and* modest buffers (1280×720×4 ≈ 3.7 MB; ~55 MB/s at 15 fps).
`ScanViewModel` still samples down to 3 fps / 1.5 fps; the cap just reduces wasted
copies between samples.

**Per frame (camera queue):**
1. Delegate receives `CMSampleBuffer` → `CVPixelBufferLockBaseAddress` (read-only).
2. **Stride compaction:** `bytesPerRow` may exceed `width*4` (alignment padding); rows
   are packed to exactly `w*h*4` — the preprocessor requires that size. This is i2c's
   sneaky-detail equivalent of i2b's lifetime-pin and gets its own unit test.
3. `ImageInput(bytes, w, h, rotationDegrees = 0, format = BGRA_8888,
   timestampMillis = wall clock)` — connections are portrait-locked so frames arrive
   upright; wall-clock time is mandatory for the freshness guard.
4. `tryEmit` into the `callbackFlow` → the shared sample/throttle/classify chain takes
   over unchanged.

**Classification:** the preprocessor's BGRA branch reads `b,g,r` per pixel — same crop,
same quantization, no new math.

**Freeze (once per session):** `onFreeze` → `persistFrame(ImageInput)` →
CGBitmapContext over the BGRA bytes → JPEG q85 → `scan-frames/<uuid>.jpg` → existing
`ScanSource.Image` routing into the match flow. If the user saves the find, the new
`IosPhotoStorage.persistJpeg` rescales the 1280-long-side frame to ≤ 1024 — the debt
pays off immediately.

**Simulator behavior (deliberate):** no camera device exists → `start()` logs and leaves
`ZoomState.NONE`; the screen shows a black preview + "searching…". No shared-code
changes to paper over a dev-only state — real devices with granted permission always
have a back camera.

## 6. Error handling

| Situation | Behavior |
|---|---|
| Permission denied / not asked | Existing shared `PermissionRequired`/`PermissionDenied` panels, driven by the `IosCameraPermission` mapping |
| Classifier errors | Existing `consecutiveErrors > 5` → `Error` state (shared, untouched) |
| Freeze persist fails (encode/write) | Throws → `runCatching` in `onFreeze` silently aborts the freeze — exact Android behavior |
| Take-photo cancelled/dismissed | No bytes → stays on the start state, mirroring i2b's picker cancel |
| Corrupt/empty pixel buffer | Frame skipped (Android's per-frame swallow) |
| Session start failure (camera busy etc.) | Logged; black preview + "searching…" — same silent model as Android's `bindToLifecycle` swallow |
| Background → foreground | AVCaptureSession's default interruption handling (auto-resume) — no custom code; explicit item on the device-verify list |

## 7. Testing & verification

Sim-first, since the device gate is open:

1. **BGRA parity guard (most important):** decode `parity_Q180991.jpg` → RGBA → permute
   to BGRA → `ImageInput(BGRA_8888)` → the **real classifier** → expect top-1 `Q180991`
   within the established tolerance. Binds the new format branch to the real model; runs
   in CI's macOS job (`:shared:ml:iosSimulatorArm64Test` already exists there). The
   channel-swap trap becomes mechanically impossible to reintroduce unnoticed.
2. **Preprocessor BGRA unit test** with a synthetic pixel pattern (asymmetric channel
   values so a B/R swap is caught).
3. **Stride compaction test** — synthetic padded buffer → exactly `w*h*4` out.
4. **Freeze-encode test** — BGRA bytes → JPEG → decode → dimensions + pixel sanity.
5. **`IosPhotoStorage` rescale test** — > 1024 input → persisted file ≤ 1024 longest
   side, q85.

   Tests 1–3 live in `:shared:ml` and ride the existing CI macOS job. Tests 4–5 live in
   `:composeApp`'s `iosSimulatorArm64Test`, which CI does **not** run today — the plan
   adds that target to the macOS job (small additive `ci.yml` change).
6. **`ScanViewModelTest`** updated for the `persistFrame` signature (otherwise
   untouched).
7. **Full Android regression gate** (shared signature touched):
   `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest
   :androidApp:assembleDebug ktlintCheck detekt` — plus agent sim-boot verify (scan tab
   renders the permission flow / black preview without crashing; take-photo button
   no-ops in sim).

**Open device gate (Albin, when an iPhone exists) — closes Milestone 1:** real camera
frames end-to-end, FOV zoom 1×/5×/10×, freeze→match→save (photo lands in the journal),
take-photo→crop→analyze, permission round-trips (deny → Settings toggle → return),
background/foreground resume, low-light sanity. Note: the composed live path
(capture → classify → freeze → persist) has the same caveat as i2b — parity tests cover
the components, only the device verify proves the composition.

## 8. Out of scope

- Audio-ID (i3), map/notifications/PDF (i4), StoreKit (i5), App Store release (i6).
- i2b's 13 deferred minors (local SDD ledger).
- Custom in-app photo-capture UI (system camera chosen deliberately).
- Any handling of external/continuity cameras, multi-cam device switching, or
  front-camera scanning — back wide-angle only, like Android.
- Surfacing camera-start failures as a new shared UI state (documented silent model).
