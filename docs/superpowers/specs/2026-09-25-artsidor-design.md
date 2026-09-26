# Artsidor på birdy.community: design

> **Datum:** 2026-09-25 (Windows). **Beslutat med Albin** i ett brainstorm-pass samma dag, med mockups i webbläsaren.
> **Mål:** Organisk söktrafik till birdy.community genom sidor för varje art och grupp, byggda ur Birdys eget artinnehåll. Sidorna ska svara på det folk söker ("talgoxe", "ugglor i Sverige", "hur låter en koltrast"), leda vidare till appen och samtidigt bli AlbIT:s eget bevis för SEO Pro.
> **Mockups (godkända):** `docs/superpowers/specs/assets/2026-09-25-artsidor/`. `helheten.html` visar sidhuvud, kategorirad, ingångssida, artsida och sidfot på dator och i mobil. `artsida-layouter.html` visar de tre layouterna som jämfördes (B valdes). Öppna filerna direkt i en webbläsare, bilderna ligger bredvid. Talgoxens texter i mockuperna är exempel på tonen, inte slutlig text.
> **Hör ihop med:** `2026-09-24-website-1-3-lyft-design.md` (sajtens look, palett och komponenter som artsidorna bygger på) och valvets regel `seo-och-ton-vid-nya-sidor` (checklistan som avsnitt 10 gör till kod).

---

## 1. Utgångsläge (verifierat 2026-09-25)

**Artdatan** (`shared/content/species/*/*.yaml`, 839 arter):

- **180 arter har `review_status: approved`**, och alla är klassade `abundance: allmän`. Alla 180 har ett huvudfoto. De övriga 659 är `auto` (ogranskade) och `ovanlig`.
- **Säsongsdatan saknar signal.** Alla 839 arter står som `present` i alla tolv månader, och alla har samma fem länder under `regions`. Den kan inte användas för att säga när eller var en art syns.
- **Beskrivningarna håller inte som webbtext.** De är Claude Haiku-sammanfattningar av Wikipedias *ingress* (prompt `description-v1.md`). Bland de 180 granskade:
  - 57 svenska beskrivningar nämner inget utseendedrag (ingen färg, näbb eller fjäderdräkt). Talgoxens text handlar om taxonomi.
  - 22 arter har en tom beskrivning på minst ett språk, bland dem Bofink, Havsörn, Skrattmås och Rödbena.
  - 162 börjar med en markdownrubrik (`# Talgoxe`).
  - Flytt-texterna har typiska AI-fraser ("vilket är anmärkningsvärt för en så liten fågel").
- **`iucn_status` är IUCN:s globala rödlista** (Wikidata), inte Artdatabankens svenska. Fördelning bland alla 839: LC 697, NE 51, NT 46, VU 39, CR 6.
- **Fotona:** 533 foton för de 180 arterna (huvudfoto plus upp till två extra), 90 MB i full storlek (1800 till 2400 px). Licenser: CC BY 4.0, CC BY 2.0, CC BY-SA 4.0, CC0, public domain, CC BY-SA 2.0, CC BY 3.0 och CC BY-SA 3.0. Fältet `author` innehåller rå HTML från Commons i 178 av 180 filer.
- **Grupperna** finns redan i appen: `shared/content/src/jvmMain/resources/family_groups.yaml` (15 ekologiska grupper, Tättingar via ordningen Passeriformes, övriga via latinsk familj). Etiketterna finns i appens strängar (`archive_chip_*`, SV och EN). Antal granskade arter per grupp: Tättingar 88, Änder & gäss 18, Vadare 14, Rovfåglar 10, Måsar & tärnor 9, Ugglor 8, Hönsfåglar 7, Hackspettar 5, Duvor 4, Tranor & rallar 4, Övriga 4, Alkor 3, Doppingar & lommar 3, Hägrar & storkar 2, Havsfåglar 1.
- **Innehållspipelinen** (`tools/content-pipeline`, Python med uv, `birdy-fetcher`) hämtar redan Wikidata, Wikipedia och Commons och anropar Claude via `ANTHROPIC_API_KEY` i `.env`, med kostnadstak (`--max-cost`).

**Webben** (grenen `website/1.3-lyft`, worktree `C:\w\birdy-web`, går live med vC129):

