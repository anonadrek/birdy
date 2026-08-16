# i4 — Paritets-svep på iOS: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stänga de tre sista feature-luckorna mot Android v1.2 på iOS — personlig fynd-karta (MapKit + MapTiler-tiles + Field Journal-duotone), lokala notiser (UNUserNotificationCenter, foreground-omschemaläggning) och PDF-export (UIGraphicsPDFRenderer) — plus rest-städ.

**Architecture:** MapKit + `MKTileOverlay`-subklass mot samma MapTiler raster-tiles som Android, tintade per tile med samma duotone-matris via Core Image (beslut 2 i spec:en — MapLibre är dokumenterad fallback). Notiser: payload-bygget hoistas till commonMain så Android-workers och nya `IosNotificationScheduler` delar innehållslogik; iOS förschemalägger dagens fågel ~7 dagar och omschemalägger allt vid foreground (beslut 3). PDF: geometri/palett/strängar extraheras till commonMain-`JournalPdfMetrics`; iOS-actualen ritar samma fem sidor med UIKit. Allt wire:as via befintliga `AppGraph`-slots + expect/actual-sömmar — inga nya beroenden, ingen Swift, ingen cinterop.

**Tech Stack:** Kotlin/Native mot systemramverk (`platform.MapKit`, `platform.CoreImage`, `platform.CoreLocation`, `platform.UserNotifications`, `platform.UIKit`), Compose Multiplatform `UIKitView`, kotlinx-datetime, xcodegen.

**Spec:** `docs/superpowers/specs/2026-08-16-ios-i4-parity-sweep-design.md`

## Global Constraints

- **Android förblir shippbar efter varje commit:** `./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt` grön.
- **iOS-raden per commit:** `./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64` grön (efter Task 12 ingår även `:shared:pdf:iosSimulatorArm64Test`).
- **Skal-miljö (Mac):** exportera vid behov `JAVA_HOME="$HOME/.local/java21/Contents/Home"` och för alla `xcodebuild`/`xcrun`-anrop `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` (varje nytt skal drabbas).
- **Inga nya beroenden, ingen CocoaPods, ingen ny Swift-fil.** Endast systemramverk via K/N-bindningar.
- **Trap-katalogen gäller:** K/N failable ObjC-init kastar rå NPE (använd `uiImageFromDataOrNull`); ObjC-delegater är weak → strong-retaina i Kotlin; `CLLocationManager` MÅSTE skapas på main-tråden (runloop-krav); VM:er stänger aldrig bootstrap-ägda singletons.
- **compose-resources når inte K/N-testbinärer** — inga tester får kräva `Res.*`/`getString` på iOS-testtargets.
- **AAC-uppspelning, BGAppRefreshTask, MapLibre = utanför scope** (spec-beslut; ompröva inte).
- **🧑 BLOCKERANDE FÖRBEREDELSE (Albin, före Task 4):** lägg MapTiler-nyckeln i `iosApp/Local.xcconfig` (skapas i Task 1 från sample-filen; samma nyckel som Androids `gradle.properties`). Utan den blir spikens tiles 403-tomma — bygget/gaterna påverkas inte, men den visuella verifieringen kräver nyckeln.
- **Kända icke-mål:** i3:s deferred-minor-triage-tabell finns endast i förra sessionens slutrapport (inte committad — `.superpowers/sdd/` saknar i3-katalog). Ingen task kan konsumera den; flaggas i slutrapporten så Albin kan klistra in tabellen i ett senare pass.

---

### Task 1: MapTiler-nyckelinjektion för iOS (Local.xcconfig → Info.plist → runtime)

**Files:**
- Create: `iosApp/Local.xcconfig.sample`
- Modify: `iosApp/project.yml`
- Modify: `iosApp/iosApp/Info.plist`
- Modify: `.gitignore`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerKey.ios.kt`

**Interfaces:**
- Consumes: —
- Produces: `internal object MapTilerKey { fun value(): String }` (iosMain, paket `se.birdy.app.ui.map`) — tom sträng + NSLog-varning när nyckeln saknas. Task 4 läser den.

- [ ] **Step 1: Skapa sample-filen**

`iosApp/Local.xcconfig.sample`:

```
// Lokal, gitignorerad build-konfig. Kopiera till Local.xcconfig och fyll i:
//   cp Local.xcconfig.sample Local.xcconfig
// Samma nyckel som Androids gradle.properties (MAPTILER_API_KEY).
MAPTILER_API_KEY =
```

- [ ] **Step 2: Gitignorera den riktiga filen**

Lägg till i `.gitignore` under Xcode-sektionen ("# Xcode (för iOS-modulen senare)"):

```
iosApp/Local.xcconfig
```

- [ ] **Step 3: project.yml — configFiles + regenerera-skydd**

I `iosApp/project.yml`, lägg till på target-nivå (samma indrag som `settings:`):

```yaml
    configFiles:
      Debug: Local.xcconfig
      Release: Local.xcconfig
```

- [ ] **Step 4: Info.plist — nyckel-entry**

Lägg till i `iosApp/iosApp/Info.plist`-dicten (Xcode expanderar `$(MAPTILER_API_KEY)` från xcconfig vid bygge):

```xml
    <key>MAPTILER_API_KEY</key>
    <string>$(MAPTILER_API_KEY)</string>
```

- [ ] **Step 5: Runtime-läsaren**

`composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerKey.ios.kt`:

```kotlin
package se.birdy.app.ui.map

import platform.Foundation.NSBundle
import platform.Foundation.NSLog

/**
 * MapTiler-nyckeln på iOS läses ur app-bundlens Info.plist, dit xcodegen expanderar
 * den från gitignorerade iosApp/Local.xcconfig (spec-beslut 5). Saknad nyckel ⇒ tom
 * sträng ⇒ 403-tiles — samma tysta degrade som Androids BuildConfig-default, men loggad.
 */
internal object MapTilerKey {
    fun value(): String {
        val key = NSBundle.mainBundle.objectForInfoDictionaryKey("MAPTILER_API_KEY") as? String ?: ""
        if (key.isBlank()) {
            NSLog("%@", "Birdy/map: MAPTILER_API_KEY saknas — kopiera iosApp/Local.xcconfig.sample till Local.xcconfig och fyll i nyckeln")
        }
        return key
    }
}
```

- [ ] **Step 6: Regenerera Xcode-projektet + verifiera**

```bash
cd iosApp && cp -n Local.xcconfig.sample Local.xcconfig || true && ~/.local/bin/xcodegen generate
```

Förväntat: generate lyckas. Kör sedan iOS-länkgaten:

```bash
export JAVA_HOME="$HOME/.local/java21/Contents/Home" && ./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 7: Android-gate + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
git add iosApp/Local.xcconfig.sample iosApp/project.yml iosApp/iosApp/Info.plist .gitignore composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerKey.ios.kt
git commit -m "feat(ios): i4 T1 — MapTiler-nyckelinjektion via Local.xcconfig → Info.plist → runtime-läsare"
```

---

### Task 2: Duotone-matris → CIColorMatrix-vektorer (ren konvertering, TDD)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/CiColorMatrixVectors.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/CiColorMatrixVectorsTest.kt`

**Interfaces:**
- Consumes: `MapTileTheme.duotoneMatrix(ink, paper): FloatArray` (befintlig, 4×5 rad-major, kanaler 0..255, offset i kolumn 5).
- Produces: `data class CiColorMatrixVectors(val r: FloatArray, val g: FloatArray, val b: FloatArray, val a: FloatArray, val bias: FloatArray)` + `fun ciVectorsFrom(colorMatrix: FloatArray): CiColorMatrixVectors` — CIColorMatrix-konventionen: alla vektorer är 4 element, bias i 0..1-skala. Task 4 matar dessa till `CIFilter("CIColorMatrix")`.

- [ ] **Step 1: Skriv failande test**

`composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/CiColorMatrixVectorsTest.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class CiColorMatrixVectorsTest {
    /** CI-modellen: out.r = dot(in, r) + bias[0], allt i 0..1. */
    private fun apply(
        v: CiColorMatrixVectors,
        rgba: FloatArray,
    ): FloatArray {
        fun dot(vec: FloatArray) = vec[0] * rgba[0] + vec[1] * rgba[1] + vec[2] * rgba[2] + vec[3] * rgba[3]
        return floatArrayOf(dot(v.r) + v.bias[0], dot(v.g) + v.bias[1], dot(v.b) + v.bias[2], dot(v.a) + v.bias[3])
    }

    private fun assertClose(
        expected: Float,
        actual: Float,
    ) = assertTrue(abs(expected - actual) < 0.005f, "expected $expected got $actual")

    @Test
    fun white_maps_to_paper() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        val out = apply(v, floatArrayOf(1f, 1f, 1f, 1f))
        assertClose(0xEF / 255f, out[0])
        assertClose(0xE7 / 255f, out[1])
        assertClose(0xD6 / 255f, out[2])
    }

    @Test
    fun black_maps_to_ink() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        val out = apply(v, floatArrayOf(0f, 0f, 0f, 1f))
        assertClose(0x2E / 255f, out[0])
        assertClose(0x24 / 255f, out[1])
        assertClose(0x17 / 255f, out[2])
    }

    @Test
    fun alpha_passes_through() {
        val v = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))
        assertClose(0.5f, apply(v, floatArrayOf(0.3f, 0.3f, 0.3f, 0.5f))[3])
    }
}
```

- [ ] **Step 2: Kör testet — förväntat FAIL** ("unresolved reference ciVectorsFrom")

```bash
./gradlew :composeApp:testDebugUnitTest --tests "*CiColorMatrixVectorsTest*"
```

- [ ] **Step 3: Implementera**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/CiColorMatrixVectors.kt`:

```kotlin
package se.birdy.app.ui.map

/**
 * Androids 4×5-ColorMatrix (rad-major; rad = [→R, →G, →B, →A, offset i 0..255])
 * omuttryckt i Core Images CIColorMatrix-konvention (fyra 4-vektorer + bias, allt 0..1).
 * Ren matte så färgpariteten mellan osmdroids ColorMatrixColorFilter och iOS-tintningen
 * bevisas i test i stället för med ögonmått.
 */
data class CiColorMatrixVectors(
    val r: FloatArray,
    val g: FloatArray,
    val b: FloatArray,
    val a: FloatArray,
    val bias: FloatArray,
)

fun ciVectorsFrom(colorMatrix: FloatArray): CiColorMatrixVectors {
    require(colorMatrix.size == 20) { "expected 4x5 row-major ColorMatrix, got ${colorMatrix.size}" }

    fun row(i: Int) = floatArrayOf(colorMatrix[i * 5], colorMatrix[i * 5 + 1], colorMatrix[i * 5 + 2], colorMatrix[i * 5 + 3])
    return CiColorMatrixVectors(
        r = row(0),
        g = row(1),
        b = row(2),
        a = row(3),
        bias =
            floatArrayOf(
                colorMatrix[4] / 255f,
                colorMatrix[9] / 255f,
                colorMatrix[14] / 255f,
                colorMatrix[19] / 255f,
            ),
    )
}
```

Obs: `data class` med `FloatArray`-fält triggar ktlints/detekts equals/hashCode-varning i vissa lägen — om gaten klagar, byt `data class` mot vanlig `class` (ingen equals-användning finns).

- [ ] **Step 4: Kör testet — förväntat PASS** (samma kommando som steg 2), plus iOS-testtarget:

```bash
./gradlew :composeApp:iosSimulatorArm64Test --tests "*CiColorMatrixVectorsTest*"
```

- [ ] **Step 5: Full gate + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/CiColorMatrixVectors.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/CiColorMatrixVectorsTest.kt
git commit -m "feat(map): i4 T2 — duotone-ColorMatrix → CIColorMatrix-vektorer, testbevisad färgparitet"
```

---

### Task 3: MapMarkerSpec — vaxsigill-geometrin till commonMain (behavior-preserving)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapMarkerSpec.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt`

**Interfaces:**
- Consumes: —
- Produces: `object MapMarkerSpec` med exakt dessa konstanter (Task 5:s iOS-ritning läser dem):

- [ ] **Step 1: Skapa spec-objektet**

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapMarkerSpec.kt` — värdena är kopierade ur `MapMarkerIcon.android.kt` och får INTE ändras:

```kotlin
package se.birdy.app.ui.map

/**
 * Delad geometri + palett för fynd-markören (cream vaxsigill-disc, koppar-ring,
 * navy-tintad Birdy-fågel, nedåtpekande spets vars topp markerar fyndet).
 * Android ritar med android.graphics (MapMarkerIcon.android.kt), iOS med CoreGraphics —
 * båda MÅSTE läsa härifrån så plattformarna inte divergerar. Mått i dp/pt.
 */
object MapMarkerSpec {
    const val COPPER: Long = 0xFFA8552D // AccentCopper
    const val NAVY: Long = 0xFF1F3A5F // StampNavy
    const val CREAM_HI: Long = 0xFFF4EDDC
    const val CREAM_LO: Long = 0xFFE5DBC4
    const val SHADOW: Long = 0x66281910

    const val RING_WIDTH: Float = 3f
    const val DISC_DIAMETER: Float = 46f
    const val POINT_HEIGHT: Float = 9f
    const val PADDING: Float = 3f // luft för drop-skuggan
    const val POINT_HALF_WIDTH: Float = 7f
    const val POINT_TOP_INSET: Float = 2f // spetsens bas ligger 2dp in i discen

