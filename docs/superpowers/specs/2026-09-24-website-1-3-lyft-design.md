# birdy.community i appens nya look (släpps med 1.3.0) — design

> **Datum:** 2026-09-24 (Windows). **Beslutat med Albin och hans kompanjon** i två brainstorm-pass samma dag. Det första passet stängdes av minnesbrist och det andra avbröts precis innan specen skulle skrivas. Besluten nedan är hämtade ur båda passen och ur de godkända mockuperna.
> **Mål:** Webben ska se ut och kännas som appen 1.3.0 (riktning "Redaktionell", paletten "Mossa, rost & mässing"), säga exakt det appen gör, vara mjukare att bläddra i, få tillbaka Birdy-fågeln och en rödhake som lever i fotot, och bloggen ska få bilder och lugnare typografi. Sidan går live samma dag som 1.3.0 når produktion.
> **Mockups (godkända):** `docs/superpowers/specs/assets/2026-09-24-website-1-3-lyft/` — `startsida-v5.html` (hela startsidan, dator och mobil) och `blogg.html` (listan och inlägget). Öppna filerna direkt i en webbläsare, bilderna ligger bredvid. `rodhake-utklipp/` innehåller skripten, masken och det förlustfria rödhakelagret.
> **Hör ihop med:** `2026-09-24-v1-3-release-design.md` (appens palett, riktning och vad Premium innehåller).

---

## 1. Utgångsläge (verifierat 2026-09-24)

- **Live idag:** dagens Codex-pass (commits `46b80fbb`, `4483d96f`, `d12efcf1`): varm orange palett, hero med rödhake, galleri med Play-kort, blogg med ett inlägg.
- **Det som var fel** (genomgång i första passet):
  - Paletten är rost och aprikos utan mossgrönt, alltså den palett som valdes bort för appen.
  - De svenska skärmbilderna i galleriet visar appen på engelska, och Play-korten har siffror som "01/08".
  - Galleriet klipper bilderna i nederkant, och de sista bilderna laddas först när man bläddrar fram till dem. Därför känns det ryckigt.
  - Påståenden som inte stämmer med 1.3.0: ljud-ID "håll i 3 sekunder" (1.3.0 spelar in fritt i upp till 60 s och stannar själv), iPhone "slutet av september", Premium-listan saknar kartan och "Helt offline" gäller inte kartan.
  - Bloggen saknar bilder och har mycket stora rubriker.
  - Birdy-fågeln (kopparfågeln som steg upp i första vyn och flög iväg vid scroll) försvann när Codex gjorde om sidan.
- **Teknik:** Astro 5 och Tailwind v4 (i praktiken CSS i komponenterna). Texterna ligger i `src/content/copy.{en,sv}.json` med paritets- och accuracy-vakt (`npm run test:i18n`, `npm run test:no-accuracy`). Playwright-testerna (`npm run test:smoke`) körs lokalt, CI täcker inte webben. Typsnitten är självhostade i `public/fonts/`. Kartan är MapLibre med MapTiler (domänlåst nyckel i Vercel, statisk reservbild utan nyckel). Vercel publicerar från `main`.

## 2. Beslut

| # | Fråga | Beslut |
|---|---|---|
| 1 | Riktning | **A "Fotot först":** rödhakefotot i mossgrön ton och en telefon som visar en träff på samma rödhake. Samma formspråk som appens nya skärmar. |
| 2 | Palett | Appens palett "Mossa, rost & mässing" (avsnitt 4.1). Den orange webbpaletten ersätts helt. |
| 3 | Karusellens skärmar | **A: byggda i kod** i appens nya look, inte skärmdumpar. Skarpa, rätt språk på SV och EN, lätta att ladda och kan ha små rörelser. |
| 4 | Startsidans ordning | Hero → Så funkar det → Fältboken → Appen (karusell) → Uppslagsverket med karta → Premium → Integritet → Fältanteckningar → Frågor → Ta med Birdy ut i fält → sidfot. |
| 5 | Birdy-fågeln | **A "Fågeln lyfter från rubriken":** landar ovanför rubriken när sidan öppnas, lyfter och flyger upp mot rödhaken när man scrollar och kommer tillbaka när man scrollar upp. Aprikos på mossgrönt. Syns direkt, även på mobil. |
| 6 | Levande rödhake | **Alternativ 2, rörelse i ren kod** (ingen AI-video, ingen Higgsfield). Rödhaken är ett eget lager ovanpå en bakgrundsplatta där den är borttagen. Den andas, nigar ungefär var åttonde sekund, blinkar ibland, daggdropparna glittrar, den nigar när Birdy-fågeln flyger förbi och fotot rör sig lite långsammare än texten. |
| 7 | Bloggen | Listan och inlägget enligt `blogg.html`. Varje inlägg får en egen bild, och bildfältet blir obligatoriskt. |
| 8 | Texter | Omskrivna och kontrollerade mot appen och integritetspolicyn. Inga tankstreck, inget datum för iPhone, ljud-ID beskrivs som det fungerar i 1.3.0, kartan är med i Premium och "fungerar utan täckning" ersätter "helt offline". |
| 9 | Publicering | Sidan visar 1.3.0-looken och 1.3.0-beteendet (ljud-ID), så den **går live när 1.3.0 (vC129) finns i produktion**. Fram till dess ligger den som förhandsvisning (avsnitt 3). |

**Tre saker som specen bestämmer utöver mockupen** (ändra i granskningen om ni vill något annat):

1. **Telefonen i heron flyttas så att den inte täcker rödhaken.** I mockupen täcker den rödhakens orange bröst på alla datorbredder, alltså den del som andas. Frågan ställdes i passet och svaret blev "Kanon!", vilket specen tolkar som ja. Reglerna står i 5.2.
2. **Dagens fågel beskrivs som den fungerar.** Mockupen sa "finns nära dig nu", men Dagens fågel bygger på säsongen i Sverige och inte på var du är. Ny text: "här just nu" (appens egen formulering) och "Dagens fågel visar en art som finns i Sverige just nu."
3. **Menyn följer med när man scrollar**, som idag. Den är genomskinlig ovanpå fotot och blir mossgrön när man scrollat förbi första vyn. Mockupen visar bara läget överst.

