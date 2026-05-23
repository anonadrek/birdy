# Birdy Bird Scanner — arbetsguide för Claude Code

> **Den här filen läses automatiskt av Claude Code i varje session.** Den ger sammanhang för projektet, var saker ligger, och hur vi arbetar.

## Vad är detta?

AI-driven Android-app för fågelidentifiering. Realtidsskanning via kamera + foto-upload + audio-ID + uppslagsverk över 839 europeiska arter. Kotlin Multiplatform + Compose Multiplatform. v1 = Android-only ("Skanna & lär"); senare faser lägger till karta, push, community, iOS.

## Status (2026-05-23)

- **Android-app:** **v1.0.0 taggad 2026-05-23** (versionCode 112, versionName 1.0.0). All v1-scope inne. Senaste tags: `v1.0.0` + `v0.9.0c-premium-content` (Plan 6b3 — Premium content). Nästa = **Internal Testing-upload** till Play Console enligt runbook `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md` → Closed Testing (14d) → Production. Branch `main` är clean och pushad.
- **Plan 6b3 (Premium content) — DONE:** PDF-export av fältdagbok (`JournalPdfRenderer` med title/stats/species/badges/colophon-sidor + share-sheet), Season Statistics-skärm (Canvas-baserade bar/line/donut-chartar, 0 deps), 10 nya premium-badges + manuell rule + `BadgeStringMap` (40 strängar SV+EN). T19 wire:ade `PremiumActivationListener` + `effectivePremiumActive` på AppGraph. T22 reducerade base APK från ~300 MB till **136 MB** via WebP-migration av plate-foton + `:asset-pack` install-time-modul för TFLite-vikterna. T21 device-verify på SM-S918B + 10 skärmdumpar. T25 internal-testing hand-off runbook.
- **Brand refresh (2026-05-22):** Ny launcher-ikon + custom Compose splash med wordmark (commit `0c76113`). Webbplats hero bytte brand-foot wordmark mot fågel-app-ikon med fade-up + ny Glimpse-carousel-sektion med 6 Play Store-skärmdumpar + transparenta multi-size favicons (`.ico` + apple-touch). Settings: webbplats-länk i OM BIRDY + Language-ikonbyte + privacy/terms-länkar migrerade till `birdy.community/legal/`.
- **BirdNET-licensbeslut (2026-05-22):** BirdNET-Lite-modellen är **CC BY-NC-SA 4.0 (NonCommercial)** — får inte gate:as bakom Premium. **Option A vald:** audio-ID är gratis för alla. PremiumGate i `ListenLauncher` rivet. Premium-tier intäktsmodell står på Plan 6b3-features (PDF, stats, badges) som vi byggt själva. Detaljer i memory `project_birdnet_license_decision.md`.
- **Launch-period premium-öppen (2026-05-22):** För closed testing (14d) + initial production-launch är `PREMIUM_OPEN_FOR_LAUNCH=true` i `androidApp/build.gradle.kts`. Det gör att `MainActivity` hardcodar `premiumOverride = Active(LIFETIME)` för alla användare. Anledning: testarna ska få komplett v1.0-upplevelse, Billing v8 IPC är inte verify:ad än (defer:ad till Internal Testing). Stängs manuellt när Billing-flow är runtime-verifierad och vi vill flippa på monetization (se follow-up #4).
- **Marketing-website:** Live på `https://birdy.community` via Vercel (Astro 5 + Tailwind v4 + i18n EN/SV). Inkluderar `/legal/{privacy,terms,data-safety}/`. Gammal GitHub Pages-deploy (birdy.app via `pages.yml`) är avskaffad. Senaste deploy fungerar; setup-gotcha = Vercel **Root Directory måste vara `website`** (annars ENOENT på package.json).
- **Google Play:** Developer account approved 2026-05-20 (personligt konto). AB-flytt deferred till post-launch via Account Transfer. Billing v8 IPC-verify deferred till Internal Testing (kräver app entry + license testers i Play Console).

Full per-plan historik: se "Avslutade planer (referens)" nedan + auto-memory.

## Plan-of-plans (v1)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI | ✅ `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton (5 arter) | ✅ `v0.2.0a-pipeline` |
| 2b | Content backfill (5 → 839 arter) | ✅ `v0.2.0-content` |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + 3 fps CameraX) | ✅ `v0.4.0a-camera-ui` |
| 4b | Real TFLite (AIY Birds V1, 965 klasser, ~14ms) | ✅ `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (25 badges, streaks, unlock-queue) | ✅ `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding, Settings | ✅ `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | ✅ `v0.7.0b-screens` |
| 7c | Field Journal redesign — DM Serif + Caveat + paper-bg + StampSeal | ✅ `v0.7.0c-field-journal` |
| 7d | Match-flow — threshold-logik, Match/Disambig/NoBird-screens | ✅ `v0.7.0d-match-flow` |
| 7e | Premium tier — PremiumScreen + per-tab teasers + cold-start modal | ✅ `v0.7.0e-premium` |
| 6a | Foundation — UX-polish + release-mekanik (R8, signing, icon, a11y) | ✅ `v0.8.0-rc1` |
| 6b1 | Billing v8 + launch-prep (PremiumBillingClient + Restore Purchases) | ✅ `v0.9.0a-billing` |
| 6b2 | Audio-ID via BirdNET-Lite (3s rec + FlexRFFT TF Select op) — **free-tier** | ✅ `v0.9.0b-audio` (PremiumGate rivet 2026-05-22) |
| 6b3 | Premium content (PDF-export + season-statistics + 10 fält-märken) | ✅ `v0.9.0c-premium-content` + `v1.0.0` |
| W | Marketing-website (Astro + Vercel + birdy.community + /legal/) | ✅ Live |

**Föreslagen ordning:** ~~Plan 6b3 → tag v1.0~~ ✅ → **Internal Testing** → Closed Testing (14d) → Play Store-launch.

Varje plan ska lämna projektet i ett byggbart, testbart tillstånd: `./gradlew build` ska gå grönt.

## Var hittar du saker

| Vad | Var |
|---|---|
| Designspec för v1 | `docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md` |
| Implementationsplaner | `docs/superpowers/plans/YYYY-MM-DD-v1-NN-<phase>.md` |
| Skärmdumpar per milstolpe | `docs/superpowers/screenshots/` |
| Milstolpe-review-runbook | `docs/superpowers/runbooks/milstolpe-review.md` |
| Internal Testing hand-off runbook | `docs/superpowers/runbooks/2026-05-22-v1.0.0-internal-testing.md` |
| Play Store-artefakter (markdown) | `docs/play-store/{privacy-policy,terms,store-listing-{sv,en},data-safety-form}.md` |
| Website källkod | `website/` (Astro 5 + Tailwind v4 + Playwright) |
| Visuellt språk (Mossbädd + Field Journal) | sammanfattat nedan + auto-memories `visual_language_birdy_v1.md`, `project_plan_7c_status.md` |
| Auto-memory (lokalt, inte i repo) | `~/.claude/projects/C--Users-abbea-dev-birdy-bird-scanner/memory/` |
| Launch-research | `docs/superpowers/research/2026-05-15-play-store-launch/` + `2026-05-20-play-store-audit.md` |

## Hur vi jobbar

### När du börjar en ny session
1. Säg "Vi fortsätter med birdy-bird-scanner" eller liknande.
2. Be om statusöversikt: "Var står vi?" → kolla git log + senaste commit.
3. Bestäm nästa steg utifrån status.

### Behövs superpowers?
- **Brainstorming, ny plan, plan-execution med review** → `superpowers:brainstorming` / `:writing-plans` / `:subagent-driven-development`.
- **Vanliga frågor, snabba bugfixar, mindre refactoring** → bara prata; ingen skill.

Tumregeln: större än ett samtal eller kräver disciplin (TDD, plan-tracking) → skill. Annars inte.

### Modell-strategi
| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 |
| Implementer-subagents i `subagent-driven-development` | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

Vid avbrott: all progress är committad i git. Nästa session fortsätter från senaste commit utan tappad kontext.

## Visuellt språk

**Field Journal-tema (Plan 7c, locked 2026-05-10) är canonical app-wide.** Mossbädd-paletten under är legacy-tokens som fortfarande används punktvis (HeroMoss-gradient, AccentCopper).

**Field Journal tokens** (i `composeApp/.../ui/theme/Color.kt`):

| Token | Hex | Roll |
|---|---|---|
| PaperBg / PaperEdge | `#EFE7D6` / `#E5DCC7` | Pappersbakgrund + texture |
| MarginaliaInk | `#3F4F30` | Caveat-text, sub-lines (WCAG AA-bumpad i Plan 6a T9) |
| AccentCopper | `#A8552D` | CTA, aktiv tab, stat-siffror, copper-pills |
| StampNavy | `#1F3A5F` | StampSeal-states |
| HeroMossMid / Deep / Shadow | `#5C6E48` / `#3F4F30` / `#2A3520` | Mossgrön gradient (Listen/Premium hero) |