    const val SHADOW_BLUR: Float = 2f
    const val SHADOW_DY: Float = 1f

    /** RadialGradient-parametrar relativt disc-radien r: center (cx - r*CX, cy - r*CY), radie r*RADIUS. */
    const val GRADIENT_CX_OFFSET: Float = 0.25f
    const val GRADIENT_CY_OFFSET: Float = 0.3f
    const val GRADIENT_RADIUS: Float = 1.3f

    /** Fågelsilhuettens långsida som andel av disc-diametern. */
    const val BIRD_FRACTION: Float = 0.6f

    /** Markör-bitmapens totalmått (dp/pt) — BÅDA plattformarnas canvas-storlek räknas härifrån. */
    fun markerWidth(): Float = DISC_DIAMETER + PADDING * 2

    fun markerHeight(): Float = DISC_DIAMETER + POINT_HEIGHT + PADDING * 2
}
```

och test `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapMarkerSpecTest.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapMarkerSpecTest {
    @Test
    fun marker_canvas_dimensions_match_android_formula() {
        assertEquals(52f, MapMarkerSpec.markerWidth()) // 46 + 2*3
        assertEquals(61f, MapMarkerSpec.markerHeight()) // 46 + 9 + 2*3
    }
}
```

- [ ] **Step 2: Refaktorera Android-ritningen till spec-konstanterna**

I `MapMarkerIcon.android.kt`: ta bort de fem lokala färgkonstanterna (`COPPER`/`NAVY`/`CREAM_HI`/`CREAM_LO`/`SHADOW`) och ersätt varje användning + varje magiskt tal med spec-konstanten. `w`/`h` räknas via `dp(MapMarkerSpec.markerWidth())`/`dp(MapMarkerSpec.markerHeight())`:

```kotlin
// färger: MapMarkerSpec.COPPER.toInt(), MapMarkerSpec.NAVY.toInt(), osv.
val ring = dp(MapMarkerSpec.RING_WIDTH)
val diameter = dp(MapMarkerSpec.DISC_DIAMETER)
val point = dp(MapMarkerSpec.POINT_HEIGHT)
val pad = dp(MapMarkerSpec.PADDING)
// spetsen:
moveTo(cx - dp(MapMarkerSpec.POINT_HALF_WIDTH), cy + r - dp(MapMarkerSpec.POINT_TOP_INSET))
lineTo(cx + dp(MapMarkerSpec.POINT_HALF_WIDTH), cy + r - dp(MapMarkerSpec.POINT_TOP_INSET))
// skuggor: setShadowLayer(dp(MapMarkerSpec.SHADOW_BLUR), 0f, dp(MapMarkerSpec.SHADOW_DY), MapMarkerSpec.SHADOW.toInt())
// gradient:
RadialGradient(
    cx - r * MapMarkerSpec.GRADIENT_CX_OFFSET,
    cy - r * MapMarkerSpec.GRADIENT_CY_OFFSET,
    r * MapMarkerSpec.GRADIENT_RADIUS,
    MapMarkerSpec.CREAM_HI.toInt(),
    MapMarkerSpec.CREAM_LO.toInt(),
    Shader.TileMode.CLAMP,
)
// fågeln: val target = diameter * MapMarkerSpec.BIRD_FRACTION
```

`@Suppress("MagicNumber")` kan tas bort om inga magiska tal återstår.

- [ ] **Step 3: Full gate (bevisar behavior-preservation via kompilering + befintliga tester) + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapMarkerSpec.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapMarkerSpecTest.kt composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapMarkerIcon.android.kt
git commit -m "refactor(map): i4 T3 — vaxsigill-geometri/palett extraherad till delad MapMarkerSpec"
```

---

### Task 4: MapKit-spike — MapTilerTileOverlay + duotone-tint + minimal host (RISKGRIND)

> **RULING (SDD-körningen 2026-08-16, riskgrinden utlöst + löst):** K/N-bindningen (Kotlin 2.1.20) gör BÅDA `MKTileOverlay`-override-punkterna (`loadTileAtPath`, `URLForTilePath`) `final` — Kotlin-subklassen i Step 1 nedan är inte byggbar (klib-verifierat; CI-tint-pipelinen bevisades däremot fungera fristående). Beslut i stället för MapLibre-fallback: **Swift-shim-bro** — `iosApp/iosApp/BirdyTileOverlay.swift` (~25 rader, ren vidarebefordran; Swift kan overrida där K/N inte kan) + Kotlin `IosTileFetcher` (URL/NSURLSession-cache/CI-tint — Step 1-kodens semantik oförändrad) + Kotlin `IosMapOverlayBridge` (factory som `iOSApp.swift` registrerar vid start; hosten hämtar overlayn därifrån). Alla spec-utfall bevaras (MapKit, samma tiles, samma duotone-matris, noll nya beroenden); minimal Swift-shim är husarkitektur per CLAUDE.md. Läs Step 1-koden nedan som SEMANTIK för `IosTileFetcher`, inte som subklass.

Detta är spec:ens riskgrind: bevisa `MKTileOverlay`-subklass + CI-tint + `canReplaceMapContent` i simulatorn INNAN resten byggs. **STOPP-REGEL:** om K/N-subklassningen av `MKTileOverlay` (struct-param i `loadTileAtPath`) eller CI-tintningen inte går att få att fungera efter rimlig felsökning — avbryt, rapportera, och fall tillbaka på MapLibre-spåret per spec §Risker. Bygg INTE vidare på task 5–6 i det läget.

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTilerUrls.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTilerUrlsTest.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerTileOverlay.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapScreenHost.ios.kt`

**Interfaces:**
- Consumes: `MapTilerKey.value()` (T1), `ciVectorsFrom` + `MapTileTheme` (T2), `MapPin(observationId, latitude, longitude, stampNumber)` (befintlig commonMain).
- Produces: `fun mapTilerTileUrl(z: Long, x: Long, y: Long, apiKey: String): String` (commonMain, testad — samma URL-form som Androids XYTileSource); `internal class MapTilerTileOverlay(apiKey: String) : MKTileOverlay` (tintade tiles, `canReplaceMapContent=true`); `MapScreenHost.ios`-actual som visar MKMapView med overlay + standardpins (vaxsigill kommer i T5).

- [ ] **Step 0: URL-byggaren (TDD)**

Test `composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTilerUrlsTest.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlin.test.Test
import kotlin.test.assertEquals

class MapTilerUrlsTest {
    @Test
    fun builds_same_shape_as_android_xy_tile_source() {
        assertEquals(
            "https://api.maptiler.com/maps/toner-v2/13/4400/2686@2x.png?key=abc123",
            mapTilerTileUrl(z = 13, x = 4400, y = 2686, apiKey = "abc123"),
        )
    }
}
```

Kör → FAIL. Implementera `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTilerUrls.kt`:

```kotlin
package se.birdy.app.ui.map

/** Samma URL-form som Androids XYTileSource i MapScreenHost.android.kt — ändra BÅDA eller ingen. */
fun mapTilerTileUrl(
    z: Long,
    x: Long,
    y: Long,
    apiKey: String,
): String = "https://api.maptiler.com/maps/toner-v2/$z/$x/$y@2x.png?key=$apiKey"
```

Kör → PASS (jvm + `:composeApp:iosSimulatorArm64Test`).

- [ ] **Step 1: Tile-overlayen**

`composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerTileOverlay.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIVector
import platform.CoreImage.filterWithName
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLCache
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataTaskWithURL
import platform.MapKit.MKTileOverlay
import platform.MapKit.MKTileOverlayPath
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import se.birdy.app.ui.photoanalyze.uiImageFromDataOrNull

/**
 * MapTiler toner-v2 @2x-raster (samma källa som Androids osmdroid-XYTileSource) med
 * Field Journal-duotonen applicerad PER TILE via CIColorMatrix — iOS-spegeln av
 * osmdroids ColorMatrixColorFilter. canReplaceMapContent=true ersätter Apples baskarta
 * helt. Hämtning via egen NSURLSession med disk-cache (MapTiler skickar cache-headers)
 * + User-Agent (paritet med osmdroids 403-krav).
 */
@OptIn(ExperimentalForeignApi::class)
internal class MapTilerTileOverlay(
    private val apiKey: String,
) : MKTileOverlay(URLTemplate = null) {
    private val session: NSURLSession =
        NSURLSession.sessionWithConfiguration(
            NSURLSessionConfiguration.defaultSessionConfiguration.apply {
                val cacheDir =
                    (NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String) +
                        "/map_tiles"
                URLCache = NSURLCache(memoryCapacity = 20uL * 1024uL * 1024uL, diskCapacity = 100uL * 1024uL * 1024uL, directoryURL = NSURL.fileURLWithPath(cacheDir))
                HTTPAdditionalHeaders = mapOf("User-Agent" to "se.birdy.ios")
            },
        )

    // En delad CIContext — dyr att skapa, trådsäker att använda.
    private val ciContext = CIContext()
    private val vectors = ciVectorsFrom(MapTileTheme.duotoneMatrix(MapTileTheme.INK, MapTileTheme.PAPER))

    init {
        canReplaceMapContent = true
        tileSize = CGSizeMake(512.0, 512.0)
        minimumZ = 0
        maximumZ = 20
    }

    override fun loadTileAtPath(
        path: CValue<MKTileOverlayPath>,
        result: (NSData?, NSError?) -> Unit,
    ) {
        val (x, y, z) = path.useContents { Triple(x, y, z) }
        val url = NSURL.URLWithString(mapTilerTileUrl(z = z, x = x, y = y, apiKey = apiKey))
        if (url == null) {
            result(null, null)
            return
        }
        session
            .dataTaskWithURL(url) { data, _, error ->
                if (data == null) {
                    result(null, error)
                } else {
                    result(tinted(data) ?: data, null)
                }
            }.resume()
    }

    /** Duotone-tint; null vid dekodfel → råtilen används hellre än ett hål i kartan. */
    private fun tinted(data: NSData): NSData? {
        val source = uiImageFromDataOrNull(data)?.CIImage ?: CIImage.imageWithData(data) ?: return null
        val filter = CIFilter.filterWithName("CIColorMatrix") ?: return null
        filter.setValue(source, forKey = "inputImage")
        fun vec(v: FloatArray) = CIVector.vectorWithX(v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), v[3].toDouble())
        filter.setValue(vec(vectors.r), forKey = "inputRVector")
        filter.setValue(vec(vectors.g), forKey = "inputGVector")
        filter.setValue(vec(vectors.b), forKey = "inputBVector")
        filter.setValue(vec(vectors.a), forKey = "inputAVector")
        filter.setValue(vec(vectors.bias), forKey = "inputBiasVector")
        val output = filter.outputImage ?: return null
        val cg = ciContext.createCGImage(output, fromRect = output.extent) ?: return null
        return UIImagePNGRepresentation(UIImage.imageWithCGImage(cg))
    }
}
```

OBS kompilerings-jämkning förväntas: exakta K/N-namn (`URLTemplate`-parametern, `URLCache`-settern, `CIImage.imageWithData`, `createCGImage(_, fromRect =)`) kan avvika en aning — låt kompilatorn styra, behåll semantiken. `uiImageFromDataOrNull` är `internal` i samma modul (`IosImageDecode.kt`) — om `UIImage.CIImage` är null för PNG-data (vanligt: UIImage backas av CGImage), använd `CIImage.imageWithData(data)`-vägen som primär i stället.

- [ ] **Step 2: Minimal host-actual (ersätter ComingSoon-stubben)**

`composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapScreenHost.ios.kt` — mönstra `UIKitView`-användningen på `CameraPreviewHost.ios.kt` (samma modul):

```kotlin
package se.birdy.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.MapKit.MKMapView
import platform.MapKit.MKPointAnnotation
import platform.MapKit.addOverlay

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun MapScreenHost(
    pins: List<MapPin>,
    onPinClick: (String) -> Unit,
    modifier: Modifier,
) {
    val mapView =
        remember {
            MKMapView().apply {
                addOverlay(MapTilerTileOverlay(MapTilerKey.value()))
            }
        }
    UIKitView(factory = { mapView }, modifier = modifier)
    // Spike-läge: systemets standardpins; vaxsigill + kamera + delegat kommer i nästa task.
    androidx.compose.runtime.LaunchedEffect(pins) {
        mapView.removeAnnotations(mapView.annotations)
        pins.forEach { pin ->
            mapView.addAnnotation(
                MKPointAnnotation().apply { setCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude)) },
            )
        }
    }
}
```

VIKTIGT: utan overlay-renderer visas tile-overlayen INTE — MKMapView behöver en delegate som svarar `MKTileOverlayRenderer` i `mapView:rendererForOverlay:`. Lägg till en minimal delegat i samma fil:

```kotlin
import platform.Foundation.NSObject
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKTileOverlay
import platform.MapKit.MKTileOverlayRenderer

