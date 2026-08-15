# i3-research: BirdNET:s FlexRFFT på iOS utan CocoaPods (2026-08-15, Mac)

**Syfte:** De-riska i3:s (ljud-ID på iOS) enda stora tekniska okändhet FÖRE brainstorm/spec: BirdNET-Lite kräver Select-TF-ops (Flex), vår vendrade `TensorFlowLiteC.xcframework` 2.17.0 är CPU/XNNPACK-only, och projektet kör utan CocoaPods. Allt nedan är **verifierat idag på den här maskinen** (inte läst ur minnet), om inget annat anges.

## TL;DR

Risken är **avsevärt mindre än befarat**:

1. Modellen behöver **exakt EN flex-op: `FlexRFFT`** (node 29) — inget mer.
2. Google publicerar en **direkt-nedladdningsbar officiell artefakt** (`TensorFlowLiteSelectTfOps` 2.17.0 = exakt samma version som vår TFLC) som kan vendras precis som TensorFlowLiteC — ren länkning, **ingen cinterop/headers behövs**.
3. Auto-registreringen är **bevisad på symbolnivå** i våra faktiska binärer (weak/strong-par + dlsym-hook, se nedan) — `-force_load` in i app-binären räcker, noll API-anrop.
4. **Enda riktiga hålet: artefakten saknar simulator-slice** (device-only). Hanterbart: vC127:s ärliga felstate + DEBUG/DEMO-gate finns redan i delad kod → sim visar demo/felstate, device kör på riktigt. (Samma "endast device-verify bevisar den sammansatta vägen"-förbehåll som i2c-kameran redan har.)

**Rekommendation:** Väg A (vendra officiella arkivet) som primärspår, väg B (selektiv bazel-build, endast RFFT) som fallback om binärpåverkan visar sig ohanterlig eller om vi senare vill ha sim-slice + mindre storlek. Väg C (egen vDSP-kernel) endast som nödutgång.

## Verifierade fakta om modellen

- Fil: `composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite`, **54 MB** (Android-only hemvist idag — se "Övriga byggstenar").
- Inputs (via ai-edge-litert-introspektion):
  - `INPUT` `[1, 144000]` float32 = 3 s @ 48 kHz waveform.
  - `METADATA_INPUT` `[1, 6]` float32 — **Android matar ALDRIG denna**: `AndroidTfliteAudioRunner.classify()` kör `interpreter.run(inputBuf, outputBuf)` med en enda input. iOS-runnern ska spegla detta men **explicit nollfylla** tensor 1 (C-API:t garanterar inte arena-innehåll; determinism > implicit beteende).
- Output: `Identity` `[1, 6362]` float32 pre-sigmoid-logits → `flatSigmoid` (clip ±15) i Kotlin, redan commonMain-nära (i runnern).
- Flex-behov, verifierat två oberoende vägar:
  - `strings`-dump av flatbuffern: enda `Flex`-prefixade custom-op-koden är `FlexRFFT` (från `stft/rfft` i grafen).
  - `ai-edge-litert` (Python, ml-eval-miljön): `allocate_tensors()` går igenom men `invoke()` failar med **exakt Android-felet**: `Select TensorFlow op(s) … Node number 29 (FlexRFFT) failed to prepare.` — bekräftar både op:en och att **ai-edge-litert-wheelen INTE bundlar Flex** (påverkar parity-referensen, se nedan).
- `BirdNetLabelMapper` + `birdnet_lite_to_qid.json` ligger redan i `shared/ml` **commonMain** → återanvänds på iOS utan ändring. Mapping-guarden (6362-klass-checken) följer med.

## Väg A (rekommenderad): vendra officiella TensorFlowLiteSelectTfOps 2.17.0

**Artefakt** (ur podspec `3/f/8/TensorFlowLiteSelectTfOps/2.17.0` på CocoaPods-CDN):

