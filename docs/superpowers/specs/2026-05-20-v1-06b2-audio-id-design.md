# Plan 6b2 — Audio-ID via BirdNET-Lite (v0.9.0b-audio)

**Datum:** 2026-05-20
**Status:** Godkänd design — väntar på implementations-plan
**Spec-typ:** Sub-plan inom Plan 6 ("v1.0 Play Store launch")
**Föregående plan:** Plan 6b1 (Billing & Launch Prep, `v0.9.0a-billing`)
**Tag vid slut:** `v0.9.0b-audio`
**Tidsbudget:** 5–7 dagar (HIGH wildcard, 9 dagar med buffer)
**Efterföljande sub-planer:** 6b3 (Premium-content, `v0.9.0c-premium-content`) → `v1.0.0`

---

## 1. Bakgrund och syfte

Plan 6b1 levererade Billing v8 + Restore Purchases + cold-start-throttle + ML Phase B threshold-fix (`v0.9.0a-billing`, versionCode 110, versionName 1.0.0-rc2). Premium-feature-leveransen är dock fortfarande till stora delar **placeholder** — PremiumScreen visar 4 stamp-bullets (audio/PDF/stats/badges) men ingen av dem är faktiskt implementerad. Det är en känd risk: när Closed Testing-spåret går live kommer testare som köper Premium se en tom upplevelse.

Plan 6b2 levererar **audio-ID som THE premium-feature** — den mest FOMO-laddade funktionen enligt launch-research (`docs/superpowers/research/2026-05-15-play-store-launch/03-product-differentiation.md`) och primär säljpunkt i Premium-screenens hero ("Lyssna efter sång"). Listen-launcher-gear-knappen från Plan 7e + "Listen to a song"-kortet på launcher antyder redan audio till användaren.

**6b2-syfte:** Göra audio-scan till en fungerande premium-only-funktion med samma teknisk-och-design-kvalitet som image-flödet i Plan 4b + Plan 7d. När 6b2 är klart har Premium-användare en verklig "wow"-funktion att uppleva direkt efter purchase, vilket sluter conversion-loopen som Plan 6b1 öppnade.

**Strategisk position i launch-roadmap:** Plan 6b2 levererar Tier 1-item C-1 (Audio-ID, decomposed from Premium-feature-leveransen) från `docs/superpowers/research/2026-05-15-play-store-launch/00-launch-roadmap.md`. Plan 6b3 levererar resten av Premium-content (PDF-export + säsongs-stats + 10 fält-märken).

## 2. Scope

### 2.1 In scope (9 task-bundles)

