# Överlämning 2026-10-09 eftermiddag (Windows)

## Kvällen 9 okt (läs först; resten av filen är eftermiddagens läge)

**Albins svar 9 okt:** visningsnamnet "Birdy: Bird ID" får agenten ordna, liksom TikTok-företagskontot och YouTube-verifieringen; **telefonordningen A** (orden först, flocken under, som byggt); **klippsidan: "kör"**; Märken 1a i 1.3.1: agentens rekommendation (ja, ihop med 1b; "Nära att låsa upp" flyttar till fliken Märken som "Inom räckhåll") väntar på hans svar. Han bad också agenten "delete the double posts".

**Gjort:**
- **Visningsnamnet "Birdy: Bird ID":** TikTok, Instagram (via Kontocenter) och YouTube klara; Facebook-sidan begärd (Albin klickade "Begär ändring", granskas upp till 3 dagar, sedan låst 60 dagar). Business Suite visar redan det nya namnet.
- **YouTube-kanalen telefonverifierad** (Albin skrev in koden). Den höjde inte dagsgränsen nog: efter 11 uppladdningar 9 okt kom "Daglig uppladdningsgräns uppnådd" igen.
- **YouTube:** 19–28 okt schemalagda 08.00 Stockholm (tidszonen vald uttryckligen, så att 26–28 okt efter sommartidens slut också går ut 08.00). Listan kontrollerad: 9 okt publicerad, 10–28 okt en per dag. **Kvar: 29 okt–7 nov (10)**, tidigast när gränsen släppt (cirka 19.15 lör 10 okt).
- **Meta, dubbletterna:** de gamla versionerna 12–22 okt (FB + IG, 22 st) flyttade till Utkast med guard per rad och kontrollerade efter omladdning. Röda rader "Det gick inte att publicera" står kvar i Schemalagt (publiceras inte). 23–28 okt har fortfarande bara gamla versioner.
- **TikTok, dubbletterna:** agenten kan inte avplanera där (bara "Delete" finns, och permanent radering gör Albin). Albins lista: för varje datum 11–18 okt, behåll den översta raden och ta bort resten (9 inlägg). **Första tidsgränsen lör 11 okt 08.00.**
- **Telefonordningen:** jämförelsesidan `docs/superpowers/specs/assets/2026-10-09-telefonordning/` (artefakt FPNbw3BDY9QSLj5DizZZGb), specen uppdaterad.
- **Flocken lyfter** (`C:/w/birdy-flock`): Task 5 (rörelsen) klar efter två fixrundor (`8fa3c722`, `e683a103`, `f4fbc52f`: startar vid alla höjder och zoomnivåer, en tangent eller fokus i hjälten hoppar till slutet, polaroiden finns kvar för skärmläsare under flykten, inga frusna rutor vid storleksändring). Task 6 (menyn på persikan, `19194e3b`) och Task 7 (kontrasttester, `d8a39833`) klara och granskade. Task 8 (delningsbilderna) pågick vid överlämningen. Smoke 240 gröna efter Task 7. Småsaker till slutstädningen står i ledgern.
- **Klippsidan** (`C:/w/birdy-klipp`, gren `website/klipp`, lokal): spec `28a5b748` (på main), plan `2e88b8ae` (6 tasks, delad i `C:/w/birdy-klipp-qa/tasks/`). Task 1 baslinje (unit 98, Playwright 209), Task 2 klippdatan `b7c066bb` (30 klipp 9 okt–7 nov, 30 flockomslag, 5 CC BY-silhuetter märkta bearbetade) granskad "Ready" + en liten fixrunda (`.DS_Store`-filter, datumkoll, felmeddelanden, omslagsväg). **Notering:** vid push hamnar alla 30 omslag i det publika repot före sina dagar; ledtrådarna finns redan publikt på `social/see-the-song`, så det accepterades. Kvar: Task 3–6, sedan Vercel-förhandsvisning till Albin.

**Nästa session, i ordning:**
1. Påminn om TikTok-städningen (lör 11 okt 08.00 för 11 okt).
2. YouTube 29 okt–7 nov när gränsen släppt (receptet i runbooken, avsnittet "Kvällen 9 okt"; Chrome-fönstret ska ligga framme, eftersom ett skymt fönster inte ritas om, och tryck aldrig Escape i uppladdningsdialogen).
3. Meta 23 okt–7 nov (16) när Meta tar uppladdningar igen, sedan de gamla 23–28 okt till Utkast.
4. Flocken lyfter Task 8 (granskning) till Task 11 (push och Vercel-förhandsvisning till Albin), och klippsidan Task 3–6, båda med SDD, ett tungt jobb åt gången.
5. När klippsidan är live: Albin lägger `birdy.community/clips/` i bion på Instagram och, efter bytet till företagskonto, TikTok (båda bara i appen).

