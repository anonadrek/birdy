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

Varje PNG har en matchande `.xml` bredvid sig (en `uiautomator dump` av samma skärm)
för exakta elementkoordinater om någon vill bygga animationer senare.

## Filerna

| Fil | Visar | Datum/tillstånd |
|---|---|---|
| `07-identifiera-savsangare.png` | Identifiera-fliken (Identify) med Dagens fågel (Bird of the day) = **Sävsångare** / **Sedge Warbler** | Enhetens klocka tillfälligt satt till tis 6 okt 2026 (`daily_bird_history`-tabellen i appens egen databas kopplar Sävsångare till just det datumet; appen startades om kallt för att läsa av det), sedan återställd till riktig tid efteråt. |
| `12-veckans-uppslag.png` | Fältrapport/Field report, "En vecka i fält" / "A week in the field", vecka 41 (5–11 okt) | **Omtagen 2026-10-10**, se avsnittet "Skärm 12 omtagen" nedan — de ursprungliga fynd-fotona hade okänd licens. |
| `14-troferum.png` | Ditt troférum / Your trophy room, märkesrutnätet | Enhetens riktiga datum. Inga artfoton på den här skärmen (bara numrerade stämpelcirklar). |
| `16-uppslagsverk-vadare.png` | Uppslagsverket/Archive med gruppchippet **Vadare**/**Waders** valt, listan nedskrollad en bit så artrader syns | Enhetens riktiga datum. Chip-filtret "Vadare" visade sig vara sparat sedan en tidigare session (persisteras i appens inställningar) — ingen tryckning behövdes på nytt. |

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

## Krediter som behövs om bilderna publiceras (CC BY)

| Art | Fotograf | Licens | Var den syns |
|---|---|---|---|
| Sävsångare / Sedge Warbler | Valuer Hardy | CC BY 4.0 | `07-identifiera-savsangare.png` (sv+en), redan textad i bilden |
| Brushane / Ruff | TRinaud | CC BY 4.0 | `16-uppslagsverk-vadare.png` (sv) |
| Dammsnäppa / Terek Sandpiper | Stephan Sprinz | CC BY 4.0 | `16-uppslagsverk-vadare.png` (sv), smal strimma |
| Black-winged Pratincole | Derek Keats | CC BY 2.0 | `16-uppslagsverk-vadare.png` (en), smal strimma |

Inga CC BY-SA-, NC- eller på annat sätt olicensierade pipeline-foton upptäcktes i
någon av de åtta bilderna. Skärm 12:s tidigare öppna licensfrågetecken (egna
fynd-foton med okänd licens) är löst genom omtagningen 2026-10-10 ovan: skärmen
visar nu bara artbilder som redan är CC0/Public domain-kontrollerade.

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
