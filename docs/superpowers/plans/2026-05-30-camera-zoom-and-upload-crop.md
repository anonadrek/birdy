# Kamera-zoom + crop/justera uppladdade bilder — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lägg till (1) zoom 1×→10× i live-kameran via preset-chips och (2) crop + 90°-rotation av uppladdade bilder innan de analyseras.

**Architecture:** Två oberoende spår. Zoom hakar in i `AndroidCameraSource` (fångar `Camera` från `bindToLifecycle`, exponerar `zoom`/`setZoomRatio` via ett utökat `CameraSource`-interface) och renderas som chips i `ScanScreen`. Crop skjuts in som lokalt skärm-state i `PhotoAnalyzeHost` mellan decode och `analyze`, med en egenbyggd `CropAdjustScreen` ovanpå en ren, JVM-testbar `CropGeometry`. Inga nya Gradle-beroenden.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, CameraX (`androidx.camera:camera-core`, redan inne), kotlinx.coroutines Flow, compose-resources (SV+EN).

**Referensspec:** `docs/superpowers/specs/2026-05-30-camera-zoom-and-upload-crop-design.md`

**Byggprefix för bash-`./gradlew`** (annars hittar Gradle inte Java):
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## Filöversikt

| Fil | Roll | Skapas/Ändras |
|---|---|---|
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/Zoom.kt` | `ZoomState` + `zoomPresets()` ren logik | Skapas |
| `shared/ml/src/commonTest/kotlin/se/birdy/ml/ZoomTest.kt` | Tester för `zoomPresets` | Skapas |
| `shared/ml/src/commonMain/kotlin/se/birdy/ml/CameraSource.kt` | Interface får `zoom`/`setZoomRatio` (default-impl) | Ändras |
| `shared/ml/src/androidMain/kotlin/se/birdy/ml/camera/AndroidCameraSource.kt` | Fångar `Camera`, implementerar zoom | Ändras |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ZoomChips.kt` | Chip-rad-komponent | Skapas |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt` | Renderar `ZoomChips` | Ändras |
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/CropGeometry.kt` | Ren crop-rect-matematik | Skapas |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/CropGeometryTest.kt` | Tester för `CropGeometry` | Skapas |
| `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.android.kt` | Compose crop-UI (Android Bitmap) | Skapas |
| `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt` | Ombyggd pipeline med crop-steg | Ändras |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | SV crop-strängar | Ändras |
| `composeApp/src/commonMain/composeResources/values-en/strings.xml` | EN crop-strängar | Ändras |
| `androidApp/build.gradle.kts` | versionCode/versionName-bump | Ändras |

---

# SPÅR 1 — KAMERA-ZOOM

## Task 1: `ZoomState` + `zoomPresets()` ren logik (TDD)

**Files:**
- Create: `shared/ml/src/commonMain/kotlin/se/birdy/ml/Zoom.kt`
- Test: `shared/ml/src/commonTest/kotlin/se/birdy/ml/ZoomTest.kt`

- [ ] **Step 1: Skriv det fallerande testet**

Create `shared/ml/src/commonTest/kotlin/se/birdy/ml/ZoomTest.kt`:
```kotlin
package se.birdy.ml

import kotlin.test.Test
import kotlin.test.assertEquals

class ZoomTest {
    @Test
    fun presets_filtered_to_device_max_and_clamped_to_ten() {
        // S23-klass kamera: max 12x → bara 1/2/5/10 visas (10 är taket).
        assertEquals(listOf(1f, 2f, 5f, 10f), zoomPresets(maxRatio = 12f))
    }

    @Test
    fun presets_drop_values_above_device_max_and_add_max_as_top() {
        // Enhet med max 6x → 1/2/5 + 6 som översta chip.
        assertEquals(listOf(1f, 2f, 5f, 6f), zoomPresets(maxRatio = 6f))
    }

    @Test
    fun presets_exactly_on_a_preset_do_not_duplicate() {
        assertEquals(listOf(1f, 2f, 5f), zoomPresets(maxRatio = 5f))
    }

    @Test
    fun no_zoom_capability_returns_empty() {
        assertEquals(emptyList(), zoomPresets(maxRatio = 1f))
    }

    @Test
    fun none_constant_is_identity() {
        assertEquals(ZoomState(1f, 1f, 1f), ZoomState.NONE)
    }
}
```

- [ ] **Step 2: Kör testet — ska faila (osymbol)**

Run: `./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.ZoomTest"`
Expected: FAIL — `Unresolved reference: zoomPresets` / `ZoomState`.

- [ ] **Step 3: Skriv minimal implementation**

Create `shared/ml/src/commonMain/kotlin/se/birdy/ml/Zoom.kt`:
```kotlin
package se.birdy.ml

/**
 * Snapshot av kamerans zoom-läge. minRatio är 1f på alla kameror vi stödjer;
 * maxRatio är enhetens faktiska tak (kan vara < eller > UI-takets 10x).
 */
data class ZoomState(
    val ratio: Float,
    val minRatio: Float,
    val maxRatio: Float,
) {
    companion object {
        val NONE = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = 1f)
    }
}

private val BASE_PRESETS = listOf(1f, 2f, 5f, 10f)

/**
 * Vilka zoom-chips som ska visas givet enhetens maxRatio.
 * - Tar baspresets (1/2/5/10) som är < maxRatio.
 * - Lägger till min(10, maxRatio) som översta chip (taket), om det inte redan finns.
 * - Returnerar tom lista om kameran saknar zoom (maxRatio <= 1).
 */
fun zoomPresets(maxRatio: Float): List<Float> {
    if (maxRatio <= 1f) return emptyList()
    val top = minOf(10f, maxRatio)
    val below = BASE_PRESETS.filter { it < top }
    return if (below.lastOrNull() == top) below else below + top
}
```