internal class BirdyMapDelegate : NSObject(), MKMapViewDelegateProtocol {
    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol,
    ): MKOverlayRenderer =
        (rendererForOverlay as? MKTileOverlay)?.let { MKTileOverlayRenderer(tileOverlay = it) }
            ?: MKOverlayRenderer(overlay = rendererForOverlay)
}
```

och i `remember`-blocket: skapa delegaten FÖRE MKMapView, håll den i en egen `remember`-val (**ObjC-delegater är weak — Kotlin-referensen är det enda som håller den vid liv**), sätt `delegate = birdyDelegate`.

- [ ] **Step 3: Bygg + boot + screenshot i simulatorn (riskgrindens bevis)**

1. Gör en TEMPORÄR, LOKAL edit (committas EJ) så appen bootar direkt i kart-fliken: i `composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`, hitta start-destinationen (grep efter `AppRoute.` + `startDestination` eller motsvarande) och byt till `AppRoute.Map`.
2. Bygg + installera + boota i iPhone 17-simulatorn (UDID `183DD149-45ED-49B8-A2C1-70317698B383`) enligt receptet i auto-memory `reference_ios_simulator_build_and_verify` (`DEVELOPER_DIR` krävs, `-destination id=<udid>`).
3. `xcrun simctl io <udid> screenshot docs/superpowers/screenshots/i4-01-map-spike.png`
4. Bedöm: kartan visar **duotone-tintade tiles** (papper/sepia — INTE Apples standardkarta, INTE gråskala). Kräver att Albin lagt nyckeln i `Local.xcconfig`; utan nyckel: verifiera i stället att appen inte kraschar + att NSLog-varningen syns + att tile-requests skickas, och flagga screenshot-checken som kvarstående.
5. **Återställ den temporära editen** (`git checkout -- composeApp/src/commonMain/kotlin/se/birdy/app/ui/scaffold/AppScaffold.kt`).

- [ ] **Step 4: Gater + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapTilerUrls.kt composeApp/src/commonTest/kotlin/se/birdy/app/ui/map/MapTilerUrlsTest.kt composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapTilerTileOverlay.kt composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapScreenHost.ios.kt docs/superpowers/screenshots/i4-01-map-spike.png
git commit -m "feat(ios): i4 T4 — MapKit-spike: MKTileOverlay + CIColorMatrix-duotone bevisad i sim (riskgrind passerad)"
```

---

### Task 5: Full kart-host — vaxsigill-pins, kamera, pin-tap + städ

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/IosSealMarker.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/MapScreenHost.ios.kt`
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/map/MapScreen.kt` (rad 37: stale kommentar)
- Delete: `composeApp/src/iosMain/kotlin/se/birdy/app/ui/components/IosComingSoonPanel.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-en/strings.xml` (ta bort `ios_coming_soon_title`/`ios_coming_soon_body`)

**Interfaces:**
- Consumes: `MapMarkerSpec` (T3), `MapTilerTileOverlay` + `BirdyMapDelegate` (T4), `uiImageFromDataOrNull` (befintlig), `Res.readBytes("files/branding/hero_bird.png")` (samma resurs som Android-markören).
- Produces: färdig `MapScreenHost.ios` med `BirdyPinAnnotation(observationId, stampNumber)`, vaxsigill-`MKAnnotationView` (ankrad bottom-center), kameralogik (1 pin → region ~4 km; flera → union-rect + 48 pt padding), pin-tap → `onPinClick(observationId)`.

- [ ] **Step 1: Vaxsigill-ritningen (CoreGraphics, från MapMarkerSpec)**

`composeApp/src/iosMain/kotlin/se/birdy/app/ui/map/IosSealMarker.kt`:

```kotlin
package se.birdy.app.ui.map

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextDrawRadialGradient
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetShadowWithColor
import platform.CoreGraphics.CGGradientCreateWithColorComponents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGGradientDrawsAfterEndLocation
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingModeAlwaysTemplate

private fun uiColor(argb: Long): UIColor =
    UIColor(
        red = ((argb shr 16) and 0xFF).toDouble() / 255.0,
        green = ((argb shr 8) and 0xFF).toDouble() / 255.0,
        blue = (argb and 0xFF).toDouble() / 255.0,
        alpha = ((argb shr 24) and 0xFF).toDouble() / 255.0,
    )

/**
 * iOS-tvillingen till buildBirdySealMarker (MapMarkerIcon.android.kt) — ritar från
 * samma MapMarkerSpec så plattformarna inte kan divergera. Mått i pt (≙ dp);
 * scale=0.0 ger enhetens naturliga skala. Returnerar null endast om ingen
 * grafikkontext kan skapas (då faller anroparen tillbaka på systempinnen).
 */
@OptIn(ExperimentalForeignApi::class)
internal fun buildBirdySealMarkerImage(bird: UIImage): UIImage? {
    val s = MapMarkerSpec
    val w = s.DISC_DIAMETER + s.PADDING * 2
    val h = s.DISC_DIAMETER + s.POINT_HEIGHT + s.PADDING * 2
    val cx = w / 2.0
    val cy = (s.PADDING + s.DISC_DIAMETER / 2).toDouble()
    val r = (s.DISC_DIAMETER / 2).toDouble()

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(w.toDouble(), h.toDouble()), false, 0.0)
    val ctx = UIGraphicsGetCurrentContext() ?: run {
        UIGraphicsEndImageContext()
        return null
    }

    val shadowColor = uiColor(s.SHADOW).CGColor
    // Nedåtspets (bakom discen), med skugga.
    CGContextSaveGState(ctx)
    CGContextSetShadowWithColor(ctx, CGSizeMake(0.0, s.SHADOW_DY.toDouble()), s.SHADOW_BLUR.toDouble(), shadowColor)
    uiColor(s.COPPER).setFill()
    UIBezierPath().apply {
        moveToPoint(CGPointMake(cx - s.POINT_HALF_WIDTH, cy + r - s.POINT_TOP_INSET))
        addLineToPoint(CGPointMake(cx + s.POINT_HALF_WIDTH, cy + r - s.POINT_TOP_INSET))
        addLineToPoint(CGPointMake(cx, cy + r + s.POINT_HEIGHT))
        closePath()
    }.fill()
    CGContextRestoreGState(ctx)

    // Cream-disc med radial gradient + skugga.
    CGContextSaveGState(ctx)
    CGContextSetShadowWithColor(ctx, CGSizeMake(0.0, s.SHADOW_DY.toDouble()), s.SHADOW_BLUR.toDouble(), shadowColor)
    UIBezierPath.bezierPathWithOvalInRect(CGRectMake(cx - r, cy - r, r * 2, r * 2)).addClip()
    val colorSpace = CGColorSpaceCreateDeviceRGB()
    fun comps(argb: Long) =
        listOf(16, 8, 0).map { ((argb shr it) and 0xFF).toDouble() / 255.0 } + 1.0
    val components = (comps(s.CREAM_HI) + comps(s.CREAM_LO)).toDoubleArray()
    // gradient från (cx - r*0.25, cy - r*0.3) med radie r*1.3 — samma som Android
    components.usePinned { pinned ->
        doubleArrayOf(0.0, 1.0).usePinned { locs ->
            val gradient = CGGradientCreateWithColorComponents(colorSpace, pinned.addressOf(0), locs.addressOf(0), 2u)
            CGContextDrawRadialGradient(
                ctx,
                gradient,
                CGPointMake(cx - r * s.GRADIENT_CX_OFFSET, cy - r * s.GRADIENT_CY_OFFSET),
                0.0,
                CGPointMake(cx - r * s.GRADIENT_CX_OFFSET, cy - r * s.GRADIENT_CY_OFFSET),
                r * s.GRADIENT_RADIUS,
                kCGGradientDrawsAfterEndLocation,
            )
        }
    }
    CGContextRestoreGState(ctx)

    // Koppar-ring.
    uiColor(s.COPPER).setStroke()
    UIBezierPath.bezierPathWithOvalInRect(
        CGRectMake(
            cx - r + s.RING_WIDTH / 2.0,
            cy - r + s.RING_WIDTH / 2.0,
            (r - s.RING_WIDTH / 2.0) * 2,
            (r - s.RING_WIDTH / 2.0) * 2,
        ),
    ).apply { lineWidth = s.RING_WIDTH.toDouble() }.stroke()

    // Navy-tintad fågel, centrerad, BIRD_FRACTION av discen.
    val birdTemplate = bird.imageWithRenderingMode(UIImageRenderingModeAlwaysTemplate)
    uiColor(s.NAVY).set()
    val target = s.DISC_DIAMETER * s.BIRD_FRACTION
    val birdW = bird.size.useContents { width }
    val birdH = bird.size.useContents { height }
    val scale = target / maxOf(birdW, birdH)
    val bw = birdW * scale
    val bh = birdH * scale
    birdTemplate.drawInRect(CGRectMake(cx - bw / 2, cy - bh / 2, bw, bh))

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}
```

(Import-jämkning vid kompilering: `usePinned`/`addressOf` ur `kotlinx.cinterop`, `useContents` för `CGSize`. Om template-tint via `drawInRect` inte tintar — fall tillbaka på CGContextClipToMask-mönstret: clip till fågelns CGImage som mask + fyll rect med navy.)

- [ ] **Step 2: Full host — annotationer, delegat, kamera**

Skriv om `MapScreenHost.ios.kt` (bygger vidare på T4):

```kotlin
internal class BirdyPinAnnotation(
    val observationId: String,
    val stampNumber: Int,
) : MKPointAnnotation()

internal class BirdyMapDelegate(
    var sealImage: UIImage?,
    var onPinClick: (String) -> Unit,
) : NSObject(), MKMapViewDelegateProtocol {
    override fun mapView(mapView: MKMapView, rendererForOverlay: MKOverlayProtocol): MKOverlayRenderer =
        (rendererForOverlay as? MKTileOverlay)?.let { MKTileOverlayRenderer(tileOverlay = it) }
            ?: MKOverlayRenderer(overlay = rendererForOverlay)

    override fun mapView(mapView: MKMapView, viewForAnnotation: MKAnnotationProtocol): MKAnnotationView? {
        val annotation = viewForAnnotation as? BirdyPinAnnotation ?: return null
        val view =
            mapView.dequeueReusableAnnotationViewWithIdentifier(REUSE_ID)
                ?: MKAnnotationView(annotation = annotation, reuseIdentifier = REUSE_ID)
        view.annotation = annotation
        val image = sealImage
        if (image != null) {
            view.image = image
            // Ankra bottom-center: vyn centreras på koordinaten som default; skjut upp halva höjden
            // så spetsens topp hamnar på fyndet (Androids ANCHOR_CENTER/ANCHOR_BOTTOM).
            view.centerOffset = CGPointMake(0.0, -image.size.useContents { height } / 2.0)
        }
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation as? BirdyPinAnnotation ?: return
        mapView.deselectAnnotation(annotation, animated = false)
        onPinClick(annotation.observationId)
    }

    private companion object { const val REUSE_ID = "birdySealPin" }
}
```

Host-composablen:
- `remember { BirdyMapDelegate(sealImage = null, onPinClick = onPinClick) }` — uppdatera `delegate.onPinClick = onPinClick` i en `SideEffect` (lambda kan byta identitet).
- `LaunchedEffect(Unit)`: ladda fågeln på `Dispatchers.Default` — `Res.readBytes("files/branding/hero_bird.png")` → `uiImageFromDataOrNull(bytes.toNSData())` → `buildBirdySealMarkerImage(...)` → sätt `delegate.sealImage`; tvinga omritning genom att re-adda annotationerna. (`toNSData`-hjälpare finns i `FileBytes.kt`/`IosImageDecode.kt` — återanvänd; skapa inte en ny kopia.)
- `LaunchedEffect(pins)`: `removeAnnotations` → skapa `BirdyPinAnnotation` per pin (`title = "#${pin.stampNumber}"` behövs ej — Android-titeln används inte i callout) → kamera:

```kotlin
if (pins.size == 1) {
    val c = CLLocationCoordinate2DMake(pins[0].latitude, pins[0].longitude)
    mapView.setRegion(MKCoordinateRegionMakeWithDistance(c, 4000.0, 4000.0), animated = false)
} else if (pins.size > 1) {
    var rect = MKMapRectNull.readValue()
    pins.forEach { pin ->
        val point = MKMapPointForCoordinate(CLLocationCoordinate2DMake(pin.latitude, pin.longitude))
        rect = MKMapRectUnion(rect, point.useContents { MKMapRectMake(x, y, 0.1, 0.1) })
    }
    mapView.setVisibleMapRect(rect, edgePadding = UIEdgeInsetsMake(48.0, 48.0, 48.0, 48.0), animated = false)
}
```

(48 pt ≈ Androids 96 px @2x-padding.)
- `DisposableEffect(Unit) { onDispose { mapView.delegate = null } }` — delegaten släpps med composablen; MKMapView själv ägs av `remember` och dör med den.

- [ ] **Step 3: Städ**

1. Radera `IosComingSoonPanel.kt` (enda call-siten var gamla map-hosten).
2. Ta bort `ios_coming_soon_title` + `ios_coming_soon_body` ur BÅDA strings.xml (values + values-en).
3. `MapScreen.kt` rad 37: ersätt kommentaren med:

```kotlin
/** Android ritar osmdroid-ytan; iOS ritar MKMapView + MapTilerTileOverlay (i4). */
```

- [ ] **Step 4: Sim-screenshot (samma temporära start-tab-trick som T4, återställ efteråt)**

`docs/superpowers/screenshots/i4-02-map-pins.png` — duotone-tiles + minst en vaxsigill-pin (kräver ett fynd med koordinater; injicera via temporär hårdkodad pin-lista i hosten om inga fynd finns i sim-appen — även den editen återställs).