| # | Område | Förändring |
|---|---|---|
| T1 | **Modell-prep + label-mapping** | Ladda BirdNET-Lite v2.4 från Cornell GitHub Release (MIT-licens), bundla i AAB (`composeApp/src/androidMain/assets/models/birdnet_lite_v2.tflite`). Skriv `tools/build-birdnet-mapping.py` som tar BirdNET-Lite:s `labels.txt` (6 522 klasser) och cross-referrar mot `species_list.yaml` → genererar `birdnet_lite_to_qid.json` filtrerad till våra 839 europeiska arter. Lägg till gradle-task `validateBirdNetMapping` (samma shape som `validateAiyMapping` i Plan 4b). |
| T2 | **`:shared:ml` audio-foundation** | `BirdAudioClassifier` interface, `AudioInput` + `AudioClassification` + `Candidate` data-classes (Candidate identisk shape med image-flödet), `AudioModelInfo`, `BirdNetLabelMapper`, `FakeAudioClassifier` (deterministisk top-3 för UI-tester). Generic-ifiera `SessionFailureGuard<T>` ut till commonMain om inte redan. Unit-tester: factory + label-mapper + failure-guard. |
| T3 | **TFLite-runner + lazy bootstrap** | `AndroidTfliteAudioRunner` med float32 input/output (BirdNET tar rå waveform `[1, 144_000]` float32, modellen har inbyggd Spec-ops för mel-spectrogram-generering). Lägg till `tensorflow-lite-select-tf-ops` dependency (~6 MB). `AudioClassifierFactory.buildAudioClassifier()` med init-fallback till `FakeAudioClassifier`. `AudioClassifierBootstrap` med AtomicReference+CAS lazy-init-pattern. `AppGraph.audioClassifierProvider: suspend () -> BirdAudioClassifier` wirad från `MainActivity`. |
| T4 | **AudioRecord + preprocessing** | `AndroidAudioRecorder` (48 kHz mono, ENCODING_PCM_16BIT, MediaRecorder.AudioSource.UNPROCESSED med fallback VOICE_RECOGNITION, 3s buffer = 144 000 samples). `suspend fun record3s(onLevel: (Float) -> Unit): ShortArray` med ~30 Hz RMS-callbacks. `AudioPreprocessor.preprocess()` är trivial — bara int16→float32-normalize (modellen gör spec internt). JVM-stubb kastar. Unit-tester med `FakeAudioRecorder`. |
| T5 | **AudioScanScreen + WaveformBars + permissions** | `AudioScanViewModel` (state machine: Preparing → Idle → Recording → Analyzing → NavigateToMatch \| Error.{Permission,Released,Failed}). `AudioScanScreen` (paper-bg + JournalIntro marginalia + WaveformBars + mic-disc + Caveat-CTA + timer + Avbryt-text-button). `WaveformBars`-component (12 staplar animerade via `StateFlow<Float>`, MarginaliaInk, `animateFloatAsState` per stapel, `frozen=true` under Analyzing). Audio-permission-controller expect/actual med `ActivityResultContracts.RequestPermission` + `shouldShowRequestPermissionRationale` + `LifecycleEventObserver(ON_RESUME)`-recheck. Onboarding-bottom-sheet vid `audioFirstEntryShown=false` (DataStore-flag). 16 nya strings sv+en (8 per locale: 3 marginalia + 3 audio-noBird-hints + permission + onboarding-rader). |
| T6 | **WaveformRenderer + ScanSource + Match-flow refactor** | `WaveformRenderer` expect/actual (PCM ShortArray → bucketing → smoothing → Bitmap → PNG, ~30 ms render-tid, 600×200 paper-bg + MarginaliaInk staplar + AccentCopper underline). Opus encoder wrapper via MediaCodec (~32 kbps, ~30 KB per 3s-fil). `ScanSource` sealed interface (Image + Audio variants, båda har `frameJpegPath: String` non-null). `MatchResultViewModel` muteras till `(source: ScanSource, ...)`-konstruktor. Match/Disambig/NoBird-skärmar tar emot `source`-param; routes `noBirdHints()` via extension `ScanSource.noBirdHints(): List<Hint>`. DB-migration v3→v4 (lägg `audio_path TEXT DEFAULT NULL` + `source TEXT NOT NULL DEFAULT 'photo'` på `observation`). `SqlDelightObservationRepository.delete()` returnerar `FileCleanupRequest` så VM kan radera audio + PNG. |
| T7 | **ListenLauncher + PremiumGate** | `PremiumGate` reusable component (semi-transparent dark overlay + 🔒-ikon + Caveat "Premium → Lås upp"-CTA, tap → emittar `onUnlockTap`). Lägg till tredje `LauncherCard` ("Listen to a song" / "Lyssna på sång") i ListenLauncherScreen. Routing-logik: `premiumActive=true` → `Navigate(AudioScan)`; `premiumActive=false` → `Navigate(Premium)`. Pessimistic lock vid `premiumState=Loading`. |
| T8 | **ML eval + accuracy report** | `tools/ml-eval/build_audio_corpus.py` (xeno-canto API + CC-licens filter, 30 svenska arter, crop till 3s windows). `tools/ml-eval/eval_birdnet.py` (kör BirdNET-Lite på corpus, mäter top-1 + top-3 accuracy). Commit `tools/ml-eval/accuracy_report_2026-05-XX.md`. Target: top-3 ≥ 70% (samma kvalitetsribba som AIY V1 i Plan 4b). |
| T9 | **Device-verify + screenshots + tag** | 12-checklist device-verify på SM-S918B (API 35) enligt Sektion 9 av designen. Capture 12 canonical screenshots. Bump versionCode 110→120, versionName 1.0.0-rc2→1.0.0-rc3. Commit screenshots + accuracy-report. Tag `v0.9.0b-audio`. Uppdatera CLAUDE.md + auto-memory `project_plan_6b2_status.md`. |

### 2.2 Out of scope (deferrat till 6b3 eller helt utanför)