**Typografi:** `DM Serif Display Italic` för rubriker (`JournalHeadline` parsar `*ord*` → Caveat-italic accent-segment med rotation), `Caveat` för marginalia/sub-lines, `Inter` (system sans) för body. Fonts bundlade via `compose-resources` (`rememberDmSerifDisplay()` / `rememberCaveat()` i `Type.kt`).

**Layout-element:** `Modifier.paperBackground()` med dot-texture som default-bas; `JournalIntro` (eyebrow + JournalHeadline + ornament + sub-line); `StampSeal` (locked/in-progress/unlocked-states); `PlateFrame` (naturalist-foto-frame); `OrnamentRule` (❦ + horisontellt streck).

## Tekniska val

- **Android-stack:** KMP + Compose Multiplatform (Android primär, iOS-skelett)
- **DB:** SQLDelight 2.x med Flow-baserade queries
- **ML (foto):** TensorFlow Lite + AIY Birds V1 (uint8-quantized MobileNetV2, ~14ms/inference)
- **ML (audio):** BirdNET-Lite v2 + `tensorflow-lite-select-tf-ops:2.16.1` (FlexRFFT TF Select op — utan denna failar node 29)
- **Kamera:** CameraX 3 fps `ImageAnalysis` + auto-throttle till 1.5 fps vid p95 > 333ms
- **Audio:** 48kHz mono PCM_16 via UNPROCESSED → VOICE_RECOGNITION graceful fallback, 3s rec → OGG/Opus
- **Billing:** Google Play Billing v8 (`PremiumBillingClient` expect/actual) + RSA SHA1-signature-verify via Play Licensing public key embeddad i BuildConfig
- **Språk:** SV + EN, Sverige först; alla UI-strängar via `compose-resources`
- **Distribution:** AAB via Play Asset Delivery
- **CI:** GitHub Actions (ktlint 12.1.2, detekt 1.23.7, unit tests, assembleDebug)
- **Website:** Astro 5 + Tailwind v4 + `@astrojs/sitemap` + `marked` + Playwright smoke tests, hostat på Vercel (auto-deploy från `main`, root dir = `website`)

