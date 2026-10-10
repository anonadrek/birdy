# See the song: schemaläggning på Facebook, Instagram, YouTube och TikTok

Runbook för de 30 dagliga videorna (9 oktober till 7 november 2026, kl. 08.00 Stockholmstid) och för nästa serie. Skriven 2026-10-08 efter att de första 50 inläggen schemalagts via Chrome i Albins inloggade webbläsare.

## Till nästa serie (efter 7 nov)

- **En liten förloppsmätare överst i varje video** (Albin 2026-10-10: "en liten mätare i toppen av våra klipp där man ser hur långt det är kvar av klippet"). Byggs inför nästa schemaläggning, inte i de redan schemalagda. Den ska gå över hela den färdiga videon, alltså även titelkortet och loopen som `cover/title-card.mjs` lägger på efteråt, så den hör hemma i det sista steget (eller i stagen med den totala längden känd). Tunn och lugn i appens färger, ovanför plattformarnas egna knappar uppe till höger, och den får inte krocka med texten överst i stagen. Kontrollera i en förhandsvisning till Albin innan serien renderas.
- YouTube 10 till 18 okt har fortfarande de gamla versionerna (utan flockomslag). Att byta dem kräver en omrendering av `week1` och `week1-reserves` (mapparna finns inte längre); valfritt.

## Läget 2026-10-10 kväll: hela serien schemalagd

Alla fyra kanaler har ett inlägg om dagen 08.00 till och med 7 nov. YouTube 29 okt till 7 nov laddades upp lör 10 okt kväll, klart 19.53 (Albins OK samma dag) och kontrollerades i listan Shorts: tio rader "Schemalagd", en per dag i den publicerade ordningen, inga utkast. Nästa schemaläggning gäller serien från 8 nov.

## Läget 2026-10-08 kväll

| Kanal | Verktyg | Schemalagt | Kvar |
|---|---|---|---|
| Facebook + Instagram | Meta Business Suite (en reel, egen text per kanal) | 9 till 28 okt (20) | 29 okt till 7 nov (10) |
| YouTube Shorts | YouTube Studio | 9 till 18 okt (10) | 19 okt till 7 nov (20) |
| TikTok | TikTok Studio | 9 till 18 okt (10) | 19 okt till 7 nov (20) |

- TikTok schemalägger bara cirka 10 dagar framåt: 19 till 28 okt kan läggas tidigast omkring 9 okt, 29 okt till 7 nov tidigast omkring 19 okt.
- Meta slutade ta emot uppladdningar efter 20 reels i rad (förloppet stod still på 0 % för Knölsvan och Gärdsmyg). Ta resten i en ny omgång, gärna nästa dag.
- Rutnätsraden (omslaget delat i tre bilder, `grid/` på `social/see-the-song`) publicerades på Instagram och TikTok 2026-10-08. De tre fästs före den första videon fre 9 okt 08.00, i ordningen höger, mitten, vänster (den som fästs sist hamnar först). **TikTok: fästa av agenten 2026-10-08 kväll** i TikTok Studio (se steg 8 under TikTok Studio); Studio visar nu Pinned i ordningen vänster, mitten, höger. **Instagram: Albin fäster i appen** (⋯, "Pin to your profile"), eftersom varken instagram.com eller Business Suite har någon fäst-knapp.

## Omplaneringen med flockomslagen (2026-10-09)

Albin ville ha ett eget omslag per inlägg ("C med en tvist": flocken formar varje videos fågel) och att slutet loopar in i början. Hans OK: "Go for it", "you have go ahead"; "re upload and ill remove all the old ones last". Videorna ligger i samma mappar: `see-the-song.mp4` = titelkort + loop (8cd2e9a3), `cover.jpg` = flockomslaget, `-v1`-filerna är de gamla.

