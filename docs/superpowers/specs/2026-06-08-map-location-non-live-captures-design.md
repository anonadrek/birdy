# Geotag non-live captures — design (map-polish-v2 item 4)

> **⚠️ REVISED 2026-06-08 — EXIF approach abandoned; shipped with current-location instead.**
> Device-verify revealed that the Android photo picker **strips GPS EXIF** from returned images
> unless the app holds `ACCESS_MEDIA_LOCATION` (a dangerous permission + Play Console data-safety
> disclosure). So the "read the photo's EXIF GPS" design below cannot read coordinates without a
> new permission. Per product decision (Albin, 2026-06-08), **gallery + take-photo uploads now use
> the device's CURRENT location** (gated by the opt-in toggle), exactly like live scans — no new
> permission. The origin-enum / EXIF-coords / presetLocation machinery described below was built,
> found null on device, and reverted; the shipped change is simply: `shouldAttachLocation` returns
> true for every capture. Implemented + device-verified in commit `459e6038`. The EXIF design is
> retained below for the record. **4a (take-photo → current location) survived unchanged; only 4b
> (gallery) changed from EXIF to current-location.**
>
> **Status:** approved by Albin 2026-06-08 (original EXIF design); revised + shipped same day. Part of `feat/map-polish-v2` (items 1+2 already done + device-verified). Backlog: `docs/superpowers/plans/2026-06-07-map-polish-v2-backlog.md` §4. Personal finds map: spec `2026-06-05-personal-finds-map-design.md`.

## Problem

The personal finds map only shows observations that carry coordinates. Today only **live "Look" scans** and **"Listen" audio** geotag — gallery uploads and in-app take-photo do not. Real-world bug 2026-06-08: Albin saved a real find via a **gallery photo** with the location toggle ON and it never appeared on the map.

Root cause (verified, no crash): `ScanSource.Image.live` is the only signal that drives location attach, and it is set `false` for **both** gallery and in-app take-photo (`PhotoAnalyzeScreen` hardcodes `live = false`). The save path (`SaveObservationUseCase`) only ever calls `LocationProvider.current()`, gated by `attachLocation && locationEnabled()`. So:

| Entry | `ScanSource` today | Location today |
|---|---|---|
| Look (live scan) | `Image(live=true)` | `current()` ✅ |
| Listen (audio) | `Audio` | `current()` ✅ |
| In-app take photo | `Image(live=false)` | none ❌ |
| Gallery upload | `Image(live=false)` | none ❌ |

`live` is used **only** for this location decision (plus nav serialization) — no UI branches on it, so it is safe to refactor.

## Goal

Geotag the two non-live capture paths, each with the *correct* location source, all gated by the existing opt-in toggle (`UserPreferences.locationCaptureEnabled`):

- **4a — In-app take-photo → CURRENT device location.** The capture is here-and-now, so `LocationProvider.current()` is correct (same as live scan).
- **4b — Gallery photo → the photo's EXIF GPS, NOT current location.** A gallery image may be from another place/time; current location would be wrong. Read EXIF lat/lng from the picked URI; no EXIF GPS → no location (can't know).

### Target behavior matrix (location toggle ON)

| Entry | `ScanSource` | Location attached |
|---|---|---|
| Look (live scan) | `Image(origin=LiveScan)` | `current()` — unchanged |
| Listen (audio) | `Audio` | `current()` — unchanged |
| **In-app take photo** | `Image(origin=CameraCapture)` | `current()` — **NEW (4a)** |
| **Gallery upload** | `Image(origin=Gallery, exifLat/Lng)` | EXIF GPS if present, else none — **NEW (4b)** |

When the toggle is OFF, nothing is attached anywhere (unchanged).

## Permission (already handled)

Enabling the Settings location toggle calls `onRequestLocationPermission()` → `requestLocationPermLauncher.launch(ACCESS_FINE_LOCATION)` (MainActivity). So `locationCaptureEnabled == true` implies the runtime permission was already prompted. **No new permission plumbing** is needed for 4a. `AndroidLocationProvider` already returns `null` (never throws) when permission is missing or location is off.

## Design

### Representation (decided: origin enum)

Replace `ScanSource.Image.live: Boolean` with an `origin` enum plus pre-resolved EXIF coords:

```kotlin
sealed interface ScanSource {
    data class Image(
        override val frameJpegPath: String,
        override val classification: Classification,
        val origin: ImageOrigin = ImageOrigin.LiveScan,
        val exifLatitude: Double? = null,
        val exifLongitude: Double? = null,
    ) : ScanSource
    // Audio unchanged
}

enum class ImageOrigin { LiveScan, CameraCapture, Gallery }
```

`ScanSource` lives in `shared/ml` (commonMain) and must not depend on composeApp's `LatLng`, so EXIF coords are carried as primitive `Double?` pairs. `exifLatitude/exifLongitude` are only set for `Gallery`.

### Data flow (host → screen → save)

