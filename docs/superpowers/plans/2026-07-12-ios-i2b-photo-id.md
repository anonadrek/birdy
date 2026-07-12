# iOS i2b — Photo-ID (ML runtime + gallery scan) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Start-of-session:** `git pull` first (two-machine repo). Read the design spec `docs/superpowers/specs/2026-07-12-ios-i2b-photo-id-design.md` before Task 1. Build prefix on the Mac: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"`. iOS simulator build recipe + the "no tap injection" limitation are in auto-memory `reference_ios_simulator_build_and_verify` (use a concrete `-destination "id=<sim-udid>"`).

**Goal:** Identify a bird from a gallery photo on iPhone via real on-device TFLite inference — PHPicker upload → crop → classify (AIY Birds V1 through a vendored `TensorFlowLiteC` runtime) → the existing Match/Disambig/NoBird flow. No live camera, no take-photo (i2c).

**Architecture:** The `TfliteRunner` SPI (`run(input: FloatArray, output: FloatArray)`) is the single seam; everything above it is already shared commonMain. i2b adds iOS actuals below the seam: a cinterop runner against the vendored `TensorFlowLiteC.xcframework` C API, a CoreGraphics `ImagePreprocessor.ios`, the AppGraph wiring, a PHPicker photo-analyze host, and a crop screen lifted to shared `ImageBitmap`. Spike-first: prove the cinterop link before any UI.

**Tech Stack:** Kotlin/Native cinterop, `TensorFlowLiteC` 2.17.0 (Apache-2.0, static xcframework, CPU/XNNPACK), CoreGraphics/ImageIO, PHPickerViewController, Compose `ImageBitmap`, xcodegen. Kotlin 2.1.20 / CMP 1.8.2.

## Global Constraints

- **Android stays shippable every commit:** `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` green. Only Task 5 (crop refactor) touches Android; all else is iosMain/iosTest/iosApp/build config.
- **Parity, not parity-plus.** Port Android's gallery photo-analyze; add nothing new. Take-photo + live camera = i2c.
- **Privacy:** inference fully on-device, no telemetry.
- **Numeric parity:** iOS classifier must match the reference top-1 Q-id on a fixed image; confidence within ±0.05 absolute (see Task 3) — do not weaken without investigating preprocessing + XNNPACK first.
- **Runner mirror:** `IosTfliteRunner` must replicate `AndroidTfliteRunner` quantization exactly — input `scale=1/128, zeroPoint=128`; output `scale=info.outputScale (0.00390625), zeroPoint=info.outputZeroPoint (0)`; `numThreads=4`.
- **Min iOS 16**, bundle id `se.birdy.ios`. New Kotlin passes `ktlintCheck detekt` (run `ktlintFormat` first).
- **CPU/XNNPACK only.** Do not add Metal/CoreML delegate frameworks.

## File Structure

**Vendored runtime (new)**
- `iosApp/Frameworks/TensorFlowLiteC.xcframework/` — committed static xcframework (device + sim slices; Metal/CoreML dropped).
- `shared/ml/src/nativeInterop/cinterop/TensorFlowLiteC.def` — cinterop definition.
- Modify `shared/ml/build.gradle.kts` — cinterop registration on both iOS targets + iosTest deps.
- Modify `iosApp/project.yml` — link the xcframework + `FRAMEWORK_SEARCH_PATHS`.

**iOS ML actuals (new / replace)**
- `shared/ml/src/iosMain/kotlin/se/birdy/ml/IosTfliteRunner.kt` — `TfliteRunner` actual via cinterop.
- Replace `shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt` — CoreGraphics.
- `shared/ml/src/iosTest/kotlin/se/birdy/ml/...` — runner smoke, preprocessor golden, classification parity.
- Test resource: a committed fixed bird image under `shared/ml/src/commonTest/composeResources/files/testdata/` (reuse a `tools/ml-eval/corpus` image).

