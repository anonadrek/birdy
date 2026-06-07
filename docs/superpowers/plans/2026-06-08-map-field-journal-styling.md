# Field Journal-karta — tema & vax-sigill-pins — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ge den personliga fynd-kartan ett bläck-på-papper-tema (toner-v2 + duotone-färgfilter) och byt fynd-pinsen mot Birdy-fågeln i en vax-sigill.

**Architecture:** En ren `duotoneMatrix(ink, paper)`-funktion i commonMain bygger en 4×5-färgmatris (luminans → tvåton). osmdroid-hosten i androidMain pekar tile-källan på `toner-v2`, lägger matrisen som färgfilter på tiles-overlayn, och ersätter default-markörikonen med en Canvas-komponerad vax-sigill-bitmap (gräddcirkel + kopparring + navy-tintad fågel + spets).

**Tech Stack:** Kotlin Multiplatform, Compose, osmdroid 6.1.20, MapTiler raster-tiles (@2x/512), Android `ColorMatrixColorFilter` + `Canvas`, compose-resources (`Res.readBytes`), kotlin.test.

**Spec:** `docs/superpowers/specs/2026-06-07-map-field-journal-styling-design.md`
**Bygger på:** item 1 (HiDPI @2x/512 + 8 trådar) redan landad på `feat/map-polish-v2` (`ccec35c2`).

**Build-prefix för alla `./gradlew`-kommandon (bash):**
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

---

## File Structure

| Fil | Ansvar |
|---|---|
| `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTileTheme.kt` (ny) | Tema-konstanter (`PAPER`, `INK`) + ren `duotoneMatrix(ink, paper): FloatArray` |
| `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTileThemeTest.kt` (ny) | Enhetstest för duotone-matrisen |
| `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt` (ny) | `buildBirdySealMarker(res, bird): BitmapDrawable` — Canvas-komponerad sigill |
| `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt` (ändras) | Tile-källa → toner-v2; tiles-färgfilter; sigill-ikon på markörer |

---

## Task 1: Duotone color-matrix (ren logik, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTileTheme.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTileThemeTest.kt`

- [ ] **Step 1: Write the failing test**

Create `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTileThemeTest.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class MapTileThemeTest {
    // Applies a 4x5 ColorMatrix to an opaque (r,g,b) pixel, returns clamped RGB ints.
    private fun apply(
        m: FloatArray,
        r: Int,
        g: Int,
        b: Int,
    ): Triple<Int, Int, Int> {
        fun ch(o: Int) =
            (m[o] * r + m[o + 1] * g + m[o + 2] * b + m[o + 3] * 255 + m[o + 4])
                .roundToInt().coerceIn(0, 255)
        return Triple(ch(0), ch(5), ch(10))
    }

    private fun rgb(c: Int) = Triple((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    @Test
    fun blackMapsToInk() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x2E2417, paper = 0xEFE7D6)
        assertEquals(rgb(0x2E2417), apply(m, 0, 0, 0))
    }

    @Test
    fun whiteMapsToPaper() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x2E2417, paper = 0xEFE7D6)
        assertEquals(rgb(0xEFE7D6), apply(m, 255, 255, 255))
    }

    @Test
    fun midGrayLandsBetweenInkAndPaper() {
        val m = MapTileTheme.duotoneMatrix(ink = 0x000000, paper = 0xFFFFFF)
        val (r, g, b) = apply(m, 128, 128, 128)
        // luminance of pure gray 128 == 128; duotone of black..white == identity gray
        assertEquals(128, r)
        assertEquals(128, g)
        assertEquals(128, b)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapTileThemeTest"`
Expected: FAIL — compile error, `MapTileTheme` / `duotoneMatrix` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTileTheme.kt`:

```kotlin
package se.birdy.app.ui.map

/**
 * Field Journal map tint. The map uses MapTiler's grayscale "toner-v2" tiles; this duotone
 * ColorMatrix remaps luminance so black ink -> warm sepia [INK] and white -> paper [PAPER],
 * giving the ink-on-paper look without a custom MapTiler style. Constants tuned on device.
 */
object MapTileTheme {
    /** Field Journal paper (PaperBg #EFE7D6). White tile pixels map here. */
    const val PAPER: Int = 0xEFE7D6

    /** Warm dark sepia. Black tile pixels (ink lines, labels) map here. */
    const val INK: Int = 0x2E2417

