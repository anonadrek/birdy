# iOS i2b — Photo-ID (ML runtime + gallery scan) — Design

**Date:** 2026-07-12
**Status:** Approved in brainstorming (Mac). Ready for writing-plans → execution in a fresh session.
**Owner:** Albin (solo dev, via Claude Code)
**Parent spec:** `docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md` (§3 row i2). This design **splits i2** into three independent sub-plans (see §2).

## 1. Goal & success criteria

Identify a bird from a **gallery photo** on the physical iPhone using **real on-device TFLite inference** — no FakeClassifier. PHPicker upload → crop → classify (AIY Birds V1 via the vendored TensorFlow Lite C runtime) → the existing Match / Disambiguation / NoBird flow.

- **Success:** on iPhone (and iOS simulator), pick a bird photo from the library → the real classifier returns species predictions → the shared match flow routes correctly (Match ≥0.50 / Disambig ≥0.35 / NoBird). Confidences numerically match the Android/desktop reference within tolerance.
- **Milestone-1 progress:** this is the ML-runtime half of the spec's Milestone 1. Live camera scan (the other half) is **i2c**.
- **No new features.** This ports Android v1.2's photo-analyze (gallery) path to iOS. Take-photo and live camera are out of scope (i2c).

## 2. Why i2 is split (decision 2026-07-12)

Research (2026-07-12) established that **the Android LiteRT migration and the iOS ML runtime are independent** — iOS runs the vendored `TensorFlowLiteC` xcframework, *not* the Android `com.google.ai.edge.litert` Maven artifact. They share only the `.tflite` file format, not a runtime. So the parent spec's rationale ("do Android first so iOS builds on the new lib") does not hold, and i2 splits into three plans that can proceed in any order, on whichever machine is convenient:

| Sub-plan | Scope | Machine | Depends on |
|---|---|---|---|
| **i2a** | Android TFLite→LiteRT migration (photo `.so` 16 KB fix via `litert:1.4.1`; audio Flex `.so` decision; regression + 16 KB verify; vC126-ready) | Windows (Galaxy S23 device-verify) | — |
| **i2b** | iOS photo-ID: vendored `TensorFlowLiteC` + cinterop runner + `ImagePreprocessor.ios` + gallery/PHPicker scan + crop | **Mac** | — |
| **i2c** | iOS live camera (AVCaptureSession + `UIKitView` preview + zoom) + take-photo = **Milestone 1** | Mac | i2b (runtime) |

**This document is i2b only.** i2a and i2c get their own specs/plans.

## 3. Current state (verified 2026-07-12, grounds this design)

- The classifier stack is **entirely commonMain** and already cross-platform: `BirdClassifier`, `ImageInput`(bytes + `FrameFormat`), `TfLiteBirdClassifier`, the **`TfliteRunner` SPI** (`run(input: FloatArray, output: FloatArray)`), `BirdClassifierModelInfo`, `AiyLabelMapper`, `ModelArtifactProvider`, `ClassifierBootstrap`, `BirdClassifierFactory`, `SessionFailureGuard`.
- The **photo model + sidecars already ship to iOS** via compose-resources: `shared/ml/src/commonMain/composeResources/files/ml/aiy_birds_v1.tflite` (3,561,598 B, uint8, input `[1,224,224,3]`, output `[1,965]`, background class 964), `model_metadata.json`, `aiy_to_qid.json`. `Res.readBytes("files/ml/aiy_birds_v1.tflite")` already works on iOS — model-loading is **free**.
- **`ImagePreprocessor.ios.kt` throws** `UnsupportedOperationException("ImagePreprocessor lands on iOS in plan i2")` — replace it.
- **No iOS `TfliteRunner`** exists (only `AndroidTfliteRunner`, using `org.tensorflow.lite.Interpreter` with manual uint8 `ByteBuffer`s: quantize in `scale=1/128 zeroPoint=128`, dequantize out `scale=0.00390625 zeroPoint=0`, `numThreads=4`).
- The photo-analyze pipeline is shared: `PhotoAnalyzeViewModel` + `PhotoAnalyzeScreen` (commonMain), `PhotoAnalyzeHost.ios` = `IosComingSoonPanel` stub. `CropGeometry` is shared + tested; `CropAdjustScreen` is androidMain-only (`Bitmap`-typed). Match routing (`MatchThresholds` 0.50/0.35/0.15, `MatchResultViewModel`) is shared and needs no iOS work.
- `iosApp/Info.plist` has **no** photo/camera usage keys (PHPicker on iOS 14+ needs none — out-of-process — so gallery-only i2b is **permission-free**).
- `readFileBytes` iOS actual exists; the app already links `-lsqlite3` and vendors nothing native beyond the Kotlin framework.

