# Karta över mina fynd (Feature A) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a private, on-device map of the user's own bird observations — opt-in location capture (free), premium-gated map view — without breaking the "data stays on phone" promise.

**Architecture:** Location is captured at save-time via an injected `LocationProvider` (Android `LocationManager`, no Play Services), gated by a free Settings toggle, and only for live captures (live camera scan + audio), never gallery uploads. Observations already carry `latitude`/`longitude` columns. A new 5th bottom-nav tab "Karta" renders saved pins on an osmdroid map fed by commercial MapTiler tiles; the map view is premium-gated with a teaser for free users.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, SQLDelight, osmdroid 6.1.20 (Android), MapTiler raster tiles, DataStore, Android `LocationManager`.

**Branch:** `feat/personal-finds-map` (fast-follow after v1.1 GA — do NOT fold into the vC122 train).

**Spec:** `docs/superpowers/specs/2026-06-05-personal-finds-map-design.md`

**Conventions for every task:** Java env prefix for gradle in bash:
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```
Fast shared-module unit tests: `./gradlew :composeApp:testDebugUnitTest` (and `:shared:data:jvmTest` where noted).

---

## File Structure

**New files:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/location/LocationProvider.kt` — `LatLng` + `LocationProvider` interface.
- `composeApp/src/androidMain/kotlin/se/birdy/app/location/AndroidLocationProvider.kt` — `LocationManager` one-shot impl.
- `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeLocationProvider.kt` — test double.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPin.kt` — `MapPin` + `MapPinMapper`.
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapPinMapperTest.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapViewModel.kt` — `MapUiState` + `MapViewModel`.
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapViewModelTest.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapScreen.kt` — common scaffold + empty state + `expect fun MapScreenHost`.
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt` — teaser for free users.
- `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt` — osmdroid `actual`.

**Modified files:**
- `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`
- `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt` + `ScanSourceSerialization.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`
- `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt` + `InMemoryUserPreferences.kt` + `androidMain/.../UserPreferencesStore.android.kt`
- `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`
- `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt` + `androidApp/src/main/AndroidManifest.xml`
- `composeApp/.../ui/settings/SettingsViewModel.kt` + `SettingsScreen.kt`
- `composeApp/.../ui/scaffold/AppRoute.kt` + `BottomNavBar.kt` + `AppScaffold.kt`
- `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`
- `gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `androidApp/build.gradle.kts`, `gradle.properties`
- `docs/play-store/data-safety-form.md` + privacy policy markdown.

---

## Task 1: `LatLng` + `LocationProvider` interface + fake

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/location/LocationProvider.kt`
- Create: `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeLocationProvider.kt`

Pure declarations — no behavioral test (the fake is exercised by later tasks).

- [ ] **Step 1: Create the interface + value type**

`LocationProvider.kt`:
```kotlin
package se.birdy.app.location

/** A geographic coordinate. Named LatLng to avoid clashing with osmdroid's GeoPoint. */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * One-shot device location. Android actual uses LocationManager (no Play Services).
 * Returns null when permission is missing, location is off, or the fix times out —
 * implementations MUST NOT throw.
 */
interface LocationProvider {
    suspend fun current(): LatLng?
}
```

- [ ] **Step 2: Create the test double**

`FakeLocationProvider.kt`:
```kotlin
package se.birdy.app.testing

import se.birdy.app.location.LatLng
import se.birdy.app.location.LocationProvider

class FakeLocationProvider(
    var next: LatLng? = null,
    var currentCalls: Int = 0,
) : LocationProvider {
    override suspend fun current(): LatLng? {
        currentCalls += 1
        return next
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/location/LocationProvider.kt composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeLocationProvider.kt
git commit -m "feat(map): add LocationProvider interface + LatLng + fake"
```

---

## Task 2: `MapPin` + `MapPinMapper` (pure, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPin.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapPinMapperTest.kt`

- [ ] **Step 1: Write the failing test**

`MapPinMapperTest.kt`:
```kotlin
package se.birdy.app.ui.map

import kotlinx.datetime.Instant
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals

class MapPinMapperTest {
    private fun obs(
        id: String,
        lat: Double?,
        lng: Double?,
    ) = Observation(
        id = id,
        speciesId = "Q123",
        capturedAt = Instant.fromEpochMilliseconds(1000),
        savedAt = Instant.fromEpochMilliseconds(1000),
        photoPath = "/p/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = lat,
        longitude = lng,
        locationLabel = null,
        stampNumber = 1,
    )

    @Test
    fun dropsObservationsWithoutBothCoordinates() {
        val pins =
            MapPinMapper.toPins(
                listOf(
                    obs("a", 59.3, 18.0),
                    obs("b", null, 18.0),
                    obs("c", 59.3, null),
                    obs("d", null, null),
                ),
            )
        assertEquals(listOf("a"), pins.map { it.observationId })
    }

    @Test
    fun mapsCoordinatesAndPhoto() {
        val pins = MapPinMapper.toPins(listOf(obs("a", 59.3293, 18.0686)))
        val pin = pins.single()
        assertEquals(59.3293, pin.latitude)
        assertEquals(18.0686, pin.longitude)
        assertEquals("/p/a.jpg", pin.photoPath)
        assertEquals("Q123", pin.speciesId)
        assertEquals(1, pin.stampNumber)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapPinMapperTest"`
Expected: FAIL (unresolved `MapPin`/`MapPinMapper`).

- [ ] **Step 3: Implement**