Albin bad om en ny session ("Se till så vi kan dra igång i en ny session"). Läs den här filen först, sedan de översta posterna i CLAUDE.md. Börja med `git pull` i huvudklonen. Överlämningen från 2026-10-08 (`OVERLAMNING-2026-10-08.md`) gäller fortfarande för artsidorna, sökindex och rutinerna; det som har ändrats sedan dess står här.

## Börja här

1. **Albins städlista har tidsgränser.** Ligger en gammal version av ett inlägg kvar när dagen kommer går båda ut kl. 08.00. Första gränsen är **lör 11 okt 08.00** (TikTok). Fråga Albin om listan är gjord. Agenten tar inte bort inlägg själv: Albin vill göra det sist, och Claude Codes säkerhetskontroll stoppar ofta borttagning. Se avsnitt 3.
2. **Flocken lyfter, Task 5 (rörelsen)** med SDD i `C:/w/birdy-flock`. Se avsnitt 1.
3. **De sociala uppladdningarna som återstår** (Chrome, Albins inloggade webbläsare): YouTube 19 okt till 7 nov när dagsgränsen har släppt, Meta 23 okt till 7 nov när strypningen har släppt. Se avsnitt 2.

Kör högst ett tungt jobb åt gången (Gradle, emulator eller Lighthouse) och högst tre Opus-agenter samtidigt.

## Spåren

| Spår | Var | Läge | Nästa |
|---|---|---|---|
| Flocken lyfter | `C:/w/birdy-flock`, gren `website/flocken-lyfter` (lokal, inte pushad) | Task 0 till 4 klara och granskade (`b507a789`) | Task 5 till 11 |
| Sociala: nya omslag och loop | runbooken, avsnittet "Omplaneringen med flockomslagen" | TikTok klart, Meta klart till 22 okt, YouTube inget nytt | Meta 23 okt till 7 nov, YouTube 19 okt till 7 nov |
| Albins städlista | avsnitt 3 | okänt om något är gjort | påminn före lör 11 okt 08.00 |
| 1.3.1 | `release/1.3.0` (`56efe9f8`) och `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` där | punkt 7, 8 och 12 klara; länkarna i Märken 1b beslutade (A) | punkt 6, sedan 2 till 5 |
| Klippsidan | förslag, avsnitt 5 | väntar på Albins "kör" | sidan först, länkarna sedan |
| Galleriet på startsidan | `docs/superpowers/specs/assets/2026-10-08-galleri/` | ordningen vald, fyra nya skärmar tagna | skärm 12 om, sedan in i `AppTour` |
| Artsidorna och sökindex | `C:/w/birdy-publish`, Search Console | paus, 100 av 180 arter live | tidigast tors 15 okt (överlämningen 2026-10-08, avsnitt 3) |

## 1. Flocken lyfter (startsidans hjälte)

- **Spec:** `docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md` (godkänd, `34c3a81f`).
- **Plan:** `docs/superpowers/plans/2026-10-09-startsidan-flocken-lyfter.md` (`a3338818`). **Läs planen på grenen:** Task 4:s kodblock rättades där i `b507a789`. Task 5 till 11 är desamma som på `main`.
- **Worktree:** `C:/w/birdy-flock`, gren `website/flocken-lyfter`, ren vid `b507a789`. Commits:
  - Task 1 `68e8d55d` (flockdatan)
  - Task 2 `9ad8287c`, `d1333a7b`, `8692de45` (flocklogiken)
  - Task 3 `5dfc9b75` (persikotonerna)
  - Task 4 `b2fc41b3`, `b507a789` (orden, den landade flocken, polaroiden)
