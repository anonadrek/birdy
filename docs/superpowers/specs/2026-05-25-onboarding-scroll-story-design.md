# Onboarding v2 — scroll-driven story

> **Status:** Spec (klar för plan).
> **Datum:** 2026-05-25
> **Författare:** Albin + Claude
> **Berör:** `composeApp/.../ui/onboarding/`, Settings-skärm, `values{,-en}/strings.xml`
> **Ersätter:** Plan 7a-onboarding-flöde (3 sidor: Brand → Overview → Name)
> **Förutsatt:** Plan 7c Field Journal-tema är canonical (DM Serif + Caveat + paper-bg + StampSeal)

## Bakgrund

Nuvarande onboarding (Plan 7a, `v0.7.0a-foundation`) består av 3 statiska sidor i en `HorizontalPager`:

1. Wordmark + två body-paragrafer
2. JournalIntro + 4 FeatureRow (Listen/Archive/Lifelist/Badges)
3. JournalIntro + namn-input + CTA

Den fungerar tekniskt men:

- Innehållet säljer inte värdet — copy är poetisk men säger inte explicit vad appen gör för användaren.
- Allt är statiskt — inga animationer utöver pager-dots och swipe-transition.
- SV-strängarna `onboarding_p2_*_name` har en bugg: de säger "Listen/Archive/Lifelist/Badges" på engelska istället för svenska tab-namn (Identifiera/Arkiv/Lifelist/Märken).
- Onboarding visas bara en gång — om en användare vill se den igen finns ingen väg in.

Det vi vill bygga: en **scroll-driven story i Apple-stil** med 7 scener, hybrid copy (poetisk headline + konkret USP-sub) och hybrid visuella effekter (riktiga app-assets på huvudobjekt + abstrakt motion runt). Och en "Visa introduktion igen"-rad i Settings.

## Mål

- **Få nya användare att fastna** genom att tidigt visa konkret app-värde (foto-AI, audio-ID gratis, fält-dagbok, märken, privacy).
- **Behåll Field Journal-tonen** så att intro-flowet inte bryter mot resten av appens visuella språk.
- **Minimal teknisk skuld** — återanvänd existerande komponenter (`StampSeal`, `PlateFrame`, `JournalIntro`), inga nya externa deps (ingen Lottie).
- **Replay-bar från Settings** så att vi inte slänger 7 scens animation på en visning per användare.

## Icke-mål

- Lottie eller annat motion-graphics-bibliotek.
- Real kamera-preview eller real audio-uppspelning i intro.
- Föränderlig copy baserat på språkdetektering, tid på året eller A/B-flagg.
- Spara onboarding-progress för "resume where you left off".
- Auto-replay vid versionCode-bump.
- iOS-implementation (commonMain-kod är iOS-kompatibel; iOS-target landar i v2).

## Storyboard — 7 scener

Varje scen följer mönstret: **eyebrow (small caps) → JournalHeadline (DM Serif Italic med `*ord*`-accent) → sub-line (konkret USP) → visuellt huvudmotiv**. Sub-lines säljer; headlines håller Field Journal-poesin.

### Scen 1 — Hero

- **Eyebrow (SV):** `FÄLT-FÖLJESLAGARE · NO 1`
- **Eyebrow (EN):** `FIELD COMPANION · NO 1`
- **Headline (SV):** `*Birdy.*`
- **Headline (EN):** `*Birdy.*`
- **Sub (SV):** `Identifiera fåglar i fält — foto eller ljud, på enheten, utan internet.`
- **Sub (EN):** `Identify birds in the field — photo or sound, on-device, no internet.`
- **Visuellt:** Wordmark (existerande `files/branding/wordmark.png`) centrerat. Ingen separat app-ikon — wordmark + paper-bg ger Field Journal-känsla utan dubbel-branding.
- **Animation:** Vid scroll-enter fade:as wordmark upp (alpha 0 → 1 + Y-translation 16dp → 0, 500ms). Subtil per-char "skriv-in"-effekt via opacity-stagger (alternativ: keep det enkelt med fade-up — plan-implementer avgör utifrån device-test). Paper-dot-texture parallax (translation Y = pageOffset × 24.dp) följer scroll-fingret.