## 3. Publicering

- Allt webbarbete görs på grenen **`website/1.3-lyft`** från `main`, i en egen worktree med kort sökväg (till exempel `C:\w\birdy-web`). Huvudmappen står då kvar på `main`, och appens 1.3.0-arbete i `C:\w\birdy-130` påverkas inte.
- Vercel bygger en förhandsvisning för varje push, och länken delas med Albin och kompanjonen. Första pushen visar om Vercel faktiskt bygger förhandsvisningar för grenar; gör den inte det slår Albin på det i Vercel. Kräver previewn Vercel-inloggning skapar Albin en delningslänk i Vercel.
- MapTiler-nyckeln är låst till birdy.community, så i previewn visas kartans reservbild. Det är väntat.
- **Go-live:** när vC129 (1.3.0) syns som aktuell version på Google Play slås grenen ihop med `main` samma dag. Före sammanslagningen tas `main` in i grenen och hela QA:n i avsnitt 10 körs på previewn. Efter sammanslagningen kontrolleras birdy.community, /sv/, /blog/ och /sv/blog/why-birdy/ live, och indexering av / och /sv/ begärs i Search Console.
- **Om 1.3.0 slirar förbi 30 september:** en liten separat textfix på nuvarande sajt på `main` tar bort iPhone-datumet ur frågan om iPhone, så att ingen gammal uppgift ligger ute. Inget annat ändras på den gamla sajten.
- **Mål:** klart i förhandsvisning före appens release (cirka 30 september).

## 4. Grund: färger, typsnitt och gemensamma delar

### 4.1 Färger

Definieras på ett ställe (`src/styles/tokens.css`) och speglas i Tailwinds `@theme` i `global.css`. De gamla namnen (`--color-orange`, `--color-rust-shadow` med flera) försvinner, och de sidor som finns kvar (juridik) byter till de nya namnen.

| Token | Hex | Roll |
|---|---|---|
| `--paper` | `#F6EFE2` | sidbakgrund |
| `--card` | `#FFFAF1` | kort och FAQ-ytan |
| `--ink` | `#26301F` | brödtext och rubriker på papper |
| `--muted` | `#5B6350` | sekundär text |
| `--line` | `#DFD2BA` | hårlinjer |
| `--rust` / `--rust-deep` | `#9A4526` / `#72301A` | knappar, stämplar, accent på papper |
| `--apricot` | `#F2B27A` | accent på mossgrönt och Birdy-fågeln |
| `--moss` / `--moss-2` / `--moss-deep` | `#1F2A19` / `#2B3A23` / `#172013` | hero, karusell, Premium, sidfot |
| `--brass` / `--brass-hi` | `#B8893A` / `#E2C07E` | Premium |
| `--brass-ink` | `#241B0C` | text på mässing (sigill, Premium-märken) |
| `--cream` | `#FFF8EE` | text på mossgrönt |
| `--navy` | `#1F3A5F` | en stämpelvariant, som i appen |

Kontrast (uträknad): muted på papper 5,5:1, rost på papper 5,6:1, aprikos på mossa 8,1:1, ljus mässing på mossa 8,6:1 och mörk text på mässing 5,4:1. Allt klarar WCAG AA även för liten text. Krämfärgad text på mässing (som i mockupens sigill) ger bara 3:1, så text på mässing är alltid mörk, precis som i appen. `theme-color` blir `#1F2A19`.

### 4.2 Typsnitt

- **DM Serif Display** (regular och kursiv) för rubriker. Accentordet i en rubrik (`*ord*` i texterna) blir kursivt: rost på papper, aprikos på mossgrönt och ljus mässing i Premium. Den befintliga `JournalHeadline` används, med färgerna via `--jh-ink` och `--jh-accent`.
- **Inter** för brödtext, i de vikter som redan finns (400 och 600). Mockupens 500 och 700 mappas dit.
- **Caveat** för handskrivna detaljer: ordmärket "Birdy.", marginalanteckningar och bildtexter.
- Allt är självhostat som idag, inga Google Fonts (mockupen använder dem bara lokalt). Bara typsnitt som syns i första vyn förladdas.

### 4.3 Gemensamma delar

- **Kicker:** liten versal rad med ett kort streck före. Ersätter `EyebrowLabel` på de nya sidorna.
- **Stämpel (sigill):** rost, mässing, navy, tom och mini, i samma form som i appen.
- **Telefonram (`PhoneFrame`):** statusrad och valfri flikrad, ritad i designstorleken 250×520 och skalbar. Används av heron och karusellen.
- **Knappar och märken:** rostgradient för "Hämta appen". Det officiella Google Play-märket (befintliga `play-badge-{sv,en}.png`) används, inte mockupens CSS-kopia. App Store-märket är nedtonat och ingen länk ("Snart på App Store"), utan den handskrivna "snart!"-lappen.
- **Kort:** kortfärg med hårlinje som lyfter lite vid hover.
- Den befintliga scroll-reveal-effekten (`data-reveal`) och uppräkningen av siffror behålls, med samma stöd för minskad rörelse.

## 5. Startsidan, sektion för sektion

Samma sida på `/` (engelska) och `/sv/` (svenska). Sektions-ID:n är språkneutrala: `how-it-works`, `journal`, `app`, `guide`, `premium`, `privacy`, `field-notes`, `faq` och `download`. Alla interna länkar i meny, sidfot och blogginlägg uppdateras. `#download`, `#faq`, `#privacy` och `#premium` behåller sina nuvarande namn. Alla texter står i bilaga A.

### 5.1 Meny