- [ ] **Step 5: Gater + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add -A composeApp/src docs/superpowers/screenshots/i4-02-map-pins.png
git commit -m "feat(ios): i4 T5 — full kart-host: vaxsigill-pins, kamera, pin-tap; ComingSoon-panelen borttagen"
```

---

### Task 6: IosLocationProvider + platsbehörighet + Info.plist-strängar + graf-wiring

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/location/IosLocationProvider.kt`
- Modify: `iosApp/iosApp/Info.plist` + `iosApp/iosApp/en.lproj/InfoPlist.strings` + `iosApp/iosApp/sv.lproj/InfoPlist.strings`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`

**Interfaces:**
- Consumes: `interface LocationProvider { suspend fun current(): LatLng? }` + `LatLng(latitude, longitude)` (commonMain; kontraktet: null vid nekad permission/timeout, **kastar aldrig**).
- Produces: `class IosLocationProvider : LocationProvider` + `object IosLocationPermissionRequester { fun request() }`; `AppGraph.locationProvider` + `requestLocationPermission` icke-null på iOS.

- [ ] **Step 1: Providern + requestern**

`composeApp/src/iosMain/kotlin/se/birdy/app/location/IosLocationProvider.kt`:

```kotlin
package se.birdy.app.location

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.Foundation.NSError
import platform.Foundation.NSObject
import kotlin.coroutines.resume

/**
 * One-shot plats via CoreLocation — spegel av AndroidLocationProvider-kontraktet:
 * null vid nekad behörighet, timeout (8 s) eller fel; KASTAR ALDRIG.
 *
 * Gotchas som styr formen:
 * - CLLocationManager MÅSTE skapas på en tråd med runloop → allt sker på Dispatchers.Main.
 * - manager.delegate är weak → delegaten hålls vid liv av coroutine-closuren tills resume.
 */
@OptIn(ExperimentalForeignApi::class)
class IosLocationProvider : LocationProvider {
    override suspend fun current(): LatLng? =
        runCatching {
            withContext(Dispatchers.Main) {
                val manager = CLLocationManager()
                if (!isAuthorized(manager)) return@withContext null
                manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
                val fix =
                    withTimeoutOrNull(8_000L) { requestSingleFix(manager) }
                        ?: manager.location // lastKnown-fallback, som Android
                fix?.coordinate?.useContents { LatLng(latitude, longitude) }
            }
        }.getOrNull()

    private fun isAuthorized(manager: CLLocationManager): Boolean =
        manager.authorizationStatus == kCLAuthorizationStatusAuthorizedWhenInUse ||
            manager.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways

    private suspend fun requestSingleFix(manager: CLLocationManager): CLLocation? =
        suspendCancellableCoroutine { cont ->
            val delegate =
                object : NSObject(), CLLocationManagerDelegateProtocol {
                    override fun locationManager(
                        manager: CLLocationManager,
                        didUpdateLocations: List<*>,
                    ) {
                        if (cont.isActive) cont.resume(didUpdateLocations.firstOrNull() as? CLLocation)
                    }

                    override fun locationManager(
                        manager: CLLocationManager,
                        didFailWithError: NSError,
                    ) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            manager.delegate = delegate
            manager.requestLocation()
            cont.invokeOnCancellation {
                // Referera delegate + manager så closuren retainar dem till cancel/resume.
                manager.stopUpdatingLocation()
                manager.delegate = null
                delegate.hashCode()
            }
        }
}

/**
 * Fyrar iOS-platsdialogen. Ingen result-hantering — capture degraderar graciöst
 * (Android-paritet: requestLocationPermLauncher har tom callback).
 * Managern är en retained singleton: släpps den medan dialogen visas fyras callbacken aldrig.
 */
object IosLocationPermissionRequester {
    private val scope = kotlinx.coroutines.MainScope()
    private var manager: CLLocationManager? = null

    fun request() {
        scope.launch {
            val m = manager ?: CLLocationManager().also { manager = it }
            m.requestWhenInUseAuthorization()
        }
    }
}
```

(Kompilerings-jämkning: `authorizationStatus` är instansproperty på iOS 14+ — min-target är 16.0 ✓. `MainScope().launch`-importen: `kotlinx.coroutines.launch`.)

- [ ] **Step 2: Info.plist + lokaliserade strängar**

`Info.plist` (basspråk EN, samma mönster som kamera/mic):

```xml
    <key>NSLocationWhenInUseUsageDescription</key>
    <string>Birdy uses your location to place your finds on your private map. Coordinates are stored only on your device and never leave it.</string>
```

`en.lproj/InfoPlist.strings`:

```
"NSLocationWhenInUseUsageDescription" = "Birdy uses your location to place your finds on your private map. Coordinates are stored only on your device and never leave it.";
```

`sv.lproj/InfoPlist.strings`:

```
"NSLocationWhenInUseUsageDescription" = "Birdy använder din plats för att placera dina fynd på din privata karta. Koordinaterna sparas bara på din enhet och lämnar den aldrig.";
```

- [ ] **Step 3: Wiring i `buildIosAppGraph()`**

Lägg till i `AppGraph(...)`-anropet i `IosAppGraph.kt`:

```kotlin
        locationProvider = IosLocationProvider(),
        requestLocationPermission = { IosLocationPermissionRequester.request() },
```

- [ ] **Step 4: Gater + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/iosMain/kotlin/se/birdy/app/location/IosLocationProvider.kt iosApp/iosApp composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt
git commit -m "feat(ios): i4 T6 — IosLocationProvider (CoreLocation one-shot) + platsbehörighet + Info.plist-strängar"
```

---

### Task 7: NotificationTimes — datummatten till commonMain (TDD) + Android-scheduler-refaktor

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/notifications/NotificationTimes.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/notifications/NotificationTimesTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt`

**Interfaces:**
- Consumes: kotlinx-datetime.
- Produces (Task 10 bygger kalender-triggers av `LocalDateTime`; Android räknar om till millis):

```kotlin
object NotificationTimes {
    fun nextDaily(now: Instant, zone: TimeZone, hour: Int, minute: Int): LocalDateTime
    fun nextWeekly(now: Instant, zone: TimeZone, day: DayOfWeek, hour: Int, minute: Int): LocalDateTime
    fun upcomingDaily(now: Instant, zone: TimeZone, hour: Int, minute: Int, count: Int): List<LocalDateTime>
    fun millisUntil(target: LocalDateTime, now: Instant, zone: TimeZone): Long
}
```

- [ ] **Step 1: Skriv failande tester**

`NotificationTimesTest.kt` — porta semantiken ur `NotificationSchedulerImpl`s tre privata funktioner (fasta instants, `TimeZone.of("Europe/Stockholm")`):

```kotlin
package se.birdy.app.notifications

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTimesTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = LocalDateTime(y, mo, d, h, mi).toInstant(zone)

    @Test
    fun nextDaily_before_target_is_today() =
        assertEquals(LocalDateTime(2026, 8, 17, 8, 0), NotificationTimes.nextDaily(at(2026, 8, 17, 6, 30), zone, 8, 0))

    @Test
    fun nextDaily_after_target_rolls_to_tomorrow() =
        assertEquals(LocalDateTime(2026, 8, 18, 8, 0), NotificationTimes.nextDaily(at(2026, 8, 17, 8, 0), zone, 8, 0))

    @Test
    fun nextWeekly_same_day_before_time_is_today() =
        // 2026-08-16 är en söndag
        assertEquals(LocalDateTime(2026, 8, 16, 18, 0), NotificationTimes.nextWeekly(at(2026, 8, 16, 12, 0), zone, DayOfWeek.SUNDAY, 18, 0))

    @Test
    fun nextWeekly_same_day_at_time_rolls_a_week() =
        assertEquals(LocalDateTime(2026, 8, 23, 18, 0), NotificationTimes.nextWeekly(at(2026, 8, 16, 18, 0), zone, DayOfWeek.SUNDAY, 18, 0))

    @Test
    fun nextWeekly_other_day() =
        // onsdag 09:00 sett från söndag
        assertEquals(LocalDateTime(2026, 8, 19, 9, 0), NotificationTimes.nextWeekly(at(2026, 8, 16, 12, 0), zone, DayOfWeek.WEDNESDAY, 9, 0))

    @Test
    fun upcomingDaily_returns_consecutive_days() {
        val list = NotificationTimes.upcomingDaily(at(2026, 8, 17, 9, 0), zone, 8, 0, count = 3)
        assertEquals(
            listOf(LocalDateTime(2026, 8, 18, 8, 0), LocalDateTime(2026, 8, 19, 8, 0), LocalDateTime(2026, 8, 20, 8, 0)),
            list,
        )
    }

    @Test
    fun millisUntil_is_positive_delta() =
        assertEquals(90 * 60 * 1000L, NotificationTimes.millisUntil(LocalDateTime(2026, 8, 17, 8, 0), at(2026, 8, 17, 6, 30), zone))
}
```

- [ ] **Step 2: Kör — förväntat FAIL** (`./gradlew :composeApp:testDebugUnitTest --tests "*NotificationTimesTest*"`)

- [ ] **Step 3: Implementera**

`NotificationTimes.kt` — flytta logiken ur `NotificationSchedulerImpl.millisUntilNext`/`millisUntilNextSunday`/`millisUntilNextDayOfWeek` (de två sistnämnda är identiska sånär som på dagparametern → EN `nextWeekly`):

```kotlin
package se.birdy.app.notifications

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Nästa-förekomst-matte för notisscheman, delad mellan Androids WorkManager-delays
 * (via [millisUntil]) och iOS UNCalendarNotificationTrigger (LocalDateTime direkt).
 * Semantik hoistad oförändrad från NotificationSchedulerImpl (androidMain):
 * "nu == måltid" rullar framåt (>= på minuten).
 */
object NotificationTimes {
    fun nextDaily(now: Instant, zone: TimeZone, hour: Int, minute: Int): LocalDateTime {
        val local = now.toLocalDateTime(zone)
        val today = LocalDateTime(local.year, local.monthNumber, local.dayOfMonth, hour, minute)
        return if (today.toInstant(zone) > now) {
            today
        } else {
            val tomorrow = local.date.plus(1, DateTimeUnit.DAY)
            LocalDateTime(tomorrow.year, tomorrow.monthNumber, tomorrow.dayOfMonth, hour, minute)
        }
    }

    fun nextWeekly(now: Instant, zone: TimeZone, day: DayOfWeek, hour: Int, minute: Int): LocalDateTime {
        val local = now.toLocalDateTime(zone)
        val rawDays = (day.isoDayNumber - local.dayOfWeek.isoDayNumber + 7) % 7
        val days =
            if (rawDays == 0 && (local.hour > hour || (local.hour == hour && local.minute >= minute))) 7 else rawDays
        val date = local.date.plus(days, DateTimeUnit.DAY)
        return LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
    }

    /** [count] på varandra följande dagliga förekomster, med start i [nextDaily]. */
    fun upcomingDaily(now: Instant, zone: TimeZone, hour: Int, minute: Int, count: Int): List<LocalDateTime> {
        val first = nextDaily(now, zone, hour, minute)
        return (0 until count).map { offset ->
            val date = first.date.plus(offset, DateTimeUnit.DAY)
            LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute)
        }
    }

    fun millisUntil(target: LocalDateTime, now: Instant, zone: TimeZone): Long = (target.toInstant(zone) - now).inWholeMilliseconds
}
```

- [ ] **Step 4: Kör — förväntat PASS** (jvm + `:composeApp:iosSimulatorArm64Test --tests "*NotificationTimesTest*"`)

- [ ] **Step 5: Refaktorera Android-schedulern till delegering**

I `NotificationSchedulerImpl`: radera de tre privata funktionerna; ersätt anropen:

```kotlin
.setInitialDelay(NotificationTimes.millisUntil(NotificationTimes.nextDaily(clock.now(), zone, 8, 0), clock.now(), zone), TimeUnit.MILLISECONDS)
// weekly recap:
.setInitialDelay(NotificationTimes.millisUntil(NotificationTimes.nextWeekly(clock.now(), zone, DayOfWeek.SUNDAY, 18, 0), clock.now(), zone), TimeUnit.MILLISECONDS)
// trophy:
.setInitialDelay(NotificationTimes.millisUntil(NotificationTimes.nextWeekly(clock.now(), zone, DayOfWeek.WEDNESDAY, 9, 0), clock.now(), zone), TimeUnit.MILLISECONDS)
```

(Fånga `clock.now()` i en lokal `val now` per metod så båda anropen ser samma "nu".)

- [ ] **Step 6: Full gate + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/commonMain/kotlin/se/birdy/app/notifications/NotificationTimes.kt composeApp/src/commonTest/kotlin/se/birdy/app/notifications/NotificationTimesTest.kt composeApp/src/androidMain/kotlin/se/birdy/app/notifications/NotificationSchedulerImpl.kt
git commit -m "refactor(notifications): i4 T7 — nästa-förekomst-matten hoistad till delad NotificationTimes (TDD)"
```

---

### Task 8: NotificationPayloads — innehållsbygget till commonMain + Android-workers → tunna skal

**Files:**
- Create: `composeApp/src/commonMain/kotlin/se/birdy/app/notifications/NotificationPayloads.kt`
- Test: `composeApp/src/commonTest/kotlin/se/birdy/app/notifications/NotificationPayloadsTest.kt`
- Modify: `composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/DailyBirdWorker.kt`, `WeeklyRecapWorker.kt`, `TrophyProgressWorker.kt`

