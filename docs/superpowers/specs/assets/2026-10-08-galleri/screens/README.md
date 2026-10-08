# Galleriets skärmar (2026-10-09)

Fyra skärmar ur Birdy-appen, på svenska och engelska, tagna på emulatorn `pg-api36`
(1080×1920, samma recept som webbens befintliga galleribilder: klocka 9:30, batteri
100 %, full wifi, inga notiser). Körda mot worktreen `C:/w/birdy-130`
(`release/1.3.0`, debug-bygget `se.birdy.android.debug`) installerat över emulatorns
befintliga data (QA-fynd och märken från tidigare sessioner, orörda).

Varje PNG har en matchande `.xml` bredvid sig (en `uiautomator dump` av samma skärm)
för exakta elementkoordinater om någon vill bygga animationer senare.

## Filerna

| Fil | Visar | Datum/tillstånd |
|---|---|---|
| `07-identifiera-savsangare.png` | Identifiera-fliken (Identify) med Dagens fågel (Bird of the day) = **Sävsångare** / **Sedge Warbler** | Enhetens klocka tillfälligt satt till tis 6 okt 2026 (`daily_bird_history`-tabellen i appens egen databas kopplar Sävsångare till just det datumet; appen startades om kallt för att läsa av det), sedan återställd till riktig tid efteråt. |
| `12-veckans-uppslag.png` | Fältrapport/Field report, "En vecka i fält" / "A week in the field", vecka 41 (5–11 okt) | Enhetens riktiga datum (8 okt kväll). |
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

### Skärm 12 — Veckans uppslag/This week's page

**OBS, viktigt:** de runda foton som syns här (dag-cirklarna för mån/tis/ons och
"Nya i livslistan"/"New on your life list"-radernas tumnaglar) är **inte** appens
redaktionella artfoton ur innehållspipelinen. De är de faktiska fynd-fotona som
sparats i just den här emulatorns egen databas (`birdy-observations.db`,
`observation.photo_path` pekar på filer i appens egna `files/observations/`), det
vill säga bilder från tidigare QA-sessioner, inte licenskontrollerade Wikimedia
Commons-foton. Jag kan inte verifiera CC0/PD/CC BY-status för dem — de ligger
utanför `shared/content/species`-pipelinen helt.

Arterna som syns (samma sexart-mängd på båda språken, bara olika ordning/avskurna
rader):

- Koltrast / Common Blackbird (Q25234) — eget fynd-foto, okänd licens
- Blåmes / Eurasian Blue Tit (Q25382) — eget fynd-foto, okänd licens
- Talgoxe / Great Tit (Q25485) — eget fynd-foto, okänd licens
- Större hackspett / Great Spotted Woodpecker (Q26209) — eget fynd-foto, okänd
  licens (bara en smal strimma synlig längst ned, avskuren av navigeringsfältet)
- Domherre / Eurasian Bullfinch (Q25404) — syns i dag-cirkeln för måndag (ej i den
  synliga delen av listan, men samma fotokälla)
- Rödhake / European Robin (Q25334) — syns i dag-cirkeln för måndag, samma sak

**Rekommendation till Albin:** om den här skärmen ska användas publikt på
webben, bekräfta att de här fynd-fotona verkligen är dina egna (och inte t.ex.
bilder du använt för att simulera ett fynd under ett QA-pass) innan de
publiceras. Om osäkert går det att byta till en emulator/session utan sparade
fynd, eller fylla på fält-dagboken med foton du vet härstammar från Commons
under rätt licens, och ta om skärmen.

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
någon av de åtta bilderna. Det enda öppna licensfrågetecknet är de egna
fynd-fotona i `12-veckans-uppslag.png` (se ovan).

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
