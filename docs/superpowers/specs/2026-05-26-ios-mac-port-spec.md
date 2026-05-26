# Birdy — iOS + macOS port spec (pure Swift rewrite from scratch)

> **Vad det här är:** En självständig spec som låter en fräsch Claude Code-session på Mac bygga om Birdy från noll i Swift för **iOS (primärt mål, App Store)** + **macOS (sekundärt mål, Mac App Store)** — ingen återanvändning av Kotlin Multiplatform-koden från Android-versionen. Specen beskriver *vad* som ska byggas och *varför*, så att Mac-Claude kan producera en feature-identisk version som beter sig som Android-versionen men idiomatiskt Swift/Apple.
>
> **Status vid skrivning (2026-05-26):** Android-versionen är på senaste tag `v1.1.0-rc1` (commit `67ddf9e`, versionCode 116). Den är feature-frusen för v1.0-launch. Den här porten kör efter samma plan-of-plans-disciplin som Android-versionen — sekventiella planer, tagged milstolpar, device-screenshots per fas, två-stegs-review mellan tasks.

---

## Innehåll

1. [Hur du läser den här specen](#1-hur-du-läser-den-här-specen)
2. [Vad är Birdy?](#2-vad-är-birdy)
3. [Mål, scope och icke-mål](#3-mål-scope-och-icke-mål)
4. [Feature-inventory (komplett lista)](#4-feature-inventory-komplett-lista)
5. [Datamodell](#5-datamodell)
6. [Visuellt språk — Field Journal](#6-visuellt-språk--field-journal)
7. [ML-stack på Apple](#7-ml-stack-på-apple)
8. [Match-flow + thresholds](#8-match-flow--thresholds)
9. [Audio-flow](#9-audio-flow)
10. [Premium-tier + StoreKit](#10-premium-tier--storekit)
11. [Onboarding v2 — 7-scens scroll-story](#11-onboarding-v2--7-scens-scroll-story)
12. [Retention-hooks (Phase A)](#12-retention-hooks-phase-a)
13. [Lokalisering (SV + EN)](#13-lokalisering-sv--en)
14. [Privacy-löfte + license guards](#14-privacy-löfte--license-guards)
15. [Distribution + App Store-flöde](#15-distribution--app-store-flöde)
16. [Plan-of-plans för iOS-porten](#16-plan-of-plans-för-ios-porten)
17. [Arbetsflöde med Claude Code](#17-arbetsflöde-med-claude-code)
18. [Trap-katalog — iOS-equivalents](#18-trap-katalog--ios-equivalents)
19. [Vad som ska studeras i Android-repo:t](#19-vad-som-ska-studeras-i-android-repot)
20. [Första-sessions-prompt](#20-första-sessions-prompt)

---

## 1. Hur du läser den här specen

Den här specen är skriven från en Windows-session som har byggt Android-versionen. Den antar att läsaren är **Claude Code på Mac** och att användaren är **Albin** (solo-utvecklare).

**Mac-Claude ska:**
- Läsa hela specen först, även om bara enskilda sektioner verkar relevanta.
- Sedan läsa de plan-docs och spec-doc i Android-repo:t som §19 pekar ut.
- *Sedan* starta `superpowers:brainstorming` → `:writing-plans` → `:subagent-driven-development` för Plan 1 (iOS Foundation), precis som Android-versionen körde.

**Ingen kod skrivs förrän:**
1. Hela specen är läst.
2. Användaren har bekräftat tech-stack-vägval (SwiftUI, paketstruktur, IDE-konfig).
3. Plan 1 är brainstormad + skriven + godkänd av användaren.

---

## 2. Vad är Birdy?

AI-driven fågelidentifierare. Tre kärnflöden:

1. **Skanna med kamera** — realtidsklassificering i live preview (foto, 3 fps på Android) + 3-sekunders inspelning (audio).
2. **Uppslagsverk** — 839 europeiska arter med plate-illustrationer, fakta, säsongs- och migrationsdata.
3. **Fältdagbok** — sparar fynd, bygger life list, delar ut 35 badges, exporterar PDF.

**Brand:** "Birdy" — intim, kunnig följeslagare i fält. Inte "BirdNet+++" eller "Merlin Lite". Visuellt språk = pappersbaserad **Field Journal** med kursiv DM Serif Display + handskriven Caveat-marginalia + mossgrön accent. Värm pedagogisk inramning runt seriös ML.

**Användargrupp:** Bred två-lager — nybörjare som vill *lära sig* OCH entusiaster som vill ha hjälp i fält. Inte sliten "expertapp".

**Geografi:** Norden/Europa, 839 arter. ML-modellerna är globalt tränade (AIY V1 ≈ 965 klasser, BirdNET-Lite ≈ 6000) — vi filtrerar till EU.

**Marknadsföring:** Live på `https://birdy.community` (Astro 5 + Vercel, gemensam för Android + iOS).

---

## 3. Mål, scope och icke-mål

### 3.1 Mål för iOS/Mac-porten

- **Feature-paritet med Android v1.0:** Samma flöden, samma badges, samma match-thresholds, samma 839 arter, samma Field Journal-design (Apple-anpassad).
- **iOS-first design:** iPhone som primärt formfaktor (handhållen i fält). Mac som sekundär (desktop-review, journal-export, encyclopedia-browse, badge-overview).
- **Native känsla:** SwiftUI-idiom, Apple HIG där det inte krockar med Field Journal-brand. Haptics, gestures, sheet-presentations, system-share-sheet.
- **App Store-ready:** Sandboxing, App Transport Security, Privacy Manifest (Apple kräver från 2024), App Store Review-godkänd.
- **Identisk privacy-stance:** "Almost nothing collected, data stays on phone." Ingen telemetri.
- **Identisk license-stance:** BirdNET-audio gratis-feature. Premium = endast egenbyggda features (PDF, stats, badges).

### 3.2 Scope för v1.0 iOS

Lika med Android v1.0:
- Skanna (foto + audio)
- Uppslagsverk (839 arter)
- Fältdagbok (observation, life list, save flow)
- Gamification (35 badges, streaks, unlock-queue)
- Premium tier (PDF-export, season stats, premium badges)
- Onboarding v2 (7-scens scroll-story)
- Daily Bird + notifikationer (Phase A retention)
- Settings (språk, premium, debug)
- SV + EN

### 3.3 Icke-mål för v1.0

- Karta + cloud-sync (= v1.5+ på Android, samma här)
- Community/feed/comments (= v2+)
- Quiz/utbildningsläge (post-v1)
- Push-notiser om sällsynta arter nära användaren (post-v1)
- iPad-specifika multitasking-features (split-view, drag-and-drop) — funka, men inte optimera
- Apple Watch companion (post-v1)
- watchOS / visionOS targets

### 3.4 Mac-specifika icke-mål

- **Realtid-kamera-scanning på Mac via webcam** är icke-mål för v1.0. Mac-versionen får kamera-input via fil-upload (drag-and-drop OCH file picker). Realtidsläget från Android är inbyggt för "telefon i hand i fält"-formfaktorn — desktop-webcam pekad mot fönster är edge-case.
- **Audio-recording på Mac** är inkluderat (mic är standard på Mac) men UI:t prioriterar inte push-to-record-knappen — Mac-Listen-tabben är "drag in audio-fil" + "spela in från default-mic" sida vid sida.

---

## 4. Feature-inventory (komplett lista)

Detta är *vad* som ska finnas. *Hur* per plattform diskuteras i §6, §7, §8 osv.

### 4.1 Onboarding (v2 — 7-scens scroll-story)

Visas första gången appen öppnas. Replay-bar via Settings. Detaljerad spec i §11.

- Scen 1: Hero (intro till Birdy + brand-mood)
- Scen 2: Photo (kamera-scanning förklarat)
- Scen 3: Audio (push-to-record förklarat)
- Scen 4: Fältboken (dagbok + life list)
- Scen 5: Märken (badges + gamification)
- Scen 6: Privatliv (data-stays-on-phone-löftet)
- Scen 7: Namn (frivillig namn-prompt)

### 4.2 Bottom navigation (Android har 5 tabbar)

iOS-equivalent = `TabView`:
1. **Identify** (kamera/foto-scanning)
2. **Listen** (audio-scanning)
3. **Journal** (fältdagbok + life list)
4. **Encyclopedia** (839-art-uppslagsverk)
5. **Badges** (gamification, märken)

Settings finns inte som tabb — gear-ikon top-right på Identify-tabben + djuplänk från Profile-screen.

### 4.3 Identify-flowet (foto)

- Live kamera-preview (på iOS: `AVCaptureSession` + `AVCaptureVideoDataOutput`).
- 3 fps inferens (på iOS: throttla via timestamp på `captureOutput`).
- Overlay: kamera-brackets + crosshair + "Identifierar..."-text.
- Auto-throttle till 1.5 fps om p95 inference > 333 ms (på Android — kalibrera motsvarande på iOS).
- Foto-knapp för still-capture → identifierar och visar Match-flow.
- Photo-library-import (UIImagePickerController eller PhotosPicker).
- **Daily Bird-kortet** på top av screen (Phase A — se §12).
- Match-flow-routing (se §8): Match / Disambig / NoBird.

### 4.4 Listen-flowet (audio)

- "Push to record"-mic-knapp (waveform-bars animerar under inspelning).
- 3 sek max recording.
- 48 kHz mono PCM → OGG/Opus (på iOS: AVAudioEngine + AudioConverter till AAC eller m4a; OGG/Opus är ovanligare på Apple — m4a/AAC räcker).
- Identify-knapp efter rec → BirdNET-Lite (TFLite-iOS eller CoreML-konverterad).
- Result-screen visar top-1 + spectrogram + confidence + "Save to journal".

**Licensguard:** Audio-flowet får ALDRIG gate:as bakom Premium. Spec §14 + §18.

### 4.5 Journal-flowet (fältdagbok)

- Lista av observationer (datum, art-namn, fotokort, location-label, streak-info).
- Filtrera/söka (post-v1 prio).
- Tap → detalj-screen med foto, art-länk till encyclopedia, edit-note, delete.
- Save-flow (efter Match/Disambig): foto + art + note + auto-stamp-nummer (#1, #2, ...).
- Life list-sub-screen: alla observerade arter, antal observations per art.
- PDF-export (Premium) — JournalPdfRenderer-equivalent (titel/stats/arter/badges/colophon).

### 4.6 Encyclopedia-flowet (uppslagsverk)

- Index-screen: 839 arter, search + filter (familj, säsong).
- Species-detail-screen: plate-foto, namn (SV + EN + latin), beskrivning, säsongs-stapel (1–12 månader), habitat, ID-tips, ljud (om finns).
- "Have you seen?"-tracking — visar om arten finns i din journal + length-of-streak.
- Cross-länk från Match-screen → Encyclopedia-detail.

### 4.7 Badges-flowet (gamification)

- 35 totalt: 25 base + 10 premium.
- States: locked / in-progress / unlocked.
- BadgeRule-evaluator: kollar antal observationer, art-diversity, streak-längd, säsongs-spread, premium-aktivitet osv.
- UnlockQueue: visar pop-up när badge unlock:as, sparar i kö om flera triggar samtidigt.
- Badges-screen: grid av StampSeals (locked/in-progress/unlocked), tap → detalj med kriterier.

### 4.8 Premium-tier

Features bakom Premium:
- **PDF-export av fältdagboken** (titel + stats + arter + badges + colophon-sidor + share via system-share-sheet)
- **Season Statistics-screen** (bar/line/donut-chartar över observationer per månad, art-mix, streak-historik)
- **10 premium-badges** (utöver 25 base) — märken med högre kriterier, visuellt copper-pill-markerade

Features som inte är bakom Premium:
- Camera scanning (alltid gratis)
- Audio scanning (alltid gratis — license-tvång, se §14)
- Encyclopedia
- Fältdagbok + life list
- 25 base badges
- Daily Bird + notifikationer

**StoreKit:** Se §10.

### 4.9 Settings

- Profilnamn (frivillig, sparas lokalt)
- Språk (SV / EN)
- Premium-status (knapp → PremiumScreen ELLER restore purchases)
- "Visa introduktion igen" → replay onboarding v2
- Notifikationer (toggle daily bird + streak risk)
- Privacy/Terms-länkar (öppnar `birdy.community/legal/{privacy,terms}/`)
- Webbplats-länk (`birdy.community`)
- Feedback-länk (`feedback@birdy.community` mailto — bridge till albin@abrahamssons.se tills domän-mailen är live)
- Debug-sektion (om `#DEBUG`): force-run workers, skip premium override (Billing-verify), clear DB

### 4.10 Notifikationer

- **Daily Bird** — schemaläggs kl 08:00 lokal tid, visar dagens art (deterministisk seed på datum).
- **Streak Risk** — schemaläggs kl 20:00 om användaren har aktiv streak men ingen observation idag.

På iOS: `UNUserNotificationCenter` + `UNTimeIntervalNotificationTrigger` eller `UNCalendarNotificationTrigger`. Lokala notifikationer, ingen push.

---

## 5. Datamodell

Modellen är direkt port:ad från Android (SQLDelight) till Swift (rekommenderat: Core Data ELLER SQLite.swift ELLER GRDB.swift). Min rekommendation: **GRDB.swift** — det är moget, snabbt, har `@Observable`-stöd, och migrationsmönstret är likt SQLDelight.

### 5.1 Entities

**Species** (statisk, läses från bundled YAML i app-resources):
- `id: String` (Wikidata Q-ID, t.ex. `Q25485`)
- `commonName: [String: String]` (locale → name)
- `latinName: String`
- `familyId: String`
- `seasonMonths: [Int]` (1–12)
- `migrationProfile: Enum`
- `heroImagePath: String`
- `description: [String: String]`
- `idTips: [String: String]`

**Observation** (user-created, persisted):
- `id: UUID`
- `speciesId: String?` (nullable för "unknown/skip")
- `photoPath: String?` (file URL)
- `audioPath: String?` (file URL)
- `timestamp: Date`
- `note: String?`
- `stampNumber: Int` (auto-increment per user)
- `latitude: Double?` (nullable, för v1.5+)
- `longitude: Double?` (nullable, för v1.5+)
- `locationLabel: String?` (nullable, för v1.5+)

**BadgeUnlock**:
- `badgeId: String`
- `unlockedAt: Date`
- `state: Enum (Locked, InProgress, Unlocked)`
- `progress: Int` (för in-progress badges)
- `criteriaSnapshot: String?` (JSON-blob med vad som triggade)

**DailyBird**:
- `date: Date`
- `speciesId: String`
- `seasonTag: Enum`
- `shownAt: Date?`

**DailyBirdHistory** (migration 4 från Android):
- `date: Date`
- `speciesId: String`

**UserPreferences** (DataStore på Android → UserDefaults eller GRDB-table på iOS):
- `displayName: String?`
- `language: Enum (sv, en, systemDefault)`
- `hasCompletedOnboarding: Bool`
- `notificationsDailyBirdEnabled: Bool`
- `notificationsStreakRiskEnabled: Bool`
- `premiumState: Enum (NotActive, Active(YEARLY), Active(LIFETIME))`
- `lastObservationDate: Date?`
- `currentStreakDays: Int`

### 5.2 Content-pipeline

I Android-repo:t finns 839 art-YAMLer i `shared/content/src/commonMain/resources/species/` + en `species_list.yaml`. Strukturen är:

```yaml
id: Q25485
common_name:
  sv: "Talgoxe"
  en: "Great Tit"
latin_name: "Parus major"
family_id: paridae
season:
  jan: high
  feb: high
  # ... mar..dec
migration: resident
description:
  sv: "..."
  en: "..."
id_tips:
  sv: "..."
  en: "..."
hero_image: "Q25485/hero.webp"
```

**Port-jobb:** Kopiera hela `shared/content/.../species/`-mappen + alla `*.webp`-bilder till iOS-app-resources. YAML-parsing i Swift via `Yams`-paketet (eller serialisera till JSON build-time och använd JSONDecoder). Plate-foton är ~50–150 KB per art = ~50–80 MB total.

**Skapa inte ny content-pipeline** — kopiera bara över befintliga YAMLer. Content är samma över Android + iOS för att undvika divergens.

### 5.3 Migrations

Android har 4 migrations i SQLDelight. iOS-versionen börjar på "Migration 4 = baseline" — alla tabeller skapas i en migration. Använd GRDB:s `DatabaseMigrator` med `registerMigration("v1") { db in ... }`.

---

## 6. Visuellt språk — Field Journal

**Canonical app-wide tema** från Android Plan 7c (locked 2026-05-10). Måste reproduceras pixel-nära på iOS.

### 6.1 Färgtokens (`Color.swift`-fil med SwiftUI Color-extensions)

| Token | Hex | iOS-namn | Roll |
|---|---|---|---|
| PaperBg | `#EFE7D6` | `Color.paperBg` | Pappersbakgrund |
| PaperEdge | `#E5DCC7` | `Color.paperEdge` | Edge/texture-shadow |
| MarginaliaInk | `#3F4F30` | `Color.marginaliaInk` | Caveat-text, sub-lines |
| AccentCopper | `#A8552D` | `Color.accentCopper` | CTA, aktiv tab, stat-siffror, copper-pills |
| StampNavy | `#1F3A5F` | `Color.stampNavy` | StampSeal-states |
| HeroMossMid | `#5C6E48` | `Color.heroMossMid` | Mossgrön mellan-stop |
| HeroMossDeep | `#3F4F30` | `Color.heroMossDeep` | Mossgrön djup-stop |
| HeroMossShadow | `#2A3520` | `Color.heroMossShadow` | Mossgrön skugga (Listen/Premium hero) |

**Dark mode:** Inte stöttat i Android v1.0 (papper-temat bygger på ljus färgpalett). Lås light-mode på iOS också via `.preferredColorScheme(.light)` på root. Mac-versionen får samma — eller diskutera dark-variant som post-v1.

### 6.2 Typografi

| Roll | Font | iOS-namn |
|---|---|---|
| Rubriker | DM Serif Display Italic | `DMSerifDisplay-Italic` |
| Marginalia + sub-lines | Caveat (handskriven) | `Caveat-Regular` |
| Body | Inter (system sans) | `Inter-Regular` / system `San Francisco` som fallback |

Fonts bundlas via Info.plist `UIAppFonts`. Filer ligger i Android-repo:t under `composeApp/src/commonMain/composeResources/font/`. Kopiera över.

### 6.3 Custom components

| Component | Beteende | iOS-fil (förslag) |
|---|---|---|
| `JournalHeadline` | Parsar `*ord*` i text → renderar segmentet i Caveat-italic med lätt rotation (–2° till +2°). | `JournalHeadline.swift` (Text + AttributedString + custom-modifier) |
| `JournalIntro` | Eyebrow-text + JournalHeadline + ornament-rule + sub-line. | `JournalIntro.swift` (VStack) |
| `PaperBackground` | Pappers-bg med dot-texture. | View-modifier `.paperBackground()` |
| `StampSeal` | Cirkel-stämpel med 3 states (locked/in-progress/unlocked). Locked = grå outline, in-progress = copper-stripes, unlocked = navy-fill. | `StampSeal.swift` |
| `PlateFrame` | Naturalist-foto-frame (kanten ser ut som tryckt platta). | `PlateFrame.swift` |
| `OrnamentRule` | ❦ + horisontellt streck. | `OrnamentRule.swift` |
| `WaveformBars` | Animerade staplar under audio-record. | `WaveformBars.swift` |
| `ShimmerSweep` | Modifier som sveper en gradient-glow över en view (DailyBirdCard, PremiumHeroCard). | `.shimmerSweep()` |

### 6.4 Layout-principer

- Generös vertikal whitespace (papper "andas").
- Headlines har alltid Caveat-accent-segment om mer än ett ord.
- Mossgrön gradient som hero-bakgrund på Listen + Premium screens.
- Copper-pills för premium-status, aktiv tab, badge-counter.
- Inga skarpa hörn — använd `cornerRadius(12)` på kort, `cornerRadius(20)` på modaler.

---

## 7. ML-stack på Apple

### 7.1 Foto-klassificering — AIY Birds V1

Android använder TensorFlow Lite med AIY Birds V1 (uint8-quantized MobileNetV2, 965 klasser). På Galaxy S23 Ultra: ≈ 14 ms/inference.

**På iOS — två vägar:**

**A) CoreML-konvertering (rekommenderas):**
- Konvertera TFLite-modellen till CoreML via `coremltools` (Python). Output: `.mlmodel` eller `.mlpackage`.
- Embedda i app-bundle som compiled `.mlmodelc`.
- Anropas via `Vision`-frameworket (`VNCoreMLModel` + `VNCoreMLRequest`).
- Snabbast på Apple-silicon. Använder Neural Engine när möjligt.
- ML-modell-filer är i Android-repo:t under `asset-pack/src/main/assets/`. Hämta dem därifrån som källa.

**B) TFLite-iOS:**
- `TensorFlowLiteSwift` pod / SwiftPM-paket. 
- Anropa modellen direkt utan konvertering.
- Mer kontroll, men inte lika optimerat som CoreML.
- Funkar om vi vill ha *exakt* samma modell-output som Android (CoreML-konvertering kan introducera minimal numerisk drift).

**Rekommendation:** Börja med A (CoreML). Verifiera top-3-accuracy mot xeno-canto-eval-set (samma som Android använde). Om drift > 2% på top-3, byt till B.

### 7.2 Audio-ID — BirdNET-Lite

Android använder BirdNET-Lite v2 + `tensorflow-lite-select-tf-ops:2.16.1` för FlexRFFT TF Select op.

**På iOS:**

CoreML-konvertering är **inte trivial** för BirdNET-Lite eftersom modellen använder FlexRFFT (TF Select). Möjliga vägar:
- **A) TFLite-iOS med Select-ops:** TFLite Swift har `TensorFlowLiteSelectTfOps`-target — kräver lite mer build-config men funkar.
- **B) Ersätt FlexRFFT med native iOS Accelerate-FFT** + rerout till en CoreML-kompatibel model-graf. Mycket arbete.

**Rekommendation:** A. Använd TFLite-iOS med Select-ops för audio. Mata in 48 kHz mono PCM, get spectrogram-features ut, klassificera via modellen.

### 7.3 Licens

- AIY Birds V1: Apache 2.0 → fri användning, inklusive bakom Premium (men vi gate:ar inte foto bakom premium ändå).
- **BirdNET-Lite: CC BY-NC-SA 4.0** → kommersiell paywall = licensbrott. **Audio-ID ska ALDRIG gate:as bakom Premium på iOS heller.** Se §14.

---

## 8. Match-flow + thresholds

Identiska thresholds som Android Plan 7d:

- **Match** (visa Match-screen): top-1 confidence ≥ 0.55
- **Disambig** (visa top-3 + "Inte rätt? Spara som okänd"): top-1 < 0.55 OCH top-3 spridning < 0.15
- **NoBird** (visa "Ingen fågel hittad — försök igen"): top-1 < 0.30

### Screens

- **MatchScreen:** Plate-foto av top-1-arten + name + confidence-bar + "Spara i journalen" + inline-note-fält + "Se i uppslagsverket"-länk.
- **DisambigScreen:** Top-3 i lista med foton + confidence-bars + tap → MatchScreen för vald. "Spara som okänd"-knapp längst ner.
- **NoBirdScreen:** Tom-state-illustration + "Prova igen" + "Kanske skymd?" + tips-text.

Override för testning: hidden gesture i debug → tvinga specifik klass (förenklar test-image-infra).

---

## 9. Audio-flow

### 9.1 Permissions

iOS kräver `NSMicrophoneUsageDescription` i Info.plist. Be om permission **just-in-time** när användaren tap:ar mic-knappen, inte vid app-start. UX-mönster: tryck mic → om denied, visa modal "Mikrofon krävs för att identifiera fågelsånger — gå till Inställningar".

### 9.2 Recording

- AVAudioSession-kategori: `.playAndRecord` med mode `.measurement`.
- Sample rate: 48 kHz.
- Channels: mono.
- Format: PCM_16.
- Längd: 3 sek max (auto-stop).
- Output: m4a eller wav (iOS-natural), inte OGG/Opus (mer Linux-/Android-flavor).

### 9.3 Inferens

- PCM-buffer → BirdNET-Lite via TFLite-iOS.
- Output: 6000 classes × scores → filtrera till europeiska 839 → top-1 + top-3 → routa via match-thresholds (§8).

### 9.4 UI

- `RecordingMicButton` med pulsande copper-ring under recording.
- `WaveformBars` (10 staplar) animerar med real-time amplitude.
- Tryck-och-håll OCH tap-toggle båda fungerar (Android har båda; iOS-användare förväntar sig oftare tap).
- 3-sec countdown-progress-ring runt mic-knappen.
- Efter inspelning: result-card med top-1 + spectrogram-thumbnail + "Spara" + "Spela upp igen".

---

## 10. Premium-tier + StoreKit

### 10.1 Tier-struktur

Identisk med Android:

- **YEARLY-subscription** — auto-renewable yearly.
- **LIFETIME-purchase** — non-consumable one-time.
- LIFETIME vinner över YEARLY om båda finns (precedens).

### 10.2 Prissättning

- Android har pris satt i Play Console (uppdatera per region).
- iOS: motsvarande prisstege i App Store Connect. **Sätt initialt samma SEK/USD-pris som Android.** Apple tar 15% (small business) eller 30% (1M+ rev/year).

### 10.3 StoreKit 2

- Använd modern StoreKit 2 (`StoreKit` + `@Observable`-store).
- `Product.products(for:)` för att hämta tillgängliga IAPs.
- `product.purchase()` → `VerificationResult<Transaction>`.
- `Transaction.currentEntitlements` för restore-purchases-flow.
- `Transaction.updates` async stream för server-notifications.

### 10.4 Verification

Apple verifierar StoreKit-transactions automatiskt via signed JWS. Ingen RSA-signature-verify som Android behöver med Play Licensing. Ren `if case .verified(let transaction) = result`.

### 10.5 Premium UI (samma som Android)

- **PremiumScreen** — hero med mossgrön gradient, 3 features (PDF, Stats, 10 badges), pris-pills (YEARLY + LIFETIME), CTA-knapp, "Restore Purchases"-länk längst ner.
- **Per-tab teasers** — copper-pill med "Premium"-badge på låsta features.
- **Cold-start modal** — visas 1 gång under första 7 dagarna, throttle:as till max 1 visning per 24h.

### 10.6 Launch-period premium-öppen

Android har `PREMIUM_OPEN_FOR_LAUNCH=true` i build.gradle som hardcodar `Active(LIFETIME)` för alla under closed testing + initial production. På iOS: motsvarande `IS_LAUNCH_PERIOD: Bool = true` i en `LaunchConfig.swift` — när true, PremiumStore returnerar alltid `Active(.lifetime)`. Flippas till `false` när StoreKit-flowet är runtime-verifierat (samma blocker som på Android, se Android `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md`).

---

## 11. Onboarding v2 — 7-scens scroll-story

Detaljerad spec finns i Android-repo:t: `docs/superpowers/specs/2026-05-25-onboarding-scroll-story-design.md`. **Läs den för full kontext.**

### 11.1 Struktur

`TabView` med `.page`-style (iOS-equivalent av Android `VerticalPager`). 7 sidor, vertikal scroll-snap, `pageOffset`-driven animationer per scen.

### 11.2 Scener

1. **Hero** — wordmark + tagline + "scroll down to explore"-hint.
2. **Photo** — PlateFrame med exempel-bild + crosshair + slam-stamp (visar "Identify-flow").
3. **Audio** — WaveformBars-animation + "lyssna efter ljud" copy.
4. **Fältboken** — exempel-journal-page med två observations + stamp-sigill.
5. **Märken** — grid av 6 StampSeals (mix av locked/in-progress/unlocked).
6. **Privatliv** — OfflineShield-icon + "Data stays on phone"-löfte.
7. **Namn** — frivilligt namn-input → sparas i UserPreferences → start app.

### 11.3 Copy-stil

Hybrid Field Journal: poetisk DM Serif-headline + konkret USP-sub. Exempel:
- Headline: "Lyssna efter *gömda* röster"
- Sub: "Spela in 3 sekunder. Vi identifierar 6000 fåglar."

### 11.4 Replay-mode

Settings → "Visa introduktion igen" → öppnar samma flow med `isReplay = true` flag. Skippar DataStore-writes (har redan completed=true), tillåter att stänga utan att markera done igen.

### 11.5 Trap från Android-verify (viktig)

På Android lade vi crosshair + stamp + kamera-brackets i fel container — de hamnade i hela PlateFrame istället för image-slot:en. Resultat: stamp shiftade 9dp uppåt och hamnade ovanför crosshair. Fix: lägg dem inuti image-slot:en, sätt `name = nil` på StampSeal så bara cirkeln renderas. **På iOS: undvik samma trap genom att använda `ZStack { image; overlays }` strikt inom photo-frame-boundsen.**

---

## 12. Retention-hooks (Phase A)

Implementerat i Android `v1.1.0-rc1` per spec `docs/superpowers/specs/2026-05-25-v1-1-phase-a-retention-hooks.md`. Komponenter:

### 12.1 Daily Bird

- Deterministisk seed på `date.formatted("yyyy-MM-dd")` → väljer en art från `species_list.yaml`.
- Visas på Identify-tab som top-kort: blurred hero-foto + art-namn + season-tag + tap → encyclopedia-detail.
- Sparas i `DailyBirdHistory` (för senare statistik).
- Notifikation kl 08:00 lokal tid: "Dagens fågel: Talgoxe — kolla in den!"

### 12.2 Streak

- `currentStreakDays` i UserPreferences uppdateras vid varje observation som sparas.
- Reset om > 24h utan observation.
- Visas som copper-pill längst upp på Identify-tab + på Profile-screen.

### 12.3 Streak Risk-notifikation

- WorkManager på Android → `BGAppRefreshTask` (Background Tasks framework) på iOS.
- Schemalägg kl 20:00 lokal tid.
- Triggar om: `currentStreakDays > 0` AND ingen observation idag.
- Notifikation: "Din streak går ut! Spara minst en observation idag."

### 12.4 Shimmer-sweep

Modifier som sveper en gradient-glow över DailyBirdCard + PremiumHeroCard (subtle attention-pull). Implementation: `LinearGradient`-mask som animeras x-axiellt över 2s med 5s delay i loop.

---

## 13. Lokalisering (SV + EN)

- **SV är primärt** (Sverige först, Norden second).
- **EN är sekundärt** (Europa-launch).
- Alla user-facing strängar via `.strings`-filer + `String(localized:)`-API.
- Art-namn kommer från `species` YAML (`common_name.sv` / `common_name.en`).
- Datum + nummer-formattering: `Locale.current` styr.
- Auto-fallback till EN om SV saknas.

### Locale picker i Settings

- "Systemets språk" (default)
- "Svenska"
- "English"

Användarens val sparas i UserPreferences och appliceras via `LocaleManager`-singleton som muterar `Bundle.main.preferredLocalizations`-lookup.

### Trap

På Android har vi compose-resources-traps: `%%` processas inte som `%`-escape, `\'` unescape:as inte. På iOS är `.strings`-filer mer förlåtande, men: **var noga med `%@` vs `%d` vs `%lld`** — Apple kraschar appen vid mismatch. Använd `String(localized:)` med `defaultValue` så Xcode 15+ extrakterar automatiskt till `.xcstrings`-katalog.

---

## 14. Privacy-löfte + license guards

### 14.1 Privacy-löftet

> "Almost nothing collected, data stays on phone."

Verifierat i Android-revisionen 2026-05-20 (6-agents fältrevision, rapport i `docs/superpowers/research/2026-05-20-play-store-audit.md`).

**Konkret innebörd för iOS:**
- **Ingen analytics-SDK** (Firebase Analytics, Mixpanel, Amplitude → alla förbjudna).
- **Ingen telemetri.** Inte heller "anonym usage data".
- **Ingen cloud-backend för inference** — allt ML körs on-device via CoreML/TFLite.
- **Ingen cloud-storage av observations** — allt lokal Core Data/GRDB.
- **Inga 3rd-party-trackers** — inga ad-SDKer, inga attribution-SDKer.
- **Privacy Manifest** (`PrivacyInfo.xcprivacy`) ska deklarera: ingen data collection, inga tracking domains. Apple kräver det från 2024.
- **NSPrivacyAccessedAPITypes:** Endast `Disk Space` (för PDF-export-staging), `User Defaults` (för UserPreferences). INGET `Active Keyboard`, `File Timestamp`, `System Boot Time` (alla har "tracking"-kategori).

**App Store Connect Data Safety-formulär:** Allt "Data not collected". Mirror av Android Play Console-formuläret som ligger i `docs/play-store/data-safety-form.md`.

### 14.2 BirdNET-licensguard

**BirdNET-Lite-modellen är CC BY-NC-SA 4.0 (NonCommercial).** Premium-tier är kommersiell paywall. Att gate:a audio-features bakom Premium = licensbrott.

**Mekanisk guard på Android:** Unit test `BirdNetLicenseGuardTest` i `composeApp/src/androidUnitTest/kotlin/se/birdy/app/ui/listen/` walkar `ui/listen/` + `ui/audio/` och failar om någon fil refererar `PremiumState`/`effectivePremiumActive`/`isPremiumActive`.

**På iOS:** Implementera en motsvarande unit test som söker `Listen/`- + `Audio/`-mapparna för referenser till `PremiumStore`, `isPremiumActive`, `premiumState`. XCTestCase som walkar filerna via `FileManager`.

```swift
func testListenAndAudioNeverReferencePremium() throws {
    let projectRoot = URL(fileURLWithPath: #file).deletingLastPathComponent()
        .deletingLastPathComponent().deletingLastPathComponent()
    let dirs = ["Birdy/Features/Listen", "Birdy/Features/Audio"]
    let forbidden = ["PremiumStore", "isPremiumActive", "premiumState"]
    var offenders: [String] = []
    for relativeDir in dirs {
        let dir = projectRoot.appendingPathComponent(relativeDir)
        let enumerator = FileManager.default.enumerator(at: dir, includingPropertiesForKeys: nil)!
        for case let url as URL in enumerator where url.pathExtension == "swift" {
            let text = try String(contentsOf: url)
            for token in forbidden {
                if text.contains(token) {
                    offenders.append("\(url.lastPathComponent) contains '\(token)'")
                }
            }
        }
    }
    XCTAssertTrue(offenders.isEmpty, "BirdNET license guard tripwire: \(offenders)")
}
```

Inkludera i CI för iOS-bygget. Detta är icke-förhandlingsbart.

---

## 15. Distribution + App Store-flöde

### 15.1 Apple Developer-konto

- Albins personliga Apple ID (existerar redan).
- Apple Developer Program: $99/år. Aktivera om inte gjort.
- App Store Connect: skapa app "Birdy" + bundle ID `com.birdy.app` eller `community.birdy.app` (matcha Android `se.birdy.android` i anda men inte exakt — iOS bundle-IDs är globala).

### 15.2 TestFlight

iOS-equivalent av Internal/Closed Testing.

- **Internal Testing (Apple TestFlight):** Upp till 100 testare (anpassade Apple-konton). Build distribueras till alla utan review.
- **External Testing (Apple TestFlight):** Upp till 10,000 testare via public link eller email-invite. Kräver Apple Beta App Review (1–2 dagar).

### 15.3 App Store Review

- Submit till review via App Store Connect.
- Review-tid: typiskt 24–48h.
- Vanliga fail-reasons för apps som vår:
  - Privacy Manifest saknas/ofullständig → komplettera.
  - Tracking-permissions promptar utan att vi trackar → ta bort permission requests vi inte använder.
  - StoreKit-restore-purchases-knapp saknas → kolla §10.3.
  - Audio-permission utan tydlig användningsförklaring → uppdatera `NSMicrophoneUsageDescription`.

### 15.4 Mac App Store

- Samma App Store Connect-app, separat platform-build.
- **Mac Catalyst-väg:** kompilera iOS-app som körs på Mac (snabbaste vägen).
- **macOS-native-väg:** separat target med SwiftUI för Mac (mer Mac-känsla). Min rekommendation: **Mac Catalyst för v1.0**, native som v1.5+.

### 15.5 Distribution-artefakter

- Marketing-website (`birdy.community`) finns redan — adda en iOS-section.
- Privacy Policy + Terms: använd existerande på `birdy.community/legal/{privacy,terms}/` (samma policy gäller iOS).
- App Store Connect Privacy Labels: Allt "Data Not Collected".
- App Store Connect screenshots: kräver för iPhone 6.7" + 6.1" + 5.5" (legacy) + iPad 12.9" + 11" + macOS 16:10. Mirror Android-screenshot-flowet men på iOS-simulators.
- App Store Connect promo-text + description: använd existerande store-listing från `docs/play-store/store-listing-{sv,en}.md` som start.

---

## 16. Plan-of-plans för iOS-porten

Följ samma disciplin som Android: sekventiella planer, tagged milstolpar, device-screenshots per fas. Förslag:

| # | Plan | Mål |
|---|---|---|
| iOS-1 | Foundation | Tom Xcode-projekt, SwiftUI app-skelett, GRDB.swift, font-loading, ColorTokens, Inter+DMSerifDisplay+Caveat bundled. CI på GitHub Actions med `xcodebuild`. |
| iOS-2a | Content pipeline | Kopiera 839 art-YAMLer + plate-foton från Android-repo. YAML→JSON build-time. SpeciesRepository. |
| iOS-2b | Encyclopedia | EncyclopediaListView + SpeciesProfileView. Search + filter. SV+EN-toggle. |
| iOS-3 | Match-flow stubs | MatchScreen + DisambigScreen + NoBirdScreen med fake-data. ThresholdRouter. |
| iOS-4a | Camera UI + FakeClassifier | AVCaptureSession + live preview + 3 fps frame-tap → FakeClassifier returnerar deterministic match → Match-screen. |
| iOS-4b | Real CoreML (foto) | Konvertera AIY V1 till CoreML. Verifiera top-3-accuracy ≥ 70% mot eval-set. Integrera i Camera-flow. |
| iOS-5a | Journal | Save observation från Match-screen. JournalListView + ObservationDetailView. Life list. |
| iOS-5b | Gamification | 35 BadgeRules. UnlockQueue. BadgesScreen. BadgeStringMap (SV+EN). |
| iOS-6 | Foundation (release-mekanik) | App icon. Splash. R8-equivalent (bitcode/swift-optimize). App Store metadata-stubs. |
| iOS-7a | Field Journal-design system | All visual-language-komponenter polerade (JournalHeadline, StampSeal, PlateFrame, OrnamentRule, paperBackground). |
| iOS-7b–7e | Redesign Skärmar | Listen/Audio + Encyclopedia + Badges + Premium-screens i Field Journal-stil. |
| iOS-8 | Audio-ID via BirdNET | TFLite-iOS + BirdNET-Lite-modell. AudioPermissionFlow. RecordingMicButton + WaveformBars. **License guard test in place.** |
| iOS-9 | Premium-tier + StoreKit 2 | PremiumScreen. StoreKit purchase + restore. Per-tab teasers. Cold-start modal. `IS_LAUNCH_PERIOD` flag. |
| iOS-10 | Onboarding v2 | 7-scens TabView. Replay-mode. Settings-replay-toggle. |
| iOS-11 | Phase A retention | DailyBirdCard. StreakRiskNotification (BGTask). ShimmerSweep. DB-migration baseline. |
| iOS-12 | TestFlight launch | Build → upload → Internal Testing → External Testing (14d) → Production submit. |

**Varje plan ska lämna projektet i ett byggbart, testbart tillstånd:** `xcodebuild test -scheme Birdy -destination "platform=iOS Simulator,name=iPhone 15"` ska gå grönt på `main`.

**Plan iOS-1 (Foundation) brainstormas + skrivs först av Mac-Claude.** Den specen blir den första plan-doc i `docs/superpowers/plans/`-mappen på iOS-repo:t (eller iOS-mappen i samma repo, beroende på user-val — se §17.2).

---

## 17. Arbetsflöde med Claude Code

### 17.1 Kommunikationsspråk

Användaren skriver och får svar på **svenska**. Kod, commits, plan-docs, identifierare på engelska enligt etablerad konvention.

### 17.2 Repo-strategi (beslut för Mac-Claude)

Två alternativ:

- **A) Nytt repo `birdy-ios`** — clean slate, ingen risk för cross-platform-förvirring. Min rekommendation. Cross-pekare till `anonadrek/birdy` (Android-repo) för spec-/plan-doc-källor.
- **B) Samma repo, iOS-mapp** — `ios/`-folder i samma repo. Delar `docs/` + content-pipeline. Risk: developer-pain med dual-build-tooling.

**Diskutera med Albin i första Mac-sessionen** vilket han föredrar. Lägg det som första brainstorming-fråga i Plan iOS-1.

### 17.3 Skills (superpowers)

Använd samma disciplin som Android-versionen:

- **`superpowers:brainstorming`** → varje plan börjar här. Inga undantag.
- **`superpowers:writing-plans`** → skriv ut planen + task-uppdelning innan kod-skrivande.
- **`superpowers:subagent-driven-development`** → exekvera planen task-för-task med review-checkpoints.
- **`superpowers:test-driven-development`** → TDD-disciplin för logik (badge-rules, threshold-routing).
- **`superpowers:requesting-code-review`** → mellan tasks.
- **`superpowers:verification-before-completion`** → kör `xcodebuild test` innan task markeras klar.

### 17.4 Autonomi-direktiv (samma som Android)

**"Don't ask me for permission to run anything."** Commits, push, xcodebuild-kommandon, file-edits enligt plan körs utan bekräftelse. Vid scope-creep i review: fixa autonomt (soft-reset + re-commit). Undantag: blockerare som kräver fysisk åtkomst (iPhone, simulator) eller tredjepartsbeslut (App Store Connect-UI) — där rapporteras status.

### 17.5 Två-stegs-review mellan tasks

1. **Spec-konformitet:** Gör koden det plan-task:en kräver? Inga skipade krav?
2. **Kvalitet/säkerhet:** Edge cases? Regressions? Tester passar? Privacy-stance intakt? License-guard-tests gröna?

### 17.6 Modell-strategi

| Uppgift | Modell |
|---|---|
| Brainstorming, design, arkitektur, code review | Opus 4.7 (senaste) |
| Implementer-subagents | Sonnet 4.6 |
| Snabba lookups | Haiku 4.5 |

---

## 18. Trap-katalog — iOS-equivalents

Listan med Android-traps från Android-repo:ts `CLAUDE.md` + auto-memories, översatt till iOS-context:

- **Bundle-resource-loading:** Filer i `Resources/`-foldern är inte automatiskt tillgängliga — måste vara i "Copy Bundle Resources" build-phase. Använd `Bundle.main.url(forResource:withExtension:)`-API:t med try/catch, inte force-unwrap.
- **`AVCaptureSession.startRunning()` får inte köras på main thread** — kasta till bakgrund-queue, annars UI-jank.
- **`AVAudioSession`-kategori måste sättas innan `AVAudioEngine.start()`** — annars kastas `OSStatus -50`.
- **Privacy-permissions är promptade lazy** — `NSMicrophoneUsageDescription` i Info.plist är obligatoriskt + visas första gången `requestRecordPermission` anropas. Om Info.plist-key saknas → silent fail.
- **CoreML-modeller måste kompileras till `.mlmodelc`** vid build — Xcode gör det automatiskt om filen ligger i target. Om manuellt importerad: kör `xcrun coremlcompiler compile`.
- **GRDB.swift migration-ordning är inte commutativ** — registrera migrations i exakt ordning de ska köras. Lägg aldrig till en migration "i mitten" — alltid append.
- **StoreKit Configuration File för local testing** — utan en `.storekit`-fil i schemet kan du inte testa purchases lokalt. App Store Connect sandbox-account krävs för real testing.
- **TestFlight-builds har annan bundle-version än release** — använd `CFBundleShortVersionString` för marknadsföring (1.0.0), `CFBundleVersion` för build-nummer (incrementing).
- **`@Published`/`@Observable`-mutationer från bakgrundstrådar** → kasta till main: `await MainActor.run { ... }`.
- **iOS Simulator har ingen mic** för audio-record-test → använd "Device → Microphone → External Audio Input" + hosthardware-mic, ELLER kör på physical device. Testa endast UI-flow i simulator.
- **iOS Simulator har ingen kamera** → använd "Device → Camera → Photo" för still-images-import. Live-preview kräver physical device.
- **Mac Catalyst vs native macOS:** Catalyst ger iOS-app som körs på Mac (snabbt MVP), men har quirks med menu bar + multiple windows. Native macOS-target kräver separata SwiftUI-views för många screens. Börja Catalyst.
- **iOS Push-permissions:** Lokala notifikationer kräver `UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])`. Promptar användaren första gången. Be lazy, inte vid app-launch.
- **Background tasks är RIGOROUSLY rate-limited på iOS** — `BGAppRefreshTask` får ~30 sek per körning, schemaläggs av OS based on usage. Inte garantera exakt timing av StreakRiskNotification — bara "någon gång på kvällen".

---

## 19. Vad som ska studeras i Android-repo:t

Android-repo:t (`https://github.com/anonadrek/birdy`) är canonical source för design-, plan- och produkt-beslut. Klona det till sidan av iOS-repo:t på Mac:n och läs:

### 19.1 Topp-prioritet (läs först)

1. **`CLAUDE.md`** — projektets canonical guide. Översikt, beslut, vanliga kommandon, traps.
2. **`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`** — original v1-design-spec.
3. **`docs/project-overview.md`** — projektöversikt för Claude.ai web (samma info, annan packaging).

### 19.2 Plan-of-plans-arkivet (läs per plan)

`docs/superpowers/plans/` innehåller varje plan-doc med tasks, akceptanskriterier, beslut. Läs när du börjar motsvarande iOS-plan:

- iOS-1 Foundation → `2026-04-30-v1-01-foundation.md`
- iOS-2a/b Content pipeline → `2026-05-02-v1-02a-content-pipeline.md` + content-backfill-runbook
- iOS-3 Encyclopedia → `2026-05-04-v1-03-encyclopedia.md`
- iOS-4a Camera UI → `2026-05-05-v1-04a-camera-ui.md`
- iOS-4b Real ML → `2026-05-07-v1-04b-real-tflite.md`
- iOS-5a Diary → `2026-05-05-v1-05a-diary.md`
- iOS-5b Gamification → `2026-05-06-v1-05b-gamification.md`
- iOS-7a Field Journal-design → `2026-05-09-v1-07c-field-journal.md`
- iOS-7d Match-flow → `2026-05-12-v1-07d-match-flow.md`
- iOS-7e Premium-tier → `2026-05-12-v1-07e-premium-tier.md`
- iOS-8 Audio → `2026-05-20-v1-06b2-audio-id.md`
- iOS-9 Billing + launch-prep → `2026-05-16-v1-06b1-billing-launch-prep.md`
- iOS-10 Onboarding v2 → `2026-05-25-onboarding-v2-scroll-story.md` + spec `2026-05-25-onboarding-scroll-story-design.md`
- iOS-11 Phase A retention → `2026-05-25-v1-1-phase-a-retention.md` + spec `2026-05-25-v1-1-phase-a-retention-hooks.md`
- Production-launch → runbook `2026-05-26-billing-verify-and-go-live.md`

### 19.3 Reference-material

- **Visuellt språk:** alla skärmdumpar i `docs/superpowers/screenshots/v0.7.0c-field-journal/`, `v0.8.0-rc1/`, osv.
- **Content:** alla 839 art-YAMLer i `shared/content/src/commonMain/resources/species/` + plate-foton.
- **ML-modeller:** `asset-pack/src/main/assets/` (AIY V1 + BirdNET-Lite).
- **Strings:** `composeApp/src/commonMain/composeResources/values{,-sv}/strings.xml` — alla user-facing strängar SV + EN.
- **Play Store-artefakter:** `docs/play-store/{privacy-policy,terms,store-listing-{sv,en},data-safety-form}.md` — kan återanvändas för App Store-listing (justera plattform-namn).

### 19.4 Trap-katalog & lessons

- **`CLAUDE.md`** "Trap-katalog"-sektionen.
- **Auto-memories** ligger lokalt på Windows-maskinen i `~/.claude/projects/.../memory/` — **följer INTE med**. Kopiera över specifika filer om de behövs. Indexet är `MEMORY.md` i samma mapp.

---

## 20. Första-sessions-prompt

Klistra in detta i Mac-Claude:s första prompt:

```
Jag startar nu Mac/iOS-porten av Birdy. Pure Swift-rewrite från noll.

Repo-struktur är inte bestämd — jag har två alternativ vi behöver välja:
A) Nytt repo `birdy-ios` (separat)
B) iOS-mapp i existerande `anonadrek/birdy`-repo

Innan vi börjar koda:
1. Läs `docs/superpowers/specs/2026-05-26-ios-mac-port-spec.md` i Android-repo:t — det är det fullständiga kontextdokumentet jag förberett.
2. Läs `CLAUDE.md` i Android-repo:t för bakgrund.
3. Bekräfta att du förstår scope + tech-stack + visuellt språk + privacy-/license-stance.
4. Hjälp mig välja repo-strategi (A vs B).
5. Starta sedan `superpowers:brainstorming` för Plan iOS-1 (Foundation).

Klart med läsning + bekräftelse innan vi gör något annat.
```

Mac-Claude ska *aldrig* hoppa över brainstorming-fasen och börja koda direkt. Plan iOS-1 brainstormas → planeras → exekveras med två-stegs-review per task. Samma disciplin som Android.

---

## Bilaga A — Akronymer + paketnamn

| Term | Förklaring |
|---|---|
| KMP | Kotlin Multiplatform (Android-stacken) — *ej använt på iOS-porten* |
| CMP | Compose Multiplatform — *ej använt på iOS-porten* |
| TFLite | TensorFlow Lite |
| CoreML | Apples on-device ML-framework |
| TFLite-iOS | `TensorFlowLiteSwift` Swift Package |
| GRDB | GRDB.swift — Swift SQLite wrapper (rekommenderad DB) |
| StoreKit 2 | Apples nya IAP-API (iOS 15+) |
| BGTask | Background Tasks framework (iOS bakgrundsjobb) |
| HIG | Human Interface Guidelines (Apples designguide) |
| TestFlight | Apples beta-distribution-platform (~ Play Console Internal/External Testing) |
| App Store Connect | Apples motsvarighet till Play Console |

## Bilaga B — Filstruktur-förslag (Plan iOS-1 brainstormar slutgiltig)

```
Birdy/
  App/
    BirdyApp.swift            # @main
    AppGraph.swift            # DI-root (singletons)
  Features/
    Identify/                 # camera-tab
    Listen/                   # audio-tab — license-guarded
    Audio/                    # AVAudioEngine wrapper — license-guarded
    Journal/                  # observations + life list
    Encyclopedia/             # 839 species browse
    Badges/                   # gamification
    Premium/                  # StoreKit + PremiumScreen
    Onboarding/               # 7-scens scroll-story
    Settings/                 # språk/premium/debug
    DailyBird/                # Phase A retention
  Core/
    Design/                   # Colors, Fonts, Components (JournalHeadline, StampSeal, ...)
    Data/                     # GRDB models, migrations, repositories
    ML/                       # CoreML + TFLite-iOS wrappers
    Content/                  # YAML/JSON parser, SpeciesRepository
    Notifications/            # UNUserNotificationCenter wrappers
    L10n/                     # localization helpers
  Resources/
    Fonts/                    # DMSerifDisplay-Italic, Caveat-Regular, Inter-Regular
    Species/                  # 839 YAML + WebP-bilder
    Models/                   # AIY-V1.mlmodel, BirdNET-Lite.tflite
    Assets.xcassets/          # app-icon, splash, in-app-images
    PrivacyInfo.xcprivacy     # Apple Privacy Manifest
  Localization/
    sv.lproj/Localizable.strings
    en.lproj/Localizable.strings
  Tests/
    BirdNetLicenseGuardTests.swift   # CRITICAL — see §14
    MatchThresholdTests.swift
    BadgeRuleTests.swift
    ...
```

## Bilaga C — Versions-konventioner

Mirror Android:
- `1.0.0` — first production release
- `1.0.x` — patch (hot-fixes)
- `1.1.0` — Phase A retention etc.
- `1.x.0-rc1` — release candidate (TestFlight Internal)

Tag-format: `ios-v1.0.0`, `ios-v1.0.1` om delat repo. Bara `v1.0.0` om separat repo.

---

**Slut på spec. Mac-Claude — börja från §20.**
