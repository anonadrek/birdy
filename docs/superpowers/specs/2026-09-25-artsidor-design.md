# Artsidor på birdy.community: design

> **Datum:** 2026-09-25 (Windows). **Reviderad 2026-10-01** med Albin i ett nytt brainstorm-pass (se "Revision 2026-10-01" nedan), **2026-10-05** med ett beslut om hur faktabladen kontrolleras (se "Revision 2026-10-05" nedan), och samma dag igen med ett beslut om hur sidorna publiceras (se "Revision 2026-10-05 (b)" nedan). Första versionen beslutades med mockups i webbläsaren.
> **Mål:** Organisk söktrafik till birdy.community genom sidor för varje art, grupp och förväxlingspar, byggda ur flera källor och egen data. Sidorna ska svara på det folk söker ("talgoxe", "talgoxe eller blåmes", "hur låter en koltrast"), ge något som Wikipedia inte har, leda vidare till appen och samtidigt bli AlbIT:s eget bevis för SEO Pro.
> **Mockups (godkända 2026-09-25):** `docs/superpowers/specs/assets/2026-09-25-artsidor/`. `helheten.html` visar sidhuvud, kategorirad, ingångssida, artsida och sidfot på dator och i mobil. De nya modulerna (diagram, karta, inspelning, förväxlingsarter) och jämförelsesidan har ingen mockup; de godkänns genom de automatiska kontrollerna och stickprovet efter publicering (avsnitt 14, ändrat 2026-10-05 (b); var tidigare våg 1:s förhandsvisning).
> **Hör ihop med:** `2026-09-24-website-1-3-lyft-design.md` och `2026-09-28-webb-faltboksfarger-design.md` (sajtens look), valvets regel `seo-och-ton-vid-nya-sidor` (checklistan som avsnitt 12 gör till kod) och baslinjen `docs/superpowers/research/2026-09-30-artsidor-baslinje.md`.

---

## Revision 2026-10-01

Albin ville lösa risken för massproducerat innehåll innan den uppstår och hellre göra det ordentligt en gång än göra om det. Kostnaden får öka, det är en investering. Ändringar mot första versionen:

1. **Fler källor:** tyska Wikipedia, rapportdata från Artportalen via GBIF, Svenska rödlistan 2025 och inspelningar från Wikimedia Commons (avsnitt 9.1).
2. **Faktablad först, skrivande sedan:** modellen tar ut citerade fakta ur artiklarna, kod kontrollerar citaten, en automatisk kontroll granskar varje faktum mot sina citat (ersatte Albins manuella granskning 2026-10-05, se nedan), och texten skrivs bara ur godkända fakta. En andra modell kontrollerar varje mening (avsnitt 9).
3. **Egen data på sidan:** månadsdiagram och länskarta ur Artportalen, svensk rödlistestatus, inspelning (avsnitt 5).
4. **Förväxlingsarter:** en sektion på varje artsida och cirka 30 jämförelsesidor för de mest sökta paren (avsnitt 7).
5. **Kontroll:** en automatisk kontroll av varje arts faktablad plus ett undantagsark per våg som Albin beslutar om (ändrat 2026-10-05, se nedan), och sidan visar "Kontrollerad mot källorna" med datum. (En anlitad fågelkunnig granskare övervägdes och valdes bort.)
6. **Öppenhet:** en ny sida "Så gör vi artsidorna" (avsnitt 8).
7. **Publicering i vågor:** en sida publiceras bara när den är färdig och granskad. Våg 1 (vinter- och trädgårdsfåglar) ska vara live i början av december (avsnitt 14).
8. **Licensreglerna** samlade i ett eget avsnitt (avsnitt 10).
9. **Appaningen per art:** approtan säger bara det appen faktiskt klarar för arten (foto, läte, båda eller ingen), läst ur appens modellmappningar.

Fas 1-koden som redan finns (`birdy-fetcher web`, 2026-09-26) återanvänds: Wikipediacachen, bildskalningen, licenstabellen, slugs, rapporten och kontrollerna. Det enda skrivpasset ersätts av stegen i avsnitt 9. Inga pengar har lagts på texter än, så inget behöver göras om.

## Revision 2026-10-05

Albin slutade granska varje arts faktablad för hand. I stället: automatisk kontroll av varje faktum, plus granskning bara av det som sticker ut. Anledningen är tiden: hans manuella granskning var beräknad till 10 till 15 timmar för 180 arter, det nya flödet ska kosta honom cirka 15 till 30 minuter per våg (1 till 2 timmar totalt).

Flödet (ersätter avsnitt 9.4 "Albins granskning", nu "Automatisk kontroll och undantagsgranskning"):

1. **Faktakontroll med en andra modell (V1):** en annan modell än skribenten (Sonnet-klass; skribenten är Opus-klass) får varje faktum med alla sina citat (med artikelns språk) och det omgivande stycket till vart och ett ur den cachade artikeln (**ändrat 2026-10-07**: förut bara det första citatet, så en status som stöddes av sitt andra citat ströks för talgoxe, bofink och råka; citaten bedöms tillsammans), och svarar per faktum `supported`, `partial` eller `unsupported` med en kort motivering, som JSON. Fakta med `partial` eller `unsupported` stryks automatiskt och loggas i rapporten. Blir ett obligatoriskt ämne (utseende, läte, miljö) tomt körs det befintliga omförsöket för faktabladet med felen; failar det igen får arten `status: "failed"`.
2. **Siffror mellan artiklarna (V2, kod, ingen modell):** för fakta med tal (storlek, vikt, vingbredd, kullstorlek och liknande) tar koden ut tal och enhet och jämför med de andra artiklarna (sv/en/de) som anger samma mått. Intervallen måste överlappa (toleransen bestäms i planen, med tester). Skiljer de sig åt blir det en flagga.
3. **Förekomst (V3, kod):** den befintliga statussignalen mot Artportalens data kompletteras med en kontroll mot Svenska rödlistan 2025: status `absent` eller `rare_visitor` för en art som har en bedömd rödlistekategori (inte NA/NE) ger en flagga.
4. **Inspelningen (V4):** Birdys egen BirdNET-modell körs på den klippta inspelningen (skrivbordsreferensen finns redan i `tools/ml-eval/flexref/`; planen utformar anropet, till exempel ett litet script där som pipelinen anropar via `uv run --project ...` och som svarar med JSON top-3 per 3-sekundersfönster). Arten måste ligga i top-3 med minst 0,10 i konfidens i minst ett fönster, annars tas inspelningen bort automatiskt (sidan visar ingen inspelning). Arter som ljudmodellen inte täcker (174 av 180, avsnitt 1) ger i stället en flagga så att Albin kan lyssna. **Ändrat 2026-10-07** (efter provkörningen R3 och en mätning på alla 152 inspelningar modellen täcker): är arten modellens första gissning i minst två fönster med minst 0,02 i konfidens, men når inte 0,10, är det svagt stöd och blir en flagga ("lyssna och besluta") i stället för en strykning. Når inspelningen inte 0,10 prövar V4 först upp till tre andra tillåtna inspelningar av arten från Commons, i källstegets ordning; den första som modellen behåller ersätter inspelningen (med sin egen credit), annars står flaggan eller strykningen. Varje inspelning som ströks eller prövades förgäves sparas i `review.audioStruckSources` och väljs aldrig igen, varken av V4 eller av `web sources`; med listan letar `web sources` ändå efter inspelningar som laddats upp sedan dess (bara en äldre post med `review.audioStruck` utan listan hoppar över ljudsteget helt).
5. **Undantagsark per våg:** ett Google-ark per våg (samma uppbyggnad och kolumner som tidigare, bilaga E, justerad) men med bara: alla flaggor från V2 till V4 (V3 omfattar både den befintliga statussignalen mot Artportalen och den nya rödlistekontrollen), och ett **stickprov** på 2 arter per våg draget med ett sparat frö, visade med hela faktabladet. Albins beslut som förut (`behåll`, `stryk`, `ändra`); flaggor kräver ett beslut. Hittar stickprovet ett fel som V1 till V4 missade dras två nya arter och missen loggas i rapporten.
6. **Import:** artens fält `review.facts = { by, at }` ersätts av `verification = { method: "auto", at: "<datum>", model: "<modell-id>", spotChecked: true|false }`. Ingen text skrivs för en art utan `verification` eller med en öppen flagga.

Konsekvenser för sidorna och metadatan (ska alltid vara sant):

- Raden "Faktagranskad av Albin Abrahamsson {datum}" på artsidor och jämförelsesidor blir **"Kontrollerad mot källorna {datum}"** (EN: "Checked against sources {date}"), fortfarande länkad till "Så gör vi artsidorna".
- JSON-LD: `reviewedBy` tas bort. `lastReviewed` behålls, satt till kontrolldatumet (samma datum som raden på sidan). `check-seo.mjs` kontrollerar datumet och kontrollerar inte längre en granskare.
- "Så gör vi artsidorna" (avsnitt 8): granskningspunkten blir en beskrivning av de automatiska kontrollerna (en andra modell kontrollerar varje faktum mot sina citat, siffror jämförs mellan artiklarna, förekomst jämförs med Artportalen och rödlistan, inspelningen kontrolleras med Birdys egen ljudmodell) och att det som inte går att avgöra automatiskt, plus ett stickprov per våg, granskas av Albin Abrahamsson. Påståenden om att Albin granskar varje art tas bort.
- Jämförelsesidorna behåller "Albin läser varje jämförelsesida i förhandsvisningen" oförändrat (ingår inte i detta beslut).
- Kostnad: V1 lägger uppskattningsvis 10 till 20 USD till (uppskattning, provkörningen avgör), fortfarande inom de 200 USD Albin fyller på.

## Revision 2026-10-05 (b): publicering sida för sida

Albin: "Commit and push each page one by one; I don't need to go through each one manually." Vågorna (rad 15, avsnitt 14) var hittills releasedatum med en gemensam förhandsvisning per våg, där Albin läste varje jämförelsesida och skummade artsidorna innan `publish: true` sattes för hela vågen på en gång. Det byts nu ut mot publicering art för art.

1. **Vågorna blir en körordning, inte releasedatum.** Pipelinen går fortfarande igenom arterna i vågornas ordning (de tolv vanliga arterna och vinterfåglarna först, sedan övriga stannfåglar, sedan flyttfåglarna), men en sida går live så snart den är klar och godkänd. Inget väntar på ett datum.
2. **Per art, helt automatiskt:** källor → faktablad → V1 till V4 → ingen öppen flagga → skrivande → meningskontroll → pipelinen sätter `publish: true` → lokalt bygge (`astro build`) + `check-seo.mjs` + Playwright/axe-testerna för just den sidan → en commit för arten (dess JSON, foton, inspelning och licensdata), till exempel `data(artsidor): talgoxe (Q25485)` → push till `main` → Vercel bygger och publicerar.
3. **En öppen flagga** gör att arten hoppas över och väntar i undantagsarket. Fortsätter samma väg så snart Albin har beslutat.
4. **Stickprovet flyttas till efter publicering:** för var 40:e publicerade art dras 2 (sparat frö, fröt står i rapporten) och läggs i Albins ark. Ett bekräftat fel rättas, sidan publiceras om med nytt datum på "Kontrollerad mot källorna", och missen loggas; då dras två nya.
5. **Jämförelsesidorna** skrivs, kontrolleras och publiceras likadant, en i taget, så snart båda arterna är publicerade. Albin läser inte längre jämförelsesidorna i förväg (ersätter raden i Revision 2026-10-05 om att han behåller den granskningen); de ingår i stickprovet efter publicering, 1 av 10.
6. **Grupp- och ingångssidor** går live automatiskt när de når sin tröskel (ingångssidan med den första publicerade arten, en gruppsida vid minst tre publicerade arter, beslut 4 i avsnitt 2) och uppdateras vid varje senare push. "Så gör vi artsidorna" går live med den första artsidan.
7. **Takt:** högst en push var cirka 5 minuter, så varje Vercel-bygge hinner klart (cirka 230 pushar totalt). Kräver att Vercel-projektets plan (teamet `loop-lead-ab`) tillåter det: Hobby ger 100 bygg om dagen, Pro betydligt fler. Med Hobby sprids körningen över flera dagar, vilket är okej.
8. **Stoppvillkor:** en misslyckad kontroll för en sida blockerar inte de andra, den rapporteras. En nödstopp (`--max-publish N` och stopp vid N publiceringar i rad som failar) så att ett systematiskt fel inte publicerar många dåliga sidor.
9. **Återkallning:** `publish: false` och push. Sidan och sitemap-posten försvinner vid nästa bygge.

Avsnitt 2 (rad 2, 8, 11, 15), 7, 8, 9.4, 9.8, 14, 15, 16, 17 och bilaga E är ändrade. Se avsnitt 14 för hela flödet.

## 1. Utgångsläge (verifierat 2026-09-25, källorna 2026-09-30 och 2026-10-01)

**Artdatan** (`shared/content/species/*/*.yaml`, 839 arter):