- Astro 5 och Tailwind v4, statisk sajt, Vercel bygger `website/` (Root Directory = `website`). Innehåll via content collections (`src/content.config.ts`).
- `Layout.astro` sätter canonical, hreflang och x-default. Språkparet räknas fram av `alternateHref()` i `src/lib/i18n.ts`, som bara byter prefixet `/sv`. Det fungerar inte när adresserna skiljer sig mellan språken.
- Sidfoten har redan "Byggd av AlbIT" med follow-länk till `albit.se/produkter/birdy/`. Varje ny sida får den länken automatiskt.
- Befintliga kontroller: `test:i18n`, `test:no-accuracy`, `test:no-dashes`, `test:contrast` och Playwright (`test:smoke`). CI täcker inte webben.

## 2. Beslut

| # | Fråga | Beslut |
|---|---|---|
| 1 | Språk | **Svenska och engelska från start.** Texterna finns på båda, och Search Console mäter varje katalog för sig. |
| 2 | Omfång | **De 180 granskade arterna.** Bara `review_status: approved` får en sida. Övriga tillkommer i takt med att de granskas. |
| 3 | Texter | **Ny webbtext per art** via ett nytt pipelinesteg, med fast struktur, automatiska kontroller och Albins stickprov. Appens texter rörs inte. |
| 4 | Artsidans layout | **B "Uppslaget":** två spalter som en uppslagen fältbok. Foto, fakta och appknapp följer med när man scrollar på dator. En spalt i mobilen. |
| 5 | Navigering | **Kategoriraden** (chips med grupperna) under menyn på alla artsidor, **sökfält** i kategoriraden, kolumnen **Arter** och raden **Vanliga arter** i sidfoten. "Arter" blir första länken i menyn. **Ingen** utfällbar meny i sidhuvudet. |
| 6 | Teknik | **Nytt pipelinesteg** skriver texter och nedskalade foton till `website/`. Vercel bygger bara `website/` som idag. |
| 7 | Grupper | Samma 15 grupper som appens uppslagsverk, med egna sidor. |
| 8 | Publicering | Tre faser (avsnitt 13). Sidorna byggs först när 1.3-webben är live. |

**Saker specen bestämmer utöver mockupen** (ändra i granskningen om du vill något annat):

1. **Rödlisteraden heter "Global rödlista (IUCN)"**, inte "Rödlistan". Datan är IUCN:s globala status. Mockupen sa bara "Rödlistan", vilket hade antytt den svenska listan.
2. **Ingångssidans rubrik är "Fåglar i Sverige och Europa"**, inte "Sveriges fåglar". Bland de 180 finns arter som inte förekommer i Sverige (till exempel Koboltmes på Kanarieöarna).
3. **Brödsmulorna hoppar över familjen** (Birdy › Arter › Tättingar › Talgoxe), eftersom familjerna inte får egna sidor och varje led i brödsmulorna ska vara en länk.
4. **Grupper med färre än tre arter får `noindex`** och ligger utanför sitemapen tills de växer (i dag Havsfåglar och Hägrar & storkar). Sidorna finns och är länkade, men Google ombeds vänta.

## 3. Omfång

**Ingår:**

- Pipelinesteget `web` i `tools/content-pipeline`: webbtexter, fakta med källcitat, nedskalade foton och licensdata för de 180 arterna samt ingresser för de 15 grupperna.
- Sidorna: 180 artsidor, 15 gruppsidor och en ingångssida, på två språk (cirka 392 sidor).
- Kategorirad, sökfält, ändringar i meny, sidfot och startsidans uppslagsverkssektion.
- `Layout` får ett uttryckligt språkpar.
- SEO-reglerna som kod (`check-seo.mjs`) och nya Playwright-tester.
- Mätning: baslinje i Search Console, UTM-taggar på Play-länkarna och triggrar för nästa omgång.

**Ingår inte:**

- Månadssidor ("Fåglar att se i oktober"): säsongsdatan saknar signal. Riktig säsongsdata blir ett eget projekt som även gynnar appen.
- De 659 ogranskade arterna.
- Utfällbar Arter-meny i sidhuvudet, familjesidor, ljud och inspelningar, App Store-länk (iPhone-appen är inte ute).
- Ändringar i appen eller i `shared/content/species/*.yaml`. Att föra tillbaka de bättre texterna till appen är ett möjligt senare steg.
- Lyssnare och utkast för sociala medier (egen idé från samma samtal, eget projekt).

## 4. Adresser och sidtyper

| Sidtyp | Svenska | Engelska |
|---|---|---|
| Ingångssida | `/sv/arter/` | `/species/` |
| Grupp | `/sv/arter/ugglor/` | `/species/owls/` |
| Art | `/sv/arter/talgoxe/` | `/species/great-tit/` |

