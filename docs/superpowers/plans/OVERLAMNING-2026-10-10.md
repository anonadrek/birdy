# Överlämning 2026-10-10 (lördag kväll, allt på main)

Skriven lör 10 okt cirka 23.45 på Windows-maskinen. **Nästa session börjar här.** Chatten med Albin är på engelska, repot och Slack på svenska. Eftermiddagens version av den här filen (14.00, med städningen 10 okt) finns i git-historiken före `c9c07384`; föregående överlämning är `OVERLAMNING-2026-10-09.md`.

## Läget på en minut

- **Allt ligger på `main`** (`c9c07384` plus dokumentcommits): inga andra grenar lokalt eller på GitHub, inga worktrees, inga öppna PR:er, inga stashar. I `C:/w/` finns bara `social-rerender.log`.
- **Birdy 1.3.1 (vC130) är inskickad till Googles granskning** (lör 22.15): fullständig lansering 100 %, AAB:n från biblioteket, What's new på engelska och svenska. Hanterad publicering är av, så versionen går ut av sig själv när den är godkänd (oftast en till sju dagar). Varningar: 1 telefonmodell av 12 275 tappar stödet (3 ABI:er mot 4 i 1.2) och inga felsökningssymboler; inga fel.
- **Webben (birdy.community), allt live i kväll:**
  - Bloggen i flockbilder: See the songs flockomslag, flocken som rödhake till "Varför Birdy finns", ett kortsystem, alla småfåglar åt samma håll som fågeln de bildar.
  - **Flygvägen:** fåglarna som lämnar en bloggbild flyger vidare och landar i flocken i inlägget ovanför (telefon: uppför högermarginalen förbi kortens text; breda skärmar: upp in i det stora kortet och åt höger in i nästa kort; startsidans tre anteckningar som en flygning). Albin: "Mycket bra måste jag säga". Kod `website/src/lib/note-flight.mjs`, `src/components/NoteFlight.astro`, data `src/data/note-art.json`; spec och plan `docs/superpowers/specs/2026-10-10-blogg-flygvag-och-namn-design.md`, `docs/superpowers/plans/2026-10-10-blogg-flygvag.md`.
  - Premium i ljuset med flocken som flyger ett år runt sigillet (P3).
  - Menyn: **Blogg** och **Möt fåglarna** (Blog, Meet the birds); titeln "Birdy: Känn igen, samla och lär känna fåglarna" och en ingress om fältdagboken, livslistan och märkena.
- **Sociala medier:** See the song är schemalagd varje dag 08.00 på alla fyra kanaler till och med 7 nov (YouTube 29 okt till 7 nov laddades upp i kväll). Instagram heter nu **"Birdy: Know the bird"** (Albins val); de andra namnbytena väntar (se nedan).
- **v1.3.2 = Premium-uppdateringen:** planen `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` har nu punkt 15 (kalibrering av ljud- och kamera-ID) och punkt 16 (faktagranskning av appens 839 arter).

## Gör i den här ordningen

1. **1.3.1 när Google godkänt:** kontrollera Play Console, Publiceringsöversikt ("Ändringarna granskas"). Sedan datumet som reserv i `APP_1_3_LIVE_FROM` (`website/src/lib/release.mjs`), webbgrinden, push och live-koll; en rad i #birdy-bygge.
2. **Följa fågelskådare (Albins val: lista och lugnt tempo):** listan och rutinen ligger i `docs/superpowers/runbooks/2026-10-10-folja-fagelskadare.md`. 10 till 15 nya per plattform och dag från Birdys konton, några ärliga gillanden, aldrig avfölja i omgångar; anteckna varje omgång i loggtabellen och stanna vid första varning från plattformen. Chrome-fönstret med agentens flikar måste ligga framme (se fällorna).
3. **Namnet "Birdy: Know the bird" på resten av kanalerna:**
   - YouTube vägrade namnet 10 okt ("Det här namnet kan inte användas för din YouTube-kanal"), även med "·" i stället för kolon. Troligen är 14-dagarsgränsen förbrukad sedan bytena 9 okt; försök igen tidigast 23 okt, och fungerar det inte då, fråga Albin om en variant.
   - TikTok: namnet är låst 7 dagar efter bytet 9 okt; byt tidigast 16 okt.
   - Facebook-sidan: låst 60 dagar efter bytet 9 okt (och sista steget kräver Albins lösenord); tas när låset släpper.
