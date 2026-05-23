# Birdy Bird Scanner — helhetsrapport

**Datum:** 2026-05-15
**Tagg vid rapporttillfället:** `v0.8.0-rc1` (versionCode 100, versionName `1.0.0-rc1`)
**Senaste 48h:** 74 commits över två parallella spår (Plan 6a release-rc1 + Plan 2b content backfill till färdigt läge)

---

## 1. Vad är appen?

En AI-driven Android-app för fågelidentifiering i Norden/Europa. Tre kärnflöden:

1. **Skanna** — realtidskamera (3 fps) med on-device TFLite-klassificering som identifierar fåglar i sökaren. Tap-to-freeze + threshold-routing (`<0.35 → NoBird`, `0.35–0.50 → Disambig`, `≥0.50 → Match`).
2. **Lär** — uppslagsverk över 839 europeiska arter med foton, beskrivning, läten, säsong/migration, sparse-data-fallbacks.
3. **Samla** — dagbok över egna fynd, 25 fält-märken (badges), streaks, månadsgrupperad arkivvy.

Visuellt språk = **Field Journal** (paper-bg, DM Serif Italic + Caveat, marginalia, stämplar, ornament-rules). Två lager av användare: nybörjare som vill lära sig + entusiaster i fält. Solo-utvecklare, KMP (Android först, iOS-skelett finns men inget bygge).

---

## 2. Status just nu

**Build:** signed release-AAB byggd via `:androidApp:bundleRelease` (1m32s grön), installerad på SM-S918B via bundletool 1.18.1. Cold-start ≤ 1.5s, locale-switch SV↔EN utan kill, ingen R8/TFLite-crash i logcat.

**Plan-of-plans:**

| # | Plan | Tagg | Status |
|---|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI, tema | `v0.1.0-foundation` | ✅ |
| 2a | Content pipeline + walking skeleton | `v0.2.0a-pipeline` | ✅ |
| 2b | Content backfill (5 → 839 arter) | `v0.2.0-content` | ✅ |
| 3 | Encyclopedia (browse + species profile) | `v0.3.0-encyclopedia` | ✅ |
| 4a | ML & Camera UI (FakeClassifier + UI) | `v0.4.0a-camera-ui` | ✅ |
| 4b | Real TFLite-modell (AIY Birds V1) | `v0.4.0b-real-tflite` | ✅ |
| 5a | Diary (browse + detail + save flow) | `v0.5.0a-diary` | ✅ |
| 5b | Gamification (badges, streaks, unlock-queue) | `v0.5.0b-gamification` | ✅ |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding | `v0.7.0a-foundation` | ✅ |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | `v0.7.0b-screens` | ✅ |
| 7c | Field Journal redesign — paper-bg + DM Serif + Caveat | `v0.7.0c-field-journal` | ✅ |
| 7d | Match-flow — threshold + Match/Disambig/NoBird | `v0.7.0d-match-flow` | ✅ |
| 7e | Premium tier — Premium-skärm + Settings + teasers | `v0.7.0e-premium` | ✅ |
| 6a | Foundation — UX-polish + release-mekanik | `v0.8.0-rc1` | ✅ |
| 6b | Billing + Premium-feature-leverans | _(mål `v1.0.0`)_ | ⏸ nästa |

**Hård deadline:** Closed Testing måste startas senast **2026-05-18** (Google kräver 14 dagars test för nya konton för att få production-release). Launch-mål: **2026-06-01**.

---

## 3. Arkitektur

### Stack

- **Kotlin Multiplatform + Compose Multiplatform** (Android först; iOS-skelett finns men inget bygge)
- **SQLDelight 2.x** med Flow-baserade queries
- **TensorFlow Lite on-device** — AIY Birds V1, 965 klasser, ~3.5 MB, ~14ms/inference på S23 Ultra
- **CameraX** (Android) — 3 fps streaming, auto-throttle till 1.5 fps vid p95 > 333ms
- **Coil** för bildladdning
- **compose-resources** för i18n (sv + en)
- **CI:** GitHub Actions (ktlint 12.1.2, detekt 1.23.7, unit-tests, `assembleDebug`, APK-artefakt)
- **Distribution:** Play Asset Delivery för stora bundles
- **Distribution-format:** signerad AAB med R8/ProGuard aktiverat