### Scen 2 — Foto

- **Eyebrow (SV):** `FOTO · NO 2`
- **Eyebrow (EN):** `PHOTO · NO 2`
- **Headline (SV):** `*Rikta*. Tryck. Stämpla.`
- **Headline (EN):** `*Aim*. Tap. Stamp.`
- **Sub (SV):** `On-device-AI känner igen 839 europeiska arter på sekunder.`
- **Sub (EN):** `On-device AI recognizes 839 European species in seconds.`
- **Visuellt:** Kamera-frame-mockup (Canvas: 4 hörn-bracketer + tunn crosshair-cirkel mitten, AccentCopper-ink) med en riktig plate-foto av **Talgoxe (`Parus major`)** centrerad inuti `PlateFrame`. Ovanpå, slammar `StampSeal` ner i "MATCH"-state med art-namnet ("Talgoxe").
- **Animation:** Vid scen-enter (`pagerState.currentPage == 1`):
  1. Crosshair-cirkel pulsar (scale 1.0 → 1.1 → 1.0, 600ms)
  2. Plate-foto fade:as in (0 → 1 alpha, 400ms, ease-out)
  3. `StampSeal` slammar in (scale 0 → 1.1 → 1.0 + rotation -8° → 0°, 500ms, spring-bounce)
  4. Subtil "ink-impact" — ornament-burst (`OrnamentRule`-ornament gör en kort scale-pop, 200ms)
- **Hardcoded species-id:** `parus_major`. Om Talgoxe-fotot visuellt failar mot paper-bg (för många färger): fallback `pyrrhula_pyrrhula` (Domherre).

### Scen 3 — Ljud

- **Eyebrow (SV):** `LJUD · NO 3`
- **Eyebrow (EN):** `SOUND · NO 3`
- **Headline (SV):** `Eller — *lyssna*.`
- **Headline (EN):** `Or — *listen*.`
- **Sub (SV):** `3 sekunders inspelning. Identifiera sången. Gratis, alltid.`
- **Sub (EN):** `3 seconds of recording. ID the song. Free, always.`
- **Visuellt:** Mic-ikon (Material `Icons.Filled.Mic`) i copper-ring (`AccentCopper.copy(alpha = 0.12f)` bg + 1dp `AccentCopper` border), 64.dp diameter. Under: animerad `WaveformBars` (16 vertikala Canvas-bars med sinus-baserad amplitud). Under den: art-namn "Talgoxe — 94%" + copper-pill "GRATIS" / "FREE".
- **Animation:** Vid scen-enter:
  1. Mic-ikon scale-up 0 → 1 (300ms)
  2. WaveformBars loop:as så länge `pagerState.currentPage == 2` (varje bar amplitud = `sin(time + barIndex × phase) × maxHeight`). Pausas vid scen-byte (`LaunchedEffect(pagerState.currentPage)`-disposal).
  3. 3-sek-countdown-ring runt mic-ikonen (sweep-arc 0° → 360° över 3s, AccentCopper, 3dp stroke). När 360° nås: ringen pulsar (alpha 1 → 0, 200ms).
  4. Art-namn + "94%" fade:as in (alpha + Y-translation 8dp → 0, 400ms)
  5. "GRATIS"-pill snäpper in (scale 0 → 1.05 → 1.0, 350ms, spring)

### Scen 4 — Fältboken

