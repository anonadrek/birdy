# Birdy Bird Scanner — Plan 4a: ML & Camera UI Design Spec

**Datum:** 2026-05-05
**Status:** Utkast — väntar på användargranskning
**Spec-typ:** Plan 4a av 6 (split: 4a = UI + FakeClassifier + CameraX, 4b = riktig TFLite-modell senare)
**Föregående:** Plan 3 (Encyclopedia) klar — `v0.3.0-encyclopedia`. Plan 2b (content backfill) kvar vid 97/700 arter, kan köra parallellt med 4a (pure-data).
**Författare:** Albin Lindblom + Claude Code (brainstormingsession 2026-05-05)

---

## 1. Bakgrund och syfte

Plan 4 i v1-specen är "ML & Camera": realtidsskanning + foto-upload + klassifikations-resultat-flöde. Två orelaterade riskdimensioner ligger i samma plan: **UI/integration** (CameraX-pipeline, ViewModel-throttling, navigations-flöde, permission-UX) och **modell** (val av modell, label-mapping från modellindex till Q-IDs, eventuell finetuning).

För att låta UI-arbetet röra sig utan att blockeras på modellval splittas Plan 4 i två:

- **Plan 4a (denna spec):** Bygger hela UI-stacken — LiveScanScreen, PhotoAnalyzeScreen, ClassificationResultScreen, app-navigations-graf — mot en `BirdClassifier`-interface med en `FakeBirdClassifier`-implementation som production-of-record. Wirar CameraX för riktiga frames. Wirar systemets gallery-picker + kamera-launcher för foto-upload. Levererar ett körbart "demoläge" där modellen är fejk men allting annat (kamera, navigation, freeze-flow, top-3-rendering, sparse-handling) är riktigt.
- **Plan 4b (separat brainstorm + spec senare):** Plockar in en riktig modell (sannolikt iNaturalist-vision pre-trained → optionellt finetune på svenska arter) bakom samma `BirdClassifier`-interface. Hanterar label-mapping till Q-IDs. Inga ändringar i ViewModels, screens, eller nav-graf — bara `AppGraph`-bindningen byter implementation.

**Plan 4a är klar när:**

- App startar med Skanna-fliken som default.
- Skanna-tab visar LiveScanScreen med CameraX-feed på Galaxy S23 Ultra; top-chip uppdateras 3 fps med fake-predictions; tap freezar bilden.
- Frozen-state navigerar till ClassificationResultScreen med top-3-listan + frozen-frame som hero.
- PhotoAnalyzeScreen-flödet kan trigga från en knapp i Skanna-skärmen, hämta bild från galleri eller systemkamera, och ge samma ClassificationResult-skärm.
- Save-CTA på ClassificationResultScreen finns men är disabled med hint-text "kommer i Plan 5".
- Permissions hanteras just-in-time (vid Skanna-tab-klick) med deny-fallback.
- Alla strängar i `strings.xml` (sv) + `values-en/strings.xml` (en).
- ViewModel-tester gröna; FakeBirdClassifier-tester gröna.
- Manuell device-verifiering på SM-S918B med skärmdumpar.
- Tag `v0.4.0a-camera-ui` pushad.

Plan 4b kan starta direkt därefter (eller parallellt med Plan 5 om modellen behöver träningstid).

---

## 2. Låsta beslut från brainstormingen