**Wiring + UI**
- Modify `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` — real classifier.
- Move `composeApp/src/androidMain/.../photoanalyze/CropAdjustScreen.android.kt` → `composeApp/src/commonMain/.../photoanalyze/CropAdjustScreen.kt` (param `ImageBitmap`); adapt the Android host.
- Replace `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.ios.kt` — PHPicker flow.
- New `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosImageDecode.kt` — ImageIO decode/EXIF/downscale/JPEG helpers.

**Docs:** `CLAUDE.md` status + Plan-of-plans.

---

### Task 1: cinterop spike — vendor the runtime and prove one inference

Prove the vendored static xcframework + Kotlin/Native cinterop + static Compose framework link and run one real inference, before any UI or preprocessing. This is the de-risking task.

**Files:**
- Create: `iosApp/Frameworks/TensorFlowLiteC.xcframework/` (extracted, committed)
- Create: `shared/ml/src/nativeInterop/cinterop/TensorFlowLiteC.def`
- Modify: `shared/ml/build.gradle.kts`
- Modify: `iosApp/project.yml`
- Test: `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosTfliteSmokeTest.kt`

**Interfaces:**
- Produces: cinterop package `tflitec` exposing the C API (`TfLiteModelCreate`, `TfLiteInterpreterOptionsCreate`, `TfLiteInterpreterOptionsSetNumThreads`, `TfLiteInterpreterCreate`, `TfLiteInterpreterAllocateTensors`, `TfLiteInterpreterGetInputTensor`, `TfLiteTensorCopyFromBuffer`, `TfLiteInterpreterInvoke`, `TfLiteInterpreterGetOutputTensor`, `TfLiteTensorCopyToBuffer`, `TfLiteTensorByteSize`, `TfLiteInterpreterDelete`, `TfLiteInterpreterOptionsDelete`, `TfLiteModelDelete`).

- [ ] **Step 1: Download + extract + commit the xcframework (keep only TensorFlowLiteC)**

```bash
cd /Users/albinabrahamsson/dev/birdy
mkdir -p iosApp/Frameworks && cd iosApp/Frameworks
curl -L -o tflitec.tar.gz "https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteC/2.17.0/0c10b3543e01f547/TensorFlowLiteC-2.17.0.tar.gz"
tar xzf tflitec.tar.gz
# Keep only the base CPU framework; drop Metal/CoreML + the tarball.
rm -rf TensorFlowLiteCMetal.xcframework TensorFlowLiteCCoreML.xcframework tflitec.tar.gz
ls TensorFlowLiteC.xcframework   # expect: Info.plist + ios-arm64 + ios-arm64_x86_64-simulator
find TensorFlowLiteC.xcframework -name "*.h" | head   # expect c_api.h, c_api_types.h, common.h
```
Expected: `TensorFlowLiteC.xcframework` with both slices and a `Headers/` dir per slice.

- [ ] **Step 2: Write the cinterop def**

Create `shared/ml/src/nativeInterop/cinterop/TensorFlowLiteC.def`:

```
language = C
package = tflitec
modules = TensorFlowLiteC
```

(Header + framework search paths are supplied per-target from Gradle in Step 3 so the sim vs device slice is selected correctly.)

- [ ] **Step 3: Register the cinterop + iosTest deps in the ml build**

In `shared/ml/build.gradle.kts`, inside `kotlin { }`, after the `iosArm64()`/`iosSimulatorArm64()` target declarations, configure the cinterop per target and add iosTest deps:

```kotlin
    val fwRoot = "$projectDir/../../iosApp/Frameworks/TensorFlowLiteC.xcframework"
    iosArm64 {
        compilations.getByName("main").cinterops.create("TensorFlowLiteC") {
            defFile("src/nativeInterop/cinterop/TensorFlowLiteC.def")
            compilerOpts("-F$fwRoot/ios-arm64")
        }
        binaries.all { linkerOpts("-F$fwRoot/ios-arm64", "-framework", "TensorFlowLiteC") }
    }
    iosSimulatorArm64 {
        compilations.getByName("main").cinterops.create("TensorFlowLiteC") {
            defFile("src/nativeInterop/cinterop/TensorFlowLiteC.def")
            compilerOpts("-F$fwRoot/ios-arm64_x86_64-simulator")
        }
        binaries.all { linkerOpts("-F$fwRoot/ios-arm64_x86_64-simulator", "-framework", "TensorFlowLiteC") }
    }
```