- [ ] **Step 4: Kör testet — ska passa**

Run: `./gradlew :shared:ml:jvmTest --tests "se.birdy.ml.ZoomTest"`
Expected: PASS (5 tester gröna).

- [ ] **Step 5: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/Zoom.kt shared/ml/src/commonTest/kotlin/se/birdy/ml/ZoomTest.kt
git commit -m "feat(zoom): ZoomState + zoomPresets ren logik med tester"
```

---

## Task 2: Utöka `CameraSource`-interfacet med zoom (default-impl)

**Files:**
- Modify: `shared/ml/src/commonMain/kotlin/se/birdy/ml/CameraSource.kt`

Default-impl gör att `FakeCameraSource` (commonTest) och alla befintliga tester kompilerar oförändrat — bara `AndroidCameraSource` overridar.

- [ ] **Step 1: Ersätt filinnehållet**

Replace the entire contents of `shared/ml/src/commonMain/kotlin/se/birdy/ml/CameraSource.kt` with:
```kotlin
package se.birdy.ml

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Delad no-op-flow så default-impl inte allokerar en ny StateFlow per access.
private val NO_ZOOM: StateFlow<ZoomState> = MutableStateFlow(ZoomState.NONE)

interface CameraSource {
    fun frames(): Flow<ImageInput>

    suspend fun start()

    suspend fun stop()

    /** Kamerans zoom-läge. Default = ingen zoom (för fakes/test-impl). */
    val zoom: StateFlow<ZoomState>
        get() = NO_ZOOM

    /** Sätt zoom-ratio (klampas mot enhetens min/max av implementationen). Default = no-op. */
    fun setZoomRatio(ratio: Float) {}
}
```

- [ ] **Step 2: Verifiera att allt fortfarande kompilerar + tester gröna**

Run: `./gradlew :shared:ml:jvmTest :composeApp:testDebugUnitTest`
Expected: PASS — `FakeCameraSource` och `ScanViewModelTest` orörda och gröna (default-impl täcker de nya medlemmarna).

- [ ] **Step 3: Commit**

```bash
git add shared/ml/src/commonMain/kotlin/se/birdy/ml/CameraSource.kt
git commit -m "feat(zoom): CameraSource-interface får zoom/setZoomRatio med default-impl"
```

---

## Task 3: Implementera zoom i `AndroidCameraSource`

**Files:**
- Modify: `shared/ml/src/androidMain/kotlin/se/birdy/ml/camera/AndroidCameraSource.kt`

Fångar `Camera` från `bindToLifecycle`, läser `maxZoomRatio` en gång vid bind, tvingar 1× vid start, och uppdaterar en egen `MutableStateFlow<ZoomState>` (vi observerar inte CameraX `LiveData` → inga nya deps).

- [ ] **Step 1: Lägg till imports + Camera/zoom-state**

In `AndroidCameraSource.kt`, add to the import block (efter `import androidx.camera.core.CameraSelector`):
```kotlin
import androidx.camera.core.Camera
```
And add to the kotlinx.coroutines.flow-imports (det finns redan `MutableStateFlow`):
```kotlin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
```
And import the domain type (efter `import se.birdy.ml.ImageInput`):
```kotlin
import se.birdy.ml.ZoomState
```

- [ ] **Step 2: Lägg till fält för Camera + zoom-flow**

Inside the class, efter raden `private var cameraProvider: ProcessCameraProvider? = null` add:
```kotlin
    private var camera: Camera? = null
    private val _zoom = MutableStateFlow(ZoomState.NONE)
    override val zoom: StateFlow<ZoomState> = _zoom.asStateFlow()
```

- [ ] **Step 3: Fånga Camera + initiera zoom i `start()`**

Replace the body of `start()` (raderna med `provider.bindToLifecycle(...)` t.o.m. `cameraProvider = provider`) with:
```kotlin
        provider.unbindAll()
        val boundCamera =
            provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, analysis)
        camera = boundCamera
        val max = boundCamera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f
        _zoom.value = ZoomState(ratio = 1f, minRatio = 1f, maxRatio = max)
        boundCamera.cameraControl.setZoomRatio(1f)
        cameraProvider = provider
```

- [ ] **Step 4: Implementera `setZoomRatio` + nollställ i `stop()`**

Add a new method efter `stop()`:
```kotlin
    override fun setZoomRatio(ratio: Float) {
        val current = _zoom.value
        val clamped = ratio.coerceIn(current.minRatio, current.maxRatio)
        camera?.cameraControl?.setZoomRatio(clamped)
        _zoom.value = current.copy(ratio = clamped)
    }
