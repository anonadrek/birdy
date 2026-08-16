# i3 — Ljud-ID på iOS (design-spec)

**Datum:** 2026-08-16 (Mac). **Status:** godkänd i brainstorm 2026-08-16; bygger på research-doc `docs/superpowers/research/2026-08-15-ios-i3-flex-select-ops-research.md` (alla tekniska påståenden däri är maskinverifierade — läs den först).

## Mål

Ljud-ID på iOS med **Android v1.2-paritet**: öppen inspelning (60 s tak), rullande 3 s-fönster med live-hör-chip, auto-stopp vid träff, per-art sessions-ackumulator → top-3 → Match/Disambig/NoBird-routing, ärligt felstate + DEMO-banner i debug. Detta är iOS-spårets i3 i plan-of-plans (v2).

**Central insikt som styr hela designen:** all produktlogik (`AudioScanViewModel`, `AudioScanScreen`, trösklar, ackumulator, felstates, `AudioClassifierFactory`/`AudioSessionFailureGuard`, `BirdNetLabelMapper`) är redan commonMain sedan vC127-batchen. i3 bygger **enbart plattforms-actuals + länk-engineering** — ingen ny produktlogik, inga ändringar i delade flöden.

## Beslut (låsta i brainstorm)

| # | Beslut | Motiv |
|---|---|---|
| B1 | **Ingen uppspelningsfil på iOS i i3** — `encodeOpus` returnerar `null` | Exakt den dokumenterade degrade-vägen (Android API<29-precedens; VM:en hanterar redan null). iOS kan ändå inte spela .opus nativt; AAC + player-abstraktion = ev. senare follow-up. Albins val 2026-08-16. |
| B2 | **Flex-väg A: vendra officiella `TensorFlowLiteSelectTfOps` 2.17.0** med SHA-pinnad fetch + `-force_load` | Noll toolchain-arbete, exakt TFLC-versionsmatch, auto-registrering symbolverifierad. Väg B (selektiv bazel-build) är dokumenterad fallback bakom storleksgaten (se §Risker). Albins val 2026-08-16. |
| B3 | Modellfilen **ligger kvar** i `composeApp/src/androidMain/assets/models/` | Noll Android-churn (Android mmap:ar via AssetFileDescriptor — får inte bli composeResources-heap-kopia). iOS refererar samma fil som bundle-resurs i `project.yml`. |
| B4 | iOS-runnern **nollfyller `METADATA_INPUT` explicit** | Android lämnar tensor 1 omatad (implicit). C-API:t garanterar inte arena-innehåll — vi gör determinismen synlig. Ingen beteendeskillnad avsedd. |
| B5 | IosAppGraph speglar **MainActivitys `Deferred`-CAS-cache** (inte `AudioClassifierBootstrap`-klassen) | Trogen-spegel-principen från i2b/i2c + vC127:s eviction-fix (failad load ⇒ riktigt nytt försök) följer med. |
| B6 | Simulatorn kör **aldrig** riktig BirdNET-inferens | Artefakten saknar sim-slice (verifierat). Sim visar ärligt felstate (release-beteende) / DEMO-banner (debug) — infrastrukturen finns redan. Riktig inferens bevisas endast på fysisk iPhone + desktop-referens. |

## Arkitektur

### A. Flex-länken (väg A)