And in `sourceSets { }` add (mirrors :shared:data i1):
```kotlin
        iosTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
```

- [ ] **Step 4: Write the smoke test**

Create `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosTfliteSmokeTest.kt`. It loads the committed model bytes (via the commonMain `ModelArtifactProvider`/`loadModelMetadata`), builds an interpreter through the cinterop C API, invokes once on a zero-filled uint8 input, and asserts the output tensor byte size is 965.

```kotlin
package se.birdy.ml

import kotlinx.cinterop.*
import kotlinx.coroutines.test.runTest
import tflitec.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosTfliteSmokeTest {
    @Test
    fun loads_model_and_invokes_once() =
        runTest {
            val info = loadModelMetadata()
            val bytes = ModelArtifactProvider().loadModelBytes(info)
            val model = bytes.usePinned { p -> TfLiteModelCreate(p.addressOf(0), bytes.size.convert()) }
            assertTrue(model != null, "TfLiteModelCreate returned null")
            val opts = TfLiteInterpreterOptionsCreate()
            TfLiteInterpreterOptionsSetNumThreads(opts, 4)
            val interp = TfLiteInterpreterCreate(model, opts)
            assertTrue(interp != null)
            assertEquals(kTfLiteOk, TfLiteInterpreterAllocateTensors(interp))

            val inTensor = TfLiteInterpreterGetInputTensor(interp, 0)
            val inSize = 224 * 224 * 3
            val input = UByteArray(inSize) // zeros
            input.usePinned { p -> TfLiteTensorCopyFromBuffer(inTensor, p.addressOf(0), inSize.convert()) }
            assertEquals(kTfLiteOk, TfLiteInterpreterInvoke(interp))

            val outTensor = TfLiteInterpreterGetOutputTensor(interp, 0)
            assertEquals(965uL, TfLiteTensorByteSize(outTensor).toULong())

            TfLiteInterpreterDelete(interp)
            TfLiteInterpreterOptionsDelete(opts)
            TfLiteModelDelete(model)
        }
}
```
(Exact cinterop symbol/enum names — `kTfLiteOk`, pointer nullability, `.convert()` widths — are validated here; adjust to what the generated `tflitec` package exposes. This is the point of the spike.)

- [ ] **Step 5: Run the smoke test on the simulator**

Run: `export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:ml:iosSimulatorArm64Test --console=plain`
Expected: the cinterop klib generates, the test links against the sim slice, and `loads_model_and_invokes_once` PASSES. If linking fails, the xcframework must also be added to `iosApp/project.yml` (Step 6) — but the `iosSimulatorArm64Test` executable links via the Gradle `linkerOpts` above, so it should resolve without Xcode.

- [ ] **Step 6: Link the xcframework in the app project + regenerate**

In `iosApp/project.yml`, under `targets.Birdy.settings.base`, add the framework search path and link flag, and add the framework to the target's frameworks. Simplest with xcodegen — add a `dependencies` entry:
```yaml
    dependencies:
      - framework: Frameworks/TensorFlowLiteC.xcframework
        embed: false   # static — link only
```
Then regenerate: `cd iosApp && ~/.local/bin/xcodegen generate`. Build the app: use the concrete-destination recipe from `reference_ios_simulator_build_and_verify`. Expect `** BUILD SUCCEEDED **`.

- [ ] **Step 7: ktlint + commit**

```bash
export JAVA_HOME="$HOME/.local/java21/Contents/Home"; ./gradlew :shared:ml:ktlintCheck --console=plain
git add iosApp/Frameworks shared/ml/src/nativeInterop shared/ml/build.gradle.kts shared/ml/src/iosTest iosApp/project.yml iosApp/Birdy.xcodeproj
git commit -m "feat(ios): vendor TensorFlowLiteC + cinterop smoke — one real inference (i2b T1)"
```
(If `species.db` shows modified after the app build, `git restore` it — regeneration noise, per the reference memory.)