- Ordmärket "Birdy." (Caveat) till vänster. Länkar: Så funkar det, Appen, Premium, Fältanteckningar (till bloggen), EN/SV och knappen "Hämta appen" (till `#download`).
- **Mobil:** hamburgermeny med den befintliga logiken (`aria-expanded`, Esc stänger). Där finns även Uppslagsverket, Integritet och Frågor.
- Genomskinlig ovanpå heron och inläggsfotot överst, mossgrön bakgrund när man scrollat förbi första vyn, och den följer med (sticky). På blogglistan och juridiksidorna är den alltid mossgrön.

### 5.2 Hero

Innehåll enligt mockupen: kicker, rubriken "Känn igen fågeln. / Bevara stunden.", ingress, Play- och App Store-märkena, en metarad (839 europeiska arter · Fungerar utan täckning · Inget konto) och telefonen med träffskärmen för rödhaken.

**Levande foto**

- Bakgrunden byggs av två lager ur webbens befintliga rödhakebild (`src/assets/hero-robin.webp`, 1672×941, AI-genererad): en platta där rödhaken är borttagen och ifylld, och rödhaken som eget lager (419×502 med alfa, cirka 21 KB).
- Geometrin finns i `rodhake-utklipp/layers.json`: rutan börjar på 63,038 % / 15,409 % och är 25,06 % × 53,348 % av bilden, och rotationspunkten (fötterna) ligger på 33,93 % / 98,61 % av rutan. Plattan tas fram med `layers.py` och masken (WebP kvalitet 95). Därefter lägger `restore-feet.mjs` tillbaka originalbilden i ett band runt fötterna, så att tårna griper mossan i stället för att sväva över en suddig fläck; fötterna är rotationspunkten och rör sig mindre än 2 px. Astro optimerar plattan. Lagret levereras som WebP.
- Lagren hålls ihop med ren CSS (samma bildförhållande och samma beskärning), så rödhaken sitter rätt även utan JavaScript.
- Rörelserna från mockupen: andning (3,8 s), nigning (var 8:e sekund), blinkning (var 5,3:e sekund, ögonlocket är fjädrarna ovanför ögat), fyra daggdroppar som glittrar i tur och ordning, parallax (fotot rör sig med 0,14 av scrollen) och en extra nigning när Birdy-fågeln passerar.
- Telefonens foto är ett förbeskuret utsnitt av samma rödhake, inte hela bilden uppskalad.

**Birdy-fågeln**

- Appens fågel (`composeApp/src/commonMain/composeResources/files/branding/hero_bird.png`, beskuren till 237×229) används som mask i aprikos.
- Den landar ovanför kickern (1,2 s). Vid scroll flyger den upp mot rödhaken under de första 320 pixlarna, krymper och tonar ut, och den kommer tillbaka när man scrollar upp.
- 62 px på dator och 52 px på mobil.

**Komposition** (krav, eftersom mockupen bara ritades i 980 och 375 px bredd)

- Telefonen överlappar aldrig rödhakens ruta. Från cirka 1280 px står den i luckan mellan texten och rödhaken, till vänster om näbben. Där den inte får plats (cirka 761–1279 px) ligger den under texten som på mobilen och sticker ner i nästa sektion.
- Rödhakens huvud hamnar aldrig under menyn. Från cirka 1600 px förankras fotot så att rödhaken inte växer ur bild (i mockupen blir den för stor i 1920 px).
- Rubriken bryts på högst två rader på dator (1024 px och bredare).
- Mobil (upp till 760 px) följer mockupen: fotot överst med rödhaken synlig, sedan fågeln och texten, och telefonen sist.
- Playwright mäter att telefonens och rödhakens rutor inte skär varandra i 1024, 1280, 1440 och 1920 px, och skärmdumpar i samma bredder granskas.

### 5.3 Så funkar det (`#how-it-works`)

Rubriken "Tre sätt att fånga." med ingress till vänster och tre rader till höger: Live med kameran, Från ett foto och Lyssna på lätet (med etiketten "Gratis"). Ersätter dagens Loop- och Listen-sektioner.

### 5.4 Fältboken (`#journal`)

Planschen (ladusvala, "första för året", stämpel Nr 31), texten och tre fakta: 34 märken att samla, 27 av dem gratis och 0 konton.

### 5.5 Appen (`#app`): karusellen

- **Åtta telefoner byggda i kod:** Identifiera, Träff, Ljud-ID (gratis), Fältboken, Artprofil, Märken, Fynd-kartan (Premium) och Säsong (Premium). Innehåll och exempeldata följer mockupen, och artfakta är kontrollerade mot appens artdatabas. Den engelska versionen använder appens engelska ord där de finns (Bird of the day, Trophy room, Finds map, Caught today) och engelska artnamn.
- **Rörelse enligt mockupen:** scroll-snap, grannarna tonas ned och lutar, texten under byter mjukt, en progressräls i stället för prickar, pilknappar, piltangenter, klick på en granne och musdrag med fart. På pekskärm är det vanlig svepning.
- **Laddning:** bilderna i skärmarna är små och laddas alla på en gång när sektionen närmar sig, så inget hoppar in under bläddringen.
- **Tillgänglighet:** telefonernas innehåll är dekor. Varje telefon har `role="img"` och en kort beskrivning, och texten under läses upp när den byts.
- Ersätter `Glimpse.astro` och Play-korten.

### 5.6 Uppslagsverket (`#guide`)

- Rubriken "Europas fåglar, i fickan.", ingress och siffrorna 839 arter, 34 märken och 0 konton.
- Den interaktiva kartan (befintlig MapLibre-logik) färgas om i fältbokens färger: papper, bläck och rost. Länderna Birdy täcker fylls i rost, och nålarna blir rostiga sigill med en mossgrön fågel.
- Reservbilden görs om i samma färger och ritas ur `public/coverage/coverage-europe.geojson`, så ingen kartnyckel behövs.
- Ersätter Inside och Coverage.

### 5.7 Premium (`#premium`)

