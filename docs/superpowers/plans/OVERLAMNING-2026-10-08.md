# Överlämning 2026-10-08 kväll (Windows)

Albin bad om en ny session efter kvällens pass ("Förbered en ny session"). Läs den här filen först, sedan de översta posterna i CLAUDE.md. Börja med `git pull` i huvudklonen.

## Börja här

Två spår kan gå parallellt (olika filer och worktrees), men högst ett tungt Gradle-jobb och en emulator åt gången, annars blir maskinen tung:

1. **Webben i Flock-looken, Albins huvudspår.** Fortsätt brainstormen (`superpowers:brainstorming`) från det som är godkänt: utkast 1 och rörelseprototypen "Flocken lyfter". Ställ de frågor som återstår en i taget, skriv specen, sedan planen, och bygg i steg.
2. **1.3.1 del 7 och 8 med SDD** i worktreen `C:/w/birdy-streck` (gren `feature/1.3.1-tankstreck`, pushad 2026-10-08): Task 2:s fixrunda, sedan Task 3 till 6.

## Spåren

| Spår | Var | Läge | Nästa |
|---|---|---|---|
| Webben i Flock-looken | `docs/superpowers/specs/assets/2026-10-08-flocken-webben/` | utkast 1 gillat, utkast 2 avvisat, rörelsen godkänd | brainstorm, spec, plan, bygge |
| 1.3.1 del 7 och 8 | `C:/w/birdy-streck`, `feature/1.3.1-tankstreck` | Task 1 klar, Task 2 godkänd med småfynd | fixrunda, Task 3 till 6 |
| 1.3.1 i stort | `release/1.3.0`, `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` | planen godkänd av Albin | grindarna nedan |
| Artsidorna | loopen i `C:/w/birdy-publish` | 100 av 180 live, paus | tidigast tors 15 okt |
| Sökindex | Search Console, IndexNow | sitemaps inskickade, 11 adresser begärda | cirka 10 om dagen |
| Sociala medier | runbook `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md` | schemalagt till 18 och 28 okt | resten i Chrome |

## 1. Webben i Flock-looken

**Godkänt:**

- Utkast 1: https://claude.ai/artifact/P39PewN7n4vVoURz5vbDFi (version 3), filen `flocken-pa-webben.html`.
- Rörelseprototypen: https://claude.ai/artifact/Y9PWpnzdTSkPrgyGoYtgY1 (version 3), filen `flocken-lyfter.html`. Albin: "Ser kanon ut", "Sen är vi nöjda", "Mycket vackert".

Besluten 1 till 6 står i mappens README.

**Albins smak, så långt:**

- Djup betyder en tydligare idé och en lugnare komposition, inte fler lager. Utkast 2 (en röd linje genom sidorna, sånghalo, stämplar, fler anteckningar) var "kladdigt som tusan".
- Inga pilar eller anteckningar ovanpå flocken. Pilen från "dagens fågel" till den tända fågeln togs bort i kväll ("ser inte så bra ut").
- Alla sidor behöver inte den stora flocken.
- En enda stor rörelse, på startsidan, som berättar något och sedan står still.

**Förslag att pröva i brainstormen:** varje art är en fågel i flocken. Startsidan visar flocken som bildas och dagens fågel som tänds. En artsida kan bära sin egen fågel ur flocken, liten och stilla, i stället för hela flocken. Då blir idén den röda tråden, utan en dekoration som binder ihop sidorna.

**Frågor som återstår, en i taget:**

- Vad säger varje sidtyp: inlägg, artsida, gruppsida, startsida, Premium och juridik?
- Var syns den stora flocken, och var bara märket?
- Ersätter rörelsen startsidans nuvarande hjälte (Dagens fågel som plansch)?
- Ordningen är bestämd: inläggen först, sedan artsidorna, sist startsidan med meny och sidfot.

**Tekniskt:**

- Flockdatan är `flock-data.js` (839 fåglar per flock, cirka 25 kB).
- Rörelsen ritas på en canvas med en bitmap av märket per färg.
- Minskade rörelser ger den färdiga bilden direkt.
- Bygget måste klara webbens vakter: `test:palette` behöver nya regler för de sociala färgerna, plus `test:contrast`, `test:no-dashes`, Playwright, axe och Lighthouse på mobil.