---

### Task 2: `ImagePreprocessor.ios` (CoreGraphics) + byte-parity golden

Replace the throwing stub with a CoreGraphics implementation that byte-matches the Android normalization, verified by a golden-vector test.

**Files:**
- Modify: `shared/ml/src/iosMain/kotlin/se/birdy/ml/ImagePreprocessor.ios.kt`
- Test: `shared/ml/src/iosTest/kotlin/se/birdy/ml/ImagePreprocessorIosTest.kt`
- Test resource: `shared/ml/src/commonTest/composeResources/files/testdata/paritycard.png` (a tiny fixed image with known pixels — e.g. a 2×2 committed PNG)

**Interfaces:**
- Consumes: `ImageInput(bytes, widthPx, heightPx, rotationDegrees, format)`, contract from `ImagePreprocessor.kt` — returns `FloatArray(outHeight*outWidth*3)`, RGB row-major, `out = (px/255 − mean)/std`.
- Produces: working `ImagePreprocessor()` iOS actual.

- [ ] **Step 1: Write the golden test first**

Create `ImagePreprocessorIosTest.kt`. Encode a known 2×2 RGB image as PNG bytes in-test (or load the committed `paritycard.png`), call `preprocess(..., outHeight=2, outWidth=2, mean=[0.5,0.5,0.5], std=[0.5,0.5,0.5])`, and assert each of the 12 floats equals `(channel/255 − 0.5)/0.5` for the known pixels (RGB row-major). Use a small epsilon (1e-3) for resampling-free 2×2 (no scaling).

```kotlin
// asserts, e.g., a pure-red pixel (255,0,0) -> R=1.0f, G=-1.0f, B=-1.0f
```

- [ ] **Step 2: Run it — expect FAIL (stub throws)**

Run: `./gradlew :shared:ml:iosSimulatorArm64Test --tests "*ImagePreprocessorIos*" --console=plain` → FAIL (`UnsupportedOperationException`).

- [ ] **Step 3: Implement CoreGraphics preprocessing**

Replace `ImagePreprocessor.ios.kt`: decode `input.bytes` via `CGImageSource` (ImageIO), apply `input.rotationDegrees`, draw into an `outWidth×outHeight` RGBA `CGBitmapContext` (premultiplied-last, 8-bit, sRGB), read the pixel buffer, and emit `FloatArray(outH*outW*3)` RGB row-major with `(px/255 − mean[c])/std[c]`. Require `mean.size == 3 && std.size == 3`. Free CG objects.

(Full concrete implementation using `platform.CoreGraphics.*` / `platform.ImageIO.*` / `kotlinx.cinterop`; mirror the Android channel order R,G,B interleaved.)

- [ ] **Step 4: Run — expect PASS.** `./gradlew :shared:ml:iosSimulatorArm64Test --console=plain` green.

- [ ] **Step 5: ktlint + commit** (`git add shared/ml/src/iosMain shared/ml/src/iosTest shared/ml/src/commonTest`; `feat(ios): CoreGraphics ImagePreprocessor + byte-parity test (i2b T2)`).

---

### Task 3: `IosTfliteRunner` + numeric classification parity

Wrap the cinterop into a `TfliteRunner` actual with the exact Android quant/dequant, then verify the *whole* iOS classifier (preprocessor + runner + mapper) picks the right species on a fixed image.

**Files:**
- Create: `shared/ml/src/iosMain/kotlin/se/birdy/ml/IosTfliteRunner.kt`
- Test: `shared/ml/src/iosTest/kotlin/se/birdy/ml/IosClassifierParityTest.kt`
- Test resource: a committed real bird photo from `tools/ml-eval/corpus/` with a known expected Q-id (copy into `shared/ml/src/commonTest/composeResources/files/testdata/`).