Mossgrönt med mässing och ett mässingssigill. Först det som är gratis ("Alltid gratis: …"), sedan de fyra sakerna: Fynd-kartan, Fältdagboken som PDF, Säsongsstatistik och 7 premiummärken. Inga priser, bara raden "Köp och priser sköts i appen, via Google Play." Innehållet stämmer med appens Premium i 1.3.0.

### 5.8 Integritet (`#privacy`)

Ljus sektion med rubriken "AI:n bor i telefonen.", tre kolumner (Inget konto, Stannar i telefonen, Plats bara om du vill) och en länk till integritetspolicyn.

### 5.9 Fältanteckningar (`#field-notes`)

Det senaste inlägget som ett stort fotokort (bild, kategori, rubrik, ingress, datum och lästid) och länken "Alla fältanteckningar". Sektionen döljs om det inte finns några inlägg, som idag.

### 5.10 Frågor (`#faq`)

Fem frågor enligt mockupen på en yta i kortfärg. Svaren öppnas mjukt i webbläsare som klarar det och direkt i övriga. FAQPage-datan för Google byggs av samma lista.

### 5.11 Ta med Birdy ut i fält (`#download`) och sidfot

- Ett foto av en skäggmes i bakgrunden med mossgrön toning, rubriken, raden "Gratis att ladda ner. Inget konto. Fungerar utan täckning." och märkena.
- Sidfoten i mörk mossa: ordmärket i aprikos och "Känn igen fågeln. Bevara stunden.", kolumnerna Utforska, Läs (Fältanteckningar, Varför Birdy finns, Skriv till oss) och Information (Integritetspolicy, Villkor, Datasäkerhet, språkbyte), och sist raden "© 2026 Birdy · Skapad i Sverige" med "Byggd av AlbIT".
- **Tillägg (2026-09-24 kväll, från albit.se-sessionen på Albins uppdrag; länkkartan finns i albit.se-repot under `docs/superpowers/specs/assets/2026-09-24-produktsidor/lankkarta.html`):**
  - "AlbIT" stavas med stort A. Länken går till Birdys produktsida på albit.se, inte till startsidan: `https://www.albit.se/produkter/birdy/` på svenska sidor och `https://www.albit.se/en/products/birdy/` på engelska.
  - Under taggraden i sidfoten ligger blocket "Från samma verkstad: LoopLead" / "From the same workshop: LoopLead". Det har LoopLeads märke (grön rundad ruta med en båge mellan två prickar) och raden "HR-verktyg för chefer i växande bolag" / "An HR tool for managers in growing companies", och länkar till `https://looplead.se/`.
  - Alla länkar är vanliga follow-länkar med varumärket som ankartext, en gång per sidfot.

## 6. Fältanteckningarna (bloggen)

**Innehållsmodell** (`src/content.config.ts`)

- Nya fält: `image` (obligatoriskt, Astros `image()`), `imageAlt` (obligatoriskt), `imageCaption` (valfri bildtext, till exempel "Rödhake, Erithacus rubecula") och `imagePosition` (valfri, var bilden ska centreras).
- Bygget stoppar om bild eller alt-text saknas.
- Lästiden räknas fram ur texten: 200 ord per minut, minst 1 minut.
- Inläggens bilder ligger i `src/assets/photos/` tillsammans med startsidans foton, och varje bilds källa och licens skrivs in i `SOURCES.md` där.
- `BLOG.md` uppdateras med de nya fälten och regeln om källor.

**Listan** (`/blog/` och `/sv/blog/`)

- Mossgrön rubrikrad ("Anteckningar från fältet.") och det senaste inlägget som ett stort fotokort som överlappar kanten.
- Övriga inlägg visas som kort i tre kolumner, en kolumn på mobil. Mockupens nedtonade exempelkort följer inte med.
- Dagens rad "Vill du prova Birdy själv?" tas bort.

**Inlägget**

- Fotot ligger överst som i appens artprofil. Kategori, rubrik, datum och lästid står på fotot, och på dator står bildtexten handskriven i hörnet.
- Texten ligger på ett pappersark som glider upp över fotot, i en smalare spalt med lugnare rubriker. Första stycket är ingress.
- Citat skrivs med `>` i markdown och visas som ett stort kursivt citat med en rostlinje.
- I slutet finns en mossgrön ruta med "Ta med Birdy ut i fält" och Play-märket, och sedan länkarna "← Alla fältanteckningar" och "Se hur Birdy fungerar".

**Delningsbild:** inläggets bild beskärs till 1200×630 och används som `og:image` och i BlogPosting-datan för inlägget, med alt-texten.

**Tillägg (2026-09-24 kväll, samma källa som sidfotens tillägg):**
- **Författarrad:** under rubriken i inläggets foto står "Albin Abrahamsson, AlbIT". Raden länkar till produktsidan på albit.se, med samma adress per språk som i sidfoten.
- **Strukturerad data:** BlogPosting får `author` = Person "Albin Abrahamsson" (`https://www.albit.se/om-albin/`) och `publisher` = Organization "AlbIT AB" (`https://www.albit.se/`). Appens MobileApplication på startsidorna får `creator` = samma organisation.
- **Webbplatskartan:** inläggens adresser får `lastmod` (inläggets datum), så att albit.se kan hämta nya inlägg därifrån.

**"Varför Birdy finns" (SV och EN):** får rödhakefotot (Q25334, public domain) som bild. Meningen "Identifieringen ger dig ett namn. Dagboken hjälper dig att behålla fyndet." blir ett citat, och länkarna pekar på `#how-it-works` och `#download`.

## 7. Övriga sidor och bilder

