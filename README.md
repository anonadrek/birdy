# Birdy Bird Scanner

AI-driven Android-app för fågelidentifiering — realtidsskanning via kamera, foto-upload, audio-ID via mikrofon, och ett rikt uppslagsverk över 839 europeiska arter.

> **Status (2026-05-30):** v1.0 släppt (`v1.0.0`, 2026-05-23) följt av onboarding-omarbetning (`v1.0.2` — 7-scens scroll-story). v1.1 Phase A (retention-hooks: Dagens fågel + streak-notis) taggad `v1.1.0-rc1`, följt av kamera-zoom + crop/rotera (`v1.1.0-rc2`). Pågående: en v1.x-respons på feedback från en erfaren testare — **DP A (robust sök) och DP B (positionering & copy: "behåll fyndet, inte bara ID:a det") är mergade till `main`**; DP C (kategori-hotfix) påbörjad, DP D–E (märken, ekologisk grupp-axel) planeras var för sig. Marketing-website live på [birdy.community](https://birdy.community) (Astro + Vercel).

## Vad du kan göra i appen idag

- **Skanna foto** via kamera (CameraX 3 fps, TFLite + AIY Birds V1, ~14 ms/inference på S23 Ultra) eller upload från galleri.
- **Identifiera via ljud** — push-to-record, 3 sek, BirdNET-Lite v2 med FlexRFFT TF Select op.
- **Uppslagsverk** över 839 nordiska/europeiska arter med foto, ljud, beskrivning, migration och säsongs-sannolikhet.
- **Robust sök** i uppslagsverket — på art, vetenskapligt namn, familj och genus; okänsligt för apostroftyp (`'`/`’`), diakriter (`ü`→`u`) och aktivt språk (cross-locale).
- **Dagbok (Field Journal)** med pappers-look, DM Serif Italic + Caveat-typografi, stampseals och plate-frames.
- **Gamification** — 25 badges, streaks, life-list, unlock-queue.
- **Premium-tier** — månatlig/årlig/livstid via Google Play Billing v8, med Restore Purchases och RSA-signature-verify.
- **Lokaliserad** på svenska och engelska (compose-resources).

## Arkitektur

Kotlin Multiplatform-app där affärslogik och UI är delad via Compose Multiplatform. v1 är Android-only; iOS-skelettet finns men aktiveras först i en senare fas.

| Modul | Innehåll |
|---|---|
| `composeApp` | Compose Multiplatform UI (delad mellan Android och framtida iOS) |
| `shared/domain` | Use cases, domänmodeller, business rules (ren Kotlin) |
| `shared/data` | SQLDelight 2.x-queries, repositories, content providers |
| `shared/ml` | `BirdClassifier` expect/actual för foto-ID, bildpreprocessing |
| `shared/audio` | BirdNET-Lite audio-ID + push-to-record pipeline |
| `shared/pdf` | `JournalPdfRenderer` expect/actual (Premium PDF-export, Plan 6b3) |
| `shared/content` | Artdatabas-loading (839 arter), gamification-regler, badge-evaluator |
| `androidApp` | Android entry point, MainActivity, plattforms-actuals |
| `iosApp` | iOS-skelett (aktiveras post-v1.0) |
| `website` | Marketing-site (Astro 5 + Tailwind v4 + i18n EN/SV), deployas till birdy.community via Vercel |

Se design-specen för detaljer: [`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`](docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md)

## Komma igång

**Krav:**
- JDK 21 (Temurin/Adoptium rekommenderat)
- Android Studio Iguana (eller senare) eller IntelliJ IDEA 2024.2+
- Android SDK 35 + build-tools
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
cd website && npm run test:i18n      # SV/EN parity check
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

**v1.0 är levererad** (`v1.0.0`, 2026-05-23). Vägen framåt: Internal Testing → Closed Testing (14d) → Play Store-launch. Billing v8 IPC-runtime-verify + go-live-flippen (`PREMIUM_OPEN_FOR_LAUNCH`) är kvar innan produktions-monetisering — se `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`.

### Efter v1.0 (pågående)

| Spår | Innehåll | Status |
|---|---|---|
| v1.0.2 | Onboarding-omarbetning (7-scens scroll-story) | ✅ taggad |
| v1.1 Phase A | Retention-hooks — Dagens fågel + söndags streak-notis + deep-links | ✅ `v1.1.0-rc1` |
| v1.1 zoom + crop | Kamera-zoom 1–10× + crop/rotera av uppladdade bilder | ✅ `v1.1.0-rc2` |
| v1.x feedback-respons | Testar-feedback i 5 delprojekt (DP A–E): **DP A sök-fix ✅, DP B positionering & copy ✅ mergade**; DP C kategori-hotfix påbörjad; DP D–E planerade | 🔧 pågår |
| v1.2 Phase B | Weekly Recap ("Veckans uppslag") — adaptiv söndagsskärm + enad push | 📋 spec + plan klara |

## Roadmap post-v1.0

- **v1.5 — "Karta & moln":** Konton, molnsynk av dagboken, karta med fynd från publika datakällor, push-notiser om sällsynta arter nära användaren.
- **v2 — "Community":** Delning av fynd, kommentarer, flöde, moderering.
- **v2.x:** Quiz/utbildningsläge, fullt offline-läge för längre exkursioner, iOS-aktivering.

## Bidragande

Specs och plans i `docs/superpowers/` är källan till sanning. Workflow: Markdown-spec (`specs/`) → implementation plan (`plans/`) → kod, med Claude Code som assistent. Detaljerade arbets-regler för AI-assistenten finns i [`CLAUDE.md`](CLAUDE.md).

## Licens

TBD (lägg till `LICENSE`-fil innan första publika release).