**Interfaces:**
- Consumes: cinterop `tflitec.*` (Task 1), `BirdClassifierModelInfo`, model bytes.
- Produces: `class IosTfliteRunner(modelBytes: ByteArray, info: BirdClassifierModelInfo) : TfliteRunner` with `run(input: FloatArray, output: FloatArray)` + `close()`.

- [ ] **Step 1: Implement `IosTfliteRunner`** — construct model/interpreter once (as Task 1 smoke), keep handles; `run()` quantizes `input` float→uint8 (`(v/(1f/128f)+128).roundToInt().coerceIn(0,255)`), `TfLiteTensorCopyFromBuffer`, `TfLiteInterpreterInvoke`, `TfLiteTensorCopyToBuffer` into a uint8 out buffer, dequantizes `(q - info.outputZeroPoint) * info.outputScale` into `output`; `close()` deletes handles. Mirror `AndroidTfliteRunner.run` exactly.

- [ ] **Step 2: Write the parity test** — build the full iOS classifier directly (no factory needed in the test): `val mean = info.normalizationMean.toFloatArray(); val std = info.normalizationStd.toFloatArray(); TfLiteBirdClassifier(info, IosTfliteRunner(bytes, info), preprocess = { img -> ImagePreprocessor().preprocess(img, info.inputHeightPx, info.inputWidthPx, mean, std) }, mapper = loadAiyLabelMapper())`, classify the fixed corpus image (as `ImageInput(format = FrameFormat.JPEG)`), assert `top()!!.speciesId == "<expected Q-id>"` and `top()!!.confidence` within ±0.05 of the ml-eval reference value (hard-code the reference from `tools/ml-eval` golden). Note `normalizationMean/Std` are `List<Float>` on `info` → `.toFloatArray()` for the `ImagePreprocessor` signature.

- [ ] **Step 3: Run** `./gradlew :shared:ml:iosSimulatorArm64Test --console=plain`. Expected PASS. If top-1 wrong or confidence off: verify Task 2 golden still green (preprocessing), then try `TfLiteInterpreterOptions`/XNNPACK toggles; only then discuss tolerance with the reviewer.

- [ ] **Step 4: ktlint + commit** (`feat(ios): IosTfliteRunner + numeric classification parity (i2b T3)`).

---

### Task 4: Wire the real classifier into `buildIosAppGraph()`

Replace the DEMO `FakeBirdClassifier` with the real factory + `TfLiteBirdClassifier`, mirroring `MainActivity.buildClassifier()`.

**Files:** Modify `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`.

**Interfaces:** Consumes `BirdClassifierFactory`, `TfLiteBirdClassifier`, `IosTfliteRunner`, `ImagePreprocessor`, `loadModelMetadata`, `loadAiyLabelMapper`, `ModelArtifactProvider`, `ClassifierMode`.

- [ ] **Step 1: Replace the classifier bootstrap** — change the `buildClassifier` lambda in `buildIosAppGraph()` to build the real classifier:

```kotlin
val classifierBootstrap =
    ClassifierBootstrap(
        buildClassifier = {
            val info = loadModelMetadata()
            val mapper = loadAiyLabelMapper()
            val modelBytes = ModelArtifactProvider().loadModelBytes(info)
            val mean = info.normalizationMean.toFloatArray()
            val std = info.normalizationStd.toFloatArray()
            // BirdClassifierFactory.create() returns Pair<BirdClassifier, ClassifierMode>;
            // onCrashlytics is REQUIRED (iOS has none yet — swallow).
            val (classifier, mode) =
                BirdClassifierFactory(
                    createReal = {
                        val runner = IosTfliteRunner(modelBytes, info)
                        val preprocessor = ImagePreprocessor()
                        TfLiteBirdClassifier(
                            info = info,
                            runner = runner,
                            preprocess = { img -> preprocessor.preprocess(img, info.inputHeightPx, info.inputWidthPx, mean, std) },
                            mapper = mapper,
                        )
                    },
                    createFallback = { FakeBirdClassifier() },
                    onCrashlytics = { /* no Crashlytics on iOS in i2b */ },
                ).create()
            Triple(classifier, mode, info.tfliteSha256.take(8))
        },
    )
```
Verified against source: `BirdClassifierModelInfo` fields are `inputWidthPx`/`inputHeightPx`/`normalizationMean: List<Float>`/`normalizationStd: List<Float>`/`outputScale`/`outputZeroPoint`/`tfliteSha256`; `BirdClassifierFactory(createReal, createFallback, onCrashlytics).create(): Pair<BirdClassifier, ClassifierMode>` (it already wraps the real classifier in `SessionFailureGuard`); `ClassifierBootstrap(buildClassifier: suspend () -> Triple<BirdClassifier, ClassifierMode, String?>)`. Update the file's KDoc: the FakeClassifier-DEMO limitation is now resolved (photo). Bump `versionName` to `"1.2.0-ios-i2b"`.