- **180 arter har `review_status: approved`**, och alla är klassade `abundance: allmän`. Alla 180 har ett huvudfoto. De övriga 659 är `auto` (ogranskade) och `ovanlig`.
- **Säsongsdatan saknar signal.** Alla 839 arter står som `present` i alla tolv månader, och alla har samma fem länder under `regions`.
- **Beskrivningarna håller inte som webbtext.** De är Claude Haiku-sammanfattningar av Wikipedias ingress. Bland de 180: 57 svenska beskrivningar nämner inget utseendedrag, 22 arter har en tom beskrivning på minst ett språk, 162 börjar med en markdownrubrik, och flyttexterna har typiska AI-fraser.
- **`iucn_status` är IUCN:s globala rödlista** (Wikidata), inte den svenska. Fördelning bland alla 839: LC 697, NE 51, NT 46, VU 39, CR 6.
- **Fotona:** 533 foton för de 180 arterna, åtta olika fria licenser. Fältet `author` innehåller rå HTML från Commons i 178 av 180 filer.
- **Grupperna** finns i appen (`shared/content/src/jvmMain/resources/family_groups.yaml`, 15 grupper). Antal granskade arter per grupp: Tättingar 88, Änder & gäss 18, Vadare 14, Rovfåglar 10, Måsar & tärnor 9, Ugglor 8, Hönsfåglar 7, Hackspettar 5, Duvor 4, Tranor & rallar 4, Övriga 4, Alkor 3, Doppingar & lommar 3, Hägrar & storkar 2, Havsfåglar 1.
- **Appens modeller:** fotomodellen (`shared/ml/.../files/ml/aiy_to_qid.json`) täcker **122** av de 180, ljudmodellen (`birdnet_lite_to_qid.json`) **174**, och **6** arter finns i ingen av dem.

**Källorna** (provade 2026-09-30 och 2026-10-01):

- **Artportalen via GBIF:** datasetet Artportalen (SLU Artdatabanken) har licensen **CC0**. Talgoxe har cirka 2,1 miljoner rapporter i Sverige, varav 96 procent CC0. Månadsfacetter och länsfacetter (`GADM_LEVEL_1_GID`, 21 län) fungerar i sök-API:t utan nyckel. Ladusvalan syns tydligt april till september.
- **Svenska rödlistan 2025** finns på GBIF som checklista (dataset `87e639cc-30a9-4007-bd2c-b0cab60326b9`, **CC0**). Tofsvipa och hussvala kommer ut som `VULNERABLE`. Arter som inte är rödlistade finns inte i listan.
- **Inspelningar:** 120 av de 180 har en ljudfil via Wikidata (P51). Commonssökning hittar kandidater för 54 av de 60 övriga, men en del träffar är uttalsfiler (till exempel det tyska ordet för havsörn), så de måste filtreras bort. Xeno-canto har mest NonCommercial-licenser och används inte.
- **Tyska Wikipedia** har artiklar för 178 av de 180, ofta med utförligare avsnitt om kännetecken och läte än de svenska och engelska.

**Webben:** Astro 5 och Tailwind v4, statisk sajt, Vercel bygger `website/`. Fältbokens färger är live sedan 2026-09-28. `Layout.astro` sätter canonical och hreflang, men `alternateHref()` byter bara prefixet `/sv`. Sidfoten har "Byggd av AlbIT". Kontroller: `test:i18n`, `test:no-accuracy`, `test:no-dashes`, `test:contrast`, `test:palette` och Playwright. CI täcker inte webben.

**Sökläget:** se baslinjen 2026-09-30. Kort: 3 klick och 364 visningar på tre månader, bara varumärkesfrågor, en indexerad sida, cirka fem länkande domäner.

## 2. Beslut

| # | Fråga | Beslut |
|---|---|---|
| 1 | Språk | **Svenska och engelska från start.** |
| 2 | Omfång | **De 180 granskade arterna**, publicerade sida för sida i vågornas körordning (rad 15, ändrat 2026-10-05 (b)). |
| 3 | Texter | **Ny webbtext per art**, skriven ur ett kontrollerat faktablad (rad 10). Appens texter rörs inte. |
| 4 | Artsidans layout | **B "Uppslaget"**, utökad med nya moduler (avsnitt 5). |
| 5 | Navigering | Kategoriraden med sökfält, "Arter" först i menyn, kolumnen Arter och raden Vanliga arter i sidfoten. Ingen utfällbar meny. |
| 6 | Teknik | Pipelinesteget `web` skriver data, texter och media till `website/`. Vercel bygger bara `website/`. |
| 7 | Grupper | Appens 15 grupper, med egna sidor. |
| 8 | Publicering | Fas 2-koden byggs nu. Varje sida publiceras för sig så snart den är kontrollerad och godkänd (avsnitt 14, ändrat 2026-10-05 (b)). |
| 9 | Källor | **Wikipedia på svenska, engelska och tyska**, **Artportalen via GBIF** (CC0), **Svenska rödlistan 2025** (CC0), **inspelningar från Commons** (fria licenser). |
| 10 | Pipeline | **Faktablad först:** citerade fakta, kodkontroll av citaten, automatisk kontroll av varje faktum mot sina citat, skrivande bara ur godkända fakta, kontroll av varje mening med en andra modell. |
| 11 | Kontroll | **Automatisk kontroll plus undantagsgranskning** (ändrat 2026-10-05): en andra modell kontrollerar varje faktum, kod jämför siffror mellan artiklarna och förekomst mot Artportalen och rödlistan, Birdys egen ljudmodell kontrollerar inspelningen. Albin beslutar bara om flaggorna. Stickprovet (arter och jämförelser) tas efter publicering, inte i förväg (ändrat 2026-10-05 (b), avsnitt 14). Sidan visar "Kontrollerad mot källorna" med datum. |
| 12 | Förväxlingsarter | **Sektion på varje artsida plus egna jämförelsesidor** för de cirka 30 mest sökta paren. |
| 13 | Datamoduler | **Månadsdiagram och länskarta** ur Artportalen, **svensk rödlistestatus** i faktalistan. |
| 14 | Ljud | **En inspelning per art** när en fri inspelning finns, högst 20 sekunder. |
| 15 | Vågor | **Vågorna är en körordning, inte releasedatum** (ändrat 2026-10-05 (b)): våg 1 (de tolv vanliga arterna plus vinter- och trädgårdsfåglar) körs först, riktmärke klar i början av december, sedan våg 2 (övriga stannfåglar) i mitten av januari, sedan våg 3 (flyttfåglar) i slutet av februari. Varje art publiceras för sig så snart den är klar. |
| 16 | Öppenhet | **Sidan "Så gör vi artsidorna"** om källor, AI, kontroll, licenser och rättelser. |

**Saker specen bestämmer utöver mockupen** (ändra i granskningen om du vill något annat):

1. **Rödlisteraderna heter "Svenska rödlistan 2025" och "Global rödlista (IUCN)".**
2. **Ingångssidans rubrik är "Fåglar i Sverige och Europa".** Bland de 180 finns arter som inte förekommer i Sverige.
3. **Brödsmulorna hoppar över familjen** (Birdy › Arter › Tättingar › Talgoxe).
4. **Grupper med färre än tre publicerade arter får `noindex`** och ligger utanför sitemapen tills de växer.
5. **Inspelningen klipps till högst 20 sekunder** för att hålla nere storleken. ~~Creditraden säger att den är klippt.~~ **Ändrat 2026-10-06 (godkänt av Albin):** varje inspelning bearbetas (högst 20 sekunder, mono, utjämnad ljudnivå, MP3), så creditraden säger alltid att den är bearbetad: "bearbetad", eller "klippt och bearbetad" när den kortats.

## 3. Omfång

**Ingår:**

- Pipelinesteget `web`: källor, datamoduler, faktablad, automatisk kontroll, undantagsark, skrivande, kontroll, jämförelsetexter, nedskalade foton, klippta inspelningar och licensdata för de 180 arterna, samt ingresser för de 15 grupperna.
- Sidorna: 180 artsidor, 15 gruppsidor, en ingångssida, cirka 30 jämförelsesidor och sidan "Så gör vi artsidorna", på två språk (cirka 454 sidor).
- Kategorirad, sökfält, ändringar i meny, sidfot och startsidans uppslagsverkssektion.
- `Layout` får ett uttryckligt språkpar.
- SEO-reglerna som kod (`check-seo.mjs`) och nya tester.
- Publicering sida för sida, i vågornas körordning (ändrat 2026-10-05 (b)).
- Mätning: baslinje, UTM på Play-länkarna, filter för egna besök i Vercel Analytics, triggrar per våg.
- Utkast till utskick för länkar (Albin skickar).

**Ingår inte:**

- Månadssidor ("Fåglar att se i oktober"). De blir möjliga med rapportdatan men är ett eget senare projekt, som även kan rätta appens säsongsdata.
- De 659 ogranskade arterna.
- Utfällbar Arter-meny, familjesidor, App Store-länk.
- Ändringar i appen eller i `shared/content/species/*.yaml`.
- Lyssnare och utkast för sociala medier.

## 4. Adresser och sidtyper

| Sidtyp | Svenska | Engelska |
|---|---|---|
| Ingångssida | `/sv/arter/` | `/species/` |
| Grupp | `/sv/arter/ugglor/` | `/species/owls/` |
| Art | `/sv/arter/talgoxe/` | `/species/great-tit/` |
| Jämförelse | `/sv/arter/blames-eller-talgoxe/` | `/species/eurasian-blue-tit-vs-great-tit/` |
| Om sidorna | `/sv/arter/om-artsidorna/` | `/species/about-these-pages/` |

- **Alla sidtyper delar mapp.** En dynamisk route per språk (`src/pages/species/[slug].astro` och `src/pages/sv/arter/[slug].astro`) renderar art, grupp eller jämförelse. Om-sidan är en egen fil i samma mapp.
- **Slug-regler för arter:** artens namn på språket, gemener, `å ä` → `a`, `ö` → `o`, `é è` → `e`, `ü` → `u`, mellanslag och apostrofer → `-`, `&` → `och`/`and`, övriga tecken bort.
- **Slug-regler för jämförelser:** de två arternas slugs i bokstavsordning efter slug, sammanfogade med `-eller-` (SV) eller `-vs-` (EN). Varje par får exakt en sida per språk. Rubriken på sidan följer samma ordning. **EN-slugen är artens egen slug** ur `names.en` (t.ex. "Eurasian Blue Tit" → `eurasian-blue-tit`), inte ett förkortat visningsnamn -- exemplet i tabellen ovan rättat 2026-10-06 (Task 21-granskningen), som tidigare visade `blue-tit-vs-great-tit`.
- **Gruppernas slugs** (fasta):

  | Grupp | SV | EN |
  |---|---|---|
  | songbirds | `tattingar` | `songbirds` |
  | waterfowl | `ander-och-gass` | `ducks-and-geese` |
  | waders | `vadare` | `waders` |
  | gulls_terns | `masar-och-tarnor` | `gulls-and-terns` |
  | auks | `alkor` | `auks` |
  | seabirds | `havsfaglar` | `seabirds` |
  | grebes_divers | `doppingar-och-lommar` | `grebes-and-divers` |
  | herons_storks | `hagrar-och-storkar` | `herons-and-storks` |
  | raptors | `rovfaglar` | `birds-of-prey` |
  | owls | `ugglor` | `owls` |
  | gamebirds | `honsfaglar` | `gamebirds` |
  | doves | `duvor` | `doves-and-pigeons` |
  | woodpeckers | `hackspettar` | `woodpeckers` |
  | cranes_rails | `tranor-och-rallar` | `cranes-and-rails` |
  | other | `ovriga-faglar` | `other-birds` |

- **Unikhet:** ett test i bygget failar om två sidor på samma språk får samma slug (art, grupp, jämförelse eller om-sidan).
- **Språkparet** går alltid mellan samma art (QID), samma grupp, samma par eller om-sidorna.

## 5. Artsidan (layout B)

Utseendet följer fältbokens färger: inget grönt, espresso för mörka ytor, handskrivna accentord via `JournalHeadline`. `test:palette` gäller de nya modulerna.

**Dator (från 1024 px):** två spalter, 5 : 7, med en streckad hårlinje emellan.

- **Vänster spalt**, `position: sticky` under menyn och kategoriraden:
  1. Huvudfoto i planschram med bildtext i Caveat ("Pl. 1, Talgoxe" och "Foto: {fotograf}").
  2. Faktalista (etiketter i bilaga A):
     - Vetenskapligt namn (kursivt)
     - Familj
     - I Sverige: status, se avsnitt 9.5
     - Storlek
     - Svenska rödlistan 2025: kategorin i ord med koden inom parentes, eller "Inte rödlistad"
     - Global rödlista (IUCN): döljs när koden är NE

     "I Sverige", "Storlek" och "Svenska rödlistan 2025" döljs när uppgiften saknas.
  3. Appruta (espresso): rubrik "Osäker på vad du ser?" och en text som beror på vad appen klarar för just den arten (foto och läte, bara läte, bara foto, eller ingen av dem; bilaga A). Därunder Google Play-märket med UTM.
  4. Marginalanteckning i Caveat (`MarginNote`), bara där artfilen har `marginalia`.
