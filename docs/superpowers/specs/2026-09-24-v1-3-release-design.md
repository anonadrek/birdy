# Birdy 1.3.0 (vC128): API 36, betalning och utseendelyft — design

> **Datum:** 2026-09-24 (Windows). **Beslutat med Albin** i brainstorming samma dag.
> **Mål:** en Android-release i slutet av september 2026 som (1) uppfyller Play-kravet targetSdk 36, (2) slår på betalningen via AB-kontot utan att tidiga användare förlorar något, (3) lyfter utseendet till en modernare och mer exklusiv Field Journal, och (4) är proaktivt buggjagad av agenten före produktion. Funktionen ska vara exakt som idag.
> **Mockups (godkända):** `docs/superpowers/specs/assets/2026-09-24-v1-3-release/` — `palette.html` (vald palett = rad 1), `key-screens.html`, `encyclopedia-stats.html`, `visual-direction.html` (vald riktning = A). Filerna är fragment utan companion-ramen; öppna direkt i webbläsare som referens.

---

## 1. Utgångsläge (verifierat 2026-09-24)

- `main` bär vC127 / 1.2.2 (språkmotor, ljud-ID-frysfix m.m.) + Cursor-fixarna efter det (save-as-unknown #21, trådläckor i kamera/plats). **vC127 laddades aldrig upp.**
- **Play-krav:** sedan 31 aug 2026 avvisas uppdateringar som siktar under API 36 (engångsförlängning till 1 nov finns i Console). vC127 (targetSdk 35) kan alltså inte laddas upp → nästa bygge MÅSTE ha targetSdk 36. Källor: Play Console Help "Target API level requirements", developer.android.com "Meet Google Play's target API level requirement".
- Byggkedja idag: Kotlin 2.1.20, AGP 8.7.3, CMP 1.8.2, compileSdk/targetSdk 35, minSdk 24. AGP 8.x < 8.9.1 stöder inte compileSdk 36.
- **Appen är flyttad till AB:s utvecklarkonto** (Albin 2026-09-24). Produkterna, licensnyckeln och licenstestare måste sättas upp där.
- Lokala `gradle.properties` (Windows): `BIRDY_PLAY_LICENSE_KEY` **tom**, `MAPTILER_API_KEY` **tom** (gammal nyckel läckt i git-historiken → roteras).
- Premium beräknas idag i `MainActivity` (`premiumOverride` = `Active(LIFETIME)` när `PREMIUM_OPEN_FOR_LAUNCH=true`) och konsumeras via `AppGraph.effectivePremiumActive`. `firstInstallTimestamp` finns i DataStore för alla användare sedan Plan 6a. DataStore ingår i Auto Backup + device-transfer (`backup_rules.xml`, `data_extraction_rules.xml`).
- Appspråk: **svenska** (`composeResources/values/`, default) och **engelska** (`values-en/`). Språkväljare SV/EN/System.
- Windows-SDK: `platforms/android-35` + `android-36.1`, build-tools 34/36.1/37, emulator-paketet finns men **inga system-images, inga AVD:er och ingen `sdkmanager`** (cmdline-tools saknas). Galaxy S23 Ultra (API 35) ej ansluten vid spec-tillfället.

## 2. Beslut

| # | Beslut | Källa |
|---|---|---|
| D1 | Datumet styr: release runt 30 sep. Det som inte hinns flyttas till en oktober-uppföljare, aldrig tvärtom. | Albin |
| D2 | Riktning **A · Redaktionell**: helbildsfoto med mörk ton, stor serif, hårlinjer i stället för ramar, handskrift (Caveat) bara som krydda. Nuvarande Field Journal-tema **behålls och lyfts**, det byts inte ut. | Albin |
| D3 | Palett **1 · Mossa, rost & mässing** (tokens i §4.1). Rost = handling, mässing = Premium, mossa = mörka ytor och naturton. | Albin |
| D4 | Ny layout för: Identifiera, resultat (Match/Disambig/NoBird), Mina arter (dagboken), Premium + tack-skärm, Uppslagsverk, Artprofil, Säsongsstatistik. Övriga skärmar lyfts via tokens + delade komponenter. | Albin |
| D5 | Tidiga användare (installerade före brytpunkten) får Premium för alltid + en tack-skärm. | Albin (grundbeslut 2026-06-17, tack-skärm 2026-09-24) |
| D6 | Premium-copy ska vara sann: löften om funktioner som inte finns tas bort; alla priser kommer från Google Play. | Albin |
| D7 | Allt ny/ändrad text på **svenska och engelska**; paritet testas automatiskt. | Albin |
| D8 | Agenten testar funktionaliteten själv (automatiskt + på enhet/emulator) och fixar buggar proaktivt före produktion. | Albin |
| D9 | Stanna på AGP 8.x (≥ 8.9.1, inte AGP 9) för att slippa AGP 9:s KMP-plugin-migrering mitt i en deadline. | Agent (teknik) |
| D10 | Version **1.3.0 / versionCode 128**. | Agent |

## 3. Spår A — Release-teknik (API 36)

**A1. Byggkedja.** `android-compileSdk`/`android-targetSdk` → 36 i `gradle/libs.versions.toml`. AGP → lägsta 8.x som stöder API 36 (≥ 8.9.1) med matchande Gradle-wrapper. Kotlin ligger kvar på 2.1.20 om bygget fungerar; kräver KGP en uppgradering tas minsta möjliga patch-steg och konsekvensen för CMP/iOS dokumenteras. Installera `platforms;android-36` + build-tools 36 (cmdline-tools/`sdkmanager` installeras först).

**A2. Android 16-beteenden att verifiera** (targetSdk 36):
- Edge-to-edge är tvingande (opt-out borttagen): alla skärmar ska hantera insets korrekt, även de nya layouterna med helbildsfoto överst (statusbar ovanpå foto, navbar under menyraden).
- Predictive back är på som standard: systemets tillbaka ska fungera från varje skärm, beskärningsskärmen, skanning, bottom sheets och dialoger (`PlatformBackHandler`/`BackHandler` bygger på `OnBackInvokedCallback` via androidx.activity).
- Orientering/storleksändring ignoreras på stora skärmar (≥ 600 dp): layouterna får inte gå sönder på surfplatta (lägre prioritet, sanity-check).
- Bakgrundsjobb (WorkManager-notiser: veckosammanfattning, trofé, dagens fågel) schemaläggs och avfyras fortfarande.

**A3. Version + nycklar.** versionCode 128, versionName 1.3.0. Release-bygget ska **faila** om `MAPTILER_API_KEY` eller `BIRDY_PLAY_LICENSE_KEY` är tomma (nytt Gradle-skydd; debug påverkas inte). Förhindrar att vi skeppar tom karta eller trasig betalning.

**A4. Paketering.** `:androidApp:bundleRelease` → `tools/check_16kb_alignment.py` + `zipalign -c -P 16` på AAB:n → R8-smoke på ett riktigt release-bygge (billingklasser får inte strippas).

## 4. Spår C — Utseendelyftet

### 4.1 Tokens (palett 1)

Befintliga tokennamn i `ui/theme/Color.kt` behålls där rollen är densamma, så att de ~72 filer som refererar dem lyfts automatiskt; värdena uppdateras och nya tokens läggs till.

| Roll | Hex | Ersätter / ny |
|---|---|---|
| Papper (bakgrund) | `#F6EFE2` | PaperTop/PaperBottom/MossCreme → ljusare, renare |
| Kortyta | `#FFFAF1` | ny `CardPaper` |
| Bläck (primär text) | `#26301F` | TextOnCreme |
| Dämpad text | `#5B6350` | ny `InkMuted` (MarginaliaInk behåller AA-krav ≥ 4.5:1) |
| Rost (handling) | `#9A4526` | AccentCopper |
| Djup rost | `#72301A` | ny `AccentCopperDeep` (gradientslut på primärknapp) |
| Aprikos (accent på mörkt) | `#F2B27A` | AccentCopperLight |
| Mossa (mörka ytor) | `#1F2A19` | HeroMossDeep/HeroMossShadow |
| Mässing (Premium) | `#B8893A` | ny `Brass` |
| Ljus mässing | `#E2C07E` | ny `BrassLight` |
| Hårlinje | `#DFD2BA` | ny `Hairline` |
| Sällsynt (navy) | `#1F3A5F` | StampNavy oförändrad |

`BirdyTheme` får motsvarande `lightColorScheme`. Pappersbakgrundens prickmönster tonas ned kraftigt (subtilt korn, inte prickar). Kontrast: all text på papper ≥ 4.5:1 (WCAG AA), kontrolleras per token.

### 4.2 Typografi

- Rubriker: DM Serif Display **upprätt** som bas, kursiv serif för accentordet (`*ord*`) i rost/aprikos. Caveat används inte längre i huvudrubriker, bara i marginalia/små sub-rader ("7 fynd den här veckan", "Första i din fältbok!").
- Kicker: versaler 8,5–9,5 sp, spärrning ~0,16 em, med en kort hårlinje framför (ersätter dagens "LABEL · LABEL"-rad).
- Brödtext: Inter (system sans) som idag.

### 4.3 Delade komponenter (lyfts en gång, slår igenom överallt)

- **`PhotoHero` (ny):** helbildsfoto + mossa-gradient nerifrån + kicker/rubrik/meta-rad med hårlinje. Används av Identifiera, Match, Artprofil, Premium, tack-skärmen.
- **`JournalIntro`/`JournalHeadline`:** ny kicker med hårlinje, upprätt serif, accentord i kursiv; ornamentet ❦ behålls i tunnare form.
- **`StampSeal`/`MiniStamp`:** präglat vaxsigill: fylld cirkel, ljuspunkt uppe till vänster, inre ring, lätt rotation, mjuk skugga. Varianter: rost (vanlig), mässing (Premium), navy (sällsynt/rödlistad), streckad (låst).
- **Kort:** kortyta + hårlinje/mjuk skugga i stället för kopparramar.
- **Knappar:** primär = rost-gradient (rost → djup rost) med subtil topplinje; Premium = mässingsgradient; sekundär = textknapp i rost.
- **`BottomNavBar`:** papper + hårlinje överst, aktiv flik i rost, inaktiva dämpade.
- **Chips/piller** (grupper, filter): hårlinje, aktivt piller fyllt mossa.
- **Arksida (`sheet`):** innehåll som glider upp över fotot med 24 dp rundade toppar (Match, Artprofil).

### 4.4 Skärmar med ny layout (enligt mockups)

1. **Identifiera** (`ListenLauncherScreen`): Dagens fågel som `PhotoHero` överst (art, "finns nära dig nu", latinskt namn, x/3 fångade), sedan kicker "Identifiera" + "Tre sätt att *fånga.*", primärknapp "Starta kameran" och två hårlinjerader (Från ett foto, Lyssna på lätet). Samma tre ingångar och samma navigering som idag.
2. **Resultat** (`MatchView`, `DisambigView`, `NoBirdView`): användarens foto i övre halvan med artnamn, latinskt namn och säkerhetsstapel; arksida med ny art-/upprepningsrad + präglad stämpel, anteckning, "Spara i fältboken", "Inte den? Se fler förslag". Disambig och NoBird får samma språk. Spara-flödets beteende (vänta in `SaveStatus`, spinner, snackbar) är oförändrat.
3. **Mina arter** (`LifelistScreen`): ny intro, stora siffror (arter, stämplar, streak), "Veckans uppslag" som foto-kort, lista med hårlinjer och präglade mini-stämplar.
4. **Premium** (`PremiumScreen`): foto-topp + mörk mossa-yta med mässing; "Alltid gratis"-rad först, sedan Premium-funktionerna (Fynd-kartan, Fältdagboken som PDF, Säsongsstatistik, 7 premiummärken), två prisval (år / för alltid) med priser från Play, mässingsknapp, finstilt om förnyelse + Återställ köp. Aktiv köpare ser sin aktiva status som idag, restylad.
5. **Tack-skärm** (ny, `PremiumThankYouScreen`): mässingssigill, "Tidig fältmedlem", "Premium är ditt. *För alltid.*", förklarande text, bockade Premium-funktioner, Caveat-rad "Tack för att du var med från början."
6. **Uppslagsverk** (`ArchiveScreen`): kicker "Uppslagsverk · 839 arter", rubrik, sökfält överst, gruppiller, sortering (A–Ö/Familj/Senast), sektionsrubrik med antal, rader med miniatyrfoto, namn, latinskt namn och präglad mini-stämpel för stämplade arter; rödlistad-tagg där det gäller.
7. **Artprofil** (`SpeciesProfileScreen`): `PhotoHero` (familj, namn, latinskt namn, piller: stämplad + antal fynd, abundans, IUCN), arksida med beskrivning, säsongsremsa per månad, foton. Premium-teasern behålls men med sann copy (§5.4).
8. **Säsongsstatistik** (`SeasonStatsScreen`): kicker + Premium-märke i mässing, stora siffror (arter, observationer), kort med månadsstaplar (innevarande månad i mässing), säsongsfördelning, mest sedda. Samma data och diagramtyper som idag.

**Utanför D4 i denna release** (endast token/komponentlyft; ny layout i oktober-uppföljaren): onboarding, troférum/märken, karta, observationsdetalj, inställningar, skanningsvyn (kamera), ljudinspelning, beskärning.

### 4.5 Ordning (datumet styr)

Tokens → komponenter → Identifiera → Resultat → Mina arter → Premium + tack → Uppslagsverk → Artprofil → Statistik. Statistik/Uppslagsverk/Artprofil ligger sist; om de inte hinns till söndag kväll 27/9 väljer Albin mellan att skjuta releasen 1–2 dagar eller att flytta dem till oktober.

## 5. Spår B — Betalning

### 5.1 Grandfather-regeln (tidiga användare)

- Ren funktion i delad kod: `isGrandfathered(storedFirstInstallMs: Long?, packageFirstInstallMs: Long?, cutoffMs: Long): Boolean` = `true` om **någon** av källorna är satt och `< cutoffMs`.
  - `storedFirstInstallMs` = DataStore `firstInstallTimestamp` (skrivs vid första start sedan Plan 6a).
  - `packageFirstInstallMs` = Androids `PackageInfo.firstInstallTime` (täcker den som installerat men aldrig öppnat appen före brytpunkten).
- **Beslutas en gång** vid första start av 1.3.0 (före premium-wiringen i `MainActivity`) och sparas som `premiumGrandfathered: Boolean?` i `UserPreferences` (`null` = ej beslutat). Ett sparat `true` återkallas aldrig. DataStore ingår i Google-backup → följer med till ny telefon.
- **Brytpunkt:** `GRANDFATHER_CUTOFF_MS` i `BuildConfig` = planerad go-live + 48 h. Plan: **2026-10-02 00:00 Europe/Stockholm (2026-10-01T22:00:00Z)**. Slirar releasen flyttas brytpunkten till nya go-live + 48 h (release-checklistepunkt). Hellre några dagar för generöst än att en tidig användare förlorar något.
- **Premium-upplösning:** `grandfathered → Active(LIFETIME)`; annars `DEBUG && skipPremiumOverride → null`; annars billing-state. `PREMIUM_OPEN_FOR_LAUNCH` flippas till `false` i **samma bygge**.
- iOS berörs inte (ingen iOS-användarbas; betalvägg från dag 1 i i5). Funktionen ligger i delad kod men källorna kopplas bara på Android.
- **Debug-verktyg** (DiagnosticsScreen, bara debug): simulera grandfathered, återställ beslutet, visa tack-skärmen igen. Krävs för QA av båda tillstånden.

### 5.2 Tack-skärmen

- Visas **en gång** automatiskt vid första start efter uppdateringen för grandfathered-användare (flagga `grandfatherThanksShown` i `UserPreferences`), efter eventuell onboarding. Grandfathered-användare har aktiv Premium, så cold-start-betalväggen visas aldrig för dem; de två kan inte krocka.
- Därefter visas tack-innehållet i stället för köpskärmen när en grandfathered-användare öppnar Premium (Inställningar m.fl.).

### 5.3 Priser från Play

- Alla priser kommer från `PremiumBillingClient.formattedPrices`. Hårdkodade fallback-priser ("199 kr / år", "499 kr · engångsköp") tas bort; innan priserna laddats visas ett laddningsläge och köpknappen är inaktiv.
- `premium_tier_yearly_sub` ("~17 kr / mån") tas bort helt. Ett framräknat månadspris kräver valuta- och avrundningslogik för alla marknader, och risken för fel pris är inte värd det i den här releasen.
- Förnyelsetexten formateras med det riktiga årspriset: "Förnyas årligen till %1$s. Avsluta när som helst i Google Play." (SV + EN).

### 5.4 Sanningsenlig Premium-copy (SV + EN)

- `premium_archive_subtitle` lovar "molnsynk · flera foton per fynd" → ersätts med verkliga Premium-funktioner.
- `premium_species_title`/`premium_species_subtitle` lovar "Migrationskarta · läten · djupare fält-anteckningar" → ersätts med sann copy som pekar på verkliga Premium-funktioner.
- Genomgång av **alla** `premium_*`-strängar och teasers i båda språken mot vad Premium faktiskt låser upp (karta, PDF, statistik, 7 premiummärken). Exempel-/illustrationstexter (t.ex. förhandsvisningens "Vår 2026 · 23 nya arter") ska tydligt vara exempel.
- Audio-ID nämns aldrig som Premium (BirdNET-licensen; `BirdNetLicenseGuardTest` finns kvar).

### 5.5 Köp-verifiering (Albins händer + agentens förberedelse)

Runbook `docs/superpowers/runbooks/2026-05-26-billing-verify-and-go-live.md` §2 körs på **internt test-spår** med licenstestare på Galaxy, mot ett tidigt bygge (spår A + B, gammalt utseende) så att verifieringen löper parallellt med utseendearbetet. Agenten förbereder bygget, checklistan och debug-verktygen; runbooken uppdateras med grandfather-rutorna (§5.1) och tack-skärmen.

## 6. Spår D — Proaktiv QA och buggjakt (agenten)

### 6.1 Automatiskt
- Full gate grön vid varje commit: `:shared:domain:jvmTest :shared:ml:jvmTest :composeApp:testDebugUnitTest :androidApp:assembleDebug ktlintCheck detekt`.
- Nya tester: grandfather-funktionen (båda källor, null-fall, exakt på brytpunkten, idempotens), premium-upplösningen, prisformatering/laddningsläge, **strängparitet SV↔EN** (alla nycklar i `values/` finns i `values-en/` och tvärtom, samma formatargument), sann-copy-vakt (inga `premium_*`-strängar nämner molnsynk/migrationskarta/läten), BirdNET-vakten.
- Robolectric-renderingstester för de nya skärmarna i **båda språken** (renderar utan krasch, nyckeltexter finns, Premium-tillstånden: ej aktiv / aktiv / grandfathered).
- CI (inkl. macOS-jobbet) grön på varje push — enda iOS-vakten från Windows.

### 6.2 På enhet/emulator (agentdrivet via ADB)
Miljö: API 36-emulator (primär för targetSdk 36-beteenden), API 30-emulator (språkmotorn API 24–32, vC127-checken) och Galaxy S23 Ultra (API 35, riktig kamera/mikrofon/köp). Om emulatorer inte går att köra på Windows-maskinen (hårdvaruacceleration) rapporteras det direkt och Galaxy blir primär.

Genomgång **på svenska och engelska**, med logcat bevakad för krascher/ANR/fel:
1. Ren installation → onboarding (inkl. språkscen) → Identifiera.
2. Skanna (kamera) → Match/NoBird; bakåt → skanna igen (klassificeraren får inte dö).
3. Foto från galleri med **egna testbilder** som agenten lägger in (aldrig Albins galleri) → beskär/rotera → analys → Match/Disambig → spara; "Spara som okänd".
4. Ljud: inspelning, avbryt, bakåt mitt i (mikrofonindikatorn släcks), felstate.
5. Mina arter, observationsdetalj, anteckning.
6. Uppslagsverk: sök, grupper, sortering → artprofil.
7. Märken/troférum, karta (med riktig MapTiler-nyckel), statistik, PDF-export.
8. Premium: ny användare efter brytpunkten → betalvägg med Play-priser; simulerad grandfathered → tack-skärm en gång, sedan tack-innehåll i Premium; köpflödet på Galaxy (Albin, licenstestare).
9. Inställningar: språkbyte SV↔EN↔System slår igenom direkt, notisinställningar.
10. Systemets tillbaka från varje skärm (predictive back), insets (kant-till-kant), textstorlek 130 %, små skärmar.

### 6.3 Kodnivå
- Riktad granskning av riskytor: premium-upplösning, billing-klienten, back-hantering, insets, nya komponenter.
- Två-stegs-review per task (spec → kvalitet) enligt husregeln + en slutreview av hela grenen.
- Varje bugg som hittas: regressionstest först, fix, omverifiering.

## 7. Tidslinje

| Dag | Innehåll |
|---|---|
| Tor 24 – fre 25 | Spår A + B i kod → internt test-spår (tidigt bygge) → Albin testar köp |
| Fre 25 – sön 27 | Spår C (ordning §4.5) |
| Sön 27 kväll | Avstämning: allt i §4.4 klart? annars Albins val (skjuta 1–2 dagar eller flytta sista skärmarna) |
| Mån 28 | Spår D full körning, fixar, release-AAB → produktion (managed publishing om möjligt) |
| Tis 29 – ons 30 | Play-granskning, What's new SV+EN, nya butiksbilder SV+EN |

## 8. Albins manuella steg

1. Play Console (AB): skapa `premium_yearly_v1` (prenumeration, årlig) och `premium_lifetime_v1` (engångsköp), sätt priser, aktivera.
2. Hämta licensnyckeln (Monetization setup → Licensing) → `BIRDY_PLAY_LICENSE_KEY` i lokala `gradle.properties`.
3. Lägg till licenstestare (eget Google-konto), koppla Galaxy till samma konto.
4. MapTiler Cloud: rotera den läckta nyckeln, lägg nya i `MAPTILER_API_KEY` (lokalt).
5. Koppla in Galaxy via USB för enhetssteg; köra köpchecklistan.
6. Upload/publicering i Console + What's new-inmatning.

## 9. Synk mellan maskinerna (Windows ↔ Mac)

- CLAUDE.md (kanonisk) + AGENTS.md (Codex-spegel) uppdateras och pushas vid varje milstolpe; AGENTS.md:s status (fastnad på 2026-07-18) synkas upp i första commit.
- **Påverkan på Mac/iOS som ska stå i CLAUDE.md:** (a) Android SDK `platforms;android-36` krävs även på Macen (Gradle konfigurerar Android-targets vid iOS-bygge); (b) AGP/Gradle-versionerna ändras; (c) delad UI ändras → iOS får nya utseendet automatiskt, Macen gör en sim-boot-check; (d) nya `UserPreferences`-medlemmar implementeras i **alla** actuals inkl. iOS `NsUserDefaultsUserPreferences` i samma commit (Windows kan inte kompilera iOS — CI:s macOS-jobb är vakten).
- memory-vault (`dev/1-mina-projekt/memory-vault`, `global/strategi-birdy.md`): Birdy-statusen uppdateras (betalning live, 1.3.0) när releasen är ute.

## 10. Efter release (oktober-uppföljaren)

Nya layouter för onboarding, troférum/märken, karta, observationsdetalj, inställningar, skanning, ljudinspelning, beskärning; nya webbskärmdumpar (`website/src/assets/screens/`) i nya looken; konverteringsuppföljning +7/+14 dagar enligt runbook §4.

## 11. Risker

| Risk | Hantering |
|---|---|
| AGP-bumpen kräver Kotlin/CMP-bump som påverkar iOS | Spike först; minsta steg; CI macOS-jobbet; dokumentera i CLAUDE.md |
| Emulatorer går inte att köra på Windows | Rapportera direkt; Galaxy primär; API 36-beteenden verifieras då i Albins Android Studio |
| Licensnyckel/produkter inte klara i tid | Tidigt internt bygge; release-bygget failar på tom nyckel (A3) så vi aldrig skeppar trasig betalning |
| Utseendet hinns inte helt | Ordningen §4.5 + avstämning söndag kväll |
| Play-granskning drar ut | Upload måndag; brytpunkten flyttas om go-live slirar |