- [ ] **Step 2: Verify iOS links + Android untouched**

Run: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt --console=plain`
Expected: BUILD SUCCESSFUL; Android tasks UP-TO-DATE/green.

- [ ] **Step 3: Commit** (`feat(ios): wire real photo classifier into iOS AppGraph (i2b T4)`).

---

### Task 5: Lift `CropAdjustScreen` to shared commonMain (`ImageBitmap`)

Make the crop UI cross-platform so iOS reuses it.

**Files:**
- Move/Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.kt`
- Delete: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt` (pass `bitmap.asImageBitmap()`)

**Interfaces:** Produces `CropAdjustScreen(image: ImageBitmap, onConfirm: (CropRect) -> Unit, onCancel: () -> Unit, onRotate: () -> Unit, ...)` in commonMain. Consumes the already-shared `CropGeometry`.

- [ ] **Step 1:** Move the file to commonMain, change the parameter from `android.graphics.Bitmap` to `androidx.compose.ui.graphics.ImageBitmap`, replace `bitmap.asImageBitmap()` internals with the passed `ImageBitmap`, and use `image.width`/`image.height` for geometry. Drop Android-only bitmap-recycle handling (ARC/Compose owns `ImageBitmap`). Keep the pure `fitMapping`/`pickDragMode`/`CropGeometry` math.
- [ ] **Step 2:** In `PhotoAnalyzeHost.android.kt`, pass `cropBitmap.asImageBitmap()` to `CropAdjustScreen`; keep rotation done in the host (`rotate90()` on the source `Bitmap`), re-deriving the `ImageBitmap` after rotate.
- [ ] **Step 3: Verify Android** — `./gradlew :composeApp:testDebugUnitTest :composeApp:compileDebugKotlinAndroid :androidApp:assembleDebug ktlintCheck detekt` green; `CropGeometryTest` still passes. **Device note for Albin:** re-verify the Android crop still works on the Galaxy at the next Android session (interactive) — flag in commit.
- [ ] **Step 4: Verify iOS compiles** — `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` green (crop now visible to iosMain).
- [ ] **Step 5: Commit** (`refactor: lift CropAdjustScreen to shared commonMain (ImageBitmap) (i2b T5)`).

---

### Task 6: `PhotoAnalyzeHost.ios` — PHPicker → crop → analyze

Replace the coming-soon stub with the real gallery flow, mirroring the Android host's state machine (crop as local `mutableStateOf`, `minShortSide=224` skip, downscale long side to 1024, JPEG 90).

**Files:**
- Replace: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.ios.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/photoanalyze/IosImageDecode.kt` (ImageIO decode/EXIF-rotate/downscale/JPEG-encode + `ImageBitmap` conversion)

**Interfaces:** Consumes `PhotoAnalyzeViewModel` (shared: `markAnalyzing()`, `analyze(ImageInput, persist)`, states `Loaded`/`TooSmall`), `PhotoAnalyzeScreen` (shared picker/results UI), shared `CropAdjustScreen` (Task 5), `CropGeometry`, `readFileBytes` (iOS actual exists).