| Deferral | Plan | Anledning |
|---|---|---|
| Audio-playback i ObservationDetailScreen (`🔊 Spela upp`-knapp) | 6b3 | Audio-fält finns i schema men playback-UI är nästa steg; vi sparar utvecklingstid genom att deferra |
| Continuous-listen / passive-detection mode | v1.x | Audio = scoped till push-to-record-MVP per Q1 |
| Konfigurerbar inspelningslängd 1-5s | v1.x | Per Q7 — fixed 3s räcker; YAGNI |
| Sliding-window inference (overlapping windows) | v1.x | Per Q7 — pragmatiskt fixed 3s räcker; lägg till om device-verify visar timing-issues |
| Server-side audio-corpus / community-uploads | v2 | Privacy-promise-bryter; lokal-bara |
| Audio-quiz / utbildningsläge | v2.x | Out of v1.0-scope |
| iOS audio-support | v1.5+ | KMP-shared interface men actual=stub på iOS för v1 |
| Real-time-spectrogram-visualisering under inspelning | — | Per Q5 valde A (waveform-bars) — billigare och journal-stilrent |
| Orphan-audio-file cleanup-worker | 6b3 | Synchronous delete från VM räcker för normal flow |
| Multi-bird-detection (flera arter i ett klipp) | v1.x | BirdNET-Lite stödjer det men UI-flow blir komplext; v1 räcker med top-1 + Disambig vid osäkerhet |

### 2.3 Success criteria

1. Premium-användare kan tap Audio-kortet på ListenLauncher → spela in 3s → få Match-resultat på fysisk enhet (SM-S918B)
2. Free-användare ser audio-kortet på ListenLauncher med `PremiumGate`-overlay; tap → routes till PremiumScreen (inte AudioScan)
3. Audio-modellen laddas **endast vid första audio-entry** (verifierat via `adb logcat | grep AudioClassifierBootstrap` — ingen load-rad vid app-start)
4. Audio-inferens latens på SM-S918B ≤ 100 ms efter att inspelning är klar (mätt i `AudioClassification.inferenceMs`)
5. Waveform-PNG genereras + sparas till `filesDir/audio/{ts}.png` inom ≤ 30 ms efter inspelning
6. Opus-encoded WAV (≤ 50 KB per 3s-fil) sparas till `filesDir/audio/{ts}.opus`
7. Match-resultat-skärmen visar waveform-PNG i PlateFrame med "fångad i sång"-marginalia
8. Audio-obs visas i Diary med waveform-thumbnail som hero-bild; Detail-skärmen visar samma waveform
9. Sighting-detektering är källa-agnostisk: photo-obs + audio-obs av samma art = "GÅNG 2" i Match-marginalia
10. RECORD_AUDIO-permission-flow täcker alla tre states (NotAsked / Denied / PermanentlyDenied) med korrekt CTA per state
11. ML eval-rapport visar top-3 accuracy ≥ 70% på 30-sample xeno-canto corpus
12. APK-storlek-bump dokumenterad i CLAUDE.md (~23 MB: 17 MB modell + 6 MB select-tf-ops runtime)
13. 12 canonical device-screenshots committed till `docs/superpowers/screenshots/2026-05-XX-v0.9.0b-audio/`
14. Tag `v0.9.0b-audio` på main; versionCode `120`, versionName `1.0.0-rc3`
15. Auto-memory `project_plan_6b2_status.md` skriven med locked patterns + post-tag follow-ups

## 3. Arkitekturbeslut

### A. Parallell audio-pipeline med delade utility-pieces (locked från Q-flow)

Vald approach: **C (Hybrid)** från arkitektur-förslagen. Behåll `BirdClassifier` som image-only-interface (Plan 4b orört, ingen regressionsrisk). Introducera ny `BirdAudioClassifier`-interface parallellt. Lyft ut det som faktiskt är återanvändbart till delade abstraktioner:

```
shared/ml/
├─ BirdClassifier (image, oförändrat från Plan 4b)
├─ BirdAudioClassifier (nytt)
│
├─ SessionFailureGuard<T>           ← generic-ifierat
├─ ModelArtifactProvider            ← läs path från BuildConfig + verifiera SHA256
├─ TfliteRunnerBase                 ← gemensam buffer/Mutex/close-logik
│   ├─ AndroidTfliteImageRunner (uint8, AIY V1)
│   └─ AndroidTfliteAudioRunner (float32, BirdNET-Lite)
│
├─ ClassifierBootstrap (image — eager, oförändrad)
└─ AudioClassifierBootstrap (lazy, ny — triggas vid Audio-entry per Q3)
```