**Interfaces:**
- Consumes: `WeeklyRecapBuilder(zone).summarize(observations, unlocks, now)` (`isQuiet`/`streakAtRisk`/`observationCount`/`newSpeciesCount`), `TrophyProgress.summarize(items)`, `RecalculateBadgesUseCase(zone = ...).currentValue(rule, observations, species, matchCount)`, `BadgeProgressItem`, `BadgeStringMap.nameFor(id)`, `SeasonTag`, compose-resources `getString` (körs på app-runtime, EJ i K/N-test).
- Produces:

```kotlin
data class NotificationContent(val title: String, val body: String, val deepLink: String)

class NotificationPayloads(
    prefs: UserPreferences,
    observationRepo: ObservationRepository,
    badgeRepo: BadgeRepository,
    badgeCatalog: BadgeCatalog,
    speciesByQid: suspend () -> Map<SpeciesId, Species>,
    speciesNameFor: suspend (qid: String) -> String?,
    selectDailyBird: (suspend (LocalDate) -> DailyBird?)?,
    dailyBirdMatchCount: suspend () -> Int,
    timeZone: TimeZone,
    clock: Clock,
) {
    companion object { fun from(graph: AppGraph): NotificationPayloads }
    suspend fun dailyBird(date: LocalDate): NotificationContent?
    suspend fun weeklyRecap(forceForDev: Boolean = false): NotificationContent?
    suspend fun trophyProgress(forceForDev: Boolean = false): NotificationContent?
}
```

Null = "ingen notis" (avstängd toggle, ingen kandidat, tyst vecka, inget märke på gång) — exakt de Result.success()-utan-notify-vägar dagens workers har.

- [ ] **Step 1: Skriv failande tester (ENDAST null-vägarna — icke-null-vägarna når `getString`, som inte finns i K/N-testbinärer)**

`NotificationPayloadsTest.kt` — återanvänd `se.birdy.app.testing.FakeObservationRepository` + `FakeUserPreferences`; badge-repo-fejk: kopiera det inline-mönster `RecapViewModelTest` använder:

```kotlin
package se.birdy.app.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class NotificationPayloadsTest {
    // build(...)-hjälpare: NotificationPayloads med FakeUserPreferences (alla toggles av som default),
    // FakeObservationRepository (tom), inline-fakes för badgeRepo/catalog, selectDailyBird = null,
    // speciesByQid = { emptyMap() }, speciesNameFor = { null }, dailyBirdMatchCount = { 0 },
    // zone = TimeZone.of("Europe/Stockholm"), clock = Clock.System.

    @Test
    fun dailyBird_disabled_pref_returns_null() = runTest { assertNull(build(dailyEnabled = false).dailyBird(date)) }

    @Test
    fun dailyBird_null_selector_returns_null() = runTest { assertNull(build(dailyEnabled = true, selector = null).dailyBird(date)) }

    @Test
    fun weeklyRecap_disabled_pref_returns_null() = runTest { assertNull(build(recapEnabled = false).weeklyRecap()) }

    @Test
    fun weeklyRecap_quiet_week_no_streak_returns_null() = runTest { assertNull(build(recapEnabled = true).weeklyRecap()) }

    @Test
    fun trophy_disabled_pref_returns_null() = runTest { assertNull(build(trophyEnabled = false).trophyProgress()) }

    @Test
    fun trophy_nothing_in_progress_returns_null() = runTest { assertNull(build(trophyEnabled = true).trophyProgress()) }
}
```

(Implementera `build(...)`-hjälparen konkret i testfilen; toggles sätts via FakeUserPreferences setters — kolla dess API i `composeApp/src/commonTest/kotlin/se/birdy/app/testing/FakeUserPreferences.kt`.)

- [ ] **Step 2: Kör — förväntat FAIL** (unresolved `NotificationPayloads`)

- [ ] **Step 3: Implementera `NotificationPayloads`**

Innehållslogiken flyttas VERBATIM ur de tre workersarna (samma res-nycklar, samma ordning på besluten):

```kotlin
package se.birdy.app.notifications

// imports: kotlinx.coroutines.flow.first, kotlinx.datetime.*, org.jetbrains.compose.resources.getString,
// birdy_bird_scanner.composeapp.generated.resources.*, se.birdy.app.badges.*, se.birdy.app.recap.WeeklyRecapBuilder,
// se.birdy.app.ui.badges.BadgeStringMap, se.birdy.app.di.AppGraph, se.birdy.content.*, se.birdy.domain.dailybird.*, ...

data class NotificationContent(
    val title: String,
    val body: String,
    val deepLink: String,
)

class NotificationPayloads(/* konstruktor enligt Interfaces-blocket */) {
    suspend fun dailyBird(date: LocalDate): NotificationContent? {
        if (!prefs.dailyBirdPushEnabled.first()) return null
        val selector = selectDailyBird ?: return null
        val bird = selector(date) ?: return null
        val displayName = speciesNameFor(bird.speciesId) ?: bird.speciesId
        return NotificationContent(
            title = getString(Res.string.notification_daily_bird_title_fmt, displayName),
            body = getString(seasonBodyRes(bird.seasonTag)),
            deepLink = "birdy://species/${bird.speciesId}",
        )
    }

    suspend fun weeklyRecap(forceForDev: Boolean = false): NotificationContent? {
        if (!forceForDev && !prefs.weeklyRecapPushEnabled.first()) return null
        val observations = observationRepo.observeAll().first()
        val unlocks = badgeRepo.observeUnlocks().first()
        val summary = WeeklyRecapBuilder(timeZone).summarize(observations, unlocks, clock.now())
        return when {
            !summary.isQuiet || forceForDev ->
                NotificationContent(
                    title = getString(Res.string.notification_recap_active_title),
                    body =
                        getString(
                            Res.string.notification_recap_active_body_fmt,
                            summary.observationCount.toString(),
                            summary.newSpeciesCount.toString(),
                        ),
                    deepLink = "birdy://recap",
                )
            summary.streakAtRisk ->
                NotificationContent(
                    title = getString(Res.string.notification_recap_streak_title),
                    body = getString(Res.string.notification_recap_streak_body),
                    deepLink = "birdy://recap",
                )
            else -> null // tyst vecka utan streak-risk → ingen push (spec §3.6)
        }
    }

    suspend fun trophyProgress(forceForDev: Boolean = false): NotificationContent? {
        if (!forceForDev && !prefs.weeklyTrophyPushEnabled.first()) return null
        val observations = observationRepo.observeAll().first()
        val unlocked = badgeRepo.observeUnlocks().first().map { it.badgeId }.toSet()
        val species = speciesByQid()
        val matchCount = dailyBirdMatchCount()
        val recalc = RecalculateBadgesUseCase(zone = timeZone)
        val items =
            badgeCatalog.badges.map { badge ->
                BadgeProgressItem(
                    badgeId = badge.id,
                    current = recalc.currentValue(badge.rule, observations, species, matchCount),
                    target = badge.rule.target,
                    unlocked = badge.id in unlocked,
                )
            }
        val summary = TrophyProgress.summarize(items)
        val closest =
            summary.closest
                ?: (if (forceForDev) items.firstOrNull { !it.unlocked } else null)
                ?: return null
        val closestName = getString(BadgeStringMap.nameFor(closest.badgeId))
        return NotificationContent(
            title = getString(Res.string.notification_trophy_title),
            body =
                getString(
                    Res.string.notification_trophy_body_fmt,
                    summary.unlockedCount.toString(),
                    summary.totalCount.toString(),
                    closestName,
                    closest.current.toString(),
                    closest.target.toString(),
                ),
            deepLink = "birdy://trophy",
        )
    }

    private fun seasonBodyRes(tag: SeasonTag) =
        when (tag) {
            SeasonTag.BREEDING -> Res.string.notification_daily_bird_body_breeding
            SeasonTag.PRESENT -> Res.string.notification_daily_bird_body_present
            SeasonTag.MIGRATING -> Res.string.notification_daily_bird_body_migrating
        }

    companion object {
        fun from(graph: AppGraph): NotificationPayloads =
            NotificationPayloads(
                prefs = graph.userPreferences,
                observationRepo = graph.observationRepository,
                badgeRepo = graph.badgeRepository,
                badgeCatalog = graph.badgeCatalog,
                speciesByQid = { graph.repository.allByQid(graph.defaultLocale) },
                speciesNameFor = { qid -> graph.repository.getById(SpeciesId(qid), graph.defaultLocale).first()?.name },
                selectDailyBird = graph.selectDailyBird,
                dailyBirdMatchCount = { graph.dailyBirdHistory?.totalMatchCount() ?: 0 },
                timeZone = graph.timeZone,
                clock = graph.clock,
            )
    }
}
```

- [ ] **Step 4: Kör testerna — förväntat PASS** (jvm + iOS-target)

- [ ] **Step 5: Workers → tunna skal**

Varje worker behåller: graph-hämtning via `AndroidAppGraphHolder`, `NotificationChannels.ensureCreated`, `NotificationCompat`-bygget, `NOTIF_ID`, `KEY_FORCE_FOR_DEV`, catch/`Result.retry()`. Innehållsdelen ersätts:

```kotlin
// DailyBirdWorker.doWork() kärna:
val graph = AndroidAppGraphHolder.current ?: return Result.success()
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
val content = NotificationPayloads.from(graph).dailyBird(today) ?: return Result.success()
// ... intent = Intent(ACTION_VIEW, Uri.parse(content.deepLink)), title/body från content
```

`WeeklyRecapWorker`: `NotificationPayloads.from(graph).weeklyRecap(forceForDev)` (pref-checken flyttar in i byggaren — ta bort den lokala). `TrophyProgressWorker`: dito med `trophyProgress(forceForDev)`. Deep-link-URI:erna kommer nu från `content.deepLink` — de hårdkodade `"birdy://..."`-strängarna i workers tas bort.

- [ ] **Step 6: Full gate + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/commonMain/kotlin/se/birdy/app/notifications/NotificationPayloads.kt composeApp/src/commonTest/kotlin/se/birdy/app/notifications/NotificationPayloadsTest.kt composeApp/src/androidMain/kotlin/se/birdy/app/notifications/workers/
git commit -m "refactor(notifications): i4 T8 — payload-bygget hoistat till delad NotificationPayloads; workers = tunna skal"
```

---

### Task 9: IosPlatformNotificationsApi + permission-request + graf-wiring (inkl. dagens fågel-luckan)

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/notifications/IosPlatformNotificationsApi.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt`

**Interfaces:**
- Consumes: `PlatformNotificationsApi` (3 metoder), `UNUserNotificationCenter`.
- Produces: `class IosPlatformNotificationsApi : PlatformNotificationsApi` med `fun refreshStatus()` (cachad auth-status; Task 10:s foreground-pass anropar den); graf-slots `platformNotificationsApi`, `requestPostNotificationsPermission`, `selectDailyBird`, `dailyBirdHistory`, `deepLinkFlow` icke-null på iOS. **Upptäckt parity-lucka som stängs här:** `selectDailyBird`/`dailyBirdHistory` har ALDRIG wire:ats på iOS → Dagens fågel har varit död på Lyssna-fliken; notiserna behöver dem ändå.

- [ ] **Step 1: API-actualen**

```kotlin
package se.birdy.app.notifications

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.concurrent.Volatile

/**
 * iOS-actual för PlatformNotificationsApi. getNotificationSettings är async-callback
 * men interfacet är synkront → auth-statusen CACHAS och uppdateras vid init, vid
 * varje foreground (Task 10:s lifecycle-pass) och efter permission-request.
 * Default true = samma optimistiska fallback som SettingsViewModel redan använder.
 */
class IosPlatformNotificationsApi : PlatformNotificationsApi {
    @Volatile private var enabledCache: Boolean = true

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            enabledCache =
                settings?.authorizationStatus == UNAuthorizationStatusAuthorized ||
                settings?.authorizationStatus == UNAuthorizationStatusProvisional
        }
    }

    override fun areNotificationsEnabled(): Boolean = enabledCache

    override fun openAppNotificationSettings() {
        NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
            UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
        }
    }

    override fun needsRuntimePermission(): Boolean = true
}
```

- [ ] **Step 2: Wiring i `buildIosAppGraph()`**

Före `return AppGraph(...)`:

```kotlin
    val dailyBirdHistory =
        se.birdy.data.dailybird
            .DailyBirdHistoryRepositoryImpl(birdyData)
    val dailyBirdSelector =
        se.birdy.domain.dailybird.DailyBirdSelector(
            speciesProvider = { SpeciesRepositoryProvider.get().allByQid(resolvedLocale) },
        )
    val platformNotificationsApi = IosPlatformNotificationsApi()
    val deepLinkFlow =
        kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 4)
```

I `AppGraph(...)`-anropet:

```kotlin
        selectDailyBird = { date -> dailyBirdSelector.selectFor(date) },
        dailyBirdHistory = dailyBirdHistory,
        platformNotificationsApi = platformNotificationsApi,
        requestPostNotificationsPermission = { IosNotificationPermission.request(graphAccessor = { AppGraphHolderIos.current }) },
        deepLinkFlow = deepLinkFlow,
```

