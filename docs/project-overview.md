# Birdy Bird Scanner — projektöversikt

> Självständigt dokument avsett som "project knowledge" i Claude.ai web. Sammanfattar vad Birdy är, var vi står, hur vi jobbar och vilka tekniska val som styr arbetet. När detta används i webben finns ingen direkt access till repo — alla nedanstående filsökvägar är till mitt lokala repo `C:\Users\abbea\dev\birdy-bird-scanner` (GitHub: `https://github.com/anonadrek/birdy`, branch `main`).

---

## 1. Vad är Birdy?

AI-driven Android-app för fågelidentifiering. Tre kärnflöden:

1. **Skanna med kamera** — realtidsklassificering i live preview (foto) och 3-sekunders inspelning (audio).
2. **Uppslagsverk** — 839 europeiska arter med plate-illustrationer, fakta, säsongs- och migrationsdata.
3. **Fältdagbok** — sparar fynd, bygger life list, delar ut badges, exporterar PDF.

**Namn / brand:** "Birdy" — en intim, kunnig följeslagare i fält. Inte "BirdNet+++". Visuellt språk är en blandning av modern Mossbädd-grön app-känsla och en pappersbaserad **Field Journal** med kursiv DM Serif Display + handskriven Caveat-marginalia.

**v1-scope (det vi just nu shippar):** Android-only, Norden/Europa, on-device ML. Karta + moln-sync + community + iOS = v1.5/v2/v3.

**Användargrupp:** Bred två-lager — nybörjare som vill lära sig OCH entusiaster som vill ha hjälp i fält. Inte sliten "expertapp" — varm pedagogisk inramning runt seriös ML.

---

## 2. Status (2026-05-26)

- **v1.0.0 är taggad och uppladdad till Play Console Internal Testing** (versionCode 113, versionName 1.0.0). All v1-scope inne.
- **v1.0.1 påbörjad men ingen AAB byggd än** (versionCode 114, versionName 1.0.1). Commit `3201e0a` + tag `v1.0.1` pushad. Innehåller Identify-tab + back-pil + tab-tap-pop fixes från audit. Fler fixes förväntas innan ny AAB.
- **v1.0.2 — Onboarding v2 i flight** (versionCode 115, versionName 1.0.2). 3-sidors Plan 7a-onboarding ersatt av 7-scens scroll-driven story (Hero/Foto/Ljud/Fältboken/Märken/Privatliv/Namn) via `VerticalPager` + `pageOffset`-driven animationer. Tasks 1–16 + 18 klara, **T17 device-verify pågår på SM-S918B**. 7 scen-screenshots tagna; replay-mode + perf + Settings-screenshot återstår.
- **v1.1 Phase A — plan-ready, redo att bygga.** Spec + plan klara (commits `c0b26b1`, `66d4b04`). Implementation startar i ny session via `subagent-driven-development`.
- **Branch `main`:** lokala edits i `androidApp/build.gradle.kts` + `ListenLauncherViewModel.kt` (onboarding v2-arbete). Onboarding v2-plan: `docs/superpowers/plans/2026-05-25-onboarding-v2-scroll-story.md`.
- **Nästa milstolpe:** Slutför Onboarding v2 device-verify → bygg ny AAB (114 eller 115) → Closed Testing (14 dagar obligatoriskt) → Production launch på Google Play.

### Vad som hände senaste veckorna (sammanfattat)