- **Eyebrow (SV):** `FÄLTBOKEN · NO 4`
- **Eyebrow (EN):** `THE JOURNAL · NO 4`
- **Headline (SV):** `Varje fynd — *en sida*.`
- **Headline (EN):** `Every find — *a page*.`
- **Sub (SV):** `Din samling växer med varje fågel. PDF-export när du vill.`
- **Sub (EN):** `Your collection grows with every bird. PDF export when you want.`
- **Visuellt:** En tom dagboks-page-mockup (paper-edge med subtle shadow, RoundedCornerShape 4dp, 280×360dp) med några stamp-imprints redan (3 st `StampSeal` i unlocked-state, semi-transparenta, sprida på sidan). Centrerat: en ny stämpel slår ner. Under stämpeln: handskriven date-marginalia "16 maj · Norra Djurgården" (Caveat, MarginaliaInk).
- **Animation:** Vid scen-enter:
  1. Dagboks-page glider upp (Y-translation 32dp → 0 + alpha 0 → 1, 500ms)
  2. Pre-existing stamps fade:as in en-och-en (stagger 100ms vardera)
  3. Ny stämpel slammar (scale 0 → 1.1 → 1.0 + rotation -8° → 0°, 500ms)
  4. Marginalia fade:as in per-char (typewriter-effekt, 30ms/char, total ~700ms för texten)

### Scen 5 — Märken

- **Eyebrow (SV):** `MÄRKEN · NO 5`
- **Eyebrow (EN):** `BADGES · NO 5`
- **Headline (SV):** `*Förtjäna* märken.`
- **Headline (EN):** `*Earn* badges.`
- **Sub (SV):** `Mängder av milstolpar att jaga. Håll svit levande.`
- **Sub (EN):** `Plenty of milestones to chase. Keep your streak alive.`
- **Visuellt:** En `StampSeal` i locked-state (grå) centrerad. Vid scen-enter flippar den till unlocked (full copper) med ornament-burst. Under: streak-räknare "🔥 7" (DM Serif Italic, 48sp) som ticker upp från 0.
- **Animation:** Vid scen-enter:
  1. Locked StampSeal står still 300ms (suspense)
  2. Flippar till unlocked: rotation 0° → 180° i Y-axel (`graphicsLayer { rotationY = ... }`) — halvvägs (90°) byts state från locked till unlocked, 500ms total
  3. Ornament-burst runt stämpeln (4 ❦-glyfer scale 0 → 1 + radial spread 16dp, 300ms efter flip)
  4. Streak-räknare animerar 0 → 7 (`StreakCounter` med ease-out, 800ms)
  5. 🔥-glyph pulsar (scale 1.0 → 1.15 → 1.0, loop var 1.5s så länge scenen är aktiv)

### Scen 6 — Privatliv

- **Eyebrow (SV):** `PRIVATLIV · NO 6`
- **Eyebrow (EN):** `PRIVACY · NO 6`
- **Headline (SV):** `*Stannar* på enheten.`
- **Headline (EN):** `*Stays* on your device.`
- **Sub (SV):** `Inga konton. Ingen molnsynk. Dagboken är din.`
- **Sub (EN):** `No accounts. No cloud sync. The journal is yours.`
- **Visuellt:** En enhets-ikon centrerad (Material `Icons.Outlined.Smartphone`, 96dp, MarginaliaInk). Ovanpå enhets-ikonen: en lås-ikon (`Icons.Filled.Lock`, 24dp, AccentCopper, top-right corner). Bakgrund: en "skydds-sköld"-outline (Canvas path) som ritar sig självt runt enheten. Vid sidan: en no-wifi-symbol (`Icons.Filled.WifiOff`, 32dp) som kryssas över med en streck.
- **Animation:** Vid scen-enter:
  1. Enhets-ikon fade:as in (300ms)
  2. Lås-ikon "klickar" stängd (scale 0 → 1.2 → 1.0, 400ms) + en kort haptic-pulse-look (no actual haptic)
  3. Skölds-outline ritar sig själv via path-animation (`Canvas` med `drawPath` + `PathMeasure`, 0% → 100% längd över 700ms)
  4. No-wifi-symbol fade:as in (200ms), sen en röd streck (subtle CopperBrick) ritar sig diagonalt över den (200ms)

### Scen 7 — Namn + CTA