- **Höger spalt:**
  1. Brödsmulor: Birdy › Arter › {Grupp} › {Art}.
  2. Kicker med familjen, h1 med artens namn och det vetenskapliga namnet i Caveat.
  3. Ingress (1 till 2 meningar).
  4. h2 "Så känner du igen den": 3 till 4 punkter.
  5. h2 "Läte": ett stycke och, när en inspelning finns, en spelare (`<audio controls preload="none">` i en ram i sajtens stil) med creditrad under.
  6. h2 "Var och när": ett stycke, därunder **månadsdiagrammet** ("När ses den i Sverige?", 12 staplar) och **länskartan** ("Var rapporteras den?", Sveriges 21 län i fyra nyanser). Båda är SVG som ritas när sajten byggs, utan diagrambibliotek, med bildtext om källan och en mening per diagram som kod skriver ur datan (avsnitt 9.2). Modulerna döljs när arten har för få rapporter.
  7. h2 "Föda och beteende": ett stycke, bara när faktabladet har fakta om föda eller beteende.
  8. Extrafoto i planschram ("Pl. 2"), bara om arten har ett.
  9. h2 "Kan förväxlas med": 1 till 3 arter, var och en med litet foto (om arten har sida), namn och 1 till 2 meningar om hur man skiljer dem åt. Namnet länkar till artens sida om den är publicerad. Finns en publicerad jämförelsesida för paret visas länken "Jämför {art} och {art}". Sektionen döljs när arten saknar förväxlingsarter.
  10. h2 "Fler {familj}": upp till fyra andra publicerade arter i samma familj, annars från samma grupp.
  11. Credits (avsnitt 10), raden "Kontrollerad mot källorna {datum}" med länk till "Så gör vi artsidorna", och raden "Hittade du ett fel? Skriv till oss" (mejllänk med ämnet "Fel på artsidan: {Art}").

**Mobil (under 1024 px):** en spalt i ordningen brödsmulor, kicker, h1, huvudfoto, faktalista, ingress, kännetecken, läte med spelare, var och när med diagram och karta, föda och beteende, extrafoto, förväxlingsarter, appruta, marginalanteckning, fler arter och credits. Ingen sticky. Ingen sidledsscroll i 360 till 430 px.

**Samma komponenter som 1.3-webben:** färgtokens, `plate`, kicker, knappar och kort. Inga nya typsnitt.

## 6. Ingångssidan, gruppsidorna och navigeringen

**Kategoriraden** (`CategoryBar.astro`) ligger under menyraden på ingångssidan, gruppsidorna, artsidorna och jämförelsesidorna, sticky under menyn.

- Chips: "Alla arter" plus de grupper som har minst en publicerad art, i appens ordning, med antal publicerade arter. Aktiv grupp är rostfärgad och har `aria-current`.
- Raden går att svepa i sidled, och den aktiva chipen scrollas in i bild.
- **Sökfältet** skickar `q` till ingångssidan (`GET`). På små skärmar blir det en sökikon.

**Ingångssidan:**

- Kicker "Uppslagsverket", h1 "Fåglar i Sverige och Europa", ingress (bilaga A).
- Sökfält som filtrerar A till Ö-listan medan man skriver (svenskt, engelskt och vetenskapligt namn, oberoende av skiftläge och diakritiska tecken). `?q=` fylls i. Utan JavaScript visas hela listan.
- Ett kort per grupp med foto, namn och antal publicerade arter. Fasta foton per grupp: Tättingar Koltrast (Q25234), Änder & gäss Gräsand (Q25348), Vadare Strandskata (Q25928), Måsar & tärnor Fiskmås (Q26427), Alkor Tordmule (Q27102), Havsfåglar Storskarv (Q25440), Doppingar & lommar Skäggdopping (Q25422), Hägrar & storkar Gråhäger (Q25273), Rovfåglar Ormvråk (Q25385), Ugglor Kattuggla (Q25756), Hönsfåglar Fasan (Q25432), Duvor Ringduva (Q26026), Hackspettar Större hackspett (Q26209), Tranor & rallar Trana (Q4764) och Övriga Gök (Q18845). Är fotoarten inte publicerad används gruppens första publicerade art i bokstavsordning.
- **Ny sektion "Lätta att blanda ihop"**: alla publicerade jämförelsesidor som en lista med båda namnen.
- A till Ö-lista med alla publicerade arter.
- En rad längst ned med länk till "Så gör vi artsidorna".

**Gruppsidorna:** kicker, h1 med gruppens namn, ingress på 2 till 3 meningar (skrivs i planen och granskas av Albin) och ett rutnät med gruppens publicerade arter. Tättingar delas upp med familjen som underrubrik.

**Menyn:** "Arter" (EN "Species") blir första länken och pekar på ingångssidan, med `aria-current` på alla sidor under arter.

**Sidfoten:**

- En ny kolumn **Arter** med de fem grupperna som har flest publicerade arter och "Alla arter från A till Ö".
- En ny rad **Vanliga arter** med tolv länkar: Talgoxe, Blåmes, Koltrast, Rödhake, Gråsparv, Skata, Kaja, Bofink, Gräsand, Fiskmås, Ormvråk och Trana. Alla tolv ingår i våg 1. Ett test failar om någon av dem saknar publicerad sida.
- "Byggd av AlbIT" står kvar.

**Startsidan:** sektionen Uppslagsverket får textlänken "Bläddra bland arterna".

## 7. Jämförelsesidorna

**Vilka par:** kandidaterna är alla förväxlingspar ur godkända faktablad där båda arterna har en godkänd sida. Sökvolymerna hämtas ur Google Ads sökordsplanerare (AlbIT:s konto, i Albins webbläsare):

- Sverige, svenska: "{a} eller {b}", "{b} eller {a}", "skillnad {a} {b}", "{a} {b} skillnad".
- Storbritannien, engelska: "{a} vs {b}", "{b} vs {a}", "difference between {a} and {b}".

Paren sorteras på summan av de svenska volymerna, med den engelska summan som skiljelinje. De 30 bästa får en sida. Ett par där båda summorna är noll får ingen sida. Volymerna sparas i `tools/content-pipeline/review/comparison-volumes.csv`.

**Rättat 2026-10-06 (Task 21-granskningen):** den engelska skiljelinjen är **det högsta enskilda engelska sökvärdet**, inte en summa (matchar R7 i fas 1b-planen). 78 av 180 arter har ett IOC-prefix (Eurasian/Common/Northern/Western/European) som sällan skrivs i en sökruta ("eurasian blue tit vs great tit" läser nära noll i planeraren), så `queries()` mäter upp till sex engelska fraser per par -- de tre ovan plus samma fraser utan prefixet när ett namn har ett -- och skiljelinjen är den högsta av alla uppmätta.

**Innehåll, uppifrån och ned:**

1. Brödsmulor: Birdy › Arter › {A} eller {B}?
2. Kicker "Lätta att blanda ihop", h1 "{A} eller *{B}*?" (EN "{A} or *{B}*?") och **det korta svaret**: det snabbaste sättet att skilja dem åt, 1 till 2 meningar.
3. Två kolumner (en spalt i mobilen): artens huvudfoto, namn med länk till artsidan, storlek, status i Sverige och inspelning med credit, för båda arterna.
4. h2 "Så skiljer du dem åt": en tabell med 3 till 5 rader (kännetecken, art A, art B).
5. h2 "När ses de?": båda arternas månadsprofiler i samma diagram, två färger ur paletten, med förklaring och en mening per art skriven av kod.
6. Appruta "Fortfarande osäker?" med Play-märket.
7. Credits för alla foton, inspelningar, data och båda arternas Wikipediaartiklar, plus raden "Kontrollerad mot källorna {datum}" (det senare av de två arternas kontrolldatum).

**Skrivs och kontrolleras** bara ur de två arternas godkända faktablad, med samma kontroller som artsidorna (avsnitt 9.7).

**Publiceras** för sig, en i taget, så snart båda arterna är publicerade och sidan har klarat samma automatiska kontroller (ändrat 2026-10-05 (b), avsnitt 14). Albin läser inte sidan i förväg; den ingår i stickprovet efter publicering, 1 av 10 jämförelsesidor.

## 8. Sidan "Så gör vi artsidorna"

En sida per språk som förklarar, i sajtens ton och utan tankstreck:

- **Källorna:** Wikipedia på tre språk, Artportalen via GBIF, Svenska rödlistan 2025, foton och inspelningar från Wikimedia Commons.
- **Hur AI används:** en modell tar ut fakta med citat ur artiklarna, kod kontrollerar citaten, en modell skriver texten bara ur godkända fakta, en annan modell kontrollerar varje mening. Diagram, karta och rödlistestatus kommer direkt ur datan, utan modell.
- **Kontrollen:** en andra modell kontrollerar varje faktum mot sina citat ur artiklarna, kod jämför siffror mellan artiklarna, förekomst jämförs med Artportalen och den svenska rödlistan, och inspelningen kontrolleras med Birdys egen ljudmodell. Det som inte går att avgöra automatiskt granskas av Albin Abrahamsson innan sidan publiceras. Efter publicering granskar han dessutom ett löpande stickprov av redan publicerade sidor (ändrat 2026-10-05 (b)). Datumet för den senaste kontrollen står på varje sida.
- **Licenserna:** texterna får delas under CC BY-SA 4.0, foton och inspelningar under sina egna licenser. Inspelningarna är bearbetade (tillägg 2026-10-06, godkänt av Albin): högst 20 sekunder, mono, utjämnad ljudnivå och MP3, och en bearbetad inspelning under CC BY-SA delas under samma licens som originalet. Foton visas oförändrade, bara nedskalade.
- **Rättelser:** mejladressen och att rättade sidor får nytt kontrolldatum.

Texten skrivs i planen och godkänns av Albin. Sidan länkas från alla artsidor, jämförelsesidor och ingångssidan. JSON-LD som blogginläggen: `author` Person Albin Abrahamsson, `publisher` AlbIT AB.

## 9. Pipelinen: steget `web`

Allt körs i `tools/content-pipeline` (Python med uv) med samma cache, `.env` och kostnadsspärr (`--max-cost`) som tidigare. Exakta modell-id, tankenivåer och kommandonamn bestäms i planen med `claude-api`-skillen. Kraven här gäller oavsett modell.

### 9.1 Källor

Allt cachas under `.cache/` med hämtningsdatum. Revisioner och adresser sparas i utdata.

- **Wikipedia** på svenska, engelska och tyska: hela artikeln i klartext på en fast revision. Saknas sitelinks följs P1403 som i fas 1. Saknas tyska artikeln körs arten utan den.
- **Artportalen via GBIF:**
  - Artens taxonnyckel via `species/match` med vetenskapligt namn. Bara `matchType: EXACT` och rang `SPECIES` godkänns. Annars får arten inga datamoduler och rapporten säger varför. Är namnet en synonym i GBIF (`status: SYNONYM`) används den accepterade nyckeln (`acceptedUsageKey`); **rättat 2026-10-07**, synonymens egen nyckel gav bara posterna under det gamla namnet (54 för fjällpipare, 303 för höksångare, en del av törnsångarens och ärtsångarens). En `FUZZY`-träff vars namn bara skiljer sig med en dubbelbokstav räknas som exakt (GBIF stavar grönsångaren Phylloscopus sibillatrix; utan regeln fick den varken diagram eller rödlista), om GBIF:s säkerhet är minst 95. Varje träff måste vara en fågel (`classKey` 212, Aves), och `acceptedUsageKey` används alltid när GBIF anger en.
  - Filter i alla anrop: `country=SE`, `year=2016,2025`, `license=CC0_1_0`, `occurrenceStatus=PRESENT`, `datasetKey=38b4c89f-584c-41bb-bd8f-cd1def33e92f` (Artportalen). **Tillagt 2026-10-07:** utan datasetfiltret räknades även Ringmärkningscentralens fångster (6,4 procent av alla svenska fågelposter, 49,5 procent av kungsfågelns) och Svensk fågeltaxerings rutter med; ringmärkningen samlas vid fågelstationerna på hösten och snedvred månadsdiagrammet, länskartan och statussignalen, och sidorna anger Artportalen som källa. Räkningarna cachas under ett nytt namn så att de gamla aldrig läses igen.
  - Facetter: månad (12) och `GADM_LEVEL_1_GID` (21 län), för arten och för alla fåglar (`taxonKey=212`) med samma filter.
  - En fast tabell i pipelinen översätter GADM-id till länskod (ISO 3166-2:SE) och länsnamn på svenska och engelska.
- **Svenska rödlistan 2025:** artens post i dataset `87e639cc-30a9-4007-bd2c-b0cab60326b9`, matchad på GBIF:s taxonnyckel (inte bara namnet). Kategorin översätts till `RE`, `CR`, `EN`, `VU`, `NT` eller `DD`. Finns arten inte i listan blir värdet `not_listed`.
- **Inspelning:**
  - Kandidater i ordning: filer i Wikidata P51, sedan Commonssökning `"{vetenskapligt namn}" filetype:audio` bland filer.
  - En fil godkänns bara om alla villkor gäller: den är en ljudfil; den ligger inte i en kategori för uttal (namnet innehåller "pronunciation" eller "Lingua Libre"); licensen finns i licenstabellen (avsnitt 10); längden är minst 3 sekunder; och en fil från sökningen (inte från Wikidata) har det vetenskapliga namnet i titeln eller i en kategori. Ett xeno-canto-nummer räcker inte ensamt (skärpt 2026-10-01: numret säger inte vilken art det är).
  - Första godkända kandidaten används. Den klipps till de första 20 sekunderna om den är längre, ljudnivån normaliseras och den sparas som mono-MP3 i 64 kbit/s till `website/src/assets/species/<QID>/voice.mp3` (cirka 160 kB), bredvid fotona. Filer i `website/public/` serveras direkt, även innan en sida med credit finns, så inspelningarna ligger inte där. Omkodningen görs med ffmpeg ur paketet `imageio-ffmpeg` (installeras via uv, har MP3-kodare och `loudnorm`).