| # | Beslut | Motivering |
|---|---|---|
| 1 | **Plan 4 splittad:** 4a = UI + FakeClassifier + CameraX nu; 4b = riktig modell senare | Decoupla UI-risk från modell-risk; låter 4a + 2b köras i parallell; modell-träning kan ta dagar utan att blockera demoläge |
| 2 | **`BirdClassifier`-interface i `shared/ml`-modul** med `suspend classify(Frame): Classification` | Plan 4b plugar in samma shape, ViewModels behöver inte ändras; ny modul håller ML-bekymmer borta från `composeApp` |
| 3 | **`FakeBirdClassifier` är production-of-record i 4a** (ligger i `commonMain`, inte `commonTest`) | Genomförande av spec-checklistans "klar"-villkor utan riktig modell; testas av sina egna unit-tests; återanvänds som test-fixture för ViewModel-tester |
| 4 | **FakeClassifier-beteende:** cyklar genom 6 entries (5 walking-skeleton-arter + 1 "söker"-entry under threshold) | Demoläge ska visa både "vi har en gissning"- och "söker..."-state utan att kräva riktiga bilder; deterministisk för tester |
| 5 | **LiveScanScreen UX = Variant C:** top-chip uppe-höger med pulserande dot + crosshair i mitten + "tryck var som helst för att frysa" hint | Mest kamera-immersiv (matchar v1-vision av Merlin-style scan); top-3 reserveras för ResultScreen efter freeze |
| 6 | **ClassificationResultScreen UX = Variant A:** hero top-1 + frozen-frame-banner + top-2/3-row | Top-1 dominerar (det är vanligaste use-caset); top-2/3 finns men sekundärt; frozen-frame-banner låter användaren känna igen sin egen scan |
| 7 | **Save-CTA i 4a = disabled** med hint "kommer i Plan 5" | UI-arkitektur klar för Plan 5; ingen halv-implementation av observation-skapande som måste ändras igen |
| 8 | **Photo-upload-källor = galleri + systemkamera** (ej egen kamera-skärm) | ActivityResultContracts.PickVisualMedia + TakePicture är väl-debugged; egen kamera vore duplikering av LiveScanScreens infrastruktur utan unik värde |
| 9 | **Permissions = just-in-time** (vid Skanna-tab-klick, inte vid app-start) | Mindre intrusiv; matchar Android best-practice; deny-fallback-skärm med "byt i inställningar"-CTA |
| 10 | **Throttling-pipeline:** `Channel<Frame>` capacity=Conflated + `collectLatest` + `flow.sample(period)` med variabel period; auto-throttle 3→1.5 fps om p95-latens > 333ms över rolling 10-frame window | Spec-låst i v1 §3.2; conflated-channel kastar gamla frames utan att blockera kamerans producer |
| 11 | **Frozen-frame passas via cacheDir + nav-arg path-string** (inte ByteArray nav-arg) | Compose Navigation 2.x har storleksbegränsning på nav-args; skriva till cacheDir + path-string är robust |
| 12 | **`unresolved: List<String>` i UiState** för Q-IDs som inte finns i DB | Säkerhetsnät för 4b — om modellen ger en surprise-Q-ID som inte finns lokalt så degraderar vi grundigt istället för att krascha |

---

## 3. Arkitektur och moduler

Plan 4a lägger till **en ny gradle-modul** (`shared/ml`) och **utökar `composeApp`** med tre nya screens + nav-routes + AppGraph-utökning. CameraX-bindning ligger som actual-implementation under `shared/ml/src/androidMain/`.