- **Arter och grupper delar mapp.** En dynamisk route per språk (`src/pages/species/[slug].astro` och `src/pages/sv/arter/[slug].astro`) renderar antingen en artsida eller en gruppsida.
- **Slug-regler:** artens namn på språket, gemener, `å ä` → `a`, `ö` → `o`, `é è` → `e`, `ü` → `u`, mellanslag och apostrofer → `-`, `&` → `och`/`and`, övriga tecken bort. Exempel: "Större hackspett" → `storre-hackspett`, "Eurasian Blue Tit" → `eurasian-blue-tit`.
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

- **Unikhet:** ett test i bygget failar om två sidor på samma språk får samma slug (art mot art eller art mot grupp).
- **Språkparet** går alltid mellan samma art (samma QID) eller samma grupp.

## 5. Artsidan (layout B)

**Dator (från 1024 px):** två spalter, 5 : 7, med en streckad hårlinje emellan.

- **Vänster spalt**, `position: sticky` under menyn och kategoriraden:
  1. Huvudfoto i planschram (`plate`) med bildtext i Caveat: "Pl. 1, Talgoxe" till vänster och "Foto: {fotograf}" till höger.
  2. Faktalista (etiketter i bilaga A):
     - Vetenskapligt namn (kursivt)
     - Familj: svenskt familjenamn på svenska, latinskt på engelska
     - I Sverige: status ur texterna, se avsnitt 7
     - Storlek: ur texterna, till exempel "Cirka 14 cm" eller "13 till 15 cm"
     - Global rödlista (IUCN): kategorin i ord med koden inom parentes. Raden döljs när koden är NE.

     Raderna "I Sverige" och "Storlek" döljs när faktauppgiften saknas (`null`, avsnitt 7).
  3. Appruta (mossgrön): rubrik "Osäker på vad du ser?" och text om att Birdy känner igen arten på foto eller läte utan täckning. Därunder det officiella Google Play-märket med UTM (avsnitt 12).
  4. Marginalanteckning i Caveat, bara där artfilen har `marginalia` (i dag fyra arter).
- **Höger spalt:**
  1. Brödsmulor: Birdy › Arter › {Grupp} › {Art}.
  2. Kicker med familjen, h1 med artens namn och det vetenskapliga namnet i Caveat.
  3. Ingress (1 till 2 meningar).
  4. h2 "Så känner du igen den": 3 till 4 punkter.
  5. h2 "Läte": ett stycke.
  6. h2 "Var och när": ett stycke.
  7. Extrafoto i planschram ("Pl. 2"), bara om arten har ett.
  8. h2 "Fler {familj}", till exempel "Fler mesar" (EN: "More in the {Paridae} family"). Upp till fyra andra granskade arter i samma familj, i bokstavsordning. Finns färre än två fylls listan på från samma grupp, och rubriken blir då "Fler {grupp}".
  9. Credits (avsnitt 8) och raden "Hittade du ett fel? Skriv till oss" (mejllänk till `CONTACT_EMAIL` med ämnet "Fel på artsidan: {Art}").

**Mobil (under 1024 px):** en spalt i ordningen brödsmulor, kicker, h1 och vetenskapligt namn, huvudfoto, faktalista, ingress, texterna, extrafoto, appruta, marginalanteckning, fler arter och credits. Ingen sticky. Ingen sidledsscroll i 360 till 430 px.

**Samma komponenter som 1.3-webben:** färgtokens, `plate`, kicker, knappar och kort från `2026-09-24-website-1-3-lyft-design.md` avsnitt 4. Inga nya typsnitt.

## 6. Ingångssidan, gruppsidorna och navigeringen

**Kategoriraden** (ny komponent `CategoryBar.astro`) ligger direkt under menyraden på ingångssidan, gruppsidorna och artsidorna. Den följer med menyn när man scrollar (sticky under den).

- Chips: "Alla arter" plus de 15 grupperna i appens ordning, var och en med antal arter. Aktiv grupp (eller "Alla arter" på ingångssidan) är rostfärgad och har `aria-current`.
- Raden går att svepa i sidled (`overflow-x: auto`, ingen synlig scrollbar på mobil) och den aktiva chipen scrollas in i bild när sidan laddas.
- **Sökfältet** till höger är ett vanligt formulär (`GET`) som skickar `q` till ingångssidan. På små skärmar blir det en sökikon som leder till ingångssidans sökfält.

**Ingångssidan** (`/sv/arter/`, `/species/`):