- **Appens modeller:** `identifiable.photo` är sant om QID finns i `aiy_to_qid.json`, `identifiable.sound` om det finns under `mapping` i `birdnet_lite_to_qid.json`.

### 9.2 Datamodulerna

Räknas av kod, aldrig av en modell.

- **Månadsandel:** för varje månad är andelen artens rapporter delat med alla fågelrapporter samma månad. Diagrammet visar andelen skalad så att den högsta månaden blir 100, avrundat till heltal. Det justerar för att fler är ute och rapporterar i maj.
- **Länsandel:** samma sak per län. Kartan visar fyra nyanser: inga rapporter, 1 till 33, 34 till 66 och 67 till 100 procent av det högsta länet.
- **För lite data:** har arten färre än 200 rapporter totalt i perioden visas varken diagram eller karta. **Tillagt 2026-10-07** (provkörningen R3: koboltmesens sida sa aldrig att fågeln inte finns i Sverige): datan får då i stället en mening om förekomsten, "Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025." när arten har en exakt GBIF-träff men inga rapporter, annars "Sällsynt i Sverige: {n} rapporter i Artportalen 2016 till 2025." Den blir ett datafaktum som texten kan använda (skribenten ska säga det under Var och när), och utan rapporter visar raden "I Sverige" "Förekommer inte" med datafaktumet som källa när ingen artikel ger en status. En status som säger att arten finns här (stannfågel, flyttfågel, ses under flyttningen, vintergäst) med noll rapporter blir en V3-flagga.
- **Meningar ur datan** (mallar i bilaga A, svenska och engelska):
  - Månader med värde 80 eller mer är "mest", månader med 10 eller mindre är "sällan". Har alla månader 30 eller mer blir meningen "Rapporteras året runt." Annars "Rapporteras mest i {månader}." och, om det finns sådana månader, "Rapporteras sällan i {månader}." Månader i följd skrivs som spann ("december till februari"). **Ändrat 2026-10-07** (var "Nästan aldrig i {månader}.", som lästes som att fågeln inte finns då; pilgrimsfalken och sparvhöken fick "Nästan aldrig i juni" eftersom häckningsfynden är skyddade och ringmärkningen inte längre räknas): meningarna handlar om rapporter, inte om förekomst, och skribentens och kontrollens prompter säger det. För en stannfågel eller en flyttfågel som häckar här får skribenten "sällan"-meningen utan maj till juli (eller ingen alls, om bara de månaderna var låga); sidans mening under diagrammet är oförändrad.
  - Länen (**ändrat 2026-10-07**, var "Vanligast i rapporterna från {län}, {län} och {län}." med de tre högsta länen, som lästes som "flest rapporter" och för spridda arter pekade ut tre norrlandslän för både talgoxe och bofink: norrlandslänen rapporterar färre arter, så en vanlig fågelbordsart blir en större andel av deras rapporter): har minst hälften av länen minst halva det högsta länets andel är fördelningen jämn och meningen blir "Rapporteras från alla 21 län." (eller "från {n} av 21 län."). Annars "Andelen av alla fågelrapporter är högst i {län}, {län} och {län}." med de län som har minst halva det högsta länets andel, högst tre. Datafaktumet för andelsmeningen har `kind: "countyShare"`, och en mening i texten som anger det måste säga "andel" (EN "share"), annars stryks den (koden), så att andelen aldrig blir "flest rapporter".
- **Statussignal:** datan jämförs med statusen som texten anger (avsnitt 9.5). Bara tydliga motsägelser flaggas, till exempel `resident` med en månad under 5, `breeding_migrant` med ett snitt för december till februari över 25, `winter_visitor` med ett snitt för juni och juli över 25, eller `absent` med 200 rapporter eller fler. Trösklarna kalibreras i planen mot minst fyra kända arter bland de 180, en per status, med tester.
- Rådata (antal per månad och län, för arten och för alla fåglar) och hämtningsdatum sparas i artens JSON så att siffrorna går att kontrollera.

### 9.3 Faktabladet

- **Prompt:** `prompts/facts-v1.md`. Underlag: de tre artiklarna, namn och familj. Modell i Opus-klass med hög tankenivå.
- **Status i Sverige** tas också fram här, inte av skribenten (utom när ingen artikel ger en och datan saknar rapporter, se "För lite data" i 9.2): modellen anger en av `resident`, `breeding_migrant`, `passage`, `winter_visitor`, `rare_visitor` eller `absent` med citat, och den sparas som faktumet `s01` (`topic: "status"`). Statussignalen (9.2) jämförs med den direkt, så att en motsägelse blir en flagga i undantagsarket (9.4).
- **Ett faktum** har:
  - `id` (`f01`, `f02` …)
  - `topic`: en av `appearance`, `sex_age`, `size`, `voice`, `habitat`, `sweden`, `breeding`, `food`, `behaviour`, `lookalike`
  - `sv`: faktumet på svenska, en mening, högst 30 ord, utan värderingar
  - `sources`: minst en källa, var och en med `article` (`sv`, `en` eller `de`) och `quote` (minst 20 tecken, ordagrant ur artikeln)
  - för `lookalike` även `other`: den andra artens vetenskapliga namn (Birdys binomen när arten matchas, se nedan, med artikelns skrivsätt i `written` när det skiljer sig) och vad som skiljer dem åt
- **Antal:** 10 till 30 fakta per art, varav minst ett vardera om utseende, läte och miljö. **Tillagt 2026-10-07:** ger modellen fler än 30 tar koden ämnena i tur och ordning (det första faktumet i varje ämne, sedan det andra och så vidare, de obligatoriska ämnena först i varje varv) och behåller modellens ordning; varje struket faktum står i rapporten. Förut behölls de 30 första, och Opus 5 förlorade då alla fakta om föda, beteende och förväxlingar för talgoxen.
- **Kontroller i kod:** varje citat måste finnas ordagrant i den angivna artikeln efter samma normalisering som i fas 1 (blanksteg, skiftläge, citattecken, streck). Fakta utan giltigt citat stryks och loggas. `other` slås upp bland de 839 arterna och får QID om arten finns (**ändrat 2026-10-07**, `scinames.py`): ett binomen i listan gäller som det står; en förkortad genus ("C. corone") som passar sidans egen art betyder först det släktet, och finns arten inte där är andra släkten med samma början en gissning; en underart är sin art, eller den art den sedan dess har blivit ("Corvus corone cornix" är gråkråka), alltid i det skrivna släktet; listan har inga synonymer, så ett äldre släkte ("Parus caeruleus") matchas på artepitetet inom sidartens familj, men aldrig för en underart. En gissning (förkortning för ett annat släkte eller familjeregeln) godtas bara när faktumet eller dess citat nämner arten på svenska eller engelska ("Eurasian", "Common" eller "European" får utelämnas ur det engelska namnet bara när texten har ordet ändå). En förväxlingsart som är sidans egen art (en underart eller ett äldre namn) stryks som faktum, så att sidan aldrig säger "Kan förväxlas med" sig själv. Annars får faktumet inget QID och rapporten säger varför. Matchningen sker på vetenskapligt namn, aldrig på svenskt namn ensamt. Saknas ett obligatoriskt ämne efter kontrollen görs ett nytt försök med felen. Failar det igen får arten `status: "failed"`.
- **Datafakta** läggs till av koden (`d01` …, `topic: "data"`, källa `artportalen` eller `rodlistan`): meningarna om månader och län, rödlistekategorin, och en flagga om statussignalen motsäger fakta om förekomst i Sverige.

### 9.4 Automatisk kontroll och undantagsgranskning

Ersätter Albins manuella granskning av varje faktablad (Revision 2026-10-05). En automatisk kontroll (`birdy-fetcher web verify`) körs på alla 180 arters faktablad direkt efter faktabladen, utan Albin. Bara det kontrollen inte kan avgöra går vidare till Albin, i den ordning arterna körs (vågornas körordning, ändrat 2026-10-05 (b)).

- **V1, faktakontroll mot citaten:** en annan modell än den som skrev faktabladet (Sonnet-klass, mot skribentens Opus-klass) får varje faktum med alla sina citat (med artikelns språk) och det omgivande stycket till vart och ett ur den cachade artikeln (**ändrat 2026-10-07**: förut bara det första citatet, så en status som stöddes av sitt andra citat ströks för talgoxe, bofink och råka; citaten bedöms tillsammans), och svarar `supported`, `partial` eller `unsupported` med en kort motivering, som JSON. Fakta med `partial` eller `unsupported` stryks automatiskt och loggas i rapporten. Blir ett obligatoriskt ämne (utseende, läte, miljö) tomt efter detta körs det befintliga omförsöket (9.3) med felen; failar det igen får arten `status: "failed"`.
- **V2, siffror mellan artiklarna** (kod, ingen modell): för fakta med tal (storlek, vikt, vingbredd, kullstorlek och liknande) tar koden ut tal och enhet och jämför med de andra artiklarna (sv/en/de) som anger samma mått. Intervallen måste överlappa (toleransen ges i planen, med tester). Skiljer de sig åt blir det en flagga.
- **V3, förekomst** (kod): den befintliga statussignalen mot Artportalens data (9.2) kompletteras med en kontroll mot Svenska rödlistan 2025: status `absent` eller `rare_visitor` för en art som har en bedömd rödlistekategori (inte NA/NE) ger en flagga.
- **V4, inspelningen:** Birdys egen BirdNET-modell körs på den klippta inspelningen (skrivbordsreferensen finns i `tools/ml-eval/flexref/`; planen anropar den, till exempel via ett litet script som pipelinen kör med `uv run --project ...` och som svarar med JSON top-3 per 3-sekundersfönster). Arten måste ligga i top-3 med minst 0,10 i konfidens i minst ett fönster, annars tas inspelningen bort automatiskt (sidan visar ingen inspelning). Arter som ljudmodellen inte täcker (174 av 180, avsnitt 1) ger i stället en flagga så att Albin kan lyssna. **Ändrat 2026-10-07** (efter provkörningen R3 och en mätning på alla 152 inspelningar modellen täcker): är arten modellens första gissning i minst två fönster med minst 0,02 i konfidens, men når inte 0,10, är det svagt stöd och blir en flagga ("lyssna och besluta") i stället för en strykning. Når inspelningen inte 0,10 prövar V4 först upp till tre andra tillåtna inspelningar av arten från Commons, i källstegets ordning; den första som modellen behåller ersätter inspelningen (med sin egen credit), annars står flaggan eller strykningen. Varje inspelning som ströks eller prövades förgäves sparas i `review.audioStruckSources` och väljs aldrig igen, varken av V4 eller av `web sources`; med listan letar `web sources` ändå efter inspelningar som laddats upp sedan dess (bara en äldre post med `review.audioStruck` utan listan hoppar över ljudsteget helt).
- **Undantagsarket** (ändrat 2026-10-05 (b), var tidigare ett ark per våg): ett löpande Google-ark i Albins Drive, skapat av agenten. Kolumnerna står i bilaga E. Arket innehåller bara flaggorna från V2 till V4 (V3 omfattar både den befintliga statussignalen mot Artportalen och den nya rödlistekontrollen), en rad per art och flagga i den ordning arterna körs. Arten väntar till Albin har beslutat; de andra arterna fortsätter utan att vänta på den.
- **Beslut:** `behåll` (standard), `stryk` eller `ändra` (Albin skriver den nya texten i faktumkolumnen). Flaggor kräver ett beslut.
- **Import:** rader med beslut importeras löpande (`birdy-fetcher web import`, körs inte våg för våg längre). Strukna fakta tas bort, ändrade fakta ersätts och märks `edited: true` (citatet behålls som källa; ändringen är Albins ansvar), strukna inspelningar tas bort. Artens fält `review.facts = { by, at }` ersätts av `verification = { method: "auto", at: "<datum>", model: "<modell-id>", spotChecked: false }`.
- **Ingen text skrivs** för en art utan `verification`, eller med en öppen flagga.
- **Stickprovet** är inte längre en del av undantagsarket. Det tas efter publicering (ändrat 2026-10-05 (b), se avsnitt 14 och bilaga E); `verification.spotChecked` sätts till sant då, inte vid importen.

### 9.5 Skrivandet