- **Juridiksidorna** (`/legal/…`) byter till de nya färgerna och får den mossgröna menyn. Innehåll och upplägg ändras inte.
- **Delningsbilderna** `og-field-{en,sv}.png` görs om i de nya färgerna (`tools/generate-og.mjs`: mossgrön toning och aprikos accent).
- **Foton:** de sex fotona (fem på startsidan och rödhaken till blogginlägget) hämtas ur appens planschfoton i `asset-pack/src/main/assets/images/<QID>/hero.webp`. Alla är CC0 eller public domain (bilaga B). De läggs i `src/assets/photos/` med en `SOURCES.md` som listar art, QID, fotograf och licens.
- **Städning:** komponenter och texter som inte längre används tas bort (Loop, Glimpse, Listen, Inside, Coverage, ui-delar som blir oanvända och oanvända nycklar i copy-filerna). `src/assets/screens/` och `src/assets/slides/` ligger kvar eftersom verktygen för Play-bilderna använder dem.

## 8. Rörelse, prestanda och tillgänglighet

- **Minska rörelse:** när systeminställningen är på står allt still. Det gäller rödhaken, Birdy-fågeln (som visas på sin plats), telefonens entré, karusellens lutning, parallaxen och FAQ-animationen.
- **Utan JavaScript** syns allt innehåll och går att läsa. Rödhaken sitter rätt, och karusellen går att scrolla i sidled.
- **Det som inte syns pausas:** loopande animationer (rödhaken och ljudvågen i karusellen) stannar när sektionen är utanför skärmen.
- **Laddning:** heroplattan är sidans LCP-bild (responsiva storlekar och `fetchpriority="high"`). Rödhakelagret väger cirka 21 KB och fågelmasken cirka 16 KB. Inga nya beroenden tillkommer. Kartan laddas som idag, först när man närmar sig den.
- **Mål i Lighthouse (mobil, på previewn):** Prestanda minst 90, Tillgänglighet minst 95 och CLS under 0,05.
- **Tillgänglighet:** tangentbordet fungerar i meny och karusell, fokusringen syns (aprikos på mossgrönt, rost på papper), språkbytet har rätt `lang`, alla bilder som bär innehåll har alt-text och dekor döljs för skärmläsare.

## 9. Filer

| Fil | Ändring |
|---|---|
| `src/styles/tokens.css`, `src/styles/global.css` | nya tokens, `@theme` och fokusring |
| `src/layouts/Layout.astro` | `theme-color`, förladdade typsnitt, valfri delningsbild (`ogImage`, `ogImageAlt`) för inlägg |
| `src/components/Nav.astro`, `src/components/Footer.astro` | omskrivna |
| `src/components/Hero.astro`, nya `src/components/hero/HeroScene.astro` och `hero/BirdyBird.astro` | ny hero med levande foto och fågeln |
| nya `src/components/phone/PhoneFrame.astro` och `phone/screens/*.astro` | telefonerna i kod; träffskärmen delas mellan heron och karusellen |
| nya `HowItWorks.astro`, `JournalSection.astro`, `AppTour.astro`, `Guide.astro` och `CoverageMap.astro` | nya sektioner; kartlogiken flyttas från `Coverage.astro` |
| `Premium.astro`, `Privacy.astro`, `FieldNotesTeaser.astro`, `Faq.astro`, `ui/FaqItem.astro`, `FinalCta.astro` | omskrivna |
| `FieldNotesIndex.astro`, `FieldNoteArticle.astro`, `src/styles/article-prose.css` och ny `NoteCard.astro` | omskrivna; kortet delas av listan och startsidan |
| `src/content.config.ts`, `src/lib/field-notes.ts`, `BLOG.md` | bildfält och lästid |
| `src/content/field-notes/{sv,en}/why-birdy.md` | bild (från `src/assets/photos/`), citat och länkar |
| `src/content/copy.{sv,en}.json` | omstrukturerade texter (bilaga A) |
| `src/pages/index.astro`, `src/pages/sv/index.astro` och ny `src/components/HomePage.astro` | en gemensam startsida för båda språken med den nya sektionsordningen; sidfilerna blir tunna omslag |
| nya `src/components/ui/Icon.astro`, `Kicker.astro`, `Accent.astro` och `src/components/phone/TabBar.astro` | små gemensamma delar (ikoner, kicker, accentord, telefonernas flikrad) |
| nya `scripts/check-contrast.mjs` och `scripts/check-no-dashes.mjs` | vakter för palettens kontrast och mot tankstreck i publika texter |
| `src/layouts/LegalLayout.astro`, `src/pages/legal/index.astro`, `src/styles/legal-prose.css` | nya tokennamn och mossgrön meny |
| nya `src/assets/hero/` (platta, rödhakelager, telefonutsnitt), `src/assets/photos/` (sex foton och `SOURCES.md`) och `public/brand/birdy-bird.png` | bilder |
| `public/coverage/coverage-fallback.webp` och nytt `tools/render-coverage-fallback.mjs` | reservkartan i nya färger |
| `tools/generate-og.mjs`, `public/og-field-{en,sv}.png` | delningsbilderna |
| `tests/smoke.spec.ts`, `tests/coverage.spec.ts` och nytt `tests/home.spec.ts` | tester |
| tas bort: `Loop.astro`, `Glimpse.astro`, `Listen.astro`, `Inside.astro`, `Coverage.astro` och ui-delar som blir oanvända | städning |

## 10. Test och verifiering

**Automatiskt** (måste vara grönt före go-live)

- `npm run build`, `npm run check` (utgångsläget har ett känt typfel i `astro.config.mjs`, inga nya får tillkomma), `npm run test:i18n`, `npm run test:no-accuracy`, de nya vakterna `npm run test:contrast` och `npm run test:no-dashes`, och Playwright-testerna.
- **Uppdaterade tester:** startsidans rubriker och Play-länk (SV och EN), hreflang, blogglistan och inlägget (en h1, tre h2, `og:type=article`, `og:image` är inläggets bild, hreflang), albIT-länken i sidfoten, juridiksidorna och kartsektionen (nu `#guide`, med levande karta eller reservbild och inga fel i konsolen).
- **Nya tester:** sektionernas ordning och ID:n, karusellen har åtta skärmar och "nästa" byter text, rödhaken och karusellen står still med minskad rörelse, ingen sidledsscroll i 390 px på startsidan, blogglistan och inlägget (SV och EN), telefonen överlappar inte rödhaken i 1024, 1280, 1440 och 1920 px, och rödhakens ruta börjar under menyn i 1920 px.