- **Ledger:** `C:/w/birdy-flock/.superpowers/sdd/progress.md` (lokal på Windows). **Uppgifternas texter**, uppdelade ur planen: `C:/w/birdy-flock-qa/flock-tasks/task-NN.md` (task-04 är texten före fixrundan).
- **Att bära in i Task 5** (ur granskningarna av Task 2 och 4):
  1. Visa polaroiden först när dagvakten (`same-as-app-guard`) har körts, eller håll radens plats, så att inget hoppar.
  2. Med minskade rörelser blir varje stiländring en övergång på 0,01 ms (`website/src/styles/global.css:86`). Skriv och läs aldrig tillbaka i samma bildruta, och sätt `transition: none` på det som positioneras.
  3. Ringen runt den tända fågeln: SVG:n har 2 px, canvasen `max(1.8, size * 0.14)`, som mest 0,25 px skillnad. Godta det eller använd 2 på båda.
  4. Ankarna som Task 5 behöver finns kvar i `Hero.astro`: `<header class="hero" data-hero ... data-flock-index={litIndex}>`, skriptblocket med `import './hero/same-as-app-guard'` och den sista regeln `@media (min-width: 1024px)`.
- **Småsaker till slutstädningen (valfria):**
  - Stresstestets kommentar ("fixed clock") stämmer inte.
  - `overflow: hidden; overflow: clip;` som reserv för Safari äldre än 16.
  - Ordet "plate" ligger kvar i `tests/unit/daily-bird.unit.mjs:117`, `tests/fixtures/make-species-fixtures.mjs:75` och `src/assets/photos/SOURCES.md:12`.
  - Parametern `innerWidth` i `Polaroid.astro` skuggar den globala.
- **Testerna** (i `C:/w/birdy-flock/website`):
  - `npm run test:unit`
  - `npm run build:fixtures`, sedan `PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4751 npm run test:smoke` (220 gröna efter Task 4)
  - vakterna `test:i18n`, `test:no-accuracy`, `test:contrast`, `test:no-dashes` och `test:palette`
  - `npm run check` och `npm run test:empty-hub` (kräver `data-flock-index="107"`)
- **Baslinjen för Lighthouse** (Task 10): `/sv/` på mobil, median 96 / 100 / 96 / 100, LCP 2 711 ms (den gamla hjältebilden). Filerna och `lh-summary.mjs` ligger i `C:/w/birdy-flock-qa/`.
- **Mätskript för granskningen:** `C:/w/birdy-flock-qa/review-t4/measure.mjs`. Det körs mot `astro preview` på port 4767 (`BASE=` byter adress) och har varianter för stående foto, långt namn, lång kredit och stress.
- **Väntar på Albin: telefonordningen.** På telefon står orden först och flocken under, som i prototypen han godkände. Specen sa tvärtom. Frågan ställdes 9 okt och är obesvarad.
- **Regel:** grenen pushas och förhandsvisas på Vercel först i Task 11. Ingen merge till `main` innan Albin har sett förhandsvisningen och sagt ja.

## 2. Sociala medier: nya omslag och loop

Albins OK 8 och 9 okt: "Go for it", "you have go ahead" och "re upload and ill remove all the old ones last". Varje video har ett eget omslag där flocken formar fågeln, och slutet loopar in i omslaget (`8cd2e9a3` på `social/see-the-song`).

**Läget kl. 14.30:**

| Datum | TikTok | Facebook + Instagram | YouTube |
|---|---|---|---|
| 9 okt (Blåmes) | utanför omplaneringen | utanför omplaneringen | utanför omplaneringen |
| 10 okt (Trana) | flockomslag utan loop | flockomslag utan loop | gammal |
| 11 och 12 okt | loop överst, äldre versioner kvar | loop; äldre versioner i utkast, gammal 12 okt kvar | gammal |
| 13 till 18 okt | nya med loop, gamla kvar | nya med loop, gamla kvar | gammal |
| 19 till 22 okt | nya med loop | nya med loop, gamla kvar | saknas |
| 23 till 28 okt | nya med loop | **bara gamla** (byts) | saknas |
| 29 okt till 7 nov | nya med loop | **saknas** | saknas |

Tranan 10 okt fick ingen loop: TikToks innehållskontroll hängde två gånger i över 25 minuter på den loopade versionen, och den kasserades hellre än att två tranor skulle gå ut.

**Kvar att ladda upp** (receptet i runbooken, med en vakt i JS före varje Schemalägg):