## Lokal utvecklingsmiljö (Windows + Galaxy S23 Ultra)

| Vad | Var |
|---|---|
| JDK 21 (Temurin) | `C:\Java\OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10\jdk-21.0.11+10\` |
| Android SDK | `C:\Users\abbea\AppData\Local\Android\Sdk` |
| ADB | `C:\Users\abbea\AppData\Local\Android\Sdk\platform-tools\adb.exe` |
| Telefon | SM-S918B (Galaxy S23 Ultra, API 35), USB-felsökning på, RSA-auktoriserad |

**Standard-prefix för bash-`./gradlew`-kommandon** (annars hittar Gradle inte Java):

```bash
export JAVA_HOME="C:/Java/OpenJDK21U-jdk_x64_windows_hotspot_21.0.11_10/jdk-21.0.11+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

## Vanliga kommandon

```bash
# Android: bygga + installera + starta på ansluten enhet
./gradlew :androidApp:installDebug
"/c/Users/abbea/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell am start -n se.birdy.android/.MainActivity

# Android: snabba unit-tests (delade moduler på JVM)
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest

# Android: lint + statisk analys
./gradlew ktlintCheck detekt
./gradlew ktlintFormat   # autofix

# Android: signed release-AAB
./gradlew :androidApp:bundleRelease

# Website (kör från website/)
cd website && npm run build
cd website && npm run test:smoke   # Playwright (7 tester)
cd website && npm run test:i18n    # SV/EN parity check
```

## Repo & deploy

- **GitHub:** https://github.com/anonadrek/birdy. Branch `main` är default. Plan-arbete sker på `main` med små commits per task; tagga milstolpar (`v0.1.0-foundation` osv).
- **Website:** Auto-deploy till `birdy.community` via Vercel vid push till `main`. **Root Directory MÅSTE vara `website`** i Vercel project settings — annars failar `npm install` med ENOENT på `/vercel/path0/package.json`.
- **Play Console:** Personligt konto (Albin), approved 2026-05-20. App entry + in-app products + license testers behövs innan Billing v8 IPC kan runtime-verifieras.

## Beslut & ramar