- **Prompt:** `prompts/web-v2.md`. Underlag: bara de godkända fakta och datafakta, namn, familj och grupp. Modellen ser inte artiklarna. `identifiable` är INTE underlag för skrivandet (fix 2026-10-06): appens kännetecken-ruta är sajtens egen UI-text om vad appen klarar, inte något modellen ska skriva om. Modell i Opus-klass med hög tankenivå.
- **Fält per språk** (svenska och engelska, var för sig naturligt skrivna):

  | Fält | Innehåll | Gräns |
  |---|---|---|
  | `lead` | ingress | 1 till 2 meningar, högst 45 ord |
  | `fieldMarks` | kännetecken | 3 till 4 punkter, högst 16 ord var |
  | `voice` | läte | högst 60 ord |
  | `whereWhen` | var och när i Sverige | högst 70 ord |
  | `behaviour` | föda och beteende | högst 70 ord, kan saknas |
  | `lookAlikes` | 0 till 3 poster med `other` och text | högst 35 ord per post |
  | `metaDescription` | för sökresultatet | 120 till 155 tecken |
  | `facts.size` | "Cirka 14 cm" eller "13 till 15 cm" | med fakta-id, eller `null` |
  | `facts.swedenStatus` | fylls av koden ur det kontrollerade statusfaktumet `s01`, samma på båda språken | `{ value, factIds: ["s01"] }`, eller `null` |

- **Varje mening och punkt** sparas som `{ "text": "…", "factIds": ["f03", "d01"] }`. Sajten fogar ihop meningarna.
- **Skrivregler** som i fas 1: kort och konkret, inga streck, inga utropstecken, ingen första person, inga frågor till läsaren, inga förbjudna fraser (bilaga B), nämn inte Birdy, appar, foton eller Wikipedia.

### 9.6 Kontrollen

**I kod, innan något sparas:**

1. Inga streck (U+2014, U+2013 med mellanslag runt, `--`).
2. Inga förbjudna fraser.
3. Längder och antal enligt tabellen, inga utropstecken, ingen första person.
4. Varje mening har minst ett fakta-id, och alla id finns bland artens godkända fakta eller datafakta.
5. Varje tal i en mening (siffror) finns i texten eller citatet hos något av de fakta meningen anger.
6. Varje post i `lookAlikes` motsvarar ett godkänt `lookalike`-faktum.
7. `facts.swedenStatus` får inte motsäga statussignalen, om inte Albin har behållit statusfaktumet trots flaggan. Annars sätts den till `null`.

**Med en andra modell:** en annan modell än skribentens, i ett nytt sammanhang, med prompten `prompts/check-v1.md`. Den får en mening i taget tillsammans med de fakta meningen anger och svarar om meningen helt stöds, och annars vad som inte stöds.

**Om något failar:** fältet skrivs om en gång med felen som underlag och kontrolleras igen. Meningar som fortfarande inte klarar sig tas bort. Blir ett obligatoriskt fält tomt eller för kort (`lead`, minst 3 `fieldMarks`, `voice`, `whereWhen`, `metaDescription`) får arten `status: "failed"` och ingen sida. Texten som inte klarade sig sparas under `rejectedText`.

### 9.7 Jämförelsetexterna

- **Prompt:** `prompts/compare-v1.md`. Underlag: båda arternas godkända fakta och datafakta.
- **Fält per språk:** `shortAnswer` (1 till 2 meningar, högst 45 ord), `rows` (3 till 5 rader med `feature`, `a` och `b`, högst 14 ord per cell, där `a` anger fakta från art A och `b` från art B) och `metaDescription` (120 till 155 tecken).
- **Samma kontroller** som i 9.6, inklusive den andra modellen. Har paret färre än 3 giltiga rader efter kontrollen blir det ingen sida.

### 9.8 Körning, kostnad och rapport

- Stegen går att köra var för sig och per art: källor, faktablad, kontroll, undantagsark, import, skrivande, publicering, jämförelser, i vågornas körordning (ändrat 2026-10-05 (b), inte våg för våg som ett eget steg längre). Exakta kommandon står i planen.
- **Provkörning först:** samma fyra arter som i fas 1 (talgoxe Q25485, Q25383, Q25386 och Q10546857) med `--max-cost 5`. Kostnaden per art räknas om och Albin godkänner modell och tankenivå innan resten körs.
- **Uppskattning (räknad 2026-10-01 med Opus 5 för faktablad och text och Sonnet 5 för kontrollen):** cirka 145 USD för 180 arter och 30 jämförelser, med marginal för omförsök. **Tillägg 2026-10-05:** den automatiska faktakontrollen (V1) lägger uppskattningsvis 10 till 20 USD till (uppskattning, provkörningen avgör). Albin fyller på 200 USD, vilket täcker båda. Provkörningen avgör.
- **Rapport per körning** i `tools/content-pipeline/reports/`: kostnad, arter per status, strukna fakta och citat som inte hittades, borttagna meningar, flaggor, arter utan datamoduler eller inspelning.

### 9.9 Utdata

- Artfiler `website/src/data/species/<QID>.json` (bilaga C), jämförelsefiler `website/src/data/comparisons/<QID-A>_<QID-B>.json` med QID i bokstavsordning (bilaga D), foton och inspelningar i `website/src/assets/species/<QID>/`.
- Pipelinen skriver aldrig över kontrollerade fakta (`verification` satt) utan `--force`. Texten skrivs bara om när faktabladet har ändrats.

## 10. Media, data och licenser

1. **Texten** behandlas som en bearbetning av Wikipedia och delas under **CC BY-SA 4.0**. Varje sida anger de artiklar som använts (svenska, engelska och tyska, länkade till revisionen) och att texten bygger på dem. Repots LICENSE är proprietär, så mapparna `website/src/data/species/` och `website/src/data/comparisons/` får varsin `LICENSE.md` som säger att texterna där är CC BY-SA 4.0, och rotens LICENSE får en rad om undantaget.
2. **Rapportdata och rödlista** hämtas bara med licensen CC0. Inga villkor följer med, men källan anges ändå: "Artportalen (SLU Artdatabanken) via GBIF.org" och "Rödlistade arter i Sverige 2025 (SLU Artdatabanken)".
3. **Foton och inspelningar** används bara med licenser ur en fast tabell: CC0, public domain, CC BY 2.0, 3.0 och 4.0, CC BY-SA 2.0, 3.0 och 4.0. En creditrad per fil med upphovsperson (HTML tvättad), licens (länkad) och källsida på Commons. Foton visas oförändrade (bara nedskalade). ~~En klippt inspelning har "klippt" i creditraden och samma licens som originalet.~~ **Ändrat 2026-10-06 (godkänt av Albin):** varje inspelning är bearbetad (högst 20 sekunder, mono, utjämnad ljudnivå, MP3) och har "bearbetad" i creditraden, "klippt och bearbetad" när den kortats, och samma licens som originalet. En inspelning under CC BY eller CC BY-SA utan upphovsperson används inte.
4. **Används aldrig:** xeno-cantos NonCommercial-inspelningar, Artdatabankens egna arttexter och text ur fälthandböcker.
5. **Bygget failar** om ett foto, en inspelning, en artikel eller en datakälla som visas på en sida saknar sin creditrad (`check-seo.mjs`).

**Alt-text:** "Talgoxe (Parus major)" för huvudfotot, "Talgoxe, ytterligare foto" för extrafotot, motsvarande på engelska. Diagram och karta har sina meningar ur datan som textalternativ.

## 11. Strukturerad data

- **Artsida:** `BreadcrumbList` som matchar brödsmulorna, plus `WebPage` med:
  - `about`: `Taxon` med `name`, `alternateName`, `taxonRank: species` och `sameAs` till Wikidata
  - `primaryImageOfPage`: `ImageObject` med `contentUrl`, `license`, `acquireLicensePage`, `creator` och `creditText`
  - `associatedMedia`: `AudioObject` med samma licensfält, när en inspelning visas
  - `lastReviewed`: kontrolldatumet (`verification.at`), samma datum som raden på sidan. Ingen `reviewedBy` (borttagen 2026-10-05, kontrollen är inte längre en enskild granskare).
- **Jämförelsesida:** `BreadcrumbList` plus `WebPage` med `about` som lista över båda `Taxon` och `lastReviewed`.
- **Gruppsida och ingångssida:** `BreadcrumbList` plus `CollectionPage` med `ItemList`.
- **Om-sidan:** `WebPage` med `author` och `publisher` som blogginläggen.
- Inget i JSON-LD får säga mer än sidan visar. `check-seo.mjs` kontrollerar namn, brödsmulor, antal och datum.

## 12. SEO-reglerna som kod

**Titlar** (40 till 60 tecken, kontrolleras):

| Sidtyp | Mall | Reservmall om mallen blir över 60 |
|---|---|---|
| Art SV | `{Namn}: kännetecken, läte och foton \| Birdy` | `{Namn}: kännetecken och läte \| Birdy` |
| Art EN | `{Name}: identification, song and photos \| Birdy` | `{Name}: identification \| Birdy` |
| Jämförelse SV | `{A} eller {B}? Så skiljer du dem åt \| Birdy` | `{A} eller {B}? \| Birdy` |
| Jämförelse EN | `{A} vs {B}: how to tell them apart \| Birdy` | `{A} vs {B} \| Birdy` |
| Grupp SV | `{Grupp}: {n} arter med foton och kännetecken \| Birdy` | `{Grupp}: arter och kännetecken \| Birdy` |
| Grupp EN | `{Group}: {n} species with photos and ID tips \| Birdy` | `{Group}: species and ID tips \| Birdy` |
| Ingång SV | `Fåglar i Sverige och Europa: {n} arter med foton \| Birdy` | |
| Ingång EN | `Birds of Sweden and Europe: {n} species with photos \| Birdy` | |
| Om SV | `Så gör vi artsidorna: källor och granskning \| Birdy` | |
| Om EN | `How we make the species pages: sources and review \| Birdy` | |

En reservmall för jämförelser som blir under 40 tecken godtas (korta artnamn). Vid `n = 1` står det "1 art" respektive "1 species".

**Meta description:** ur pipelinen för arter och jämförelser, mallar i bilaga A för grupper, ingång och om-sidan. Alltid 120 till 155 tecken.

**`scripts/check-seo.mjs`** körs på `dist/` via `npm run check` (build, `check-seo`, `test:i18n`, `test:no-dashes`, `test:palette`). För sidorna under `/species/` och `/sv/arter/`:

1. Titel 40 till 60 tecken (undantaget ovan), unik.
2. Meta description 120 till 155 tecken, unik.
3. Exakt en h1, rubrikerna hoppar inte nivåer.
4. Canonical pekar på sidan själv, hreflang åt båda hållen till sidor som finns, x-default på den engelska.
5. Sidan finns i sitemapen med `lastmod` om den inte har `noindex`; sidor med `noindex` finns inte där.
6. Alla `<img>` har `alt`, `width` och `height`. Alla diagram har sin mening som text.
7. JSON-LD går att tolka och matchar sidan (brödsmulor, antal, datum).
8. Varje foto och inspelning som visas har en creditrad; sidor med diagram eller karta har datakällan; sidan har Wikipediaraden med alla artiklar som använts.

För alla sidor: inga interna länkar till sidor som saknas, exakt en h1, `alt` på alla bilder.

**`check-no-dashes.mjs`** utökas till `src/data/species/*.json`, `src/data/comparisons/*.json` och `src/data/species-groups.json`. **`check-i18n-parity.mjs`** täcker de nya nycklarna.

## 13. Tester och QA

**Python** (`uv run pytest`):

- Månads- och länsandelar, skalning, nyanser och gränsen för för lite data, på fasta testdata.
- Meningarna ur datan (spann, "året runt", tre län) på svenska och engelska.
- Statussignalen mot kalibreringsarterna.
- Rödlistans översättning och `not_listed`.
- Ljudfiltret: uttalsfiler och licenser utanför tabellen avvisas, xeno-canto-import godkänns.
- Citatkontrollen för fakta på tre språk, obligatoriska ämnen, `other`-uppslag.
- Faktakontrollen (V1) med en låtsasmodell: `supported`/`partial`/`unsupported`, strukna fakta loggas, omförsök vid tomt obligatoriskt ämne.
- Siffermatchningen (V2): tal och enhet ur fakta, överlapp mellan artiklarna, toleransen, flagga vid motsägelse.
- Rödlistekontrollen (V3): flagga när statusen är `absent` eller `rare_visitor` men arten har en bedömd rödlistekategori.
- Ljudmodellkontrollen (V4) med en fixturinspelning: top-3 och tröskeln 0,10, borttagen inspelning under tröskeln, flagga för arter modellen inte täcker.
- Stickprovsdragningen: samma frö ger samma två arter, en ny dragning vid ett missat fel.
- Undantagsarkets import: behåll, stryk, ändra, inspelning struken, flagga utan beslut stoppar importen.
- Fakta-id- och talkontrollen, `lookAlikes` mot fakta, statusregeln.
- Kontrollflödet med en låtsasmodell: omskrivning en gång, borttagna meningar, `failed` när obligatoriska fält blir för korta.
- Jämförelser: urval ur volymfilen, slug-ordning, minst tre rader.
- Att kontrollerade fakta inte skrivs över utan `--force`.

**Webben:**