- **Meta kan inte byta omslag på ett schemalagt inlägg** (bara trimma och beskära), så nya inlägg laddas upp och de gamla tas bort av Albin. Meta slår på **automatisk textning** ("Textning") som standard: bocka ur den i varje inlägg och kontrollera att texten "Automatiskt genererad textning är aktiverad" är borta.
- **TikTok tar nu hela oktober** i kalendern (kontrollerat 9 okt; förut cirka 10 dagar). Omslaget på TikTok är videons första ruta, alltså titelkortet: ingen omslagsuppladdning behövs.
- **TikToks "Content check lite" tar ibland lång tid** ("about 10 minutes"): tranan hängde två gånger i över 25 minuter, hackspetten och nötväckan flera minuter, medan andra blev klara på sekunder. Sidan frågar kontroll-API:t var tionde sekund (syns i nätverksfliken), så det är TikToks kö och inget fel i sidan. Lämna uppladdningen öppen och vänta, eller kassera den och ta en annan art under tiden.
- **Gamla och nya är lätta att skilja åt:** de nya bildtexterna har "Silhouette:" i krediten. På TikTok står den nyast skapade överst bland inlägg med samma tid. På Meta har de gamla en mörk "?"-miniatyr och Instagram-raderna heter "Din reel".
- **Loop eller inte (Meta):** öppna raden i Innehåll, Schemalagt (panelen Inläggsdetaljer visar inläggets id). Sätt videons `currentTime` till slutet med JS och titta: loopversionen slutar på omslaget, den gamla på slutkortet "Identify birds by sound". Panelens meny Åtgärder har "Flytta till utkast" (ångringsbart). När ett Instagram-inlägg flyttades till utkast blev en röd rad "Det gick inte att publicera" kvar i Schemalagt; själva inlägget låg i Utkast.
- **Kontrollera att inget publicerats av misstag** i fliken Publicerat efter större omtag.
- **Ett andra flikfönster går inte att använda** för Meta medan TikTok väntar: en flik som inte syns får `visibilityState` hidden och Business Suite slutar ladda miniatyrerna.
- **TikToks "Content check lite" har en daglig gräns** ("You've reached your check limit for today"): efter cirka 30 uppladdningar 9 okt kom den inte längre igång. Schemaläggningen fungerar ändå; TikTok granskar inlägget när det publiceras. Hänger kontrollen i mer än 10 minuter går det att slå av reglaget för just det inlägget (sidan säger då "We'll check your content for For You Feed eligibility").
- **Chrome har zoom per webbplats:** tiktok.com bytte till 75 % mitt i arbetet (innerWidth 1440 i stället för 1080), Business Suite låg kvar. Räkna alltid om koordinaterna med `874 / window.innerWidth` och hitta beskrivningsrutan med `getBoundingClientRect` i stället för fasta koordinater.
- **Läget 9 okt cirka 14.30:** TikTok klart 10 okt till 7 nov. Facebook + Instagram klart till och med 22 okt (Meta strypte vid 23 okt, uppladdningen stod på 0 %). YouTube: dagsgränsen gäller till cirka 01.00 10 okt (24 timmar efter att den nåddes); 19 okt till 7 nov saknas helt där och laddas upp först, sedan eventuellt bytet av 10 till 18 okt.

## 10 okt: nya YouTube-titlar

Albin 2026-10-10 ("rubrikerna för korta för att bli virala"): YouTube-titeln är nu en fråga följd av båda namnen, "Would you recognise this bird by its sound? Grey Heron (Gråhäger) #shorts" (`youtubeTitle` i `tools/social/lib/captions.mjs` på `social/see-the-song`). Alla 20 Shorts (9 till 28 okt) bytta i YouTube Studio och kontrollerade i listan; `caption.json` för 19 okt till 7 nov omskrivna med `--captions-only`, så uppladdningen 29 okt till 7 nov får den nya titeln direkt. Titeln går att byta även på schemalagda Shorts: fältet `#title-textarea #textbox`, `execCommand('insertText')`, sedan `ytcp-button#save` och vänta tills knappen blir inaktiv (några sekunder).

## Kvällen 9 okt: städningen, YouTube och nya fällor