- **Eyebrow (SV):** `DITT NAMN · NO 7`
- **Eyebrow (EN):** `YOUR NAME · NO 7`
- **Headline (SV):** `*Vem* skriver?`
- **Headline (EN):** `*Who* writes?`
- **Sub (SV):** `Namnet visas i dagboken.`
- **Sub (EN):** `Shown in your journal.`
- **Input placeholder (SV/EN):** `Albin` / `Alex`
- **Input helper (SV):** `Lämna tomt om du vill — vi kallar dig 'Fältornitolog'.`
- **Input helper (EN):** `Leave blank if you'd rather — we'll call you 'Field birder'.`
- **CTA (SV):** `*Börja* dagboken`
- **CTA (EN):** `*Begin* the journal`
- **Visuellt:** `JournalIntro` (eyebrow + headline + sub + ornament) + `OutlinedTextField` (Caveat) + helper-text + AccentCopper-knapp med `JournalHeadline` inuti.
- **Animation:** Endast fade-up av hela formen vid scen-enter (Y-translation 16dp → 0 + alpha 0 → 1, 400ms). **Inga discrete animations** — scenen ska vara stilla så användaren kan tappa input-fältet utan motion-distraktion.
- **CTA-tap:** Triggrar `OnboardingViewModel.complete()` → fallback-name "Fältornitolog" / "Field birder" om input tom → `prefs.setHasSeenOnboarding(true)` + `setUserName(resolvedName)` → `OnboardingUiState.Done`.

## Tech-arkitektur

### Scroll-mekanism: `VerticalPager`

```kotlin
val pagerState = rememberPagerState(pageCount = { SCENE_COUNT })  // 7
VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize().paperBackground()) { page ->
    val pageOffset = (pagerState.currentPage - page).toFloat() + pagerState.currentPageOffsetFraction
    when (page) {
        0 -> SceneHero(pageOffset)
        1 -> ScenePhoto(pageOffset)
        2 -> SceneAudio(pageOffset)
        3 -> SceneJournal(pageOffset)
        4 -> SceneBadges(pageOffset)
        5 -> ScenePrivacy(pageOffset)
        6 -> SceneName(nameInput, onNameChange, onComplete)
    }
}
```

**Val:** `VerticalPager` (`androidx.compose.foundation.pager`) framför `LazyColumn` + manuell snap.

- Inbyggd snap-fling — vi får Apple-style snap gratis.
- `currentPageOffsetFraction` ger oss en kontinuerlig 0..1-progress för "hur långt har scenen scrollats in/ut" — det är hela poängen med scroll-driven motion.
- Vertikal pager har funnits sedan Compose Foundation 1.4 och är väl-testad.
- Vi förlorar fri scroll-tempo-styrning (Pager snapper alltid till en scen). Det är acceptabelt — vi vill att varje scen får sin "completion".

### Per-scen animationer drivs av `pageOffset`

Varje scen-composable (utom scen 7) tar `pageOffset: Float` (-1..0..+1):

- `pageOffset = 0` → scenen är centrerad
- `pageOffset = -1` → scenen är ovanför viewport
- `pageOffset = +1` → scenen är under viewport

Kontinuerliga effekter (visibility, parallax) drivs direkt av `pageOffset`:

```kotlin
val visibility = (1f - pageOffset.absoluteValue.coerceIn(0f, 1f))
val parallaxY = pageOffset * 80.dp
```

Discrete events (StampSeal-slam, badge-pop, streak-count) triggas med `LaunchedEffect(pagerState.currentPage)` + `delay()` när scenens index blir aktiv:

```kotlin
LaunchedEffect(pagerState.currentPage == 1) {
    if (pagerState.currentPage == 1) {
        crosshairPulse.animateTo(1.1f, animationSpec = tween(600, easing = FastOutSlowInEasing))
        ...
    }
}
```

`Animatable<Float>` används för slam-animationer som behöver spring-bounce; `tween` för enkla fade/translation.

### Fil-layout