- **Nytt `tools/fetch_ios_selectops.sh`** (committas): laddar ner `https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteSelectTfOps/2.17.0/224693067351224e/TensorFlowLiteSelectTfOps-2.17.0.tar.gz`, verifierar **SHA-256 `bc152ec8ceb1987e78d924d90e1e537b20e8594719c93c951595f33949fe9f85`** (fail = avbryt, ingen uppackning), packar upp `TensorFlowLiteSelectTfOps.xcframework` till `iosApp/Frameworks/`. **Idempotent** (markörfil med SHA:n; finns den + stämmer → no-op). Tar.gz:en mellanlagras utanför repot (t.ex. `~/Library/Caches/birdy/`).
- **`.gitignore`:** `iosApp/Frameworks/TensorFlowLiteSelectTfOps.xcframework/` (1,1 GB — kan aldrig committas; jfr GitHubs 100 MB-gräns).
- **`iosApp/project.yml`:**
  - `preBuildScripts`-post som kör fetch-scriptet (så en färsk klon bygger utan manuella steg).
  - Device-villkorad länkning: `OTHER_LDFLAGS[sdk=iphoneos*]` = `-force_load $(SRCROOT)/Frameworks/TensorFlowLiteSelectTfOps.xcframework/ios-arm64/TensorFlowLiteSelectTfOps.framework/TensorFlowLiteSelectTfOps -lc++`. **Sim-SDK:n får INGA av dessa flaggor** (ingen slice finns — länken skulle faila).
  - `CoreML` som **weak** framework (podspec-kravet).
  - Bundla artefaktens `PrivacyInfo.xcprivacy` som app-resurs (podspec:ens resource_bundles-mönster).
- **Ingen cinterop, inga headers, inga API-anrop:** registreringen sker via weak/strong-symbolparet `tflite::AcquireFlexDelegate` + `TF_AcquireFlexDelegate`-dlsym-hooken vid appens länkning (symbolverifierat i research-docen). Kotlin-koden vet inte att Flex existerar.

### B. Modell-bundling

`project.yml` refererar `../composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite` (54 MB) som fil-resurs → hamnar i app-bundlens rot (eller under `models/` om xcodegen-referensen bevarar mappen — spiken avgör exakt form; runnern läser via `NSBundle.mainBundle.pathForResource`). Sökvägens androidMain-oddhet accepteras (B3) och dokumenteras med kommentar i `project.yml`.

### C. `IosTfliteAudioRunner` (shared/ml iosMain)

Spegel av `AndroidTfliteAudioRunner` på **befintlig** tflitec-cinterop (i2b):

- `load(modelPath: String)`: läser bytes, **lifetime-pinnar** (`pin()`-fält + `unpin()` i `close()` — `TfLiteModelCreate` kopierar inte; trap-katalogen), interpreter med `numThreads = 4`.
- Introspektion efter create: inputShape `[1, N]`-guard (adaptiv `expectedSamples = inputShape.last()`), output-guard `outputShape.last() == mapper.totalBirdnetClasses` (6362) — samma feltexter som Android så logg-grep:ar funkar tvärplattform.
- **Nollfyll `METADATA_INPUT`** (tensor 1) en gång efter `AllocateTensors` (B4).
- `classify(AudioInput)`: Mutex-serialisering, `check(!closed)`, waveform-storleks-require, float32-kopiering in/ut (ingen kvantisering — till skillnad från fotorunnern), `flatSigmoid` + `rankMappedScores` → `AudioClassification(results = top3, inferenceMs, modelVersion)`.
- **`flatSigmoid` lyfts till commonMain** (ligger idag privat i Android-runnerns companion) så båda runners delar exakt samma klippning/formel. Behavior-preserving flytt; Android-runnern pekas om.
- Fel kastas (factory/bootstrap äger degradering). På **simulator** utan Flex failar create/allocate/invoke → kastat fel → ärligt felstate (B6). Kastat fel får aldrig vara en K/N-krasch — verifieras med garbage-bytes-test.

### D. `IosAudioRecorder` (shared/ml iosMain)

Motsvarighet till `AndroidAudioRecorder` med **identisk callback-yta** (`start(onChunk, onCapReached, onError, maxDurationMs): Handle`):