- **Onboarding v2 (2026-05-25).** 7-scens `VerticalPager`-baserad story med hybrid Field Journal-copy (poetisk DM Serif-headline + konkret USP-sub). 4 nya komponenter: `IntroSceneScaffold`, `WaveformBars`, `StreakCounter`, `OfflineShield`. Settings → "Visa introduktion igen" routes till replay-mode (`isReplay = true` skippar DataStore-writes). **Device-verify trap:** `installDebug` lägger ut till `se.birdy.android.debug` (inte `se.birdy.android`); StampSeal-Column shiftade cirkeln 9dp upp från Box-center → fix: kamera-brackets + crosshair + slam-stamp inuti PlateFrame's image-slot, `name = null` på StampSeal så bara cirkeln renderas.
- **Plan 6b3 (Premium content) — DONE.** PDF-export av fältdagboken (`JournalPdfRenderer` med titel/stats/arter/badges/colophon-sidor + Android share-sheet), Season Statistics-skärm (Canvas-baserade bar/line/donut-chartar, noll dependencies), 10 nya premium-badges, `BadgeStringMap` (40 strängar SV+EN). Reducerade base APK från ~300 MB till **136 MB** via WebP-migration av plate-foton + `:asset-pack` install-time-modul för TFLite-vikterna.
- **BirdNET-licensbeslut (2026-05-22).** BirdNET-Lite-modellen är **CC BY-NC-SA 4.0 (NonCommercial)** — får inte gate:as bakom Premium. Option A vald: audio-ID är gratis-feature. PremiumGate rivet ur `ListenLauncher`. Premium-intäkter står helt på Plan 6b3-features (PDF, stats, badges) som vi byggt själva.
- **Audio-as-premium-städning (2026-05-23).** PremiumScreen + Play Store-listings hade kvar gammal "Listen to bird song"-formulering som premium — efterskärver från innan Option A-vändningen. Reddit-feedback fångade. Fixat i `ee466cf`: features 4→3, store-listing SCAN-sektion utökad med audio + "free, always", PREMIUM-sektion städad. versionCode 112→113.
- **Pre-release audit 2026-05-23.** 7-agent audit av v1.0.0; alla kod- + dokument-fixar pushade. Kvar = H1/M1/M2 manuella Play Console-steg under upload-flowet. Lärdom: **verifiera audit-fynd mot kod innan fix** — 4 av 21 fynd var fel-premiss.
- **Brand refresh.** Ny launcher-ikon + Compose splash med wordmark. Webb fick fågel-app-ikon i hero med fade-up + Glimpse-carousel-sektion med 6 Play Store-screenshots.
- **Launch-period premium-öppen.** `PREMIUM_OPEN_FOR_LAUNCH=true` (i `androidApp/build.gradle.kts`) gör att `MainActivity` hardcodar `premiumOverride = Active(LIFETIME)` för alla användare. Anledning: testarna ska få komplett v1.0-upplevelse, Billing v8 IPC-verify är defer:ad till Internal Testing. Flippas till `false` när Billing-flowet är runtime-verifierat.

---

## 3. Plan-of-plans

Hela appen har byggts som en serie sekventiella planer, var och en med plan-doc i `docs/superpowers/plans/`, taggad milstolpe och device-screenshots.

| # | Plan | Tag |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI | `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton (5 arter) | `v0.2.0a-pipeline` |
| 2b | Content backfill (5 → 839 arter) | `v0.2.0-content` |
| 3 | Encyclopedia (browse + species profile) | `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + 3 fps CameraX) | `v0.4.0a-camera-ui` |
| 4b | Real TFLite (AIY Birds V1, 965 klasser, ~14ms) | `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | `v0.5.0a-diary` |
| 5b | Gamification (25 badges, streaks, unlock-queue) | `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding, Settings | `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | `v0.7.0b-screens` |
| 7c | Field Journal redesign — DM Serif + Caveat + paper-bg + StampSeal | `v0.7.0c-field-journal` |
| 7d | Match-flow — threshold-logik, Match/Disambig/NoBird-screens | `v0.7.0d-match-flow` |
| 7e | Premium tier — PremiumScreen + per-tab teasers + cold-start modal | `v0.7.0e-premium` |
| 6a | Foundation — UX-polish + release-mekanik (R8, signing, icon, a11y) | `v0.8.0-rc1` |
| 6b1 | Billing v8 + launch-prep (PremiumBillingClient + Restore Purchases) | `v0.9.0a-billing` |
| 6b2 | Audio-ID via BirdNET-Lite (3s rec + FlexRFFT TF Select op) — free-tier | `v0.9.0b-audio` |
| 6b3 | Premium content (PDF-export + season-statistics + 10 fält-märken) | `v0.9.0c-premium-content` + `v1.0.0` |
| W | Marketing-website (Astro + Vercel + birdy.community + /legal/) | live |
| v1.0.1 | Audit-fixes (Identify-tab + back-pil + tab-tap-pop) | `v1.0.1` (in progress, ingen AAB än) |
| v1.0.2 | Onboarding v2 — 7-scens scroll-story (VerticalPager) | in flight, T17 device-verify pågår |
| v1.1 Phase A | (spec + plan klara, väntar på exekvering i ny session) | plan-ready |
| v1.2 Phase B | Migrating now-strip + söndag-veckorecap | deferred — brainstorma efter Phase A landat |

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd: `./gradlew build` ska gå grönt på `main`.