- Kicker "Uppslagsverket", h1 "Fåglar i Sverige och Europa" (EN "Birds of Sweden and Europe") och en ingress (bilaga A).
- Ett sökfält högst upp som filtrerar A till Ö-listan medan man skriver. Det matchar svenskt, engelskt och vetenskapligt namn, oberoende av skiftläge och diakritiska tecken. `?q=` fylls i från kategoriraden. Utan JavaScript visas hela listan.
- Ett kort per grupp med foto, namn och antal arter. Fotona är fasta per grupp: Tättingar Koltrast (Q25234), Änder & gäss Gräsand (Q25348), Vadare Strandskata (Q25928), Måsar & tärnor Fiskmås (Q26427), Alkor Tordmule (Q27102), Havsfåglar Storskarv (Q25440), Doppingar & lommar Skäggdopping (Q25422), Hägrar & storkar Gråhäger (Q25273), Rovfåglar Ormvråk (Q25385), Ugglor Kattuggla (Q25756), Hönsfåglar Fasan (Q25432), Duvor Ringduva (Q26026), Hackspettar Större hackspett (Q26209), Tranor & rallar Trana (Q4764) och Övriga Gök (Q18845).
- A till Ö-lista med alla arter (namn och vetenskapligt namn) under bokstavsrubriker.

**Gruppsidorna:** kicker "Uppslagsverket", h1 med gruppens namn, en ingress på 2 till 3 meningar (skrivs i planen och granskas av Albin, avsnitt 7) och ett rutnät med gruppens arter (foto, namn och vetenskapligt namn) i bokstavsordning. Tättingar är stor (88), så där delas rutnätet upp med familjen som underrubrik.

**Menyn** (`Nav.astro`): "Arter" (EN "Species") blir första länken, både på dator och i mobilmenyn, och pekar på ingångssidan. Övriga länkar står kvar. På ingångssidan, gruppsidorna och artsidorna har länken `aria-current`.

**Sidfoten** (`Footer.astro`):

- En ny kolumn **Arter** (EN "Species") mellan varumärket och "Utforska", med de fem grupperna som har flest arter (beräknas ur datan, i dag Tättingar, Änder & gäss, Vadare, Rovfåglar och Måsar & tärnor) och "Alla arter från A till Ö".
- En ny rad **Vanliga arter** (EN "Common species") ovanför copyrightraden med tolv länkar: Talgoxe, Blåmes, Koltrast, Rödhake, Gråsparv, Skata, Kaja, Bofink, Gräsand, Fiskmås, Ormvråk och Trana (alla granskade, QID i `lib/species.ts`). Ett test failar om någon av dem saknar sida.
- Rutnätet blir fem kolumner på dator. I mobilen ligger varumärket överst och kolumnerna två och två, som idag.
- "Byggd av AlbIT" står kvar oförändrad.

**Startsidan:** sektionen Uppslagsverket (`#guide`) får en textlänk "Bläddra bland arterna" till ingångssidan. Inget annat på startsidan ändras.

## 7. Texterna: pipelinesteget `web`

**Kommando:** `uv run birdy-fetcher web --approved --max-cost <belopp>`, eller `--species Q25485` för en art. Samma cache, samma `.env` och samma kostnadsspärr som de befintliga stegen. Modellen är i Sonnet-klass. Exakt modell-id och standardtak bestäms i planen utifrån `claude-api`-skillen.

**Underlag per art:**

- Hela Wikipediaartikeln i klartext på svenska och engelska, på en fast revision som sparas i utdata.
- Namn, familj, grupp och IUCN-kod ur artfilen.

**Prompt:** `tools/content-pipeline/prompts/web-v1.md`. Skrivregler i prompten: kort och konkret, inga tankstreck, inga utropstecken, inget jag eller vi, inga platsnamn utanför Sverige i "Var och när", hitta inte på något som inte står i källan, skriv hellre kortare. Svaret kommer som strukturerad JSON (tool use eller motsvarande) per språk:

| Fält | Innehåll | Gräns |
|---|---|---|
| `lead` | ingress | 1 till 2 meningar, högst 45 ord |
| `fieldMarks` | kännetecken | 3 till 4 punkter, högst 16 ord var |
| `voice` | läte | högst 60 ord |
| `whereWhen` | var och när i Sverige | högst 70 ord |
| `metaDescription` | för sökresultatet | 120 till 155 tecken |
| `facts.size` | längd i cm som text ("Cirka 14 cm", "13 till 15 cm") plus `quote` | citat krävs |
| `facts.swedenStatus` | en av `resident`, `breeding_migrant`, `passage`, `winter_visitor`, `rare_visitor`, `absent` plus `quote` | citat krävs |

**Kontroller (körs i Python innan något sparas):**