**Visuellt** (agenten)

- Skärmdumpar på SV och EN i 390, 1024, 1440 och 1920 px av startsidan, blogglistan, inlägget och en juridiksida, jämförda med mockuperna.
- Lighthouse (mobil) på previewn enligt målen i avsnitt 8.
- Textkontroll: varje sakpåstående i bilaga A stämmer med appen 1.3.0 och integritetspolicyn. Kontrollen görs om mot release-grenen strax före go-live, eftersom appens texter ändras parallellt.

**Manuellt**

- Albin och kompanjonen går igenom previewn i telefon och dator före go-live.

## 11. Utanför scope

- Nya sidor (till exempel /regions/), App Store-länk (iPhone-appen är inte ute), nya blogginlägg och länkar till sociala medier.
- Ändringar i appen, i juridiktexterna och i Play Store-bilderna.
- Mörkt läge för webben.

## 12. Risker

| Risk | Hantering |
|---|---|
| 1.3.0 slirar | Sidan väntar i previewn. Efter 30 september tas iPhone-datumet bort på den gamla sajten (avsnitt 3). |
| Rödhakelagret ser ut som ett klistermärke (glorior i kanten, skärpa som inte matchar bakgrunden, fel skala i vissa bredder) | Kontrolleras i 1x och 2x i alla bredder. Blir det fel stängs blinkningen av först och därefter alla rörelser; platta och lager ser då ut som originalfotot. |
| Animationerna blir tunga på svaga telefoner | Bara transform och opacity, paus utanför skärmen och respekt för minskad rörelse. |
| Appens texter ändras i 1.3.0-arbetet | Textkontroll mot release-grenen strax före go-live. |
| Kartan i previewn visar bara reservbilden (domänlåst nyckel) | Den levande kartan kontrolleras direkt efter go-live. |

---

## Bilaga A: Texter (SV / EN)

Accentord står inom `*…*`. Inga tankstreck i publika texter (enligt `BLOG.md`).

**Meny och metarad**

| Del | SV | EN |
|---|---|---|
| Länkar | Så funkar det · Appen · Premium · Fältanteckningar | How it works · The app · Premium · Field notes |
| Endast mobilmenyn | Uppslagsverket · Integritet · Frågor | Field guide · Privacy · FAQ |
| Språk / knapp | EN / Hämta appen | SV / Get the app |

**Hero**

| Del | SV | EN |
|---|---|---|
| Kicker | Fågelguide och fältdagbok | Bird guide and field journal |
| Rubrik | Känn igen fågeln. *Bevara stunden.* | Know the bird. *Keep the moment.* |
| Ingress | Rikta kameran, välj ett foto eller låt fågeln sjunga. Birdy föreslår arten direkt i telefonen, och du sparar fyndet i din egen fältdagbok. | Point the camera, pick a photo or let the bird sing. Birdy suggests the species right on your phone, and you save the sighting in your own field journal. |
| Metarad | 839 europeiska arter · Fungerar utan täckning · Inget konto | 839 European species · Works without a signal · No account |
| Träffskärmen | Match · Fynd nr 12 / Rödhake / Säker match · 94 % / Ny art / Första i din fältbok! / Nr 12 / Lägg till en anteckning / Spara i fältboken / Inte den? Se fler förslag | Match · Find no. 12 / European Robin / Confident match · 94% / New species / First in your field journal! / No. 12 / Add a note / Save to field journal / Not it? See more suggestions |

**Så funkar det**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Så funkar det / Tre sätt att *fånga.* | How it works / Three ways to *catch it.* |
| Ingress | Samma tre ingångar som i appen. Birdy visar alltid hur säker den är, och det är du som bestämmer vad som sparas. | The same three ways in as in the app. Birdy always shows how sure it is, and you decide what gets saved. |
| Kamera | Live med kameran: Rikta mot fågeln. Birdy tittar flera gånger i sekunden och visar vilken art den ser. | Live with the camera: Point at the bird. Birdy looks several times a second and shows the species it sees. |
| Foto | Från ett foto: Välj en bild ur galleriet, beskär runt fågeln och låt Birdy titta. | From a photo: Pick a picture from your gallery, crop around the bird and let Birdy take a look. |
| Ljud | Lyssna på lätet [Gratis]: Spela in sången så föreslår Birdy arten. Ljud-ID är gratis för alla. | Listen to the song [Free]: Record the song and Birdy suggests the species. Sound ID is free for everyone. |

**Fältboken**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Fältboken / Varje fynd får *en egen sida.* | The field journal / Every sighting gets *its own page.* |
| Ingress | Spara träffen med datum, en egen anteckning och platsen om du vill. Nya arter får en präglad stämpel, och varje vecka samlas dina fynd i ett eget uppslag. | Save the match with the date, a note of your own and the place if you like. New species get an embossed stamp, and every week your sightings come together in a spread of their own. |
| Planschen | första för året / Ladusvala · 14 maj, vid ladan / Nr 31 | first of the year / Barn Swallow · 14 May, by the barn / No. 31 |
| Fakta | 34 märken att samla · 27 av dem gratis · 0 konton | 34 badges to collect · 27 of them free · 0 accounts |

