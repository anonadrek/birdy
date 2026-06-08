# Geotag Non-Live Captures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Geotag in-app take-photo finds with the current device location (4a) and gallery finds with the photo's EXIF GPS (4b), both gated by the existing opt-in location toggle.

**Architecture:** Replace `ScanSource.Image.live: Boolean` with an `origin: ImageOrigin` enum ({LiveScan, CameraCapture, Gallery}) plus pre-resolved `exifLatitude/exifLongitude: Double?`. The save path (`SaveObservationUseCase`) gains a `presetLocation` argument: a non-null preset (gallery EXIF) is used directly; otherwise `current()` is called when the origin warrants it. Everything stays gated by `UserPreferences.locationCaptureEnabled`.

**Tech Stack:** Kotlin Multiplatform, Compose, kotlinx.serialization, androidx ExifInterface (already a dependency), JUnit/kotlin.test + Turbine.

**Spec:** `docs/superpowers/specs/2026-06-08-map-location-non-live-captures-design.md`

**Build/test commands** (prefix every `./gradlew` call with the JAVA_HOME setup):
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```
- shared/ml unit tests: `./gradlew :shared:ml:jvmTest`
- composeApp unit tests: `./gradlew :composeApp:testDebugUnitTest`
- lint: `./gradlew ktlintCheck detekt` (autofix: `./gradlew ktlintFormat`)
- android compile: `./gradlew :androidApp:assembleDebug`

---

## Task 1: `SaveObservationUseCase.presetLocation` (additive, build stays green)

A new optional `presetLocation` argument. When non-null and the toggle is on, it is used verbatim (no `current()` call). This is purely additive — no existing call site changes.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationLocationTest.kt`

- [ ] **Step 1: Write the failing tests**

Append these three tests inside `class SaveObservationLocationTest` (before the closing brace) in `SaveObservationLocationTest.kt`:

```kotlin
    @Test
    fun usesPresetLocationWhenEnabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save(
                    "Q1", capturedAt, 0.9f, ByteArray(4), "",
                    attachLocation = false, presetLocation = LatLng(40.0, -3.0),
                )
            val row = repo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(-3.0, row.longitude)
            assertEquals(0, provider.currentCalls)
        }

    @Test
    fun ignoresPresetLocationWhenToggleDisabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = false)
                .save(
                    "Q1", capturedAt, 0.9f, ByteArray(4), "",
                    attachLocation = false, presetLocation = LatLng(40.0, -3.0),
                )
            assertNull(repo.observeAll().first().single().latitude)
        }

    @Test
    fun presetLocationTakesPrecedenceOverCurrent() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save(
                    "Q1", capturedAt, 0.9f, ByteArray(4), "",
                    attachLocation = true, presetLocation = LatLng(40.0, -3.0),
                )
            val row = repo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(0, provider.currentCalls)
        }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.SaveObservationLocationTest"`
Expected: FAIL — compilation error, `save(...)` has no `presetLocation` parameter.

- [ ] **Step 3: Add the parameter + precedence logic**

In `SaveObservationUseCase.kt`, add the parameter to `save(...)` (after `attachLocation`):

```kotlin
        attachLocation: Boolean = false,
        presetLocation: LatLng? = null,
    ): SaveResult {
```

Replace the existing `latLng` block:

```kotlin
        val latLng: LatLng? =
            if (attachLocation && locationEnabled()) {
                runCatching { locationProvider?.current() }.getOrNull()
            } else {
                null
            }
```

with:

```kotlin
        val latLng: LatLng? =
            when {
                presetLocation != null && locationEnabled() -> presetLocation
                attachLocation && locationEnabled() -> runCatching { locationProvider?.current() }.getOrNull()
                else -> null
            }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.SaveObservationLocationTest"`