```
composeApp/src/commonMain/kotlin/se/birdy/app/ui/onboarding/
├── OnboardingScreen.kt           # huvudpager + scaffold (refactor från existerande)
├── OnboardingUiState.kt          # utöka MAX_PAGE_INDEX 2 → 6
├── OnboardingViewModel.kt        # + isReplay-flagg
├── scenes/
│   ├── SceneHero.kt              # scen 1
│   ├── ScenePhoto.kt             # scen 2
│   ├── SceneAudio.kt             # scen 3
│   ├── SceneJournal.kt           # scen 4
│   ├── SceneBadges.kt            # scen 5
│   ├── ScenePrivacy.kt           # scen 6
│   └── SceneName.kt              # scen 7 (innehåller existerande Page3Name-form, refactorerad)
└── components/
    ├── IntroSceneScaffold.kt     # eyebrow + JournalHeadline + sub + visualSlot
    ├── WaveformBars.kt           # animerade audio-bars (Canvas)
    ├── StreakCounter.kt          # rolling number 0 → target
    └── OfflineShield.kt          # path-animation Canvas
```

`IntroSceneScaffold` standardiserar layout för scen 1–6 (eyebrow uppe, JournalIntro mitt, visuellt huvudmotiv under). Scen 7 använder inte scaffolden (har egen form-layout).

### Komponenter — återanvändning vs nytt

**Återanvänds oförändrade:**

- `JournalHeadline`, `JournalIntro`, `BodyTextWithCaveatAccents`
- `StampSeal` (scen 2 + 4 + 5)
- `PlateFrame` (scen 2)
- `paperBackground()` Modifier
- `OrnamentRule`
- `rememberDmSerifDisplay()`, `rememberCaveat()`

**Nya komponenter:**

- `IntroSceneScaffold(eyebrow, headline, sub, visual, pageOffset)` — wrapper med fade + parallax på pageOffset
- `WaveformBars(active: Boolean, barCount: Int = 16, modifier: Modifier = Modifier)` — 16 vertikala Canvas-bars; vid `active = true` looper amplituden via `rememberInfiniteTransition`
- `StreakCounter(target: Int, trigger: Boolean, modifier: Modifier = Modifier)` — animerar 0 → target när `trigger = true`, DM Serif Italic
- `OfflineShield(progress: Float, modifier: Modifier = Modifier)` — Canvas path som ritar sig självt baserat på progress 0..1

**Inga nya externa deps.** Allt med Compose-`Animatable`, `Canvas`, `drawArc`/`drawPath`, `rememberInfiniteTransition`.

### `OnboardingViewModel`-ändringar

```kotlin
class OnboardingViewModel(
    private val prefs: UserPreferences,
    private val defaultFallbackName: String,
    private val isReplay: Boolean = false,        // NEW
) : ViewModel() {
    ...

    fun complete() {
        val current = _state.value
        if (current !is OnboardingUiState.Visible) return
        val trimmed = current.nameInput.trim()
        val resolvedName = trimmed.ifEmpty { defaultFallbackName }
        viewModelScope.launch {
            if (!isReplay) {                     // NEW
                prefs.setUserName(resolvedName)
                prefs.setHasSeenOnboarding(true)
            }
            _state.value = OnboardingUiState.Done
        }
    }

    private companion object {
        const val MAX_PAGE_INDEX = 6             // 7 pages: 0..6 (was 2)
    }
}
```

I replay-mode skriver `complete()` varken namn eller `hasSeenOnboarding`. Användaren har redan ett namn (kan ändra det via Settings → Profile separat); intention med replay är bara att titta på storyn igen.

### Replay-from-Settings

**Settings-ändring:** Ny rad **"Visa introduktion igen" / "Show intro again"** under "OM BIRDY"-sektionen, mellan "Webbplats" och "Privacy policy" (eller annan lämplig position — exakt placering avgörs av plan-implementer).

**Navigation:** Ny destination i `NavGraph` (`AppGate`/`MainNav`): `OnboardingReplay`. Routes till `OnboardingScreen` med `viewModel = OnboardingViewModel(prefs, fallbackName, isReplay = true)` och `onComplete = { navController.popBackStack() }`.

**AppGraph-ändring:** `OnboardingViewModel`-factory tar emot `isReplay: Boolean`-parameter (default false för first-run, true för replay).

### Skip-mekanik

