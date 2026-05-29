# Designspec: Kamera-zoom + crop/justera av uppladdade bilder

> Status: utkast för review · Datum: 2026-05-30 · Skopa: två oberoende features ovanpå v1.x (Android-only)

## Bakgrund & mål

Två oberoende tillägg till skannings-flödet i den befintliga appen:

1. **Zoom i live-kameran (Scan)** — användaren ska kunna zooma 1x → 10x för att fånga avlägsna fåglar.
2. **Crop + 90°-rotation av uppladdade bilder** — användaren ska kunna beskära och räta upp en bild *innan* den skickas till klassificeraren, på både galleri- och in-app-"ta foto"-vägen.

Features delar ingen kod och kan byggas, testas och release:as oberoende av varandra. Båda är Android-only (composeApp/shared/ml androidMain + commonMain), iOS-skelettet rörs inte.

### Designbeslut (låsta i brainstorming 2026-05-30)

- Zoom-kontroll = **preset-chips** (1·2·5·10x), ingen pinch, ingen slider.
- Justera-omfång = **crop + 90°-rotation** (ingen fri-vinkel-straighten, inga filter/ljusstyrka).
- Crop gäller **båda** upload-vägarna (galleri + in-app "ta foto").
- Crop-yta = **egenbyggd Compose** (inga tredjepartsberoenden).
- Crop placeras som **lokalt skärm-state i `PhotoAnalyzeHost`**, inte en nav-route.
- **Fri aspect-ratio** (modellen sträcker ändå till 224×224).
- Zoom **nollställs till 1x** varje gång kameran startar (ingen persistens).

---

## Feature 1 — Kamera-zoom (preset-chips)

### Nuläge

`shared/ml/src/androidMain/.../camera/AndroidCameraSource.kt` binder kameran men **slänger** `Camera`-objektet som `provider.bindToLifecycle(...)` returnerar (rad 80). Ingen zoom-kod finns idag. `CameraSource`-interfacet (`shared/ml/src/commonMain/.../CameraSource.kt`) har bara `frames()`, `start()`, `stop()`.

Live-preview ritas i `composeApp/.../ui/scan/ScanScreen.kt` (commonMain) via `CameraPreviewHost`. Kamerakällan ägs/exponeras via `ScanViewModel`. En `FakeCameraSource` finns i `composeApp/src/commonTest/.../testing/FakeCameraSource.kt` och måste fortsätta kompilera utan ändring.

### Interface-tillägg (`CameraSource`, commonMain)

```kotlin
data class ZoomState(
    val ratio: Float,     // nuvarande zoom
    val minRatio: Float,  // = 1f på alla kameror vi stödjer
    val maxRatio: Float,  // enhetens faktiska max (kan vara < eller > 10)
) {
    companion object { val NONE = ZoomState(1f, 1f, 1f) }
}

interface CameraSource {
    fun frames(): Flow<ImageInput>
    suspend fun start()
    suspend fun stop()

    // Nya — default-impl gör att FakeCameraSource + tester kompilerar oförändrat:
    val zoom: StateFlow<ZoomState> get() = MutableStateFlow(ZoomState.NONE)
    fun setZoomRatio(ratio: Float) {}
}
```

> Default-getter + no-op-metod = fakes och test-impl behöver inte röras. Endast `AndroidCameraSource` overridar.

### AndroidCameraSource

- Fånga `Camera`-objektet från `bindToLifecycle(...)`.
- Vid bind: läs `camera.cameraInfo.zoomState.value` → publicera `ZoomState(ratio=1f, minRatio=minZoomRatio, maxRatio=maxZoomRatio)` i en `MutableStateFlow`. Tvinga `setZoomRatio(1f)` vid start så zoom alltid börjar på 1x.
- `setZoomRatio(ratio)` → `camera.cameraControl.setZoomRatio(ratio.coerceIn(min, max))` och uppdatera flow:t (observera även `camera.cameraInfo.zoomState` som `LiveData` → flow för att hålla `ratio` synkad om CameraX justerar).
- Vid `stop()`/rebind nollställs `Camera`-referensen; nästa `start()` återställer till 1x.

### Pure helper — `zoomPresets(maxRatio): List<Float>`

Returnerar de presets ur `[1f, 2f, 5f, 10f]` som är ≤ `maxRatio`. 1x alltid med. Om `maxRatio` ligger mellan två presets klampas översta synliga chipet till `min(10f, maxRatio)`. Ligger i commonMain, ren funktion → enhetstestbar utan Android.

### UI — `ZoomChips`-komponent (commonMain, `ScanScreen`)

