# Birdy Bird Scanner

AI-driven Android-app för fågelidentifiering — realtidsskanning via kamera, foto-upload, audio-ID via mikrofon, och ett rikt uppslagsverk över 839 europeiska arter.

> **Status (2026-06-12):** v1.2-slutkandidaten är komplett på `main` (versionCode 124, `1.2.0-rc2`). v1.2 lägger till en **privat karta över egna fynd** (kartvyn är Premium, platsfångst är gratis opt-in), en **omgjord Premium-skärm med day-0-paywall**, **Troférummet**, **Veckans uppslag** och en stor UX/polish-genomgång (~105 åtgärdade fynd ur en 136-punkters audit, device-verifierad). Closed testing rullar sedan 2026-06-08 med `1.2.0-rc1` (vC123). Kvar före nästa AAB-upload: bygga vC124, Play Console-texter + färska skärmdumpar, riktig `MAPTILER_API_KEY`, samt Billing v8-runtime-verify innan `PREMIUM_OPEN_FOR_LAUNCH` stängs. Marketing-website live på [birdy.community](https://birdy.community) (Astro + Vercel).

## Vad du kan göra i appen idag

- **Skanna foto** via kamera (CameraX 3 fps, TFLite + AIY Birds V1, ~14 ms/inference på S23 Ultra) med zoom 1–10×, eller ladda upp från galleri med crop + 90°-rotation.
- **Identifiera via ljud** — push-to-record, 3 sek, BirdNET-Lite v2 med FlexRFFT TF Select op. Gratis för alla (modellen är CC BY-NC-SA och får inte ligga bakom Premium).
- **Uppslagsverk** över 839 nordiska/europeiska arter med foto, ljud, beskrivning, migration och säsongs-sannolikhet — bläddringsbart i 15 ekologiska grupper.
- **Robust sök** i uppslagsverket — på art, vetenskapligt namn, familj och genus; okänsligt för apostroftyp (`'`/`’`), diakriter (`ü`→`u`) och aktivt språk (cross-locale).
- **Dagbok (Field Journal)** med pappers-look, DM Serif Italic + Caveat-typografi, stampseals och plate-frames.
- **Karta över egna fynd** — privat och helt on-device (osmdroid + MapTiler, Field Journal-duotontema med wax-seal-pins). Platsfångst är gratis opt-in; själva kartvyn är Premium.
- **Gamification** — 34 märken (27 gratis + 7 premium) inkl. rödlistat-spår och livslista upp till 500 arter, streaks, Troférummet, Dagens fågel och Veckans uppslag (adaptiv söndags-recap).
- **Premium-tier** — PDF-export av fältdagboken, säsongsstatistik, premium-märken och kartvyn. Månatlig/årlig/livstid via Google Play Billing v8, med Restore Purchases och RSA-signature-verify.
- **Lokaliserad** på svenska och engelska (compose-resources).

## Arkitektur

Kotlin Multiplatform-app där affärslogik och UI är delad via Compose Multiplatform. v1 är Android-only; iOS-skelettet finns men aktiveras först i en senare fas.

| Modul | Innehåll |
|---|---|
| `composeApp` | Compose Multiplatform UI (delad mellan Android och framtida iOS) |
| `shared/domain` | Use cases, domänmodeller, business rules (ren Kotlin) |
| `shared/data` | SQLDelight 2.x-queries, repositories, content providers |
| `shared/datastore` | DataStore-baserade user preferences + premium-state |
| `shared/ml` | `BirdClassifier` expect/actual för foto-ID, bildpreprocessing + BirdNET-Lite audio-runner och label-mapping |
| `shared/pdf` | `JournalPdfRenderer` expect/actual (Premium PDF-export) |
| `shared/content` | Artdatabas-loading (839 arter), gamification-regler, badge-evaluator |
| `androidApp` | Android entry point, MainActivity, plattforms-actuals |
| `asset-pack` | Install-time Play Asset Delivery-modul med plåtfoton (~2060 WebP-bilder, ~326 MB) |
| `iosApp` | iOS-skelett (aktiveras i v2) |
| `website` | Marketing-site (Astro 5 + Tailwind v4 + i18n EN/SV), deployas till birdy.community via Vercel |

Se design-specen för detaljer: [`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`](docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md)

## Komma igång

**Krav:**
- JDK 21 (Temurin/Adoptium rekommenderat)
- Android Studio Iguana (eller senare) eller IntelliJ IDEA 2024.2+
- Android SDK 35 + build-tools
- En MapTiler API-nyckel i lokala `gradle.properties` (`MAPTILER_API_KEY=...`) för kartan — committas aldrig
- För website-arbete: Node.js 20+

**Bygga och köra (Android):**

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug   # om enhet/emulator är ansluten
./gradlew :androidApp:bundleRelease  # signed AAB för Play Store
```

**Köra tester:**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest
```

**Linting:**

```bash
./gradlew ktlintCheck detekt
./gradlew ktlintFormat               # autofix formatting
```

**Website (kör från `website/`):**

```bash
cd website && npm install
cd website && npm run dev            # localhost preview
cd website && npm run build
cd website && npm run test:smoke     # Playwright (7 tester)
cd website && npm run test:i18n     # SV/EN parity check
```

## Vägkarta — Plan-of-plans (v1.0)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation — KMP-bootstrap, Compose, CI | ✅ `v0.1.0-foundation` |
| 2a | Content pipeline + walking skeleton | ✅ `v0.2.0a-pipeline` |
| 2b | Content backfill (5 → 839 arter) | ✅ `v0.2.0-content` |
| 3 | Encyclopedia (browse + species profile) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML & Camera UI (FakeClassifier + 3 fps CameraX) | ✅ `v0.4.0a-camera-ui` |
| 4b | Real TFLite (AIY Birds V1, 965 klasser) | ✅ `v0.4.0b-real-tflite` |
| 5a | Diary (browse + detail + save flow) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (25 badges, streaks, unlock-queue) | ✅ `v0.5.0b-gamification` |
| 7a | Redesign Foundation — tokens, DataStore, Onboarding | ✅ `v0.7.0a-foundation` |
| 7b | Redesign Skärmar — Listen/Archive/Lifelist/Badges | ✅ `v0.7.0b-screens` |
| 7c | Field Journal — DM Serif + Caveat + paper-bg + StampSeal | ✅ `v0.7.0c-field-journal` |
| 7d | Match-flow — threshold-logik, Match/Disambig/NoBird | ✅ `v0.7.0d-match-flow` |
| 7e | Premium tier — PremiumScreen + per-tab teasers | ✅ `v0.7.0e-premium` |
| 6a | Foundation — UX-polish + release-mekanik (R8, signing, a11y) | ✅ `v0.8.0-rc1` |
| 6b1 | Billing v8 + launch-prep (Restore Purchases) | ✅ `v0.9.0a-billing` |
| 6b2 | Audio-ID via BirdNET-Lite | ✅ `v0.9.0b-audio` |
| 6b3 | Premium content (PDF-export + säsongs-stats + 10 fält-märken) | ✅ `v0.9.0c-premium-content` → `v1.0.0` |
| W | Marketing-website (Astro + Vercel + birdy.community + /legal/) | ✅ Live |

**v1.0 är levererad** (`v1.0.0`, 2026-05-23) och closed testing (14 dagar) pågår. Innan produktions-launch återstår Billing v8 IPC-runtime-verify + go-live-flippen (`PREMIUM_OPEN_FOR_LAUNCH=false`) — se `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` — samt färska v1.2-skärmdumpar till Play Store-listningen.

### Efter v1.0

| Spår | Innehåll | Status |
|---|---|---|
| v1.0.2 | Onboarding-omarbetning — 7-scens scroll-story | ✅ taggad |
| v1.1 | Retention-hooks (Dagens fågel + notiser), kamera-zoom 1–10× + crop/rotera, testarfeedback DP A–E (robust sök, positionering & copy, ekologiska kategorier, märken-omarbetning 34 st, grupp-axel i DB), Veckans uppslag, Troférummet | ✅ uppladdad till closed testing (vC122) |
| v1.2 | Privat fynd-karta (osmdroid + MapTiler, Field Journal-tema), premium-skärm-redesign + day-0-paywall, UX/polish-audit (~105 fynd) + scan-freeze-omdesign | ✅ komplett på `main` (vC124 / `1.2.0-rc2`); rc1 (vC123) i closed testing, AAB-bygge + Play Console-upload återstår |

## Roadmap post-v1.x

Geografisk expansion är huvudtracken — ML-modellerna är redan globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000); jobbet sitter i content-pipeline, regional säsongsdata, on-demand asset packs och fler språk.