- **Läget kl. 17.15:** YouTube 9 okt publicerad, 10–28 okt schemalagda (en per dag, nya versioner från 19 okt), **29 okt–7 nov saknas** (dagsgränsen nådd igen efter 11 uppladdningar, trots att Albin telefonverifierade kanalen; Studio erbjuder en "engångsverifiering" för högre gräns). Meta: de gamla versionerna 12–22 okt (FB + IG, 22 st) flyttade till Utkast och kontrollerade efter omladdning; 23–28 okt har fortfarande bara gamla (byts när de nya är uppe); 29 okt–7 nov saknas. TikTok: oförändrat, Albins städning kvar (nedan).
- **Visningsnamnet "Birdy: Bird ID"** på TikTok (låst 7 dagar), Instagram via Kontocenter (2 byten per 14 dagar), YouTube (2 per 14 dagar) och Facebook-sidan (begärt av Albin 9 okt; granskning upp till 3 dagar, därefter låst 60 dagar). Facebooks sista steg kräver kontots lösenord: det gör Albin.
- **TikTok:** ett schemalagt inlägg har bara "Delete" i menyn (ingen avplanering, inget utkast, redigering avstängd). Permanent radering gör Albin. Regel för städningen: för varje datum 11–18 okt, behåll den översta raden och ta bort resten (9 st; samma datum: nyast skapad överst). Företagskonto och webbplatslänk i bion går bara i appen.
- **Meta, Flytta till utkast:** öppna raden (radens knapp öppnar panelen Inläggsdetaljer), panelens ⋯, "Flytta till utkast", bekräfta "Flytta till utkast". Både FB- och IG-rader lämnar en röd rad "Det gick inte att publicera" kvar i Schemalagt; inlägget ligger i Utkast och publiceras inte. Gamla och nya skiljs säkert åt med miniatyrens ljushet (bild laddad med `crossOrigin='anonymous'` till en 8×8-canvas: gamla cirka 106–110, nya cirka 215–218).
- **Fönstret skymt = sidan "hidden":** när Chrome-fönstret ligger bakom ett annat fönster blir `document.visibilityState` hidden. Då ritas sidan inte om förrän något tvingar fram en bildruta, timers stryps, och YouTubes kalender och Metas dialoger hänger kvar osynligt (en osynlig dialog fångar klick). Lösning: ett litet `zoom`-utsnitt efter varje steg tvingar fram en ruta; inga `setTimeout`-kedjor i JS.
- **YouTube, beprövat recept i skymt fönster:** fokusera titel och beskrivning med `el.focus()` (fältens `aria-label` börjar med "Lägg till en titel" respektive "Berätta för tittarna") och skriv med riktiga tangenttryck; radio, Nästa, Schemalägg-panelen, datumet (`.calendar-day` under rätt `.calendar-month-label`), tiden och tidszonen går med JS-klick, en åtgärd per JS-anrop med zoom emellan. **Tryck aldrig Escape i uppladdningsdialogen**: den stängs och videon blir ett utkast. Välj tidszonen "(GMT+02:00) Stockholm" uttryckligen så att inlägg efter sommartidens slut (25 okt) också går ut 08.00.
- **När flikgruppen tappas** (till exempel när den sista fliken stängs) skapar `tabs_context_mcp` ett nytt fönster som kan hamna minimerat; Albin får då ta fram det.

## Meta klart till 7 nov (9 okt kväll, ca 17.20 till 18.40)

- **Läget:** Facebook + Instagram har de nya versionerna (flockomslag, loop) varje dag 10 okt till 7 nov. 16 nya reels laddades upp i kväll (23 okt till 7 nov) utan att Meta strypte. De gamla 23–28 okt (12 st, FB + IG) är flyttade till Utkast. Kontroll efter omladdning av Schemalagt: 29 dagar, varje dag exakt ett FB- och ett IG-inlägg som inte är rött, alla med ljus miniatyr (nya); 11 röda rester ("Det gick inte att publicera") publiceras inte. Albin kan radera utkasten och de röda raderna när han vill.
- **Hjälpskriptet** för bildtexter: `node tools/social/caption-fields.mjs <slug>` (skriver FB, IG, TikTok-texten uppdelad i brödtext och hashtaggar samt YouTubes titel och beskrivning som JSON-strängar; ersätter den lokala `cap.py` sedan 2026-10-10). Vakten före Nästa jämför editorns `innerText` med förväntad text efter att alla blanktecken slagits ihop till ett mellanslag.
- **Facebook-sidans namn** är nu "Birdy: Bird ID" (syns i Business Suite; "Publicera i" säger "Birdy: Bird ID och app.birdy").
- **Brambling (7 nov)** har ingen publicerad artsida, så Facebook-texten länkar till `https://birdy.community/` i stället; vakten ska då leta efter den länken.
- **Fällor i Business Suite i kväll:**
  - "Lägg till video" flyttar sig medan WhatsApp-banderollen överst laddar: klicka knappen med JS (den synliga `div` som är 142×36 och har texten "Lägg till video"); filpatchen fångar filinmatningen ändå.
  - I skymt fönster: reglaget "Anpassa inlägget", flikarna Facebook/Instagram, Nästa, Textning (`input[type=checkbox]` i raden), alternativet Schemalägg och knappen Schemalägg går alla med JS-klick. Textredigerarna kräver riktiga klick: kontrollera först med `elementFromPoint` att redigeraren ligger under punkten; Instagram-redigeraren hamnar på olika höjd, så kör `scrollIntoView({block: 'center'})` på den först.
  - Datum och tid: `focus()` på datumfältet med JS (det går då i redigeringsläge), sedan ctrl+a, skriv `ÅÅÅÅ-MM-DD`, Tab (fokus hamnar i timmar), `08`, högerpil, `00`. Instagram först, sedan Facebook. En röd felrad under tiden medan datumet är ändrat men tiden inte är det är normal.
  - **Långa batcher tar timeout** (över cirka 60 steg med zoomar), men batchen fortsätter i bakgrunden. Läs alltid läget med JS innan något görs om, annars schemaläggs samma reel två gånger. Dela upp i två batcher: texterna, sedan Nästa till Schemalägg.