Expected: PASS (all 6 tests — 3 existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationLocationTest.kt
git commit -m "feat(map): SaveObservationUseCase presetLocation for EXIF geotag"
```

---

## Task 2: `ScanSource` origin model + serialization + common-code plumbing

Replace `live: Boolean` with `origin: ImageOrigin` + EXIF coords across `shared/ml` and all common-code call sites, and carry origin/EXIF through `PhotoAnalyzeViewModel → Loaded → PhotoAnalyzeScreen`. This is a cross-cutting refactor; do all steps before running the full suite so the build returns to green together.

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt`
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt`
- Modify: `shared/ml/src/commonTest/kotlin/se/birdy/ml/ScanSourceLiveSerializationTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeUiState.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/AttachLocationTest.kt`
- Modify: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModelTest.kt`

- [ ] **Step 1: Replace the `live` field with `origin` + EXIF coords + the `ImageOrigin` enum**

Rewrite `ScanSource.kt` in full:

```kotlin
package se.birdy.ml

/**
 * Unifies image-based and audio-based classification results for the Match-flow.
 * Both variants carry a [frameJpegPath] so the Match screen can display a thumbnail.
 */
sealed interface ScanSource {
    val frameJpegPath: String
    val classification: Classification

    data class Image(
        override val frameJpegPath: String,
        override val classification: Classification,
        val origin: ImageOrigin = ImageOrigin.LiveScan,
        val exifLatitude: Double? = null,
        val exifLongitude: Double? = null,
    ) : ScanSource

    data class Audio(
        override val frameJpegPath: String,
        override val classification: Classification,
        val audioWavPath: String,
    ) : ScanSource
}

/** Where a [ScanSource.Image] came from — drives how location is attached at save time. */
enum class ImageOrigin {
    /** Live camera "Look" scan — attach the current device location. */
    LiveScan,

    /** In-app take-photo — here-and-now, attach the current device location. */
    CameraCapture,

    /** Gallery upload — use the photo's EXIF GPS if present, never current location. */
    Gallery,
}
```

- [ ] **Step 2: Update serialization to carry origin + EXIF coords**

In `ScanSourceSerialization.kt`, replace the `live` field in the DTO with `origin` + the two coord fields:

```kotlin
@Serializable
data class ScanSourceSerialization(
    val type: String, // "image" | "audio"
    val frameJpegPath: String,
    val classification: ClassificationSerialization,
    val audioWavPath: String? = null,
    val origin: String = "LiveScan",
    val exifLatitude: Double? = null,
    val exifLongitude: Double? = null,
)
```

Replace the `ScanSource.Image ->` branch in `toSerial()`:

```kotlin
        is ScanSource.Image ->
            ScanSourceSerialization(
                type = "image",
                frameJpegPath = frameJpegPath,
                classification = classification.toSerial(),
                origin = origin.name,
                exifLatitude = exifLatitude,
                exifLongitude = exifLongitude,
            )
```

Replace the `"image" ->` branch in `toScanSource()`:

```kotlin
        "image" ->
            ScanSource.Image(
                frameJpegPath = frameJpegPath,
                classification = classification.toClassification(),
                origin = runCatching { ImageOrigin.valueOf(origin) }.getOrDefault(ImageOrigin.LiveScan),
                exifLatitude = exifLatitude,
                exifLongitude = exifLongitude,
            )
```

- [ ] **Step 3: Update the serialization round-trip test**

Replace the body of `ScanSourceLiveSerializationTest.kt` test with an origin + EXIF round-trip:

```kotlin
package se.birdy.ml

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanSourceLiveSerializationTest {
    @Test
    fun imageOriginAndExifSurviveRoundTrip() {
        val original =
            ScanSource.Image(
                frameJpegPath = "/f.jpg",
                classification = Classification(results = emptyList()),
                origin = ImageOrigin.Gallery,
                exifLatitude = 59.3293,
                exifLongitude = 18.0686,
            )
        val json = Json.encodeToString(ScanSourceSerialization.serializer(), original.toSerial())
        val restored = Json.decodeFromString<ScanSourceSerialization>(json).toScanSource()
        assertTrue(restored is ScanSource.Image)
        restored as ScanSource.Image
        assertEquals(ImageOrigin.Gallery, restored.origin)
        assertEquals(59.3293, restored.exifLatitude)
        assertEquals(18.0686, restored.exifLongitude)
    }
}
```

- [ ] **Step 4: Verify the shared/ml module is green**

Run: `./gradlew :shared:ml:jvmTest`
Expected: PASS (includes the rewritten round-trip test).

- [ ] **Step 5: Carry origin + EXIF through `PhotoAnalyzeUiState.Loaded`**

In `PhotoAnalyzeUiState.kt`, add the import and the new fields on `Loaded`:

```kotlin
import se.birdy.ml.ClassificationResult
import se.birdy.ml.ImageOrigin
```

```kotlin
    data class Loaded(
        val predictions: List<ClassificationResult>,
        val frameJpegPath: String,
        val capturedAtMs: Long,
        val origin: ImageOrigin = ImageOrigin.Gallery,
        val exifLatitude: Double? = null,
        val exifLongitude: Double? = null,
    ) : PhotoAnalyzeUiState
```

- [ ] **Step 6: Accept origin + EXIF in `PhotoAnalyzeViewModel.analyze`**

In `PhotoAnalyzeViewModel.kt`, add the import `import se.birdy.ml.ImageOrigin` and change the `analyze` signature + the `Loaded` construction:

```kotlin
    fun analyze(
        frame: ImageInput,
        origin: ImageOrigin = ImageOrigin.Gallery,
        exifLatitude: Double? = null,
        exifLongitude: Double? = null,
    ) {
```

```kotlin
            _state.value =
                PhotoAnalyzeUiState.Loaded(
                    predictions = classification.sortedByConfidenceDescending(),
                    frameJpegPath = path,
                    capturedAtMs = capturedAtMs,
                    origin = origin,
                    exifLatitude = exifLatitude,
                    exifLongitude = exifLongitude,
                )
```

(The default `ImageOrigin.Gallery` preserves today's "no current location" behavior for any caller that has not yet been updated — the Android host is wired in Task 4.)

- [ ] **Step 7: Build the `ScanSource.Image` from `Loaded` in `PhotoAnalyzeScreen`**

In `PhotoAnalyzeScreen.kt`, replace the `scanSource` construction inside the `LaunchedEffect` (the `live = false` block):

```kotlin
            val scanSource =
                ScanSource.Image(
                    frameJpegPath = s.frameJpegPath,
                    classification = classification,
                    origin = s.origin,
                    exifLatitude = s.exifLatitude,
                    exifLongitude = s.exifLongitude,
                )
```

- [ ] **Step 8: Update the live-scan call site in `ScanScreen`**

In `ScanScreen.kt`, add `import se.birdy.ml.ImageOrigin` and replace `live = true` with `origin = ImageOrigin.LiveScan`:

```kotlin
            val scanSource =
                ScanSource.Image(
                    frameJpegPath = s.frameJpegPath,
                    classification = classification,
                    origin = ImageOrigin.LiveScan,
                )
```

- [ ] **Step 9: Update `shouldAttachLocation` to read origin**

In `MatchResultViewModel.kt`, add `import se.birdy.ml.ImageOrigin` and replace the function:

```kotlin
/** Audio + live/camera captures attach the current location; gallery uploads use EXIF instead. */
fun shouldAttachLocation(source: ScanSource): Boolean =
    when (source) {
        is ScanSource.Audio -> true
        is ScanSource.Image -> source.origin != ImageOrigin.Gallery
    }
```

- [ ] **Step 10: Update `AttachLocationTest` for the origin enum**

Rewrite `AttachLocationTest.kt`:

```kotlin
package se.birdy.app.ui.match

import se.birdy.ml.Classification
import se.birdy.ml.ImageOrigin
import se.birdy.ml.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachLocationTest {
    private val cls = Classification(results = emptyList())

    @Test
    fun liveImageAttaches() =
        assertEquals(true, shouldAttachLocation(ScanSource.Image("/f.jpg", cls, origin = ImageOrigin.LiveScan)))

    @Test
    fun cameraCaptureAttaches() =
        assertEquals(true, shouldAttachLocation(ScanSource.Image("/f.jpg", cls, origin = ImageOrigin.CameraCapture)))

    @Test
    fun galleryImageDoesNotAttach() =
        assertEquals(false, shouldAttachLocation(ScanSource.Image("/f.jpg", cls, origin = ImageOrigin.Gallery)))

    @Test
    fun audioAttaches() = assertEquals(true, shouldAttachLocation(ScanSource.Audio("/f.jpg", cls, "/a.wav")))
}
```

- [ ] **Step 11: Add a `PhotoAnalyzeViewModel` test that origin + EXIF reach `Loaded`**

In `PhotoAnalyzeViewModelTest.kt`, add `import se.birdy.ml.ImageOrigin` and this test inside the class:

```kotlin
    @Test
    fun analyze_carries_origin_and_exif_into_loaded() =
        runTest(dispatcher) {
            val vm =
                PhotoAnalyzeViewModel(
                    classifier = FakeBirdClassifier(),
                    persist = { _ -> "/cache/photo-input/abc.jpg" },
                )
            vm.state.test {
                assertEquals(PhotoAnalyzeUiState.Idle, awaitItem())
                vm.analyze(acceptableFrame, origin = ImageOrigin.Gallery, exifLatitude = 59.3, exifLongitude = 18.0)
                assertEquals(PhotoAnalyzeUiState.Analyzing, awaitItem())
                val loaded = awaitItem()
                assertIs<PhotoAnalyzeUiState.Loaded>(loaded)
                assertEquals(ImageOrigin.Gallery, loaded.origin)
                assertEquals(59.3, loaded.exifLatitude)
                assertEquals(18.0, loaded.exifLongitude)
                cancelAndIgnoreRemainingEvents()
            }
        }
```

- [ ] **Step 12: Run the full common-code suites to verify green**

Run: `./gradlew :shared:ml:jvmTest :composeApp:testDebugUnitTest`
Expected: PASS. (`MatchResultViewModelTest.csvToScanSource` relies on the `ScanSource.Image` default, which is now `ImageOrigin.LiveScan` → still "attach location", so those scenarios are unaffected.)

- [ ] **Step 13: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt \
        shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt \
        shared/ml/src/commonTest/kotlin/se/birdy/ml/ScanSourceLiveSerializationTest.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeUiState.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModel.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt \
        composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/AttachLocationTest.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeViewModelTest.kt
git commit -m "feat(map): ScanSource origin enum + EXIF coords; carry through photo-analyze"
```

---

## Task 3: `MatchResultViewModel` passes the gallery EXIF preset to save

Wire the gallery image's EXIF coordinates into the save path as `presetLocation`, in both the normal save and save-as-unknown flows.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchResultViewModelTest.kt`

- [ ] **Step 1: Write the failing integration test**

In `MatchResultViewModelTest.kt`, add these imports (top of file, with the other imports):

```kotlin
import se.birdy.app.location.LatLng
import se.birdy.app.testing.FakeLocationProvider
import se.birdy.ml.ImageOrigin
```

Add this test inside the class:

```kotlin
    @Test
    fun saveToDiary_gallery_source_uses_exif_preset_location() =
        runTest(dispatcher) {
            val obsRepo = FakeObservationRepository()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val clock = FakeClock(now = Instant.parse("2026-05-12T10:00:00Z"))
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            val saveUseCase =
                SaveObservationUseCase(
                    repo = obsRepo,
                    badgeRepo = FakeBadgeRepository(),
                    photoStorage = FakePhotoStorage(),
                    clock = clock,
                    catalog = emptyCatalog(),
                    recalculate = RecalculateBadgesUseCase(zone = TimeZone.UTC, clock = clock),
                    speciesByQid = { speciesRepo.allByQid(Locale.SV) },
                    locationProvider = provider,
                    locationEnabled = { true },
                )

            val tmpFile = File.createTempFile("birdy-test-frame", ".jpg")
            tmpFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01))

            val gallerySource =
                ScanSource.Image(
                    frameJpegPath = tmpFile.absolutePath,
                    classification = Classification(results = listOf(ClassificationResult("Q25485", 0.87f))),
                    origin = ImageOrigin.Gallery,
                    exifLatitude = 40.0,
                    exifLongitude = -3.0,
                )
            val vm =
                MatchResultViewModel(
                    repository = speciesRepo,
                    observationRepo = obsRepo,
                    saveUseCase = saveUseCase,
                    catalog = emptyCatalog(),
                    source = gallerySource,
                    capturedAtMs = capturedAtMs,
                    locale = Locale.SV,
                )

            vm.state.test {
                var item = awaitItem()
                while (item is MatchResultUiState.Loading) item = awaitItem()
                assertIs<MatchResultUiState.Match>(item)
                vm.saveToDiary()
                while (true) {
                    val n = awaitItem() as? MatchResultUiState.Match ?: continue
                    if (n.saveStatus == MatchResultUiState.SaveStatus.Saved) break
                }
                cancelAndIgnoreRemainingEvents()
            }

            val row = obsRepo.observeAll().first().single()
            assertEquals(40.0, row.latitude)
            assertEquals(-3.0, row.longitude)
            assertEquals(0, provider.currentCalls)
            tmpFile.delete()
        }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.match.MatchResultViewModelTest.saveToDiary_gallery_source_uses_exif_preset_location"`
Expected: FAIL — `row.latitude` is null (preset not yet plumbed; save currently passes no `presetLocation`).

- [ ] **Step 3: Add the preset helper + pass it at both save sites**

In `MatchResultViewModel.kt`, add `import se.birdy.app.location.LatLng` and, next to `shouldAttachLocation`, add:

```kotlin
/** Pre-resolved EXIF coordinates for a gallery image, else null (live/camera/audio use current()). */
fun presetLocationFor(source: ScanSource): LatLng? =
    (source as? ScanSource.Image)?.let { img ->
        val lat = img.exifLatitude
        val lng = img.exifLongitude
        if (lat != null && lng != null) LatLng(lat, lng) else null
    }
```

In `saveToDiary`, in the `saveUseCase.save(...)` call, add the argument right after `attachLocation = shouldAttachLocation(current.source),`:

```kotlin
                        attachLocation = shouldAttachLocation(current.source),
                        presetLocation = presetLocationFor(current.source),
```

In `saveAsUnknown`, in its `saveUseCase.save(...)` call, add the same argument after `attachLocation = shouldAttachLocation(current.source),`:

```kotlin
                    attachLocation = shouldAttachLocation(current.source),
                    presetLocation = presetLocationFor(current.source),
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.match.MatchResultViewModelTest.saveToDiary_gallery_source_uses_exif_preset_location"`
Expected: PASS.

- [ ] **Step 5: Run the full composeApp suite**

Run: `./gradlew :composeApp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/MatchResultViewModelTest.kt
git commit -m "feat(map): pass gallery EXIF preset location into save"
```

---

## Task 4: Android host wires origin per launcher + reads gallery EXIF GPS

The host already has two distinct launchers. Tag each captured URI with its origin (gallery vs in-app camera), read EXIF lat/lng for gallery picks, and pass everything into `viewModel.analyze(...)`. This is Android-only (no JVM unit test) — verified on device in Task 5.

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt`

- [ ] **Step 1: Add the `ImageOrigin` import + origin/EXIF state**

Add the import (with the other `se.birdy.ml.*` imports):

```kotlin
import se.birdy.ml.ImageOrigin
```

Inside `PhotoAnalyzeHost`, just after the `pendingDecodeUri` declaration, add:

```kotlin
    val pendingOrigin =
        remember {
            mutableStateOf(ImageOrigin.Gallery)
        }
    var captureLat by remember { mutableStateOf<Double?>(null) }
    var captureLng by remember { mutableStateOf<Double?>(null) }
```

- [ ] **Step 2: Resolve EXIF + pass origin into both `analyze` calls in the decode effect**

Replace the body of `LaunchedEffect(pendingDecodeUri.value) { ... }` with:

```kotlin
    LaunchedEffect(pendingDecodeUri.value) {
        val uri = pendingDecodeUri.value ?: return@LaunchedEffect
        viewModel.markAnalyzing()
        val origin = pendingOrigin.value
        if (origin == ImageOrigin.Gallery) {
            val latLng = readExifLatLng(context, uri)
            captureLat = latLng?.first
            captureLng = latLng?.second
        } else {
            captureLat = null
            captureLng = null
        }
        val bmp = decodeForCrop(context, uri)
        pendingDecodeUri.value = null
        if (bmp == null) {
            viewModel.decodeFailed()
            return@LaunchedEffect
        }
        if (minOf(bmp.width, bmp.height) < MIN_SHORT_SIDE_PX) {
            // För liten för meningsfull crop → analysera hela (TooSmall fångar).
            val input = withContext(Dispatchers.IO) { finalizeCrop(bmp, CropGeometry.fullRect(bmp.width, bmp.height)) }
            bmp.recycle()
            viewModel.analyze(input, origin, captureLat, captureLng)
            return@LaunchedEffect
        }
        viewModel.reset() // dölj "Analyzing" medan crop-skärmen visas
        cropBitmap = bmp
    }
```

- [ ] **Step 3: Tag the origin in each launcher callback**

Replace the `galleryLauncher` callback body:

```kotlin
        ) { uri ->
            if (uri != null) {
                pendingOrigin.value = ImageOrigin.Gallery
                pendingDecodeUri.value = uri
            }
        }
```

Replace the `takePhotoLauncher` callback body:

```kotlin
        ) { success ->
            val uri = pendingTakeUri.value
            if (success && uri != null) {
                pendingOrigin.value = ImageOrigin.CameraCapture
                pendingDecodeUri.value = uri
            }
        }