- Rad med chips i nedre delen av preview-ytan (ovanför ev. befintliga kontroller), placerad så den inte skymmer crosshair/match-overlay.
- Stil: Field Journal koppar-pill för aktivt chip (`AccentCopper`), inaktiva = lågmält papper/ink. Format `"1×"`, `"2×"`, `"5×"`, `"10×"` (×-glyph, inte bokstaven x).
- Aktivt chip = det vars värde matchar `zoom.ratio` närmast (tolerans, eftersom CameraX kan landa på t.ex. 1.97x). Tap → `viewModel`/`cameraSource.setZoomRatio(preset)`.
- Chips byggs från `zoomPresets(zoom.maxRatio)`; om `maxRatio <= 1f` (t.ex. emulator/fake) renderas inga chips.

### Bieffekt (önskvärd, inget extra arbete)

`ImageAnalysis`-framesen kommer från samma fysiska kamera → zoom påverkar även det klassificeraren ser. Avlägsna fåglar blir större i modellens input, vilket bör höja träffsäkerheten vid zoom. Ingen kod krävs för detta; noteras som förväntat beteende vid device-verify.

### Utanför scope (Feature 1)

Pinch-to-zoom, slider, zoom-persistens mellan sessioner, zoom på system-kamerans `TakePicture`-intent (utanför appens kontroll), exponering/fokus-tap.

---

## Feature 2 — Crop + 90°-rotation av uppladdade bilder

### Nuläge

`composeApp/src/androidMain/.../ui/photoanalyze/PhotoAnalyzeHost.android.kt` hanterar två launchers:
- `PickVisualMedia` (galleri) → sätter `pendingDecodeUri`.
- `TakePicture` (system-kamera via FileProvider) → sätter `pendingDecodeUri` vid success.

En `LaunchedEffect(pendingDecodeUri)` kör `decodeAndScale()` på `Dispatchers.IO` (decode → EXIF-rotera → skala långsida till 1024 → JPEG 90) och anropar sedan `viewModel.analyze(input)`. `PhotoAnalyzeViewModel` avvisar bilder med kortsida < 224 (`TooSmall`).

### Nytt flöde

```
välj bild (galleri ELLER ta foto)  →  pendingDecodeUri
   → decode till arbets-bitmap (EXIF-roterad, cap långsida ~2048 px)
   → CropAdjustScreen  (användaren beskär + roterar 90°-steg)
   → applicera rotation → applicera crop-rect → skala långsida 1024 → JPEG 90
   → viewModel.analyze(input)
```

Crop-steget skjuts in *mellan* decode och `analyze`. Crop sker **före** 1024-nedskalningen så beskärningen behåller upplösning. Ett "cap till ~2048 px"-steg vid initial decode skyddar mot OOM på riktigt stora bilder utan att kosta märkbar kvalitet för crop.

### Placering: lokalt state, inte nav-route

Crop renderas som ett villkorat skärm-state inuti `PhotoAnalyzeHost` (Android-only, äger redan decode-pipelinen). **Inte** en `AppRoute` — bitmaps och stora byte-arrayer serialiseras inte rent genom Navigation-args. Pipelinen byggs om så `pendingDecodeUri` → decode till `pendingCropBitmap` (state) → `CropAdjustScreen` visas → vid bekräftelse produceras `ImageInput` → `analyze`. Avbryt/back i crop återgår till picker-skärmen (`PhotoAnalyzeScreen`) utan att analysera.

### `CropAdjustScreen` (composeApp androidMain)

Ritlager ovanpå en Android-`Bitmap`. Innehåll:
- Bilden "fit" i tillgänglig yta (papper-bg runt om).
- Mörkad overlay utanför crop-rektangeln + rule-of-thirds-linjer inuti.
- 4 drag-bara hörnhandtag (koppar) + dra hela rektangeln för att flytta.
- **Rotera 90°-knapp** (roterar arbetsbilden medurs, crop-rect återställs till full bild efter rotation).
- **"Analysera"** (bekräfta) + **avbryt/tillbaka**.
- Default crop-rect = hela bilden → den som bara vill rotera (eller inget) bekräftar direkt.
- Fri aspect-ratio (ingen tvångs-kvot).

### `CropGeometry` (ren Kotlin, commonMain)

All geometri-/gest-logik isoleras från Compose-ritningen så den blir enhetstestbar på JVM (inga Android-typer — bara int/float-rektanglar och rotation):
- Håller crop-rect i **käll-bild-koordinater** (inte skärm-px) så mappning view↔källa är explicit.
- Handtags-drag med bounds-clamping (rect aldrig utanför bilden, hörn korsar inte varandra).
- Min-storleks-spärr: crop-rect klampas så resultatet aldrig blir < 224 px kortsida i källbilden → undviker att trigga `PhotoAnalyzeViewModel`s `TooSmall`. Befintlig guard kvar som backstop.
- Rotations-transform: 90°-steg roterar både bild-dimensioner och återställer/mappar crop-rect.
- Funktion som givet (källbild-storlek, crop-rect, rotation) producerar den slutliga beskurna+roterade bitmappen (Android-`Bitmap`-delen kan ligga i ett tunt androidMain-skikt; den rena matematiken i `CropGeometry`).

