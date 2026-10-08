# Webblyftet "Dagens fågel" (Lift C) och Premium-sidan (Premium A): implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** birdy.community får en startsida byggd kring Dagens fågel (samma fågel som appen), appbildernas karusell med riktiga 1.3.0-skärmar i kod, och en egen Premium-sida på svenska och engelska, live före lanseringen torsdag 15 oktober.

**Albins val 2026-10-08 00.20:** "C på första fast förbättra den en aning, gör denna sidan mind blowing, samma typ av rull som idag mellan appbilderna, lös det snyggt i kod. Premium page: A."

**Design source (läs först):** förhandsvisningen `docs/superpowers/specs/assets/2026-10-08-webb-lyft-premium/` på den här grenen: `index.html` (sammanhang, öppna frågor, vad som kontrollerats), `mockups/lift-c.html`, `mockups/premium-a.html`, `mockups/premium-a-en.html`, `mockups/base.css`, `mockups/media/`. Mockuperna är statisk HTML; implementationen görs i sajtens Astro-komponenter, tokens och copy-filer. "Förbättra en aning": använd frihet där mockupen är svag (rytm, typografisk skala, rörelse, detaljer), men behåll riktningen.

**Arkitektur:** Astro 7 + Tailwind v4 i `website/`. All text i `website/src/content/copy.{sv,en}.json` (paritet krävs, `test:i18n`). Dagens fågel räknas ut **vid bygget** för datumet i Europe/Stockholm med en exakt port av appens `DailyBirdSelector`; sajten byggs om varje natt av ett GitHub Actions-schema som anropar en Vercel deploy hook. Karusellen är dagens `AppTour.astro` + `app-tour.ts` (samma rullbeteende), med riktiga skärmbilder i CSS-telefoner.

**Tech:** Astro 7.3.5, Tailwind v4, Playwright (`PLAYWRIGHT_CHANNEL=chrome`, egen port, `--workers=2`), `node --test` för enhetstester, sharp.

**Gren och worktree:** `website/1.3-premium` i `C:/w/birdy-premium` (från `main`). Sammanslagning till `main` gör huvudagenten. **OBS:** en publiceringsloop pushar artsidor till `main` hela tiden; slå ihop `origin/main` in i grenen före sista kontrollen.

**Husregler:** inga tankstreck i copy (vakten), inget grönt på sajtens yta (`test:palette`), ingen ton eller form över fågelfoton (bara neutral skugga bakom text, eller text bredvid/under), fotolicenser enligt Albins regler (beslut 2026-10-08): CC0, public domain och CC BY får alltid visas, även beskurna; CC BY-SA bara hela och utan något ritat på fotot; varje visat foto krediteras med fotograf, licens (länkad) och källa med samma rad som på artsidan; aldrig AI-genererade fåglar, inga påståenden om "offline" eller träffsäkerhet (`test:no-accuracy`), WCAG AA-kontrast (`test:contrast` och axe), `prefers-reduced-motion` respekteras, ingen horisontell scroll vid 390 px.

---

### Task 1: Dagens fågel, samma fågel som appen

**Files:** Create `website/src/lib/daily-bird.mjs`, `website/tests/unit/daily-bird.unit.mjs`, en golden-fil `website/tests/fixtures/daily-bird-golden.json`; ev. en liten generatortest i `shared/domain/src/jvmTest/` (Kotlin) som skriver golden-filen.

- [ ] Porta `shared/domain/src/commonMain/kotlin/se/birdy/domain/dailybird/DailyBirdSelector.kt` exakt till JS: urvalet (abundance `allmän`/`mindre allmän`, inte utdöd enligt `isExtinctIucnStatus`, säsongstagg för månaden finns och är breeding/present/migrating, region i SE/NO/FI/DK), sortering på QID-sträng, fröet `"${year}-${monthNumber}-${dayOfMonth}-NORDIC".hashCode()` (Javas 32-bitars `String.hashCode`, som Long) och `kotlin.random.Random(seed).nextInt(n)` (XorWow; läs Kotlins stdlib-källkod för `Random(Long)`, `XorWowRandom` och `nextInt(until)`).
- [ ] Källan för arterna är appens egen lista som den skickades, fryst per apprelease i `website/src/data/app-species-1.3.0.json` (fälten `id`, `abundance`, `iucn_status`, `season`, `regions`; skriven ur golden-filen med `npm run app-species:snapshot`; granskning C1 2026-10-08). Grenens YAML `shared/content/species/**/*.yaml` är inte det telefonerna kör (main och release/1.3.0 skilde sig på tre mesars abundance, vilket bytte fågel alla dagar), så bygget läser aldrig YAML. `npm run check:app-species` jämför YAML med appens lista och är ett steg i releasechecklistan (Plan 3 Task 11 på release/1.3.0); på main faller det tills release/1.3.0 är sammanslagen, vilket skriptet säger, och bygget beror inte på det.
- [ ] **Golden-test:** generera med den riktiga Kotlin-väljaren (en engångstest i `shared/domain` jvmTest som läser samma YAML, eller som matar väljaren med exakt samma artlista) listan datum → QID för alla dagar 2026-10-01 till 2027-12-31 och jämför JS-porten mot den i `daily-bird.unit.mjs`. 2026-10-07 ska ge Råka (Corvus frugilegus), som appen visade den dagen (skärmdumpen i butiksbild 01).
- [ ] Datumet är dagens datum i Europe/Stockholm vid bygget (`Intl.DateTimeFormat` med `timeZone`), inte UTC.
- [ ] **Visning:** har dagens fågel en publicerad artsida används den (foto, namn, länk). Saknas sidan, eller har arten inget foto som planschen får visa (CC0, public domain, CC BY eller CC BY-SA, `plateImage` i `daily-bird.mjs`; beslut 2026-10-08): välj deterministiskt bland publicerade arter med ett sådant foto, med samma frö (stabilt hela dagen), och visa ingen "samma som i appen"-rad. Är det appens fågel visas en handskriven rad "samma fågel som i appen i dag" / "the same bird as in the app today", från och med 2026-10-15 (innan dess har appen i telefonerna den gamla regeln).