- **Scope v1.0:** Skanna (foto + audio) + uppslagsverk + dagbok + gamification + premium tier. Map/cloud sync = v1.5.
- **Geografi:** Norden/Europa, 839 arter.
- **Användare:** Bred två-lager (nybörjare som vill lära sig + entusiaster i fält).
- **AI:** On-device, ingen backend för inference. Migrationsdata + sannolikhet är art-nivå statisk i v1.
- **Solo-utvecklare:** användaren bygger via Claude Code; granskning sker av användaren mellan tasks.
- **Privacy-löfte:** "Almost nothing collected, data stays on phone" — verifierat i 2026-05-20 fältrevision. INTE bryt detta utan diskussion.

## Frågor + autonomi

- Otydlig task eller spec-motsägelse? **Stoppa och fråga / lyft upp** istället för att gissa.
- Annars: **"Don't ask me for permission to run anything"** — kör commits, push, gradle, file-edits enligt plan utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). Undantag: blockerare som kräver fysisk åtkomst (telefon, emulator) eller tredjepartsbeslut (Play Console, Vercel UI) — där rapporterar man status. Två-stegs-review (spec → kvalitet) körs alltid mellan tasks.

## Pending follow-ups (post-launch)

1. **URL-migration:** `SettingsScreen.kt` rad ~128–129 + Play Console store listing pekar fortfarande på `https://anonadrek.github.io/birdy/{privacy,terms}.html`. Uppdatera till `https://birdy.community/legal/{privacy,terms}/` i nästa Android release-cykel. Gamla URL:erna fungerar tills GH Pages slås av (steg 2).
2. **GitHub Pages teardown:** I repo Settings → Pages, sätt Source till "None". `pages.yml` är redan borttagen (Plan W T2).
3. **Email migration:** Sätt upp `feedback@birdy.community` (Cloudflare Email Routing eller Resend Inbound) under closed testing. **Bridge nu = `albin@abrahamssons.se`** — bytt in i alla legal-docs 2026-05-22. När birdy.community-mailen är live: byt tillbaka i `website/src/content/copy.{en,sv}.json` (FAQ + footer) + alla markdown-filer i `docs/play-store/`.
4. **Billing v8 IPC runtime-verify** (deferred från 6b1): purchase-flow + Restore Purchases + Active(YEARLY/LIFETIME)-state-flip. Kräver Internal Testing-app entry + in-app products + license testers i Play Console. **OBS:** detta blockerar inte launch — `PREMIUM_OPEN_FOR_LAUNCH=true` (defaultConfig i `androidApp/build.gradle.kts`) ger alla användare LIFETIME-premium under closed testing + initial production. När Billing-verify är klar och vi vill aktivera monetization: flippa `PREMIUM_OPEN_FOR_LAUNCH=false`, bumpa versionCode och pusha en ny release.
5. **Audio accuracy eval** (deferred från 6b2): kräver xeno-canto API v3 key. Pipeline klar i `tools/ml-eval/audio_accuracy_report_2026-05-21.md`.
6. **AB-flytt:** Account Transfer av Play Console till AB-bolaget när det är registrerat (post-launch).
7. **SV legal-översättningar:** Om Sverige-trafik växer, mirror `/sv/legal/...` med översatta markdown-filer. Idag cross-linkar SV-footer till EN-only `/legal/`-routes (intentionellt — Nordics/EU first launch).
8. **Plan 6a T8/T9 device-screenshots saknas:** `08-match-with-inline-note` + `09-disambig-save-as-unknown` (kräver deterministisk match-flow ej driveable via ADB — kan adresseras via test-image-infra i framtida sprint).

## Roadmap post-v1.0 (referens)

Tagits in från v1-design-spec så vi inte tappar bort dem. Inget byggs här innan v1.0 är ute.

- **v1.5 — "Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren. `Observation`-schemat har nullable `latitude` / `longitude` / `location_label` från Plan 5a så v1.5 bara fyller i nya rader (ingen migration behövs).
- **v2 — "Community":** Delning av fynd, kommentarer, flöde, moderering.
- **v2.x:** Quiz/utbildningsläge, fullt offline-läge för längre exkursioner, iOS.

## Avslutade planer (referens)

Detaljerade lärdomar + återanvändbara mönster finns i auto-memory (`project_plan_<NN>_status.md`). Här bara one-liners + tagg + spec-pointer.