- **Fällor i Schemalagt (Flytta till utkast):**
  - Tabellen (`table`) är själv det som skrollar; nya rader laddas bara när sidan ritas, så zooma mellan skrollningarna i skymt fönster.
  - Gamla och nya Instagram-rader heter båda "Din reel"; Facebooks gamla rader visar bildtexten. Miniatyrens ljushet är den säkra skillnaden.
  - **"Publicera nu" står i panelen även för schemalagda inlägg**: det säger inget om utkast, och knappen ska aldrig tryckas. Bara schemalagda inlägg har menyvalet "Flytta till utkast", så det är vakten.
  - Bekräftelsedialogen "Vill du avbryta det schemalagda publiceringsdatumet?" ligger kvar i DOM:en efter varje flytt, så det blir flera staplade kopior. Klicka bara knappen i den översta (den som `elementFromPoint` träffar).
  - Efter flytten: Facebook-raden blir en röd rest, Instagram-raden försvinner ur Schemalagt.
  - `javascript_tool` väntar inte in promises: lägg resultatet på `window` och läs det i nästa anrop.

## Material

- Verktyget ligger på `main` sedan 2026-10-10 (`tools/social/`; grenen `social/see-the-song` och worktreen `C:/w/birdy-social` är borta). Artdatan läses ur repots egen `website/`-mapp.
- `tools/social/out/<set>/<slug>/` (gitignorerat, finns bara på maskinen som renderade): `see-the-song.mp4`, `cover.jpg`, `caption.json` (`facebook`, `instagram`, `youtube.title`, `youtube.description`). Varje mapp har `schedule.csv` med datum, art, alla texter och en not om bästa omslagsruta. **Mapparna `week1`, `week1-reserves` och `oct19-nov7` försvann 2026-10-10** när worktreen togs bort; `oct29-nov7` (de tio sista, för YouTube) renderades om på `main` samma dag med exakt samma slutkort som de publicerade. Rendera om en serie så här (cirka 2,5 minuter per video): `node tools/social/see-the-song.mjs --species <QID,...> --out tools/social/out/<set> --start <ÅÅÅÅ-MM-DD>`, sedan i `tools/social`: `node cover/render-covers.mjs <set>` och `node cover/title-card.mjs <set>`. Obs: `schedule.csv` i en omrenderad delmängd kan få en annan ordning än den publicerade; datumen styrs av listan nedan.
- **Rör aldrig en worktree med renderade videor med `git worktree remove --force`**: `out/` är gitignorerat och försvinner utan varning.
- Facebook och YouTube har en direktlänk till artsidan (`https://birdy.community/species/<slug>/`). Instagram och TikTok säger "Link in bio", eftersom länkar i bildtexten inte går att klicka där. TikTok får Instagram-texten.
- Texterna är på engelska med artens svenska namn i parentes och `#fåglar #fågelskådning` sist. Licensraden "Video licensed CC BY-SA 4.0" finns bara när foto eller ljud är CC BY-SA.

Ordningen 19 oktober till 7 november: Grågås, Ringduva, Sångsvan, Gråkråka, Större hackspett, Knipa, Nötväcka, Skata, Grönfink, Gråhäger (28 okt), Knölsvan (29 okt), Gärdsmyg, Sidensvans, Steglits, Korp, Björktrast, Domherre, Gråtrut, Gulsparv, Bergfink (7 nov).

## Regler