### Moduler

| Modul | Innehåll |
|---|---|
| `shared/domain` | Repositories, use-cases, domänmodeller |
| `shared/ml` | `BirdClassifier`-interface + `TfLiteBirdClassifier` + `FakeBirdClassifier` + 3-strikes failure-guard |
| `shared/content` | SQLDelight species-DB + build-time YAML-validators (`validateSpeciesData`, `validateBadgesYaml`, `validateModelMapping`) |
| `composeApp` | UI (commonMain Compose, androidMain expect/actuals) |
| `androidApp` | Entry-point + native deps |
| `tools/content-pipeline` | Python, uv-managed — Wikidata + Wikipedia + Claude + Commons → YAML |
| `tools/ml-eval` | Python — corpus-build + accuracy-report |

### Nyckel-mönster vi etablerat över planerna

- **Threshold-routing** via `enum class MatchRoute` + `MatchThresholds.routeFor(conf)` (testbar isolerat)
- **Build-time YAML/JSON-validators** som `JavaExec`-tasks i `:shared:content`
- **3-strikes failure-guard** på classifier (Mutex + `onDegrade`-once + idempotent close)
- **`MutableSharedFlow(replay=0, extraBufferCapacity=1, DROP_OLDEST)`** + `flatMapLatest` för dynamisk re-sampling
- **`*ord*`-parsing-konvention** för accent-segment (delad av `JournalHeadline` + `BodyTextWithCaveatAccents`)
- **`expect/actual`-mönster** för Android-specifika capabilities: `SettingsLauncher`, `PlatformBackHandler`, `AppLocaleApplier`, `ImagePreprocessor`
- **`AppGraph` lambda-injection** för debug-only screens (undviker prod-only runtime-checks)

### Visuellt språk — tokens (Field Journal)

| Token | Hex | Roll |
|---|---|---|
| PaperBg | `#EFE7D6` | Paper background |
| PaperEdge | `#E5DCC7` | Paper darker edge |
| MarginaliaInk | `#3F4F30` | Sekundärtext (WCAG AA ~6.7:1) |
| StampNavy | `#1F3A5F` | Stämpel-blå |
| AccentCopper | `#A8552D` | CTA, aktiv flik, stat-siffror |
| HeroMossMid | `#3F4F30` | Hero gradient |

**Typografi:** DM Serif Display Italic (rubriker), Caveat (handskrift/marginalia), Inter (body/UI).

---

## 4. Innehåll & data

- **Arter:** 839 YAMLs i `shared/content/species/` över 97 familjer (`v0.2.0-content`-tagg).
- **Badges:** 25 st i `composeApp/.../files/badges.yaml` — 3 progression + 4 weekly streaks + 3 monthly + 4 säsong + 8 family + 3 rare.
- **Premium-badges:** 10 placeholder-stamps i `premium_badges.yaml` (väntar på content i Plan 6b).
- **Locales:** sv + en, alla strängar via `compose-resources` med `<plurals>` + `months_short_uppercase` string-array.

---

## 5. Det vi byggt senaste 48 timmarna (2026-05-13 → 2026-05-15)

**74 commits.** Två parallella spår:

- **Spår A:** Plan 6a (release-rc1), 47 commits från 2026-05-13 ~12:00 → 2026-05-15 ~19:00.
- **Spår B:** Plan 2b (content backfill), 27 commits från 2026-05-15 ~20:35 → 23:30.

### Spår A — Plan 6a: UX-polish + release-mekanik → `v0.8.0-rc1`

**T1 R8/ProGuard (`cbaf2aa` + `44bd876`)** — kodminifiering aktiverad, keep-rules för SQLDelight + alla ViewModels; ViewModel keep-glob korrigerad så den matchar våra faktiska klassnamn.

**T2 Signing (`4042d21`)** — upload-keystore + signing-config från `gradle.properties` så `bundleRelease` producerar signerad AAB utan Android Studio.

**T3 Icon-koncept (`d1a2080` + `bfec32e` + `a5c0096`)** — tre rundor mockups; användaren ritade till slut ett eget Field Journal-anpassat fågelset i `docs/superpowers/icon-concepts/final/`.

