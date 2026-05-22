# Open-ended Listen-flow + website-matchad recording-bar

**Datum:** 2026-05-22
**Scope:** Lyssna-fliken (audio-ID via BirdNET-Lite) + website `Listen.astro`-sektionen
**Status:** Spec — väntar på user-review + writing-plans

## Bakgrund

Plan 6b2 (`v0.9.0b-audio`) levererade audio-ID med hårdkodad 3s-inspelning: usern håller mic-knappen, en `X.Xs`-countdown räknar ned från 3.0, sedan klassificering. Designen funkar men har två problem:

1. **Tidspress.** 3 sekunder är knappt nog för en koltrast att hinna sjunga en hel fras. Användaren slipper ångest om gränsen försvinner — Birdy ska istället lyssna *tills den känner igen fågeln*.
2. **UI inkonsekvent med website.** `birdy.community/#listen` visar 48 kopparfärgade pulserande staplar och en 64dp copper mic-button med vit `●`. Appen visar 12 mörkgröna staplar och en 92dp cirkel med 🎙-emoji. Marketing-bilden lovar något appen inte levererar.

Den här specen designar bort båda problemen i en ändring.

## Mål

- Recording-duration styrs av confidence-threshold eller manuell stop, inte en hård 3s-cap.
- Appens recording-bar matchar `Listen.astro` visuellt (48 copper-staplar + copper mic-button) men utnyttjar live-RMS för en mer levande pulse än website-mockupen.
- Website + app säger samma sak ("tryck för att lyssna", inte "hold 3s").

## Icke-mål

- Live "preliminär art"-visning under inspelning (övervägd, avförd för v1 — Plan 7d har redan etablerat att preliminär species är en känslig UX-yta).
- Confidence-meter eller progress-indikator.
- Ändringar i `BirdAudioClassifier`-interface, Match/Disambig/NoBird-skärmar, eller `ScanSource.Audio`-serialization.
- Background-recording när skärm låses — befintlig `DisposableEffect`-pattern cancellar fortfarande.
- Wakelock-hantering — 60s hard cap håller power-budget acceptabelt utan.

## Stopp-villkor

Inspelningen avslutas på exakt ett av tre sätt:

| Trigger | När | Action |
|---|---|---|
| **Auto-stop** | Top-1 confidence ≥ **0.60** över rollande 3s-fönster, efter att minst 3000 ms recording-time passerat | Stoppa recorder. Final-classify på det 3s-fönster som producerade `bestSoFar`. Navigera via Plan 7d threshold-routing (Match / Disambig / NoBird). |
| **Manuell stop** | User trycker mic-knappen igen | Stoppa recorder. Kör final-classify (se Final-classify-fallback nedan). Knapp är disabled när `elapsedMs < 3000`. |
| **60s hard cap** | `elapsedMs ≥ 60_000` | Behavior identisk med manuell stop. |

Threshold-värdet 0.60 ärvs från Plan 7d (`MatchThreshold.HIGH_CONFIDENCE`) — håller routing-beteendet konsekvent mellan foto-match och audio-match.

`bestSoFar` lagras som `Top1 = (speciesId, confidence, pcmOffset, pcmEnd)` där `pcmOffset/End` markerar var i hela PCM-bufferten det 3s-fönstret ligger. Vid final-classify slicear vi ut det fönstret och kör `classifier.classify()` en gång till — vi vill ha hela `AudioClassification`-resultatet (alla candidater) för Disambig-routing, inte bara top-1.

### Final-classify-fallback

`bestSoFar` kan vara `null` även efter 3s om classifier ännu inte hunnit producera sitt första resultat (Default-dispatcher kan vara upptagen). Vid stop (manuell eller cap):

| Tillstånd | Final-classify-input |
|---|---|
| `bestSoFar != null` | PCM-fönster `[pcmOffset, pcmEnd]` |
| `bestSoFar == null` | Senaste 3s av buffer = PCM `[totalSamples - 48_000, totalSamples]` |

Resultatet routas alltid via Plan 7d threshold (Match / Disambig / NoBird). Tom `AudioClassification` → NoBird-screen.