`MapPin.kt`:
```kotlin
package se.birdy.app.ui.map

import se.birdy.domain.observation.Observation

/** A single observation rendered on the map. */
data class MapPin(
    val observationId: String,
    val latitude: Double,
    val longitude: Double,
    val speciesId: String?,
    val stampNumber: Int,
    val photoPath: String,
)

/** Pure mapping from observations to map pins. Drops rows without both coordinates. */
object MapPinMapper {
    fun toPins(observations: List<Observation>): List<MapPin> =
        observations.mapNotNull { o ->
            val lat = o.latitude
            val lng = o.longitude
            if (lat == null || lng == null) {
                null
            } else {
                MapPin(
                    observationId = o.id,
                    latitude = lat,
                    longitude = lng,
                    speciesId = o.speciesId,
                    stampNumber = o.stampNumber,
                    photoPath = o.photoPath,
                )
            }
        }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapPinMapperTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPin.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapPinMapperTest.kt
git commit -m "feat(map): MapPin + pure MapPinMapper"
```

---

## Task 3: Location capture in `SaveObservationUseCase` (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationLocationTest.kt` (new)

The use case gains two constructor deps (`locationProvider`, `locationEnabled`) and `save()` gains `attachLocation: Boolean = false`. It builds the `Observation` **once** (removing the current duplicated construction at lines 45-61 and 68-83) and populates lat/lng only when `attachLocation && locationEnabled()`.

- [ ] **Step 1: Write the failing test**

`SaveObservationLocationTest.kt`:
```kotlin
package se.birdy.app.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.app.location.LatLng
import se.birdy.app.testing.FakeLocationProvider
import se.birdy.app.testing.FakeObservationRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SaveObservationLocationTest {
    private val capturedAt = Instant.fromEpochMilliseconds(1000)

    private fun useCase(
        repo: FakeObservationRepository,
        provider: FakeLocationProvider,
        enabled: Boolean,
    ) = SaveObservationUseCase(
        repo = repo,
        badgeRepo = NoopBadgeRepository(),
        photoStorage = RecordingPhotoStorage(),
        clock = FixedClock(capturedAt),
        catalog = emptyBadgeCatalog(),
        recalculate = noopRecalculate(),
        speciesByQid = { emptyMap() },
        locationProvider = provider,
        locationEnabled = { enabled },
    )

    @Test
    fun attachesLocationWhenEnabledAndRequested() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            val row = repo.observeAll().first().single()
            assertEquals(59.3, row.latitude)
            assertEquals(18.0, row.longitude)
        }

    @Test
    fun noLocationWhenAttachFalse() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = true)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = false)
            assertNull(repo.observeAll().first().single().latitude)
            assertEquals(0, provider.currentCalls)
        }

    @Test
    fun noLocationWhenToggleDisabled() =
        runTest {
            val repo = FakeObservationRepository()
            val provider = FakeLocationProvider(next = LatLng(59.3, 18.0))
            useCase(repo, provider, enabled = false)
                .save("Q1", capturedAt, 0.9f, ByteArray(4), "", attachLocation = true)
            assertNull(repo.observeAll().first().single().latitude)
        }
}
```

> **Note for implementer:** reuse the existing test helpers in `SaveObservationUseCaseTest.kt` (`NoopBadgeRepository`, `RecordingPhotoStorage`, `FixedClock`, `emptyBadgeCatalog()`, `noopRecalculate()`). If any are `private` to that file, lift them into a small shared `composeApp/src/commonTest/kotlin/se/birdy/app/usecase/SaveObservationTestFixtures.kt` first (read `SaveObservationUseCaseTest.kt` to copy their exact bodies), then reference from both test files. Do not duplicate the bodies inline.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.SaveObservationLocationTest"`
Expected: FAIL (no `locationProvider`/`locationEnabled` params, no `attachLocation`).

- [ ] **Step 3: Implement — new deps, single Observation build, location capture**

In `SaveObservationUseCase.kt`, add imports:
```kotlin
import se.birdy.app.location.LatLng
import se.birdy.app.location.LocationProvider
```

Add two constructor params (after `dailyBirdMatchCount`):
```kotlin
    private val locationProvider: LocationProvider? = null,
    private val locationEnabled: suspend () -> Boolean = { false },
```

Add `attachLocation` to `save()` (after `sourceType`):
```kotlin
        sourceType: ObservationSource = ObservationSource.Photo,
        attachLocation: Boolean = false,
    ): SaveResult {
```

Replace the body from `val id = ...` through the second `onObservationSaved?.invoke(...)` block with this single-build version:
```kotlin
        val id = Uuid.random().toString()
        val nextStamp = repo.nextStampNumber()
        val photoPath = photoStorage.persistJpeg(rawJpegBytes)

        val latLng: LatLng? =
            if (attachLocation && locationEnabled()) {
                runCatching { locationProvider?.current() }.getOrNull()
            } else {
                null
            }

        val observation =
            Observation(
                id = id,
                speciesId = speciesId,
                capturedAt = capturedAt,
                savedAt = clock.now(),
                photoPath = photoPath,
                note = note,
                confidence = confidence,
                latitude = latLng?.latitude,
                longitude = latLng?.longitude,
                locationLabel = null,
                stampNumber = nextStamp,
                audioPath = audioPath,
                sourceType = sourceType,
            )

        try {
            repo.insert(observation)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            runCatching { photoStorage.delete(photoPath) }
            throw t
        }

        onObservationSaved?.invoke(observation)
```

(Leave the `newUnlocks` block and `return SaveResult(...)` unchanged.)

- [ ] **Step 4: Run the new + existing use-case tests**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.usecase.*"`
Expected: PASS (existing `SaveObservationUseCaseTest` still green — new params default; `save()` new param defaults `false`).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/usecase/SaveObservationUseCase.kt composeApp/src/commonTest/kotlin/se/birdy/app/usecase/
git commit -m "feat(map): capture location in SaveObservationUseCase (opt-in, gated)"
```

---

## Task 4: `ScanSource.Image.live` + serialization + producers (TDD)

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt`
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt` (line ~109)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt` (line ~66)
- Test: `shared/ml/src/commonTest/kotlin/se/birdy/ml/ScanSourceLiveSerializationTest.kt` (new)