**T4 Adaptive icon + Splash (`99645f0` + `43d852a`)** — vector adaptive launcher + Splash API 31+. `AccentCopper` retintad `#8C5A3C → #A8552D` för bättre kontrast.

**T5 Cold-start (`e0ba91c` + `11a5394`)** — TFLite-init flyttad off UI thread via `ClassifierBootstrap` + `AppGate` state-gating (`JournalLoading` tills classifier är klar). `MainActivity.onDestroy` stänger nu classifier deterministiskt.

**T6 Locale-awareness (`f4e6f91` + `547b49f`)** — `LocaleResolver` + `months_short_uppercase` string-array + `<plurals>`. Device-verifierat EN↔SV på SM-S918B. Bonusfix: Archive-header kollapsad in i outer `LazyColumn` så den scrollar med listan istället för att klistra fast.

**T7 Language-picker (`66d0430` + `bd4e508` + `3dda784`)** — riktig per-app locale via `AppCompatDelegate.setApplicationLocales`. På API 33+ används `LocaleManager` direkt eftersom `MainActivity` är `ComponentActivity` (inte `AppCompatActivity`) — AppCompat-vägen no-op:ar där. Settings-effekter går via `Channel<SettingsEffect>` för att överleva config-changes.

**T8 Shared components (`c321580` + `f604ce2`)** — konsoliderade 7 skärmar till delade primitiver: `JournalLoading`, `JournalDialog`, `JournalScaffold`, `EmptyState` (med ny `action`-slot). Migrerade: ArchiveScreen, BadgesScreen, LifelistScreen, ObservationDetailScreen, SpeciesProfileScreen, MatchResultScreen, ListenLauncherScreen. Review-fixar: I1 (`onDismiss = onConfirm` footgun → `= {}`), I2 (`TextUnit(18f, TextUnitType.Sp)` → `18.sp`).

**T9 A11y (`d27696c` + `9242c0b` + `4937ea2`)**
- `MarginaliaInk` token bumpat `#5C6E48 → #3F4F30` för WCAG AA-kontrast (~6.7:1 mot paper-bg, upp från ~3.8:1).
- `StampSeal` får `.semantics { contentDescription = ...; role = Role.Button if onClick != null }` med state-aware label.
- `BottomNavBar`, `JournalHeadline`, `JournalSubLine`, `PlateFrame` → `mergeDescendants = true`.
- AsyncImage `contentDescription` populerat på SpeciesProfile, ArchiveScreen-thumbnail, PremiumHeroCard.
- Fix för dubbel-annonsering: PlateFrame-interna AsyncImages får `contentDescription = null` när PlateFrame mergar children.

**T10 UX bumps A1–A13 (6 commits `233e020` → `cdaa06f`)**
- Encyclopedia: skeleton + clear-button på search + sticky family-headers.
- Diary: empty-state redesign + månadsgruppering + ta bort fake stats.
- Badges: locked-tap → progress bottom-sheet.
- Match: NoBird hints + Disambig save-as-unknown + Match inline-note.
- Scan: DEMO recovery sheet + tap-to-freeze fix + paper-bg permission hero.
- Onboarding: back/skip + copy fixes.
- `PlatformBackHandler` expect/actual för cross-platform BackHandler.

**T11 Onboarding pre-T15 (`1d74278` + `04c9791` + `ec3dd70`)** — Skip-CTA z-order ovanför Pager + statusBarsPadding. Wordmark hero på sida 1 + Play Store feature graphic (1024×500). Wordmark-bg gjord transparent så paper-texturen syns igenom.

**T12 Manifest (`746d86a`)** — `<queries>` för camera/mailto/https intents (Android 11 package-visibility), `data_extraction_rules.xml` + `backup_rules.xml` (begränsar Auto Backup), `usesCleartextTraffic=false`.

**T13 Settings + About (`8e44552`)** — alla 6 Settings-rader (Rate Birdy / Share / Feedback / About / Privacy / Terms) wirade via `SettingsLauncher` expect/actual som öppnar Play Store, mailto, Custom Tabs. Ny `AboutScreen` med JournalIntro + version-stamp + credits + open-source-licenses. VM-metoder emittar `SettingsEffect` → `SettingsScreen.LaunchedEffect` dispatchar.