4. **Siffrorna varje måndag från 12 okt** (YouTube Studio, TikTok Studio, Business Suite, Search Console, Vercel Analytics, Play Console): en rad per kanal i #birdy-marknad och i tabellen i Albins dokument "Birdy: läget, varumärket och 12–24 månader framåt".
5. **v1.3.2, Premium-uppdateringen:** grindarna BirdNET:s svar, köptestet med vC129, MapTiler Flex och ny nyckel, Resend och brytpunkten (1.3.1:s go-live plus 48 h); delarna i planen, var och en med egen plan och SDD i egen worktree under `C:/w/`, huvudagenten slår ihop. Punkt 15 och 16 ska vara klara före produktionsbygget.
6. **Artsidorna tidigast tors 15 okt:** Search Console först, sedan återstart enligt `OVERLAMNING-2026-10-09.md` (worktreen för loopen återskapas, `website/reports/STOP` i huvudklonen tas bort), cirka 3 arter om dagen.
7. **Nästa sociala serie från 8 nov:** rendera med förloppsmätaren (`node cover/title-card.mjs <set> --progress`), omslagen ska följa regeln att flocken flyger ihop (`cover/flock-cover.html` har fortfarande den gamla spridningen); nya hookar och längre bildtexter. Detaljerna överst i `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md`.

## Väntar på Albin

- Två osända ändringar i Play för det stängda testet Alpha (lägg till e-postlistan "Testing Group #1", ta bort Google-gruppen birdy-testers@googlegroups.com): skicka eller kasta dem i Publiceringsöversikten.
- Beslut: löftet i forumtexterna om Premium för tidiga användare (kräver brytpunkten, install referrer för Android 12 och äldre, ny webbtext); Märken 1a i v1.3.2 (rekommendation: ja, ihop med 1b); Cockpit-planen för måndagssiffrorna; undantagsarket för våg 2 före 15 okt.
- Köptestet med vC129, MapTiler Flex, Resend-kontot, BirdNET-brevet, Slack Pro.
- Klippsidan i bion (Instagram; TikTok efter företagskontot i appen), plattformsegendomarna TikTok och Instagram i Search Console, de nya bloggadresserna till albit.se-sessionen.
- iOS: sim-check, device-verify, Apple Developer-enrollment. Simulatorn går inte att köra på Windows-maskinen; alternativen som gavs Albin 10 okt: fjärrstyra Mac:en (Chrome Remote Desktop eller Skärmdelning via VNC), strömma CI:ns simulatorbygge via Appetize.io (gratis 30 minuter i månaden, Starter 59 USD för 500 minuter; Albin skapar kontot) eller spela in sim-checken i CI. Kamera och ljud-ID kräver ändå en riktig iPhone.

## Fällor från i kväll

- **Agentens Chrome-fönster bakom andra fönster:** Play Console och Metas sidor öppnar då inga dialoger och tar inte emot skrivning, och skärmbilder tar timeout. Att Albin tar fram "Chrome" räcker inte om det är ett annat fönster. Lösningen 10 okt: den gamla flikgruppen stängdes och `tabs_context_mcp` med `createIfEmpty` skapade ett nytt fönster framför. Detaljer i agentens minne om Play Console.
- **Publiceringsöversikten kan ha andras väntande ändringar:** skicka bara det som är godkänt ("Spara till senare" på resten).
- **YouTube Studio i skymt fönster:** receptet i sociala runbooken (zoom före fokus, tiden skrivs direkt, två batcher per video) gav tio uppladdningar utan fel.
- **Flygvägen ritas med `requestAnimationFrame`:** i en dold flik syns inget lager förrän fliken är synlig; kontrollera live med att `[data-note-flight]` har klassen `nflight-motion` och att korten har `data-flight-art`.
- **Bart `python` i Bash hängde** (troligen Store-aliaset): använd `node`, eller `uv run python` i `tools/content-pipeline`.

## Var saker ligger

| Vad | Var |
|---|---|
| Koden för 1.3.1 | `main`, tagg `v1.3.1` (`ca9c5927`) |
| Den betalda releasen v1.3.2 | `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` |
| Flygvägen, spec och plan | `docs/superpowers/specs/2026-10-10-blogg-flygvag-och-namn-design.md`, `docs/superpowers/plans/2026-10-10-blogg-flygvag.md` |
| Följa fågelskådare | `docs/superpowers/runbooks/2026-10-10-folja-fagelskadare.md` |
| Sociala schemaläggningen | `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md` |
| Artsidornas paus | `website/reports/STOP` i huvudklonen |
| Äldre statusposter | `docs/superpowers/status-arkiv/2026-10-10-claude-md-status.md` |