---

## 4. Tekniska val

### App-stack
- **Kotlin Multiplatform + Compose Multiplatform.** Android primär; iOS-skelett finns för framtida iOS-launch (v2).
- **DB:** SQLDelight 2.x med Flow-baserade queries.
- **Kamera:** CameraX `ImageAnalysis` på 3 fps, auto-throttle till 1.5 fps vid p95 > 333 ms inference.
- **Audio:** 48 kHz mono PCM_16 via `MediaRecorder.AudioSource.UNPROCESSED` med graceful fallback till `VOICE_RECOGNITION`. 3-sekunders push-to-record → OGG/Opus output.
- **Billing:** Google Play Billing v8. `PremiumBillingClient` är expect/actual så core-koden är platform-agnostic. RSA SHA1-signature-verify via Play Licensing public key embeddad i BuildConfig.
- **Distribution:** AAB via Play Asset Delivery. `:asset-pack` install-time-modul för TFLite-vikterna (~150 MB) håller base APK på 136 MB.
- **Språk:** SV + EN, Sverige först. Alla UI-strängar via `compose-resources` (Res.string-API).
- **CI:** GitHub Actions med ktlint 12.1.2, detekt 1.23.7, unit tests, assembleDebug.

### ML-stack
- **Foto:** TensorFlow Lite + AIY Birds V1 (uint8-quantized MobileNetV2, 965 klasser). På Galaxy S23 Ultra ≈ 14 ms/inference. Filtrerat till 839 europeiska arter via `species_list.yaml`.
- **Audio:** BirdNET-Lite v2 (CC BY-NC-SA 4.0 — viktigt licensbeslut, se §7). Kräver `tensorflow-lite-select-tf-ops:2.16.1` för FlexRFFT TF Select op (utan denna failar node 29 "Failed to prepare").
- **Match-flow:** confidence-thresholds routar till `Match` / `Disambig` (top-3 nära) / `NoBird`-screen. Implementerat i Plan 7d.
- **Migrations- och säsongsdata:** statisk per art i v1 (YAML i `shared/content/`). Geografi-baserad filtrering kommer post-v1.

### Webb
- **Marketing-site:** Live på `https://birdy.community` (Vercel auto-deploy från `main`).
- **Stack:** Astro 5 + Tailwind v4 + `@astrojs/sitemap` + `marked` + Playwright smoke-tests (7 tester) + i18n parity check (SV/EN).
- **Legal:** `/legal/{privacy,terms,data-safety}/` byggs från markdown i `website/src/content/`.
- **Setup-gotcha:** Vercel **Root Directory MÅSTE vara `website`** — annars ENOENT på `package.json`.

---