    /**
     * Builds a 4x5 [android.graphics.ColorMatrix] array (row-major, channels 0..255) that maps a
     * pixel's luminance L to `ink + L*(paper - ink)` per channel. Pure: no android.graphics types.
     */
    fun duotoneMatrix(
        ink: Int,
        paper: Int,
    ): FloatArray {
        val ir = (ink shr 16) and 0xFF
        val ig = (ink shr 8) and 0xFF
        val ib = ink and 0xFF
        val pr = (paper shr 16) and 0xFF
        val pg = (paper shr 8) and 0xFF
        val pb = paper and 0xFF

        // Rec.601 luma weights; they sum to 1.0 so white -> paper exactly.
        val lr = 0.299f
        val lg = 0.587f
        val lb = 0.114f

        val dr = (pr - ir) / 255f
        val dg = (pg - ig) / 255f
        val db = (pb - ib) / 255f

        return floatArrayOf(
            lr * dr, lg * dr, lb * dr, 0f, ir.toFloat(),
            lr * dg, lg * dg, lb * dg, 0f, ig.toFloat(),
            lr * db, lg * db, lb * db, 0f, ib.toFloat(),
            0f, 0f, 0f, 1f, 0f,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testDebugUnitTest --tests "se.birdy.app.ui.map.MapTileThemeTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTileTheme.kt \
        composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTileThemeTest.kt
git commit -m "feat(map): pure duotone ColorMatrix (paper<->sepia) for tile theme"
```

---

## Task 2: Point tiles at toner-v2 + apply the duotone filter

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt`

No unit test (osmdroid/Android glue) — verified by compile + device-verify (Task 4).

- [ ] **Step 1: Swap the tile source to toner-v2**

In `MapScreenHost.android.kt`, replace the comment + `mapTilerSource` block. Find:

```kotlin
// 512px @2x ("retina") tiles render crisp on high-DPI phones and cut the tile
// count ~4× vs 256px (each tile covers 4× the screen area), so the map is sharper
// AND fills faster. MapTiler exposes HiDPI via the "@2x.png" suffix (the "/512/"
// path form returns 404 for outdoor-v2). Source name is suffixed so the on-disk
// tile cache doesn't reuse stale, blurry 256px tiles from the previous build.
private const val MAPTILER_TILE_SIZE = 512

private fun mapTilerSource(apiKey: String): OnlineTileSourceBase =
    object : XYTileSource(
        "MapTiler-Outdoor-Retina",
        0,
        20,
        MAPTILER_TILE_SIZE,
        "@2x.png",
        arrayOf("https://api.maptiler.com/maps/outdoor-v2/"),
        "© MapTiler © OpenStreetMap contributors",
    ) {
```

Replace with (only the style id, base URL, and source name change; @2x/512 kept):

```kotlin
// 512px @2x ("retina") tiles render crisp on high-DPI phones and cut the tile
// count ~4× vs 256px, so the map is sharper AND fills faster. MapTiler exposes
// HiDPI via the "@2x.png" suffix. Base style is "toner-v2" (grayscale ink) which
// the duotone ColorMatrix (see applyFieldJournalTheme) tints to paper+sepia.
// Source name is suffixed so the on-disk cache doesn't mix toner with old tiles.
private const val MAPTILER_TILE_SIZE = 512

private fun mapTilerSource(apiKey: String): OnlineTileSourceBase =
    object : XYTileSource(
        "MapTiler-Toner-Retina",
        0,
        20,
        MAPTILER_TILE_SIZE,
        "@2x.png",
        arrayOf("https://api.maptiler.com/maps/toner-v2/"),
        "© MapTiler © OpenStreetMap contributors",
    ) {
```

- [ ] **Step 2: Add the ColorMatrix import**

Near the other `android.*` / osmdroid imports at the top of the file, add:

```kotlin
import android.graphics.ColorMatrixColorFilter
```

- [ ] **Step 3: Apply the duotone filter to the tiles overlay**

In the `remember { ... }` block, find the `MapView(context).apply { ... }` call:

```kotlin
            MapView(context).apply {
                setTileSource(mapTilerSource(BuildConfig.MAPTILER_API_KEY))
                setMultiTouchControls(true)
                setUseDataConnection(true)
            }
```

Replace with:

```kotlin
            MapView(context).apply {
                setTileSource(mapTilerSource(BuildConfig.MAPTILER_API_KEY))
                setMultiTouchControls(true)
                setUseDataConnection(true)
                overlayManager.tilesOverlay.setColorFilter(
                    ColorMatrixColorFilter(
                        MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER),
                    ),
                )
            }
```

- [ ] **Step 4: Compile + lint**

Run:
```bash
./gradlew :composeApp:ktlintAndroidMainSourceSetCheck :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL. (`MapTileTheme` resolves from commonMain; `overlayManager.tilesOverlay.setColorFilter` resolves from osmdroid `TilesOverlay`.)

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt
git commit -m "feat(map): toner-v2 tiles + duotone paper/sepia filter on tiles overlay"
```

---

## Task 3: Wax-seal Birdy-bird marker

**Files:**
- Create: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt`

No unit test (Canvas/Bitmap glue) — verified by compile + device-verify (Task 4).

- [ ] **Step 1: Create the seal-marker builder**

Create `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt`:

```kotlin
package se.birdy.app.ui.map

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable

private const val COPPER = 0xFFA8552D.toInt() // AccentCopper
private const val NAVY = 0xFF1F3A5F.toInt() // StampNavy
private const val CREAM_HI = 0xFFF4EDDC.toInt()
private const val CREAM_LO = 0xFFE5DBC4.toInt()
private const val SHADOW = 0x66281910

/**
 * Composes the find marker: a cream wax-seal disc with a copper ring, a navy-tinted Birdy bird,
 * and a downward point whose tip marks the find. Anchor the marker at (CENTER, BOTTOM) so the
 * point tip sits on the coordinate. [bird] is the copper hero_bird silhouette (any tint works
 * since it's re-tinted via SRC_IN). Sizes are in dp via [res] density.
 */
fun buildBirdySealMarker(
    res: Resources,
    bird: Bitmap,
): BitmapDrawable {
    val density = res.displayMetrics.density
    fun dp(v: Float) = v * density

    val ring = dp(3f)
    val diameter = dp(46f)
    val point = dp(9f)
    val pad = dp(3f) // breathing room for the drop shadow
    val w = (diameter + pad * 2).toInt()
    val h = (diameter + point + pad * 2).toInt()

    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val cx = w / 2f
    val cy = pad + diameter / 2f
    val r = diameter / 2f

    // Downward point (drawn first, behind the disc).
    val pointPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COPPER
            setShadowLayer(dp(2f), 0f, dp(1f), SHADOW)
        }
    val path =
        Path().apply {
            moveTo(cx - dp(7f), cy + r - dp(2f))
            lineTo(cx + dp(7f), cy + r - dp(2f))
            lineTo(cx, cy + r + point)
            close()
        }
    canvas.drawPath(path, pointPaint)

    // Cream disc with a soft drop shadow.
    val discPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                RadialGradient(
                    cx - r * 0.25f, cy - r * 0.3f, r * 1.3f,
                    CREAM_HI, CREAM_LO, Shader.TileMode.CLAMP,
                )
            setShadowLayer(dp(2f), 0f, dp(1f), SHADOW)
        }
    canvas.drawCircle(cx, cy, r, discPaint)

    // Copper ring.
    val ringPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ring
            color = COPPER
        }
    canvas.drawCircle(cx, cy, r - ring / 2f, ringPaint)