**Se en prototyp när Chrome är minimerat:** skärmdumpar i Chrome tar för lång tid när fönstret är minimerat. Rendera i stället headless med Playwright ur `website/node_modules/playwright`:

- Starta med `chromium.launch({ channel: 'chrome' })` och öppna sidan med `newPage({ viewport, reducedMotion: 'reduce' })`.
- `reducedMotion: 'reduce'` ger den färdiga bilden.
- Fotografera med `page.locator('#hero').screenshot()`.
- Lägg skriptet i sessionens scratchpad, inte i repot.

## 2. 1.3.1 del 7 och 8 (SDD)

- **Plan:** `docs/superpowers/plans/2026-10-08-1.3.1-del-7-8-tankstreck-och-pdf.md` på grenen.
- **Ledger:** `C:/w/birdy-streck/.superpowers/sdd/progress.md`. Den är lokal på Windows och har den exakta fixlistan.
- **Klart:**
  - Task 1, PDF:en: sidnumret utan tankstreck, och `fitLabel` på artnamn och märkestexter, på både Android och iOS (`66eb1927`, `b5510854`, `ea4fe7cc`).
  - Task 2, strängarna (`0c89f2c2`): 94 strängar utan tankstreck, platshållarna "OKÄNT" och "Inte angivet", appnamnet "Birdy" och vakttestet `NoDashesInAppTextTest`. Kvalitetsgranskningen gav "Approved with minors": 0 Critical, 0 Important.

**Fixrundan för Task 2** (en Sonnet-agent, sedan en kort omgranskning):

1. Vakten ska också fånga ett tankstreck som skrivs som `&#8212;`, som `&#x2013;` eller som backslash-u2014-escape. Den sista formen gör compose-resources om till ett tecken när appen byggs. Lägg också till ett självtest av mönstret.
2. Kontroll (c) ska kräva att alla källmappar finns och att fler än 100 `.kt`-filer lästes, och den ska skanna även `androidApp/src/main/kotlin`. Bredda mönstret till fristående streck med mellanslag och till teckenlitteraler, och hoppa över kommentarsrader.
3. `file.parentFile?.name ?: file.path` på två ställen, så att varningen försvinner.
4. Lägg till en slutpunkt efter "Försök igen" i fyra felmeddelanden på båda språken: `diary_save_error_frame_unavailable`, `diary_note_save_error`, `diary_delete_failed` och `badges_load_error`.
5. `onboarding_s3_species_demo` blir "Talgoxe · 94%" och "Great Tit · 94%", som chipet på Lyssna.
6. Kontroll (b) och (a) ska gå igenom alla `values*/strings.xml` i stället för att räkna med exakt två filer.

**Därefter:**

- **Task 3:** `app_dashes.py`.
- **Task 4:** körningen och kommandot `birdy-fetcher app-dashes`.
- **Task 5:** den styrande agenten kör `uv run birdy-fetcher app-dashes --max-cost 12` själv, handrättar raderna märkta KVAR och kontrollerar att en torrkörning visar 0 arter.
- **Task 6:** vakttestet för arttexterna och ny `species.db`. Kör hela grinden med `--no-configuration-cache`, pusha och vänta på CI inklusive iOS.
- **Sist:** en slutgranskning av hela grenen. Sedan slår huvudagenten ihop den i `release/1.3.0` och bockar av del 7 och 8 i den samlade planen.

**Grindar för 1.3.1** (ur den samlade planen):

- BirdNET:s svar.
- Köptestet med vC129.
- MapTiler Flex, nästa vecka.
- Resend, nästa vecka.
- Releasedagen: då räknas `GRANDFATHER_CUTOFF_MS` fram och webbens `APP_1_3_LIVE_FROM` sätts.

## 3. Artsidorna och sökindex

- **Läget:** 100 av 180 arter live (våg 1: 39 av 40, våg 2: 61 av 84). Två till är klara och väntar, 21 är flaggade och Gråsiska saknar fakta om läte. Våg 3 är 56 arter.
- **Pausen:** loopen stoppades 18.03 med `C:/w/birdy-publish/website/reports/STOP`, efter Albins beslut om 7 dagars paus så att Google inte ser en massproduktion.
- **Återstart, tidigast torsdag 15 okt:**
  1. Kolla Search Console: vad som är indexerat och vad som är "genomsökt men inte indexerat".
  2. Ta bort `STOP` och publicera de 2 klara arterna.
  3. Publicera undantagen med de säkra standardvalen i ⏸️-posten i CLAUDE.md (Albin kan ändra dem före dess).
  4. Fortsätt i jämn takt, cirka 3 arter om dagen.