- **v2 — "Asien + hela Europa" + iOS-launch:** content-expansion (Östasien/Indien först) + App Store-release via Compose Multiplatform-iOS-target.
- **v3 — "Hela världen":** alla återstående kontinenter, full content-skalning + språkstöd.
- **Parallella spår (inte version-bundna):** "Karta & moln" — konton, molnsynk av dagboken, publika fynddata, push om sällsynta arter nära dig (den privata fynd-kartan i v1.2 är första steget); "Community" — delning, kommentarer, flöde, moderering; quiz/utbildningsläge; fullt offline-läge för längre exkursioner.

## Bidragande

Specs och plans i `docs/superpowers/` är källan till sanning. Workflow: Markdown-spec (`specs/`) → implementation plan (`plans/`) → kod, med Claude Code som assistent. Detaljerade arbets-regler för AI-assistenten finns i [`CLAUDE.md`](CLAUDE.md).

## Licens

Proprietär — © 2026 Birdy / Albin Lindblom, all rights reserved. Se [`LICENSE`](LICENSE). Källkoden är sluten; ingen användnings-, kopierings- eller distributionsrätt ges. Tredjepartskomponenter (Kotlin/Compose/SQLDelight/osmdroid m.fl. under Apache 2.0, BirdNET-Lite under CC BY-NC-SA 4.0, fonter under OFL 1.1, kartdata © OpenStreetMap/MapTiler) lyder under sina egna licenser — full attribution finns i appen under Inställningar → Om.