- URL: `https://dl.google.com/tflite-release/ios/prod/tensorflow/lite/release/ios/release/32/20240729-115310/TensorFlowLiteSelectTfOps/2.17.0/224693067351224e/TensorFlowLiteSelectTfOps-2.17.0.tar.gz`
- SHA-256 (uppmätt idag): `bc152ec8ceb1987e78d924d90e1e537b20e8594719c93c951595f33949fe9f85` (266 MB tar.gz)
- Innehåll: `TensorFlowLiteSelectTfOps.xcframework` med **ENDAST `ios-arm64`** (statiskt ar-arkiv, 1,1 GB ostrippat) + `PrivacyInfo.xcprivacy` + modulemap (behövs ej — vi anropar inget API).
- 2.17.0 är **sista stabila pod-versionen** (därefter bara nightlies t.o.m. 2025-06, sedan flyttade Google till LiteRT-spåret) — och den matchar exakt vår TFLC 2.17.0. Bättre versionsmatch går inte att få.

**Integrationskrav** (podspec + Googles ops_select-docs):

- `-force_load <path-till-arkivet>` på app-länken (obligatoriskt — utan den stripp­as registrerings-objekten).
- `libraries: c++` (→ `-lc++`; sannolikt redan täckt via TFLC-länkningen, verifieras i spiken) + `weak_frameworks: CoreML`.
- Bundla `PrivacyInfo.xcprivacy` som resurs (podspec:en gör det via resource_bundles — vi speglar i `project.yml`).

**Auto-registrering — bevisad mekanism** (nm på våra faktiska binärer idag):

- Vår vendrade TFLC (statisk, "Mach-O 64-bit object"): `__ZN6tflite19AcquireFlexDelegateEv` = **weak external** definition (fallbacken som dessutom dlsym:ar `TF_AcquireFlexDelegate` i runtime).
- SelectTfOps-arkivet: **strong** `T __ZN6tflite19AcquireFlexDelegateEv` + `T _TF_AcquireFlexDelegate`.
- Båda är statiska → upplösningen sker vid **appens länkning** (strong vinner över weak), med dlsym-hooken som redundant bälte-och-hängslen. Ingen dyld-osäkerhet, ingen cinterop, noll Kotlin-kod för registreringen. Detta är samma "flex-delegaten hittas automatiskt"-beteende som Android device-verifierade i i2a (logcat: `Created TensorFlow Lite delegate for select TF ops`).

**Simulator-hålet:** podspec:en sätter `EXCLUDED_ARCHS[sdk=iphonesimulator*] = i386 x86_64 arm64` och arkivet bekräftar: ingen sim-slice existerar. Konsekvenser + hantering:

- BirdNET kan **inte köra i simulatorn** med väg A. `:composeApp`-länken för sim-target får INTE force_load:a arkivet (device-target only i `project.yml`/Gradle-linkerOpts).
- Sim-beteende: audio-init failar → vC127:s **ärliga felstate** (eller DEBUG-fejken med synlig DEMO-banner) — infrastrukturen finns redan i delad kod, inget nytt behövs. Sim-checken för i3 blir UI-flöde + felstate, INTE riktig inferens.
- Full audio-verify = fysisk iPhone (samma grind-modell som i2c:s Milestone 1).
- iOS-**testerna** (`iosSimulatorArm64Test`) kan alltså inte köra riktig BirdNET-inferens. Audio-logiken (ackumulator, trösklar, mapper) är dock redan JVM-/commonTest-täckt; runner-parity får bevisas på device + mot desktop-referens.

**App-storlek:** okänd tills länk-spike — 1,1 GB är ett *ostrippat statiskt arkiv med debug-info*; det som räknas är slutbinären efter `-force_load` + dead-strip + App Store-thinning. Googles Android-referens: full select-ops 23,0 MB vs 4,1 MB selektivt byggd. Mätning är spike-uppgift #1; gate: växer appbinären ohanterligt → väg B. (Perspektiv: iOS-appen bär redan ~323 MB artbilder + 54 MB BirdNET-modell + 3,5 MB AIY-modell.)

## Väg B (fallback): selektiv bazel-build, endast RFFT