Motivering: B (full sealed-refaktor `ModelClassifier<TInput, TOutput>`) är "rätt på papper" men kostar för mycket pre-v1.0 där vi har en låst launch-deadline och Plan 4b är i prod sedan 2026-05-08. A (full parallell pipeline) duplicerar för mycket. C balanserar Plan 4b-stabilitet med teknisk hygien.

### B. Lazy audio-bootstrap (Q3 = B)

Image-modellen laddas eager via `ClassifierBootstrap` i `MainActivity.runBlocking` (Plan 4b oförändrat). Audio-modellen laddas **lazy** första gången användaren tappar Audio-kortet:

```kotlin
// I AppGraph (commonMain):
val audioClassifierProvider: suspend () -> BirdAudioClassifier

// I MainActivity:
private val audioBootstrapCache = AtomicReference<Deferred<BirdAudioClassifier>?>(null)

val audioProvider: suspend () -> BirdAudioClassifier = {
    val cached = audioBootstrapCache.get()
    if (cached != null) cached.await()
    else {
        val deferred = lifecycleScope.async(Dispatchers.IO) {
            buildAudioClassifier(applicationContext, AudioModelInfo.load(applicationContext)).first
        }
        if (audioBootstrapCache.compareAndSet(null, deferred)) deferred.await()
        else { deferred.cancel(); audioBootstrapCache.get()!!.await() }
    }
}
```

`AtomicReference + CAS` ger thread-safe lazy init utan att blocka cold-start för free-users. Konsekvens: free-users (~90% av base per launch-research) laddar **aldrig** audio-modellen i RAM — privacy-win + perf-win.

### C. Full BirdNET-grafen (med inbyggd Spec-ops, Alt 3A)

BirdNET-Lite har inbyggd mel-spectrogram-generering i grafen (Spec ops). Vi kör hela modellen som distribueras (inte strippad till float32-spec-input) → skickar rå 144 000 float32-samples in, modellen producerar spec internt.

**Konsekvens:** `AudioPreprocessor` förenklas till bara int16→float32-normalize (ingen FFT, ingen Hanning, ingen mel-filterbank i Kotlin). TFLite-runtime kräver `tensorflow-lite-select-tf-ops` dependency (~6 MB APK-bump) istället för standard `tensorflow-lite:2.x`.

**Motivering:** Sparar betydande utvecklingstid på FFT-validering (notoriskt buggigt) och garanterar identiskt resultat som BirdNET-officiella Python-verktyg. APK-bump är acceptabel för Premium-feature.

### D. ScanSource sealed för Match-flow-återanvändning (Q6 = B)

Plan 7d:s `MatchResultViewModel` opererar redan på en sealed `MatchSource`-tanke implicit. Vi gör abstraktionen explicit:

```kotlin
sealed interface ScanSource {
    val frameJpegPath: String        // non-null för båda (audio = waveform-PNG)
    val classification: Classification

    data class Image(
        override val frameJpegPath: String,
        override val classification: Classification
    ) : ScanSource

    data class Audio(
        override val frameJpegPath: String,        // waveform-PNG path
        override val classification: Classification,
        val audioWavPath: String                   // .opus path för replay i Detail (Plan 6b3)
    ) : ScanSource
}
```

`MatchResultViewModel(source: ScanSource, ...)`-konstruktor. Match/Disambig/NoBird-skärmar tar emot `source`-parametern. Sighting-detektering är källa-agnostisk (`observationRepo.countByQid(qid)` räknar båda modaliteter).

Konsekvens: minimal duplication av Plan 7d-logiken. Två skärmar (Match + Disambig + NoBird) bär audio-flödet med justering bara på marginalia-strängar + noBirdHints + PlateFrame-content (PNG istället för JPEG).

### E. Premium-gating på tre lager (Q2 = B)

Defense-in-depth:

1. **UI-presentation:** `PremiumGate`-overlay på Listen-launcher Audio-kort
2. **Navigation:** `ListenLauncherScreen.onTap` routar till `Premium` (free) eller `AudioScan` (premium)
3. **Skärm-init:** `AudioScanViewModel.init { if (!premium) navigateUp() }` — säkrar mot framtida deep-links

`PremiumState` läses från `AppGraph.premiumStateFlow: StateFlow<PremiumState>` (samma flow som per-tab-teasers från Plan 7e använder). `PREMIUM_DEBUG_FORCE_ACTIVE = true` BuildConfig från Plan 7e fortsätter funka.