- Bygget failar på schemafel (zod) och slug-krockar.
- Playwright (`tests/species.spec.ts` och `tests/comparisons.spec.ts`), SV och EN:
  - ingångssidan: sökningen filtrerar, `?q=` fylls i, hela listan utan JavaScript, sektionen "Lätta att blanda ihop"
  - artsidan: diagrammet har 12 staplar och sin mening, kartan har 21 län, ljudspelaren har `preload="none"` och creditrad, förväxlingslänkar leder till publicerade sidor, sticky på 1440 px, ingen sidledsscroll i 360, 390 och 430 px, språkbytet leder till samma art
  - jämförelsesidan: tabellen har 3 till 5 rader, båda artsidorna är länkade, diagrammet har båda arterna
  - publiceringen: en art med `publish: false` får ingen sida i produktionsbygget men finns med `noindex` i förhandsbygget
  - sidfoten: Arter-kolumnen och Vanliga arter finns, alla länkar ger 200
- Skärmdumpar SV och EN i 390 och 1440 px av ingångssidan, Ugglor, tre artsidor (Talgoxe, en med marginalanteckning, en utan extrafoto eller inspelning), en jämförelsesida och om-sidan.
- **Lighthouse** i mobil på Talgoxe, en jämförelsesida och ingångssidan: 90 eller mer på alla fyra.

## 14. Publicering sida för sida (ändrat 2026-10-05 (b), var tidigare publicering i vågor)

- **Fältet `publish`** i artens och jämförelsens JSON avgör. Produktionsbygget gör sidor bara för `publish: true`. Fältet sätts bara av pipelinen (`birdy-fetcher web publish`), aldrig för hand. Förhandsbygget på Vercel (`SPECIES_PREVIEW=1` i miljön Preview) gör även sidor för arter med `verification` och `status: "ok"`, med `noindex` och en banderoll "Förhandsvisning"; det används under utveckling av koden, inte som ett väntrum inför en gemensam releasehändelse.
- Gruppsidor, ingångssidan, kategoriraden, sidfoten, "Fler {familj}" och förväxlingslänkar räknar bara publicerade sidor och uppdateras automatiskt vid varje push. Sitemapen har bara publicerade sidor. Ingångssidan går live med den första publicerade arten, en gruppsida när den har minst tre publicerade arter (beslut 4 i avsnitt 2), "Så gör vi artsidorna" med den första artsidan.

**Vågorna är nu en körordning, inte releasedatum.** Pipelinen går igenom arterna i den här ordningen:

| Våg | Innehåll | Riktmärke klart | Varför |
|---|---|---|---|
| 1 | De tolv vanliga arterna plus de cirka 28 arter som har flest rapporter december till februari (antal, inte andel, så att de vanligaste vinterfåglarna kommer först), deras jämförelsesidor | 4 december 2026 | Indexerade till Vinterfåglar inpå knuten (29 till 31 januari 2027) |
| 2 | Övriga stannfåglar och vanliga arter | 15 januari 2027 | Vintersäsongen |
| 3 | Flyttfåglar | 26 februari 2027 | Före vårens sökningar i april och maj |

Listorna tas fram ur datan (`birdy-fetcher web waves`) och Albin justerar dem i chatten innan körningen börjar. Ingen av arterna väntar på att hela sin våg blir klar.

**Per art, helt automatiskt:**

1. Källor → faktablad → automatisk kontroll (V1 till V4, avsnitt 9.4).
2. Ingen öppen flagga → skrivande → meningskontroll (avsnitt 9.5, 9.6) → pipelinen sätter `publish: true`.
3. Lokalt: bygge, `check-seo.mjs` och Playwright/axe-testerna för just den sidan.
4. En commit för arten (dess JSON, foton, inspelning och licensdata), till exempel `data(artsidor): talgoxe (Q25485)`. Push till `main`. Vercel bygger och publicerar.
5. En öppen flagga → arten hoppas över och väntar i undantagsarket (avsnitt 9.4); fortsätter samma väg så snart Albin har beslutat. De andra arterna väntar inte på den.

**Jämförelsesidor** går samma väg, en i taget, så snart båda arterna är publicerade (avsnitt 7).

**Takt:** högst en push var cirka 5 minuter, så att varje Vercel-bygge hinner klart (cirka 230 pushar totalt: cirka 180 arter, cirka 30 jämförelser, 15 grupper, ingångssidan och om-sidan). Kräver att Vercel-projektets plan (teamet `loop-lead-ab`) tillåter det: Hobby ger 100 bygg om dagen, Pro betydligt fler. Räcker inte Hobby till sprids körningen över flera dagar, vilket är okej.

**Stoppvillkor:** en misslyckad kontroll för en sida blockerar inte de andra, den rapporteras. En nödstopp (`--max-publish N`, och stopp vid N publiceringar i rad som failar) så att ett systematiskt fel inte publicerar många dåliga sidor.

**Stickprov efter publicering** (ersätter det tidigare stickprovet i undantagsarket): för var 40:e publicerade art dras 2 (sparat frö, fröt står i rapporten) och läggs i Albins ark med hela faktabladet. Ett bekräftat fel rättas, sidan publiceras om med nytt datum på "Kontrollerad mot källorna", och missen loggas i rapporten; då dras två nya. Jämförelsesidorna har sitt eget stickprov på samma sätt, 1 av 10 publicerade jämförelsesidor.

**Återkallning:** att dra tillbaka en sida är `publish: false` och push. Sidan och dess post i sitemapen försvinner vid nästa bygge.

Faktabladen tas fram och kontrolleras automatiskt (V1 till V4) för alla 180 arter direkt efter provkörningen, i vågornas ordning. Undantagsarket, Albins beslut, skrivandet och publiceringen sker löpande, art för art.

## 15. Mätning, triggrar och länkar

- **Baslinje:** `docs/superpowers/research/2026-09-30-artsidor-baslinje.md`. Siffrorna tas om samma dag som våg 1:s sista art publiceras (ändrat 2026-10-05 (b), det finns ingen gemensam sammanslagning längre).
- **UTM på Play-länken** i approtorna: `https://play.google.com/store/apps/details?id=se.birdy.android&referrer=utm_source%3Dbirdy.community%26utm_medium%3Dspecies%26utm_campaign%3D{slug}`. Ingen spårning i appen.
- **Egna besök** filtreras bort i Vercel Analytics: en flagga i webbläsarens lagring (satt via en adress som planen anger) gör att besöket inte skickas. Albin sätter flaggan på sina enheter.
- **Triggrar per våg:**
  1. Efter cirka 6 veckor ska minst hälften av vågens sidor vara indexerade och ha fått visningar.
  2. Efter 12 veckor utan visningar att tala om utreds indexering, titlar och innehåll innan nästa våg.
  3. Mätvärdena skrivs in i baslinjefilen efter 6 och 12 veckor och blir underlag för AlbIT-caset.
- **Länkar efter våg 1:** agenten skriver utkast, Albin skickar i eget namn. Mottagare: en tråd på birdforum.net (skickar redan besökare), lokala ornitologiska föreningar och BirdLife Sveriges vinterräkning, svenska fågelgrupper på Facebook, lärare och naturskolor (jämförelsesidorna som gratis undervisningsmaterial) och AlbIT-caset. Utkasten sparas i `docs/marketing/2026-artsidor-utskick.md`.

### 15.1 Sociala medier (beslut 2026-10-06, planeras när publiceringen startar)

**Påminnelse:** när de första artsidorna publiceras på birdy.community startar Birdy ett dagligt inlägg på Facebook och Instagram (TikTok senare) om en publicerad artsida. Planeras i detalj först då; inget byggs före fas 2:s publiceringsloop.

- **Ett inlägg per dag**, samma mall för alla arter: artens foto, svenskt och engelskt namn, ett eller två kontrollerade fakta ur artposten, fotografens kredit och länk till sidan. Albin väljer mallen bland 2 till 3 förslag (förhandsvisning).
- **Konton:** Albin skapar dem själv (Facebook-sida, Instagram som företagskonto kopplat till sidan, TikTok), samma namn på alla, gärna nu så att namnet är reserverat.
- **Schemaläggning:** steg 1 genererar en månad i taget (bild + text per dag) som Albin lägger in i Meta Business Suite; steg 2 automatiskt via Meta Graph API från ett dagligt jobb (GitHub Actions), där Albin själv lägger in åtkomstnyckeln som hemlighet. TikTok kräver att appen godkänns för publik publicering, annars utkast.
- **Koppling till publiceringen:** varje publicerad artsida köas för ett inlägg; jämförelsesidorna kan få egna inlägg. Gärna samma art som appens Dagens fågel samma dag.
- **Bildlicenser:** bara CC0, public domain, CC BY och CC BY-SA, aldrig NC eller ND; beskärning och text runt bilden räknas som bearbetning; krediten skrivs i bildtexten ("Foto: X, licens, via Wikimedia Commons, beskuren"). Filtret ligger i koden som väljer bild, inte i manuell kontroll.
- **Senare:** en kort video med artens läte ("vems sång är det här?") som andra mall för Reels och TikTok.