**Permission-flödet** (spegel av MainActivitys `requestPermLauncher`-callback — persist + schemalägg vid grant). Lägg i samma fil som API:t:

```kotlin
object IosNotificationPermission {
    private val scope = kotlinx.coroutines.MainScope()

    fun request(graphAccessor: () -> se.birdy.app.di.AppGraph?) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val options = UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                graph.userPreferences.setPushPermissionAsked(true)
                (graph.platformNotificationsApi as? IosPlatformNotificationsApi)?.refreshStatus()
                if (granted) {
                    graph.notificationScheduler?.scheduleDailyBird()
                    graph.notificationScheduler?.scheduleWeeklyRecap()
                    graph.notificationScheduler?.scheduleTrophyProgress()
                }
            }
        }
    }
}
```

Graf-åtkomsten är cirkulär (lambdan skapas innan grafen finns) → inför en minimal holder i `IosAppGraph.kt`, spegel av `AndroidAppGraphHolder`:

```kotlin
internal object AppGraphHolderIos {
    var current: AppGraph? = null
}
```

och sätt `AppGraphHolderIos.current = graph` i `MainViewController.kt` efter `buildIosAppGraph()` (gör `private val graph by lazy` till `buildIosAppGraph().also { AppGraphHolderIos.current = it }`).

- [ ] **Step 3: Gater + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/iosMain/kotlin/se/birdy/app/notifications/IosPlatformNotificationsApi.kt composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt composeApp/src/iosMain/kotlin/se/birdy/app/MainViewController.kt
git commit -m "feat(ios): i4 T9 — IosPlatformNotificationsApi + permission-flöde + graf-wiring (fixar även ovirad Dagens fågel)"
```

---

### Task 10: IosNotificationScheduler + foreground-omschemaläggning + notis-delegat + devTriggers

**Files:**
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/notifications/IosNotificationScheduler.kt`
- Create: `composeApp/src/iosMain/kotlin/se/birdy/app/notifications/IosNotificationLifecycle.kt`
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` + `MainViewController.kt`

**Interfaces:**
- Consumes: `NotificationScheduler` (7 metoder), `NotificationPayloads.from(graph)` (T8), `NotificationTimes` (T7), `AppGraph.deepLinkFlow` (T9), `IosPlatformNotificationsApi.refreshStatus()` (T9).
- Produces: `class IosNotificationScheduler(graphAccessor: () -> AppGraph?) : NotificationScheduler`; `fun installIosNotificationLifecycle(graph: AppGraph)` (foreground-observer + UNUserNotificationCenter-delegat); devTrigger-lambdor i grafen (debug-binärer).

- [ ] **Step 1: Schedulern**

```kotlin
package se.birdy.app.notifications

// imports: kotlinx.coroutines.*, kotlinx.datetime.*, platform.Foundation.NSDateComponents, platform.Foundation.NSLog,
// platform.UserNotifications.*, se.birdy.app.di.AppGraph

/**
 * iOS-schemaläggaren (spec §D): UNCalendarNotificationTrigger kräver innehåll VID
 * schemaläggning → varje schedule* räknar innehållet färskt via NotificationPayloads
 * och skrivs om vid varje foreground (installIosNotificationLifecycle). Dagens fågel
 * förschemaläggs DAILY_WINDOW dagar (deterministisk per datum → exakt innehåll);
 * recap/trofé en förekomst framåt. Payload-null ⇒ pending för den typen städas —
 * schedule* är alltså konvergent oavsett toggle-läge.
 *
 * Jobb per typ hålls så cancel* kan avbryta en pågående beräkning; ensureActive()
 * före add förhindrar att en hunnen-toggla-av-race lämnar en stale notis.
 */
class IosNotificationScheduler(
    private val graphAccessor: () -> AppGraph?,
    private val scope: CoroutineScope = MainScope(),
) : NotificationScheduler {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()
    private var dailyJob: Job? = null
    private var recapJob: Job? = null
    private var trophyJob: Job? = null

    override fun scheduleDailyBird() {
        dailyJob?.cancel()
        dailyJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val payloads = NotificationPayloads.from(graph)
                val slots = NotificationTimes.upcomingDaily(graph.clock.now(), graph.timeZone, hour = 8, minute = 0, count = DAILY_WINDOW)
                removePendingWithPrefix(ID_DAILY_PREFIX)
                slots.forEach { slot ->
                    val content = payloads.dailyBird(slot.date) ?: return@forEach
                    ensureActive()
                    add(id = "$ID_DAILY_PREFIX${slot.date}", content = content, at = slot)
                }
            }
    }

    override fun scheduleWeeklyRecap() {
        recapJob?.cancel()
        recapJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val content = NotificationPayloads.from(graph).weeklyRecap()
                if (content == null) {
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_RECAP))
                    return@launch
                }
                ensureActive()
                add(ID_RECAP, content, NotificationTimes.nextWeekly(graph.clock.now(), graph.timeZone, DayOfWeek.SUNDAY, 18, 0))
            }
    }

    override fun scheduleTrophyProgress() {
        trophyJob?.cancel()
        trophyJob =
            scope.launch {
                val graph = graphAccessor() ?: return@launch
                val content = NotificationPayloads.from(graph).trophyProgress()
                if (content == null) {
                    center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TROPHY))
                    return@launch
                }
                ensureActive()
                add(ID_TROPHY, content, NotificationTimes.nextWeekly(graph.clock.now(), graph.timeZone, DayOfWeek.WEDNESDAY, 9, 0))
            }
    }

    override fun cancelDailyBird() {
        dailyJob?.cancel()
        scope.launch { removePendingWithPrefix(ID_DAILY_PREFIX) }
    }

    override fun cancelStreakRiskCheck() = Unit // Android-legacy-id; har aldrig funnits på iOS

    override fun cancelWeeklyRecap() {
        recapJob?.cancel()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_RECAP))
    }

    override fun cancelTrophyProgress() {
        trophyJob?.cancel()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID_TROPHY))
    }

    private fun add(id: String, content: NotificationContent, at: LocalDateTime) {
        val unContent =
            UNMutableNotificationContent().apply {
                setTitle(content.title)
                setBody(content.body)
                setSound(UNNotificationSound.defaultSound)
                setUserInfo(mapOf(USERINFO_DEEP_LINK to content.deepLink))
            }
        val components =
            NSDateComponents().apply {
                year = at.year.toLong()
                month = at.monthNumber.toLong()
                day = at.dayOfMonth.toLong()
                hour = at.hour.toLong()
                minute = at.minute.toLong()
            }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content = unContent, trigger = trigger)
        center.addNotificationRequest(request) { error ->
            if (error != null) NSLog("%@", "Birdy/notif: add $id failed: ${error.localizedDescription}")
        }
    }

    private suspend fun removePendingWithPrefix(prefix: String) {
        val ids =
            suspendCancellableCoroutine<List<String>> { cont ->
                center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                    cont.resume(requests.orEmpty().mapNotNull { (it as? UNNotificationRequest)?.identifier }.filter { it.startsWith(prefix) })
                }
            }
        if (ids.isNotEmpty()) center.removePendingNotificationRequestsWithIdentifiers(ids)
    }

    companion object {
        const val DAILY_WINDOW = 7
        const val ID_DAILY_PREFIX = "birdy_daily_bird_"
        const val ID_RECAP = "birdy_weekly_recap"
        const val ID_TROPHY = "birdy_trophy_progress"
        const val USERINFO_DEEP_LINK = "deepLink"
    }
}
```

- [ ] **Step 2: Lifecycle-installatören + delegaten**

`IosNotificationLifecycle.kt`:

```kotlin
package se.birdy.app.notifications

// imports: platform.Foundation.NSNotificationCenter, NSOperationQueue, platform.UIKit.UIApplicationDidBecomeActiveNotification,
// platform.UserNotifications.*, platform.Foundation.NSObject, kotlinx.coroutines.*

/**
 * Foreground-omschemaläggningen (spec-beslut 3) + notis-tap → deepLinkFlow.
 * Observer + delegat hålls i globala vals — BÅDA är weak-refererade av systemet.
 * Anropas EN gång från MainViewController efter att grafen byggts.
 */
private var retainedDelegate: BirdyNotificationDelegate? = null
private var retainedObserver: Any? = null
private val lifecycleScope = MainScope()

fun installIosNotificationLifecycle(graph: se.birdy.app.di.AppGraph) {
    val delegate = BirdyNotificationDelegate(graph)
    retainedDelegate = delegate
    UNUserNotificationCenter.currentNotificationCenter().delegate = delegate

    retainedObserver =
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            reschedule(graph)
        }
    reschedule(graph) // även vid kallstart
}

private fun reschedule(graph: se.birdy.app.di.AppGraph) {
    (graph.platformNotificationsApi as? IosPlatformNotificationsApi)?.refreshStatus()
    lifecycleScope.launch {
        if (!graph.userPreferences.pushPermissionAsked.first()) return@launch
        // schedule* är konvergenta (payload-null ⇒ pending städas) → alltid alla tre.
        graph.notificationScheduler?.scheduleDailyBird()
        graph.notificationScheduler?.scheduleWeeklyRecap()
        graph.notificationScheduler?.scheduleTrophyProgress()
    }
}

internal class BirdyNotificationDelegate(
    private val graph: se.birdy.app.di.AppGraph,
) : NSObject(), UNUserNotificationCenterDelegateProtocol {
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionSound)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val deepLink =
            didReceiveNotificationResponse.notification.request.content.userInfo[IosNotificationScheduler.USERINFO_DEEP_LINK] as? String
        if (deepLink != null) graph.deepLinkFlow?.tryEmit(deepLink)
        withCompletionHandler()
    }
}
```

- [ ] **Step 3: Wire scheduler + devTriggers + install**

`IosAppGraph.kt` — i `AppGraph(...)`-anropet:

```kotlin
        notificationScheduler = IosNotificationScheduler(graphAccessor = { AppGraphHolderIos.current }),
        devTriggerDailyBird = devNotifTrigger { payloads, _ -> payloads.dailyBird(todayLocalDate()) },
        devTriggerWeeklyRecap = devNotifTrigger { payloads, _ -> payloads.weeklyRecap(forceForDev = true) },
        devTriggerTrophyProgress = devNotifTrigger { payloads, _ -> payloads.trophyProgress(forceForDev = true) },
```

med hjälparna (i `IosNotificationLifecycle.kt` eller egen fil; `Platform.isDebugBinary`-gate speglar `BuildConfig.DEBUG`):

```kotlin
@OptIn(ExperimentalNativeApi::class)
internal fun devNotifTrigger(
    produce: suspend (NotificationPayloads, AppGraph) -> NotificationContent?,
): (() -> Unit)? {
    if (!Platform.isDebugBinary) return null
    return {
        lifecycleScope.launch {
            val graph = AppGraphHolderIos.current ?: return@launch
            val content = produce(NotificationPayloads.from(graph), graph) ?: return@launch
            val unContent =
                UNMutableNotificationContent().apply {
                    setTitle(content.title)
                    setBody(content.body)
                    setUserInfo(mapOf(IosNotificationScheduler.USERINFO_DEEP_LINK to content.deepLink))
                }
            val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(2.0, repeats = false)
            UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier("birdy_dev_trigger", content = unContent, trigger = trigger),
                withCompletionHandler = null,
            )
        }
    }
}

internal fun todayLocalDate(): kotlinx.datetime.LocalDate =
    kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
```

`MainViewController.kt`:

```kotlin
private val graph by lazy {
    buildIosAppGraph().also {
        AppGraphHolderIos.current = it
        installIosNotificationLifecycle(it)
    }
}
```

- [ ] **Step 4: Gater + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
./gradlew :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add composeApp/src/iosMain/kotlin/se/birdy/app/notifications/ composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt composeApp/src/iosMain/kotlin/se/birdy/app/MainViewController.kt
git commit -m "feat(ios): i4 T10 — IosNotificationScheduler (UNCalendarTrigger + foreground-omschemaläggning) + notis-delegat + devTriggers"
```

---

### Task 11: JournalPdfMetrics — geometri/palett/strängar/formatters till commonMain (TDD för formatters)

**Files:**
- Create: `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfMetrics.kt`
- Test: `shared/pdf/src/commonTest/kotlin/se/birdy/pdf/JournalPdfMetricsTest.kt`
- Modify: `shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfLayout.kt`

**Interfaces:**
- Consumes: kotlinx-datetime.
- Produces (Task 12:s iOS-layout läser ALLT härifrån — inga literaler dupliceras):