### F. Waveform-thumbnail som visuell signatur (Q6 implementation)

Under inspelningen samplar vi PCM-buffern → renderar en statisk waveform-PNG via `WaveformRenderer.render()` → sparas till `filesDir/audio/{ts}.png` och passas som `frameJpegPath` till PlateFrame. Bucketing (144 000 samples → 600 buckets) + 3-tap smoothing + Canvas-rendering med paper-bg + MarginaliaInk-staplar + AccentCopper underline. Latens ~30 ms på SM-S918B.

Konsekvens: audio-fynd får visuell identitet utan att duplicera Match-skärmen. Samma PlateFrame, samma marginalia, samma stamp — bara annat innehåll i hero-zonen.

### G. Opus istället för WAV för audio-storage

WAV = 288 KB per 3s; Opus = ~30 KB per 3s. 10× lagrings-vinst. MediaCodec native på alla Android-versioner vi targetar (API 26+). Kvalitet är auditivt opåverkad för 3s monoljudsklipp.

### H. DB-migration v3→v4 (audio_path + source-kolumner)

Befintliga `observation`-schemat (efter Plan 6a Task 10.6 / migration `2.sqm`) är version 3. Vi lägger till migration `3.sqm` som tar v3 → v4:

```sql
ALTER TABLE observation ADD COLUMN audio_path TEXT DEFAULT NULL;
ALTER TABLE observation ADD COLUMN source TEXT NOT NULL DEFAULT 'photo';
```

Båda DEFAULT — backward-compat med befintliga Plan 5a/6a obs (alla får `source='photo'`, `audio_path=null`).

**Notera om `photo_path`-kolumnen:** befintliga schema har `photo_path TEXT NOT NULL` (utan default). För audio-obs sparar vi waveform-PNG-pathen i denna kolumn — kolumnen är funktionellt "hero-image path" oavsett källa. Semantiskt skulle `hero_path` vara mer korrekt; rename-migration deferrad till v1.x cleanup för att hålla v3→v4-migrationen minimal.

SQLDelight migration testas i `ObservationRepositoryAudioTest` (inkluderar import av Plan 5a-data och verifierar att `source='photo'` defaultas korrekt).

## 4. Komponenter i detalj

### 4.1 `:shared:ml` (nya filer)

```
shared/ml/src/commonMain/kotlin/se/birdy/ml/
├─ BirdAudioClassifier.kt         # interface { val info; suspend classify; close }
├─ AudioInput.kt                  # waveform: FloatArray (144_000), sampleRate, durationMs, rawPcm: ShortArray?
├─ AudioClassification.kt         # top: List<Candidate>, inferenceMs, modelVersion
├─ AudioPreprocessor.kt           # expect normalize(pcm: ShortArray): FloatArray
├─ BirdNetLabelMapper.kt          # index → Qid via birdnet_lite_to_qid.json
├─ AudioModelInfo.kt              # modelVersion, inputShape, outputShape
├─ TfliteRunnerBase.kt            # gemensam buffer-disciplin (Mutex, idempotent close)
├─ AudioClassifierBootstrap.kt    # lazy bootstrap-suspend-fun
└─ AudioClassifierFactory.kt      # init + 3-strikes fallback to FakeAudioClassifier

shared/ml/src/androidMain/kotlin/se/birdy/ml/
├─ AudioPreprocessor.android.kt   # actual: int16→float32-normalize
├─ AndroidTfliteAudioRunner.kt    # float32 Interpreter, Mutex-serialiserad
└─ AndroidAudioRecorder.kt        # AudioRecord wrap, 48kHz mono, 3s record + RMS callback

shared/ml/src/jvmMain/kotlin/se/birdy/ml/
└─ AudioPreprocessor.jvm.kt       # stub som kastar; tester injectar fakes

shared/ml/src/commonTest/kotlin/se/birdy/ml/
├─ FakeAudioClassifier.kt         # deterministic top-3
├─ AudioClassifierFactoryTest.kt  # init + 3-strikes degrade
├─ BirdNetLabelMapperTest.kt      # 5 golden mappings + missing index → null
└─ SessionFailureGuardGenericTest.kt  # om generic-ifiering sker
```

### 4.2 `:composeApp` (nya filer + mutationer)