```
shared/ml/                                          ← NY MODUL (KMP, expect/actual)
├── src/commonMain/kotlin/se/birdy/ml/
│   ├── BirdClassifier.kt                           ← interface: suspend classify(Frame) -> Classification
│   ├── Frame.kt                                    ← (bytes, w, h, format, timestampMillis)
│   ├── FrameFormat.kt                              ← enum: YUV_420_888, JPEG, RGBA_8888
│   ├── Prediction.kt                               ← (speciesId: String, confidence: Float)
│   ├── Classification.kt                           ← (predictions: List<Prediction>, frameTimestampMillis: Long)
│   ├── FakeBirdClassifier.kt                       ← production-of-record i 4a; cyclic 6-entry
│   └── CameraSource.kt                             ← interface: frames(): Flow<Frame>; suspend start(); suspend stop()
├── src/androidMain/kotlin/se/birdy/ml/
│   └── AndroidCameraSource.kt                      ← actual: CameraX ImageAnalysis 3 fps
├── src/iosMain/kotlin/se/birdy/ml/
│   └── IosCameraSource.kt                          ← stub som kastar NotImplementedError (Plan 6+)
└── src/commonTest/kotlin/se/birdy/ml/
    └── FakeBirdClassifierTest.kt                   ← unit-tests för cyklisk determinism

composeApp/
├── src/commonMain/kotlin/se/birdy/app/
│   ├── App.kt                                      ← oförändrat
│   ├── di/
│   │   └── AppGraph.kt                             ← UTÖKAS: + classifier + cameraSourceFactory
│   ├── ui/
│   │   ├── scaffold/
│   │   │   ├── AppRoute.kt                         ← UTÖKAS: + Scan, PhotoAnalyze, ClassificationResult
│   │   │   ├── AppScaffold.kt                      ← UTÖKAS: nya routes + default-flik = Skanna
│   │   │   └── BottomNavBar.kt                     ← oförändrat (Skanna-fliken finns redan)
│   │   ├── scan/
│   │   │   ├── ScanScreen.kt                       ← NY: ersätter ScanStubScreen (CameraX live + permission-flow)
│   │   │   ├── ScanViewModel.kt                    ← NY: Channel<Frame> + classifier + throttle
│   │   │   ├── ScanUiState.kt                      ← NY: sealed; PermissionRequired, Idle, Scanning, FrozenAt, Error
│   │   │   ├── TopChip.kt                          ← NY: pill-overlay top-1 + pulse-dot
│   │   │   └── Crosshair.kt                        ← NY: 88dp center crosshair
│   │   ├── photoanalyze/
│   │   │   ├── PhotoAnalyzeScreen.kt               ← NY: source-picker (galleri/kamera) + analyze-state
│   │   │   ├── PhotoAnalyzeViewModel.kt            ← NY: ByteArray → classifier
│   │   │   └── PhotoAnalyzeUiState.kt              ← NY: sealed; Idle, Analyzing, Loaded, Error
│   │   └── result/
│   │       ├── ClassificationResultScreen.kt       ← NY: hero top-1 + frozen-frame + top-2/3-row + Save-disabled
│   │       ├── ClassificationResultViewModel.kt    ← NY: resolverar predictionsCsv mot SpeciesRepository
│   │       └── ClassificationResultUiState.kt      ← NY: sealed; Loading, Loaded(top1, runnerUps, frozenFramePath?, unresolved), Error
│   └── permissions/
│       └── (commonMain endast om vi behöver KMP-perms-helper; annars androidMain)
├── src/androidMain/kotlin/se/birdy/app/
│   └── permissions/
│       └── CameraPermission.kt                     ← NY: just-in-time-helper med ActivityResultContracts
└── src/commonTest/kotlin/se/birdy/app/
    ├── scan/ScanViewModelTest.kt                   ← NY
    ├── photoanalyze/PhotoAnalyzeViewModelTest.kt   ← NY
    └── result/ClassificationResultViewModelTest.kt ← NY (återanvänder FakeSpeciesRepository från Plan 3)
```

**Modulgränser:**

- `shared/ml` exponerar bara interfaces + `FakeBirdClassifier` + `Frame`-typer. Plan 4b lägger till `TfLiteBirdClassifier` här utan att röra `composeApp`.
- `composeApp` använder `api(project(":shared:ml"))` (samma `api`-pattern som lärdes i Plan 3 — `MainActivity` konstruerar `AppGraph(repository, classifier, cameraSourceFactory)` med typer från `shared/ml`).
- iOS-actual är stubb i 4a; CameraX är Android-only i v1.

---

## 4. Komponenter

### 4.1 `BirdClassifier`-interface och datatyper (`shared/ml/commonMain`)

```kotlin
data class Frame(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val format: FrameFormat,
    val timestampMillis: Long,
)

enum class FrameFormat { YUV_420_888, JPEG, RGBA_8888 }

data class Prediction(val speciesId: String, val confidence: Float)

data class Classification(
    val predictions: List<Prediction>,   // sorted desc by confidence
    val frameTimestampMillis: Long,
)

interface BirdClassifier {
    suspend fun classify(frame: Frame): Classification
}

interface CameraSource {
    fun frames(): Flow<Frame>
    suspend fun start()
    suspend fun stop()
}
```

`Prediction.speciesId` är en sträng — i 4a alltid en Q-ID (`"Q25485"`); 4b kan ge tillbaka samma format efter label-mapping. `Classification.predictions` får ha 0..N entries; ScanViewModel + ResultViewModel hanterar tom lista.

### 4.2 `FakeBirdClassifier`

Cyklar deterministiskt genom 6 fördefinierade entries (5 walking-skeleton-arter + 1 "söker"-entry):

| # | top-1 | top-2 | top-3 | Top-1 confidence |
|---|---|---|---|---|
| 0 | Q25485 (Talgoxe) | Q25404 (Blåmes) | Q25234 (Koltrast) | 0.87 |
| 1 | Q25234 (Koltrast) | Q25402 (Knölsvan) | Q26490 (Tornfalk) | 0.74 |
| 2 | Q25404 (Blåmes) | Q25485 (Talgoxe) | Q25234 (Koltrast) | 0.91 |
| 3 | Q25402 (Knölsvan) | Q26490 (Tornfalk) | Q25234 (Koltrast) | 0.82 |
| 4 | Q26490 (Tornfalk) | Q25485 (Talgoxe) | Q25404 (Blåmes) | 0.68 |
| 5 | "söker"-entry — top-1 confidence 0.22 (under threshold 0.35) | — | — | 0.22 |