- [ ] **Step 1:** Implement `IosImageDecode.kt`: `PHPickerViewController` presented from the key window's root VC, `PHPickerConfiguration(filter = images, selectionLimit = 1)`; on pick, load the `NSItemProvider` → `UIImage`/`CGImage` bytes; helpers `decodeForCrop(bytes) -> ImageBitmap` (EXIF-rotate, cap long side 2048), `finalizeCrop(image, rect) -> ByteArray` (crop, scale long side to 1024, `UIImageJPEGRepresentation(0.9)`), `persistToCaches(bytes) -> String` (write to `NSCachesDirectory/photo-input/<uuid>.jpg`).
- [ ] **Step 2:** Implement `PhotoAnalyzeHost.ios` replicating the Android host: `PhotoAnalyzeScreen` with an "open gallery" action launching PHPicker → `pendingImage` state → `markAnalyzing()` → `decodeForCrop` → if `min(w,h) < 224` skip crop + `analyze(finalizeCrop(fullRect))` else show shared `CropAdjustScreen` → on confirm `analyze(finalizeCrop(rect), persist=::persistToCaches)` → `onLoaded(sourceJson, capturedAtMs)`. Wire into `AppScaffold` nav (it already routes `PhotoAnalyzeHost.onLoaded` → `MatchResult`; only the host body changes).
- [ ] **Step 3: Verify** — `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 ktlintCheck detekt` green; Android untouched.
- [ ] **Step 4: Commit** (`feat(ios): PHPicker photo-analyze host — gallery ID on iOS (i2b T6)`).

---

### Task 7: Verify + Android regression + docs sync

**Files:** screenshots under `docs/superpowers/screenshots/`; `CLAUDE.md`.

- [ ] **Step 1 (agent):** Build the app (concrete-destination recipe), install to a booted simulator, launch, screenshot. Confirm `:shared:ml:iosSimulatorArm64Test` all green (smoke + preprocessor + parity).
- [ ] **Step 2 (agent):** Android shippability gate — `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt` green.
- [ ] **Step 3 (Albin, interactive):** Drag a bird photo into the simulator's Photos app (or use device); in Birdy, open the photo/scan entry → gallery → pick it → crop → confirm → real classification → correct Match/Disambig screen. Screenshot. Also re-verify Android crop on the Galaxy (from Task 5).
- [ ] **Step 4 (agent):** Update `CLAUDE.md` (Status + Plan-of-plans i2b row → done/verified), commit, push.

---

## Exit criteria (i2b done)

1. `:shared:ml:iosSimulatorArm64Test` green: cinterop smoke (1 inference), preprocessor byte-parity, and full-classifier numeric parity (top-1 Q-id + confidence within tolerance).
2. On simulator + iPhone: pick a bird photo from the library → **real** on-device classification → correct Match/Disambig/NoBird routing.
3. Android shippable (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug` + ktlint/detekt green); Android crop re-verified on device.
4. `TensorFlowLiteC.xcframework` committed; cinterop builds on both iOS targets; no CocoaPods added; zero new Swift.
5. CLAUDE.md synced + pushed.

Deferred: live camera + take-photo (i2c), Android LiteRT migration (i2a), audio (i3), Metal/CoreML delegates (later).

## Self-review notes

- **Spec coverage:** runtime (T1/T3), preprocessing (T2), wiring (T4), crop-shared (T5), PHPicker gallery (T6), verify+parity (T3/T7) — all spec §5/§7 items covered.
- **Spike-first honored:** T1 proves the highest risk (cinterop link) before any UI.
- **Android blast radius:** only T5 (crop refactor) touches Android; guarded by `CropGeometryTest` + assembleDebug + device re-verify.
- **Type consistency:** `IosTfliteRunner : TfliteRunner` matches the SPI; `CropAdjustScreen(ImageBitmap, …)` used identically by both hosts; quant params match `AndroidTfliteRunner`.
- **Known-uncertain (validated in-task, not placeholders):** exact cinterop symbol/enum spellings (T1 smoke validates), CoreGraphics pixel plumbing (T2 golden validates), PHPicker presentation (T6 device verify).