    // Navy-tinted bird, centered, ~60% of the disc.
    val birdPaint =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(NAVY, PorterDuff.Mode.SRC_IN)
        }
    val target = diameter * 0.6f
    val scale = target / maxOf(bird.width, bird.height)
    val bw = bird.width * scale
    val bh = bird.height * scale
    val dst = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
    canvas.drawBitmap(bird, null, dst, birdPaint)

    return BitmapDrawable(res, bmp)
}
```

- [ ] **Step 2: Load the bird + build the seal once in the host**

In `MapScreenHost.android.kt`, add imports near the top:

```kotlin
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import birdy_bird_scanner.composeapp.generated.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

Then, inside the `MapScreenHost` composable, immediately AFTER the `val mapView = remember { ... }` block and BEFORE the `DisposableEffect(lifecycleOwner)` block, add:

```kotlin
    var sealIcon by remember { mutableStateOf<BitmapDrawable?>(null) }
    LaunchedEffect(Unit) {
        sealIcon =
            withContext(Dispatchers.Default) {
                val bytes = Res.readBytes("files/branding/hero_bird.png")
                val bird = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                buildBirdySealMarker(context.resources, bird)
            }
    }
```

- [ ] **Step 3: Use the seal icon on each marker**

In the same file, find the `LaunchedEffect(pins) { ... }` block and change its key to `pins, sealIcon`, and set the icon + anchor on each marker. Find:

```kotlin
    LaunchedEffect(pins) {
        mapView.overlays.clear()
        val geoPoints =
            pins.map { pin ->
                val point = GeoPoint(pin.latitude, pin.longitude)
                val marker =
                    Marker(mapView).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "#${pin.stampNumber}"
                        setOnMarkerClickListener { _, _ ->
                            onPinClick(pin.observationId)
                            true
                        }
                    }
                mapView.overlays.add(marker)
                point
            }
```

Replace with:

```kotlin
    LaunchedEffect(pins, sealIcon) {
        mapView.overlays.clear()
        val icon = sealIcon
        val geoPoints =
            pins.map { pin ->
                val point = GeoPoint(pin.latitude, pin.longitude)
                val marker =
                    Marker(mapView).apply {
                        position = point
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "#${pin.stampNumber}"
                        if (icon != null) this.icon = icon
                        setOnMarkerClickListener { _, _ ->
                            onPinClick(pin.observationId)
                            true
                        }
                    }
                mapView.overlays.add(marker)
                point
            }
```

(The default osmdroid pin shows until `sealIcon` finishes loading; once set, the effect re-runs and swaps in the seal.)

- [ ] **Step 4: Compile + lint**

Run:
```bash
./gradlew :composeApp:ktlintAndroidMainSourceSetCheck :androidApp:assembleDebug
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt \
        composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt
git commit -m "feat(map): wax-seal Birdy-bird marker icon for finds"
```

---

## Task 4: Device-verify + tune (manual, on SM-S918B)

**Not a code task** — visual verification on Albin's phone. The look constants (`INK`, sizes) are tuned here.

- [ ] **Step 1: Silence the phone** — ask Albin to enable Do-Not-Disturb / silence notifications BEFORE any ADB-driving (a private chat notification surfaced during the last map verify). Confirm `MAPTILER_API_KEY` is set in local `gradle.properties`.

- [ ] **Step 2: Build + install**

```bash
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android.debug/se.birdy.android.MainActivity
```

- [ ] **Step 3: Inject a test pin** (no live bird needed). Per the backlog runbook (`docs/superpowers/plans/2026-06-07-map-polish-v2-backlog.md`): `adb shell am force-stop se.birdy.android.debug` → `adb exec-out run-as se.birdy.android.debug cat databases/birdy-observations.db > obs.db` (bash, byte-safe) → host `py` (sqlite3) insert a row with `latitude`/`longitude` (e.g. 59.3293/18.0686) + real `species_id` (e.g. `Q25485`) → `adb push` to `/data/local/tmp` → `run-as ... cp` back → relaunch.

- [ ] **Step 4: Verify** by screencap (delete any screencap that catches private content immediately):
  - Tiles render **crisp** + **paper/sepia** toned (not grayscale, not generic outdoor).
  - Streets / coastline / labels stay legible down to street zoom.
  - Pins are the **wax-seal with navy bird**, point tip on the coordinate, readable against the map.

- [ ] **Step 5: Tune if needed** — adjust in code and rebuild:
  - Too dark / muddy → lighten `MapTileTheme.INK` (e.g. `0x3A2E1C`).
  - Navy bird hard to read at size → bump disc `diameter` or swap bird tint to `COPPER` in `MapMarkerIcon.android.kt`.
  - Point tip off the coordinate → nudge anchor or `pad`.
  Commit any tune as `fix(map): tune Field Journal map theme after device-verify`.

- [ ] **Step 6: Clean up the injected row**

```bash
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell pm clear se.birdy.android.debug
```

---

## Self-Review (done by plan author)

- **Spec coverage:** tile-källa→toner-v2 (T2) ✅; duotone-ColorMatrix ren + test (T1) ✅; vax-sigill-markör (T3) ✅; attribution (T2 keeps MapTiler/OSM — toner-v2 served by MapTiler, same terms; re-confirm at device-verify) ✅; oförändrat premium-gate/empty-state/zoom (untouched) ✅; device-verify + TDD-kärna (T4/T1) ✅. Out-of-scope items (clustering, info-window, custom style, thumbnails) intentionally absent ✅.
- **Placeholders:** none — every code step is complete.
- **Type consistency:** `MapTileTheme.duotoneMatrix(ink, paper): FloatArray` used identically in T1 + T2; `buildBirdySealMarker(res, bird): BitmapDrawable` defined in T3 Step 1, called in T3 Step 2; `sealIcon: BitmapDrawable?` consistent.