## 5. Var saker ligger i repot

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Internal Testing hand-off runbook | `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md` |
| Play Store-artefakter (markdown) | `docs/play-store/{privacy-policy,terms,store-listing-{sv,en},data-safety-form}.md` |
| Website källkod | `website/` |
| Launch-research | `docs/superpowers/research/2026-05-15-play-store-launch/` + `2026-05-20-play-store-audit.md` |
| Android-app-modul | `androidApp/` |
| Compose Multiplatform UI | `composeApp/` |
| Shared domain/ml/data | `shared/domain/`, `shared/ml/`, `shared/data/`, `shared/content/` |
| TFLite-modeller (asset pack) | `asset-pack/src/main/assets/` |

---

## 6. Visuellt språk

### Field Journal-tema (canonical app-wide från Plan 7c, locked 2026-05-10)

**Färgtokens** (i `composeApp/.../ui/theme/Color.kt`):

| Token | Hex | Roll |
|---|---|---|
| PaperBg / PaperEdge | `#EFE7D6` / `#E5DCC7` | Pappersbakgrund + texture |
| MarginaliaInk | `#3F4F30` | Caveat-text, sub-lines (WCAG AA-bumpad i Plan 6a) |
| AccentCopper | `#A8552D` | CTA, aktiv tab, stat-siffror, copper-pills |
| StampNavy | `#1F3A5F` | StampSeal-states |
| HeroMossMid / Deep / Shadow | `#5C6E48` / `#3F4F30` / `#2A3520` | Mossgrön gradient (Listen/Premium hero) |

**Typografi:**
- `DM Serif Display Italic` för rubriker. Komponent `JournalHeadline` parsar `*ord*` → renderar segmentet i Caveat-italic med lätt rotation (handskriven accent).
- `Caveat` för marginalia och sub-lines.
- `Inter` (system sans) för body.
- Fonts bundlade via `compose-resources` (`rememberDmSerifDisplay()` / `rememberCaveat()` i `Type.kt`).

**Layout-element:**
- `Modifier.paperBackground()` med dot-texture som default-bas.
- `JournalIntro` (eyebrow + JournalHeadline + ornament + sub-line).
- `StampSeal` med locked/in-progress/unlocked-states.
- `PlateFrame` (naturalist-foto-frame).
- `OrnamentRule` (❦ + horisontellt streck).

### Mossbädd-paletten (legacy, fortfarande använd punktvis)

Främst HeroMoss-gradient och AccentCopper. Sammanfattat i auto-memory `visual_language_birdy_v1.md`.

---

## 7. Viktiga beslut & ramar

- **Scope v1.0 är låst:** Skanna (foto + audio) + uppslagsverk + dagbok + gamification + premium tier. Karta + cloud-sync = v1.5. Inte expanderas mitt i closed testing.
- **Geografi:** Norden/Europa, 839 arter. ML-modellerna är globala men content filtrerar.
- **Privacy-löfte:** "Almost nothing collected, data stays on phone." Verifierat i 2026-05-20 fältrevision (6 agents). **Inte bryt detta utan diskussion.** All data ligger lokalt i SQLDelight-DB; ingen cloud-backend för inference.
- **AI:** On-device. Migrationsdata + sannolikhet är art-nivå statisk i v1.
- **BirdNET-Lite-modellen är CC BY-NC-SA 4.0 (NonCommercial).** Audio-ID är gratis-feature i v1.0. **Om vi någonsin lägger något bakom Premium som rör BirdNET → licensbrott.** Premium = endast Plan 6b3-features (PDF/stats/badges) som vi byggt själva.
- **Solo-utvecklare:** En person bygger via Claude Code. Granskning sker av användaren mellan tasks. Tvåstegs-review (spec → kvalitet) körs alltid mellan tasks.

---

## 8. Arbetsflöde med Claude Code

### Hur en session startar
1. "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. Statusöversikt: kolla `git log` + senaste commit.
3. Bestäm nästa steg utifrån status.

### När superpowers används
- **Brainstorming, ny plan, plan-execution med review** → `superpowers:brainstorming` / `:writing-plans` / `:subagent-driven-development`.
- **Vanliga frågor, snabba bugfixar, mindre refactoring** → bara prata; ingen skill.