- Agenten skapar aldrig konton och skriver aldrig lösenord. Albin loggar in själv.
- Varje publicering kräver Albins uttryckliga OK i chatten. Givet 2026-10-08 för alla 30 videor på de fyra kanalerna och för rutnätsraden. Nya inlägg utöver dessa kräver ett nytt OK.
- Privata adresser (Business Suite-id, Studio-adresser med kanal-id) står inte i repot, som är publikt. De finns i agentens lokala minne på Windows-maskinen.
- Bara en session åt gången får styra Chrome.

## Gemensamma knep i Chrome

- Filväljaren får aldrig öppnas (den blockerar automationen). Patcha `HTMLInputElement.prototype.click` så att ett klick på en filinmatning bara markerar den, och ladda sedan upp filen med `file_upload` på den ref som `find` ger:

  ```js
  const orig = HTMLInputElement.prototype.click;
  HTMLInputElement.prototype.click = function () {
    if (this.type === 'file') { this.setAttribute('data-claude-file', '1'); if (!this.isConnected) { this.style.display = 'none'; document.body.appendChild(this); } return; }
    return orig.call(this);
  };
  ```

- Ta alltid ref från den senaste `find`. En gissad ref kan peka på fel fält.
- Klicka på riktigt i textredigerare. Fokus via JS fungerar dåligt: första tecknet hamnade sist i Business Suite.
- Avsluta varje inlägg med en vakt i JS som läser datum, tid och text och klickar på Schemalägg bara om allt stämmer.
- Dialogen "Lämna sidan?" undviks genom att navigera med `force: true`.
- Skärmbilden är nedskalad (874 px bred). Koordinater från `getBoundingClientRect` räknas om med `874 / window.innerWidth`.
- När Chrome-fönstret är skymt eller minimerat (`document.visibilityState` är `hidden`) tar skärmbilder timeout. Arbeta då med `javascript_tool` och `get_page_text`, och undvik kedjade `setTimeout`: Chrome stryper dolda flikar till ett anrop per minut, så en loop med korta väntetider ger timeout efter 45 sekunder.

## Meta Business Suite (Facebook och Instagram i ett inlägg)

1. Öppna Business Suite och kontrollera i portföljväljaren att det står Birdy (Albin har fler företag där). Innehåll, Skapa reel.
2. "Lägg till video" med filpatchen, sedan `file_upload`. Omslag: "Ladda upp bild", ladda upp `cover.jpg` i den andra av de två filinmatningarna.
3. Slå på "Anpassa inlägget för Facebook och Instagram". Skriv Facebook-texten i Facebook-fliken, byt till Instagram-fliken och skriv Instagram-texten (riktigt klick i redigeraren båda gångerna).
4. Vakt före Nästa: båda texterna börjar med artens första mening, Facebook-texten har artlänken och slutar med `#fågelskådning`, Instagram-texten har "Link in bio", texterna skiljer sig åt.
5. Nästa (ibland två gånger), sedan Schemalägg. Det finns två datumfält (`åååå-mm-dd`) och två tidsfält, ett per kanal. För varje: trippelklick på datumfältet, ctrl+a, skriv datumet, Tab, `08`, högerpil, `00`. Tidszonen Europe/Vienna är samma som Stockholm.
6. Vakt: båda datumfälten visar "den N oktober 2026" och båda tiderna 08:00, först då klick på Schemalägg. Bekräftelse: "Reelen är schemalagd".

## YouTube Studio (Shorts)

1. Studio, Skapa, Ladda upp videor. `file_upload` i filfältet.
2. Titel: ctrl+a (Studio fyller i filnamnet) och skriv `youtube.title`. Beskrivning: `youtube.description`.
3. "Nej, den är inte gjord för barn", Nästa tre gånger, Synlighet, Schemalägg.
4. Datum: öppna listan och klicka dagen (`ytcp-scrollable-calendar .calendar-day` som inte är `disabled`). Tid: alternativet "08:00". Tidszon: "(GMT+02:00) Stockholm".
5. Vakt: tiden 08:00, datumet börjar med "N okt. 2026", tidszonen Stockholm. Klicka den sista synliga Schemalägg-knappen. Bekräftelse: "Videon ställs in som" följt av schemalagd.
6. Inga egna omslag sattes på YouTube; Shorts visar en ruta ur videon. Vill Albin byta ruta anger `note` i `schedule.csv` den bästa tidpunkten.