```
And in `stop()`, efter raden `analysisFlow.value = null`, add:
```kotlin
        camera = null
        _zoom.value = ZoomState.NONE
```

- [ ] **Step 5: Bygg modulen**

Run: `./gradlew :shared:ml:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL (inga osymbolfel; `Camera`, `cameraControl`, `cameraInfo.zoomState` finns i redan-inlänkade `androidx.camera:camera-core`).

- [ ] **Step 6: Commit**

```bash
git add shared/ml/src/androidMain/kotlin/se/birdy/ml/camera/AndroidCameraSource.kt
git commit -m "feat(zoom): AndroidCameraSource fångar Camera + wire:ar zoom-control"
```

---

## Task 4: `ZoomChips`-komponent + wire in i `ScanScreen`

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ZoomChips.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt`

Inga strängar behövs (chips är "1×"/"2×"… — locale-neutralt). Stil: koppar-pill för aktivt chip, halvgenomskinlig mörk pill för inaktiva (chips ligger över live-kameran).

- [ ] **Step 1: Skapa `ZoomChips.kt`**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ZoomChips.kt`:
```kotlin
package se.birdy.app.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.ml.ZoomState
import se.birdy.ml.zoomPresets

/**
 * Rad med zoom-preset-chips ovanpå live-kameran. Aktivt chip = det vars värde
 * ligger närmast nuvarande ratio (CameraX kan landa på t.ex. 1.97x). Renderar
 * inget om kameran saknar zoom (maxRatio <= 1).
 */
@Composable
fun ZoomChips(
    zoom: ZoomState,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = zoomPresets(zoom.maxRatio)
    if (presets.isEmpty()) return

    // Det preset vars värde ligger närmast nuvarande ratio markeras som aktivt.
    val active = presets.minByOrNull { abs(it - zoom.ratio) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { preset ->
            val isActive = preset == active
            Text(
                text = formatPreset(preset),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier =
                    Modifier
                        .clickable { onSelect(preset) }
                        .background(
                            color = if (isActive) AccentCopper else Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50),
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun formatPreset(value: Float): String {
    val whole = value.toInt()
    val label = if (value == whole.toFloat()) whole.toString() else value.toString()
    return label + "×" // "1×"
}
```

- [ ] **Step 2: Wire in i `ScanScreen`**

Inga nya imports behövs: `ZoomChips` ligger i samma paket (`se.birdy.app.ui.scan`), och `collectAsState`/`getValue`/`Arrangement`/`Column`/`CameraSource` är redan importerade.

Inside `ScanScreen`, i `else ->`-grenen, ersätt den nedersta `Column(...)`-blocket (det som idag innehåller `Text(scan_freeze_hint)` + `OutlinedButton`) med en version som har `ZoomChips` överst:
```kotlin
                val zoomState by cameraSource.zoom.collectAsState()
                Column(
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZoomChips(
                        zoom = zoomState,
                        onSelect = { cameraSource.setZoomRatio(it) },
                    )
                    Text(text = stringResource(Res.string.scan_freeze_hint), color = TextOnHero)
                    OutlinedButton(onClick = onPhotoAnalyzeClick) {
                        Text(stringResource(Res.string.scan_photo_analyze))
                    }
                }
```
> Z-order: detta `Column` ritas efter den helskärms-tap-detektorn (raderna med `detectTapGestures`), så chips ligger ovanpå och fångar tappen — ett chip-tap triggar alltså inte freeze.

- [ ] **Step 3: Bygg + kör befintliga UI-tester**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid :composeApp:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; befintliga tester gröna.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ZoomChips.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/scan/ScanScreen.kt
git commit -m "feat(zoom): ZoomChips-komponent + inkoppling i ScanScreen"
```

---

## Task 5: Device-verify zoom (SM-S918B)

**Files:** inga kodändringar — manuell verifiering. Kräver fysisk enhet → följ "händerna borta"-protokollet (be Albin lägga ner telefonen innan ADB-driving; verifiera via screencap).

- [ ] **Step 1: Bygg + installera debug-APK**

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```
> Obs: debug-paketet är `se.birdy.android.debug` (inte `se.birdy.android`) p.g.a. `applicationIdSuffix = ".debug"`.

- [ ] **Step 2: Verifiera på Scan-fliken**

Kontrollera (via screencap):
- Chip-raden 1× 2× 5× 10× syns nederst på kameravyn.
- Tap på 5× → preview zoomar in; 5×-chipet markeras (koppar + fet).
- Tap på 1× → tillbaka till vidvinkel.
- Tap på ett chip triggar INTE freeze/MatchResult.
- Lämna och återgå till Scan → zoom börjar om på 1×.

- [ ] **Step 3: Skärmdumpar**

Spara 2 skärmdumpar (1× och en inzoomad nivå) till `docs/superpowers/screenshots/` enligt milstolpe-runbook-namnschema. Radera ev. privat innehåll som råkar fångas.

- [ ] **Step 4: Commit (om skärmdumpar tas)**

```bash
git add docs/superpowers/screenshots/
git commit -m "docs(screenshots): zoom-chips device-verify på SM-S918B"
```

---

# SPÅR 2 — CROP / JUSTERA UPPLADDADE BILDER

## Task 6: `CropGeometry` ren crop-rect-matematik (TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/CropGeometry.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/CropGeometryTest.kt`