## State-machine-tillägg

`AudioScanState.Recording` får ett nytt fält:

```kotlin
data class Recording(
    val rms: Float,
    val elapsedMs: Long,
    val bestSoFar: Top1? = null,   // NY — null tills första 3s-fönstret klassificerats
) : AudioScanState

data class Top1(
    val speciesId: String,
    val confidence: Float,
    val pcmOffset: Int,
    val pcmEnd: Int,
)
```

`Analyzing(rmsFrozen)` är oförändrat. `Top1` är ett internt VM-värde — UI exponerar inte det (vi visar inte preliminär art).

## Arkitektur

### Recorder

`AudioRecorderApi.record3s(onLevel)` ersätts av en streaming-variant:

```kotlin
interface AudioRecorderApi {
    /**
     * Open-ended capture. Returns a handle as soon as AudioRecord initialiserats.
     * Emits PCM chunks via [onChunk] until [stopAndFlush] kallas eller [maxDurationMs] nås.
     */
    fun start(
        onChunk: (samples: ShortArray, rms: Float, totalSamplesSoFar: Int) -> Unit,
        maxDurationMs: Long = 60_000L,
    ): RecorderHandle
}

interface RecorderHandle {
    /** Stoppa recorder och returnera full PCM captured så långt. Idempotent. */
    suspend fun stopAndFlush(): ShortArray
    /** Avbryt + släng all data. Idempotent. */
    fun cancel()
}
```

Android-impl wrappar `AudioRecord` i en `Job` på `Dispatchers.IO`. Loop läser `chunkSize = sampleRate / 30` (~33ms) per iteration. Buffer = ringbuffer på max `maxDurationMs * sampleRate / 1000` samples = 60 × 48 000 = 2 880 000 short = ~5.7 MB. När cap nås triggas `onCapReached` callback (ViewModel hanterar som "manuell stop").

UNPROCESSED → VOICE_RECOGNITION fallback bevaras (befintligt mönster i `AndroidAudioRecorder`).

### Sliding-window klassificering

`AudioScanViewModel.startRecording()`:

1. Anropa `recorder.start(onChunk = ::handleChunk)`.
2. `handleChunk` appendar samples till intern buffer. När `totalSamplesSoFar ≥ 48_000` (3s) **och** `totalSamplesSoFar % 16_000 == 0` (1s stride) **och** ingen inferens är inflight → submitta ny inferens på senaste 48 000 samples.
3. Inferens-job kör på `Dispatchers.Default`, anropar `classifier.classify(AudioInput(window, 48_000, 3_000))`. Resultat jämförs mot `bestSoFar`. Om top-1 > `bestSoFar.confidence` → uppdatera. Om top-1 ≥ 0.60 → trigga auto-stop.
4. Inflight-cap = 1. Om föregående inferens fortfarande kör när nästa stride-marker passeras: hoppa det fönstret (vi tappar inte data, vi tappar bara en chans att klassificera tidigare). På S23 Ultra är detta sällsynt — BirdNET-Lite kör ~50-200ms/inferens, vi har 1000ms budget per stride.
5. Auto-stop/manuell-stop/cap → `recorder.stopAndFlush()` → state till `Analyzing(rmsFrozen)` → `analyzeAndNavigate(fullPcm, bestSoFar)` → kör final-classify på `bestSoFar`-fönstret → `ScanSource.Audio` → `NavigateToMatch`.

### Cancellation

`AudioScanViewModel.cancelRecording()`:
- `recorderHandle?.cancel()` — släng buffer
- Cancellar alla inflight inferens-jobs via `recordingJob?.cancel()`
- State → `Idle`

`AudioScanScreenHost` `DisposableEffect(lifecycleOwner)` ON_PAUSE → `vm.cancelRecording()` om vi är i Recording-state.

## UI

### Layout (vertikalt, top → bottom i `AudioScanScreen`)