- AVAudioSession: kategori `.record`, mode `.measurement` (≈ Androids UNPROCESSED), `setActive(true)` vid start, deaktivering i finally-vägen vid stop/cancel.
- AVAudioEngine input-tap på `inputNode` i hårdvaruformatet → `AVAudioConverter` → **48 kHz mono Int16** → `ShortArray`-chunks à ~33 ms med samma rms-formel som Android + ackumulerad total; 60 s-cap → `onCapReached`.
- Kontraktet ur `AudioRecorderApi`-KDoc:en gäller ordagrant: `onChunk` billig, callbacks på recorderns egen tråd/kö, `onError` **max en gång** och aldrig efter stop/cancel. iOS-felkällorna: engine-start-throw, `AVAudioSessionInterruptionNotification` (samtal/Siri) och mic-förlust → mappas till `onError` (motsvarar Androids `read <= 0`).
- Handle: `stopAndFlush()` (stoppa tap/engine, returnera fångad PCM; idempotent), `cancel()` (släng allt; mic-indikatorn ska släckas omedelbart).
- **K/N-fällor att vakta:** failable ObjC-inits (`AVAudioFormat`, `AVAudioConverter` m.fl.) kastar rå NPE via Kotlin-konstruktorn — fånga per trap-katalogens mönster; ingen elvis efter konstruktor.

### E. composeApp iosMain-actuals

- **`IosAudioRecorderAdapter`** — tunn `AudioRecorderApi`-brygga till shared/ml-recordern (spegel av `AndroidAudioRecorderAdapter`).
- **`IosWaveformRenderer`** (`WaveformRendererApi`): `renderWaveformPng` via CoreGraphics (CGBitmapContext → UIImagePNGRepresentation → skriv till `outPath`), visuellt speglad mot Android-renderns parametrar (staplar/färger — implementeraren läser `AndroidWaveformRenderer` och matchar); `encodeOpus` → **`null`** (B1).
- **`IosAudioPermissionController`** (implementerar befintlig `actual interface`): `AVAudioSession.requestRecordPermission` (**inte** `AVAudioApplication` — den är iOS 17+, vi stödjer 16.0), state-mappning Granted/Denied/PermanentlyDenied (iOS saknar "fråga igen" — efter denial gäller `openSettings` via `UIApplicationOpenSettingsURLString`), `recheck()` på `UIApplicationDidBecomeActiveNotification` (i2c-kameramönstret; observer städas i hostens DisposableEffect).
- **`AudioScanScreenHost.ios`** ersätter `IosComingSoonPanel`: spegel av Android-hosten — permission-flöde, `vm = remember(graph) { graph.audioScanViewModel() }`, **`DisposableEffect(vm) { onDispose { vm.cancelRecording() } }`** (det dokumenterade mic-läckage-kontraktet i `AudioScanScreenHost`-expecten), `LaunchedEffect(state)` → `onNavigateToMatch`, samma `AudioScanScreen`-anrop.
- **Mic-usage-strings** SV+EN (`NSMicrophoneUsageDescription`) i `project.yml`/InfoPlist enligt i2c:s kamerasträngs-mönster.

### F. IosAppGraph-wiring

Trogen spegel av MainActivity (B5):

- `audioBootstrapCache = AtomicReference<Deferred<Pair<BirdAudioClassifier, AudioClassifierMode>>?>` med CAS + **eviction av failad `Deferred`** (vC127-fixen) så Försök-igen gör ett riktigt nytt load-försök.
- `AudioClassifierFactory(createReal = { IosTfliteAudioRunner.load(bundlePath) }, createFallback = { FakeAudioClassifier() }, onDegrade = loggning, allowFallback = Platform.isDebugBinary)` — `kotlin.native.Platform.isDebugBinary` (`@OptIn(ExperimentalNativeApi)`) är K/N-motsvarigheten till `BuildConfig.DEBUG`: release = ärligt felstate, debug = DEMO-banner.
- `audioStorageDir = { Documents/audio, skapas vid anrop }` (NSFileManager; samma mönster som `IosPhotoStorage`).
- Recorder-adapter + waveform-renderer in i graph-konstruktionen där Android-motsvarigheterna sitter; permission-controllern skapas i hosten (som på Android).
- `versionName` → **`1.2.0-ios-i3`** (samma bump-ställe som i2b/i2c använde).

## Felhantering (sammanfattning — allt återanvänder befintliga vägar)