`live = true` means "captured live here-and-now" (live camera scan, audio) → location-eligible. `live = false` means uploaded/from storage (gallery) → never location.

- [ ] **Step 1: Read the serialization file**

Run: read `shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt` in full to see the exact `ScanSourceSerialization` data class fields, the `toScanSourceSerialization()` (or equivalent encoder), and `toScanSource()` decoder shapes. You will add a `live` field mirroring the existing `frameJpegPath` field everywhere it appears for the `"image"` type.

- [ ] **Step 2: Write the failing test**

`ScanSourceLiveSerializationTest.kt`:
```kotlin
package se.birdy.ml

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanSourceLiveSerializationTest {
    @Test
    fun imageLiveFlagSurvivesRoundTrip() {
        val original =
            ScanSource.Image(
                frameJpegPath = "/f.jpg",
                classification = Classification(results = emptyList()),
                live = false,
            )
        val json = Json.encodeToString(original.toSerialization())
        val restored = Json.decodeFromString<ScanSourceSerialization>(json).toScanSource()
        assertTrue(restored is ScanSource.Image)
        assertEquals(false, (restored as ScanSource.Image).live)
    }
}
```

> **Note:** use the project's actual encoder name. The grep showed a `toScanSource()` decoder in `ScanSourceSerialization.kt`; find its inverse (likely `ScanSource.toSerialization()` / `toScanSourceSerialization()`) and call that in the test. Adjust `Classification(results = ...)` to the real constructor if different.

- [ ] **Step 3: Add the field**

In `ScanSource.kt`, add `live` to the `Image` variant (default `true` so existing constructions compile):
```kotlin
    data class Image(
        override val frameJpegPath: String,
        override val classification: Classification,
        val live: Boolean = true,
    ) : ScanSource
```

In `ScanSourceSerialization.kt`: add `val live: Boolean = true` to the serializable DTO, set it in the encoder for the image case, and pass it through in `toScanSource()`'s `ScanSource.Image(... , live = live)`.

- [ ] **Step 4: Set the flag at the producers**

`ScanScreen.kt` (~line 109) — live camera: make it explicit:
```kotlin
                ScanSource.Image(
                    frameJpegPath = s.frameJpegPath,
                    classification = classification,
                    live = true,
                )
```

`PhotoAnalyzeScreen.kt` (~line 66) — gallery/upload: exclude from location:
```kotlin
                ScanSource.Image(
                    frameJpegPath = s.frameJpegPath,
                    classification = classification,
                    live = false,
                )
```

- [ ] **Step 5: Run test + module compile**

Run: `./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.ScanSourceLiveSerializationTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSource.kt shared/ml/src/commonMain/kotlin/se/birdy/ml/ScanSourceSerialization.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/ScanSourceLiveSerializationTest.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeScreen.kt
git commit -m "feat(map): tag ScanSource.Image.live (gallery uploads excluded from location)"
```

---

## Task 5: `MatchResultViewModel` derives + passes `attachLocation` (TDD)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/AttachLocationTest.kt` (new)

Extract a pure top-level helper so the rule is unit-testable without the full VM/use-case.

- [ ] **Step 1: Write the failing test**

`AttachLocationTest.kt`:
```kotlin
package se.birdy.app.ui.match

import se.birdy.ml.Classification
import se.birdy.ml.ScanSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AttachLocationTest {
    private val cls = Classification(results = emptyList())

    @Test
    fun liveImageAttaches() =
        assertEquals(true, shouldAttachLocation(ScanSource.Image("/f.jpg", cls, live = true)))

    @Test
    fun galleryImageDoesNotAttach() =
        assertEquals(false, shouldAttachLocation(ScanSource.Image("/f.jpg", cls, live = false)))

    @Test
    fun audioAttaches() =
        assertEquals(true, shouldAttachLocation(ScanSource.Audio("/f.jpg", cls, "/a.wav")))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.match.AttachLocationTest"`
Expected: FAIL (unresolved `shouldAttachLocation`).

- [ ] **Step 3: Implement the helper + wire both save sites**

At the top level of `MatchResultViewModel.kt` (outside the class), add:
```kotlin
/** Audio + live camera captures attach location; gallery uploads never do. */
fun shouldAttachLocation(source: ScanSource): Boolean =
    when (source) {
        is ScanSource.Audio -> true
        is ScanSource.Image -> source.live
    }
```

In `saveToDiary(...)`, in the `saveUseCase.save(...)` call (line ~217), add:
```kotlin
                        sourceType = sourceType,
                        attachLocation = shouldAttachLocation(current.source),
                    )
```

In `saveAsUnknown()`, in the `saveUseCase.save(...)` call (line ~272), add the same `attachLocation = shouldAttachLocation(current.source),` argument after `sourceType = sourceType,`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.match.AttachLocationTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/match/MatchResultViewModel.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/match/AttachLocationTest.kt
git commit -m "feat(map): MatchResultViewModel passes attachLocation per source"
```

---

## Task 6: `UserPreferences.locationCaptureEnabled` (TDD)

**Files:**
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/UserPreferences.kt`
- Modify: `shared/datastore/src/commonMain/kotlin/se/birdy/datastore/InMemoryUserPreferences.kt`
- Modify: `shared/datastore/src/androidMain/kotlin/se/birdy/datastore/UserPreferencesStore.android.kt`
- Test: `shared/datastore/src/commonTest/kotlin/se/birdy/datastore/LocationCapturePrefTest.kt` (new — create dir if missing)

- [ ] **Step 1: Write the failing test**