Crop-rektangeln hålls i **käll-pixel-koordinater**. Rotation hanteras inte här (Android-skiktet roterar själva bitmappen och nollställer rect) → ren 2D-matematik, JVM-testbar.

- [ ] **Step 1: Skriv de fallerande testerna**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/CropGeometryTest.kt`:
```kotlin
package se.birdy.app.ui.photoanalyze

import kotlin.test.Test
import kotlin.test.assertEquals

class CropGeometryTest {
    @Test
    fun full_rect_covers_whole_image() {
        assertEquals(CropRect(0, 0, 1000, 800), CropGeometry.fullRect(1000, 800))
    }

    @Test
    fun resize_corner_respects_min_side() {
        // Dra TOP_LEFT nästan in i motsatt hörn → stoppas av minSide=224.
        val rect = CropRect(0, 0, 1000, 1000)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.TOP_LEFT,
                x = 995,
                y = 995,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(776, 776, 1000, 1000), out)
    }

    @Test
    fun resize_corner_clamps_to_image_bounds() {
        val rect = CropRect(100, 100, 900, 900)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.TOP_LEFT,
                x = -50,
                y = -50,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(0, 0, 900, 900), out)
    }

    @Test
    fun resize_bottom_right_grows_within_bounds() {
        val rect = CropRect(0, 0, 400, 400)
        val out =
            CropGeometry.resizeToCorner(
                rect = rect,
                handle = CropHandle.BOTTOM_RIGHT,
                x = 5000,
                y = 5000,
                width = 1000,
                height = 1000,
                minSide = 224,
            )
        assertEquals(CropRect(0, 0, 1000, 1000), out)
    }

    @Test
    fun move_translates_and_clamps_to_bounds() {
        val rect = CropRect(800, 800, 1000, 1000) // 200×200 i nedre högra hörnet
        val out = CropGeometry.move(rect, dx = 500, dy = 500, width = 1000, height = 1000)
        // Kan inte flyttas ut → klampas så rect ligger kvar mot kanten.
        assertEquals(CropRect(800, 800, 1000, 1000), out)
    }

    @Test
    fun move_within_bounds_shifts_by_delta() {
        val rect = CropRect(0, 0, 200, 200)
        val out = CropGeometry.move(rect, dx = 50, dy = 30, width = 1000, height = 1000)
        assertEquals(CropRect(50, 30, 250, 230), out)
    }
}
```

- [ ] **Step 2: Kör testerna — ska faila**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.photoanalyze.CropGeometryTest"`
Expected: FAIL — `Unresolved reference: CropRect` / `CropGeometry` / `CropHandle`.

- [ ] **Step 3: Skriv implementationen**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/CropGeometry.kt`:
```kotlin
package se.birdy.app.ui.photoanalyze

/** Heltalsrektangel i källbildens pixel-koordinater. */
data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Ren crop-rect-matematik (inga Android-typer → JVM-testbar). */
object CropGeometry {
    fun fullRect(
        width: Int,
        height: Int,
    ): CropRect = CropRect(0, 0, width, height)

    /**
     * Flytta ett hörn till (x, y) i käll-px, klampat så hörnet stannar inom bilden
     * och varje sida förblir >= minSide.
     */
    fun resizeToCorner(
        rect: CropRect,
        handle: CropHandle,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        minSide: Int,
    ): CropRect {
        val cx = x.coerceIn(0, width)
        val cy = y.coerceIn(0, height)
        return when (handle) {
            CropHandle.TOP_LEFT ->
                rect.copy(
                    left = cx.coerceAtMost(rect.right - minSide),
                    top = cy.coerceAtMost(rect.bottom - minSide),
                )
            CropHandle.TOP_RIGHT ->
                rect.copy(
                    right = cx.coerceAtLeast(rect.left + minSide),
                    top = cy.coerceAtMost(rect.bottom - minSide),
                )
            CropHandle.BOTTOM_LEFT ->
                rect.copy(
                    left = cx.coerceAtMost(rect.right - minSide),
                    bottom = cy.coerceAtLeast(rect.top + minSide),
                )
            CropHandle.BOTTOM_RIGHT ->
                rect.copy(
                    right = cx.coerceAtLeast(rect.left + minSide),
                    bottom = cy.coerceAtLeast(rect.top + minSide),
                )
        }
    }

    /** Translatera hela rektangeln, klampad så den stannar inom bilden (storlek bevaras). */
    fun move(
        rect: CropRect,
        dx: Int,
        dy: Int,
        width: Int,
        height: Int,
    ): CropRect {
        val w = rect.width
        val h = rect.height
        val newLeft = (rect.left + dx).coerceIn(0, width - w)
        val newTop = (rect.top + dy).coerceIn(0, height - h)
        return CropRect(newLeft, newTop, newLeft + w, newTop + h)
    }
}
```

- [ ] **Step 4: Kör testerna — ska passa**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.photoanalyze.CropGeometryTest"`
Expected: PASS (6 tester gröna).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/photoanalyze/CropGeometry.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/photoanalyze/CropGeometryTest.kt
git commit -m "feat(crop): CropGeometry ren crop-rect-matematik med tester"
```

---

## Task 7: Crop-strängar (SV + EN)

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-en/strings.xml`