Tumregeln: större än ett samtal eller kräver disciplin (TDD, plan-tracking) → skill. Annars inte.

### Modell-strategi
| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 |
| Implementer-subagents i subagent-driven-development | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

### Autonomi
"Don't ask me for permission to run anything." Commits, push, gradle, file-edits enligt plan körs utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). Undantag: blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (Play Console, Vercel UI) — där rapporteras status.

### Kommunikationsspråk
Användaren skriver och får svar på **svenska**. Kod och commit-meddelanden på engelska enligt etablerad konvention i repo.

---

## 9. Pending follow-ups (post-launch)

1. **GitHub Pages teardown:** In-app + store-listing URL:er är redan migrerade till `birdy.community/legal/` (verifierat 2026-05-24). I repo Settings → Pages, sätt Source till "None". Bekräfta också Play Console-UI:ns Privacy/Terms-fält när nästa AAB laddas upp.
2. **Email migration:** Sätt upp `feedback@birdy.community` under closed testing. Bridge nu = `albin@abrahamssons.se`. När birdy.community-mailen är live: byt tillbaka i `website/src/content/copy.{en,sv}.json` (FAQ + footer) + alla markdown-filer i `docs/play-store/`.
3. **Billing v8 IPC runtime-verify:** Purchase-flow + Restore Purchases + Active(YEARLY/LIFETIME)-state-flip. Kräver Internal Testing-app entry + in-app products + license testers i Play Console. **Blockerar inte launch** — `PREMIUM_OPEN_FOR_LAUNCH=true` ger alla LIFETIME-premium under testperioden. Flippa till `false` när Billing är verify:ad och vi vill aktivera monetization.
4. **Audio accuracy eval:** Kräver xeno-canto API v3 key. Pipeline klar i `tools/ml-eval/audio_accuracy_report_2026-05-21.md`.
5. **AB-flytt:** Account Transfer av Play Console till AB-bolaget när det är registrerat.
6. **SV legal-översättningar:** Om Sverige-trafik växer, mirror `/sv/legal/...` med översatta markdown-filer.
7. **Plan 6a T8/T9 device-screenshots:** `08-match-with-inline-note` + `09-disambig-save-as-unknown` saknas (kräver deterministisk match-flow ej driveable via ADB).

---

## 10. Plan framåt

### Närmast (innan production-launch)

1. **Slutför Onboarding v2 device-verify** (T17 pågår på SM-S918B). Replay-mode + perf + Settings-screenshot kvar.
2. **Bygg ny signerad AAB** (versionCode 114 eller 115 beroende på vad som hinns med innan upload) och ladda upp till Play Console Internal Testing.
3. **Closed Testing — 14 dagar obligatoriskt** med license testers.
4. **Production launch på Google Play.**
5. **Direkt efter launch:** flippa `PREMIUM_OPEN_FOR_LAUNCH=false` när Billing v8 IPC är runtime-verifierad → aktivera monetization.

### v1.1 — Phase A (spec + plan klara, väntar på ny session)

Spec + plan klara (commits `c0b26b1`, `66d4b04`). Implementation startar via `superpowers:subagent-driven-development` i fräsch session. Start-prompt sparad i auto-memory `project_v1_1_phase_a_ready.md`.

### v1.2 — Phase B (deferred)

Migrating now-strip + söndag-veckorecap. Brainstorma efter Phase A landat hos closed-testarna med retention-data. Auto-memory: `project_v1_2_phase_b_hooks.md`.

### Geografisk expansion (lågsiktig huvudtrack)

ML-modellerna är redan globalt tränade — expansionsjobbet sitter i **content-pipeline** (YAML + plate-foto per art), **regional migrations-/säsongsdata**, **on-demand asset packs** och **fler språk**.