**I skymt fönster (beprövat 10 okt kväll, tio videor utan fel):**

- Direkt efter `file_upload` är dialogen inte utlagd (titelfältet har storleken 0 × 0 och `focus()` misslyckas). Ett litet `zoom`-utsnitt tvingar fram en bildruta; kontrollera sedan att `document.activeElement` är fältet innan ctrl+a och skrivandet.
- Beskrivningen skrivs med `type` inklusive radbrytningarna; kontrollera efteråt i JS att den börjar och slutar rätt, har artlänken och sju radbrytningar.
- Radioknappen "inte gjord för barn" (`name="VIDEO_MADE_FOR_KIDS_NOT_MFK"`), Nästa (`#next-button`) och schemapanelen (`#second-container-expand-button` i `ytcp-uploads-review`) går med JS-klick, en sekund emellan.
- Datum: `#datepicker-trigger`, ett `zoom`, sedan dagen under rätt `.calendar-month-label` ("OKT. 2026", "NOV. 2026").
- Tid: listan behövs inte. Fokusera `#time-of-day-container input` med JS, ctrl+a, skriv `08:00`, Tab.
- Tidszon: `#timezone-select-button`, ett `zoom`, sedan `tp-yt-paper-item` med "(GMT+02:00) Stockholm".
- Dela upp varje video i två batcher (texterna till och med schemapanelen, sedan datum till Schemalägg). En enda lång batch tar timeout men fortsätter i bakgrunden: läs alltid läget med JS innan något görs om.

## TikTok Studio

1. `tiktok.com/tiktokstudio/upload`, `file_upload` i "Select video to upload", vänta cirka 13 sekunder.
2. Klicka i beskrivningen (cirka 300, 285 i skärmbilden), ctrl+a, Delete och kontrollera i JS att fokus ligger i redigeraren. Skriv Instagram-texten utan hashtaggar, sedan hashtaggarna med ett mellanslag sist (annars tar förslagslistan den sista).
3. "When to post": Schedule.
4. Datum: klicka datumfältet, hitta dagen med `span.day.valid` och rätt siffra, räkna om koordinaterna och klicka på riktigt. Läs veckodagen ur kalendern, inte ur minnet (ett klick på fel kolumn gav 14 i stället för 13).
5. Tid: klicka tidsfältet, kör `scrollIntoView({block: 'center'})` på `.tiktok-timepicker-left` med `08` och `.tiktok-timepicker-right` med `00`, klicka båda på riktigt och klicka sedan utanför.
6. Vakt: värdena innehåller `08:00` och datumet, texten börjar rätt och har "Link in bio", och "Content check lite" visar "No issues found". Klicka Schedule. Kommer dialogen "Continue to post?" är kontrollen inte klar: klicka Cancel, vänta tills den är klar och klicka Schedule igen. Tryck aldrig "Post now".
7. Kontrollera på `tiktokstudio/content` att inlägget står som "Oct N, 8:00 AM". TikTok har ett smalt mellanslag (U+202F) före AM, så matcha med `\s` i reguljära uttryck.
8. Fästa ett inlägg: på `tiktokstudio/content` har varje publicerad rad en meny (radens sista knapp) med "Pin to top", "Download" och "Delete". Listan laddar raderna efter hand, så skrolla ned tills raden finns. Menyn renderas utanför raden i DOM:en: kontrollera att den sitter vid rätt rad innan klicket (från radens knapp till "Pin to top" var det +53 px när menyn öppnades nedåt och -115 px när den öppnades uppåt). Bekräftelse: "Pinned to top" och etiketten Pinned i raden. Fäst i omvänd ordning, eftersom den som fästs sist visas först. Profilsidan på tiktok.com har ingen fäst-knapp.

## Profilerna

- Facebook-sidan "Birdy": profilbild, omslag och intro klara. Användarnamnet (adressen facebook.com/namn) väljer Albin; sidfoten på webben länkar till sidans id-adress tills dess.
- Instagram @app.birdy: profilbilden klar. Bion gick inte att spara på webben ("There was a problem saving your profile"); Albin klistrar in den i appen (texten i `docs/superpowers/specs/assets/2026-10-08-sociala-profiler/bios.md` på `social/see-the-song`).
- YouTube @birdy.community: banner, profilbild, beskrivning och länk klara. Namnet "Birdy" nekades ("Det här namnet kan inte användas"), så kanalen heter birdy.community tills Albin bestämt något annat.
- TikTok @birdy.app: profilbild och bio klara.
- Handtagen kan inte vara lika på alla kanaler, eftersom namnen är upptagna.
- Webben länkar till alla fyra i sidfoten och i `sameAs` (JSON-LD), live sedan `d170de09`.