Counter ökar för varje `classify`-call; modulo 6. Konstruktor-injicerbar `cycle: List<List<Prediction>>` + `clock: () -> Long` så tester kan injicera deterministisk klocka. Default-cykeln + default-klocka (`{ 0L }`) räcker för demoläge.

### 4.3 `AppGraph` (`composeApp/.../di/AppGraph.kt`)

```kotlin
class AppGraph(
    val repository: SpeciesRepository,
    val classifier: BirdClassifier,
    val cameraSourceFactory: () -> CameraSource,
    val defaultLocale: Locale = Locale.SV,
) {
    fun encyclopediaViewModel(): EncyclopediaViewModel = ...
    fun speciesProfileViewModel(speciesId: String): SpeciesProfileViewModel = ...
    fun scanViewModel(): ScanViewModel = ScanViewModel(classifier, cameraSourceFactory)
    fun photoAnalyzeViewModel(): PhotoAnalyzeViewModel = PhotoAnalyzeViewModel(classifier)
    fun classificationResultViewModel(predictionsCsv: String, frameJpegPath: String?): ClassificationResultViewModel =
        ClassificationResultViewModel(repository, predictionsCsv, frameJpegPath, defaultLocale)
}
```

`MainActivity` (Android) konstruerar grafen:

```kotlin
val graph = AppGraph(
    repository = SqlDelightSpeciesRepository(driver),
    classifier = FakeBirdClassifier(),                         // 4b byter detta
    cameraSourceFactory = { AndroidCameraSource(this) },
    defaultLocale = Locale.SV,
)
```

iOS-stubb i `iosApp` använder samma `FakeBirdClassifier` + en `IosCameraSource` som kastar i `start()` (inget iOS-stöd i v1).

### 4.4 Nav-routes

Utöka `AppRoute`:

```kotlin
@Serializable data object Scan : AppRoute
@Serializable data object PhotoAnalyze : AppRoute
@Serializable data class ClassificationResult(
    val predictionsCsv: String,                   // "Q25485:0.87,Q25234:0.08,Q25404:0.05"
    val frameJpegPath: String?,                   // alltid satt i happy-path (live-scan: cacheDir/scan-frames/; photo: cacheDir/photo-input/)
) : AppRoute
```

CSV-formatet är simpelt + diff-vänligt; max-storlek (5 predictions × ~12 chars = 60 chars) är långt under nav-args storleksbegränsning. `frameJpegPath` är en `file://`-path under `cacheDir/`-subkatalog beroende på källa (`scan-frames/` för live-scan, `photo-input/` för foto-upload). Typen är nullable bara som säkerhetsnät om cacheDir-write skulle misslyckas — i normalfallet alltid satt så ResultScreen kan rendera frozen-frame-bannern oavsett källa.

`AppScaffold` byter default-flik till `Scan` (var Encyclopedia i Plan 3). Encyclopedia-fliken är fortfarande nåbar via bottom-nav.

### 4.5 `ScanScreen` + `ScanViewModel`

**ScanUiState (sealed):**

- `PermissionRequired` — visa permission-rationale + "Tillåt kamera"-CTA
- `PermissionDenied` — visa "byt i inställningar"-fallback-skärm
- `Idle` — kamera initialiseras
- `Scanning(top1: Prediction?, isThrottled: Boolean)` — kamera live; top-chip rendrerar top1 om ≥0.35 eller "söker..." annars; isThrottled visar liten "1.5 fps"-indikator efter auto-throttle
- `FrozenAt(predictions: List<Prediction>, frameJpegPath: String, timestampMillis: Long)` — efter tap; ScanScreen navigerar omedelbart till ClassificationResult och resettar tillbaka till Scanning
- `Error(message: String)` — fatal-fel (errorCount > 5 från classifier eller camera-init-fail)

**Pipeline (i `ScanViewModel.init`):**