### Återanvändning av befintlig kod

`decodeAndScale()` återanvänds delvis: EXIF-rotation + den slutliga `scaleToLongSide(1024)` + JPEG-encode behålls, men decode delas upp så vi får en mellanliggande bitmap för crop. `scaleToLongSide()` är redan en ren funktion och återanvänds oförändrad.

### Utanför scope (Feature 2)

Fri-vinkel-straighten, ljusstyrka/kontrast/filter, aspect-lås-presets, crop på live-Scan-flödet (realtids-klassificering, ingen enskild still att beskära).

---

## Moduler & filer som rörs

| Modul | Ändring |
|---|---|
| `shared/ml` commonMain | `CameraSource` får `zoom`/`setZoomRatio` + default-impl; ny `ZoomState` |
| `shared/ml` androidMain | `AndroidCameraSource` fångar `Camera`, wire:ar zoom-control + zoomState-flow |
| `composeApp` commonMain | `zoomPresets()` pure helper; `ZoomChips`-komponent; `ScanViewModel`/`ScanScreen` zoom-passthrough |
| `composeApp` androidMain | `CropAdjustScreen` + `CropGeometry`; ombyggd `PhotoAnalyzeHost`-pipeline |

**Inga nya Gradle-beroenden.** CameraX `cameraControl`/`zoomState` ingår i redan deklarerad `androidx.camera:camera-core`.

---

## Testning

### Unit (JVM, ingen enhet)
- `zoomPresets(maxRatio)` — filtrering + klamp (maxRatio < 2, mellan presets, > 10).
- `CropGeometry` — handtags-clamp, min-storleks-spärr (resultat ≥ 224 kortsida), flytt-bounds, 90°-rotations-mappning av crop-rect, view↔källa-koordinatmappning.
- Befintliga `ScanViewModelTest` + `PhotoAnalyzeViewModel`-tester fortsatt gröna (interface-default → `FakeCameraSource` orörd).

### Device-verify (SM-S918B, projektstandard)
- Zoom: chips 1·2·5·10x ändrar FOV; aktivt chip highlightar; avlägsen fågel större i preview → noteras om klassificering förbättras.
- Crop: galleri + "ta foto" → crop-yta visas; beskär + rotera 90° → analyserad bild är korrekt beskuren och orienterad i MatchResult.
- **Obs känd fälla:** `installDebug` lägger ut till paketet `se.birdy.android.debug` (inte `se.birdy.android`).
- Skärmdumpar enligt milstolpe-runbook (zoom-chips, crop-yta galleri, crop-yta efter rotation).

### Statisk analys
`./gradlew ktlintCheck detekt` grönt. `./gradlew build` grönt.

---

## Edge-cases & felhantering

| Fall | Hantering |
|---|---|
| Decode misslyckas (korrupt/null-stream) | Befintlig `decodeFailed()`-väg (DecodeFailure), oförändrad |
| Jätte-bild → OOM-risk | Cap arbets-bitmap långsida ~2048 px vid initial decode |
| Crop mindre än 224 px kortsida | `CropGeometry` min-storleks-spärr; `TooSmall`-guard som backstop |
| `deviceMax < preset` | Chipet droppas/klampas via `zoomPresets()` |
| Zoom efter kamera-rebind (lifecycle) | Nollställs till 1x vid `start()` |
| Avbryt i crop | Återgår till picker utan att analysera; arbets-bitmap recycle:as |
| Bitmap-minne | Mellanliggande bitmaps recycle:as (befintligt mönster i `decodeAndScale`) |

---

## Versionsbump

Sätts i implementationsplanen utifrån aktuell `androidApp/build.gradle.kts` vid exekvering (versionCode + versionName enligt rådande schema). Båda features kan ingå i samma bump eller delas — avgörs i planeringen.

---

## Leveranskriterier

1. Live-kameran (Scan) visar zoom-chips; tap zoomar 1→10x (klampat till enhetens max); zoom börjar alltid på 1x.
2. Galleri-upload och in-app "ta foto" visar crop-yta före analys; crop + 90°-rotation appliceras korrekt på den analyserade bilden.
3. Inga nya beroenden; `./gradlew build`, `ktlintCheck`, `detekt` gröna.
4. Nya unit-tester (`zoomPresets`, `CropGeometry`) gröna; befintliga tester orörda och gröna.
5. Device-verify på SM-S918B + skärmdumpar enligt runbook.