## Sökindex (Albins OK 2026-10-08 kväll)

Albin vill att webbens sidor och de sociala profilerna indexeras en gång i veckan.

**Gjort 2026-10-08 kväll:** båda sitemaps (`sitemap-index.xml`, `sitemap-0.xml`) inskickade igen i Search Console. Indexering begärd för 11 adresser: `/sv/arter/`, `/species/`, Blåmes, Talgoxe, Rödhake, Koltrast och Skata på svenska, Robin, Blue Tit, Great Tit och Blackbird på engelska; nästa (Domherre) gav "Kvoten har överskridits", så resten tas en annan dag. YouTube-kanalerna @birdy.community och @abinatalbit är tillagda som plattformsegendomar (data efter upp till 48 timmar). **IndexNow byggt:** nyckelfilen `website/public/<32 hex>.txt`, skriptet `website/scripts/indexnow.mjs` (`--all` skickar hela live-sitemapen, annars de adresser som anges, `--dry-run` skickar inget; tester i `tests/unit/indexnow.unit.mjs`) och `.github/workflows/weekly-indexnow.yml` (måndagar 05.00 UTC, går också att starta för hand under Actions). **Kvar för Albin:** TikTok och Instagram som plattformsegendomar. Google öppnar plattformens inloggning i ett eget fönster, och det når agenten inte när Chrome är minimerat: Search Console, egendomsväljaren, Lägg till egendom, TikTok (inloggad som @birdy.app), godkänn; sedan Instagram för @app.birdy och för @albit.ab.

**Recept för Begär indexering i en dold flik:** skriv adressen i fältet "Granska webbadresser på birdy.community" via JS (värdet sätts med `HTMLInputElement`-setter, sedan `input` och Enter), vänta cirka 12 sekunder, klicka "BEGÄR INDEXERING" med mousedown, mouseup och click, och vänta tills dialogen säger "Indexering begärd" (realtidstestet tar 10 till 40 sekunder). Direktadressen `inspect?...&id=<adress>` ger 404.

- **Läget i Search Console 2026-10-08 kväll:** 4 sidor indexerade, 10 inte (3 omdirigeringar, 1 alternativ sida med korrekt kanonisk tagg, 6 upptäckta men inte indexerade). Sitemapen lästes senast 7 okt, då med 10 adresser. I dag har den 208: alla publicerade artsidor på båda språken, gruppsidorna, hubbarna och de vanliga sidorna. Google läser om den själv; en ny inskickning skyndar på.
- Google: sitemapen räcker för alla sidor. Begär indexering (Googles gräns är ungefär 10 adresser per dag och egendom) bara för hubbarna `/sv/arter/` och `/species/` och de mest sökta arterna. Varje måndag: titta på Sidor och Prestanda.
- Bing och andra: IndexNow. Skicka nya och ändrade adresser efter varje publicering eller en gång i veckan (upp till 10 000 per anrop). Byggt 2026-10-08 (se ovan): körs varje måndag och för hand efter en publiceringsrunda med `cd website && node scripts/indexnow.mjs --all`. Bing ligger bakom sökningen i ChatGPT, Copilot och DuckDuckGo.
- **Plattformsegendomar i Search Console (nytt i juli 2026):** Instagram, TikTok, X och YouTube kan läggas till som egna egendomar (Lägg till egendom, kopplas med plattformens egen inloggning). De visar vilka sökningar på Google som leder till inläggen (klick, visningar, position), indexerar inget och påverkar inte rankningen. Facebook stöds inte. Kandidater: @app.birdy, @birdy.app, @birdy.community och AlbIT:s Instagram @albit.ab (Albins OK 2026-10-08); YouTube-kanalerna är tillagda, TikTok och Instagram kvar (se ovan). Källor: Googles blogg juli 2026 ("See how content from social and video platforms performs on Google Search") och hjälpsidan "About platform properties in Search Console".
- Profilerna länkas redan från webben (`sameAs` och sidfoten), vilket är det som hjälper sökmotorerna att koppla ihop dem med Birdy.