```kotlin
viewModelScope.launch {
    val source = cameraSourceFactory()
    val frameChannel = Channel<Frame>(capacity = Channel.CONFLATED)

    launch {
        source.frames().collect { frame -> frameChannel.trySend(frame) }
    }

    var samplePeriodMs = 333L            // 3 fps
    val latencies = ArrayDeque<Long>(10) // rolling window

    frameChannel.consumeAsFlow()
        .sample(samplePeriodMs)
        .collectLatest { frame ->
            val started = clock.now()
            val result = runCatching { classifier.classify(frame) }
                .onFailure { /* incrementErrorCount; fatal if > 5 */ }
                .getOrNull() ?: return@collectLatest
            val latency = clock.now() - started

            latencies.addLast(latency)
            if (latencies.size > 10) latencies.removeFirst()
            val p95 = latencies.sorted()[(latencies.size * 0.95).toInt()]
            samplePeriodMs = if (p95 > 333) 666 else 333    // throttle 3 -> 1.5 fps

            val top1 = result.predictions.firstOrNull()?.takeIf { it.confidence >= 0.35f }
            _state.value = Scanning(top1, isThrottled = samplePeriodMs == 666L)
        }

    source.start()
}
```

**Tap-to-freeze:** UI kallar `viewModel.freeze(currentFrameJpeg)`. ViewModel skriver `currentFrameJpeg` till `cacheDir/scan-frames/${UUID}.jpg`, tar senaste classification, emitterar `FrozenAt(predictions, path, timestamp)`. UI observerar och kallar `nav.navigate(ClassificationResult(predictionsCsv, path))`.

### 4.6 `PhotoAnalyzeScreen` + `PhotoAnalyzeViewModel`

**Flöde:**

1. Skanna-skärmen har en sekundär-knapp "Analysera ett foto" (overlay nedtill, mindre än freeze-tap-zonen).
2. Klick → `nav.navigate(PhotoAnalyze)`.
3. PhotoAnalyzeScreen visar två stora knappar: "Välj från galleri" + "Ta foto".
4. Klick på en av dem launchar `ActivityResultContracts.PickVisualMedia` eller `ActivityResultContracts.TakePicture`.
5. På callback: `viewModel.analyze(uri, contentResolver)`.
6. ViewModel: läser bytes via `contentResolver.openInputStream(uri)`, decodar med `BitmapFactory`, downscalar till 1024px-långsida, EXIF-rotation respekteras, encoding tillbaka till JPEG ByteArray, packar `Frame(bytes, w, h, JPEG, now)`, kallar `classifier.classify(frame)`.
7. ViewModel skriver downscaled JPEG till `cacheDir/photo-input/${UUID}.jpg` och får tillbaka pathen.
8. På success: nav.navigate(`ClassificationResult(predictionsCsv, frameJpegPath = downscaledPath)`). ResultScreen visar samma frozen-frame-banner som live-scan-flödet.
9. På fail: `Error(PhotoTooSmall|DecodeFailure|...)`.

### 4.7 `ClassificationResultScreen` + `ViewModel`

**ClassificationResultUiState (sealed):**

- `Loading`
- `Loaded(top1: ResolvedPrediction, runnerUps: List<ResolvedPrediction>, frozenFramePath: String?, unresolved: List<String>)`
- `Error(message)`

`ResolvedPrediction(species: Species, confidence: Float)`. `unresolved` innehåller Q-IDs som CSV:n hade men som inte fanns i DB — i 4a är detta alltid tom (FakeClassifier ger bara walking-skeleton-IDs); 4b kan ge tillbaka surprise-IDs och då renderar UI:n en liten "1 förslag kunde inte resolveras"-pill nederst.

**Layout (variant A):**

- Hero-kort högst upp: top-1 species-foto (från `SpeciesRepository.getById(...).heroImage`) + namn + scientific name + confidence-bar (87%) + Mossbädd-koppar-accent-pill
- Frozen-frame banner under hero: liten 80dp-thumbnail av användarens scan/foto + caption "din skan"
- Top-2/3-row: två mindre kort sida-vid-sida med thumbnail + namn + confidence
- Save-CTA: full-width-knapp "Spara som observation" — disabled, alpha 0.5, hint-text under "Sparas i Plan 5"
- Tap på top-1 / top-2 / top-3 navigerar till SpeciesProfileScreen (Plan 3) — befintlig flow

### 4.8 Permission-flow

`CameraPermission.kt` (androidMain):

```kotlin
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> ... }
    return remember { CameraPermissionState(launcher) }
}
```

ScanScreen:

1. På first-render: kolla nuvarande permission-status.
2. Om granted → state = `Idle`, init-camera.
3. Om not-granted + not-asked → state = `PermissionRequired` (med rationale-text + "Tillåt kamera"-CTA som kallar `launcher.launch(Manifest.permission.CAMERA)`).
4. Om not-granted + denied (efter prompt) → state = `PermissionDenied`, visa "byt i inställningar"-CTA som öppnar app-settings via `Intent`.
5. Om "don't ask again" — samma `PermissionDenied`-state.

iOS: ej implementerat i 4a (ingen Skanna-flik på iOS i v1).

---

## 5. Dataflöden och felhantering

### 5.1 Live-skan-flöde

```
CameraX ImageAnalysis (Android)
  ↓ (YUV_420_888 frames @ device fps, ~30 fps)
AndroidCameraSource.frames(): Flow<Frame>
  ↓
Channel<Frame> capacity=Conflated (kastar gamla frames tyst)
  ↓
flow.sample(samplePeriodMs)         (3 fps default → 1.5 fps om p95>333ms)
  ↓
collectLatest { classifier.classify(frame) }
  ↓
ScanUiState.Scanning(top1, isThrottled)  (StateFlow → Compose recomposition)
  ↓
TopChip renderar top1.species_sv + confidence%, eller "söker..." om null
```

**Tap-to-freeze:** användaren tappar någonstans på skärmen → ScanViewModel tar `currentJpegBytes` (CameraX `ImageAnalysis` ger oss YUV; vi konverterar senast-fångad till JPEG via `YuvToJpegConverter`) + senaste predictions, skriver JPEG till `cacheDir/scan-frames/{uuid}.jpg`, emitterar `FrozenAt`, nav.navigate(...).

### 5.2 Foto-upload-flöde

```
PhotoAnalyzeScreen "Galleri"-knapp
  ↓
ActivityResultContracts.PickVisualMedia.launch()
  ↓ (Uri?)
viewModel.analyze(uri, contentResolver)
  ↓
contentResolver.openInputStream(uri) -> ByteArray
  ↓
BitmapFactory.decodeByteArray + ExifInterface-rotation
  ↓
Bitmap.createScaledBitmap (long-side 1024px)
  ↓
Bitmap.compress(JPEG, 90) -> ByteArray
  ↓
Frame(bytes, w, h, JPEG, now)
  ↓
classifier.classify(frame)
  ↓
PhotoAnalyzeUiState.Loaded(predictions, frameJpegPath)
  ↓ (UI observes och kallar nav.navigate)
nav.navigate(ClassificationResult(predictionsCsv, frameJpegPath))
```

`ActivityResultContracts.TakePicture` är paralleller — skillnaden är vi pre-allokerar ett `cacheDir/photo-input/{uuid}.jpg` Uri och får tillbaka boolean.

### 5.3 Felhantering

| Scenario | Detection | Hantering |
|---|---|---|
| Permission denied vid Skanna-klick | rememberCameraPermissionState | UiState.PermissionDenied, "byt i inställningar"-knapp |
| Camera init fail (CameraX exception) | source.start() kastar | UiState.Error("Kamera kunde inte startas"), "Försök igen"-knapp |
| Classifier throws | runCatching i collectLatest | Counter+1; om >5 i rad → UiState.Error; annars logga + fortsätt med senaste OK-result |
| Photo decode fail | BitmapFactory returnerar null | PhotoAnalyzeUiState.Error("Kunde inte läsa bilden"), "Välj annan"-knapp |
| Photo too small (<224px short side) | mätt efter decode | PhotoAnalyzeUiState.Error("Bilden är för liten"), "Välj annan"-knapp |
| CacheDir write fail | runCatching kring File.outputStream() | Logga; freeze fortsätter ändå med null frameJpegPath; ResultScreen tar in null OK |
| Q-ID från Classification finns inte i DB | repository.getById returnerar null | Hopkorta predictions till de som resolverade; lägg till okänt Q-ID i `unresolved`; rendera "X kunde inte resolveras"-pill |
| Auto-throttle aktiverad | p95 > 333ms över 10-frame window | UiState.Scanning(isThrottled=true); litet "1.5 fps"-badge i top-bar; ingen popup |

### 5.4 Cache-cleanup

`scan-frames/`- och `photo-input/`-katalogerna kan växa. Två mekanismer:

1. **DisposableEffect onDispose** i ClassificationResultScreen: när användaren navigerar bort raderas just denna `frameJpegPath`.
2. **AppGraph-init safety-net pass:** `MainActivity.onCreate` raderar alla filer i `cacheDir/scan-frames/` + `cacheDir/photo-input/` äldre än 1h. Idempotent; körs alltid.