```
JournalIntro (eyebrow + headline + sub)                  oförändrad
Caveat marginalia-rad                                    text uppdateras (se Strings)
─ spacer 32dp ─
WaveformBars  — 48 staplar, AccentCopper, 80dp hög       NY
─ spacer 24dp ─
RecordingMicButton  — 64dp copper-cirkel                 NY (ersätter IdleMic)
─ spacer 12dp ─
Timer "0:07"  — Caveat, MarginaliaInk, ~14sp             NY (bara i Recording)
─ spacer 8dp ─
CTA-text  — Caveat, AccentCopper                         text varierar per state
```

### `WaveformBars` (uppdatering av befintlig komponent)

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/WaveformBars.kt`:

| Egenskap | Idag | Nytt |
|---|---|---|
| `barCount` default | 12 | 48 |
| Bar-färg | `MarginaliaInk` | `AccentCopper` med `alpha = 0.85f` |
| Height | 60dp | 80dp |
| Bar-spacing | 5dp | 3dp |
| Corner-radius | 2dp | 2dp (oförändrad) |
| Pulse | sin-phase + RMS | sin-phase + RMS (oförändrad logik, mer levande med 48 staplar) |
| Frozen-läge | 600ms tween | 600ms tween (oförändrad) |

Skärmbredds-budget: 48 × 3dp + 47 × 3dp = 285dp. På 360dp-bred skärm med 24dp horisontell padding (= 312dp tillgängligt) → ✓.

### `RecordingMicButton` (NY komponent)

`composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/RecordingMicButton.kt`:

```kotlin
@Composable
fun RecordingMicButton(
    state: MicButtonState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)

enum class MicButtonState { Idle, Recording, RecordingDisabled, Analyzing }
```

| State | Visuell | Tap-behavior |
|---|---|---|
| **Idle** | 64dp cirkel, `AccentCopper`-fyllning, vit `●` (Text, font-size 20sp), shadow `0.dp 4.dp 12.dp AccentCopper.copy(alpha=0.3f)` (via `Modifier.shadow`) | `onClick` → start recording |
| **Recording** | Samma cirkel + vit `■` glyph + pulse-ring (`Modifier.scale(animatedScale)` från 1.0 → 1.15 → 1.0, 1200ms, infinite) | `onClick` → stop recording |
| **RecordingDisabled** | Samma som Recording men ingen pulse-ring + `alpha = 0.5f` | Klick ignoreras (state < 3s) |
| **Analyzing** | Cirkel `alpha = 0.4f`, `■` glyph, ingen pulse-ring | Klick ignoreras |

Shadow ↔ website-CSS: website har `box-shadow: 0 4px 12px rgba(168, 85, 45, 0.3)`. Compose `Modifier.shadow(elevation = 4.dp, shape = CircleShape, ambientColor = AccentCopper, spotColor = AccentCopper)` ger närliggande effekt. Vi använder `ambientColor`/`spotColor`-paramatrar för API 28+ för att färga skuggan kopparröd.

`RecordingDisabled` triggas av `state.elapsedMs < 3000`.

### Timer

```kotlin
@Composable
private fun RecordingTimer(elapsedMs: Long) {
    val seconds = (elapsedMs / 1000).toInt()
    val mm = seconds / 60
    val ss = seconds % 60
    Text(
        text = "$mm:${ss.toString().padStart(2, '0')}",
        fontFamily = rememberCaveat(),
        color = MarginaliaInk,
        fontSize = 14.sp,
    )
}
```

Visas bara när `state is AudioScanState.Recording`. Inte i Analyzing eller Idle.

### Strings (`composeResources/values/strings.xml` + `values-sv/strings.xml`)

| Nyckel | EN | SV | Status |
|---|---|---|---|
| `audio_scan_marginalia_top` | "Hold your phone still and *let it sing*" | "Håll telefonen stilla och *låt den sjunga*" | UPDATE (idag säger "håll i 3s") |
| `audio_scan_cta_hold` | — | — | **DELETE** |
| `audio_scan_cta_idle` | "Tap to listen" | "Tryck för att lyssna" | NY |
| `audio_scan_cta_recording` | "Listening — tap to stop" | "Lyssnar — tryck för att stoppa" | NY |
| `audio_scan_analyzing` | "Analyzing…" | "Analyserar…" | OFÖRÄNDRAD |
| `audio_scan_listening` | — | — | **DELETE** (ersätts av `audio_scan_cta_recording`) |

`AudioScanScreen.IdleMic`-komponenten rivs och ersätts av direktanrop av `RecordingMicButton(state = MicButtonState.Idle, onClick = onStartRecording)`. `RecordingView`-komponentens countdown `(3000 - state.elapsedMs)`-text ersätts av `RecordingTimer(state.elapsedMs)`.

## Website-synk

### `website/src/components/Listen.astro`

Ändra:
- `<div class="hint">hold 3s</div>` → `<div class="hint">{t.listen.hint}</div>` (läs från copy)
- Inga andra struktur-ändringar — bar-count, färger, mic-button ser redan ut som spec'n säger appen ska se ut.

### `website/src/content/copy.en.json` + `copy.sv.json`

```jsonc
// copy.sv.json
"listen": {
    "eyebrow": "ELLER TRYCK PÅ MIKEN · PÅ ENHETEN",
    "headline": "Hör den. *Namnge* den.",
    "sub": "Tryck. Lyssna. Fågeln namnger sig själv.",
    "body": "Tryck på inspelningsknappen. Samma AI på enheten lyssnar tills den känner igen fågeln — eller tills du trycker stopp. Inget internet behövs.",
    "hint": "tryck för att lyssna"
}