```kotlin
object JournalPdfMetrics {
    // Geometri (pt): PAGE_W=595, PAGE_H=842, MARGIN_X=50f, MARGIN_TOP=60f, MARGIN_BOTTOM=60f
    // Palett (ARGB Long): COLOR_PAPER_BG=0xFFEFE7D6, COLOR_PAPER_EDGE=0xFFE5DCC7, COLOR_INK=0xFF3F4F30,
    //                     COLOR_COPPER=0xFFA8552D, COLOR_NAVY=0xFF1F3A5F
    // Typstorlekar: alla textSize-literaler ur JournalPdfLayout, namngivna (TITLE_SIZE=52f, TITLE_SUB=22f,
    //   TITLE_YEAR=28f, TITLE_TEASER=18f, ORNAMENT=14f, ORNAMENT_TOP=16f, STAT_NUMBER=64f, STAT_CAPTION=16f,
    //   TOPS_HEADER=22f, BAR_LABEL=16f, BAR_VALUE=14f, SPECIES_NAME=14f, SPECIES_SCI=12f, SPECIES_COUNT=14f,
    //   SPECIES_DATE=11f, BADGE_NAME=18f, BADGE_DESC=14f, BADGE_DATE=12f, COLOPHON_MARK=26f, COLOPHON_GEN=14f,
    //   FOOTER=12f, SECTION_EYEBROW=18f, SECTION_TITLE=36f)
    // Strängar (svenska, avsiktligt olokaliserade — PDF:en är Field Journal-artefakt):
    //   TITLE="Fältdagbok", BY_FMT="av %s", TEASER_FMT="%s arter sedda • %s fynd", STATS_EYEBROW="Säsongens räkning",
    //   STATS_TITLE_FMT="%s i siffror", STAT_SPECIES="Arter i år", STAT_TOTAL="Totala fynd", TOPS="Topparter",
    //   SPECIES_EYEBROW="Arter i fält", SPECIES_EYEBROW_PAGED_FMT="Arter i fält (%s/%s)", SPECIES_TITLE="Det jag sett",
    //   COUNT_FMT="%s fynd", FIRST_FMT="Först: %s", BADGES_EYEBROW="Märken jag tjänat",
    //   BADGES_TITLE="Stämplar i marginalen", COLOPHON="Birdy Bird Scanner", GENERATED_FMT="Genererad %s",
    //   FOOTER_FMT="— %s —", ORNAMENT_GLYPH="❦"
    fun yearOf(epochMs: Long, zone: TimeZone): Int
    fun formatDate(epochMs: Long, zone: TimeZone): String      // "yyyy-MM-dd"
    fun formatDateTime(epochMs: Long, zone: TimeZone): String  // "yyyy-MM-dd HH:mm"
    fun fmt(pattern: String, vararg args: String): String      // ersätter %s i ordning
}
```

- [ ] **Step 1: Failande formatter-tester**

```kotlin
package se.birdy.pdf

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class JournalPdfMetricsTest {
    private val zone = TimeZone.of("Europe/Stockholm")

    // 2026-05-20T13:20:00Z = 15:20 svensk sommartid (CEST)
    private val epochMs = 1779283200000L

    @Test
    fun formatDate_pads_month_and_day() = assertEquals("2026-05-20", JournalPdfMetrics.formatDate(epochMs, zone))

    @Test
    fun formatDateTime_includes_hour_minute() = assertEquals("2026-05-20 15:20", JournalPdfMetrics.formatDateTime(epochMs, zone))

    @Test
    fun yearOf_resolves_in_zone() = assertEquals(2026, JournalPdfMetrics.yearOf(epochMs, zone))

    @Test
    fun fmt_replaces_placeholders_in_order() = assertEquals("3 arter sedda • 7 fynd", JournalPdfMetrics.fmt(JournalPdfMetrics.TEASER_FMT, "3", "7"))
}
```

- [ ] **Step 2: Kör — FAIL** (`./gradlew :shared:pdf:jvmTest --tests "*JournalPdfMetricsTest*"` — obs: modulen kan sakna jvm-target; använd då `:shared:pdf:testDebugUnitTest`)

- [ ] **Step 3: Implementera `JournalPdfMetrics`** — flytta formatter-kropparna VERBATIM från `JournalPdfLayout` (byt `TimeZone.currentSystemDefault()` mot `zone`-parametern) + skriv ut alla konstanter/strängar enligt Interfaces-blocket (värdena kopieras ur `JournalPdfLayout.kt`; ändra INGET värde).

- [ ] **Step 4: Kör — PASS** (+ `:shared:pdf:iosSimulatorArm64Test --tests "*JournalPdfMetricsTest*"`)

- [ ] **Step 5: Refaktorera `JournalPdfLayout` till metrics**

Ersätt lokala konstanter + literaler med `JournalPdfMetrics.*` (behåll `internal object JournalPdfLayout` och `Paint`-hjälparna; `formatDate(...)` → `JournalPdfMetrics.formatDate(epochMs, TimeZone.currentSystemDefault())` osv; strängbyggen via `JournalPdfMetrics.TEASER_FMT`-mönstren med `String.format`-fri interpolation: literalerna innehåller `%s` — använd en liten lokal `fun fmt(pattern: String, vararg args: String)` som ersätter `%s` i ordning, samma hjälpare återanvänds på iOS i T12; lägg `fmt` i `JournalPdfMetrics` som `fun fmt(pattern: String, vararg args: String): String`).

- [ ] **Step 6: Full gate + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt :shared:pdf:testDebugUnitTest
./gradlew :shared:pdf:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfMetrics.kt shared/pdf/src/commonTest/kotlin/se/birdy/pdf/JournalPdfMetricsTest.kt shared/pdf/src/androidMain/kotlin/se/birdy/pdf/JournalPdfLayout.kt
git commit -m "refactor(pdf): i4 T11 — geometri/palett/strängar/formatters extraherade till delad JournalPdfMetrics"
```

---

### Task 12: iOS PDF-actual — UIGraphicsPDFRenderer + typsnitt + wiring + CI

**Files:**
- Create: `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/IosPdfFonts.kt`
- Create: `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfLayoutIos.kt`
- Modify: `shared/pdf/src/iosMain/kotlin/se/birdy/pdf/JournalPdfRenderer.ios.kt`
- Test: `shared/pdf/src/iosTest/kotlin/se/birdy/pdf/JournalPdfRendererIosTest.kt`
- Modify: `iosApp/project.yml` (font-resurser + UIAppFonts), `iosApp/iosApp/Info.plist` (UIAppFonts)
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` (journalExport-wiring)
- Modify: `composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsLauncher.kt` (stale KDoc), `shared/pdf/src/commonMain/kotlin/se/birdy/pdf/JournalPdfRenderer.kt` (stale KDoc)
- Modify: `.github/workflows/ci.yml` (lägg `:shared:pdf:iosSimulatorArm64Test` i macOS-jobbets gradle-rad)

**Interfaces:**
- Consumes: `JournalPdfMetrics` inkl. `fmt` (T11), `JournalPageAggregator.computeSpeciesPages`, `JournalPdfInput`, `JournalPdfRenderResult`, `ExportJournalUseCase` (konstruktorn i `usecase/ExportJournalUseCase.kt`), `BadgeStringMap` — spegla `MainActivity.kt:375-394` för wiringen.
- Produces: riktig `actual class JournalPdfRenderer` på iOS (Success/Empty/Failed per kontraktet); export-CTA:n i Arkiv-fliken synlig på iOS (`graph.journalExport != null`).

- [ ] **Step 1: Typsnitten**

`iosApp/project.yml` — två nya resource-rader (samma mönster som birdnet-modellen):

```yaml
      - path: ../shared/pdf/src/androidMain/assets/fonts/DMSerifDisplay-Italic.ttf
        buildPhase: resources
      - path: ../shared/pdf/src/androidMain/assets/fonts/Caveat-Regular.ttf
        buildPhase: resources
```

`Info.plist` — registrera via UIAppFonts (standardmekanismen; ingen CTFontManager-kod behövs):

```xml
    <key>UIAppFonts</key>
    <array>
        <string>DMSerifDisplay-Italic.ttf</string>
        <string>Caveat-Regular.ttf</string>
    </array>
```

`IosPdfFonts.kt`:

```kotlin
package se.birdy.pdf

import platform.UIKit.UIFont

/**
 * PDF-typsnitten på iOS. Filerna bundlas via project.yml + laddas av UIAppFonts
 * (Info.plist). PostScript-namnen matchar TTF-metadatan. I test-binärer (egen process
 * utan app-Info.plist) är namnen oregistrerade → systemfallback, per spec §E:
 * typsnittsfel får aldrig faila en render.
 */
internal object IosPdfFonts {
    fun dmSerifItalic(size: Double): UIFont =
        UIFont.fontWithName("DMSerifDisplay-Italic", size) ?: UIFont.italicSystemFontOfSize(size)

    fun caveat(size: Double): UIFont = UIFont.fontWithName("Caveat-Regular", size) ?: UIFont.systemFontOfSize(size)
}
```

- [ ] **Step 2: iOS-layouten**

`JournalPdfLayoutIos.kt` — spegel av `JournalPdfLayout` (androidMain), sida för sida, ALLT ur `JournalPdfMetrics`. Kärnhjälpare + en sidfunktion i sin helhet; övriga fyra följer exakt samma mönster med samma metrics-koordinater som Android-filen:

```kotlin
package se.birdy.pdf

// imports: kotlinx.cinterop.*, platform.CoreGraphics.*, platform.Foundation.*, platform.UIKit.*

/**
 * iOS-tvillingen till JournalPdfLayout (androidMain). Samma fem sidor, samma
 * JournalPdfMetrics-koordinater. Skillnad mot Android: Canvas.drawText tar BASELINE-y,
 * NSString.drawAtPoint tar TOPP-y → drawText-hjälparen översätter (top = baseline - ascender)
 * så siffervärdena kan kopieras rakt av från Android-filen.
 */
@OptIn(ExperimentalForeignApi::class)
internal object JournalPdfLayoutIos {
    private val M = JournalPdfMetrics

    private fun uiColor(argb: Long): UIColor = /* samma hjälpare som IosSealMarker — kopiera hit (shared/pdf kan inte bero på composeApp) */

    private fun drawText(
        text: String,
        x: Double,
        baselineY: Double,
        font: UIFont,
        color: Long,
        centered: Boolean = false,
    ) {
        val ns = NSString.create(string = text)
        val attrs: Map<Any?, *> = mapOf(NSFontAttributeName to font, NSForegroundColorAttributeName to uiColor(color))
        val width = ns.sizeWithAttributes(attrs).useContents { width }
        val originX = if (centered) x - width / 2.0 else x
        ns.drawAtPoint(CGPointMake(originX, baselineY - font.ascender), withAttributes = attrs)
    }

    private fun fillRect(x: Double, y: Double, w: Double, h: Double, color: Long) {
        uiColor(color).setFill()
        UIBezierPath.bezierPathWithRect(CGRectMake(x, y, w, h)).fill()
    }

    private fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double, color: Long, width: Double) {
        uiColor(color).setStroke()
        UIBezierPath().apply {
            moveToPoint(CGPointMake(x1, y1))
            addLineToPoint(CGPointMake(x2, y2))
            lineWidth = width
        }.stroke()
    }

    private fun paintPaperBg() = fillRect(0.0, 0.0, M.PAGE_W.toDouble(), M.PAGE_H.toDouble(), M.COLOR_PAPER_BG)

    private fun drawOrnamentRule(y: Double) {
        val rulePadding = 30.0
        strokeLine(M.MARGIN_X + 40.0, y, M.PAGE_W / 2.0 - rulePadding, y, M.COLOR_INK, 0.6)
        strokeLine(M.PAGE_W / 2.0 + rulePadding, y, M.PAGE_W - M.MARGIN_X - 40.0, y, M.COLOR_INK, 0.6)
        drawText(M.ORNAMENT_GLYPH, M.PAGE_W / 2.0, y + 5.0, IosPdfFonts.caveat(M.ORNAMENT.toDouble()), M.COLOR_COPPER, centered = true)
    }

    private fun drawSectionHeader(eyebrow: String, title: String) {
        drawText(eyebrow, M.PAGE_W / 2.0, M.MARGIN_TOP + 50.0, IosPdfFonts.caveat(M.SECTION_EYEBROW.toDouble()), M.COLOR_COPPER, centered = true)
        drawText(title, M.PAGE_W / 2.0, M.MARGIN_TOP + 100.0, IosPdfFonts.dmSerifItalic(M.SECTION_TITLE.toDouble()), M.COLOR_INK, centered = true)
        drawOrnamentRule(M.MARGIN_TOP + 130.0)
    }

    private fun drawPageFooter(pageNum: Int) =
        drawText(M.fmt(M.FOOTER_FMT, pageNum.toString()), M.PAGE_W / 2.0, M.PAGE_H - M.MARGIN_BOTTOM / 2.0, IosPdfFonts.caveat(M.FOOTER.toDouble()), M.COLOR_INK, centered = true)

    fun drawTitlePage(input: JournalPdfInput, pageNum: Int, zone: kotlinx.datetime.TimeZone) {
        paintPaperBg()
        val year = M.yearOf(input.generatedAtMs, zone)
        drawText(M.ORNAMENT_GLYPH, M.PAGE_W / 2.0, M.MARGIN_TOP + 30.0, IosPdfFonts.caveat(M.ORNAMENT_TOP.toDouble()), M.COLOR_COPPER, centered = true)
        drawText(M.TITLE, M.PAGE_W / 2.0, M.MARGIN_TOP + 150.0, IosPdfFonts.dmSerifItalic(M.TITLE_SIZE.toDouble()), M.COLOR_INK, centered = true)
        drawText(M.fmt(M.BY_FMT, input.displayName), M.PAGE_W / 2.0, M.MARGIN_TOP + 188.0, IosPdfFonts.caveat(M.TITLE_SUB.toDouble()), M.COLOR_INK, centered = true)
        drawText("$year", M.PAGE_W / 2.0, M.MARGIN_TOP + 240.0, IosPdfFonts.dmSerifItalic(M.TITLE_YEAR.toDouble()), M.COLOR_COPPER, centered = true)
        drawOrnamentRule(M.MARGIN_TOP + 280.0)
        drawText(
            M.fmt(M.TEASER_FMT, input.stats.speciesSeenThisYear.toString(), input.stats.totalObservationsThisYear.toString()),
            M.PAGE_W / 2.0,
            M.MARGIN_TOP + 322.0,
            IosPdfFonts.caveat(M.TITLE_TEASER.toDouble()),
            M.COLOR_INK,
            centered = true,
        )
        drawPageFooter(pageNum)
    }

    // drawStatsPage / drawSpeciesPage / drawBadgesPage / drawColophonPage:
    // exakt samma koordinater, storlekar och beslutslogik som JournalPdfLayout (androidMain),
    // uttryckta med drawText/fillRect/strokeLine + UIBezierPath.bezierPathWithOvalInRect för
    // badge-stämpelcirkeln (stroke, lineWidth 1.5). Bar-charten: barBg = fillRect(PAPER_EDGE),
    // filled = fillRect(COPPER, bredd = barAreaW * count/maxCount). Kopiera siffrorna radvis
    // från Android-filen — inget värde får uppfinnas på nytt.
}
```