Googles dokumenterade manuella väg: `bazel build -c opt --config=ios --ios_multi_cpus=arm64,x86_64 //tensorflow/lite/ios:TensorFlowLiteSelectTfOps_framework` — med selektiv op-lista (driven av vår modellfil) blir resultatet en bråkdel av full-arkivet och kan byggas för **både device och sim**. Kostnad: bazel + TF-källträd på Mac:en, engångsjobb; resultatet pinnas med SHA-256 precis som i2a:s 16 KB-flex-`.so` på Android (etablerat mönster). Väljs om (a) väg A:s binärpåverkan är oacceptabel, eller (b) vi vill ha riktig inferens i sim/CI.

## Väg C (nödutgång, undvik): egen RFFT-kernel via vDSP

Registrera en egen custom-op under namnet `FlexRFFT` (C-API:t: custom-op-registrering ligger i experimental-headern — tillgänglighet i vår vendrade TFLC får i så fall spikas) och räkna FFT:n med Accelerate/vDSP. Noll Google-binär, minimal storlek, funkar i sim — men egen numerik (vDSP:s packning/skalning vs TF:s RFFT-konvention) = paritetsrisk + mest ingenjörstid. Endast om A **och** B faller. (Modell-omkonvertering utan flex är INTE ett alternativ — vi har bara `.tflite`:n, ingen SavedModel.)

## Övriga i3-byggstenar (små, kända mönster)

- **Modellfilens hemvist:** Android mmap:ar via `AssetFileDescriptor` (`MappedByteBuffer` — behåll, byt INTE till composeResources som skulle ge 54 MB heap-kopia). iOS: bundla via `project.yml`-folder-referens (i1-bildernas mönster) och läs `NSBundle`-path → **lifetime-pinnas** som i2b (`TfLiteModelCreate` kopierar inte — trap-katalogen).
- **Capture:** AVAudioEngine input-tap → resampla till 48 kHz mono float32 (144 000 samples/3 s-fönster; rullande fönster/stride-logiken är commonMain sedan vC127). AVAudioSession `.record` + mode `.measurement` (≈ Androids UNPROCESSED). `NSMicrophoneUsageDescription` SV+EN i `project.yml` (kamera-strängarnas i2c-mönster). K/N-fällor att vakta: **failable-init-ctor-NPE** (`AVAudioFormat`/`AVAudioFile` har `init?` — använd `uiImageFromDataOrNull`-mönstret), VM-får-inte-stänga-bootstrap-singletons, self-cancel-finalize (redan fixad i delad kod).
- **Uppspelningsfil:** Android encodar Opus endast API 29+; iOS saknar MediaCodec → AVAudioFile/AAC (enkelt) eller hoppa uppspelningsfilen i första iterationen. Spec-beslut.
- **Sessionslogik gratis:** per-art-ackumulator, trösklar 0.50/0.20/0.10, top-3-Disambig, hör-chip, Analyzing-avbryt, felstates — allt commonMain sedan vC127-batchen. i3 är i praktiken "runner + capture + bundling", inte ny produktlogik.
- **Desktop-parity-referens:** ai-edge-litert-wheelen saknar Flex (verifierat idag) → audio-parity-referensen kan INTE köras med ml-eval:s nuvarande stack. Alternativ: full TensorFlow-pip (bundlar Flex) i en separat uv-miljö, eller Android-enheten som referens. Tas i planen.

## Föreslagen spike (i3 task 1, spegel av i2b T1)

1. Vendra arkivet (`iosApp/Frameworks/`, SHA-256-pinnad nedladdning — OBS git-storlek: 1,1 GB okomprimerat spränger GitHubs 100 MB-hårdgräns → **kan inte committas rått**; vendra som nedladdnings-task med pinnad SHA (i2a-flex-mönstret) eller Git LFS-beslut i spec).
2. `-force_load` på device-länken (`linkDebugFrameworkIosArm64` + xcodebuild `CODE_SIGNING_ALLOWED=NO` bevisar länkbarhet utan iPhone).
3. Mät slutbinär-delta (gate mot väg B).
4. Första riktiga inferensen kräver fysisk iPhone (sim kan aldrig bevisa flex-vägen) — planera in i device-verify-grinden.

**Öppen fråga till spec:** committa-strategi för 1,1 GB-arkivet (nedladdnings-task à la i2a-flex vs LFS) — nedladdnings-task är förhandsfavorit (samma mönster, ingen LFS-kostnad).