**Valt koncept (Albin 2026-10-07, efter två förhandsvisningsrundor; runda ett med statiska mallar räckte inte):** rörligt först. Dagligen "See the song": inspelningen spelas medan dess spektrogram ritas, sedan tonar fotot fram och namnet handskrivs, slutkort "Identify birds by sound". Saknas en tillåten inspelning blir dagens inlägg "The field journal comes alive" (handskrivna anteckningar ritas på fotot, linjal, månadsstaplar). Fredagar "Who is it?" (siluett, ledtrådar, läte, avslöjande); varannan söndag "Same bird?" (två förväxlingsarter, deras sånger på samma tidsaxel, kopplad till jämförelsesidorna). Rutnätet hålls som ett konstverk: den rivna papperskanten på samma höjd i varje bild. **Licensfynd:** bara cirka 45 av de 180 arterna har en inspelning som får bli video (CC0/PD/CC BY; att lägga ljud på bild är bearbetning, så CC BY-SA och NC går inte); 155 av 180 huvudfoton är CC0/PD/CC BY. Fråga inspelare om tillstånd eller spela in själva för fler. Förhandsvisningar: `docs/superpowers/specs/assets/2026-10-06-1.3-val/birdy-posts-2.html` på grenen `release/1.3.0` (privat länk https://claude.ai/artifact/LcWuBjVSxt4MG3sSW9XQHA).

**Kanaler och lansering (Albins beslut 2026-10-07 kväll):** Facebook, Instagram och **YouTube Shorts** som tredje kanal (agentens val på Albins uppdrag "en tredje kanal där man kan schemalägga"): konceptet är rörligt med läte ("See the song"), YouTube tittas med ljud på, Shorts kan schemaläggas direkt i YouTube Studio och via YouTube Data API (`status.publishAt`), och videorna hittas i sök länge ("great tit song"). Alternativ om Albin hellre vill ha klick till artsidorna: Pinterest. TikTok sparas till betalda B2C-annonser senare. Facebook väntas fungera bäst för innehållet. **Allt släpps i en stöt:** appen 1.3.0 i produktion, de första artsidorna på birdy.community och de första inläggen samma dag. Albin skapar kontona själv (Facebook-sida, Instagram företagskonto kopplat till sidan i Meta Business Suite, YouTube-kanal), samma namn överallt.

**Samlad lansering (Albins idé 2026-10-06):** inläggen följer artsidorna sida för sida, och starten samordnas: appen 1.3.0 ligger redan ute (med nya butiksbilder), artsidornas första publiceringar, de första inläggen och utskicken i §15 sker samma vecka, så att webben, SEO, sociala medier och den nya appen drar åt samma håll. Agenten tar fram en lanseringskalender när fas 2:s publiceringsloop är klar.

**Språk (Albins beslut 2026-10-06): alla sociala kanaler är på engelska.** Bildtext och text i bilden på engelska, länken går till den engelska artsidan (`/species/<slug>/`), det svenska namnet kan stå inom parentes.

**Inläggens struktur (förslag 2026-10-06, Albin väljer mall; texterna nedan blir engelska):** allt text- och bildinnehåll fylls i av kod ur den kontrollerade artposten (inga nya påståenden, inga modellanrop, ingen kostnad).

- **Bild (1080 × 1350, 4:5, samma på Facebook och Instagram):** fotot över cirka 70 % av ytan (beskuret, licensfiltret ovan), under det ett pappersband i webbens färger med en liten rubrik "DAGENS FÅGEL · NR 12" (löpnummer i publiceringsordning), artnamnet i DM Serif, det vetenskapliga namnet i kursiv, en handskriven rad (Caveat) med ett kort kontrollerat faktum, och Birdys märke + "birdy.community" i hörnet.
- **Bildtext (svenska, engelskt namn inom parentes):**
  1. Krok: första meningen ur sidans ingress (redan kontrollerad).
  2. "Känn igen den:" två till tre fältkännetecken ur sidan.
  3. "När och var:" månaden med flest fynd ur Artportalen och svensk rödlistestatus om arten är rödlistad.
  4. "Kan förväxlas med:" en förväxlingsart om sidan har en.
  5. Uppmaning: "Läs mer på birdy.community (länk i bio)" på Instagram, direktlänk till sidan på Facebook, och "Känn igen den med Birdy-appen".
  6. Kredit: "Foto: X, licens, via Wikimedia Commons, beskuren".
  7. Fem till åtta hashtaggar: #fåglar #fågelskådning #svenskafåglar #birdwatching #birdy + artnamnet.
- **Mallval:** A en bild per inlägg; B en karusell med tre bilder (namn och foto, "Känn igen den", "När och var" med månadsdiagrammet ur Artportalen); senare C en kort video med lätet.
- **Rytm:** ett inlägg om dagen kl. 08.00 (samma tid som appens Dagens fågel-avisering), i publiceringsordning. Variation per vecka (valfritt): fredag ett "Vem är vem?"-inlägg ur en publicerad jämförelsesida (två foton), söndag "Veckans arter" som karusell.
- **Facebook vs Instagram:** samma bild; på Facebook är länken klickbar i inlägget, på Instagram står länken i bion (en länksida på birdy.community med veckans arter).
- **Förväntan:** jämn närvaro och igenkänning; trafiken till sidorna kommer främst från Google, inte från inläggen (länkar i Instagraminlägg är inte klickbara).

## 16. Faser och beroenden

1. **Fas 1b, pipelinen** (på `main`, rör ingen befintlig webbkod): källor, datamoduler, faktablad, automatisk kontroll, undantagsark och import, skrivande, kontroll, jämförelser, provkörning, körning och kontroll av faktabladen för alla 180. Ny plan. Kodarbetet startar direkt, parallellt med 1.3.0. **Den betalda körningen väntar på API-kredit** i Anthropic Console.
2. **Fas 2, sidorna** (grenen `website/artsidor` i worktree `C:/w/birdy-artsidor`): byggs mot testdata tills riktiga filer finns. Reviderad plan. Koden slås ihop till `main` en gång när den är klar; därefter publiceras arter och jämförelser sida för sida direkt på `main` enligt avsnitt 14 (ändrat 2026-10-05 (b)).
3. **Fas 3, mätning:** baslinjen tas om vid våg 1, triggrarna följs per våg.

**Albins uppmärksamhet går till 1.3.0 först** (brytpunkten och vC129). Agentens arbete med fas 1b och fas 2 kräver honom bara vid provkörningens modellval, undantagsarkets flaggor och stickprovet efter publicering (ändrat 2026-10-05 (b), ingen förhandsvisning att läsa igenom längre).

## 17. Risker

- **Google kan se sidorna som massproducerat innehåll.** Hundratals AI-skrivna sidor som bygger på Wikipedia är just det mönster Googles regler mot massproducerat innehåll riktar sig mot. Motmedlen är inbyggda från start: texten skrivs ur citerade fakta från tre språkversioner, varje sida har egen data som Wikipedia saknar (månadsdiagram, länskarta, svensk rödlista, inspelning, förväxlingsarter), jämförelsesidorna svarar på frågor som ingen uppslagssida svarar på, en andra modell kontrollerar varje faktablad och Albin beslutar om undantagen, sidan säger det, om-sidan redovisar hur sidorna görs, och publiceringen sker sida för sida med ett stickprov efter publicering och triggrar per våg (ändrat 2026-10-05 (b)).
- **Faktafel från modellen.** Motmedel: citat som kontrolleras i kod, den automatiska faktakontrollen (V1), fakta-id och talkontroll på varje mening, en andra modell som kontrollerar texten, statussignalen ur datan och "Hittade du ett fel?" på varje sida.
- **Snedvriden rapportdata.** Fler rapporterar i maj och nära städer. Motmedel: andelar av alla fågelrapporter i stället för antal, bildtext som säger vad diagrammet visar, gräns för för lite data.
- **Fel inspelning.** Motmedel: filtret i 9.1, Birdys egen ljudmodell (V4), Albin lyssnar på flaggade inspelningar i undantagsarket.
- **Licenser.** Motmedel: avsnitt 10 och byggkontrollen.
- **Albins tid.** Cirka 15 till 30 minuter granskning per våg (ändrat 2026-10-05, se Revision 2026-10-05; tidigare uppskattning var cirka 10 till 15 timmar manuell granskning totalt). Motmedel: automatisk kontroll av varje faktablad, ett löpande undantagsark där bara flaggor behöver beslut, och ett litet stickprov efter publicering i stället för en förhandsvisning att läsa igenom (ändrat 2026-10-05 (b)).
- **Kostnad.** Motmedel: provkörning på fyra arter, `--max-cost` på varje körning, cache så att inget hämtas eller skrivs två gånger.
- **Repots och byggets storlek.** Foton cirka 39 MB och inspelningar cirka 30 MB. Motmedel: nedskalning och klippning i pipelinen. Blir bygget för långsamt sänks antalet bildstorlekar.
- **Låga sökvolymer i sökordsplaneraren.** Motmedel: par utan volym får ingen sida men behåller sektionen på artsidan.
- **Namnbyten ändrar adresser.** Sällsynt. Omdirigering i `vercel.json`.

---

## Bilaga A: texter i gränssnittet

| Nyckel | SV | EN |
|---|---|---|
| Meny | Arter | Species |
| Kategorirad, första chip | Alla arter | All species |
| Sökfält | Sök art | Search species |
| Ingång, kicker | Uppslagsverket | Field guide |
| Ingång, h1 | Fåglar i Sverige och *Europa* | Birds of Sweden and *Europe* |
| Ingång, ingress | {n} vanliga fåglar med foton, kännetecken och läten. Samma uppslagsverk som i appen, där du också kan känna igen fågeln på plats. | {n} common birds with photos, field marks and calls. The same field guide as in the app, where you can also identify the bird on the spot. |
| Ingång, description | Bläddra bland {n} vanliga fåglar i Sverige och Europa. Foton, kännetecken och läten, sorterade i samma grupper som i appen Birdy. | Browse {n} common birds of Sweden and Europe. Photos, field marks and calls, sorted in the same groups as in the Birdy app. |
| Ingång, sektion jämförelser | Lätta att blanda ihop | Easy to mix up |
| Ingång, länk om-sidan | Så gör vi artsidorna | How we make these pages |
| Grupp, description | {Grupp}: {n} arter med foton, kännetecken och läten. Lär dig skilja dem åt i fält, och känn igen dem på plats med appen Birdy. | {Group}: {n} species with photos, field marks and calls. Learn to tell them apart, and identify them on the spot with the Birdy app. |
| Grupp, rubrik för arterna | Arterna | The species |
| Fakta: vetenskapligt namn | Vetenskapligt namn | Scientific name |
| Fakta: familj | Familj | Family |
| Fakta: i Sverige | I Sverige | In Sweden |
| Fakta: storlek | Storlek | Size |
| Fakta: svensk rödlista | Svenska rödlistan 2025 | Swedish Red List 2025 |
| Fakta: global rödlista | Global rödlista (IUCN) | Global Red List (IUCN) |
| Status `resident` | Stannfågel | Resident all year |
| Status `breeding_migrant` | Flyttfågel, häckar här | Summer visitor, breeds here |
| Status `passage` | Ses under flyttningen | Seen on migration |
| Status `winter_visitor` | Vintergäst | Winter visitor |
| Status `rare_visitor` | Sällsynt gäst | Rare visitor |
| Status `absent` | Förekommer inte | Does not occur |
| Rödlista RE / CR / EN / VU / NT / DD | Nationellt utdöd / Akut hotad / Starkt hotad / Sårbar / Nära hotad / Kunskapsbrist | Regionally extinct / Critically endangered / Endangered / Vulnerable / Near threatened / Data deficient |
| Rödlista `not_listed` | Inte rödlistad | Not red-listed |
| IUCN LC / NT / VU / EN / CR | Livskraftig / Nära hotad / Sårbar / Starkt hotad / Akut hotad | Least concern / Near threatened / Vulnerable / Endangered / Critically endangered |
| Rubrik kännetecken | Så känner du igen den | How to recognise it |
| Rubrik läte | Läte | Call and song |
| Spelare, etikett | Inspelning av {art i gemener} | Recording of the {name} |
| Rubrik var och när | Var och när | Where and when |
| Diagram, rubrik | När ses den i Sverige? | When is it seen in Sweden? |
| Diagram, bildtext | Andel av alla fågelrapporter per månad i Artportalen 2016 till 2025. | Share of all bird reports per month in Artportalen, 2016 to 2025. |
| Karta, rubrik | Var rapporteras den? | Where is it reported? |
| Karta, bildtext | Andel av alla fågelrapporter per län i Artportalen 2016 till 2025. | Share of all bird reports per county in Artportalen, 2016 to 2025. |
| Mening, året runt | Rapporteras året runt. | Reported all year round. |
| Mening, mest | Rapporteras mest i {månader}. | Reported most in {months}. |
| Mening, sällan | Rapporteras sällan i {månader}. | Rarely reported in {months}. |
| Mening, inga rapporter | Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025. | Does not occur in Sweden: no reports in Artportalen 2016 to 2025. |
| Mening, få rapporter | Sällsynt i Sverige: {n} rapporter i Artportalen 2016 till 2025. | Rare in Sweden: {n} reports in Artportalen 2016 to 2025. |
| Mening, län (jämn) | Rapporteras från alla 21 län. / Rapporteras från {n} av 21 län. | Reported from all 21 counties. / Reported from {n} of the 21 counties. |
| Mening, län (andel) | Andelen av alla fågelrapporter är högst i {län}, {län} och {län}. | Its share of all bird reports is highest in {county}, {county} and {county}. |
| Rubrik föda och beteende | Föda och beteende | Food and behaviour |
| Rubrik förväxlingsarter | Kan förväxlas med | Can be confused with |
| Länk jämförelse | Jämför {art} och {art} | Compare the {name} and the {name} |
| Rubrik fler (familj) | Fler {familj i gemener} | More in the {Latin} family |
| Rubrik fler (grupp) | Fler {grupp i gemener} | More {group in lowercase} |
| Appruta, rubrik | Osäker på vad du ser? | Not sure what you are seeing? |
| Appruta, foto och läte | Birdy känner igen {art i gemener} på foto eller läte, direkt i telefonen och utan täckning. | Birdy identifies the {name} from a photo or its song, right on your phone and without signal. |
| Appruta, bara läte | Birdy känner igen {art i gemener} på lätet, direkt i telefonen och utan täckning. | Birdy identifies the {name} from its song, right on your phone and without signal. |
| Appruta, bara foto | Birdy känner igen {art i gemener} på foto, direkt i telefonen och utan täckning. | Birdy identifies the {name} from a photo, right on your phone and without signal. |
| Appruta, ingen modell | Birdy hjälper dig känna igen fåglarna omkring dig på foto och läte, direkt i telefonen och utan täckning. | Birdy helps you identify the birds around you from photos and songs, right on your phone and without signal. |
| Jämförelse, kicker | Lätta att blanda ihop | Easy to mix up |
| Jämförelse, h1 | {A} eller *{B}*? | {A} or *{B}*? |
| Jämförelse, tabellrubrik | Så skiljer du dem åt | How to tell them apart |
| Jämförelse, tabellkolumn | Kännetecken | Feature |
| Jämförelse, diagram | När ses de? | When are they seen? |
| Jämförelse, appruta | Fortfarande osäker? | Still not sure? |
| Fotocredit | Foto: {fotograf}, {licens}, via Wikimedia Commons | Photo: {photographer}, {license}, via Wikimedia Commons |
| Inspelningscredit (klippt, ändrat 2026-10-06) | Inspelning: {upphov}, {licens}, via Wikimedia Commons, klippt och bearbetad | Recording: {recordist}, {license}, via Wikimedia Commons, trimmed and edited |
| Inspelningscredit (inte klippt, ändrat 2026-10-06) | Inspelning: {upphov}, {licens}, via Wikimedia Commons, bearbetad | Recording: {recordist}, {license}, via Wikimedia Commons, edited |
| Datacredit | Rapportdata: Artportalen (SLU Artdatabanken) via GBIF.org, 2016 till 2025. Rödlista: Rödlistade arter i Sverige 2025, SLU Artdatabanken. | Report data: Artportalen (SLU Swedish Species Information Centre) via GBIF.org, 2016 to 2025. Red list: The Swedish Red List 2025, SLU Swedish Species Information Centre. |
| Textcredit | Texten bygger på artiklarna om {art i gemener} på {språk} Wikipedia och får delas under CC BY-SA 4.0. | The text is based on the articles about the {name} on {languages} Wikipedia and may be shared under CC BY-SA 4.0. |
| Textcredit, språk | svenska, engelska och tyska (eller de som använts) | Swedish, English and German (or those used) |
| Kontroll | Kontrollerad mot källorna {datum}. | Checked against sources on {date}. |
| Fel i texten | Hittade du ett fel? Skriv till oss. | Found a mistake? Write to us. |
| Förhandsvisning, banderoll | Förhandsvisning, inte publicerad | Preview, not published |
| Sidfot, kolumn | Arter | Species |
| Sidfot, sista länk | Alla arter från A till Ö | All species A to Z |
| Sidfot, rad | Vanliga arter | Common species |
| Startsidan, länk | Bläddra bland arterna | Browse the species |

Gruppnamnen är appens (`archive_chip_*`). Accentord i rubriker står inom `*…*`. Månadsnamn skrivs ut i meningarna och förkortas till en bokstav under staplarna. Inga tankstreck i någon text. Om-sidans text och description skrivs i planen.

## Bilaga B: förbjudna fraser (startlista)

**Svenska:** anmärkningsvärd, anmärkningsvärt, fascinerande, spännande, magnifik, fantastisk, unik, en sann, en riktig pärla, inte bara, utan också, i hjärtat av, en symbol för, värd att upptäcka, kort sagt, sammanfattningsvis, det är värt att notera, ett nöje att.

**Engelska:** remarkable, fascinating, stunning, breathtaking, magnificent, boasts, nestled, a true, a testament to, not only, but also, in the heart of, it is worth noting, delve, tapestry, vibrant, iconic, truly.

Listan ligger i `prompts/web-banned-phrases.txt` (med böjningsformer sedan fas 1) och får växa när kontrollen hittar nya mönster.

## Bilaga C: schema för `website/src/data/species/<QID>.json`

```json
{
  "qid": "Q25485",
  "status": "ok",
  "publish": false,
  "slug": { "sv": "talgoxe", "en": "great-tit" },
  "names": { "sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major" },
  "family": { "latin": "Paridae", "sv": "Mesar" },
  "group": "songbirds",
  "iucn": "LC",
  "swedishRedList": "not_listed",
  "identifiable": { "photo": true, "sound": true },
  "marginalia": { "sv": "…", "en": "…" },
  "images": [
    {
      "role": "hero", "file": "Q25485/hero.webp", "width": 1600, "height": 1067,
      "author": "Hobbyfotowiki", "license": "CC0", "licenseUrl": null,
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:…"
    }
  ],
  "audio": {
    "file": "Q25485/voice.mp3", "durationSec": 20, "trimmed": true,
    "author": "…", "license": "CC BY-SA 4.0", "licenseUrl": "https://creativecommons.org/licenses/by-sa/4.0/",
    "sourceUrl": "https://commons.wikimedia.org/wiki/File:…",
    "sha256": "…", "mp3Sha256": "…"
  },
  "wikipedia": {
    "sv": { "title": "Talgoxe", "revision": "59064377" },
    "en": { "title": "Great tit", "revision": "1334945574" },
    "de": { "title": "Kohlmeise", "revision": "…" }
  },
  "data": {
    "fetchedAt": "2026-10-…",
    "gbifTaxonKey": 9705453,
    "totalReports": 1932311,
    "months": [72, 58, 61, 55, 70, 79, 64, 68, 74, 100, 66, 69],
    "counties": { "SE-AB": 2, "SE-BD": 1 },
    "raw": { "speciesByMonth": [], "allBirdsByMonth": [], "speciesByCounty": {}, "allBirdsByCounty": {} },
    "sentences": { "sv": ["Rapporteras året runt.", "…"], "en": ["Reported all year round.", "…"] },
    "statusSignal": { "contradicts": null }
  },
  "facts": [
    {
      "id": "f01", "topic": "appearance",
      "sv": "Talgoxen har svart huvud med vita kinder och gul undersida.",
      "sources": [ { "article": "de", "quote": "…" } ],
      "edited": false
    },
    {
      "id": "f09", "topic": "lookalike", "sv": "…",
      "other": { "scientific": "Cyanistes caeruleus", "qid": "Q25404", "written": "C. caeruleus" },
      "sources": [ { "article": "sv", "quote": "…" } ]
    },
    { "id": "d01", "topic": "data", "source": "artportalen", "sv": "Rapporteras året runt." }
  ],
  "review": {
    "wave": 1,
    "statusConfirmed": false
  },
  "verification": {
    "method": "auto", "at": "2026-11-…", "model": "…", "spotChecked": false
  },
  "text": {
    "sv": {
      "lead": [ { "text": "…", "factIds": ["f01", "f05"] } ],
      "fieldMarks": [ { "text": "…", "factIds": ["f01"] } ],
      "voice": [ { "text": "…", "factIds": ["f04"] } ],
      "whereWhen": [ { "text": "…", "factIds": ["f06", "d01"] } ],
      "behaviour": [ { "text": "…", "factIds": ["f07"] } ],
      "lookAlikes": [ { "other": "Q25404", "text": [ { "text": "…", "factIds": ["f09"] } ] } ],
      "metaDescription": "…",
      "facts": {
        "size": { "value": "Cirka 14 cm", "factIds": ["f02"] },
        "swedenStatus": { "value": "resident", "factIds": ["s01"] }
      }
    },
    "en": { "…": "samma fält" }
  },
  "generated": {
    "facts": { "model": "…", "prompt": "facts-v1", "effort": "high", "at": "…" },
    "text": { "model": "…", "prompt": "web-v2", "effort": "high", "checker": "…", "at": "…" }
  },
  "errors": []
}
```

- `status` är `pending` (källor och faktablad finns men ingen text än), `ok` eller `failed`. `pending` och `failed` har `"text": null`; en `failed`-post har texten som inte klarade sig under `rejectedText`. Bara `ok` kan ge en sida.
- `review` är alltid ett objekt. `review.statusConfirmed` är sant när Albin har behållit statusen trots en flagga.
- `verification` finns bara för en art som har klarat den automatiska kontrollen (9.4) och, om den hade flaggor, fått Albins beslut. `verification.model` är kontrollmodellens id, `verification.spotChecked` är sant om arten har dragits i stickprovet efter publicering (ändrat 2026-10-05 (b), avsnitt 14). Ingen text skrivs för en art utan `verification`.
- `publish` styr produktionsbygget (avsnitt 14).
- `audio`, `marginalia`, `data` och `wikipedia.de` kan saknas. `data.months` och `data.counties` saknas när arten har färre än 200 rapporter.
- `family.sv` är familjens svenska namn ur pipelinens tabell `web/families.py`, ett namn per latinsk familj enligt BirdLife Sveriges lista över världens fåglar (NL20, version 2025), inte appens `family_sv` (**ändrat 2026-10-07**: den gav till exempel både "Mesar" och "Mesfåglar" för Paridae, det latinska namnet för fem familjer och "Fältsparvar" för både Calcariidae och Emberizidae). Saknas en familj i tabellen stoppar källsteget.
- `audio.sha256` är hashen av den nedladdade originalfilen (identifierar inspelningen, `review.audioKept` gäller den), `audio.mp3Sha256` hashen av den omkodade `voice.mp3` (**tillagt 2026-10-07**): städningen i slutet av `web sources`, `web verify` och `web import` tar bort en `voice.mp3` som inte har den hashen (ett avbrutet byte av inspelning i V4) och varje kvarlämnad `voice.mp3.new`, och artposten förlorar då `verification` och får en V4-flagga. Går det nya bytet inte att lägga på plats (låst fil) sparas artposten om på samma sätt. `web verify --force` hämtar då inspelningen som artposten anger från Commons igen (samma originalfil, kontrollerad med `audio.sha256`), lägger den på plats och kör V4 på den; går det inte blir det en V4-flagga och den felaktiga filen tas bort. En art vars `audio` har `mp3Sha256` räknas inte som klar för publicering om `voice.mp3` saknas eller inte har den hashen.
- `swedishRedList` är `RE`, `CR`, `EN`, `VU`, `NT`, `DD` eller `not_listed`, eller saknas om matchningen mot listan misslyckades. **Tillagt 2026-10-07:** rödlistans dataset på GBIF har bara rödlistade arter, så `not_listed` skiljer inte "bedömd, livskraftig" från "inte bedömd". En art som inte finns i listan och har färre än 200 rapporter förekommer inte regelbundet i Sverige och är inte bedömd (NA eller NE): fältet saknas då, raden döljs och ingen mening "Inte rödlistad i Svenska rödlistan 2025" skrivs.
- `lookAlikes[].other` och `facts[].other.qid` är QID när den andra arten finns bland de 839, annars saknas `qid` och `lookAlikes[].other` är det vetenskapliga namnet. Bara arter med publicerad sida länkas. `facts[].other.scientific` är Birdys binomen när arten matchades (avsnitt 9.3), annars namnet som artikeln skriver det; `facts[].other.written` finns när artikeln skriver namnet på annat sätt än Birdy ("C. caeruleus"), så att kontrollen och en granskning ser vad citatet sa.
- Sajtens zod-schema läser bara de fält sidorna behöver och görs inte `.strict()`. Av `facts` läser sajten bara `id`, `topic` och `other` (namnet på en förväxlingsart som inte har egen sida). Av `verification` läser sajten bara `at` (till creditraden och `lastReviewed`). `raw`, `generated` och `rejectedText` läses inte av sajten.
- `publish` sätts bara av pipelinen (`birdy-fetcher web publish --wave N`), aldrig för hand och aldrig av sajtens skript.

## Bilaga D: schema för `website/src/data/comparisons/<QID-A>_<QID-B>.json`

```json
{
  "a": "Q25404",
  "b": "Q25485",
  "status": "ok",
  "publish": false,
  "slug": { "sv": "blames-eller-talgoxe", "en": "eurasian-blue-tit-vs-great-tit" },
  "volumes": { "sv": 1300, "en": 880 },
  "text": {
    "sv": {
      "shortAnswer": [ { "text": "…", "factIds": ["a:f01", "b:f01"] } ],
      "rows": [
        { "feature": "Storlek", "a": { "text": "…", "factIds": ["a:f02"] }, "b": { "text": "…", "factIds": ["b:f02"] } }
      ],
      "metaDescription": "…"
    },
    "en": { "…": "samma fält" }
  },
  "generated": { "model": "…", "prompt": "compare-v1", "checker": "…", "at": "…" },
  "errors": []
}
```

Fakta-id har prefixet `a:` eller `b:` för att visa vilken arts faktablad de kommer från. `a` och `b` följer slug-ordningen på svenska.

## Bilaga E: undantagsarket och stickprovet

**Undantagsarket** (ändrat 2026-10-05 (b), var tidigare en flik per våg): ett löpande ark, inte uppdelat per våg. Innehåller bara det den automatiska kontrollen inte kan avgöra: alla flaggor från V2 till V4. En rad per faktum, inspelning eller datarad som flaggats, sorterat på art och ämne, i den ordning arterna körs.

**Stickprovet** (flyttat hit från undantagsarket, 2026-10-05 (b)): en egen flik, fylld efter publicering. För var 40:e publicerade art dras 2 (sparat frö) och läggs in med hela faktabladet; för var 10:e publicerade jämförelsesida dras 1. Samma kolumner som undantagsarket, plus vilken art eller jämförelse och vilket datum den publicerades. Hittar Albin ett fel som kontrollen borde ha fångat dras två nya och missen loggas i rapporten; ett bekräftat fel ger sidan ett nytt datum på "Kontrollerad mot källorna" och publiceras om.

**Ändrat 2026-10-06 (efter fas 1b:s slutgranskning):** varje dragning är en egen fil, `review/stickprov-dragning-N.csv`, och ett eget ark eller en egen flik i Drive, så att en ny dragning aldrig laddas upp över en som inte är importerad. Varje art inleds med en **artrad** (Typ `art`, Id `*`): `behåll` där gäller alla rader för arten i dragningen som Albin lämnar tomma, och ett beslut han skriver på en rad gäller före artraden. Utan `behåll` på artraden måste varje rad ha ett beslut. Sätter Albin i stickprovet (eller på en V3-flagga) en status som rapportdatan eller rödlistan motsäger på en publicerad art, avvisas importen: först `publish: false`, sedan importera igen.

Kolumnerna är samma i båda flikarna:

| Kolumn | Innehåll |
|---|---|
| Art | Svenskt namn (eller "{A} och {B}" för en jämförelse) |
| QID | Wikidata-id |
| Rad | `stickprov` eller `flagga` |
| Kontroll | Vilken kontroll som gav flaggan: `V2`, `V3` eller `V4`. Tomt för en stickprovsrad. |
| Typ | `faktum`, `data`, `inspelning` eller `flagga`, plus `art` för stickprovets artrad (2026-10-06) |
| Id | Fakta-id (`f03`, `d01`), eller tomt för en inspelningsflagga; `*` på artraden |
| Ämne | Ämnet på svenska |
| Faktum | Faktumet på svenska. Albin skriver här när beslutet är `ändra`. |
| Källa | `sv`, `en` eller `de` med länk till artikeln på rätt revision, eller `Artportalen` / `Rödlistan` / länk till inspelningens filsida |
| Citat | Citatet ur artikeln |
| Beslut | `behåll` (förifyllt), `stryk` eller `ändra`. Flaggor är tomma och måste fyllas i. Datarader är låsta. **Ändrat 2026-10-06 (fas 1b:s slutgranskning I1):** inget är förifyllt i stickprovet heller; en rad utan beslut gör att arten väntar, så en import innan Albin tittat räknas aldrig som kontrollerad. Inspelningsraden tar `behåll` eller `stryk`, artraden bara `behåll` (eller tomt), och `behåll` på artraden gäller artens tomma rader i samma dragning. |
| Kommentar | Fri text |
| Publicerad | Bara i stickprovsfliken (2026-10-05 (b)): datumet sidan publicerades |
| Dragning | Bara i stickprovsfliken (2026-10-06, slutgranskningen C2 och I1): vilken dragning raden hör till (`review/stickprov-state.json`). Importen tillämpar bara artens öppna dragning. |