Skip-knappen behålls i `TopEnd` med samma copy ("Hoppa över" / "Skip"). Den är alltid synlig på scen 1–6. På scen 7 är skip = same som CTA-knappen (`onComplete()` med fallback-namn) — acceptabelt.

I replay-mode kallas knappen **"Stäng" / "Close"** istället för "Hoppa över" / "Skip" — strängen `onboarding_close_replay` (listad nedan i strings-sektionen) väljs baserat på `isReplay`-flaggan.

## A11y & perf

- **TalkBack:** Varje scen får `Modifier.semantics { contentDescription = "${eyebrow}. ${headline}. ${sub}." }` på roten. Skipp-knappen har egen `contentDescription`. Discrete animations (stamp slam, badge pop) är dekorativa → `contentDescription = null`.
- **Reducerad motion:** Inget formellt OS-flagg-stöd i KMP/Compose idag. Mitigation: alla discrete animations är ≤1s och dämpade (alpha + svaga translation). Inga "explode/shake"-effekter som skulle vara vestibulärt jobbiga.
- **Loop-animationer (WaveformBars i scen 3, 🔥-pulse i scen 5):** Pausas när `pagerState.currentPage != sceneIndex`. `LaunchedEffect(pagerState.currentPage == sceneIndex)` startar/stoppar `rememberInfiniteTransition`.
- **Asset-pack & plate-foton:** `:asset-pack` är install-time module (Plan 6b3 T22) — alltid på enheten vid första app-start. Onboarding kan referera plate-foton utan staged-download-risk.
- **Snabb-scroll-perf:** Compose Pager disposar scen-composables som inte är synliga. Vi använder `pagerState.currentPage` (inte `pagerState.currentPageOffsetFraction`) som key för `LaunchedEffect` så att discrete events triggas exakt en gång per scen-besök.

## Strings — cleanup + nya

### Borttas (replaced av nytt scen-schema)

I `values/strings.xml` och `values-en/strings.xml`:

- `onboarding_journal_p1_body1`, `onboarding_journal_p1_body2`
- `onboarding_journal_p2_label`, `onboarding_journal_p2_headline`, `onboarding_journal_p2_sub`
- `onboarding_journal_p3_label`, `onboarding_journal_p3_headline`, `onboarding_journal_p3_sub`, `onboarding_journal_p3_cta`
- `onboarding_p2_archive_desc`, `onboarding_p2_archive_name`, `onboarding_p2_badges_desc`, `onboarding_p2_badges_name`, `onboarding_p2_lifelist_desc`, `onboarding_p2_lifelist_name`, `onboarding_p2_listen_desc`, `onboarding_p2_listen_name`
- `onboarding_p3_input_helper`, `onboarding_p3_input_placeholder`
- `onboarding_p1_breadcrumb`, `onboarding_p1_headline`, `onboarding_p1_body`, `onboarding_p1_sub`, `onboarding_p1_cta`
- `onboarding_p2_breadcrumb`, `onboarding_p2_headline`, `onboarding_p2_cta`
- `onboarding_p3_breadcrumb`, `onboarding_p3_headline`, `onboarding_p3_premium_label`, `onboarding_p3_cta`

(`onboarding_skip` och `onboarding_p3_fallback_name` behålls — använda av ViewModel + skip-knapp.)

### Nya strängar (21 scen-strängar + 4 utility + 1 Settings = 26 per locale)

```
onboarding_s1_eyebrow / _headline / _sub
onboarding_s2_eyebrow / _headline / _sub
onboarding_s3_eyebrow / _headline / _sub
onboarding_s4_eyebrow / _headline / _sub
onboarding_s5_eyebrow / _headline / _sub
onboarding_s6_eyebrow / _headline / _sub
onboarding_s7_eyebrow / _headline / _sub
onboarding_s7_input_placeholder
onboarding_s7_input_helper
onboarding_s7_cta
onboarding_close_replay   # "Stäng" / "Close" — visas i replay-mode istället för skip
settings_show_intro_again # ny rad i Settings → OM BIRDY
```