- [ ] **Step 1: Lägg till SV-strängar**

In `composeApp/src/commonMain/composeResources/values/strings.xml`, add efter raden `<string name="photo_retry">Försök igen</string>`:
```xml
    <string name="crop_rotate">Rotera</string>
    <string name="crop_confirm">Analysera</string>
    <string name="crop_cancel">Avbryt</string>
```

- [ ] **Step 2: Lägg till EN-strängar**

In `composeApp/src/commonMain/composeResources/values-en/strings.xml`, add efter raden `<string name="photo_retry">Try again</string>`:
```xml
    <string name="crop_rotate">Rotate</string>
    <string name="crop_confirm">Analyse</string>
    <string name="crop_cancel">Cancel</string>
```

- [ ] **Step 3: Generera Res-accessorer**

Run: `./gradlew :composeApp:generateComposeResClass`
Expected: BUILD SUCCESSFUL — `Res.string.crop_title` m.fl. blir tillgängliga.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml composeApp/src/commonMain/composeResources/values-en/strings.xml
git commit -m "feat(crop): SV+EN-strängar för crop-skärmen"
```

---

## Task 8: `CropAdjustScreen` Compose-UI

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.android.kt`

Ritar bitmappen + crop-overlay i en enda `Canvas` (garanterar att skärm↔källa-mappningen är konsekvent), hanterar drag på hörn/inre yta, samt rotera/avbryt/analysera-knappar. Systemets back = avbryt (`BackHandler`).

- [ ] **Step 1: Skapa filen**