- **Våg 3:** när budgeten räcker. API-kostnaden för oktober är cirka 76 av 100 USD, och 1.3.1:s körning tar 5 till 12 USD till, så våg 3 (cirka 25 USD) kräver en höjd gräns eller 1 november.
- **Begär indexering:** cirka 10 adresser om dagen. Nästa är Domherre, sedan de mest sökta arterna. Receptet för en dold flik står i runbookens avsnitt Sökindex.
- **IndexNow:** körs själv varje måndag 05.00 UTC. Kör den för hand efter en publiceringsrunda: `cd website && node scripts/indexnow.mjs --all`.

## 4. Sociala medier

**Schemalagt kl. 08.00 med Albins OK:**

- Facebook och Instagram: 9 till 28 okt.
- YouTube Shorts: 9 till 18 okt.
- TikTok: 9 till 18 okt.

**Kvar, i Chrome, enligt runbooken:**

- Facebook och Instagram: 29 okt till 7 nov (10 videor). Meta strypte uppladdningarna efter 20 i rad.
- YouTube: 19 okt till 7 nov (20 videor).
- TikTok: 19 till 28 okt (10 videor), tidigast omkring 9 okt.
- TikTok: 29 okt till 7 nov (10 videor), tidigast omkring 19 okt.

Fredag 9 okt efter 08.00: kontrollera att de första inläggen gick ut på alla fyra kanaler.

## 5. Datum

- **Fre 9 okt 08.00:** de första inläggen går ut. Albin fäster rutnätsraden på Instagram före dess.
- **Från omkring 9 okt:** TikTok 19 till 28 okt kan schemaläggas.
- **Mån 12 okt:** IndexNow körs automatiskt. Titta på Sidor och Prestanda i Search Console.
- **Nästa vecka:** Slack Pro (flytta kanalernas text till en canvas och "Väntar på Albin" till en lista), MapTiler Flex och Resend.
- **Tidigast tors 15 okt:** artsidorna igen.
- **Från omkring 19 okt:** TikTok 29 okt till 7 nov kan schemaläggas.
- **1 nov:** ny API-månad.

## 6. Väntar på Albin

1. Fäst rutnätsraden på Instagram i appen, i ordningen höger, mitten, vänster, före fre 9 okt 08.00.
2. Lägg till TikTok @birdy.app, Instagram @app.birdy och @albit.ab som plattformsegendomar i Search Console. Inloggningen öppnas i ett eget fönster.
3. Klistra in Instagram-bion i appen, bestäm YouTube-namnet och välj ett användarnamn för Facebook-sidan.
4. Säg "publicera" om blogginlägget "See the song" (`website/socials`).
5. Karusellens val A, B eller C: ersätts troligen av Flock-looken. Fråga innan något byggs.
6. Köptestet med vC129, MapTiler Flex, Resend och BirdNET:s svar.
7. API-gränsen för våg 3.
8. Play-titeln "Birdy — Bird Identify & Guide" har ett tankstreck. Gäller regeln även butikstiteln? Titeln är viktig för sökningen i Play.
9. Kanalnamnen för notiser visas bara på svenska i Androids inställningar, en äldre lucka. Ska de med i 1.3.1?
10. Undantagens standardval för våg 2 kan ändras före 15 okt.
11. Simkontrollerna på Macen (i2c, i3, i4) när det passar.

## 7. Rutiner

- **Efter varje arbetspass:** en kort rapport på svenska i #birdy-status (klart, pågår, nästa, väntar på Albin), och ett inlägg i den kanal som berörs när något stort ändras. Kanalernas id:n finns i agentens lokala minne.
- **Repot är publikt:** inga privata id:n eller länkar till konton i commits, Slack eller delade artefakter.
- **Synkregeln:** uppdatera CLAUDE.md, kör `python tools/sync_agents_md.py`, committa och pusha före slutet av varje session.