## 4. Decisions (resolved in brainstorming)

1. **iOS runtime = vendored `TensorFlowLiteC.xcframework` (v2.17.0, CPU/XNNPACK) + Kotlin/Native cinterop against its C API**, behind the existing `TfliteRunner` expect/actual. **Zero new Swift.** (There is no separate "LiteRT-iOS" pod; on iOS LiteRT *is* the `TensorFlowLiteC` xcframework.)
2. **Vendor the xcframework committed into the repo** (consistent with the repo already committing large binaries — images, models, species.db). Reproducible/offline; no CocoaPods.
3. **i2b is gallery-only** (PHPicker, permission-free). Take-photo (needs camera permission) → i2c.
4. **Crop screen becomes shared** — refactor `CropAdjustScreen` to commonMain parameterized on Compose `ImageBitmap`; the Android host converts `Bitmap.asImageBitmap()`. Both platforms share one crop UI + the already-shared `CropGeometry`.
5. **Spike-first**: prove the cinterop↔static-xcframework↔static-Compose-framework link with a minimal "load model → 1 inference → read output" before any UI, on both simulator and (Albin) device.
6. **CPU/XNNPACK only** for i2b. Metal/CoreML delegates are a later optimization (not bundled — keeps the vendored binary smaller).

## 5. Architecture & component map

**The `TfliteRunner` SPI is the single seam.** iOS work is a defined set of actuals; everything above the seam is already shared.

| Concern | Android today | iOS (i2b) |
|---|---|---|
| Runtime | `org.tensorflow.lite.Interpreter` | cinterop → `TensorFlowLiteC` C API (`TfLiteModel*`/`TfLiteInterpreter*`) |
| Runner | `AndroidTfliteRunner : TfliteRunner` | **new** `IosTfliteRunner : TfliteRunner` (mirror: uint8 in/out, same quant params) |
| Preprocess | `ImagePreprocessor.android` (`android.graphics`) | **replace stub** `ImagePreprocessor.ios` (CoreGraphics/ImageIO) |
| Model bytes | `ModelArtifactProvider` (compose-resource) | same commonMain provider — no change |
| Classifier wiring | `MainActivity.buildClassifier()` | **change** `buildIosAppGraph()`: real `BirdClassifierFactory` + `TfLiteBirdClassifier` |
| Photo pick | `PhotoAnalyzeHost.android` (`PickVisualMedia`) | **new** `PhotoAnalyzeHost.ios` (`PHPickerViewController`) |
| Crop UI | `CropAdjustScreen.android` (`Bitmap`) | **refactor** → commonMain `CropAdjustScreen(ImageBitmap)` |
| Frame persist | host writes to `cacheDir` | host writes to `NSCachesDirectory` |

**Vendored binary (verified download):**
`https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteC/2.17.0/0c10b3543e01f547/TensorFlowLiteC-2.17.0.tar.gz` (HTTP 200, ~76.5 MB tarball; extract and keep **only** `TensorFlowLiteC.xcframework` — it contains `ios-arm64` (device) + `ios-arm64_x86_64-simulator` slices, a static Mach-O with a Clang module + C headers `c_api.h`, `c_api_types.h`, `common.h`). Commit under e.g. `iosApp/Frameworks/TensorFlowLiteC.xcframework/`.

**cinterop:** a `.def` in `:shared:ml` (`shared/ml/src/nativeInterop/cinterop/TensorFlowLiteC.def`) with `language = C`, `modules = TensorFlowLiteC` (or `headers = c_api.h`), plus `compilerOpts`/`linkerOpts` pointing at the xcframework slice per target. Wire `iosArm64`/`iosSimulatorArm64` `cinterops { create("TensorFlowLiteC") }` in `shared/ml/build.gradle.kts`. Link the xcframework in `iosApp/project.yml`.

**TFLite C API sequence** (`IosTfliteRunner`, mirroring `AndroidTfliteRunner`):
`TfLiteModelCreate(bytes, size)` → `TfLiteInterpreterOptionsCreate` + `…SetNumThreads(4)` → `TfLiteInterpreterCreate` → `TfLiteInterpreterAllocateTensors` → per `run()`: `TfLiteInterpreterGetInputTensor(0)` + `TfLiteTensorCopyFromBuffer(uint8 input)`, `TfLiteInterpreterInvoke`, `TfLiteInterpreterGetOutputTensor(0)` + `TfLiteTensorCopyToBuffer(uint8 output)`; `close()`: `TfLiteInterpreterDelete`/`TfLiteInterpreterOptionsDelete`/`TfLiteModelDelete`. Quantize/dequantize identically to Android.