**T14 Play Store-artefakter (`59b644c`)** — `docs/play-store/{privacy-policy,terms,store-listing-sv,store-listing-en,data-safety-form}.md` + `.github/workflows/pages.yml` med pandoc GFM→HTML5 som emittar `privacy.html` / `terms.html` så de matchar T13 Intent-URL:erna.

**T15 Release (`4e96f7f` → `b45d891`)**
- Wirar species hero-image i Match + Disambig (tidigare placeholder).
- Replacar launcher-icon med användarens egna design.
- Bumpar version till `1.0.0-rc1` / versionCode 100.
- Device-verify pass på SM-S918B: 12 device-screenshots committade i `docs/superpowers/screenshots/2026-05-15-v0.8.0-rc1/`.
- Spec self-review: 7/10 criteria met, 2 partial (Pages-deploy + TalkBack-djuptest), 1 deferred (Match/Disambig screenshots kräver test-image-infra).

### Spår B — Plan 2b: content backfill → `v0.2.0-content`

Pure-data, kördes av parallel agent. Från `273/700 (corvidae paused)` till **839/839 arter** (alla rader i `species_list.yaml` har YAMLs).

Familjer landade i kronologisk ordning idag kväll (förkortad):

`cuculidae → dromadidae → emberizidae → estrildidae → falconidae → fregatidae → fringillidae (+14 approved) → gaviidae → glareolidae → gruidae → haematopodidae → hirundinidae → hydrobatidae → micro-batch → laniidae → laridae → leiothrichidae-bundle → motacillidae → muscicapidae (51!) → nectariniidae-bundle (9 familjer i en) → otididae-bundle2 (9 familjer) → scolopacidae → phasianidae → sylviidae → strigidae → phylloscopidae+procellariidae → picidae+passeridae`

Approval-rate ≈ 22% (manuellt godkända hero-picks) — resten på `auto`-status. Mönstret stämmer med runbookens "bara `abundance: allmän`-arter får manuell hero-review".

---

## 6. Var vi är på väg

### Nästa: Plan 6b (Billing + Premium-feature-leverans) → `v1.0.0`, launch 2026-06-01

Locked scope efter 5-agent launch-research 2026-05-15:

1. **Google Play Billing v8** (INTE v6 — v6 deprecated 2026-08-31). Faktisk `purchase()` istället för mock som bara flippar `PremiumState.Active`. Restore Purchases-knapp (Play-krav).
2. **Audio-ID via BirdNET-Lite** TFLite-modell (återanvänder Plan 4b-mönster: Mutex-serialiserad runner, 3-strikes failure-guard, build-time validator).
3. **PDF-export** av dagbok + season-statistics.
4. **10 premium fält-märken** — content för `premium_badges.yaml`.
5. **ML preprocessing Phase 1 diagnos PRE-LAUNCH** — 10% field hit-rate bryter "honest uncertainty"-USP.
6. **Premium-screen-fixar:** ta bort "spara 60%"-stämpeln (EU Omnibus + dark-pattern-policy), fixa EN-valuta-bug, behåll priser 199/499 kr, throttla cold-start-modal till 1×/3d + 7d grace.

### Roadmap post-v1.0 (referens)

- **v1.5 — "Karta & moln":** konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. Schemat har redan nullable `latitude` / `longitude` / `location_label`-kolumner från Plan 5a så v1.5 bara fyller i nya rader.
- **v2 — "Community":** delning av fynd, kommentarer, flöde, moderering.
- **v2.x:** quiz/utbildningsläge, fullt offline-läge för längre exkursioner.

---

## Sammanfattning

På 48 timmar shippade vi hela "förberedelse-för-Play-Store"-bygget (R8, signing, icon, splash, locale-picker, a11y, Settings-wiring, manifest-hardening, Play Store-dokumentation) som rc1, plus att den parallella content-pipelinen fullbordades — Plan 2b stängdes från `273/700 pausad` till `839/839 klar` och taggades `v0.2.0-content`. Nästa kritiska milstolpe: Closed Testing-upload senast 2026-05-18 + Google Play Billing v8-integration för production-release 2026-06-01.