1. **YouTube 19 okt till 7 nov (20 videor).** Dagsgränsen nåddes 9 okt cirka 01.00 och gäller i 24 timmar. Albin kan höja den på youtube.com/verify. Därefter nya versioner för 13 till 18 okt, om Albin vill. Förslaget är att 10 till 12 okt går ut som de är, eftersom Shorts inte tar något eget omslag. Varje bytt dag kräver att Albin tar bort den gamla före 08.00.
2. **Meta 23 till 28 okt (6, byts) och 29 okt till 7 nov (10, nya).** Meta strypte vid 23 okt efter cirka 20 uppladdningar; uppladdningen stod på 0 %. Försök igen nästa dag. Bocka ur "Textning" i varje inlägg.

**Material:**

- Videorna: `C:/w/birdy-social/tools/social/out/{week1,week1-reserves,oct19-nov7}/<slug>/` med `see-the-song.mp4`, `cover.jpg`, `caption.json` och `schedule.csv`. Ordningen 19 okt till 7 nov står i runbooken.
- `C:/w/birdy-social-hjalp/cap.py <slug>` skriver ut mappen och texterna för Facebook, Instagram och TikTok.
- Samma mapp har statustabellen (`omplanering.md`) och städlistan (`att-ta-bort.md`) som de såg ut kl. 14.

**Fällor från i dag** (alla står i runbooken):

- TikToks "Content check lite" har en daglig gräns och kan hänga i över 25 minuter. Slå av reglaget för inlägget efter 10 minuter.
- Chrome har zoom per webbplats. Räkna om koordinaterna med `874 / window.innerWidth`.
- En andra flik blir dold, och Business Suite slutar då ladda miniatyrerna.
- Meta kan inte byta omslag på ett schemalagt inlägg.
- Loop eller inte avgörs genom att spola videon till slutet med JS: loopen slutar på omslaget, den gamla på slutkortet.

## 3. Albins städlista (med tidsgränser)

Albin tar bort de gamla själv. Ligger en gammal version kvar när dagen kommer går båda ut kl. 08.00.

| Senast | TikTok | Facebook + Instagram |
|---|---|---|
| lör 11 okt 08.00 | 11 okt: den nedre gråsparven (flock utan loop) | 11 okt: den röda raden "Det gick inte att publicera" (inlägget ligger redan i Utkast) |
| sön 12 okt 08.00 | 12 okt: behåll den översta gräsanden, ta bort de två andra | 12 okt: de två med mörk "?"-miniatyr |
| 08.00 varje dag 13 till 18 okt | den gamla utan "Silhouette:" i texten (6 st) | de två med mörk "?"-miniatyr (12 st) |
| 08.00 varje dag 19 till 22 okt | inget | de två med mörk "?"-miniatyr (8 st) |
| 23 till 28 okt | inget | vänta tills de nya är uppe, ta sedan bort de gamla |
| när som helst | inget | 8 utkast, alla ersatta |

- På Instagram heter raderna med mörk "?" "Din reel".
- På TikTok står den senast skapade överst samma dag.
- YouTube: laddas nya versioner upp för 13 till 18 okt måste den gamla samma dag bort före 08.00.

## 4. 1.3.1

- **Klart 9 okt** (inslaget i `release/1.3.0` med `8456faa1`, CI grön inklusive iOS):
  - punkt 7: inga tankstreck i appen, arttexterna omskrivna med `birdy-fetcher app-dashes`
  - punkt 8: PDF-etiketter som ryms
  - punkt 12: notiskanalernas namn på appens språk
- **Albins beslut:** mellanslagsbindestrecken " - " i fyra svenska arttexter står kvar.
- **Märken 1b:** fältrapporten och troférummet länkar till varandra enligt **alternativ A, "Stämpeln som bro"** (Albin 9 okt: "I dokumentet så tycker jag samma som dig"). Skissen och vad koden behöver ligger i `docs/superpowers/specs/assets/2026-10-09-faltrapport-troferum/` på `release/1.3.0` (`56efe9f8`) och i planens punkt 2.
- **Öppna frågor till Albin:**
  - Följer Märken 1a med i 1.3.1? Den står inte i tabellen, men överlämningen för 1.3.0 sa att den flyttas dit.
  - Märken 1b:s hyllor visar inget "Nära att låsa upp". Tas det bort ur troférummet ska raden "Närmast" i fältrapporten öppna fliken Märken i stället.
- **Nästa i planens ordning:** punkt 6 (månadsprenumerationen, med egen granskning av köpflödet), sedan 2 till 5 (designen finns), sedan 9 till 11 när grindarna närmar sig.
- **Grindar:** BirdNET:s svar, köptestet med vC129, MapTiler Flex och Resend (nästa vecka) och releasedagen.
- `C:/w/birdy-streck` är inslagen och kan tas bort med `git worktree remove` när som helst.