Create `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.android.kt`:
```kotlin
package se.birdy.app.ui.photoanalyze

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import birdy_bird_scanner.composeapp.generated.resources.Res
import birdy_bird_scanner.composeapp.generated.resources.crop_cancel
import birdy_bird_scanner.composeapp.generated.resources.crop_confirm
import birdy_bird_scanner.composeapp.generated.resources.crop_rotate
import org.jetbrains.compose.resources.stringResource
import se.birdy.app.ui.theme.AccentCopper
import se.birdy.app.ui.theme.OffwhiteWarm
import se.birdy.app.ui.theme.paperBackground
import kotlin.math.roundToInt

private const val MIN_CROP_SIDE_PX = 224

/**
 * Beskärnings- och rotations-yta för en uppladdad bild. Crop-rektangeln hålls i
 * källbildens pixel-koordinater; gester konverteras via en ContentScale.Fit-mappning.
 */
@Composable
fun CropAdjustScreen(
    bitmap: Bitmap,
    onRotate: () -> Unit,
    onConfirm: (CropRect) -> Unit,
    onCancel: () -> Unit,
) {
    BackHandler(onBack = onCancel)

    // Rect nollställs när bitmappen byts (efter rotation), tack vare remember(bitmap)-key.
    var rect by remember(bitmap) {
        mutableStateOf(CropGeometry.fullRect(bitmap.width, bitmap.height))
    }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val touchPx = with(LocalDensity.current) { 32.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize().paperBackground()) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val image = remember(bitmap) { bitmap.asImageBitmap() }
            Canvas(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { boxSize = it }
                        .pointerInput(bitmap, boxSize) {
                            val fit = fitMapping(boxSize, bitmap.width, bitmap.height)
                            var mode: DragMode = DragMode.None
                            detectDragGestures(
                                onDragStart = { pos ->
                                    mode = pickDragMode(rect, pos, fit, touchPx)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dxSrc = (dragAmount.x / fit.scale).roundToInt()
                                    val dySrc = (dragAmount.y / fit.scale).roundToInt()
                                    rect =
                                        when (val m = mode) {
                                            is DragMode.Corner ->
                                                applyCorner(rect, m.handle, dxSrc, dySrc, bitmap)
                                            DragMode.Move ->
                                                CropGeometry.move(
                                                    rect, dxSrc, dySrc, bitmap.width, bitmap.height,
                                                )
                                            DragMode.None -> rect
                                        }
                                },
                            )
                        },
            ) {
                val fit = fitMapping(IntSize(size.width.toInt(), size.height.toInt()), bitmap.width, bitmap.height)
                // 1. Bilden
                drawImage(
                    image = image,
                    dstOffset = IntOffset(fit.offsetX.roundToInt(), fit.offsetY.roundToInt()),
                    dstSize = IntSize(fit.dispWidth.roundToInt(), fit.dispHeight.roundToInt()),
                )
                // 2. Crop-rektangelns skärm-koordinater
                val l = fit.offsetX + rect.left * fit.scale
                val t = fit.offsetY + rect.top * fit.scale
                val r = fit.offsetX + rect.right * fit.scale
                val b = fit.offsetY + rect.bottom * fit.scale
                // 3. Mörkad overlay utanför crop (fyra rektanglar)
                val dim = Color.Black.copy(alpha = 0.5f)
                drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, t))
                drawRect(dim, topLeft = Offset(0f, b), size = Size(size.width, size.height - b))
                drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
                drawRect(dim, topLeft = Offset(r, t), size = Size(size.width - r, b - t))
                // 4. Rule-of-thirds-linjer
                val third = AccentCopper.copy(alpha = 0.6f)
                val cw = (r - l) / 3f
                val ch = (b - t) / 3f
                for (i in 1..2) {
                    drawLine(third, Offset(l + cw * i, t), Offset(l + cw * i, b), strokeWidth = 1.5f)
                    drawLine(third, Offset(l, t + ch * i), Offset(r, t + ch * i), strokeWidth = 1.5f)
                }
                // 5. Crop-ram + hörnhandtag
                drawRect(AccentCopper, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(width = 3f))
                val handle = 14f
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { c ->
                    drawCircle(AccentCopper, radius = handle, center = c)
                    drawCircle(Color.White, radius = handle, center = c, style = Stroke(width = 2f))
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.crop_cancel), color = AccentCopper)
            }
            OutlinedButton(onClick = onRotate, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.crop_rotate), color = AccentCopper)
            }
            Button(
                onClick = { onConfirm(rect) },
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = AccentCopper,
                        contentColor = OffwhiteWarm,
                    ),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(Res.string.crop_confirm))
            }
        }
    }
}

private data class FitMapping(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val dispWidth: Float,
    val dispHeight: Float,
)

private fun fitMapping(
    box: IntSize,
    srcWidth: Int,
    srcHeight: Int,
): FitMapping {
    if (box.width == 0 || box.height == 0) return FitMapping(1f, 0f, 0f, srcWidth.toFloat(), srcHeight.toFloat())
    val scale = minOf(box.width.toFloat() / srcWidth, box.height.toFloat() / srcHeight)
    val dispW = srcWidth * scale
    val dispH = srcHeight * scale
    return FitMapping(
        scale = scale,
        offsetX = (box.width - dispW) / 2f,
        offsetY = (box.height - dispH) / 2f,
        dispWidth = dispW,
        dispHeight = dispH,
    )
}

private sealed interface DragMode {
    data object None : DragMode

    data object Move : DragMode

    data class Corner(val handle: CropHandle) : DragMode
}

private fun pickDragMode(
    rect: CropRect,
    pos: Offset,
    fit: FitMapping,
    touchPx: Float,
): DragMode {
    val corners =
        mapOf(
            CropHandle.TOP_LEFT to Offset(fit.offsetX + rect.left * fit.scale, fit.offsetY + rect.top * fit.scale),
            CropHandle.TOP_RIGHT to Offset(fit.offsetX + rect.right * fit.scale, fit.offsetY + rect.top * fit.scale),
            CropHandle.BOTTOM_LEFT to Offset(fit.offsetX + rect.left * fit.scale, fit.offsetY + rect.bottom * fit.scale),
            CropHandle.BOTTOM_RIGHT to Offset(fit.offsetX + rect.right * fit.scale, fit.offsetY + rect.bottom * fit.scale),
        )
    val nearest = corners.minByOrNull { (_, c) -> (c - pos).getDistance() }
    if (nearest != null && (nearest.value - pos).getDistance() <= touchPx) {
        return DragMode.Corner(nearest.key)
    }
    val insideX = pos.x in (fit.offsetX + rect.left * fit.scale)..(fit.offsetX + rect.right * fit.scale)
    val insideY = pos.y in (fit.offsetY + rect.top * fit.scale)..(fit.offsetY + rect.bottom * fit.scale)
    return if (insideX && insideY) DragMode.Move else DragMode.None
}

private fun applyCorner(
    rect: CropRect,
    handle: CropHandle,
    dxSrc: Int,
    dySrc: Int,
    bitmap: Bitmap,
): CropRect {
    val (cx, cy) =
        when (handle) {
            CropHandle.TOP_LEFT -> rect.left to rect.top
            CropHandle.TOP_RIGHT -> rect.right to rect.top
            CropHandle.BOTTOM_LEFT -> rect.left to rect.bottom
            CropHandle.BOTTOM_RIGHT -> rect.right to rect.bottom
        }
    return CropGeometry.resizeToCorner(
        rect = rect,
        handle = handle,
        x = cx + dxSrc,
        y = cy + dySrc,
        width = bitmap.width,
        height = bitmap.height,
        minSide = MIN_CROP_SIDE_PX,
    )
}
```

> **Detekt-not:** Composablen är lång. Om detekt flaggar `LongMethod`/`CyclomaticComplexMethod` på `CropAdjustScreen`, extrahera `DrawScope`-ritningen (steg 1–5 i Canvas-blocket) till en privat `DrawScope.drawCropOverlay(fit, rect)`-funktion — beteendet är oförändrat.

- [ ] **Step 2: Bygg modulen**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/CropAdjustScreen.android.kt
git commit -m "feat(crop): CropAdjustScreen Compose-UI (canvas-baserad crop + rotera)"
```

---

## Task 9: Bygg om `PhotoAnalyzeHost`-pipelinen med crop-steg

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt`

Skjut in crop mellan decode och `analyze`: decode → arbets-bitmap (EXIF-roterad, cap 2048) → `CropAdjustScreen` → finalisera (crop + skala 1024 + JPEG) → `analyze`. Källbild < 224 px kortsida hoppar över crop och går direkt till `analyze` (befintlig `TooSmall` hanterar).

- [ ] **Step 1: Ersätt filinnehållet**