- **v1.0 — Norden/Europa (839 arter)** ← current
- **v2 — "Asien + hela Europa" + iOS-launch.** Östasien/Indien först. iOS-arbetet = Compose Multiplatform-iOS-target + SwiftUI-shim för plattforms-API:er (kamera, audio, StoreKit istället för Play Billing, share-sheet, file-export).
- **v3 — "Hela världen."** Alla återstående kontinenter; full content-skalning + språkstöd.

### Parallella feature-spår (inte version-bundna)

- **"Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. `Observation`-schemat har redan nullable `latitude` / `longitude` / `location_label` från Plan 5a — ingen migration behövs.
- **"Community":** Delning av fynd, kommentarer, flöde, moderering.
- **Övrigt:** Quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

---

## 11. Trap-katalog (vanliga repeterande buggar)

Saker som har bitit oss mer än en gång:

- **`:androidApp` saknar transitiva deps från `:composeApp`.** composeApp använder `implementation()` inte `api()`, så varje ny shared/library-referens måste få egen `implementation()` i `androidApp/build.gradle.kts`.
- **compose-resources unescape:ar inte Android `\'`** — använd raw `'` eller Unicode `’` (U+2019).
- **compose-resources processar inte `%%` som `%`-escape** — använd `%1$s` och passa pre-formatterad `"${value}%"` från Kotlin call-site.
- **`ImageProxy.imageInfo.timestamp` returnerar nanos sedan boot, inte Unix-epoch** — använd `System.currentTimeMillis()` i CameraX-analyzer.
- **Hardcoded localized strings bryter andra locale** — alltid `stringResource(Res.string.xxx)`, aldrig `"spara 60%"` direkt i Kotlin.
- **ADB-tap y < 300 kan trigga notification-drawer** — använd `KEYCODE_BACK` för recovery + `uiautomator dump` för exact bounds.
- **Quality-review måste köra `:androidApp:installDebug` + device-test** — `:composeApp:assembleDebug` ensam missar manifest/dep-trap för Android-screens.
- **FlexRFFT-crash i TFLite-audio** — kräver `tensorflow-lite-select-tf-ops:2.16.1` dep, annars failar node 29. Diagnostisk logging > catch-all `Throwable`-swallow.
- **Vercel `npm install` ENOENT** — Root Directory måste vara `website` (inte `/website`, inte tomt).
- **BirdNET-Lite-modellen är CC BY-NC-SA (NonCommercial)** — får ALDRIG gate:as bakom Premium.
- **Compose tap-miss debug protocol:** när ett element renderas men tap missar, kör `uiautomator dump` FÖRST. Missing node ⇒ z-order. Node present ⇒ inset/overlay. Inte pixel-scanna.
- **Verify audit-rapporter mot kod innan fix.** 4 av 21 audit-fynd 2026-05-23 var fel-premiss. Alltid grep/Read före edit.

---

## 12. Lokal utvecklingsmiljö (för referens)

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Telefon | SM-S918B (Galaxy S23 Ultra, API 35) |

**Bash-prefix för gradle på Windows:**
```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

**Vanliga kommandon:**
```bash
# Android: bygga + installera + starta
./gradlew :androidApp:installDebug
adb shell am start -n se.birdy.android/.MainActivity

# Unit tests
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix

# Signed release-AAB
./gradlew :androidApp:bundleRelease

# Website
cd website && npm run build
cd website && npm run test:smoke
```

---

## 13. Snabbreferens — vad jag (Claude) ska tänka på

- Svara på **svenska**.
- **Inte bryt privacy-löftet** utan diskussion.
- **Inte gate:a BirdNET-relaterade audio-features bakom Premium** (licensbrott).
- **Inte expandera v1-scope** mitt i closed testing.
- Stora ändringar → använd `superpowers:brainstorming` → `:writing-plans` → `:subagent-driven-development`. Småfix → bara prata och kör.
- Code-review är tvåstegs: spec-konformitet först, kvalitet/säkerhet sen.
- Vid otydlig task: **stoppa och fråga**, inte gissa.
- Annars: autonom. Push, tag, gradle, file-edits enligt plan utan att fråga om lov.