`LocationCapturePrefTest.kt`:
```kotlin
package se.birdy.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocationCapturePrefTest {
    @Test
    fun defaultsFalseThenTogglesTrue() =
        runTest {
            val prefs = InMemoryUserPreferences()
            assertEquals(false, prefs.locationCaptureEnabled.first())
            prefs.setLocationCaptureEnabled(true)
            assertEquals(true, prefs.locationCaptureEnabled.first())
        }
}
```

> If `shared/datastore` has no `commonTest` source set wired, mirror an existing datastore test's location/`build.gradle.kts` test deps; otherwise place this test next to existing datastore tests.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:datastore:jvmTest --tests "se.birdy.datastore.LocationCapturePrefTest"`
Expected: FAIL (unresolved member).

- [ ] **Step 3: Implement across the three impls**

`UserPreferences.kt` interface — add near the other `Flow<Boolean>` members:
```kotlin
    val locationCaptureEnabled: Flow<Boolean>
```
and near the other setters:
```kotlin
    suspend fun setLocationCaptureEnabled(value: Boolean)
```

`UserPreferencesStore.android.kt`:
- In `object Keys`: `val LOCATION_CAPTURE_ENABLED = booleanPreferencesKey("location_capture_enabled")`
- Getter: `override val locationCaptureEnabled: Flow<Boolean> = safeData.map { it[Keys.LOCATION_CAPTURE_ENABLED] ?: false }`
- Setter:
```kotlin
    override suspend fun setLocationCaptureEnabled(value: Boolean) {
        store.edit { it[Keys.LOCATION_CAPTURE_ENABLED] = value }
    }
```

`InMemoryUserPreferences.kt` — follow its existing `MutableStateFlow`-per-pref pattern (read the file): add a backing `MutableStateFlow(false)`, expose it as `locationCaptureEnabled`, and implement `setLocationCaptureEnabled { it.value = value }`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:datastore:jvmTest --tests "se.birdy.datastore.LocationCapturePrefTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/datastore/
git commit -m "feat(map): add locationCaptureEnabled preference (default off)"
```

---

## Task 7: `AndroidLocationProvider` (LocationManager, no Play Services)

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/location/AndroidLocationProvider.kt`

No unit test (Android framework); exercised in device-verify (Task 17).

- [ ] **Step 1: Implement**

```kotlin
package se.birdy.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * One-shot device location via the platform LocationManager — no Google Play Services.
 * Returns null when permission is missing, no provider is enabled, or no fix arrives in time.
 */
class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {
    override suspend fun current(): LatLng? {
        if (!hasPermission()) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider =
            when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return null
            }
        val location =
            withTimeoutOrNull(8_000L) {
                requestSingleFix(lm, provider)
            } ?: lastKnown(lm)
        return location?.let { LatLng(it.latitude, it.longitude) }
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleFix(
        lm: LocationManager,
        provider: String,
    ): Location? =
        suspendCancellableCoroutine { cont ->
            val executor = Executors.newSingleThreadExecutor()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                lm.getCurrentLocation(provider, signal, executor) { loc ->
                    if (cont.isActive) cont.resume(loc)
                }
                cont.invokeOnCancellation { signal.cancel(); executor.shutdownNow() }
            } else {
                val listener =
                    object : android.location.LocationListener {
                        override fun onLocationChanged(loc: Location) {
                            if (cont.isActive) cont.resume(loc)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) {}

                        override fun onProviderEnabled(p: String) {}

                        override fun onProviderDisabled(p: String) {}
                    }
                lm.requestSingleUpdate(provider, listener, null)
                cont.invokeOnCancellation { lm.removeUpdates(listener); executor.shutdownNow() }
            }
        }

    @Suppress("MissingPermission")
    private fun lastKnown(lm: LocationManager): Location? =
        runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/location/AndroidLocationProvider.kt
git commit -m "feat(map): AndroidLocationProvider one-shot via LocationManager"
```

---

## Task 8: AppGraph wiring (location into use case + permission hook)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt`

- [ ] **Step 1: Add constructor params**

Add an import:
```kotlin
import se.birdy.app.location.LocationProvider
```
Add to the `AppGraph(` constructor (near `cameraSourceFactory`):
```kotlin
    val locationProvider: LocationProvider? = null,
```
Add near the other platform lambdas (e.g. after `audioStorageDir`):
```kotlin
    /** Requests ACCESS_FINE_LOCATION via an Activity launcher; null on tests/non-Android. */
    val requestLocationPermission: (() -> Unit)? = null,
```

- [ ] **Step 2: Thread into the use case**

In the `saveObservationUseCase` construction (line ~245), add at the end of the argument list:
```kotlin
            dailyBirdMatchCount = { dailyBirdHistory?.totalMatchCount() ?: 0 },
            locationProvider = locationProvider,
            locationEnabled = { userPreferences.locationCaptureEnabled.first() },
        )
```
(`first` is already imported at the top of AppGraph.kt.)

- [ ] **Step 3: Compile**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(map): wire LocationProvider + permission hook into AppGraph"
```

---

## Task 9: MainActivity wiring + AndroidManifest permission

**Files:**
- Modify: `androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt`
- Modify: `androidApp/src/main/AndroidManifest.xml`

- [ ] **Step 1: Manifest permissions**

In `AndroidManifest.xml`, alongside the existing `<uses-permission>` lines (after RECORD_AUDIO):
```xml
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] **Step 2: Add a location permission launcher**

In `MainActivity`, near the existing `requestPermLauncher` (line ~87), add:
```kotlin
    private val requestLocationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* granted: capture stays graceful either way */ }
```

- [ ] **Step 3: Pass deps into AppGraph**