1. **`PhotoAnalyzeHost.android.kt`** already has two separate launchers (`galleryLauncher`, `takePhotoLauncher`). Track which one fired through `pendingDecodeUri` (e.g. carry an origin alongside the URI). For the **gallery** path, read `ExifInterface(stream).latLong` (the non-deprecated `getLatLong(): DoubleArray?`) from the picked URI — reuse the same `openInputStream` pattern as the existing `readExifRotation`. Pass `origin` (+ `exifLat`/`exifLng` for gallery) into `viewModel.analyze(...)`.
2. **`PhotoAnalyzeViewModel.analyze(frame, origin, exifLat, exifLng)`** carries them into `PhotoAnalyzeUiState.Loaded`.
3. **`PhotoAnalyzeScreen`** reads `Loaded` and builds `ScanSource.Image(origin=…, exifLatitude=…, exifLongitude=…)` instead of the hardcoded `live=false`.
4. **`ScanSourceSerialization`** serializes `origin` (as a string) + the two nullable doubles, with a back-compat default of `LiveScan` on decode.

### Save decision (`MatchResultViewModel` + `SaveObservationUseCase`)

- `shouldAttachLocation(source)` keeps its meaning **"call `current()`"**:
  - `Audio` → true
  - `Image` → `origin != Gallery` (i.e. `LiveScan` or `CameraCapture`) → true; `Gallery` → false
- For the gallery case, `MatchResultViewModel` builds a `LatLng?` from `exifLatitude/exifLongitude` and passes it to the use case as a new `presetLocation: LatLng?` argument.
- `SaveObservationUseCase.save(..., attachLocation: Boolean, presetLocation: LatLng? = null)`, **both branches gated by `locationEnabled()`**:

```kotlin
val latLng: LatLng? = when {
    presetLocation != null && locationEnabled() -> presetLocation
    attachLocation && locationEnabled()        -> runCatching { locationProvider?.current() }.getOrNull()
    else                                       -> null
}
```

Resulting save behavior (toggle ON):

| Source | attachLocation | presetLocation | result |
|---|---|---|---|
| live scan | true | null | `current()` |
| audio | true | null | `current()` |
| take photo | true | null | `current()` |
| gallery + EXIF GPS | false | `LatLng` | preset |
| gallery, no EXIF GPS | false | null | `null` |

### Why `ExifInterface.getLatLong()`

It returns a `DoubleArray[2]?` directly (null when no GPS tags), handling the DMS→decimal conversion and N/S/E/W sign internally. No custom coordinate math means no bug-prone conversion to unit-test. No new dependency — `androidx.exifinterface.media.ExifInterface` is already imported in the host for rotation.

## Testing

**commonTest (JVM):**
- `shouldAttachLocation` over all cases: `Audio`→true, `Image(LiveScan)`→true, `Image(CameraCapture)`→true, `Image(Gallery)`→false (update `AttachLocationTest`).
- `SaveObservationUseCase`: preset used when `locationEnabled` true; preset ignored when toggle off (→ null); preset takes precedence over `current()`; `current()` still used for `attachLocation` paths (extend `SaveObservationLocationTest`).
- `ScanSourceSerialization` round-trip for `origin` + `exifLatitude/exifLongitude` incl. back-compat default (update `ScanSourceLiveSerializationTest`).

**Device-verify (androidMain not JVM-testable):** the host EXIF read + origin wiring. On SM-S918B with toggle ON:
1. Gallery photo **with** GPS EXIF → pin appears at the photo's location.
2. Gallery photo **without** GPS EXIF → saved, no pin (no crash).
3. In-app take-photo → pin appears at current location.
> Before device-verify: ask Albin to silence notifications / enable DND (his daily phone). A real `MAPTILER_API_KEY` must be in local `gradle.properties`. Debug package: `se.birdy.android.debug`.

## Out of scope (deferred — separate brainstorms)

- EXIF **datetime** as `capturedAt` (gallery finds still show today's date — pre-existing behavior, location-only here).
- Manual pin placement / adjust.
- Offline region download ("excursion mode").
- Clustering / thumbnail pins (backlog item 3).

No new UI or toast: "no GPS available" is silent, consistent with today's live capture that silently gets `null`.

## Files touched

- `shared/ml/.../ScanSource.kt` — `live` → `origin` enum + `exifLatitude/exifLongitude`; add `ImageOrigin`.
- `shared/ml/.../ScanSourceSerialization.kt` — serialize origin + coords (back-compat default).
- `composeApp/.../ui/scan/ScanScreen.kt` — `live=true` → `origin=LiveScan`.
- `composeApp/.../ui/photoanalyze/PhotoAnalyzeScreen.kt` — build `Image` with origin + exif coords from `Loaded`.
- `composeApp/.../ui/photoanalyze/PhotoAnalyzeViewModel.kt` — `analyze(frame, origin, exifLat, exifLng)`; carry into `Loaded`.
- `composeApp/.../ui/photoanalyze/PhotoAnalyzeHost.android.kt` — track origin per launcher; read EXIF lat/lng for gallery; pass into `analyze`.
- `composeApp/.../ui/match/MatchResultViewModel.kt` — `shouldAttachLocation` over origins; pass `presetLocation` to save.
- `composeApp/.../usecase/SaveObservationUseCase.kt` — add `presetLocation`, gate both branches by `locationEnabled()`.
- Tests: `AttachLocationTest`, `SaveObservationLocationTest`, `ScanSourceLiveSerializationTest`, `MatchResultViewModelTest` (construction site).
