# Birdy Bird Scanner

AI-driven fågelapp: identifiera fåglar med kameran, ett foto eller ett ljudklipp, slå upp dem i ett uppslagsverk över 839 europeiska arter och spara fynden i en privat fältdagbok. All identifiering sker på telefonen.

> **Läge (2026-09-30):** Android-appen är live på [Google Play](https://play.google.com/store/apps/details?id=se.birdy.android) sedan 2026-06-17 (1.2.0, versionCode 125). **Release 1.3.0 pågår:** API 36, betalningen påslagen på AlbIT AB:s utvecklarkonto (den som installerat före brytpunkten behåller Premium för alltid) och ett utseendelyft i paletten "Mossa, rost & mässing". Köptestbygget (versionCode 128) finns; produktionsbygget (versionCode 129) väntar på ny MapTiler-nyckel, ett riktigt köptest och go-live-datum. **iPhone-appen** byggs från samma kod: plan i0 till i4 är kodklara och väntar på simulator- och enhetstester, StoreKit (i5) och App Store (i6) återstår. Webben [birdy.community](https://birdy.community) är live i fältbokens färger. Löpande och fullständig status finns i [`CLAUDE.md`](CLAUDE.md).

## Vad appen gör

- **Skanna live** med kameran (3 bilder/s, AIY Birds V1 on-device, cirka 14 ms per bild på Galaxy S23 Ultra) med zoom 1× till 10×, eller välj ett foto ur galleriet eller ta ett nytt, med beskärning och 90°-rotation.
- **Identifiera på ljud:** öppen inspelning upp till 60 s med BirdNET-Lite, ett rullande fönster på 3 s, automatiskt stopp vid en tydlig träff och upp till tre kandidater. Gratis för alla, alltid: modellen är CC BY-NC-SA och får aldrig ligga bakom Premium.
- **Ärlig osäkerhet:** en säker träff ger en stämpel, en osäker visar kandidater att välja mellan, och utan fågel säger appen just det.
- **Uppslagsverk** över 839 nordiska och europeiska arter i 15 ekologiska grupper, med foto, beskrivning, flyttning och global rödlistestatus (IUCN). Sökningen tål apostroftyper, diakriter och båda språken.
- **Fältdagbok (Mina arter)** med foto och anteckning per fynd, frivillig platsfångst, Dagens fågel och Veckans uppslag.
- **Märken:** 34 stycken (27 gratis och 7 Premium) i Troférummet, med livslista upp till 500 arter och ett spår för rödlistade arter.
- **Premium, ett tillval:** privat karta över egna fynd, PDF-export av dagboken, säsongsstatistik och 7 extra märken. Årsabonnemang eller livstidsköp via Google Play Billing v8 med Restore Purchases och signaturkontroll; priserna hämtas alltid från Play.
- **Svenska och engelska**, med språkval redan i introduktionen.
- **Privat från grunden:** inga konton och ingen spårning. Foton och dagbok stannar på telefonen.

## Arkitektur

Kotlin Multiplatform och Compose Multiplatform: både affärslogik och UI delas mellan Android och iOS. Plattformskod (kamera, ML-körning, ljud, karta, betalning, PDF, notiser) ligger bakom `expect`/`actual`.

| Modul | Innehåll |
|---|---|
| `composeApp` | Delad UI i Compose Multiplatform för Android och iOS |
| `shared/domain` | Use cases, domänmodeller och affärsregler (ren Kotlin) |
| `shared/data` | SQLDelight 2.x (Android- och native-drivrutin), repositories |
| `shared/datastore` | Användarinställningar och Premium-läge (DataStore på Android, NSUserDefaults på iOS) |
| `shared/ml` | Fotoklassificering (LiteRT på Android, vendrad TensorFlowLiteC på iOS), bildförbehandling, BirdNET-ljudkörning och etikettmappning |
| `shared/pdf` | `JournalPdfRenderer` för Premium-PDF:en |
| `shared/content` | Artdatabasen (839 arter) och märkesregler |
| `androidApp` | Android-ingången och plattforms-actuals |
| `asset-pack` | Play Asset Delivery-modul (install-time) med cirka 2 060 WebP-plåtfoton (~326 MB) |
| `iosApp` | iPhone-appen: Xcode-projekt genererat med xcodegen ur `project.yml`, ett tunt SwiftUI-skal i två Swift-filer |
| `tools/content-pipeline` | `birdy-fetcher` (Python, uv): artlistan, Wikidata och Wikipedia, webbtexter till artsidorna |
| `tools/ml-eval` | Modellutvärdering och paritetsreferenser |
| `website` | birdy.community (Astro 5, Tailwind v4, SV/EN), publiceras av Vercel |

Designspecar: v1 [`docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md`](docs/superpowers/specs/2026-04-30-birdy-bird-scanner-v1-design.md), iOS [`docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md`](docs/superpowers/specs/2026-07-07-birdy-ios-v2-design.md), release 1.3.0 [`docs/superpowers/specs/2026-09-24-v1-3-release-design.md`](docs/superpowers/specs/2026-09-24-v1-3-release-design.md).

## Komma igång

**Krav:**
- JDK 21 (Temurin)
- Android SDK 36 med build-tools (AGP 8.9.1, Gradle 8.11.1)
- `MAPTILER_API_KEY` i lokala `~/.gradle/gradle.properties` för kartan (committas aldrig). Release-bygget behöver dessutom `BIRDY_PLAY_LICENSE_KEY` och signeringsuppgifter.
- iOS, bara på Mac: Xcode 26, xcodegen och `iosApp/Local.xcconfig` (mall: `Local.xcconfig.sample`)
- Webben: Node.js 20 eller senare

**Android:**

```bash
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:installDebug   # med enhet eller emulator ansluten
./gradlew :androidApp:bundleRelease  # signerad AAB till Play
```

**Tester och lint:**

```bash
./gradlew :shared:domain:jvmTest :shared:ml:jvmTest :shared:datastore:jvmTest :composeApp:testDebugUnitTest
./gradlew ktlintCheck detekt
./gradlew ktlintFormat               # autofix
```

**iOS (Mac):**

```bash
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64
./gradlew :shared:content:iosSimulatorArm64Test :shared:domain:iosSimulatorArm64Test :shared:data:iosSimulatorArm64Test :shared:ml:iosSimulatorArm64Test :composeApp:iosSimulatorArm64Test
cd iosApp && xcodegen generate       # bara efter ändring i project.yml
```

**Webben (från `website/`):**

```bash
npm install
npm run dev
npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette && npm run build
PLAYWRIGHT_PORT=4741 npm run test:smoke   # Playwright, 113 tester
```

## Tidslinje i korthet

| Datum | Händelse |
|---|---|
| 2026-04-30 | Första commit kl. 00.54: v1-specen, Plan 1 och CLAUDE.md |
| 2026-05-08 | Riktig AI-identifiering på telefonen, 74 commits (projektets rekorddag) |
| 2026-05-21 | Ljud-ID med BirdNET |
| 2026-05-23 | `v1.0.0`, 23 dagar efter första commit |
| 2026-06-08 | Privat fyndkarta, closed testing med 1.2.0-rc1 |
| 2026-06-17 | Live på Google Play (1.2.0, versionCode 125), 48 dagar efter första commit |
| 2026-07-07 | iOS-spåret startar på Mac:en |
| 2026-07-18 | LiteRT och 16 KB-fixen på Android |
| 2026-07-27 | Livekamera på iPhone (Milestone 1 i kod) |
| 2026-08-17 | iOS i4: karta, notiser och PDF |
| 2026-09-24 | Release 1.3.0 startar: API 36, betalning, tidiga användare behåller Premium |
| 2026-09-28 | Utseendelyftet mergat, webben i fältbokens färger live |

## Vägkarta

### v1.0 (plan för plan)

| # | Plan | Status |
|---|---|---|
| 1 | Foundation: KMP-bootstrap, Compose, CI | ✅ `v0.1.0-foundation` |
| 2a | Content pipeline och walking skeleton | ✅ `v0.2.0a-pipeline` |
| 2b | Content backfill (5 till 839 arter) | ✅ `v0.2.0-content` |
| 3 | Uppslagsverk (bläddra och artprofil) | ✅ `v0.3.0-encyclopedia` |
| 4a | ML och kamera-UI (FakeClassifier, CameraX 3 fps) | ✅ `v0.4.0a-camera-ui` |
| 4b | Riktig TFLite (AIY Birds V1, 965 klasser) | ✅ `v0.4.0b-real-tflite` |
| 5a | Dagbok (bläddra, detalj, spara) | ✅ `v0.5.0a-diary` |
| 5b | Gamification (25 märken, streaks) | ✅ `v0.5.0b-gamification` |
| 7a | Redesign-grund: tokens, DataStore, onboarding | ✅ `v0.7.0a-foundation` |
| 7b | Redesign av skärmarna | ✅ `v0.7.0b-screens` |
| 7c | Field Journal: DM Serif, Caveat, papper, StampSeal | ✅ `v0.7.0c-field-journal` |
| 7d | Match-flödet: Match, Disambig, NoBird | ✅ `v0.7.0d-match-flow` |
| 7e | Premium-nivån | ✅ `v0.7.0e-premium` |
| 6a | UX-polish och release-mekanik (R8, signering, a11y) | ✅ `v0.8.0-rc1` |
| 6b1 | Billing v8 och lanseringsförberedelser | ✅ `v0.9.0a-billing` |
| 6b2 | Ljud-ID med BirdNET-Lite | ✅ `v0.9.0b-audio` |
| 6b3 | Premium-innehåll (PDF, säsongsstatistik, 10 fältmärken) | ✅ `v0.9.0c-premium-content`, `v1.0.0` |
| W | Webbplatsen (Astro, Vercel, birdy.community, /legal/) | ✅ Live |

### Efter v1.0

| Version | Innehåll | Status |
|---|---|---|
| 1.0.2 | Introduktionen som en berättelse i sju scener | ✅ |
| 1.1 | Dagens fågel och notiser, zoom och beskärning, testarnas feedback (sök, ekologiska grupper, omgjorda märken), Veckans uppslag, Troférummet | ✅ |
| 1.2 | Privat fyndkarta, ny Premium-skärm, UX-genomgång och frys-omdesign i skanningen | ✅ Live sedan 2026-06-17 |
| 1.3.0 | API 36, betalning på AlbIT AB:s konto, Premium för alltid för tidiga användare, utseendelyftet, 1.2.1/1.2.2-fixarna (16 KB, språkval, ljud-ID) | 🔄 Plan 1 och 2 klara, Plan 3 (QA och release) kvar |
| Artsidor | En sida per art på birdy.community (180 arter, SV och EN) | 🔄 Fas 1 kodklar, väntar på API-kredit |

### v2: iPhone (plan i0 till i6)

Samma app från samma kod, utan nya funktioner, med betalväggen på från dag ett. i0 (miljö), i1 (uppslagsverk och dagbok), i2 (foto-ID, LiteRT, livekamera), i3 (ljud-ID) och i4 (karta, notiser, PDF) är kodklara och granskade. Kvar: simulator- och enhetstester, i5 (StoreKit 2) och i6 (App Store).

### Längre fram

Geografisk expansion är huvudspåret. Modellerna är redan globalt tränade (AIY V1 cirka 965 klasser, BirdNET-Lite cirka 6 000), så jobbet sitter i innehållet, regional säsongsdata, on-demand asset packs och fler språk.

- **v2, "Asien och hela Europa":** innehåll för Östasien och Indien först, tillsammans med iOS-lanseringen.
- **v3, "Hela världen":** resterande kontinenter, full innehålls- och språkskalning.
- **Parallella spår:** konton och molnsynk av dagboken, publika fynddata och notiser om sällsynta arter nära dig (den privata kartan i 1.2 är första steget), community, quiz och utbildningsläge.

## Arbetssätt

Specar och planer i `docs/superpowers/` är sanningskällan: spec (`specs/`), sedan implementationsplan (`plans/`), sedan kod, med Claude Code som motor och granskning i två steg (spec, sedan kvalitet) per uppgift. Arbetsregler, fällor och löpande status för AI-assistenten finns i [`CLAUDE.md`](CLAUDE.md). Två maskiner delar repot (Windows för Android, Mac för iOS), och `main` på GitHub är sanningskällan mellan dem.

## Licens

Proprietär, © 2026 AlbIT AB (org.nr 559593-7607), alla rättigheter förbehållna. Se [`LICENSE`](LICENSE). Koden går att läsa här men ingen rätt att använda, kopiera, ändra eller distribuera den ges. Tredjepartskomponenter (Kotlin, Compose, SQLDelight, LiteRT, osmdroid med flera under Apache 2.0, BirdNET-Lite under CC BY-NC-SA 4.0, typsnitt under OFL 1.1, kartdata © OpenStreetMap och MapTiler) lyder under sina egna licenser; full attribution finns i appen under Inställningar, Om.