In `buildAppGraph()`'s `return AppGraph(` (line ~345), add:
```kotlin
            cameraSourceFactory = { AndroidCameraSource(applicationContext, this@MainActivity) },
            locationProvider = se.birdy.app.location.AndroidLocationProvider(applicationContext),
            requestLocationPermission = {
                requestLocationPermLauncher.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
            },
```
(Place these among the existing named args; order doesn't matter.)

- [ ] **Step 4: Build the Android app**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add androidApp/src/main/kotlin/se/birdy/android/MainActivity.kt androidApp/src/main/AndroidManifest.xml
git commit -m "feat(map): MainActivity wires AndroidLocationProvider + location permission launcher"
```

---

## Task 10: Settings toggle (ViewModel + Screen + strings) (TDD for VM)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsViewModel.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt` (Settings composable — pass the permission callback)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/SettingsLocationToggleTest.kt` (new)

- [ ] **Step 1: Read** `SettingsViewModel.kt` + the toggle section of `SettingsScreen.kt` to copy the exact `dailyBirdPushEnabled`/`setDailyBirdPushEnabled` exposure pattern (StateFlow + setter calling `prefs.set...`).

- [ ] **Step 2: Write the failing VM test**

`SettingsLocationToggleTest.kt`:
```kotlin
package se.birdy.app.ui.settings

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import se.birdy.datastore.InMemoryUserPreferences
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsLocationToggleTest {
    @Test
    fun togglePersistsToPrefs() =
        runTest {
            val prefs = InMemoryUserPreferences()
            val vm = settingsViewModelForTest(prefs) // see note
            vm.setLocationCaptureEnabled(true)
            assertEquals(true, prefs.locationCaptureEnabled.first())
        }
}
```

> **Note:** construct `SettingsViewModel` the same way existing `SettingsViewModel` tests do (or inline its constructor with `InMemoryUserPreferences` + any other required fakes). Replace `settingsViewModelForTest(prefs)` with the real constructor call once you read the class. If no existing Settings VM test exists, build the VM directly with its constructor args, passing fakes for non-pref deps.

- [ ] **Step 3: Implement VM members**

In `SettingsViewModel.kt`, mirroring `dailyBirdPushEnabled`:
```kotlin
    val locationCaptureEnabled: StateFlow<Boolean> =
        prefs.locationCaptureEnabled.stateIn(scope, SharingStarted.Eagerly, false)

    fun setLocationCaptureEnabled(value: Boolean) {
        scope.launch { prefs.setLocationCaptureEnabled(value) }
    }
```
(Match the exact `scope`/`SharingStarted` names used by the neighbouring flows in the file.)

- [ ] **Step 4: Run VM test**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.settings.SettingsLocationToggleTest"`
Expected: PASS.

- [ ] **Step 5: Add strings**

`values/strings.xml` (SV):
```xml
<string name="settings_location_section">Plats</string>
<string name="settings_toggle_location">Spara plats med mina fynd</string>
<string name="settings_location_caption">Lagras bara på din telefon. Behövs för kartan.</string>
```
`values-en/strings.xml` (EN):
```xml
<string name="settings_location_section">Location</string>
<string name="settings_toggle_location">Save location with my finds</string>
<string name="settings_location_caption">Stored only on your phone. Needed for the map.</string>
```

- [ ] **Step 6: Add the toggle row to SettingsScreen**

`SettingsScreen` takes a new param `onRequestLocationPermission: () -> Unit` (add to its signature). Collect the flow and render a `ToggleRow` (reuse the private helper at lines ~468-515) alongside the existing toggles, plus a caption:
```kotlin
val locationEnabled by viewModel.locationCaptureEnabled.collectAsState()
// ... within the settings list, following the notification ToggleRow block:
DashedDivider()
ToggleRow(
    icon = Icons.Outlined.Place,
    label = stringResource(Res.string.settings_toggle_location),
    checked = locationEnabled,
    onCheckedChange = { enabled ->
        viewModel.setLocationCaptureEnabled(enabled)
        if (enabled) onRequestLocationPermission()
    },
)
Text(
    text = stringResource(Res.string.settings_location_caption),
    color = MarginaliaInk,
    fontSize = 12.sp,
    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
)
```
Add the `Icons.Outlined.Place` import (`androidx.compose.material.icons.outlined.Place`) and ensure `MarginaliaInk`, `collectAsState`, `sp`, `stringResource`, `Res` imports exist (they do for the file's other rows).

- [ ] **Step 7: Pass the callback from AppScaffold**

In `AppScaffold.kt`'s `composable<AppRoute.Settings>` block, add to the `SettingsScreen(...)` call:
```kotlin
            onRequestLocationPermission = { graph.requestLocationPermission?.invoke() },
```

- [ ] **Step 8: Build + lint**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid ktlintCheck`
Expected: BUILD SUCCESSFUL, ktlint clean.

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/ composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt composeApp/src/commonMain/composeResources/ composeApp/src/commonTest/kotlin/se/birdy/app/ui/settings/
git commit -m "feat(map): Settings location toggle (free, opt-in) + permission request"
```

---

## Task 11: `AppRoute.Map` + 5th bottom-nav tab + tab strings

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt`
- Modify: `values/strings.xml` + `values-en/strings.xml`

- [ ] **Step 1: Add the route**

In `AppRoute.kt`, after `Archive`:
```kotlin
    @Serializable data object Map : AppRoute
```

- [ ] **Step 2: Add tab strings**

SV: `<string name="tab_map">Karta</string>` · EN: `<string name="tab_map">Map</string>`

- [ ] **Step 3: Add the tab**

In `BottomNavBar.kt`: import `androidx.compose.material.icons.outlined.Map` and `Res.string.tab_map`, then add to the `tabs` list (place after Lifelist or at the end):
```kotlin
        TabSpec(AppRoute.Map, Res.string.tab_map, Icons.Outlined.Map),
```

- [ ] **Step 4: Build**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (route screen wired in Task 15; tab navigates to a not-yet-registered route until then — acceptable mid-plan, but if the NavHost validates routes at build time it won't; registration lands in Task 15 before any run).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppRoute.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/BottomNavBar.kt composeApp/src/commonMain/composeResources/
git commit -m "feat(map): AppRoute.Map + 5th bottom-nav tab"
```

---

## Task 12: `MapViewModel` + `MapUiState` + AppGraph factory (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapViewModel.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapViewModelTest.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt` (add `mapViewModel()`)

- [ ] **Step 1: Write the failing test**

`MapViewModelTest.kt`:
```kotlin
package se.birdy.app.ui.map

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.domain.observation.Observation
import kotlin.test.Test
import kotlin.test.assertEquals

class MapViewModelTest {
    private fun obs(id: String, lat: Double?, lng: Double?) =
        Observation(
            id = id, speciesId = "Q1",
            capturedAt = Instant.fromEpochMilliseconds(1),
            savedAt = Instant.fromEpochMilliseconds(1),
            photoPath = "/$id.jpg", note = "", confidence = 1f,
            latitude = lat, longitude = lng, locationLabel = null, stampNumber = 1,
        )

    @Test
    fun exposesOnlyLocatedPins() =
        runTest {
            val repo = FakeObservationRepository()
            repo.insert(obs("a", 59.0, 18.0))
            repo.insert(obs("b", null, null))
            val vm = MapViewModel(repo)
            val state = vm.state.first { it.pins.isNotEmpty() || it.locatedCount == 0 }
            assertEquals(listOf("a"), state.pins.map { it.observationId })
            assertEquals(1, state.locatedCount)
        }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapViewModelTest"`
Expected: FAIL (unresolved `MapViewModel`).

- [ ] **Step 3: Implement**

`MapViewModel.kt` (mirror the `viewModelScope`/`stateIn` style of `LifelistViewModel` — read it for the exact base class + scope):
```kotlin
package se.birdy.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import se.birdy.domain.observation.ObservationRepository

data class MapUiState(
    val pins: List<MapPin> = emptyList(),
    val locatedCount: Int = 0,
)

class MapViewModel(
    observationRepo: ObservationRepository,
) : ViewModel() {
    val state: StateFlow<MapUiState> =
        observationRepo
            .observeAll()
            .map { all ->
                val pins = MapPinMapper.toPins(all)
                MapUiState(pins = pins, locatedCount = pins.size)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())
}
```

In `AppGraph.kt`, add a factory next to `seasonStatsViewModel()`:
```kotlin
    fun mapViewModel(): se.birdy.app.ui.map.MapViewModel =
        se.birdy.app.ui.map.MapViewModel(observationRepo = observationRepository)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapViewModel.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapViewModelTest.kt composeApp/src/commonMain/kotlin/se/birdy/app/di/AppGraph.kt
git commit -m "feat(map): MapViewModel exposes located pins + count"
```

---

## Task 13: `MapScreen` (common) + `expect MapScreenHost` + teaser + strings

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapScreen.kt`
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt`
- Modify: `values/strings.xml` + `values-en/strings.xml`

Use the Field Journal idiom (`paperBackground()`, `JournalIntro`/top bar) consistent with neighbouring screens — read `LifelistScreen.kt` for the exact scaffold/top-bar composables in use.

- [ ] **Step 1: Add strings**

SV (`values/strings.xml`):
```xml
<string name="map_title">Karta</string>
<string name="map_empty">Inga fynd med plats än. Slå på platsfångst i Inställningar och spara ett fynd ute i fält.</string>
<string name="map_attribution">© OpenStreetMap · © MapTiler</string>
<string name="map_teaser_title">Din fågelkarta</string>
<string name="map_teaser_caption">Se var du har sett dina fåglar.</string>
<string name="map_teaser_count">%1$s fynd med plats väntar på kartan</string>
<string name="map_teaser_cta">Lås upp med Premium</string>
<string name="map_teaser_badge">Premium</string>
```
EN (`values-en/strings.xml`):
```xml
<string name="map_title">Map</string>
<string name="map_empty">No located finds yet. Turn on location in Settings and save a find out in the field.</string>
<string name="map_attribution">© OpenStreetMap · © MapTiler</string>
<string name="map_teaser_title">Your bird map</string>
<string name="map_teaser_caption">See where you spotted your birds.</string>
<string name="map_teaser_count">%1$s located finds waiting on the map</string>
<string name="map_teaser_cta">Unlock with Premium</string>
<string name="map_teaser_badge">Premium</string>
```

> **Trap reminder:** compose-resources does not process `%%`. The count uses `%1$s`; pass `count.toString()` from Kotlin — never embed a literal `%`.

- [ ] **Step 2: Implement the common screen + expect host**

`MapScreen.kt`:
```kotlin
package se.birdy.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_attribution
import birdy_bird_scanner.composeapp.generated.resources.map_empty
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground

/** Android draws the osmdroid surface; other targets fall back to nothing (Android-only v1). */
@Composable
expect fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
)

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    onPinClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(modifier = modifier.fillMaxSize().paperBackground()) {
        if (state.pins.isEmpty()) {
            Text(
                text = stringResource(Res.string.map_empty),
                color = MarginaliaInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(32.dp),
            )
        } else {
            MapScreenHost(pins = state.pins, onPinClick = onPinClick, modifier = Modifier.fillMaxSize())
            Text(
                text = stringResource(Res.string.map_attribution),
                color = MarginaliaInk,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        }
    }
}
```
> If `collectAsStateWithLifecycle` isn't already used in the module, use `collectAsState()` from `androidx.compose.runtime` instead (match what `LifelistScreen.kt` uses). Confirm `paperBackground()` import path against `Modifier.paperBackground()` usage in `LifelistScreen.kt`.

- [ ] **Step 3: Implement the teaser**

`MapPremiumTeaser.kt` — reuse `LockedStatsPreview` styling cues (read `composeApp/.../ui/components/LockedStatsPreview.kt`). Minimum viable:
```kotlin
package se.birdy.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_caption
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_count
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_cta
import birdy_bird_scanner.composeapp.generated.resources.map_teaser_title
import se.birdy.app.ui.theme.MarginaliaInk
import se.birdy.app.ui.theme.paperBackground

@Composable
fun MapPremiumTeaser(
    viewModel: MapViewModel,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier.fillMaxSize().paperBackground().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.map_teaser_title), textAlign = TextAlign.Center)
        Text(stringResource(Res.string.map_teaser_caption), color = MarginaliaInk, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.map_teaser_count, state.locatedCount.toString()),
            color = MarginaliaInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onUpgrade) { Text(stringResource(Res.string.map_teaser_cta)) }
    }
}
```
> Polish pass (optional, same task): swap the bare `Text`/`Button` for the Field Journal headline + copper CTA used by `LockedStatsPreview` so it matches the rest of the app.

- [ ] **Step 4: Build common (Android compile defers until the actual exists — next task)**

Skip building here; the `expect` has no `actual` yet. Proceed to Task 14, then build.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapScreen.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapPremiumTeaser.kt composeApp/src/commonMain/composeResources/
git commit -m "feat(map): common MapScreen + expect host + premium teaser"
```

---

## Task 14: osmdroid deps + `MapScreenHost` Android actual + MapTiler key

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts` + `gradle.properties`
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt`

- [ ] **Step 1: Add the dependency**

`gradle/libs.versions.toml` — under `[versions]`:
```toml
osmdroid = "6.1.20"
```
under `[libraries]`:
```toml
osmdroid-android = { module = "org.osmdroid:osmdroid-android", version.ref = "osmdroid" }
```

`composeApp/build.gradle.kts` — in `androidMain.dependencies`:
```kotlin
            implementation(libs.osmdroid.android)
```

`androidApp/build.gradle.kts` — in its `androidMain.dependencies` (transitive-dep trap: composeApp uses `implementation`):
```kotlin
            implementation(libs.osmdroid.android)
```

- [ ] **Step 2: Add the MapTiler BuildConfig key**

`gradle.properties` (local-only, leave value empty in repo):
```properties
# MapTiler raster-tile key — https://cloud.maptiler.com/account/keys/ . Local-only; do not commit a real value.
MAPTILER_API_KEY=
```
`androidApp/build.gradle.kts` — alongside the `PLAY_LICENSE_KEY` `buildConfigField` (line ~59):
```kotlin
        buildConfigField(
            "String",
            "MAPTILER_API_KEY",
            "\"${project.findProperty("MAPTILER_API_KEY") ?: ""}\"",
        )
```
> The host reads the key via the generated `se.birdy.android.BuildConfig`. If `composeApp` cannot see `androidApp`'s BuildConfig, pass the key into `AppGraph` instead (add `val mapTilerKey: String = ""` to AppGraph, set from MainActivity `BuildConfig.MAPTILER_API_KEY`, and thread it to `MapScreen`/host). Prefer the AppGraph route if there's any module-visibility doubt.

- [ ] **Step 3: Implement the Android host**

`MapScreenHost.android.kt`:
```kotlin
package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import se.birdy.android.BuildConfig
import java.io.File

private fun mapTilerSource(apiKey: String): OnlineTileSourceBase =
    object : XYTileSource(
        "MapTiler-Outdoor",
        0, 20, 256, ".png",
        arrayOf("https://api.maptiler.com/maps/outdoor-v2/256/"),
        "© MapTiler © OpenStreetMap contributors",
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl +
                MapTileIndex.getZoom(pMapTileIndex) + "/" +
                MapTileIndex.getX(pMapTileIndex) + "/" +
                MapTileIndex.getY(pMapTileIndex) +
                mImageFilenameEnding + "?key=" + apiKey
    }

@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView =
        remember {
            Configuration.getInstance().apply {
                userAgentValue = context.packageName // REQUIRED or tile servers 403
                osmdroidBasePath = File(context.cacheDir, "osmdroid")
                osmdroidTileCache = File(osmdroidBasePath, "tiles")
            }
            MapView(context).apply {
                setTileSource(mapTilerSource(BuildConfig.MAPTILER_API_KEY))
                setMultiTouchControls(true)
                setUseDataConnection(true)
            }
        }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    LaunchedEffect(pins) {
        mapView.overlays.clear()
        val points =
            pins.map { pin ->
                Marker(mapView).apply {
                    position = GeoPoint(pin.latitude, pin.longitude)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "#${pin.stampNumber}"
                    setOnMarkerClickListener { _, _ ->
                        onPinClick(pin.observationId)
                        true
                    }
                    mapView.overlays.add(this)
                    GeoPoint(pin.latitude, pin.longitude)
                }
            }
        if (points.isNotEmpty()) {
            if (points.size == 1) {
                mapView.controller.setZoom(13.0)
                mapView.controller.setCenter(points.first())
            } else {
                mapView.post { mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), false, 96) }
            }
        }
        mapView.invalidate()
    }

    AndroidView(modifier = modifier, factory = { mapView })
}
```

- [ ] **Step 4: Build the Android app**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL. (Tiles render blank without a real `MAPTILER_API_KEY`; that's expected until the key is set locally — see Task 17.)

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts androidApp/build.gradle.kts gradle.properties composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt
git commit -m "feat(map): osmdroid MapScreenHost + MapTiler tiles + cache"
```