1. **Inga streck:** tankstreck (U+2014), tankstreck med mellanslag runt (U+2013) och `--`. Samma regler som webbens `check-no-dashes.mjs`.
2. **Förbjudna fraser** ur en lista i `prompts/web-banned-phrases.txt`. Startlistan finns i bilaga B.
3. **Längder** enligt tabellen, antal punkter i `fieldMarks`, inga utropstecken och ingen första person ("jag", "vi", "I ", "we").
4. **Källcitat:** varje `quote` måste finnas ordagrant i artikeltexten (någon av språkversionerna) efter normalisering av blanksteg, skiftläge och citattecken. Annars räknas faktauppgiften som ogiltig.
5. **Rimlighet:** `absent` får inte kombineras med en `whereWhen` som beskriver förekomst i Sverige. Det kontrolleras med en enkel ordlista ("häckar", "ses", "vanlig", "breeds", "common").

**Om en kontroll failar** görs ett nytt försök där felen skickas tillbaka till modellen. Vad som händer om det failar igen beror på fältet:

- **Textfälten** (`lead`, `fieldMarks`, `voice`, `whereWhen`, `metaDescription`) och kontrollerna 1, 2, 3 och 5 är obligatoriska. Failar de igen sparas arten med `status: "failed"` och felen. Den får ingen sida.
- **Faktauppgifterna** (`facts.size`, `facts.swedenStatus`) är frivilliga. Saknar en av dem giltigt citat efter det andra försöket stryks den (sätts till `null`). Arten får ändå sin sida, men den raden i faktalistan döljs. Alla artiklar anger inte storlek, och då ska ingen storlek hittas på.

Allt hamnar i rapporten `tools/content-pipeline/reports/web-<datum>.md`: kostnad, antal godkända, arter med `failed` och deras fel, och alla faktauppgifter som ströks.

**Utdata:** en fil per art, `website/src/data/species/<QID>.json` (schema i bilaga C). Filen går att rätta för hand, och den som rättar sätter `review` till `"approved"`. Pipelinen skriver aldrig över en fil med `review: "approved"` utan flaggan `--force`.

**Gruppernas ingresser** (15 grupper × 2 språk) skrivs som text i planen, inte av pipelinen, och ligger i `website/src/data/species-groups.json`. Samma kontroller körs på dem (`test:no-dashes` och längd).

**Albins granskning före go-live:** de 20 vanligaste arterna på svenska (i första hand de tolv i sidfoten) och 10 på engelska, plus alla gruppingresser. Granskningen gäller ton och fakta. Hittar Albin systematiska fel ändras prompten och alla arter körs om, inte bara de granskade.

## 8. Bilder och licenser

- **Nedskalning i pipelinen:** huvudfoto till 1600 px bredd och första extrafotot till 1200 px, WebP i kvalitet 78, till `website/src/assets/species/<QID>/hero.webp` och `extra.webp`. Uppskattat cirka 25 MB för 180 arter. Astro gör responsiva storlekar (`<Picture>` med `widths` och `sizes`) när sajten byggs.
- **Metadata per foto** i artens JSON:
  - fotograf som ren text (HTML tvättas bort)
  - licens-id och länk till licensen, ur en fast tabell i pipelinen för de åtta licenserna (public domain och CC0 får ingen licenslänk utan texten "Public domain" eller "CC0")
  - källsida på Commons och bredd och höjd
- **Credits på sidan** (bilaga A har formuleringarna):
  - en rad per foto som visas, med fotograf, licens (länkad) och "via Wikimedia Commons" (länkad till källsidan)
  - en rad om texten: att den bygger på Wikipediaartiklarna (länkade, med revision) och får delas under CC BY-SA 4.0
- **Alt-text:** "Talgoxe (Parus major)" för huvudfotot och "Talgoxe, ytterligare foto" för extrafotot. Engelska motsvarigheter.
- **Fotona visas oförändrade** (bara nedskalade, ingen beskärning i filen). Ramen beskär inte.

## 9. Strukturerad data

- **Artsida:** `BreadcrumbList` som matchar brödsmulorna ordagrant, plus `WebPage` med:
  - `about`: `@type: Taxon` med `name`, `alternateName` (andra språkets namn och det vetenskapliga namnet), `taxonRank: species` och `sameAs` till artens Wikidatasida
  - `primaryImageOfPage`: `ImageObject` med `contentUrl`, `license`, `acquireLicensePage` (Commons-sidan), `creator` och `creditText`

  Licensdatan ger fotona licensmärket i Google Bilder.
- **Gruppsida och ingångssida:** `BreadcrumbList` plus `CollectionPage` med en `ItemList` över artsidorna.
- Inget i JSON-LD får säga mer än sidan visar. `check-seo.mjs` kontrollerar det för namn, brödsmulor och antal.

## 10. SEO-reglerna som kod

**Titlar** (40 till 60 tecken, kontrolleras):