```

- [ ] **Step 4: Pass origin + EXIF into the crop-confirm `analyze` call**

In the `CropAdjustScreen(onConfirm = { rect -> ... })` block, change the `viewModel.analyze(input)` line to:

```kotlin
                    val input = withContext(Dispatchers.IO) { finalizeCrop(toFinalize, rect) }
                    toFinalize.recycle()
                    viewModel.analyze(input, pendingOrigin.value, captureLat, captureLng)
```

- [ ] **Step 5: Add the `readExifLatLng` helper**

Add this private function near `readExifRotation` at the bottom of the file:

```kotlin
/** Reads decimal lat/lng from the image's EXIF GPS tags; null when absent or unreadable. */
private suspend fun readExifLatLng(
    context: Context,
    uri: Uri,
): Pair<Double, Double>? =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).latLong?.let { it[0] to it[1] }
            }
        }.getOrNull()
    }
```

- [ ] **Step 6: Compile + lint**

Run: `./gradlew :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL. (If ktlint flags formatting, run `./gradlew ktlintFormat` and re-run.)

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt
git commit -m "feat(map): host wires take-photo current location + gallery EXIF GPS"
```

---

## Task 5: Full local sweep + device-verify

**Files:** none (verification only).

- [ ] **Step 1: Run lint + the full local test sweep**

```bash
./gradlew ktlintCheck detekt
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest
```
Expected: all green.

- [ ] **Step 2: Install on device**

> First ask Albin to silence notifications / enable Do-Not-Disturb (SM-S918B is his daily phone). Confirm a real `MAPTILER_API_KEY` is in local `gradle.properties`. Debug package: `se.birdy.android.debug`.

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 3: Device-verify the three new paths (toggle ON)**

Enable the location toggle in Settings first (this also grants the runtime permission).

1. **Gallery photo WITH GPS EXIF** (e.g. a photo taken outdoors by the phone camera with location on) → Identify → pick from gallery → crop → save the match → open **Karta**: a pin appears at the photo's location.
2. **Gallery photo WITHOUT GPS EXIF** (e.g. a screenshot or stripped image) → save → no crash; saved find has no pin.
3. **In-app take photo** → Identify → take photo → crop → save → open **Karta**: a pin appears at the current location.

Capture screencaps for the backlog. (Note: a real bird match is not required for the location plumbing — but saving needs a Match-routed prediction; use a clear bird photo or the disambig "save as unknown" path, which also carries the preset.)

- [ ] **Step 4: Update the backlog + auto-memory**

Mark item 4 DONE in `docs/superpowers/plans/2026-06-07-map-polish-v2-backlog.md` and update memory `project_map_polish_v2.md`. Commit.

```bash
git add docs/superpowers/plans/2026-06-07-map-polish-v2-backlog.md
git commit -m "docs(map): item 4 (geotag non-live captures) DONE + device-verified"
```

---

## Self-review notes (already reconciled)

- **Spec coverage:** 4a (Task 4 sets `CameraCapture` → `current()` via Task 2's `shouldAttachLocation`); 4b (Task 4 reads EXIF → Task 2 carries coords → Task 3 preset → Task 1 uses it); opt-in gating (Task 1 gates both branches on `locationEnabled()`); silent no-GPS (preset stays null → null location, no UI). All covered.
- **Type consistency:** `ImageOrigin {LiveScan, CameraCapture, Gallery}`, `ScanSource.Image(origin, exifLatitude, exifLongitude)`, `PhotoAnalyzeUiState.Loaded(origin, exifLatitude, exifLongitude)`, `PhotoAnalyzeViewModel.analyze(frame, origin, exifLatitude, exifLongitude)`, `save(..., presetLocation)`, `presetLocationFor(source): LatLng?`, `readExifLatLng(context, uri): Pair<Double, Double>?` — consistent across tasks.
- **No placeholders:** every code step shows the full snippet to insert/replace.