Replace the entire contents of `PhotoAnalyzeHost.android.kt` with:
```kotlin
package se.birdy.app.ui.photoanalyze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import se.birdy.app.di.AppGraph
import se.birdy.ml.FrameFormat
import se.birdy.ml.ImageInput
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

private const val CROP_WORKING_MAX_PX = 2048
private const val ANALYZE_LONG_SIDE_PX = 1024
private const val MIN_SHORT_SIDE_PX = 224

@Composable
actual fun PhotoAnalyzeHost(
    graph: AppGraph,
    onLoaded: (sourceJson: String, capturedAtMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoCacheDir =
        remember(context) {
            File(context.cacheDir, "photo-input").apply { mkdirs() }
        }
    val viewModel =
        viewModel {
            graph.photoAnalyzeViewModel(persist = { bytes ->
                val file = File(photoCacheDir, UUID.randomUUID().toString() + ".jpg")
                file.outputStream().use { it.write(bytes) }
                file.absolutePath
            })
        }

    val pendingTakeUri =
        rememberSaveable(stateSaver = uriSaver()) {
            mutableStateOf<Uri?>(null)
        }
    val pendingDecodeUri =
        remember {
            mutableStateOf<Uri?>(null)
        }
    // Arbets-bitmap som crop-skärmen visar (null = visa picker).
    var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Decode off-main → visa crop (eller analysera direkt om bilden är för liten att beskära).
    LaunchedEffect(pendingDecodeUri.value) {
        val uri = pendingDecodeUri.value ?: return@LaunchedEffect
        viewModel.markAnalyzing()
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
            viewModel.analyze(input)
            return@LaunchedEffect
        }
        viewModel.reset() // dölj "Analyzing" medan crop-skärmen visas
        cropBitmap = bmp
    }

    val galleryLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            if (uri != null) pendingDecodeUri.value = uri
        }
    val takePhotoLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = pendingTakeUri.value
            if (success && uri != null) pendingDecodeUri.value = uri
        }

    val bmp = cropBitmap
    if (bmp != null) {
        CropAdjustScreen(
            bitmap = bmp,
            onRotate = {
                val current = cropBitmap
                if (current != null) {
                    val rotated = rotate90(current)
                    cropBitmap = rotated
                    current.recycle()
                }
            },
            onConfirm = { rect ->
                val toFinalize = cropBitmap ?: return@CropAdjustScreen
                cropBitmap = null
                viewModel.markAnalyzing()
                scope.launch {
                    val input = withContext(Dispatchers.IO) { finalizeCrop(toFinalize, rect) }
                    toFinalize.recycle()
                    viewModel.analyze(input)
                }
            },
            onCancel = {
                cropBitmap?.recycle()
                cropBitmap = null
                viewModel.reset()
            },
        )
    } else {
        PhotoAnalyzeScreen(
            viewModel = viewModel,
            onPickFromGallery = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onTakePhoto = {
                val file = File(photoCacheDir, UUID.randomUUID().toString() + ".jpg")
                val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                        context,
                        context.packageName + ".fileprovider",
                        file,
                    )
                pendingTakeUri.value = uri
                takePhotoLauncher.launch(uri)
            },
            onLoaded = onLoaded,
            onBack = onBack,
        )
    }
}

private fun uriSaver(): Saver<Uri?, String> =
    Saver(
        save = { it?.toString() ?: "" },
        restore = { if (it.isEmpty()) null else Uri.parse(it) },
    )

/** Decode → EXIF-rotera → cap långsida till CROP_WORKING_MAX_PX (OOM-skydd). */
private suspend fun decodeForCrop(
    context: Context,
    uri: Uri,
): Bitmap? =
    withContext(Dispatchers.IO) {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / sample > CROP_WORKING_MAX_PX * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }

        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@withContext null
        val rotation = readExifRotation(context, uri)
        val rotated =
            if (rotation != 0) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
        val ls = maxOf(rotated.width, rotated.height)
        if (ls > CROP_WORKING_MAX_PX) {
            val s = CROP_WORKING_MAX_PX.toFloat() / ls
            Bitmap.createScaledBitmap(rotated, (rotated.width * s).toInt(), (rotated.height * s).toInt(), true)
                .also { if (it !== rotated) rotated.recycle() }
        } else {
            rotated
        }
    }

/** Beskär enligt rect, skala långsida till ANALYZE_LONG_SIDE_PX, encoda JPEG 90. */
private fun finalizeCrop(
    src: Bitmap,
    rect: CropRect,
): ImageInput {
    val cropped = Bitmap.createBitmap(src, rect.left, rect.top, rect.width, rect.height)
    val (w, h) = scaleToLongSide(cropped.width, cropped.height, target = ANALYZE_LONG_SIDE_PX)
    val scaled =
        Bitmap.createScaledBitmap(cropped, w, h, true).also {
            if (it !== cropped) cropped.recycle()
        }
    val baos = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 90, baos)
    scaled.recycle()
    return ImageInput(
        bytes = baos.toByteArray(),
        widthPx = w,
        heightPx = h,
        format = FrameFormat.JPEG,
    )
}

private fun rotate90(src: Bitmap): Bitmap {
    val m = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

private fun readExifRotation(
    context: Context,
    uri: Uri,
): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

private fun scaleToLongSide(
    w: Int,
    h: Int,
    target: Int,
): Pair<Int, Int> {
    val long = maxOf(w, h)
    if (long <= target) return w to h
    val ratio = target.toFloat() / long
    return (w * ratio).toInt() to (h * ratio).toInt()
}
```