// copy.en.json
"listen": {
    "eyebrow": "OR TAP THE MIC · ON-DEVICE",
    "headline": "Hear it. *Name* it.",
    "sub": "Tap. Listen. The bird names itself.",
    "body": "Tap the record button. The same on-device AI listens until it recognises the bird — or until you tap stop. No internet needed.",
    "hint": "tap to listen"
}
```

### Playwright + i18n parity

`website/tests/smoke.spec.ts` testar antagligen för "hold 3s"-strängen. Vi söker efter `hold 3s` / `3 sekunder` i tests och uppdaterar till de nya strängarna.

`website/tests/i18n-parity.spec.ts` verifierar att SV och EN har samma copy-nycklar — den fångar automatiskt om vi glömmer `hint` i ena språket.

## Test-strategi

### Unit-tests (`composeApp/src/commonTest/.../AudioScanViewModelTest.kt`)

| Test | Beskrivning |
|---|---|
| `auto-stops when confidence reaches 0.60 after 3s` | Mock classifier returnerar 0.65; verifiera Analyzing-state efter ~3s |
| `does not auto-stop before 3s passed` | Mock classifier returnerar 0.99 men vi har bara 2s samples; verifiera fortfarande Recording |
| `manual stop triggers final classify` | Recording → cancelRecording → Analyzing → NavigateToMatch |
| `60s cap triggers same flow as manual stop` | Driv klockan + recorder till 60 000 ms; verifiera Analyzing |
| `cancel mid-record returns to Idle` | startRecording → cancelRecording (before 3s) → Idle, classifier aldrig kallad |
| `bestSoFar tracks highest confidence across windows` | Windows 0.3 → 0.5 → 0.4 → 0.7 → bestSoFar = 0.7 |
| `inflight inference is skipped when previous still running` | Slow classifier (200ms latency) + 100ms stride → vissa windows hoppas, ingen exception |
| `final classify uses bestSoFar window` | Best-fönster = samples [16000..64000]. Verifiera att `classifier.classify` kallas med exakt det slicet vid stop |
| `final classify falls back to last 3s when bestSoFar is null` | Manuell stop vid 4s utan att första inferens hunnit slutföra → final-classify på samples [exp-48000..exp] |

### `FakeAudioRecorder` (NY, i `commonTest/.../audio/FakeAudioRecorder.kt`)

Driver `onChunk`-callback från en in-memory PCM-array. Tests kan styra "ge mig samples upp till N totalt" och "trigga `onCapReached`". Ersätter behov av Android-emulator i VM-tests.

### Recorder-tests (Android-instrumentation eller manuell)

`AndroidAudioRecorder.start()` testas inte unit — kräver riktig `AudioRecord`. Manuell verifiering täcker.

### WaveformBars

Compose Preview med RMS = 0, 0.5, 1.0 + frozen = true/false. Visuell verifiering.

### Manuella device-tester (dokumenteras i plan, screenshots krävs)

| Scenario | Förväntad utgång |
|---|---|
| Tystnad 60s | Cap triggar → NoBirdScreen |
| Spela koltrast-clip från högtalare | Auto-stop inom ~5-8s → MatchScreen med koltrast |
| Tryck stopp efter 2s | Knapp disabled, ingen state-ändring |
| Tryck stopp efter 8s | MatchScreen eller NoBirdScreen beroende på bestSoFar |
| Tryck back mid-record | Recorder cancels, ingen leak, state → Idle |
| Skärm lock mid-record | ON_PAUSE → cancel, ingen background-recording |

### Screenshots (i `docs/superpowers/screenshots/`)

- `listen-idle-2026-05-XX.png` — ny mic-button + 48 staplar i copper, statisk
- `listen-recording-5s-2026-05-XX.png` — 5s in, timer `0:05`, pulserande staplar
- `listen-recording-30s-2026-05-XX.png` — 30s in, timer `0:30`
- `listen-auto-stop-match-2026-05-XX.png` — Match-skärm post auto-stop
- `listen-manual-stop-nobird-2026-05-XX.png` — NoBirdScreen post manuell stop

## Definition of Done

1. `./gradlew build` grön (ktlint + detekt + JVM tests inklusive nya VM-tests)
2. AudioScanViewModelTest: alla 8 nya tester passerar
3. Alla 6 manuella device-scenarier verifierade
4. 5 device-screenshots committade
5. Website ändringar live på `birdy.community` (Vercel auto-deploy från `main`)
6. Website Playwright smoke + i18n parity grön
7. Tag `v0.9.0c-listen-open` (separat från `v0.9.0c-premium-content` — den taggen tillhör Plan 6b3)

## Filer som ändras

### App
- `shared/ml/src/androidMain/kotlin/se/birdy/ml/AndroidAudioRecorder.kt` — riv `record3s`, lägg till `start` + `RecorderHandle`-impl
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanViewModel.kt` — sliding-window-logik, bestSoFar, auto-stop
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanState.kt` — `Recording`-data class får `bestSoFar` + nytt `Top1`-typ
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/AudioScanScreen.kt` — riv `IdleMic`, ersätt `RecordingView`-countdown med Timer
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/WaveformBars.kt` — 48 default, AccentCopper, 80dp, 3dp spacing
- `composeApp/src/commonMain/kotlin/se/birdy/app/ui/audio/RecordingMicButton.kt` — NY
- `composeApp/src/androidMain/kotlin/se/birdy/app/ui/audio/AndroidAudioRecorderAdapter.android.kt` — anpassa till nya API
- `composeApp/src/commonMain/composeResources/values/strings.xml` + `values-sv/strings.xml` — string-uppdateringar
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/AudioScanViewModelTest.kt` — nya tester
- `composeApp/src/commonTest/kotlin/se/birdy/app/ui/audio/FakeAudioRecorder.kt` — NY

### Website
- `website/src/components/Listen.astro` — riv hardcoded "hold 3s"
- `website/src/content/copy.en.json` + `copy.sv.json` — uppdaterade copy + ny `hint`-nyckel
- `website/tests/smoke.spec.ts` — uppdatera strängassertions (om nödvändigt)

## Open questions / antaganden

- **Threshold-värde 0.60**: ärvt från Plan 7d. Om audio-accuracy-eval (post-launch follow-up #5) visar att 0.60 är fel värde justerar vi senare. Eval-data finns i `tools/ml-eval/audio_accuracy_report_2026-05-21.md`.
- **Pulse-ring runt mic-button under recording**: enkel scale-animation 1.0 → 1.15 → 1.0. Om det visuellt "stör" RMS-pulsade staplarna kan vi ta bort i polish-pass.
- **Tag-namn**: `v0.9.0c-listen-open` valt eftersom Plan 6b3 äger `v0.9.0c-premium-content`. Om planen körs som en T (task) under 6b3 istället för egen plan, faller separat tagg bort.