(Namnkonvention fastslagen i T11: strängen heter `TITLE`, storleken `TITLE_SIZE` — koden ovan förutsätter det.)

- [ ] **Step 3: Renderer-actualen**

`JournalPdfRenderer.ios.kt`:

```kotlin
package se.birdy.pdf

// imports: kotlinx.cinterop.*, kotlinx.coroutines.*, platform.CoreGraphics.CGRectMake, platform.Foundation.*, platform.UIKit.*

actual class JournalPdfRenderer actual constructor() {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun render(
        input: JournalPdfInput,
        outputPath: String,
    ): JournalPdfRenderResult =
        withContext(Dispatchers.Default) {
            if (input.observations.isEmpty()) return@withContext JournalPdfRenderResult.Empty
            runCatching {
                val zone = kotlinx.datetime.TimeZone.currentSystemDefault()
                val bounds = CGRectMake(0.0, 0.0, JournalPdfMetrics.PAGE_W.toDouble(), JournalPdfMetrics.PAGE_H.toDouble())
                val renderer = UIGraphicsPDFRenderer(bounds = bounds, format = UIGraphicsPDFRendererFormat.defaultFormat())
                var pageCount = 0
                val url = NSURL.fileURLWithPath(outputPath)
                // säkra parent-katalogen (kontraktet: caller ger app-privat path)
                NSFileManager.defaultManager.createDirectoryAtPath(
                    (outputPath.substringBeforeLast('/')),
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )
                val ok =
                    memScoped {
                        val err = alloc<ObjCObjectVar<NSError?>>()
                        val success =
                            renderer.writePDFToURL(
                                url,
                                withActions = { ctx ->
                                    var pageNum = 1
                                    fun page(draw: () -> Unit) {
                                        ctx!!.beginPage()
                                        draw()
                                        pageNum++
                                    }
                                    page { JournalPdfLayoutIos.drawTitlePage(input, pageNum, zone) }
                                    page { JournalPdfLayoutIos.drawStatsPage(input, pageNum, zone) }
                                    val speciesPages = JournalPageAggregator.computeSpeciesPages(input)
                                    speciesPages.forEachIndexed { idx, rows ->
                                        page { JournalPdfLayoutIos.drawSpeciesPage(input, rows, pageNum, idx, speciesPages.size, zone) }
                                    }
                                    if (input.unlockedPremiumBadges.isNotEmpty()) {
                                        page { JournalPdfLayoutIos.drawBadgesPage(input, pageNum, zone) }
                                    }
                                    page { JournalPdfLayoutIos.drawColophonPage(input, pageNum, zone) }
                                    pageCount = pageNum
                                },
                                error = err.ptr,
                            )
                        if (!success) throw IllegalStateException("writePDFToURL failed: ${err.value?.localizedDescription}")
                        success
                    }
                check(ok)
                val size = (NSFileManager.defaultManager.attributesOfItemAtPath(outputPath, error = null)?.get(NSFileSize) as? Long) ?: 0L
                JournalPdfRenderResult.Success(pageCount = pageCount, sizeBytes = size)
            }.getOrElse { t -> JournalPdfRenderResult.Failed("PDF render failed: ${t.message}", t) }
        }
}
```

(Sidnumrerings-detalj: spegla Android-räkningen exakt — `pageNum` ökas EFTER varje sida och `totalPages = pageNum` sätts efter colophon utan extra inkrement; justera `page`-hjälparen så beteendet blir identiskt med `JournalPdfRenderer.android.kt`:s.)

- [ ] **Step 4: iOS-testet (skriv FÖRE implementationen körs grön — TDD på kontraktsnivå)**

`shared/pdf/src/iosTest/kotlin/se/birdy/pdf/JournalPdfRendererIosTest.kt` — bygg `Observation`-fixturer med samma builder-mönster som `JournalPageAggregatorTest` (commonTest, samma modul):

```kotlin
package se.birdy.pdf

import kotlinx.coroutines.test.runTest
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JournalPdfRendererIosTest {
    @Test
    fun renders_real_pdf_with_expected_page_count() =
        runTest {
            val input = /* JournalPdfInput med 1 observation (1 art), inga badges — kopiera fixturbygget ur JournalPageAggregatorTest */
            val path = NSTemporaryDirectory() + "i4_test_${'$'}{kotlin.random.Random.nextInt(100000)}.pdf"
            val result = JournalPdfRenderer().render(input, path)
            // titel + stats + 1 artsida + colophon = 4 (inga badges)
            assertTrue(result is JournalPdfRenderResult.Success, "got: ${'$'}result")
            assertEquals(4, (result as JournalPdfRenderResult.Success).pageCount)
            assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path))
            assertTrue(result.sizeBytes > 0)
        }
}
```

Kör FÖRST med gamla stubben → förväntat FAIL (Failed, inte Success). Efter steg 2–3 → PASS:

```bash
./gradlew :shared:pdf:iosSimulatorArm64Test --tests "*JournalPdfRendererIosTest*"
```

(Om `iosTest`-source-setet inte finns i modulen: skapa katalogen — `birdy.kmp-android-lib`-pluginens default hierarchy ger `iosTest` automatiskt när `iosArm64()`/`iosSimulatorArm64()` är deklarerade, vilket de är.)

- [ ] **Step 5: Wiring i `buildIosAppGraph()`**

Spegla `MainActivity.kt:375-394` (samma use case, iOS-paths):

```kotlin
    val journalRenderer = JournalPdfRenderer()
    val exportJournalUseCase =
        ExportJournalUseCase(
            observationRepo = observationRepo,
            speciesRepo = SpeciesRepositoryProvider.get(),
            badgeRepo = badgeRepo,
            catalog = badgeCatalog,
            render = { input, path -> journalRenderer.render(input, path) },
            userPreferences = userPreferences,
            outputPathFactory = { ms -> journalExportDirPath() + "/birdy_field_journal_$ms.pdf" },
            clock = Clock.System,
            timeZone = kotlinx.datetime.TimeZone.currentSystemDefault(),
            locale = resolvedLocale,
            badgeNameResolver = { id -> resolveBadgeString(id) { BadgeStringMap.nameFor(id) } },
            badgeDescriptionResolver = { id -> resolveBadgeString(id) { BadgeStringMap.descriptionFor(id) } },
        )
```

plus i `AppGraph(...)`: `journalExport = { exportJournalUseCase.run() },` och hjälparna i filen (spegel av `audioStorageDirPath` + MainActivitys `resolveBadgeString`):

```kotlin
internal fun journalExportDirPath(): String {
    val caches =
        NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    val dir = "$caches/journal_exports"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir
}

private suspend fun resolveBadgeString(
    badgeId: String,
    resourceFor: () -> org.jetbrains.compose.resources.StringResource,
): String =
    runCatching { org.jetbrains.compose.resources.getString(resourceFor()) }
        .getOrElse { badgeId.removePrefix("premium_").replace('_', ' ').replaceFirstChar { it.uppercase() } }
```

(Kolla MainActivitys `resolveBadgeString`-kropp (rad 505–512+) och spegla fallback-formen exakt.)

- [ ] **Step 6: Stale-doc-städ + CI**

1. `SettingsLauncher.kt` KDoc: "JVM/iOS actuals are no-ops" → "JVM actual is a no-op; iOS presents UIActivityViewController (sedan i2b)".
2. `JournalPdfRenderer.kt` KDoc: "JVM and iOS actuals return Failed — PDF export is Android-only in v1" → "JVM actual returns Failed; Android renders via PdfDocument, iOS via UIGraphicsPDFRenderer (i4)".
3. `ci.yml` macOS-raden: lägg till `:shared:pdf:iosSimulatorArm64Test` efter `:shared:ml:iosSimulatorArm64Test`.

- [ ] **Step 7: Full gate (nu inkl. :shared:pdf-iOS) + commit**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt :shared:pdf:testDebugUnitTest
./gradlew :shared:pdf:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
git add shared/pdf iosApp composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt composeApp/src/commonMain/kotlin/se/birdy/app/ui/settings/SettingsLauncher.kt .github/workflows/ci.yml
git commit -m "feat(ios): i4 T12 — PDF-export: UIGraphicsPDFRenderer-actual + typsnitt + journalExport-wiring + pdf-iOS-tester i CI"
```

---

### Task 13: NotifyOthersOnDeactivation (i3-rest-polish)

**Files:**
- Modify: `shared/ml/src/iosMain/kotlin/se/birdy/ml/IosAudioRecorder.kt` (teardown, ~rad 337)

**Interfaces:**
- Consumes: `AVAudioSession.setActive(active:withOptions:error:)`.
- Produces: duckad musik/podd (Spotify m.fl.) återupptas när Birdy släpper record-sessionen. Bevisas i device-verifyn (musik → inspelning → stopp → musiken återupptas).

- [ ] **Step 1: Ändringen**

I `teardown()`:

```kotlin
        runCatching {
            memScoped {
                val err = alloc<kotlinx.cinterop.ObjCObjectVar<NSError?>>()
                // NotifyOthersOnDeactivation: appar vi duckade/avbröt (musik, podd) får
                // resume-signalen när record-sessionen släpps — utan flaggan förblir de tysta.
                session.setActive(
                    false,
                    withOptions = platform.AVFAudio.AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation,
                    error = err.ptr,
                )
            }
        }
```

(Import-jämkning: konstanten ligger i `platform.AVFAudio` eller `platform.AVFoundation` beroende på cinterop-uppdelning — följ filens befintliga imports för `AVAudioSession`.)

- [ ] **Step 2: Gater + commit**

```bash
./gradlew :shared:ml:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt
git add shared/ml/src/iosMain/kotlin/se/birdy/ml/IosAudioRecorder.kt
git commit -m "fix(ios): i4 T13 — NotifyOthersOnDeactivation vid audio-session-släpp (duckad musik återupptas)"
```

---

### Task 14: versionName + slutgate + boot-verify + docs-synk

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt` (`versionName = "1.2.0-ios-i4"`)
- Modify: `docs/ios-release-checklist.md`, `CLAUDE.md`

**Interfaces:**
- Consumes: allt ovan.
- Produces: i4 markerad KODKLAR i repo-dokumentationen; sim-check-instruktioner för Albin.

- [ ] **Step 1: versionName** → `"1.2.0-ios-i4"`.

- [ ] **Step 2: Full slutgate, båda raderna**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt :shared:pdf:testDebugUnitTest
./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :shared:pdf:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64
```

- [ ] **Step 3: Boot-verify i simulatorn** (recept i auto-memory `reference_ios_simulator_build_and_verify`): appen bootar utan krasch efter alla wiring-ändringar; screenshot `docs/superpowers/screenshots/i4-03-boot.png`.

- [ ] **Step 4: Docs-synk**

1. `docs/ios-release-checklist.md`: i4-raden → kodklar; punkt 9 (MapTiler-nyckeln) uppdaterad med `Local.xcconfig`-vägen; lägg i4-sim-check-punkter under Albins sektion (karta: tiles+pin+`simctl location`; notiser: pre-prompt→systemdialog→devTrigger-notis→tap→deep link; PDF: export→share-sheet→5 sidor; Inställningar: notis-toggles + "notiser av"-hjälplinjen) + i4-device-punkter (riktig GPS-pin, kalender-avfyrad notis, PDF till Filer/AirDrop, musik-resume).
2. `CLAUDE.md`: Status-post för i4 + plan-of-plans-raden i4 → 🔄 kodklar; notera parity-bonusen (Dagens fågel wirad på iOS).
3. Commit + push:

```bash
git add composeApp/src/iosMain/kotlin/se/birdy/app/IosAppGraph.kt docs/ios-release-checklist.md CLAUDE.md docs/superpowers/screenshots/i4-03-boot.png
git commit -m "chore(ios): i4 T14 — versionName 1.2.0-ios-i4, slutgate grön, checklist/CLAUDE.md-synk"
git push
```

---

## Verifiering utanför planen (Albins grindar, ur spec §Grindar)

1. **Sim-check (~15 min):** enligt checklistans nya i4-punkter (Task 14 steg 4). Kräver MapTiler-nyckeln i `Local.xcconfig`.
2. **Device-verify:** i4-punkterna körs på den samlade iPhone-sessionen (~2026-08-26) tillsammans med i0–i3-grindarna.