## 6. Numeric parity strategy

The research flagged that iOS `TensorFlowLiteC` 2.17.0 and Android `litert 1.4.x` are different runtime lineages; XNNPACK/quantization differences can shift confidences. Mitigations, baked into the plan:
- A committed **fixed test image** (reuse one from `tools/ml-eval/corpus/`) with a known expected top-1 Q-id.
- An `iosSimulatorArm64Test` that runs the full iOS classifier (preprocessor + runner) on that image and asserts **top-1 == expected Q-id** and **confidence within a tolerance** (e.g. ±0.05 absolute) of the desktop `ai-edge-litert` reference in `tools/ml-eval`.
- If parity fails: first confirm preprocessing byte-parity (separate preprocessor golden-vector test), then investigate XNNPACK (try disabling it) / quantization handling — before weakening the assertion.

## 7. Task outline (spike-first; detailed in the plan)

1. **T1 — cinterop spike:** vendor `TensorFlowLiteC.xcframework`; `.def` + gradle cinterop + `project.yml` link; a smoke test that loads `aiy_birds_v1.tflite`, invokes once on a zeroed/fixed input, asserts output tensor is `[1,965]` uint8 — green on `iosSimulatorArm64` (and Albin runs it on device). **De-risks the whole plan.**
2. **T2 — `ImagePreprocessor.ios`** (CoreGraphics) + a preprocessor golden-vector test (byte-parity with Android's normalization on a fixed small image).
3. **T3 — `IosTfliteRunner`** (full quant/dequant) + numeric-parity classification test (top-1 == expected Q-id on the fixed corpus image).
4. **T4 — wire real classifier** into `buildIosAppGraph()` (`BirdClassifierFactory` real/fallback, `TfLiteBirdClassifier`, mode REAL); Android untouched.
5. **T5 — crop refactor** `CropAdjustScreen` → commonMain `ImageBitmap`; Android host adapts. Android + iOS both green.
6. **T6 — `PhotoAnalyzeHost.ios`** (PHPicker → ImageIO decode/EXIF/downscale → shared crop → JPEG → `PhotoAnalyzeViewModel`; persist to `NSCachesDirectory`); wire into the nav graph (replace `IosComingSoonPanel`).
7. **T7 — verify:** simulator (agent: build + install + runner/parity tests green; boot) + interactive gallery→ID (Albin, sim/iPhone); Android regression; CLAUDE.md sync.

## 8. Testing & verification

- **Agent-drivable:** `iosSimulatorArm64Test` (runner smoke, preprocessor golden, classification parity), `:shared:ml:iosSimulatorArm64Test`; Android regression (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlint detekt`); build the iOS app + boot screenshot.
- **Albin-in-the-loop (as in i1 — no sim tap injection):** interactive gallery-pick → crop → ID → match, on the simulator (drag a bird image into the sim Photos app) and/or the physical iPhone.

## 9. Hard constraints (inherited)

Android stays shippable every commit (only T5 touches Android — the crop refactor — guarded by tests). Parity, not parity-plus. Privacy: no telemetry, inference fully on-device. Min iOS 16. New Kotlin passes ktlint/detekt. `TensorFlowLiteC` is Apache-2.0 (permissive — no license issue, unlike BirdNET/CC-BY-NC-SA which is audio/i3 only).

## 10. Risks & mitigations

| Risk | Sev | Mitigation |
|---|---|---|
| cinterop ↔ static xcframework ↔ static Compose framework link plumbing | High | **T1 spike first**, sim + device, before any UI |
| Numeric parity drift (XNNPACK/quant) iOS vs Android | Med | T3 parity test on fixed image; preprocessor golden in T2; documented fallback (disable XNNPACK) |
| App size grows (+~30–50 MB xcframework on top of 413 MB images) | Med | CPU-only (no Metal/CoreML); acceptable for launch; ODR later (parent spec §10) |
| Crop refactor regresses Android crop | Low | T5 keeps Android `CropGeometry` tests green + device crop still works; small typed-param change |
| PHPicker/ImageIO EXIF handling differs from Android | Low | Mirror Android's decode/rotate/downscale; interactive verify |

## 11. Out of scope (other plans)

- **Live camera** (AVCaptureSession, `UIKitView` preview, zoom chips), **take-photo** — i2c.
- **Android TFLite→LiteRT migration** + 16 KB + vC126 — i2a.
- **Audio-ID** (BirdNET, Select-TF-ops `-force_load`) — i3.
- Metal/CoreML GPU/ANE delegates — later optimization.