## 5. Klippsidan (förslag, väntar på Albins "kör")

Albin frågade 9 okt om klippen ska länka till artsidorna. Förslaget:

- **Från inläggen:** Facebook och YouTube länkar redan direkt till artsidan. Instagram och TikTok kan inte ha klickbara länkar i texten och säger "Link in bio", men bion går till startsidan. Bygg en sida på birdy.community som listar klippen, nyaste först, var och en med länk till sin artsida, och gör den till bio-länken på Instagram och TikTok. Egen sida, ingen Linktree, ingen spårning.
- **Från artsidorna:** en kort rad, till exempel "Se klippet: TikTok · Instagram · YouTube · Facebook", på de cirka 30 artsidor som har ett klipp. Inga inbäddade spelare: de laddar plattformarnas skript och kakor och bryter löftet om att nästan inget samlas in.
- Ett inlägg får en publik adress först när det är publicerat, så länkarna läggs på i takt med att inläggen går ut, ett litet steg i veckan.
- Börja med klippsidan. Inget av det rör Flocken lyfter.

## 6. Galleriet på startsidan

- Albin valde ordningen "7, 1, 6, 4, 2, 12, 14, 16" (8 okt).
- Skärmarna 7, 12, 14 och 16 är tagna på emulatorn på svenska och engelska (`130f070f`, `docs/superpowers/specs/assets/2026-10-08-galleri/screens/` med licenskoll per foto).
- **Skärm 12 (Veckans uppslag) tas om** med fynd vars foton är fria. I dag syns appens egna QA-bilder, med okänd licens.
- Sedan in i `website/src/components/AppTour.astro`: bilderna i `website/src/assets/screens/1.3.0/{sv,en}/`, bildtexterna i `copy.{sv,en}.json` (`tour.slides`). Gör det på en egen gren; `copy.*.json` ändras också av Flocken lyfter, så räkna med en liten konflikt.

## 7. Datum

- **Fre 9 okt 08.00:** de första inläggen (Blåmes) var schemalagda. Meta visade dem som publicerade under dagen; TikTok och YouTube är inte kontrollerade.
- **Cirka 01.00 lör 10 okt:** YouTubes dagsgräns släpper.
- **Lör 11 okt 08.00:** första tidsgränsen i städlistan. **Sön 12 okt 08.00:** den andra.
- **Mån 12 okt:** IndexNow körs automatiskt. Titta på Sidor och Prestanda i Search Console.
- **Nästa vecka:** Slack Pro, MapTiler Flex och Resend.
- **Tidigast tors 15 okt:** artsidorna igen (Search Console först, sedan de 2 klara, undantagen och cirka 3 arter om dagen).
- **1 nov:** ny API-månad, våg 3.

## 8. Väntar på Albin

1. Städlistan i avsnitt 3, med tidsgränserna.
2. YouTube: verifiera kanalen på youtube.com/verify om gränsen ska höjas.
3. Telefonordningen i hjälten: orden först, flocken under.
4. Klippsidan och länkarna på artsidorna: "kör"?
5. Visningsnamnet "Birdy: Bird ID" på de sociala kanalerna (förslag, för att skilja appen från artisten Birdy).
6. TikTok: byt till företagskonto i appen och gör gillade videor privata.
7. Märken 1a i 1.3.1, och "Nära att låsa upp" i troférummet.
8. Kvar sedan 8 okt:
   - Instagram-bion (klistras in i appen) och användarnamnet för Facebook-sidan.
   - "Publicera" för blogginlägget "See the song".
   - TikTok och Instagram som plattformsegendomar i Search Console.
   - Undantagens standardval för våg 2 (kan ändras före 15 okt) och API-gränsen för våg 3.
   - Grindarna för 1.3.1 och simkontrollerna på Macen.

## 9. Rutiner

- **Efter varje arbetspass:** en kort rapport på svenska i #birdy-status (klart, pågår, nästa, väntar på Albin). Kanalernas id:n finns i agentens lokala minne.
- **Repot är publikt:** inga privata id:n eller adresser till konton (Business Suite, YouTube Studio) i commits, Slack eller delade artefakter. De ligger i agentens lokala minne.
- **Synkregeln:** uppdatera CLAUDE.md, kör `python tools/sync_agents_md.py`, committa och pusha före slutet av varje session.