Concrete content för alla 26 × 2 locales = **52 strängar**. Exakta värden listas i storyboard-avsnittet ovan; plan-implementer ansvarar för att lägga in dem i `values/strings.xml` + `values-en/strings.xml`.

## Testing

- **Unit test (`OnboardingViewModelTest.kt`):** utöka existerande tester:
  - `MAX_PAGE_INDEX` är nu 6 (var 2). `setPageIndex(7)` ska coercas till 6. `setPageIndex(-1)` → 0.
  - Nya tester för `isReplay = true`: `complete()` skriver INTE `prefs.setUserName()` eller `prefs.setHasSeenOnboarding()`. State blir `Done` ändå.
  - Nya tester för `isReplay = false`: existerande beteende.
- **Inga UI-unit tests för scen-composables.** Animationer är svåra att unit-testa meningsfullt; vi förlitar oss på device-verify (Plan 4b/6a-pattern).
- **Device-verify på SM-S918B:**
  1. **Färsk install** → app öppnar onboarding → scrolla genom alla 7 scener → screenshot varje scen (`adb shell screencap -p /sdcard/...`) → CTA → main-screen visas
  2. **Skip på scen 3** → completes med fallback-namn ("Fältornitolog") → main-screen
  3. **Settings → "Visa introduktion igen"** → onboarding visas → completes (CTA eller "Stäng") → tillbaka till Settings utan att skriva om hasSeenOnboarding (verifiera med `adb shell run-as se.birdy.android cat ...datastore/preferences.preferences_pb` att `has_seen_onboarding=true` är oförändrat)
  4. **Snabb-scroll 0→6 utan paus** → inga frame-drops, inga crashes (visuell inspektion + logcat ingen ANR)
- **Screenshots:** 7 scen-screenshots + 1 Settings-replay-screenshot = **8 totalt** i `docs/superpowers/screenshots/onboarding-v2/`.

## Risk & öppna frågor

- **Risk: Plate-foto av Talgoxe visuellt fail mot paper-bg.** Mitigation: bestäms vid device-verify (T-task för plan). Fallback Domherre.
- **Risk: 7 scener känns långt vid replay.** Acceptans: skip-knappen finns alltid. Användare som inte vill se hela kan stänga.
- **Öppen fråga: ska "Visa introduktion igen"-raden i Settings ha en icon-prefix?** Settings-rader idag är blandade (vissa har icons, vissa text-only). Plan-implementer avgör i samband med Settings-redesignen — ingen blocker.
- **Öppen fråga: scen 5 headline säger "Mängder av milstolpar" — bör vi ha en exakt siffra (t.ex. "Över 55 milstolpar")?** Acceptans: behåll "Mängder av" till v1.0; om vi vill ha hård siffra senare bumpar vi strängen post-launch (badge-katalogen växer ändå).

## Acceptanskriterier

Spec är komplett implementerad när:

- [ ] 7 nya scen-composables existerar i `ui/onboarding/scenes/`
- [ ] 4 nya hjälpkomponenter existerar i `ui/onboarding/components/`
- [ ] `OnboardingScreen.kt` använder `VerticalPager` med `SCENE_COUNT = 7` och pageOffset-driven animationer
- [ ] `OnboardingViewModel` har `isReplay`-flagg som skippar DataStore-writes
- [ ] Settings har ny rad "Visa introduktion igen" som routes till replay-mode
- [ ] Alla 52 nya/ändrade strängar finns i `values/strings.xml` + `values-en/strings.xml`
- [ ] Alla borttagna strängar (listade ovan) är raderade från båda locales
- [ ] `OnboardingViewModelTest` täcker `MAX_PAGE_INDEX = 6` + `isReplay = true/false`
- [ ] 8 device-screenshots existerar i `docs/superpowers/screenshots/onboarding-v2/`
- [ ] `./gradlew :androidApp:installDebug` + `ktlintCheck` + `detekt` + `:composeApp:testDebugUnitTest` är gröna
- [ ] versionCode bumpas (v1.0.1 är i progress med 114 — denna landar i 115 eller högre)