---

## 6. Test-strategi

### 6.1 Vad vi testar (commonTest, jvmTest)

| Lager | Test | Pattern |
|---|---|---|
| `FakeBirdClassifier` | cyklar 0..5..0; "söker"-entry har confidence < 0.35; deterministisk givet konstruktor-args | Pure unit-test |
| `ScanViewModel` | givet injicerad `Flow<Frame>` + `FakeBirdClassifier` → emitterar UiState i ordning Idle → Scanning(top1=Talgoxe@0.87) → Scanning(top1="söker") | Turbine + UnconfinedTestDispatcher; samma pattern som `EncyclopediaViewModelTest` |
| `ScanViewModel` throttle | injicera `clock` + tvinga p95-latens > 333ms över 10 frames → samplePeriodMs flippar 333 → 666 | Tids-baserad assertion, `advanceTimeBy(...)` |
| `ScanViewModel` freeze | tap → emitterar `FrozenAt(predictions, path, timestamp)` exakt en gång; nya frames efter freeze ignoreras tills återställning | Turbine `awaitItem()` + `expectNoEvents()` |
| `PhotoAnalyzeViewModel` | given `ByteArray` (test-fixture JPEG) + `FakeBirdClassifier` → Loading → Loaded(predictions); too-small → Error(PhotoTooSmall) | Pure ViewModel-test, ingen ActivityResultContracts |
| `ClassificationResultViewModel` | `predictionsCsv="Q25485:0.87,Q25234:0.08"` + `FakeSpeciesRepository` → Loaded med top1=Talgoxe + runnerUps; csv med okänt Q-ID → unresolved-entry | Återanvänder `FakeSpeciesRepository` från Plan 3 |

### 6.2 Vad vi inte testar i 4a

- **Compose UI-tester:** ingen `composeTest`-runner i 4a. Visuell verifiering = manuella device-screenshots (samma policy som Plan 3).
- **CameraX-integration:** `AndroidCameraSource` är actual-impl som device-verifieras manuellt. Ingen Robolectric/instrumentation-test (lägga till Robolectric är scope-creep).
- **TFLite-loading:** kommer i 4b. `BirdClassifier`-interfacet är språnget som låter 4b plugga in utan ViewModel-ändringar.
- **iOS-actual:** stubb som kastar; Plan 6+.

### 6.3 Manuell device-verifiering

På SM-S918B efter task 10 (polish + tag), screenshots committade i separat post-tag-commit:

1. Skanna-tab — permission-request synlig (just-in-time)
2. Permission denied — fallback-skärmen
3. LiveScan — top-chip "Talgoxe 87%" + crosshair + pulse-dot
4. LiveScan — "söker..." (cyclic-entry under threshold)
5. LiveScan — auto-throttle-badge "1.5 fps" (om hardware-trigger möjligt; annars dokumentera)
6. ClassificationResult — hero top-1 + frozen-frame-banner + top-2/3-row
7. Save-CTA disabled med "Plan 5"-hint synlig
8. Photo-upload — galleri-picker entry
9. Photo-upload — systemkamera entry
10. PhotoAnalyzeScreen → ClassificationResult-flöde

Committas i en separat commit (samma mönster som Plan 2a `b9b85bb` + Plan 3 `54a87e0`).

---

## 7. Scope-decomposition (preview)

Plan 4a delas upp i ~10 tasks i implementation-planen (varje task = TDD-cykel: test → implementation → grön → commit):

