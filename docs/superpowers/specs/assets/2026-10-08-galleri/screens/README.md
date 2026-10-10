# Galleriets skärmar (2026-10-09)

Fyra skärmar ur Birdy-appen, på svenska och engelska, tagna på emulatorn `pg-api36`
(1080×1920, samma recept som webbens befintliga galleribilder: klocka 9:30, batteri
100 %, full wifi, inga notiser). Körda mot worktreen `C:/w/birdy-130`
(`release/1.3.0`, debug-bygget `se.birdy.android.debug`) installerat över emulatorns
befintliga data (QA-fynd och märken från tidigare sessioner, orörda).

**Skärm 12 är omtagen 2026-10-10** med en annan metod (data seedad direkt i
appens databas i stället för att återanvända gamla QA-fynd) för att fixa ett
licensproblem — se "Skärm 12 omtagen" nedan. De tre andra (07, 14, 16) är
oförändrade sedan 2026-10-09 och beskrivs som de ursprungligen togs.

**2026-10-10, Albins andra omgång feedback:** skärm 07 (`07-identifiera-savsangare.png`)
är borttagen ur galleriet igen (den låg bara där en dag) — filerna står kvar
här som historik. Tre nya Premium-skärmar tillkom i stället: `17-fynd-karta.png`,
`18-sasongsstatistik.png` och `19-exportera-pdf.png`, se "Tre nya Premium-skärmar"
nedan för metod och licenskontroll.

Varje PNG har en matchande `.xml` bredvid sig (en `uiautomator dump` av samma skärm)
för exakta elementkoordinater om någon vill bygga animationer senare.

## Filerna