```
composeApp/src/commonMain/kotlin/se/birdy/ui/
├─ audio/                                           # NY paket
│   ├─ AudioScanScreen.kt
│   ├─ AudioScanViewModel.kt
│   ├─ AudioPermissionController.kt                 # expect interface
│   ├─ WaveformBars.kt
│   └─ WaveformRenderer.kt                          # expect
├─ launcher/ListenLauncherScreen.kt                 # MUTERAD — lägg AudioCard
├─ match/MatchResultViewModel.kt                    # MUTERAD — sealed ScanSource
├─ match/MatchView.kt                               # MUTERAD — source-aware marginalia
├─ match/NoBirdView.kt                              # MUTERAD — audio-hints om source=Audio
├─ components/PremiumGate.kt                        # NY
└─ AppRoute.kt                                      # NY — AppRoute.AudioScan + AppRoute.MatchResult tar sourceJson

composeApp/src/androidMain/kotlin/se/birdy/ui/
├─ audio/AudioScanScreenHost.android.kt             # actual host med permission-controller
├─ audio/AudioPermissionController.android.kt       # actual via ActivityResultContracts
└─ audio/WaveformRenderer.android.kt                # actual via Canvas + Bitmap → PNG

composeApp/src/commonMain/composeResources/
├─ values/strings.xml                               # MUTERAD — +8 strings (sv)
├─ values-en/strings.xml                            # MUTERAD — +8 strings (en)
└─ files/birdnet_lite_to_qid.json                   # NY — genererad av tools/build-birdnet-mapping.py
```

### 4.3 `:shared:data` (mutation)

```
shared/data/src/commonMain/sqldelight/migrations/
└─ 3.sqm                                            # NY migration v3→v4
    ALTER TABLE observation ADD COLUMN audio_path TEXT DEFAULT NULL;
    ALTER TABLE observation ADD COLUMN source TEXT NOT NULL DEFAULT 'photo';

shared/data/src/commonMain/kotlin/se/birdy/data/
└─ Observation.kt                                   # MUTERAD — audioPath, sourceType-fält

shared/data/src/commonMain/kotlin/se/birdy/data/
└─ SqlDelightObservationRepository.kt               # MUTERAD — delete returnerar FileCleanupRequest
```

### 4.4 `:composeApp/assets` (bundled model)

```
composeApp/src/androidMain/assets/models/
└─ birdnet_lite_v2.tflite                           # ~17 MB, MIT-licens
```

### 4.5 `tools/ml-eval/` (nytt eval-script)

```
tools/ml-eval/
├─ build_audio_corpus.py                            # xeno-canto API + CC-filter + 3s-crop
├─ eval_birdnet.py                                  # kör BirdNET-Lite på corpus, mäter accuracy
├─ corpus_audio/                                    # gitignored
└─ accuracy_report_2026-05-XX.md                    # committed
```

### 4.6 `tools/build-birdnet-mapping.py` (nytt build-script)

Tar BirdNET-Lite `labels.txt` (6 522 klasser) + `species_list.yaml` (839 europeiska arter) → genererar `birdnet_lite_to_qid.json` med `{ "index": int → qid: string }` mapping. Filtrerar bort arter som inte finns i vår species_list.

## 5. Edge cases

Konsoliderad lista — alla covered i implementation:

| Kategori | Edge case | Hantering |
|---|---|---|
| **Permission** | RECORD_AUDIO nekas första gången | `Error.PermissionDenied` med "Ge tillstånd"-CTA som re-triggar request |
| | "Don't ask again" / 2× denial | `Error.PermanentlyDenied` med "Öppna inställningar"-CTA → `APPLICATION_DETAILS_SETTINGS`-intent |
| | Användaren ger tillstånd via Settings → kommer tillbaka | `LifecycleEventObserver(ON_RESUME)` recheckar, state flippar till `Idle` |
| **Recording** | Användaren släpper före 3000 ms | Snackbar "Håll i 3 sekunder", state åter till `Idle` |
| | Telefonsamtal/system-overlay mid-scan | `onPause` stoppar `AudioRecord`, state åter till `Idle`, ingen partial-recording sparas |
| | AudioRecord-init fails (rare) | `Error.RecordingFailed` med "Mikrofonen kunde inte startas" + retry-CTA |
| **Classifier** | TFLite-inferens kastar 1-2 gånger | `SessionFailureGuard` retryar, transparent för UI |
| | TFLite-inferens kastar 3 ggr i rad | `onDegrade` → swap till `FakeAudioClassifier` + DEMO-banner |
| | Bootstrap fails (model-fil korrupt / OOM) | `Error.BootstrapFailed` + JournalDialog "Kunde inte starta lyssnandet" + reset-CTA |
| | Inference returnerar empty top-list | `Classification.top.isEmpty()` → routes till `NoBird` |
| **Premium** | Premium-state flippar `Active → Inactive` mid-scan | Låt scanan slutföras, ny gating gäller från nästa entry |
| | Premium-state är `Loading` när användaren tappar Audio-kort | Pessimistic lock — tap är no-op, kortet visar overlay |
| | `Restore Purchases` aktiverar mitt under app-session | `StateFlow.collectAsState()` triggar rekomposition, overlay försvinner |
| **Storage** | `filesDir/audio/` finns inte vid första save | `mkdirs()` skapar idempotent |
| | Timestamp-collision | Suffix `-1`, `-2` på filnamnet |
| | Disk-fullt vid PNG/Opus-write | Catch `IOException`, save-action fail:ar med Caveat-toast "Kunde inte spara — fritt utrymme behövs" |
| **Lifecycle** | Process death mid-recording | Recording förloras; nästa app-launch börjar fresh från ListenLauncher |
| | Process death mid-Match-screen | Match-state förloras; användaren börjar om. Ingen DB-rad skriven |
| | `onTrimMemory(TRIM_MEMORY_BACKGROUND+)` | `audioBootstrapCache.set(null)` frigör modellen; nästa entry reloadar |
| **Modell-data** | BirdNET-index saknas i mapping JSON | Drop kandidaten från top-list, log warning. Om alla top-3 saknas → `NoBird` |
| | BirdNET returnerar utomeuropeisk art | Filtrerat bort vid build-time — `birdnet_lite_to_qid.json` har bara våra 839 arter |
| **Multi-modal** | Photo-scan + audio-scan av samma art | `countByQid` = 2 → "GÅNG 2"-marginalia. Båda obs visas separat i Diary |
| | Audio-obs i Lifelist + Badges | Källa-agnostiskt — räknas som "seen" oavsett modalitet |
| **Locale** | EN/SV-switch under app-session | strings rekomponerar via compose-resources (existing pattern från Plan 6a) |

## 6. Testing-strategi

### 6.1 Unit-tester (JVM)

`:shared:ml` — `AudioClassifierFactoryTest`, `BirdNetLabelMapperTest`, `AudioModelInfoTest`, `SessionFailureGuardGenericTest`.

`:shared:data` — `ObservationRepositoryAudioTest` (insert/delete/countByQid med audio-source), migration v3→v4-test.

`:composeApp` — `AudioScanViewModelTest` (full state-machine med fakes), `MatchResultViewModelAudioTest` (utvidgar Plan 7d med audio-source-tester), `ListenLauncherViewModelTest` (premium-routing).

### 6.2 Integration-tester (JVM med fakes)

`AudioSaveFlowIntegrationTest` — VM → Repo → DB → BadgeEvaluator end-to-end med fake classifier. `AudioPremiumLapseTest` — Premium-state flip mid-scan, scanan slutförs, gating gäller nästa entry.

### 6.3 Device-verify (manuell ADB-driven på SM-S918B)

12-checklist enligt design Sektion 9 (Sektion 9 listar full lista; alla 12 måste pass före tag).

### 6.4 ML eval (offline Python)

`tools/ml-eval/eval_birdnet.py` på 30-sample xeno-canto corpus. Target: top-3 ≥ 70%.

### 6.5 Test-image-infra för deterministisk audio (dev-only)

Återanvänder mönstret från Plan 5b:s `test_species.txt`. Om `filesDir/test_audio.opus` finns vid app-start, `AndroidAudioRecorder.record3s()` returnerar test_audio:s PCM istället för riktig mic. Krävs för deterministisk Disambig + NoBird screenshot-capture.

### 6.6 CI-påverkan

Befintliga `:shared:ml:jvmTest` + `:composeApp:testDebugUnitTest` plockar upp nya tester automatiskt. Ingen ny gradle-task. Inget device-test-step i CI (samma policy som Plan 4b).

## 7. Sprint-plan