**Appen (karusellen)**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Appen / Ett varv i *fältboken.* | The app / A tour of *the field journal.* |
| Ingress | Från första träffen till ditt eget uppslagsverk. Så här ser Birdy ut i telefonen. | From the first match to your own field guide. This is Birdy on your phone. |
| 1 | Identifiera / Tre sätt att fånga / Kamera, foto eller läte. Dagens fågel visar en art som finns i Sverige just nu. | Identify / Three ways to catch it / Camera, photo or song. Bird of the day shows a species you can find in Sweden right now. |
| 2 | Träff / Ärlig om hur säker den är / Birdy visar säkerheten tydligt. Är den osäker får du fler förslag att välja mellan. | Match / Honest about how sure it is / Birdy shows its confidence clearly. When it is unsure, you get more suggestions to choose from. |
| 3 | Ljud-ID · gratis / Lyssna på lätet / Spela in sången så föreslår Birdy arten. Ljudidentifieringen är gratis för alla. | Sound ID · free / Listen to the song / Record the song and Birdy suggests the species. Sound identification is free for everyone. |
| 4 | Fältboken / Din egen fältbok / Varje fynd sparas i telefonen. Nya arter får en präglad stämpel. | Field journal / Your own field journal / Every sighting is saved on your phone. New species get an embossed stamp. |
| 5 | Artprofil / Allt om arten på ett uppslag / Foton, beskrivning och när arten finns i Sverige. Du ser direkt om du har stämplat den. | Species profile / Everything about a species on one spread / Photos, a description and when the species is in Sweden. You see right away if you have stamped it. |
| 6 | Märken / Kom hem med ett märke / 34 märken att samla, 27 av dem gratis. Troférummet visar allt du har jagat ihop. | Badges / Come home with a badge / 34 badges to collect, 27 of them free. The trophy room shows everything you have tracked down. |
| 7 (Premium) | Fynd-kartan / Överallt du har varit / Dina fynd på en egen karta. Platsen sparas bara om du vill, och bara i telefonen. | Finds map / Everywhere you have been / Your sightings on a map of their own. The location is only saved if you want, and only on your phone. |
| 8 (Premium) | Säsong / Ett år i fält / Stora siffror och lugna diagram. Se mönstren i dina fynd, månad för månad. | Season / A year in the field / Big numbers and calm charts. See the patterns in your sightings, month by month. |
| Dagens fågel i skärm 1 | Stjärtmes / här just nu · Inte fångad än | Long-tailed Tit / here right now · Not caught yet |

Övrig exempeldata i telefonerna (arter, datum, platser, siffror) följer mockupen. I EN används engelska artnamn (Eurasian Bullfinch, Great Tit, Bohemian Waxwing, Common Blackbird med flera) och engelska datum ("12 Jan"). Platsnamnen är desamma.

**Uppslagsverket**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Uppslagsverket / Europas fåglar, *i fickan.* | The field guide / Europe's birds, *in your pocket.* |
| Ingress | 839 arter med foton, beskrivningar och utbredning. Allt ligger i telefonen, så guiden fungerar även där täckningen tar slut. | 839 species with photos, descriptions and range. Everything lives on your phone, so the guide keeps working where the signal ends. |
| Siffror | 839 arter · 34 märken · 0 konton | 839 species · 34 badges · 0 accounts |
| Under kartan | Birdy känner igen fåglar i hela Europa, från trädgårdens stammisar till sällsynta gäster. | Birdy recognises birds across Europe, from garden regulars to rare visitors. |
| Reservbildens alt-text | Karta över Europa där länderna som Birdy täcker är ifyllda i rost | Map of Europe with the countries Birdy covers filled in rust |

**Premium**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Birdy Premium / För dig som vill *se mer.* | Birdy Premium / For when you want *to see more.* |
| Ingress | Allt du behöver för att känna igen och spara fåglar är gratis. Premium lägger till fyra saker för dig som vill följa ditt år i fält. | Everything you need to identify and save birds is free. Premium adds four things for when you want to follow your year in the field. |
| Alltid gratis | **Alltid gratis:** identifiering med kamera, foto och ljud · fältdagboken · 839 arter · 27 märken | **Always free:** identification with camera, photo and sound · the field journal · 839 species · 27 badges |
| Köp | Köp och priser sköts i appen, via Google Play. | Purchases and prices are handled in the app, through Google Play. |
| De fyra | Fynd-kartan: Se var du har sett dina fåglar · Fältdagboken som PDF: Hela dagboken, redo att spara eller skriva ut · Säsongsstatistik: Mönster månad för månad · 7 premiummärken: Extra stämplar att jaga | Finds map: See where you have seen your birds · Field journal as PDF: The whole journal, ready to save or print · Season statistics: Patterns month by month · 7 premium badges: Extra stamps to chase |

**Integritet**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Integritet / AI:n bor i *telefonen.* | Privacy / The AI lives *on your phone.* |
| Ingress | Birdy känner igen fåglar utan att skicka dina bilder eller ljud någonstans. | Birdy identifies birds without sending your photos or sound anywhere. |
| Kolumner | Inget konto: Öppna appen och börja. Det finns inget att logga in på. · Stannar i telefonen: Bilder, ljud och fältdagbok sparas hos dig. Inget laddas upp för att identifiera en fågel. · Plats bara om du vill: Platsen sparas bara om du slår på det, och då bara i telefonen. | No account: Open the app and start. There is nothing to sign in to. · Stays on your phone: Photos, sound and your field journal are stored with you. Nothing is uploaded to identify a bird. · Location only if you want: Location is only saved if you turn it on, and then only on your phone. |
| Länk | Läs integritetspolicyn | Read the privacy policy |

**Fältanteckningar (startsidan)**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik / länk | Fältanteckningar / Från *fältboken.* / Alla fältanteckningar | Field notes / From *the field journal.* / All field notes |
| Kortets metarad | 24 SEPTEMBER 2026 · 3 MIN LÄSNING | 24 SEPTEMBER 2026 · 3 MIN READ |