---

## Task 15: Register `AppRoute.Map` + premium gating in AppScaffold

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`

- [ ] **Step 1: Register the route with gating**

Add a `composable<AppRoute.Map>` block (mirror the `effectivePremiumActive` usage already present in the `Lifelist`/`SeasonStats` blocks — reuse the same local `effectivePremiumActive` boolean):
```kotlin
            composable<AppRoute.Map> {
                val mapVm = remember(graph) { graph.mapViewModel() }
                if (effectivePremiumActive) {
                    se.birdy.app.ui.map.MapScreen(
                        viewModel = mapVm,
                        onPinClick = { id -> navController.navigate(AppRoute.ObservationDetail(id)) },
                    )
                } else {
                    se.birdy.app.ui.map.MapPremiumTeaser(
                        viewModel = mapVm,
                        onUpgrade = { navController.navigate(AppRoute.Premium) },
                    )
                }
            }
```
> Confirm whether `effectivePremiumActive` in AppScaffold is a raw `Boolean` (as agent-reported in the Lifelist block) or a flow needing `.collectAsState()`. Use whichever form the neighbouring blocks use so gating reacts live.

- [ ] **Step 2: Build + lint**

Run: `./gradlew :androidApp:assembleDebug ktlintCheck detekt`
Expected: BUILD SUCCESSFUL, ktlint + detekt clean.

- [ ] **Step 3: Full fast test sweep**

Run: `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt
git commit -m "feat(map): register Map route with premium gate + teaser"
```

---

## Task 16: Privacy / Data Safety / docs

**Files:**
- Modify: `docs/play-store/data-safety-form.md`
- Modify: the privacy-policy markdown (find it: `docs/play-store/privacy-policy.md` and/or `website/` legal content) — update both app-store and website copies.

- [ ] **Step 1: Update the Data Safety form**

In `docs/play-store/data-safety-form.md`, add a Location entry stating: approximate/precise location is **used** for the in-app map, **stored only on the device**, **not collected** (not transmitted off device) and **not shared**; collection is optional and off by default. Note map tiles are fetched from MapTiler (the device's map viewport is sent to the tile provider to render imagery; user observations are never sent).

- [ ] **Step 2: Update the privacy policy**

Add a short paragraph: "If you turn on 'Save location with my finds' (off by default), Birdy records the location of finds you make in the field and stores it only on your device, to show them on your personal map. This data never leaves your phone. When you view the map, map imagery is loaded from MapTiler; only the map area you view is requested — your finds and their locations are not sent."

- [ ] **Step 3: Cross-check the "data stays on phone" promise**

Confirm no wording in store-listing/website now contradicts the promise. The promise holds: observations never leave the device; only map-tile viewport requests go out (standard for any map). Note this nuance in `data-safety-form.md` so the next store upload reflects it.

- [ ] **Step 4: Commit**

```bash
git add docs/play-store/ website/
git commit -m "docs(map): location is on-device only — data-safety + privacy policy"
```

---

## Task 17: versionCode bump + green build + device-verify

**Files:**
- Modify: `androidApp/build.gradle.kts` (versionCode/versionName)

- [ ] **Step 1: Bump version**

In `androidApp/build.gradle.kts`, bump `versionCode` to the next integer above the current closed-testing build (read current value first — it is past vC122) and set a `versionName` continuing the series (e.g. `1.2.0-rc1` or per the maintainer's scheme). Confirm the chosen numbers with the maintainer if ambiguous.

- [ ] **Step 2: Full local build + tests + lint (CI only runs 3/7 modules)**

Run:
```bash
./gradlew build
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest
./gradlew ktlintCheck detekt
```
Expected: all green.

- [ ] **Step 3: Set a local MapTiler key + install on device**

Put a real `MAPTILER_API_KEY=...` in `gradle.properties` (local only), then:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
> Reminder: the debug package is `se.birdy.android.debug` (not `se.birdy.android`).

- [ ] **Step 4: Device-verify checklist (SM-S918B)** — ask Albin for "hands off" first; the phone is his daily device.

Verify:
1. Settings → "Spara plats med mina fynd" toggle (default OFF). Enabling it raises the system location-permission dialog.
2. With location ON + permission granted: do a **live scan** save → open the **Karta** tab (premium is open during launch, so the map shows) → a pin appears at your location → tap it → lands on the observation detail.
3. **Gallery upload** save with location ON → no pin added (gallery excluded).
4. Map tiles render (MapTiler key working); pan/zoom works; attribution visible.
5. Empty state shows when no located finds exist.
6. (Premium gate) cannot be device-verified while `PREMIUM_OPEN_FOR_LAUNCH=true`; verify the teaser path by temporarily forcing non-premium in a debug build OR note it as residual to verify when Billing flips.

- [ ] **Step 5: Capture screenshots** into `docs/superpowers/screenshots/` (map with pins, empty state, settings toggle, teaser if forced).

- [ ] **Step 6: Commit**

```bash
git add androidApp/build.gradle.kts docs/superpowers/screenshots/
git commit -m "chore(map): version bump + device-verify (SM-S918B)"
```

---

## Self-Review notes (for the executor)

- **Gallery vs take-photo:** both flow through `PhotoAnalyzeScreen` and are tagged `live = false`, so in-app "take photo" also gets no location in v1. This is the conservative, spec-honoring default; refining take-photo to capture location is a deliberate follow-up, not a bug.
- **Premium gate during launch:** `PREMIUM_OPEN_FOR_LAUNCH=true` means everyone sees the real map. The teaser only bites after the Billing flip — verify it by forcing non-premium, and treat full teaser device-verify as residual (consistent with how other premium screens were handled).
- **No new SQL:** pins + teaser count both derive from `observeAll()` via `MapPinMapper`. If observation counts ever grow large, add a `WHERE latitude IS NOT NULL` query then; YAGNI for v1.
- **osmdroid user-agent** MUST be set before the first tile request or MapTiler/OSM returns 403 — it's in the `remember{}` block; do not move it later.