| Fil | Visar | Datum/tillstånd |
|---|---|---|
| `07-identifiera-savsangare.png` | Identifiera-fliken (Identify) med Dagens fågel (Bird of the day) = **Sävsångare** / **Sedge Warbler** | Enhetens klocka tillfälligt satt till tis 6 okt 2026 (`daily_bird_history`-tabellen i appens egen databas kopplar Sävsångare till just det datumet; appen startades om kallt för att läsa av det), sedan återställd till riktig tid efteråt. |
| `12-veckans-uppslag.png` | Fältrapport/Field report, "En vecka i fält" / "A week in the field", vecka 41 (5–11 okt) | **Omtagen 2026-10-10**, se avsnittet "Skärm 12 omtagen" nedan — de ursprungliga fynd-fotona hade okänd licens. |
| `14-troferum.png` | Ditt troférum / Your trophy room, märkesrutnätet | Enhetens riktiga datum. Inga artfoton på den här skärmen (bara numrerade stämpelcirklar). |
| `16-uppslagsverk-vadare.png` | Uppslagsverket/Archive med gruppchippet **Vadare**/**Waders** valt, listan nedskrollad en bit så artrader syns | Enhetens riktiga datum. Chip-filtret "Vadare" visade sig vara sparat sedan en tidigare session (persisteras i appens inställningar) — ingen tryckning behövdes på nytt. |
| `17-fynd-karta.png` | Karta/Map, Premium: 20 fynd-nålar spridda runt Stockholm, MapTiler/OSM-attributionen synlig | **Ny 2026-10-10**, se "Tre nya Premium-skärmar" nedan. |
| `18-sasongsstatistik.png` | Säsongsstatistik/Season statistics, Premium: årsringen (oktober i mässing, aktuell månad), "20 fynd · 13 arter" | **Ny 2026-10-10**, se "Tre nya Premium-skärmar" nedan. |
| `19-exportera-pdf.png` | Uppslagsverket/Archive, Premium-kortet "Exportera fältdagboken"/"Export your field journal" | **Ny 2026-10-10**, se "Tre nya Premium-skärmar" nedan. |

SV- och EN-varianten av samma skärm är tagna efter varandra i samma session, med
appens eget språkval (Inställningar/Settings → Språk/Language) växlat mellan dem.
Eftersom listorna sorteras alfabetiskt per språk visar skärm 16 **olika arter** på
svenska jämfört med engelska (samma sak gäller delvis skärm 12, se nedan) — det är
förväntat, inte ett fel.

## Licenskontroll av synliga artfoton

Regeln: varje synligt artfoto måste vara CC0, public domain eller CC BY (aldrig
CC BY-SA, NC eller annat). Kontrollerat mot `shared/content/species/**/Q*.yaml`
(`image_refs`, `role: hero`).

### Skärm 07 — Identifiera/Identify (Sävsångare/Sedge Warbler)

- **Sävsångare / Sedge Warbler** (Q27236): **CC BY 4.0**, foto **Valuer Hardy**.
  Godkänd licens. Krediten syns redan inbyggd i skärmdumpen ("Foto: Valuer Hardy ·
  CC BY 4.0" / "Photo: Valuer Hardy · CC BY 4.0").

Inga andra artfoton synliga på den här skärmen.

### Skärm 12 — Veckans uppslag/This week's page (omtagen 2026-10-10)

**Den ursprungliga 2026-10-09-versionen är ersatt.** De runda fynd-foton som syntes
där kom ur emulatorns egen, redan befintliga QA-data (`birdy-observations.db`,
`observation.photo_path` pekade på filer i appens egna `files/observations/`) —
foton med okänd licens, inte Wikimedia Commons-bilder. Skärmen gick därför inte att
publicera som den var; se "Skärm 12 omtagen" nedan för hur den gjordes om.

### Skärm 12 omtagen — metod och licenskontroll (2026-10-10)

**Metod:** i stället för att skanna in fysiska foton via galleriet skrevs sex
observationsrader direkt in i `birdy-observations.db` (debug-appen,
`adb root` + `run-as` tillgängligt på `pg-api36`), en per dag måndag till lördag
(5–10 okt, vecka 41). De gamla QA-raderna i `observation` och `badge_unlock`
rensades först (`DELETE FROM observation; DELETE FROM badge_unlock;`) så inga
gamla fynd-foton med okänd licens kunde synas. Varje ny rad har `photo_path = ''`
(tom sträng): appens egen `recapImageModel`/`RecapPhoto` (se
`composeApp/.../ui/recap/RecapFinds.kt`) faller då tillbaka på **artens egen
redaktionella plansch-bild** (`heroImagePath`, samma bild som Artprofilen och
Uppslagsverket använder, `speciesImageUri`) i stället för ett eget fynd-foto.
Resultatet är alltså den riktiga appvyn, byggd av riktiga, redan
licenskontrollerade artbilder — inte en mockup. Databasfilen pullades med
`adb pull`, redigerades med Pythons inbyggda `sqlite3`-modul, och pushades
tillbaka (ägare/rättigheter återställda med `chown`/`chmod` efteråt).

Arterna valdes bland de vars **plansch-hero är CC0 eller Public domain** (aldrig
CC BY, för att helt slippa krav på synlig kredit i de små runda tumnaglarna), ur
`shared/content/species/**/Q*.yaml` (`image_refs`, `role: hero`):

| Dag (2026) | Art (SV) | Art (EN) | QID | Licens | Fotograf |
|---|---|---|---|---|---|
| mån 5 okt | Talgoxe | Great Tit | Q25485 | CC0 | Hobbyfotowiki |
| tis 6 okt | Rödhake | European Robin | Q25334 | Public domain | Rob Hille |
| ons 7 okt | Större hackspett | Great Spotted Woodpecker | Q26209 | CC0 | Hobbyfotowiki |
| tors 8 okt | Gråsparv | House Sparrow | Q14683 | CC0 | Hobbyfotowiki |
| fre 9 okt | Domherre | Eurasian Bullfinch | Q25382 | CC0 | Estormiz |
| lör 10 okt | Sädesärla | White Wagtail | Q25399 | CC0 | Hobbyfotowiki |

Samtliga sex arter syns i dag-remsan (måndag till lördag, söndag korrekt tom/
"inte än") och i "Nya i livslistan"/"New on your life list" (alla sex är nya
den här första veckan på en nollställd app, № 1 till 6). SV- och EN-bilden visar
samma sex arter i samma dagordning (till skillnad från skärm 16 sorteras inte
den här listan alfabetiskt, utan efter fyndtid). Ingen kredit krävs i bilden för
CC0/Public domain, så inget synligt licenstexttillägg behövdes.

**Känd avvikelse, ofarlig:** emulatorns systemklocka stod på fre 9 okt (inte
den verkliga sessionsdagen lör 10 okt) när skärmarna togs — `adb shell date`
bekräftade detta, demo-lägets klockvisning ("9:30") styr bara statusradens
text, inte den riktiga klockan som `captured_at_ms`/"idag" räknas mot. Det
påverkar inte utseendet: en dag med fynd ritas likadant oavsett om den räknas
som "framtida" eller ej (se `DayCell`/`isFuture` i `RecapWeek.kt`), och både
lör 10 okt och sön 11 okt ligger ändå kvar i samma vecka 41. Systemklockan
rördes medvetet inte (för att inte riskera sidoeffekter på annat i appen);
bara databasens tidsstämplar sattes explicit (klockan 12:00 UTC per dag,
emulatorns tidszon är GMT).

### Skärm 14 — Troférum/Trophy room

Inga artfoton. Bara numrerade stämpelcirklar i enfärgad rost/mässing. Inget att
kontrollera.

### Skärm 16 — Uppslagsverket/Archive, Vadare/Waders

**Svenska** (synliga arter, inkl. en tunn strimma av en fjärde rad som precis
skymtar ovanför bottennavigeringen):

- Amerikansk tundrapipare (Q216836): **CC0**, foto Owen Strickland.
- Brednäbbad simsnäppa (Q208335): **Public domain**, foto USFWSAlaska.
- Brushane (Q28122714): **CC BY 4.0**, foto **TRinaud**. Kräver kredit.
- Dammsnäppa (Q28355, delvis avskuren rad): **CC BY 4.0**, foto **Stephan
  Sprinz**. Kräver kredit.

**Engelska** (annan sortering ger andra arter på samma skrollposition):

- American Golden Plover (samma art som ovan, Q216836): **CC0**, Owen Strickland.
- Bar-tailed Godwit (Q18864): **CC0**, foto Hobbyfotowiki.
- Black-tailed Godwit (Q18841): **CC0**, foto Hobbyfotowiki.
- Black-winged Pratincole (Q695729, delvis avskuren rad): **CC BY 2.0**, foto
  **Derek Keats**. Kräver kredit.

Dagens fågel-kortet (Ringduva/Common Wood Pigeon, Q25386, CC BY 2.0 Ott Rebane)
ligger överst på den här skärmen men är helt bortskrollat i den sparade bilden
— bara en rad text ("Inte fångad idag · 0 av 3 dagar" / "Not caught today · 0 of
3 days") syns kvar av kortet, inget foto. Ingen kredit behövs för den här bilden.

## Tre nya Premium-skärmar (2026-10-10, Albins andra omgång feedback)

Albin tittade på förhandsvisningen av `website/galleri` och bad om tre tillägg:
ta bort skärm 07 igen, en levande ljudvåg på Lyssna-kortet, och tre nya kort
som visar Premium-funktionerna (fynd-kartan, säsongsstatistiken, PDF-export)
**tydligt märkta som Premium**. De tre nya skärmarna krävde en strängare regel
än vanligt: "alla artfoton som syns måste vara CC0 eller public domain" (inte
CC BY som annars är godkänt på webben) — så de små tumnaglarna aldrig behöver
en synlig kredit.

**Metod:** samma emulator och recept som skärm 12 (`pg-api36`, 1080×1920, demo-
läge), samma debug-bygge från `C:/w/birdy-130` (`release/1.3.0`; Premium är
öppet för alla i 1.3.1 så inget köp eller testkort behövdes). Ett helt
kalenderårs fynd (2026-01-15 till 2026-10-08, 20 observationer, 13 arter)
seedades direkt i `birdy-observations.db` på samma sätt som skärm 12
(`photo_path = ''`, så appen alltid visar artens egen redaktionella
planschbild) — den här gången också med `latitude`/`longitude` satta (spridda
runt Stockholm) så samma data föder både kartan och statistiken. Alla 13 arter
har kontrollerat **CC0** eller **Public domain**-hero i
`shared/content/species/**/Q*.yaml`:

| Datum 2026 | Art (SV) | Art (EN) | QID | Licens | Fotograf |
|---|---|---|---|---|---|
| 15 jan | Talgoxe | Great Tit | Q25485 | CC0 | Hobbyfotowiki |
| 14 feb | Domherre | Eurasian Bullfinch | Q25382 | CC0 | Estormiz |
| 10 mar | Rödhake | European Robin | Q25334 | Public domain | Rob Hille |
| 3 apr | Sångsvan | Whooper Swan | Q25612 | CC0 | Estormiz |
| 18 maj | Ladusvala | Barn Swallow | Q25429 | CC0 | Аимаина хикари |
| 1 jun | Stjärtmes | Long-tailed Tit | Q170831 | CC0 | Membeth |
| 12 jun | Rödhake (#2) | European Robin | Q25334 | Public domain | Rob Hille |
| 25 jun | Domherre (#2) | Eurasian Bullfinch | Q25382 | CC0 | Estormiz |
| 7 jul | Sädesärla | White Wagtail | Q25399 | CC0 | Hobbyfotowiki |
| 25 jul | Fiskmås | Common Gull | Q26427 | CC0 | Estormiz |
| 5 aug | Gråsparv | House Sparrow | Q14683 | CC0 | Hobbyfotowiki |
| 20 aug | Gråsparv (#2) | House Sparrow | Q14683 | CC0 | Hobbyfotowiki |
| 5 sep | Större hackspett | Great Spotted Woodpecker | Q26209 | CC0 | Hobbyfotowiki |
| 15 sep | Taltrast | Song Thrush | Q26349 | CC0 | Matti Virtala |
| 20 sep | Rödhake (#3) | European Robin | Q25334 | Public domain | Rob Hille |
| 2 okt | Talgoxe (#2) | Great Tit | Q25485 | CC0 | Hobbyfotowiki |
| 3 okt | Domherre (#3) | Eurasian Bullfinch | Q25382 | CC0 | Estormiz |
| 5 okt | Talgoxe (#3) | Great Tit | Q25485 | CC0 | Hobbyfotowiki |
| 7 okt | Svarthätta | Eurasian Blackcap | Q188446 | CC0 | Hobbyfotowiki |
| 8 okt | Ormvråk | Common Buzzard | Q25385 | CC0 | Hobbyfotowiki |

Talgoxe, Domherre och Rödhake hamnar på tre fynd var — årets topp tre, ett
medvetet tre-delat oavgjort.

## Kvalitetsdirektivet "ultra premium" (2026-10-10, samma kväll)

Albin tittade på förhandsvisningen igen: "Designen ska vara ultra premium
design, vi bygger något extremt stort här." Tre konkreta rättningar innan
något fick kallas klart:

1. **Mässingstaggen var för hög.** Den runda, fyllda "Premium"-plaketten på
   bygeln (ett wax-seal-mönster återanvänt från `.pseal`/`.hseal` på
   webbens Premium-sektioner) lästes som en skrikig medalj. Bytt mot en
   diskret kapsel med tunn mässingskontur på bygelns egen nästan-svarta
   färg (`.pbadge` i `AppTour.astro`) — samma återhållsamma språk som
   `.tag` på Premium-sidan, inte den fyllda versionen. `.plno-premium`
   (ordet "Premium" i kickern under plattan) står kvar oförändrat.
2. **Kartan var för tät.** 20 nålar på en gång läste som en datadump, inte
   en berättelse. Skuren till **sju** riktiga, namngivna platser i och
   runt Stockholm (Djurgården, Hagaparken, Järvafältet, Fjäderholmarna,
   Tyresta nationalpark, Hellasgården, Mälarens strand), spridda över hela
   året — se tabellen i skärm 17 nedan. Säsongsstatistikens 20/13 är
   oförändrade (samma observationer, bara färre av dem har en plats satt).
3. **PDF-kortet visade en knapp, inte en fältdagbok.** Bytt till den
   riktiga PDF:ens egen titelsida — se skärm 19 nedan för hur.

### Skärm 17 — Karta/Map, Premium (sju platser)

Pinnarna på kartan (`MapScreenHost.android.kt`, `buildBirdySealMarker`) är en
generisk marinblå fågel-silhuett i en rostfärgad stämpelring, **aldrig** ett
artfoto — `MapPin.photoPath` finns i datamodellen men används inte för
nålikonen. Inget licenskrav alls för den här skärmen. Attributionen "©
MapTiler · © OpenStreetMap contributors" (`map_attribution_maptiler` +
`map_attribution_osm`) syns som en kedja längst ned till vänster, båda
länkarna klickbara i appen — kravet "kartan måste behålla sin attribution" är
uppfyllt per appens egen design, inget extra gjordes för skärmdumpen.

Bara sju av de 20 observationerna fick `latitude`/`longitude` satt (resten är
fortfarande med i säsongsstatistikens 20/13, bara osynliga på kartan) — riktiga,
namngivna Stockholmsplatser i stället för en slumpmässig spridning, så kartan
läser som någons faktiska runda i stadens natur:

| Datum 2026 | Art | Plats |
|---|---|---|
| 15 jan | Talgoxe | Djurgården |
| 3 apr | Sångsvan | Hagaparken |
| 1 jun | Stjärtmes | Järvafältet |
| 25 jul | Fiskmås | Fjäderholmarna |
| 5 sep | Större hackspett | Tyresta nationalpark |
| 5 okt | Talgoxe (#3) | Hellasgården |
| 8 okt | Ormvråk | Mälarens strand, väster |

### Skärm 18 — Säsongsstatistik/Season statistics, Premium

Årsringen (`YearRing`) ritar bara siffror, inga foton. De tre "Mest sedda"-
sigillen längre ned på sidan (`TopSpeciesSeals`, `PhotoSeal`) använder
`heroImagePath` — samma redaktionella planschbild som Artprofilen, aldrig
fyndets eget foto — så de är lika licenskontrollerade som alla andra
artfoton på webben. Skärmdumpen är tagen överst på sidan (rubrik + årsring +
säsongsraden), innan sigillen hinner synas, så ingen ytterligare kontroll
behövdes för just den sparade bilden; "Nya arter i år"-kortets första rad
(Talgoxe, 15 januari) skymtar längst ned och är samma `heroImagePath`-mönster.
Den mässingsfärgade "PREMIUM"-pillen i headerraden (`PremiumBadge`,
`Res.string.premium_badge`) är appens egen, inbyggd i skärmdumpen.

### Skärm 19 — fältdagbokens egen titelsida (inte knappen)

**Bytt efter kvalitetsdirektivet:** den ursprungliga bilden av
Uppslagsverkets gröna "Exportera fältdagboken"-kort var en bra, äkta
skärmdump (och fungerar fortfarande som skärm 19:s *metod* nedan, se not),
men Albin ville se fältdagboken själv, inte knappen som skapar den. Det enda
fotot på knapp-varianten var Dagens fågel-kortet överst, som råkade visa
**Törnsångare** (Q110257516, **CC BY 4.0**, Alexis Lours) första gången —
`daily_bird_history`-raden för dagens datum byttes till **Talgoxe** (Q25485,
CC0) innan den bilden togs om. Den fixen är kvar relevant eftersom samma
emulatorsession användes för att generera PDF:en nedan.

**Metod för den nya bilden:** i appen (Uppslagsverket, Premium-kortet,
`Exportera fältdagbok`) genererades en riktig PDF via
`ExportJournalUseCase`/`JournalPdfRenderer` — appens delnings-UI (Androids
systemdelningsark) är generisk och inte värd att visa, så i stället
hämtades filen direkt: `adb pull
/data/data/se.birdy.android.debug/cache/journal_exports/*.pdf`. Sidan 1 (A4,
595×842pt, `JournalPdfMetrics`) renderades sedan rent i Chrome
(`file://…pdf#toolbar=0&navpanes=0&view=FitH`, Playwright, ingen
webbläsarram kvar) och beskars till **bara pappret** (ingen av sidans egen
svarta kant), förstorad till 1080 px bredd och förlängd nedåt till 1920 px
med samma papperston som sidan redan har (ett pixel-sampel, ingen gissad
färg) — så övergången mellan "sidans botten" och "den extra pappersytan
under" är helt osynlig, bara en lugn, naturlig lucka som redan fanns på
sidan själv. Inget beskuret, inget sträckt. Skriptet som gör beskärningen +
förlängningen ligger inte kvar i repot (kördes engångs från sessionens
scratch-mapp), men receptet är: hitta pappersfärgen med ett pixel-sampel,
beskär till den första mörka kantlinjen (page 1:s egen ram, inte sidbrytningen
till sida 2), förläng med `sharp().extend({ background: <pappersfärg> })`.

Titelsidan har **inga foton alls** — bara typografi (rubrik, "av Birdy",
årtal, en ornamentmarkör, "13 arter sedda · 20 fynd", sidnumret) på
fältdagbokens egen papperston. Inget licenskrav. Siffrorna (13 arter, 20
fynd) matchar exakt skärm 18:s säsongsstatistik, samma 20 observationer.

**Fälla, viktig:** PDF:en som gav 13/20 togs fram i en session där databasen
av misstag innehöll 75 fynd/44 arter vid ett tillfälle (troligen en kvardröjd
emulator-snapshot eller en felriktad tryckning mot en skärm som visade sig
ligga på fel skärmstorlek — `wm size` hade glömts bort efter en
emulatoromstart, så bottennavigeringens riktiga koordinater låg ~480 px
längre ner än väntat och en tryckning i blindo träffade fel knapp). Kontrollera
alltid den exporterade PDF:ens egna siffror mot det du vet att du seedat
innan du litar på den — en ny `adb pull` av databasen och ett snabbt
`SELECT COUNT(*) FROM observation` hade avslöjat det direkt.

Den tidigare bilden (det gröna Premium-exportkortet, bytt ut ovan) är kvar
beskriven här som referens om metoden någonsin behöver bytas tillbaka: det
enda fotot var Dagens fågel-kortet, Artlistan under filterraden (Vadare-
chippet stod kvar från en tidigare session) var aldrig synlig i den sparade
bilden — den mörkgröna Premium-rutan ("Exportera fältdagboken"/"Export your field
journal", mässingsflaggan "PREMIUM", CTA:n "Exportera fältdagbok ›"/"Export
Field Journal ›") fyller resten av skärmen. Ingen PDF öppnades eller delades;
kortet i sitt eget, overifierade "innan tryck"-läge är vad som visas.

## Krediter som behövs om bilderna publiceras (CC BY)

| Art | Fotograf | Licens | Var den syns |
|---|---|---|---|
| Sävsångare / Sedge Warbler | Valuer Hardy | CC BY 4.0 | `07-identifiera-savsangare.png` (sv+en), redan textad i bilden |
| Brushane / Ruff | TRinaud | CC BY 4.0 | `16-uppslagsverk-vadare.png` (sv) |
| Dammsnäppa / Terek Sandpiper | Stephan Sprinz | CC BY 4.0 | `16-uppslagsverk-vadare.png` (sv), smal strimma |
| Black-winged Pratincole | Derek Keats | CC BY 2.0 | `16-uppslagsverk-vadare.png` (en), smal strimma |

Inga CC BY-SA-, NC- eller på annat sätt olicensierade pipeline-foton upptäcktes i
någon av bilderna ovan (de ursprungliga åtta, 07/12/14/16). Skärm 12:s tidigare
öppna licensfrågetecken (egna fynd-foton med okänd licens) är löst genom
omtagningen 2026-10-10 ovan: skärmen visar nu bara artbilder som redan är
CC0/Public domain-kontrollerade. De tre nya Premium-skärmarna (17, 18, 19,
samma datum) höll sig till en strängare egen regel, CC0/Public domain överallt
(se tabellen ovan) — inte en enda CC BY-bild, så inga nya rader i krediterna
här.

## Tekniska anteckningar

- Emulatorns tidszon står på `GMT` (inte Europe/Stockholm) — `adb shell date`
  visar alltså samma klockslag som UTC. Dagens fågel-historiken
  (`daily_bird_history`-tabellen) kopplar Sävsångare till `2026-10-06`; appen
  måste startas om kallt (force-stop + start) efter ett klockbyte för att läsa av
  rätt datum, en redan körande process behåller annars sitt gamla "idag".
- Demo-mode-kommandot `network -e wifi show -e level 4` räckte inte för att få
  bort en envis "inget internet"-utropsstecken-badge på wifi-ikonen efter
  klockhoppet (riktig uppkoppling var hela tiden validerad, `dumpsys
  connectivity` visade VALIDATED); extraparametern `-e fully true` löste det.
- Emulatorn hade en förinstallerad testapp `com.postgraft.app` ("PostGraft")
  vars påminnelsenotis ("Time for your dose") poppade upp som en banderoll ovanpå
  Birdy och störde skärmdumparna. Dess notisbehörighet drogs tillfälligt in
  (`pm revoke … POST_NOTIFICATIONS`) och återställdes (`pm grant`) efter sista
  bilden; `heads_up_notifications_enabled` stängdes av och sattes tillbaka till 1.
- Chip-filtret "Vadare"/"Waders" i Uppslagsverket/Archive visade sig redan vara
  ihågkommet från en tidigare session (sparas i appens inställningar) — ingen
  manuell tryckning krävdes för att välja det på nytt den här gången.
- Emulatorns skärmstorlek sattes till `wm size 1080x1920` för fotograferingen och
  återställdes (`wm size reset`) efteråt. Språket växlades Svenska → English →
  Svenska och lämnades på svenska; datumet växlades 8 okt (riktigt) → 6 okt
  (Sävsångare) → 8 okt (riktigt, återställt) både för den svenska och den
  engelska Identifiera-bilden.
- **2026-10-10, Premium-omgången:** samma `adb root` + `adb pull`/`adb push` +
  Pythons `sqlite3`-metod som skärm 12, men ett helt år (20 rader) i stället för
  en vecka, och med `latitude`/`longitude` satta den här gången (en enkel
  deterministisk gyllene-vinkel-spiral runt 59.33°N 18.07°Ö, så nålarna sprids
  ut i stället för att trava på varandra) — samma 20 rader föder både kartan
  och säsongsstatistiken. `badge_unlock` rensades igen för ett rent läge.
  Emulatorns riktiga systemklocka visade sig stå på fre 9 okt (en dag efter
  "i dag" i den här sessionens kalender) trots att demo-lägets statusrad sa
  "9:30" — statusradens klocka styr bara vad som visas, inte den riktiga
  `adb shell date`; ofarligt här (samma vecka/år oavsett), men värt att känna
  till om något framtida skärmdump bryr sig om exakt vilken dag som räknas som
  "i dag". Dagens fågel (`daily_bird_history`, dagens rad) byttes från
  Törnsångare (CC BY 4.0) till Talgoxe (CC0) direkt i databasen för
  Uppslagsverket-skärmen — se Skärm 19 ovan.