| Sidtyp | Mall | Reservmall om mallen blir över 60 |
|---|---|---|
| Art SV | `{Namn}: kännetecken, läte och foton \| Birdy` | `{Namn}: kännetecken och läte \| Birdy` |
| Art EN | `{Name}: identification, song and photos \| Birdy` | `{Name}: identification \| Birdy` |
| Grupp SV | `{Grupp}: {n} arter med foton och kännetecken \| Birdy` | `{Grupp}: arter och kännetecken \| Birdy` |
| Grupp EN | `{Group}: {n} species with photos and ID tips \| Birdy` | `{Group}: species and ID tips \| Birdy` |
| Ingång SV | `Fåglar i Sverige och Europa: {n} arter med foton \| Birdy` | |
| Ingång EN | `Birds of Sweden and Europe: {n} species with photos \| Birdy` | |

Svenska artmallen ryms för alla 180 (40 till 58 tecken). Engelska artmallen blir för lång för 25 arter, som då får reservmallen. Vid `n = 1` står det "1 art" respektive "1 species", i titlar och descriptions.

**Meta description:** artens `metaDescription` ur pipelinen. För grupper och ingångssidan finns mallar i bilaga A. Alltid 120 till 155 tecken.

**`scripts/check-seo.mjs`** körs på den byggda sajten (`dist/`) och failar med en lista över alla fel. Det hakas på `npm run build` via ett nytt skript `npm run check` som kör build, `check-seo`, `test:i18n` och `test:no-dashes`. För **nya sidor** (allt under `/species/` och `/sv/arter/`) gäller:

1. Titel 40 till 60 tecken, unik på sajten.
2. Meta description 120 till 155 tecken, unik.
3. Exakt en h1. Rubrikerna hoppar inte nivåer.
4. Canonical pekar på sidan själv. Hreflang finns åt båda hållen och målsidan finns i `dist/`. x-default pekar på den engelska sidan.
5. Sidan finns i sitemapen, med `lastmod` från artens `generatedAt`, om den inte har `noindex`. Sidor med `noindex` finns inte i sitemapen.
6. Alla `<img>` har `alt`, `width` och `height`.
7. JSON-LD går att tolka, `BreadcrumbList` matchar de synliga brödsmulorna och `ItemList` har lika många poster som sidan visar.
8. Varje foto som visas har en creditrad, och sidan har Wikipediaraden.

För **alla sidor** på sajten, även de befintliga:

9. Inga interna länkar som leder till en sida som inte finns i `dist/`.
10. Exakt en h1, och alla `<img>` har `alt`.

**`check-no-dashes.mjs`** utökas till `src/data/species/*.json` och `src/data/species-groups.json`. **`check-i18n-parity.mjs`** täcker de nya nycklarna i `copy.{en,sv}.json`.

## 11. Tester och QA

**Python** (`uv run pytest` i `tools/content-pipeline`): tester för varje kontroll i avsnitt 7 (inklusive citatmatchning med olika blanksteg och citattecken), HTML-tvätten av fotografnamn, licenstabellen, slug-reglerna och att en fil med `review: "approved"` inte skrivs över.

**Webben:**

- Bygget failar på schemafel i artdatan (zod-schema i `content.config.ts`) och på slug-krockar.
- Playwright (`tests/species.spec.ts`), på SV och EN:
  - ingångssidan: sökningen filtrerar ("talg" visar Talgoxe), `?q=` fyller i fältet, hela listan visas utan JavaScript
  - kategoriraden: aktiv chip på gruppsidan, sveps i sidled i 390 px
  - artsidan: vänsterspalten är sticky på 1440 px, ordningen i mobilen är h1, foto, fakta och ingress, ingen sidledsscroll i 360, 390 och 430 px, språkbytet leder till samma art
  - sidfoten: Arter-kolumnen och raden Vanliga arter finns på startsidan och bloggen, och alla länkar ger status 200
- Skärmdumpar SV och EN i 390 och 1440 px av ingångssidan, en gruppsida (Ugglor) och tre artsidor (Talgoxe, en art med marginalanteckning och en utan extrafoto), jämförda med mockuperna.
- **Lighthouse** på Talgoxe och ingångssidan i mobil: mål 90 eller mer på Performance, Accessibility, Best Practices och SEO. Resultatet sparas som underlag till AlbIT-caset.

## 12. Mätning och triggrar