**Frågor**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Frågor / Innan du *laddar ner.* | Questions / Before you *download.* |
| 1 | Fungerar Birdy utan täckning? Ja. Identifiering med kamera, foto och ljud körs i telefonen, och guiden och dagboken finns alltid med. Bara kartbilderna i Premium-kartan behöver uppkoppling. | Does Birdy work without a signal? Yes. Identification with the camera, photos and sound runs on your phone, and the guide and journal are always there. Only the map tiles in the Premium map need a connection. |
| 2 | Behöver jag ett konto? Nej. Öppna appen och börja. Din dagbok finns i telefonen. | Do I need an account? No. Open the app and start. Your journal lives on your phone. |
| 3 | Hur säker är identifieringen? Tillräckligt bra för att lära sig av, och ärlig när den är osäker. Varje träff visar hur säker Birdy är, och du bekräftar alltid innan något sparas. | How sure is the identification? Good enough to learn from, and honest when it is unsure. Every match shows how sure Birdy is, and you always confirm before anything is saved. |
| 4 | Vad kostar Birdy? Birdy är gratis att ladda ner och använda. Premium är ett tillval med kartan, PDF-export, säsongsstatistik och extra märken. Priset ser du i appen. | What does Birdy cost? Birdy is free to download and use. Premium is an optional upgrade with the map, PDF export, season statistics and extra badges. You see the price in the app. |
| 5 | Finns Birdy för iPhone? iPhone-appen är på väg. Android-appen finns att hämta nu, och länken till App Store dyker upp här när iPhone-versionen är ute. | Is Birdy available for iPhone? The iPhone app is on its way. The Android app is available now, and the App Store link will appear here when the iPhone version is out. |

**Ta med Birdy ut i fält och sidfot**

| Del | SV | EN |
|---|---|---|
| Kicker / rubrik | Birdy för Android och snart iPhone / Ta med Birdy *ut i fält.* | Birdy for Android, soon on iPhone / Take Birdy *into the field.* |
| Rad | Gratis att ladda ner. Inget konto. Fungerar utan täckning. | Free to download. No account. Works without a signal. |
| Sidfotens tagline | Känn igen fågeln. Bevara stunden. | Know the bird. Keep the moment. |
| Kolumner | Utforska: Så funkar det, Appen, Premium, Frågor · Läs: Fältanteckningar, Varför Birdy finns, Skriv till oss · Information: Integritetspolicy, Villkor, Datasäkerhet, English | Explore: How it works, The app, Premium, FAQ · Read: Field notes, Why Birdy exists, Write to us · Information: Privacy policy, Terms, Data safety, Svenska |
| Sista raden | © 2026 Birdy · Skapad i Sverige · Byggd av AlbIT | © 2026 Birdy · Made in Sweden · Built by AlbIT |
| Syskonblocket | Från samma verkstad · LoopLead · HR-verktyg för chefer i växande bolag | From the same workshop · LoopLead · An HR tool for managers in growing companies |

**Bloggen**

| Del | SV | EN |
|---|---|---|
| Listans kicker / rubrik | Fältanteckningar / Anteckningar *från fältet.* | Field notes / Notes *from the field.* |
| Listans ingress | Om fåglarna vi möter, hur Birdy fungerar och varför den är byggd som den är. | About the birds we meet, how Birdy works and why it is built the way it is. |
| Inläggets metarad | 24 september 2026 · 3 min läsning | 24 September 2026 · 3 min read |
| Rutan i slutet | Ta med Birdy *ut i fält.* / Gratis att ladda ner. Inget konto. Fungerar utan täckning. | Take Birdy *into the field.* / Free to download. No account. Works without a signal. |
| Länkar | ← Alla fältanteckningar · Se hur Birdy fungerar | ← All field notes · See how Birdy works |
| Bildtext (why-birdy) | Rödhake, Erithacus rubecula | European Robin, Erithacus rubecula |
| Författarrad | Albin Abrahamsson, AlbIT | Albin Abrahamsson, AlbIT |

Sidornas titlar och metabeskrivningar (SEO) ändras inte.

**Sakpåståenden som kontrolleras i QA**

- Ljud-ID är gratis för alla (BirdNET-licensen; appen).
- Kameran tittar flera gånger i sekunden (3 bilder per sekund).
- Ljud-ID spelar in fritt och stannar själv när den är säker (1.3.0).
- 839 arter, 34 märken varav 27 gratis och 7 i Premium.
- Premium är Fynd-kartan, PDF-export, säsongsstatistik och 7 märken, och inget annat (1.3.0).
- Platsen sparas bara om man slår på det, och bara i telefonen (integritetspolicyn).
- Inget laddas upp för att identifiera en fågel. Bara kartbilderna behöver uppkoppling.
- Köp och priser sköts i appen via Google Play (1.3.0).
- iPhone-appen är "på väg", utan datum.
- Dagens fågel bygger på säsongen i Sverige.

## Bilaga B: Bildkällor

| Används i | Art | QID | Fotograf | Licens |
|---|---|---|---|---|
| Heron (platta, rödhakelager, telefonutsnitt) | Rödhake | – | AI-genererad, webbens befintliga `hero-robin.webp` | egen |
| Karusellen: Identifiera (Dagens fågel) | Stjärtmes | Q170831 | Membeth | CC0 |
| Karusellen: Träff och Veckans uppslag | Domherre | Q25382 | Estormiz | CC0 |
| Karusellen: Artprofil | Talgoxe | Q25485 | Hobbyfotowiki | CC0 |
| Fältboken (planschen) | Ladusvala | Q25429 | Аимаина хикари | CC0 |
| Ta med Birdy ut i fält | Skäggmes | Q192817 | Hobbyfotowiki | CC0 |
| Bloggens första inlägg och kortet på startsidan | Rödhake | Q25334 | Rob Hille | Public domain |
| Uppslagsverket: kartans fallback-bild och kartans rostfärgade lager (samma data, inte bara fallbacken) | – | – | Natural Earth, svenska utgåvan, 1:10m (`ne_10m_admin_0_countries_swe`), renderad av `tools/render-coverage-fallback.mjs` | Public domain |

Källfil för varje foto: `asset-pack/src/main/assets/images/<QID>/hero.webp`. Metadata: `shared/content/species/**/<QID>.yaml`.