| Fel | Beteende |
|---|---|
| Modell-load/allocate failar (t.ex. sim utan Flex; korrupt fil) | Kastat fel → factory: release = VM:ens befintliga felstate + Försök igen (eviction ger riktigt nytt försök); debug = DEMO + banner |
| Mic nekad / permanent nekad | Befintlig permission-panel + `openSettings` |
| Mic stulen / avbrott mitt i | `onError` en gång → VM:ens `RecordingFailed`-väg (samma som Android `read<=0`) |
| Waveform-PNG/persist failar | VM:ens befintliga persist-degradering (vC127 finalize-härdning) |
| `encodeOpus` | Alltid `null` på iOS (B1) — dokumenterad degrade |

## Test & verifiering

- **Sim-körbara tester (nya):** `normalize`-ios-actual (ren matte, spegel av jvm-testet); `IosWaveformRenderer` skriver PNG med rätt dimensioner; runner-garbage-bytes → **kastat fel, inte krasch**. OBS: riktig modell kan inte lastas i K/N-testbinärer (54 MB-filen är inte composeResources + sim saknar Flex) — runnerns lyckade väg är device/desktop-territorium, uttalat.
- **Befintligt skydd:** ~300 commonTests (inkl. hela `AudioScanViewModel`-sviten med fake-recorder/klassificerare) kör redan på K/N sedan i2c — de täcker all sessionslogik på iOS-target.
- **Desktop-referens (ny plan-task):** separat uv-miljö med **full TensorFlow-pip** (ai-edge-litert saknar Flex — verifierat) genererar facit-top-3 för en committad, licenssäker 3 s-testklipp (CC0/egen inspelning — INTE xeno-canto-material); används som jämförelse vid device-verifyn.
- **Full gate per commit:** Android (`:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`) + iOS (`:shared:content/domain/data/ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test :composeApp:linkDebugFrameworkIosSimulatorArm64`).
- **Spike-bevis (task 1):** device-länken bevisas lokalt utan iPhone via `xcodebuild build -destination generic/platform=iOS CODE_SIGNING_ALLOWED=NO` (länkar force_load-arkivet på riktigt) + binärstorleksmätning.

## Grindar (Albins händer — i3 gör inte anspråk på "klar" förrän dessa körts)

1. **Sim-check** (ingen iPhone krävs): Lyssna-fliken → permission-flow med lokaliserad usage-sträng → inspelnings-UI (chip/nedräkning/waveform) → **ärligt felstate** vid analys i release-läge / DEMO-banner i debug; Back mitt i släcker mic-indikatorn.
2. **Device-verify** (kräver fysisk iPhone; samma kö som i2c Milestone 1): riktig fågelinspelning → Match/Disambig med rimlig art (jämför desktop-facit), ambient-brus 60 s → NO MATCH (0.50-sessions-max-tröskeln), Back-mitt-i, bakgrundning/resume, 60 s-cap, samtalsavbrott.

## Risker & gates

- **Storleksgaten (hård):** spike task 1 mäter appbinär-delta med force_load. **> ~150 MB okomprimerat ⇒ STOPP** — ompröva mot väg B (selektiv bazel-build, RFFT-only) innan vidare tasks. Beslutet eskaleras till Albin med uppmätta siffror.
- **`AVAudioConverter`-kvalitet:** resampling från hårdvaruformat till 48 kHz är Apples standardväg; paritetsrisken bedöms låg men fångas av device-verify mot desktop-facit.
- **Sim-blindheten:** allt mellan capture och inferens på riktigt ljud bevisas först på device (samma stående förbehåll som i2c:s live-väg). Kompenseras av commonTest-sviten + desktop-facit + ärliga felstates.

## Utanför scope (uttalat)

AAC-uppspelningsfil + player-abstraktion (ev. follow-up efter device-verify), selektiv bazel-build (endast om storleksgaten faller), ändringar i Android-ljudvägen (noll diff utanför `flatSigmoid`-lyftet), xeno-canto-kalibrering av trösklar (eget spår, follow-up #4 i CLAUDE.md).