- **Baslinje före go-live:** Albin exporterar Search Consoles resultat (klick, visningar, indexerade sidor) för hela birdy.community de senaste 3 månaderna. Siffrorna sparas i `docs/superpowers/research/<datum>-artsidor-baslinje.md`, där datumet är dagen baslinjen tas. Det blir startpunkten för AlbIT-caset.
- **UTM på Play-länken** i approtan och på gruppsidorna: `https://play.google.com/store/apps/details?id=se.birdy.android&referrer=utm_source%3Dbirdy.community%26utm_medium%3Dspecies%26utm_campaign%3D{slug}`. Play Console visar förvärv per källa. Ingen spårning läggs i appen, så integritetslöftet påverkas inte.
- **Vercel Analytics** (redan på sajten) visar sidvisningar per adress.
- **Efter go-live:** sitemapen skickas in i Search Console och indexering begärs för ingångssidorna och de tolv vanliga arterna.
- **Triggrar:**
  1. **Efter cirka 6 veckor:** har minst hälften av artsidorna indexerats och fått visningar går nästa omgång arter till granskning och körs genom pipelinen.
  2. **Efter 12 veckor utan visningar att tala om:** vi utreder indexering, titlar och innehåll innan fler sidor byggs.
  3. **Efter 6 och 12 veckor:** mätvärdena skrivs in i baslinjefilen och blir underlag för AlbIT-caset.

## 13. Faser och beroenden

1. **Fas 1, texter och bilder** (på `main`, rör ingen befintlig webbkod):
   - pipelinesteget `web` med tester
   - körning för de 180 arterna
   - rapporten, Albins granskning och gruppernas ingresser

   Filerna hamnar i nya mappar (`website/src/data/species/`, `website/src/assets/species/`) som inget på sajten läser än, så 1.3-grenen påverkas inte. **Startar efter 1.3.0-releasen.**
2. **Fas 2, sidorna** (grenen `website/artsidor` från `main`, med Vercel-förhandsvisning):
   - komponenter och routes
   - Layout, meny, sidfot och startsidans länk
   - `check-seo.mjs` och testerna

   **Startar först när `website/1.3-lyft` är ihopslagen och live.** Slås ihop när QA i avsnitt 11 är grön och Albin har godkänt förhandsvisningen.
3. **Fas 3, mätning:** baslinjen tas före sammanslagningen i fas 2. Sitemap och indexering sker samma dag som sammanslagningen, och triggrarna följs sedan enligt avsnitt 12.

## 14. Risker

- **Google kan se sidorna som massproducerat innehåll.** Motmedel: en pilot på 180 i stället för 839, fast struktur med egna fakta och foton, källcitat, Albins stickprov och triggrar innan nästa omgång.
- **Faktafel från modellen.** Motmedel: citatkravet för storlek och status, ordlistekontrollen och "Hittade du ett fel?" på varje sida.
- **Licenser.** Motmedel: creditrad per foto med länkad licens, Wikipediaraden med CC BY-SA och fotona oförändrade. `check-seo.mjs` failar om en credit saknas.
- **Byggtiden på Vercel** växer med cirka 360 foton i flera storlekar. Motmedel: fotona är redan nedskalade i pipelinen. Blir bygget för långsamt sänks antalet storlekar i `widths`.
- **Namnbyten ändrar adresser.** Sällsynt. Om det händer läggs en omdirigering i `vercel.json`.

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
| Grupp, description | {Grupp}: {n} arter med foton, kännetecken och läten. Lär dig skilja dem åt i fält, och känn igen dem på plats med appen Birdy. | {Group}: {n} species with photos, field marks and calls. Learn to tell them apart, and identify them on the spot with the Birdy app. |
| Grupp, rubrik för arterna | Arterna | The species |
| Fakta: vetenskapligt namn | Vetenskapligt namn | Scientific name |
| Fakta: familj | Familj | Family |
| Fakta: i Sverige | I Sverige | In Sweden |
| Fakta: storlek | Storlek | Size |
| Fakta: rödlista | Global rödlista (IUCN) | Global Red List (IUCN) |
| Status `resident` | Stannfågel | Resident all year |
| Status `breeding_migrant` | Flyttfågel, häckar här | Summer visitor, breeds here |
| Status `passage` | Ses under flyttningen | Seen on migration |
| Status `winter_visitor` | Vintergäst | Winter visitor |
| Status `rare_visitor` | Sällsynt gäst | Rare visitor |
| Status `absent` | Förekommer inte | Does not occur |
| IUCN LC / NT / VU / EN / CR | Livskraftig / Nära hotad / Sårbar / Starkt hotad / Akut hotad | Least concern / Near threatened / Vulnerable / Endangered / Critically endangered |
| Rubrik kännetecken | Så känner du igen den | How to recognise it |
| Rubrik läte | Läte | Call and song |
| Rubrik var och när | Var och när | Where and when |
| Rubrik fler (familj) | Fler {familj i gemener} | More in the {Latin} family |
| Rubrik fler (grupp) | Fler {grupp i gemener} | More {group in lowercase} |
| Appruta, rubrik | Osäker på vad du ser? | Not sure what you are seeing? |
| Appruta, text | Birdy känner igen {art i gemener} på foto eller läte, direkt i telefonen och utan täckning. | Birdy identifies the {name} from a photo or its song, right on your phone and without signal. |
| Fotocredit | Foto: {fotograf}, {licens}, via Wikimedia Commons | Photo: {photographer}, {license}, via Wikimedia Commons |
| Textcredit | Texten bygger på artiklarna om {art i gemener} på svenska och engelska Wikipedia och får delas under CC BY-SA 4.0. | The text is based on the articles about the {name} on Swedish and English Wikipedia and may be shared under CC BY-SA 4.0. |
| Fel i texten | Hittade du ett fel? Skriv till oss. | Found a mistake? Write to us. |
| Sidfot, kolumn | Arter | Species |
| Sidfot, sista länk | Alla arter från A till Ö | All species A to Z |
| Sidfot, rad | Vanliga arter | Common species |
| Startsidan, länk | Bläddra bland arterna | Browse the species |