| Sprint | Tasks | Estimat |
|---|---|---|
| **T1: Modell-prep + label-mapping** | Ladda BirdNET-Lite v2, bundle, `build-birdnet-mapping.py`, `validateBirdNetMapping`-gradle-task | 0.5 dag |
| **T2: `:shared:ml` audio-foundation** | Interfaces, data classes, FakeAudioClassifier, label-mapper, unit-tester | 1 dag |
| **T3: TFLite-runner + lazy bootstrap** | `AndroidTfliteAudioRunner`, factory, `SessionFailureGuard<T>`-generic, `AudioClassifierBootstrap` med CAS, `AppGraph`-wiring | 1 dag |
| **T4: AudioRecord + preprocessing** | `AndroidAudioRecorder`, `AudioPreprocessor.normalize`, unit-tester med fake recorder | 0.5 dag |
| **T5: AudioScanScreen + WaveformBars + permissions** | VM, screen, WaveformBars, permission-controller expect/actual, onboarding-bottom-sheet, strings sv+en | 1.5 dagar |
| **T6: WaveformRenderer + ScanSource + Match-flow refactor** | `WaveformRenderer`, Opus encoder, `ScanSource` sealed, `MatchResultViewModel` mutation, DB-migration v3→v4, FileCleanupRequest | 1.5 dagar |
| **T7: ListenLauncher + PremiumGate** | `PremiumGate` reusable, AudioCard i ListenLauncher, premium-routing, navigation-effect | 0.5 dag |
| **T8: ML eval + accuracy report** | `build_audio_corpus.py`, `eval_birdnet.py`, accuracy_report.md | Parallellt med T2-T3 |
| **T9: Device-verify + screenshots + tag** | 12-checklist, 12 screenshots, versionCode bump, tag, CLAUDE.md, auto-memory | 1 dag |
| **Buffer** | Oförutsedda issues, model-mapping-fix, Opus-encoder-quirks | 1 dag |
| **TOTAL** | | **~9 dagar (5-7 om buffer inte används)** |

## 8. Definition of Done

- [ ] Alla unit-tester gröna i CI (`:shared:ml:jvmTest`, `:shared:data:jvmTest`, `:composeApp:testDebugUnitTest`)
- [ ] `./gradlew build` grön
- [ ] `./gradlew ktlintCheck detekt` grön
- [ ] `./gradlew validateBirdNetMapping` grön
- [ ] 12-checklist device-verify pass på SM-S918B (API 35) — alla 12 steg godkända
- [ ] 12 canonical screenshots committed till `docs/superpowers/screenshots/2026-05-XX-v0.9.0b-audio/`
- [ ] ML eval-report visar top-3 ≥ 70% på 30-sample xeno-canto corpus; committed till `tools/ml-eval/accuracy_report_2026-05-XX.md`
- [ ] DB-migration v3→v4 testad med befintlig Plan 5a data (importera obs, verifiera fortfarande läsbara, audioPath=null + source='photo' default)
- [ ] APK-storlek-bump dokumenterad i CLAUDE.md (~23 MB)
- [ ] Tag `v0.9.0b-audio` på main; versionCode `120`, versionName `1.0.0-rc3`
- [ ] Auto-memory `project_plan_6b2_status.md` skriven med locked patterns + post-tag follow-ups
- [ ] CLAUDE.md uppdaterad med Plan 6b2-status

---

## Bilaga A — Q-flow-beslut (för posterity)

| # | Fråga | Svar | Lockad |
|---|---|---|---|
| Q1 | MVP audio-capture-strategi | A — push-to-record, 3s fast fönster | ✅ |
| Q2 | Premium-gating | B — Tease (UI öppen + knapp låst via PremiumGate) | ✅ |
| Q3 | Multi-model bootstrap | B — Lazy-load audio vid första entry | ✅ |
| Q4 | Audio-mode entry-punkt | B — Listen-launcher får ett "Audio"-kort | ✅ |
| Q5 | Listening-UI | A — Waveform-bars (12 staplar, RMS-driven) | ✅ |
| Q6 | Resultat-skärm | B — Auto-genererad waveform-thumbnail som "hero-bild" | ✅ |
| Q7 | Inferens-fönster | A — Fixed 3s (BirdNET-Lite native window) | ✅ |
| Arch | Pipeline-strategi | C — Hybrid (parallell pipeline + delade utility-pieces) | ✅ |
| Alt 3 | TFLite-graf-strategi | A — Full BirdNET-grafen med inbyggda Spec ops | ✅ |
| Bootstrap | Lazy-init-pattern | AtomicReference+CAS (komplext men korrekt) | ✅ |
| Audio-storage | Format | Opus (~30 KB per 3s, MediaCodec native) | ✅ |
