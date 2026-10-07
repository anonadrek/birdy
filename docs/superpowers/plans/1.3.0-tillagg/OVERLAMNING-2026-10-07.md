# Överlämning 2026-10-07: release 1.3.0 + artsidorna, fortsätt härifrån

Skriven i slutet av sessionen 2026-10-05–07 (Windows). Läs den här filen och `CLAUDE.md` (status överst) först i nästa session. Allt nedan är på disk; inget ligger kvar bara i den gamla sessionens scratchpad.

## 1. Läget i git

| Gren | Worktree | HEAD | Läge |
|---|---|---|---|
| `release/1.3.0` (utkast-PR #53) | `C:/w/birdy-130` | `795754b5` + denna överlämningscommit | **Allt godkänt hittills är sammanslaget här** (se §2). Full gate grön efter sista merge (`:shared:content/domain/ml/datastore` + `:shared:pdf:testDebugUnitTest` + `:composeApp:testDebugUnitTest` + `assembleDebug` + ktlint + detekt), pipelinen 336 tester. species.db byggd om efter sammanslagningen (`application_id` 268266909; 2 066 bilder; IUCN LC 713, NT 47, VU 40, EN 12, CR 6, EX 3, NE 18). |
| `main` | `C:/Users/abbea/dev/1-mina-projekt/birdy` | se `git log` | Artsidornas pipeline (fas 1b) är sammanslagen här (668 tester). Har INTE 1.3.0-koden eller de städade fotona än. |
| `feature/1.3-stats`, `feature/1.3-dagens-fagel`, `feature/1.3-foto-klar`, `feature/1.3-puts`, `feature/1.3-bilder` | `C:/w/birdy-stats`, `-dagens`, `-foto`, `-puts`, `-bilder` | pushade | **Alla sammanslagna i release.** Worktrees kan tas bort (`git worktree remove`), grenarna kan raderas efter att PR #53 är sammanslagen. |
| `pipeline/artsidor-fas1b` | `C:/w/birdy-fas1b` | `2b86967a` | Sammanslagen i main. Worktree kan tas bort. |

**Säkerhetskontrollen:** Claude Codes automatiska kontroll stoppar hjälpagenter som försöker slå ihop grenar till en delad gren ("Modify Shared Resources"). Albin godkände 2026-10-07 att huvudagenten slår ihop godkända grenar i `release/1.3.0`. Gör sammanslagningarna själv (inte via hjälpagent), och fråga Albin om något nytt slags delad ändring.

## 2. Sammanslaget i release/1.3.0 (granskat och godkänt)

- Plan 3 Task 0–6 (se planen), Task 7 enhetsgenomgången: 10 buggar + 9 granskningsfixar (se CLAUDE.md-statusen).
- **7c Säsongsstatistik B** (årsring, årets första fynd, topp tre som sigill). Spec: `task07c-statistik.md`.
- **7d Dagens fågel B + aviseringen** (datum, vetenskapligt namn, "Läs om arten"/"Lyssna efter den", "0 av 3 dagar", remsor, prick på fliken, midnattsbyte, avisering med bild + två knappar). Spec: `task07d-dagens-fagel.md`.
- **7f Fotona utan grön ton** (inget ritat över fågeln; texten under fotot på Match/Artprofil; rundad kant på Premium/Tack; Match-fotots höjd följer textstorleken).
- **7g Putsen + innehåll** (pluraler, sökningen behålls, "tal" → Talgoxe först, svensk A–Ö, IUCN-mappningsbuggen, 15 svenska namn, Vinmajna, PLATS-rubriken, Premium-kortets skugga neutral, API 30-ikoner). Spec: `task07g-puts.md`.
- **7e-1 Bildstädningen** (~245 foton bytta, alkekungens pamflett/ägg/kartor/fel arter borta, riktiga foton före planscher, sRGB, rena krediter, licenslista CC0/PD/CC BY/BY-SA i hämtaren + validatorn + CI-test).

## 3. Kvar till 1.3.0 (i denna ordning, SDD med två-stegs-granskning)

1. **7b Tillbakapilar på varje skärm.** Spec: `task07b-tillbaka.md` (granskningens lista över skärmar utan synlig väg tillbaka + inkonsekvenser). Obs: Match/Artprofil har nu `PhotoHero(textBelowPhoto = true)`; `ArchiveStateAcrossProfileTest` trycker på contentDescription "Tillbaka".
2. **7e-2 Fotokrediter i appen:** kredit per foto på artprofilen (fotograf, licens med länk, källa, "nedskalad"), en lista över fotografer under Om, krediterna finns redan rena i species.db (`SpeciesImage`). Rätta villkoren §2 (undantag för CC-innehåll) och §4 (krediten finns på Om-skärmen, rätt licensnamn) i `docs/play-store/terms.md`.
3. **7j Veckans uppslag "Uppslag 1"** (Albin 2026-10-07). Design: `docs/superpowers/specs/assets/2026-10-06-1.3-val/birdy-uppslag-marken.html`, sektion A, "Uppslag 1" (S).
4. **7k Märkesbilden "Bild 2"** (Albin: "Märkesbild S", tolkat som rekommenderade Bild 2: dina tre senaste stämplar i solfjäder; bekräfta med Albin om osäkert). Samma fil, sektion B. Ta bort `composeApp/src/commonMain/composeResources/files/branding/trophy_hero.webp` helt (okänt ursprung).
5. **7l Märken 1a** (märkesfliken med "Inom räckhåll") om det hinns före go-live, annars 1.3.1. Samma fil, sektion C. Byt namn på säsongsmärket "Året runt" → "Fyra årstider" (två märken heter "Året runt").
6. **7m Svenska namn enligt BirdLife Sverige** (Albin "Yes" 2026-10-07): de 30 arter vars svenska namn skiljer sig från BirdLife Sveriges aktuella lista (t.ex. sädgås → skogsgås, cettisångare → sumpcettia, rödfody → röd fody, Kap Verdepetrell → kapverdepetrell; listan togs fram i Task 7g, jämför `species_list.yaml` mot BirdLife Sveriges namnlista) byts till de officiella namnen. Det gamla namnet blir sökord (sökning på "sädgås" hittar arten) och artprofilen visar "tidigare sädgås" / "formerly …" om det behövs. Sätt namnen som `common_sv` i `species_list.yaml` så en pipeline-körning behåller dem; species.db byggs om; test som låser namnen. Gäller även artsidorna (de läser namnen från samma källa).
7. **Små uppföljare** (`uppfoljare.md`): Dagens fågel får inte välja en utdöd art; midnattsmarginal + test.
8. **7i Upphovsrätts- och juridikgenomgång** (KRÄVS): se Plan 3, avsnittet Task 7i, tio punkter; resultatet i `docs/legal/2026-10-1.3.0-genomgang.md`.
9. **Task 8** R8-röktest (minifierat bygge) av tack-skärmen och betalväggen.
10. **Task 9** butiksbilder i webbens look (KRÄVS; bara CC0/PD-foton; feature graphic; laddas upp med vC130, agenten fyller i Console med Albins godkännande).
11. **Task 10** vC130 produktionsbygget: höj `releaseVersionCode` till 130, ny MapTiler-nyckel (alternativ b: Albin skapar ny nyckel i MapTiler Cloud, Default key återkallas när vC130 är live), signering, raden `Birdy release config: versionCode=130 versionName=1.3.0 GRANDFATHER_CUTOFF_MS=1792101600000 billingTestBuild=false`. **Brytpunkten 2026-10-16 00:00 flyttas FÖRE bygget om go-live inte sker senast 2026-10-14.** Ladda upp till Intern testning först; Albin kör hela testsviten på telefonen; befordra till produktion.
12. **Task 11** avslut: CI grön på PR #53, slutgranskning av hela release-grenen, merge till main, CLAUDE.md.

**1.3.1 (direkt efter go-live, Albins val 2026-10-07 "kör allt" enligt rekommendationen):** Märken 1b (certifikatbladet med fyndet som gav märket: kräver ett nytt fält `observationId` i `BadgeUnlock`, troférummet som hyllor, stämpelslaget), PDF 1 (1.3-färgerna, riktiga foton med krediter, sida "Bildkällor"; i dag har PDF:en inga foton och rad 25 krockar med sidnumret), Pop-up 1 (Premium-ark från 5:e sparade fyndet, en gång, aldrig för tidiga användare/Premium, gemensam 14-dagarspaus för alla Premium-uppmaningar; 3:e sparade är redan Play-recensionen), Intro 1 (fem sidor, levande demo, Dagens fågel). Senare: Uppslag 2/3, Märken 2 (fältpass), PDF 2, Intro 2.

## 4. Emulatorn

- AVD `pg-api36` (API 36, google_apis x86_64) körs som `emulator-5554`; `pg-api30` finns. WHPX fungerar. Start: `emulator -avd pg-api36 -no-snapshot -no-boot-anim -netdelay none -netspeed full` (från `C:/Users/abbea/AppData/Local/Android/Sdk/emulator`).
- Debugappen som ligger installerad är från FÖRE de senaste sammanslagningarna. Bygg och installera om från `C:/w/birdy-130`: `./gradlew :androidApp:installDebug` (JAVA_HOME-prefix enligt CLAUDE.md). Debugbygget har inget asset pack: artfotona syns först i ett AAB-/bundletool-bygge.
- Hjälpverktyg: `python .superpowers/qa/tap_text.py "<text>" [--desc] [--contains] [--serial emulator-5554]` (gitignorerat, i `C:/w/birdy-130`).
- **Regel: en agent åt gången äger emulatorn** (två agenter krockade 2026-10-06).
- Kvar att se på enhet: riktiga fotona i Dagens fågel-hjälten och remsorna, 08.00-aviseringen med bild (kräver AAB), prickens försvinnande, "Spara observation" vid 130 % text, Premium-arket och tack-skärmen på API 30 (treknappsnavigering), tillbakapilarna när 7b är klar.

## 5. Albins steg (blockerar release)

1. **Köptestet med vC129** på sin telefon (gå med via `https://play.google.com/apps/internaltest/4701434188270894832`, installera, köp Livstid med testkortet; "Välkommen, fältmedlem." = hård grind före vC130).
2. **Ny MapTiler-nyckel** i MapTiler Cloud före vC130-bygget.
3. **Bankkontot** i betalningsprofilen (sist): Play Console → Inställningar → Betalningsprofil → Betalningssätt → "Lägg till betalningsmetod" (agenten öppnade sidan 2026-10-07; Albin fyller i själv).
4. Kontrollera att inspelningstimern går i rätt takt på riktig telefon (den gick för fort på emulatorn).

## 6. Albins öppna beslut

- **Gratisanvändare och raden "0 av 3 dagar"** (räknar mot Premium-märket Dagens fågel-jägare; i dag syns den för alla).
- ~~30 svenska namn~~: **beslutat 2026-10-07: byt till BirdLife Sveriges officiella namn** (Task 7m ovan).
- **Tolkningen av svaren 2026-10-07** ("Uppslag 1 > B, Märkesbild S, Yes kör allt"): tolkat som Uppslag 1 (A), Märkesbild Bild 2 (B), övrigt enligt rekommendationen ovan. Bekräfta kort i nästa session.

## 7. Artsidorna (parallellt spår)

- **Fas 1b (pipelinen) klar och i main.** Nästa: R1 (nyckeln, har kredit) och R2 (`web sources`, gratis) körs **först när de städade fotona finns där pipelinen läser dem**. Fotona finns i `release/1.3.0` men inte i main förrän 1.3.0 slås ihop (Task 11). Två vägar: (a) vänta på release-sammanslagningen (~12–14 okt), eller (b) slå ihop `release/1.3.0` → `main` tidigare (OBS: då går också juridiktexterna för 1.3.0 live på birdy.community före appen; Albin godkände det upplägget för webben förut). Rekommendation: (a), och använd tiden till fas 2.
- **Fas 2 (sidorna) kan byggas nu** mot testdata: plan `docs/superpowers/plans/2026-09-25-artsidor-fas2-sidor.md` (med Tillägg för publiceringsloopen: `web publish --next`, `--exclude` efter första felet, `reports/publish-loop-excluded.txt`, kod 3 för `none`, Task 16), gren `website/artsidor` i en ny worktree `C:/w/birdy-artsidor`. SDD.
- **Efter R2:** kör om `web sources` för Kaja (NE→LC) och för de godkända arter vars foton byttes (83 st, se `review_notes`) innan deras sidor genereras.
- **Sociala medier** (spec §15.1 i `2026-09-25-artsidor-design.md`, engelska, startar när de första sidorna publiceras; Albin skapar kontona själv). Albins val 2026-10-07: runda två, rekommendationen: dagligen "See the song" (när det finns en tillåten inspelning, bara CC0/PD/CC BY; ca 45 av 180 arter) annars "The field journal comes alive", fredagar "Who is it?", varannan söndag "Same bird?", rutnätet som ett konstverk (rivna papperskanten på samma höjd). Förhandsvisningar: `docs/superpowers/specs/assets/2026-10-06-1.3-val/birdy-posts-2.html` (och runda ett `birdy-posts.html`).

## 8. Länkar (privata artefakter, Albins konto)

- Val statistik/Dagens fågel: https://claude.ai/artifact/V3YRKWD1ascX26WjY8JmRg
- Foton före/efter: https://claude.ai/artifact/H7XbXsmxAzbasGXDvZzEWL
- Inlägg runda ett: https://claude.ai/artifact/XpdpV71A2Kd6TEzXcrSrjb
- Inlägg runda två: https://claude.ai/artifact/LcWuBjVSxt4MG3sSW9XQHA
- Veckans uppslag, märken, PDF, pop-up, intro: https://claude.ai/artifact/JSYaFchsZSCv4Y2DQGwWoL
Samma filer ligger i `docs/superpowers/specs/assets/2026-10-06-1.3-val/` på release-grenen.

## 9. Arbetssätt som fungerade (behåll)

Subagent-driven development: en implementerare per task (Sonnet för mekaniskt, Opus för omdöme), en separat granskare efter varje task (spec, sedan kvalitet), fixvågor tills granskaren säger "Ready". Parallella spår i egna worktrees under `C:/w/` när de rör olika filer; huvudagenten slår ihop. Granskningarna fångade riktiga fel varje gång (bl.a. pamflettfotot, tyst inspelning, midnattsbuggen, publicerade sidor med strukna fakta). Förhandsvisningar som länkar innan större UI-arbete; Albin väljer de djärvare förslagen.