Gruppnamnen är appens (`archive_chip_*`). Accentord i rubriker står inom `*…*` som i 1.3-specen. Inga tankstreck i någon text.

## Bilaga B: förbjudna fraser (startlista)

**Svenska:** anmärkningsvärd, anmärkningsvärt, fascinerande, spännande, magnifik, fantastisk, unik, en sann, en riktig pärla, inte bara, utan också, i hjärtat av, en symbol för, värd att upptäcka, kort sagt, sammanfattningsvis, det är värt att notera, ett nöje att.

**Engelska:** remarkable, fascinating, stunning, breathtaking, magnificent, boasts, nestled, a true, a testament to, not only, but also, in the heart of, it is worth noting, delve, tapestry, vibrant, iconic, truly.

Listan ligger i `prompts/web-banned-phrases.txt` och får växa när Albins granskning hittar nya mönster.

## Bilaga C: schema för `website/src/data/species/<QID>.json`

```json
{
  "qid": "Q25485",
  "status": "ok",
  "review": "unreviewed",
  "slug": { "sv": "talgoxe", "en": "great-tit" },
  "names": { "sv": "Talgoxe", "en": "Great Tit", "scientific": "Parus major" },
  "family": { "latin": "Paridae", "sv": "Mesar" },
  "group": "songbirds",
  "iucn": "LC",
  "marginalia": { "sv": "Talgoxen söker frön i barren mitt i vintern.", "en": "The great tit forages among pine needles deep in winter." },
  "images": [
    {
      "role": "hero",
      "file": "Q25485/hero.webp",
      "width": 1600, "height": 1067,
      "author": "Hobbyfotowiki",
      "license": "CC0", "licenseUrl": null,
      "sourceUrl": "https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg"
    }
  ],
  "wikipedia": {
    "sv": { "title": "Talgoxe", "revision": "59064377" },
    "en": { "title": "Great tit", "revision": "1334945574" }
  },
  "text": {
    "sv": {
      "lead": "…", "fieldMarks": ["…", "…", "…"], "voice": "…", "whereWhen": "…",
      "metaDescription": "…",
      "facts": {
        "size": { "value": "Cirka 14 cm", "quote": "…" },
        "swedenStatus": { "value": "resident", "quote": "…" }
      }
    },
    "en": { "…": "samma fält" }
  },
  "generated": { "model": "…", "prompt": "web-v1", "effort": "high", "at": "2026-10-…" },
  "errors": []
}
```

`status` är `ok` eller `failed` (då fylls `errors` och sidan byggs inte). `review` är `unreviewed` eller `approved`. `facts.size` och `facts.swedenStatus` kan vara `null` (avsnitt 7), och `marginalia` saknas för de flesta arter. Sajtens zod-schema speglar exakt de här fälten.

**Tillägg under fas 1 (2026-09-26):** en art med `status: "failed"` har alltid `"text": null`. Texten som modellen skrev men som inte klarade kontrollerna ligger i stället under `"rejectedText"` (samma form som `text`, eller `null` om modellen inte gav något svar), så att Albin kan läsa den utan att sajtens längdregler ser den. `ok`-poster har ingen `rejectedText`. `generated.effort` är den tankenivå (`low`, `medium` eller `high`) som körningen använde. Sajtens zod-schema läser inte `rejectedText` eller `effort` och ska därför inte göras `.strict()`.