| # | Task | Skapar / rör |
|---|---|---|
| 1 | `shared/ml`-modul + `BirdClassifier`/`Frame`/`Prediction`/`Classification`/`CameraSource` interfaces + `FakeBirdClassifier` + `FakeBirdClassifierTest` | ny modul |
| 2 | Wire `BirdClassifier` + `cameraSourceFactory` i `AppGraph`; `expect class CameraSource` + Android stub-impl som kastar `NotImplementedError` (riktig CameraX i Task 7) | `AppGraph.kt`, `shared/ml/src/androidMain/` |
| 3 | Nav-routes: `Scan`, `PhotoAnalyze`, `ClassificationResult(predictionsCsv, frameJpegPath?)` + ersätt Skanna-stub med `ScanScreen` placeholder + byt default-flik till Skanna | `AppRoute.kt`, `MainNavGraph.kt` |
| 4 | Permissions-helper (just-in-time camera-permission på Skanna-tab); permission-required + permission-denied fallback-skärmar | `androidMain/permissions/` |
| 5 | `ScanViewModel` + `ScanUiState` + tester (utan camera, mockad `Flow<Frame>` via FakeCameraSource för tester) | `composeApp/.../scan/` |
| 6 | `ScanScreen` UI (variant C: top-chip + crosshair + tap-to-freeze + close-X + foto-upload-entry-knapp) | `composeApp/.../scan/` |
| 7 | `AndroidCameraSource` actual: CameraX `ImageAnalysis` 3 fps + YuvToJpeg + auto-throttle | `androidMain/.../camera/` |
| 8 | `PhotoAnalyzeViewModel` + Screen (galleri-picker + systemkamera-launch via ActivityResultContracts + downscale-pipeline) | `composeApp/.../photoanalyze/` |
| 9 | `ClassificationResultViewModel` + Screen (variant A: hero top-1 + frozen-frame-banner + top-2/3 + Save-disabled + tap → SpeciesProfile) | `composeApp/.../result/` |
| 10 | Polish: i18n-strängar (sv+en), CI-grön, post-tag screenshots-commit, `v0.4.0a-camera-ui`-tag | `strings.xml`, GH Actions |

**Användarcheckpoint mellan tasks:** vanligt subagent-driven-flöde. Mellan task 4 ↔ 5 är ett bra avbrottsläge om vi vill seriealisera Plan 2b's anatidae i parallell.

**Inte i 4a (uttryckligen):**

- Riktig TFLite-modell (4b)
- Label-mapping iNaturalist→Q-ID (4b)
- "Spara som observation"-funktionalitet (Plan 5; bara disabled CTA)
- iOS-actuals för CameraSource (Plan 6+)
- Robolectric/instrumentation-tester (deferred)
- Egen kamera-skärm för foto-upload (vi använder systemkamera + galleri)

---

## 8. Öppna frågor

Inga blockerande. Två mindre punkter att avgöra under implementation:

1. **YuvToJpegConverter:** finns flera community-implementationer. Picka en när vi gör Task 7 (CameraX) — minst-friction är CameraX `ImageProxy.toBitmap()` + `Bitmap.compress(JPEG)` om APIn är stabil i vår CameraX-version, annars en `RenderScript`/`libyuv`-baserad fallback.
2. **Top-1 confidence i top-chip — visa råsiffra eller "hög/medium/låg"?** Råsiffra (87%) i 4a för debug-tydlighet; kan döpa om till kvalitativa nivåer i Plan 5+ om användaren vill ha mindre tekniskt språk.

---

## 9. Acceptanskriterier

Plan 4a är klar när:

- [ ] App startar med Skanna-fliken som default-flik
- [ ] Permission-flow fungerar just-in-time vid Skanna-tab-klick (granted, denied, permanent denied alla testade)
- [ ] CameraX-feed renderar live på SM-S918B; 3 fps capacity-conflated-pipeline + auto-throttle till 1.5 fps om p95-latens > 333ms
- [ ] Top-chip uppdateras med fake-predictions; "söker..." visas under threshold; pulse-dot + crosshair synliga
- [ ] Tap freezar bilden, skriver JPEG till cacheDir, navigerar till ClassificationResultScreen
- [ ] PhotoAnalyzeScreen kan trigga från Skanna-skärmen, hämta bild från galleri eller systemkamera, downscala + analyze
- [ ] ClassificationResultScreen visar hero top-1 + frozen-frame-banner + top-2/3-row
- [ ] Save-CTA finns men är disabled med "kommer i Plan 5"-hint
- [ ] Tap på top-1/2/3 navigerar till SpeciesProfileScreen (Plan 3-flow återanvänd)
- [ ] Alla nya UI-strängar i `strings.xml` (sv) + `values-en/strings.xml` (en)
- [ ] `FakeBirdClassifierTest`, `ScanViewModelTest`, `PhotoAnalyzeViewModelTest`, `ClassificationResultViewModelTest` gröna
- [ ] CI grön (`./gradlew build` + ktlint + detekt)
- [ ] Manuell device-verifiering på SM-S918B med 10 screenshots committade
- [ ] Tag `v0.4.0a-camera-ui` pushad

Plan 4b kan starta efter `v0.4.0a-camera-ui` (eller parallellt med Plan 5).