- [ ] **Step 2: Bygg modulen**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/ui/photoanalyze/PhotoAnalyzeHost.android.kt
git commit -m "feat(crop): PhotoAnalyzeHost-pipeline med crop-steg före analys"
```

---

## Task 10: Device-verify crop (SM-S918B)

**Files:** inga kodändringar — manuell verifiering. Kräver fysisk enhet → "händerna borta"-protokollet.

- [ ] **Step 1: Bygg + installera**

Run:
```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 2: Verifiera galleri-vägen**

- Öppna foto-upload → "Välj från galleri" → välj ett fågelfoto.
- Crop-skärmen visas med hela bilden vald + thirds-linjer.
- Dra hörn → crop-rektangeln krymper, stannar vid kanter, kan inte bli orimligt liten.
- Tryck "Rotera" → bilden roterar 90°, crop nollställs till hela bilden.
- Tryck "Analysera" → MatchResult visar den beskurna/roterade bilden korrekt orienterad.

- [ ] **Step 3: Verifiera ta-foto-vägen**

- Foto-upload → "Ta foto" → ta ett foto i systemkameran → bekräfta.
- Samma crop-skärm visas → beskär + analysera → korrekt i MatchResult.

- [ ] **Step 4: Verifiera avbryt + back**

- Öppna crop → "Avbryt" → tillbaka till picker (ingen analys).
- Öppna crop → systemets back-gest → tillbaka till picker (ingen analys).

- [ ] **Step 5: Skärmdumpar**

Spara 3 skärmdumpar (crop galleri, crop efter rotation, MatchResult med beskuren bild) till `docs/superpowers/screenshots/`. Radera ev. privat innehåll.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/screenshots/
git commit -m "docs(screenshots): crop+rotera device-verify på SM-S918B"
```

---

## Task 11: Version-bump + status + slutverifiering

**Files:**
- Modify: `androidApp/build.gradle.kts`
- Modify: `CLAUDE.md`

- [ ] **Step 1: Bumpa version**

In `androidApp/build.gradle.kts`, change:
```kotlin
        versionCode = 116
        versionName = "1.1.0-rc1"
```
to:
```kotlin
        versionCode = 117
        versionName = "1.1.0-rc2"
```
> Versionsval: stannar i den batchade 1.1-trainen (release-strategin "starta klockan tidigt, batcha innehållet"). Om dessa features istället ska bli en egen release-train, sätt `versionName = "1.2.0-rc1"`. Detta är en release-koordinationsfråga — bekräfta med Albin vid behov.

- [ ] **Step 2: Uppdatera CLAUDE.md-status**

In `CLAUDE.md`, add a new bullet at the top of the "## Status"-block summarizing: kamera-zoom (1–10× preset-chips) + crop/rotera uppladdade bilder tillagt, versionCode 117 / "1.1.0-rc2", plan-doc `docs/superpowers/plans/2026-05-30-camera-zoom-and-upload-crop.md`.

- [ ] **Step 3: ktlintFormat på nya/ändrade filer**

Run: `./gradlew ktlintFormat`
Expected: formaterar ev. avvikelser i de nya filerna.

- [ ] **Step 4: Full verifiering**

Run:
```bash
./gradlew :shared:ml:jvmTest :composeApp:testDebugUnitTest ktlintCheck detekt
```
Expected: ALLA gröna (`ZoomTest`, `CropGeometryTest`, befintliga tester, ktlint, detekt).

- [ ] **Step 5: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add androidApp/build.gradle.kts CLAUDE.md
git commit -m "chore(release): versionCode 117 / 1.1.0-rc2 — zoom + crop; status-uppdatering"
```

---

## Self-review-anteckningar (utförd vid planskrivning)

**Spec-täckning:** Zoom-interface (T2) + impl (T3) + presets/klamp (T1) + chips-UI (T4) + device-verify (T5). Crop-geometri (T6) + UI (T8) + pipeline (T9) + strängar (T7) + device-verify (T10). Edge-cases: OOM-cap (T9 `decodeForCrop`), min-storlek (T6 + T8 `MIN_CROP_SIDE_PX` + T9 <224-guard), deviceMax-klamp (T1), zoom-reset (T3 `stop()`/`start()`), avbryt/back (T8 `BackHandler` + T9 `onCancel`). Versionsbump (T11). ✅

**Typ-konsistens:** `ZoomState`(ratio/minRatio/maxRatio) används identiskt i Zoom.kt, CameraSource.kt, AndroidCameraSource.kt, ZoomChips.kt. `CropRect`(left/top/right/bottom + width/height), `CropHandle`, `CropGeometry.fullRect/resizeToCorner/move` används identiskt i CropGeometry.kt, CropGeometryTest.kt, CropAdjustScreen, PhotoAnalyzeHost. `zoomPresets(maxRatio)` matchar mellan Zoom.kt och ZoomChips.kt. ✅

**Inga placeholders:** all kod komplett; alla kommandon har förväntad output. ✅