| Plan | Tag | Plan-doc | Auto-memory |
|---|---|---|---|
| 1 Foundation | `v0.1.0-foundation` | `2026-04-30-v1-01-foundation.md` | — |
| 2a Pipeline | `v0.2.0a-pipeline` | `2026-05-02-v1-02a-content-pipeline.md` | `project_plan_2b_status.md` (delad) |
| 2b Content backfill | `v0.2.0-content` | runbook `2026-05-02-plan-2b-content-backfill.md` | `project_plan_2b_status.md` |
| 3 Encyclopedia | `v0.3.0-encyclopedia` | `2026-05-04-v1-03-encyclopedia.md` | `project_plan_3_strategy.md` |
| 4a Camera UI | `v0.4.0a-camera-ui` | `2026-05-05-v1-04a-camera-ui.md` | `project_plan_4a_status.md` |
| 4b Real TFLite | `v0.4.0b-real-tflite` | `2026-05-07-v1-04b-real-tflite.md` | `project_plan_4b_status.md` |
| 5a Diary | `v0.5.0a-diary` | `2026-05-05-v1-05a-diary.md` | `project_plan_5a_status.md` |
| 5b Gamification | `v0.5.0b-gamification` | `2026-05-06-v1-05b-gamification.md` | `project_plan_5b_status.md` |
| 7a Redesign Foundation | `v0.7.0a-foundation` | `2026-05-08-v1-07a-redesign-foundation.md` | `project_plan_7a_status.md` |
| 7b Redesign Skärmar | `v0.7.0b-screens` | `2026-05-09-v1-07b-redesign-screens.md` | — |
| 7c Field Journal | `v0.7.0c-field-journal` | `2026-05-09-v1-07c-field-journal.md` | `project_plan_7c_status.md` |
| 7d Match-flow | `v0.7.0d-match-flow` | `2026-05-12-v1-07d-match-flow.md` | `project_plan_7d_status.md` |
| 7e Premium tier | `v0.7.0e-premium` | `2026-05-12-v1-07e-premium-tier.md` | `project_plan_7e_status.md` |
| 6a Release foundation | `v0.8.0-rc1` | `2026-05-13-v1-06a-foundation.md` | `project_plan_6a_status.md` |
| 6b1 Billing + launch-prep | `v0.9.0a-billing` | `2026-05-16-v1-06b1-billing-launch-prep.md` | `project_plan_6b1_status.md` |
| 6b2 Audio-ID | `v0.9.0b-audio` | `2026-05-20-v1-06b2-audio-id.md` | `project_plan_6b2_status.md` |
| 6b3 Premium content | `v0.9.0c-premium-content` + `v1.0.0` | `2026-05-21-v1-06b3-premium-content.md` | `feedback_plan_6b3_doc_traps.md` |
| W Website (Vercel + /legal/) | — (live) | `2026-05-21-website-vercel-legal.md` | — |

## Trap-katalog (vanliga repeterande buggar)

Saker som har bitit oss mer än en gång — kolla först här om något konstigt händer:

- **`:androidApp` saknar transitiva deps från `:composeApp`** (Plan 5a T12) — composeApp använder `implementation()` inte `api()`, så varje ny shared/library-referens måste få egen `implementation()` i `:androidApp/build.gradle.kts`.
- **compose-resources unescape:ar inte Android `\'`** — använd raw `'` eller Unicode `’` (U+2019) direkt i strings.xml.
- **compose-resources processar inte `%%` som `%`-escape** — använd `%1$s` och passa pre-formatterad `"${value}%"` från Kotlin-call-site. Regression i Plan 5a → 7d.
- **`ImageProxy.imageInfo.timestamp` returnerar nanos sedan boot, inte Unix-epoch** — använd `System.currentTimeMillis()` i CameraX-analyzer för wall-clock timestamps.
- **Hardcoded localized strings bryter andra locale** — alltid `stringResource(Res.string.xxx)`, aldrig `"spara 60%"` direkt i Kotlin.
- **ADB-tap y < 300 kan trigga notification-drawer** i stället för UI-element — använd `KEYCODE_BACK` för recovery + `uiautomator dump` för exact bounds.
- **Quality-review måste köra `:androidApp:installDebug` + device-test** (Plan 5a process-lärdom) — `:composeApp:assembleDebug` ensam missar manifest/dep-trap för Android-screens.
- **FlexRFFT-crash i TFLite-audio** — kräver `tensorflow-lite-select-tf-ops:2.16.1` dep, annars failar node 29 "Failed to prepare". Diagnostisk logging > catch-all `Throwable`-swallow.
- **Vercel `npm install` ENOENT** — Root Directory måste vara `website` i project settings (inte `/website`, inte tomt).
- **BirdNET-Lite-modellen är CC BY-NC-SA (NonCommercial)** — får INTE gate:as bakom Premium. Audio-ID är gratis-feature i v1.0. Om vi någonsin lägger något bakom Premium som rör BirdNET → licensbrott. Premium = endast Plan 6b3-features (PDF/stats/badges) som vi byggt själva.