### Task 2: Startsidans hjälte, Dagens fågel som plansch

**Files:** Modify `website/src/components/Hero.astro` (eller ersätt med `components/hero/DailyBirdHero.astro`), `HomePage.astro`, copy-filerna; remove AI-rödhaken `website/src/assets/hero-robin.webp` och dess rad i `SOURCES.md`; kontrollera `tools/generate-og.mjs` och delningsbilderna (`og-field-*.png`): använder de rödhaken, rendera om dem utan den.

- [ ] Enligt `mockups/lift-c.html`: inramad plansch med dagens fågel (fotot från artsidans data, nedskalat, visat helt och aldrig beskuret, utan något ritat över det; därför får det vara CC0, public domain, CC BY eller CC BY-SA), museietikett med svenskt + vetenskapligt namn, Artportalens månadsstaplar (samma data och färger som artsidans diagram), fotokredit med fotograf, länkad licens och källa, samma rad som artsidans (`PhotoCredit.astro`), länk till artsidan, planschnummer = dagens nummer på året ("Pl. 281" den 8 okt).
- [ ] Rubrik, underrad och knappar (Google Play-märket, App Store "snart") behålls i sak; texten skrivs om så att den bär Dagens fågel. Inga tankstreck.
- [ ] LCP: hjältebilden laddas `eager` med `fetchpriority="high"`, rätt `width`/`height`, inga layoutskift (CLS < 0,05).
- [ ] Playwright: hjälten visar en art med länk till en sida som svarar 200 i bygget, kredit finns, och vid fixturdatum (sätt datumet via en env-variabel i testbygget, t.ex. `BIRDY_TODAY=2026-10-07`) är det den förväntade arten.

### Task 3: "Fåglarna i oktober"

**Files:** en komponent under `components/`, copy, unit-test för urvalet.

- [ ] Fyra publicerade arter som rapporteras mer i innevarande månad än årsgenomsnittet (artsidornas `data.months`, högst kvot månadens värde / medel), med foto, namn och länk; månadens namn i rubriken. Korten beskär fotot, så bara CC0, public domain och CC BY (`cropImage`), aldrig CC BY-SA; en art utan sådant foto hoppas över, och krediten under kortet är artsidans rad. Färre än fyra kvalificerade: visa de som finns, noll: dölj sektionen.
- [ ] Unit-test för urvalet (ordning, likavärden avgörs på QID).

### Task 4: Appens bilder, samma rull som i dag, i kod

**Files:** `AppTour.astro`, `app-tour.ts`, `components/phone/*`, assets för skärmbilderna.

- [ ] Behåll dagens rullbeteende i appturen (samma interaktion, snap, kontroller, tangentbord) men byt de HTML-ritade telefonerna mot riktiga 1.3.0-skärmar i en CSS-telefon: `docs/play-store/screenshots/1.3.0/{sv,en}/` (01 identifiera, 02 match, 03 mina arter, 04 uppslagsverk, 05 artprofil, 07 lyssna; **inte** 06 karta). Kopiera dem till `website/src/assets/screens/1.3.0/` och låt Astro optimera dem (AVIF/WebP, flera bredder). Svensk sida visar svenska skärmar, engelsk engelska.
- [ ] Bildtexterna under varje skärm som planschetiketter enligt mockupen, med appens egna ord (inga ord som appen saknar, t.ex. inte "Inte den? Se fler förslag").
- [ ] Ta bort oanvända gamla telefonritningar och mockupord; uppdatera tester som letar efter dem.

### Task 5: Startsidan, "mind blowing"

- [ ] Gå igenom hela startsidan mot `mockups/lift-c.html` och lyft rytm, typografi och detaljer (planschnummer, etiketter, rivna kanter, handskrivna noter) så att sidan känns som Birdy och ingen annan. Rörelse: en orkestrerad sidladdning för hjälten (planschen "läggs" på plats), diskret, avstängd vid `prefers-reduced-motion`.
- [ ] Premium-bandet på startsidan länkar till `/sv/premium/` resp. `/premium/`; FAQ-svaret om pris länkar dit.
- [ ] Lighthouse mobil på `/sv/` och `/`: prestanda ≥ 90, tillgänglighet 100, best practices ≥ 96, SEO 100 (mät lokalt på produktionsbygget, notera att `/_vercel/insights` 404:ar lokalt).

### Task 6: Premium-sidan (A, Mässing)

**Files:** `website/src/pages/premium.astro`, `website/src/pages/sv/premium.astro`, `components/premium/*`, copy, `Nav.astro` (Premium i menyn), sitemap, tester.

- [ ] Enligt `mockups/premium-a.html` och `premium-a-en.html`: espresso och mässing som appens Premium- och tack-skärm, rubriken "Hela året som fältornitolog.", läget som tidslinje, funktionerna med riktiga skärmar (säsongsstatistiken/årsringen, PDF:en, troférummet; **inte** kartan, ersätt med en not att kartan visas när den nya stilen är klar), prissättningen, löftet till tidiga användare, "ljud-ID är alltid gratis" med BirdNET-skälet, hur man köper (Google Play-märket).
- [ ] **Fakta (bara sant):** Premium = privat karta över dina fynd, fältdagboken som PDF, säsongsstatistik, 7 extra märken. Priser: svenska sidan 199 kr per år eller 499 kr en gång, inklusive moms; engelska sidan "shown in Google Play in your currency". Tidiga användare: "Började du använda Birdy före 17 oktober 2026 klockan 00.00 (svensk tid) behåller du Premium gratis" (ordet "började använda", inte "laddade ned", tills install referrer-frågan är avgjord). Ljud-ID gratis för alla, alltid (BirdNET-modellens licens är ickekommersiell). Ingen reklam, ingen spårning i appen, fältdagboken stannar i telefonen. iPhone på väg. Månadsabonnemang nämns inte (kommer i 1.3.1). Kontrollera mot `docs/play-store/terms.md` och `privacy-policy.md` på `release/1.3.0` att inget motsäger dem.
- [ ] SEO: titel 40 till 60 tecken, beskrivning 120 till 155, canonical, hreflang SV/EN + x-default, en h1, `og:image` (egen delningsbild i mässing), i sitemapen. Ingen `Offer`/`Product`-JSON-LD med priser (priserna styrs av Play); `WebPage` + `BreadcrumbList` räcker.
- [ ] Playwright: båda språken svarar, priserna står bara på svenska sidan, länken från startsidans Premium-band och från menyn fungerar, axe 0 fel.

### Task 7: Nattlig ombyggnad

**Files:** `.github/workflows/daily-site-build.yml`.

- [ ] Ett schemalagt jobb (cron 22:05 och 23:05 UTC, så att det blir 00:05 svensk tid både sommar- och vintertid; ett extra bygge skadar inte) som gör `curl -X POST "$VERCEL_DEPLOY_HOOK_BIRDY"` från en GitHub-secret. Hoppa över tyst om secreten saknas (skriv en notis i loggen). Huvudagenten skapar deploy hooken i Vercel och secreten i GitHub.

### Task 8: QA och överlämning

- [ ] Slå ihop `origin/main` in i grenen. Kör `npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette && npm run test:unit`, `npx astro check`, `npm run build:prod` + `node scripts/check-seo.mjs`, `npm run test:empty-hub`, `npm run test:preview-build`, hela Playwright (fixturbygget) och axe. Skärmbilder 390 och 1440 px av `/sv/`, `/`, `/sv/premium/`, `/premium/` i `docs/superpowers/specs/assets/2026-10-08-webb-lyft-premium/final/`.
- [ ] Rapportera: commits, exit-koder, Lighthouse-siffror, avvikelser från mockuperna och varför, öppna frågor.

### Att komma ihåg (releasechecklistan, Plan 3 Task 11 och varje apprelease efter den)

- **Lanseringsdagen:** `APP_1_3_LIVE_FROM` i `website/src/lib/release.mjs` (2026-10-15) styr heroraden "samma fågel som i appen" och Premium-sidans tidslinje ("Birdy 1.3 kommer" tills dagen är inne, sedan datumet). Slirar go-live: flytta den. Flyttas brytpunkten (17 oktober 2026 kl. 00.00) ändras också texterna om den i båda copy-filerna (Premium-sidans tidslinje och löftet till tidiga användare).
- **Appens artlista:** regenerera golden-filen på release-grenen (`BIRDY_GOLDEN_OUT=website/tests/fixtures/daily-bird-golden.json ./gradlew :shared:domain:jvmTest --tests "*DailyBirdGoldenGenerator*"`, ett Gradle-jobb åt gången), höj `APP_VERSION` i `website/src/lib/release.mjs`, kör `npm run app-species:snapshot` och sedan `npm run check:app-species`. Det sista ska vara grönt på release-grenen och på main efter sammanslagningen; rött på main dessförinnan är väntat och stoppar inte bygget.
