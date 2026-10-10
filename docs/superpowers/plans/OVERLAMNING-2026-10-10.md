# Överlämning 2026-10-10 (lördag eftermiddag, allt på main)

Skriven lör 10 okt cirka 14.00 på Windows-maskinen. **Nästa session börjar här.** Chatten med Albin är på engelska, repot och Slack på svenska. Nattens version av den här filen finns i git-historiken (`OVERLAMNING-2026-10-10.md` före `e16d5d43`).

## Uppdatering lör 10 okt kväll (20.00)

- **YouTube 29 okt till 7 nov: klart** (19.53). Alla fyra kanaler har See the song varje dag till och med 7 nov. Punkt 2 nedan är gjord.
- **Bloggen och Premium är sammanslagna med `main`** (Albins OK: "Merge blog after and premium"), efter Albins önskan att flockkonsten alltid ska flyga ihop: alla små fåglar åt samma håll som fågeln de bildar, rödhaken speglad åt höger, de som lämnar i en båge framför huvudet, och i Premiums årsring följer fåglarna och dagens fågel ringen. Regeln står under Bestående fakta i CLAUDE.md. Valsidans val: Premium P3, Varför Birdy A.
- **1.3.1: inskickad 22.15** med fullständig lansering 100 % (AAB:n från biblioteket; Albin hade laddat upp den i ett annat fönster). Punkt 1.1 och 1.2 nedan är gjorda; kvar är 1.3 när Google godkänt. Två osända ändringar för det stängda testet Alpha lämnades kvar åt Albin.
- **Förloppsmätaren:** Albins OK ("ok", 10 okt kväll), sammanslagen med `main`; grenen och worktreen är borta. Rendera serien från 8 nov med `--progress` (runbooken).
- **iOS-simulator på Windows-maskinen:** går inte direkt (Simulatorn finns bara i Xcode på macOS, och Apples licens tillåter macOS bara på Apples hårdvara). Alternativen som gavs Albin: fjärrstyra Mac:en från Windows (Chrome Remote Desktop eller Skärmdelning via VNC), strömma CI:ns simulatorbygge till Chrome via Appetize.io (gratis 30 minuter i månaden, Starter 59 USD för 500 minuter; Albin skapar kontot), eller spela in sim-checken som video i CI. Kamera och ljud-ID kräver ändå en riktig iPhone.

## Läget på en minut

- **Allt ligger på `main`.** Inga andra grenar lokalt eller på GitHub, inga öppna PR:er, inga worktrees, inga stashar, `C:/w/` är tom. Grenar som inte slogs ihop finns som taggar `archive/<gren>`; releasen är taggad `v1.3.1` (`ca9c5927`).
- **1.3.1 (vC130) väntar bara på AAB:n.** Play-utkastet "130 (1.3.1)" i produktion har What's new, ny butikstext på tio språk och de nya butiksbilderna i Flock-looken (Albin: "The store looks perfect"). AAB:n `birdy-1.3.1-vc130-PRODUKTION.aab` ligger på Albins skrivbord och dras in av honom.
- **Webben** har tre nya eller omskrivna blogginlägg sedan i dag: "See the song", "Birdy × AlbIT: en rädsla för fåglar blev en app" och omskrivna "Varför Birdy finns", plus en polerad läsvy (läslinje överst, anfang, skribentruta, två fler fältanteckningar sist). Inget inlägg får nämna "full auto", att något körs utan tillåtelse eller citera interna regler (Albin 10 okt; enhetstest `tests/unit/field-notes-copy.unit.mjs`).
- **Sociala medier** är schemalagda till 7 nov, utom YouTube 29 okt till 7 nov, som laddas upp i kväll.

## Gör i den här ordningen

### 1. Skicka in 1.3.1

1. Albin drar `birdy-1.3.1-vc130-PRODUKTION.aab` från skrivbordet till produktionsutkastet "130 (1.3.1)" i Play Console. Chrome-fliken står på utkastet (Produktion, Förbereda ny version); rutan "Ladda upp AAB-arkiv" tar filen. Agentens filuppladdning klarar högst 10 MB, så det här steget är hans.
2. Agenten läser varningarna (väntat: saknade felsökningssymboler; 16 KB-kontrollen gav 15 av 15), kontrollerar versionsnamnet, What's new (en-US, sv-SE), butikssidans tio språk och bilderna, och **skickar in med 100 % utrullning** (Albins val 9 okt). En rad i #birdy-bygge.
3. När Google har godkänt: sätt datumet som reserv i `website/src/lib/release.mjs` (`APP_1_3_LIVE_FROM = process.env.BIRDY_APP_LIVE_FROM || 'ÅÅÅÅ-MM-DD'`), kör webbgrinden, pusha och kontrollera live. Premium-sidans texter är redan skrivna så att de stämmer för gratisversionen; datumet slår på tidslinjens datum och raden "samma fågel som i appen" på startsidan.

### 2. YouTube 29 okt till 7 nov (i kväll)

- Dagsgränsen släpper cirka 19.15. Ett engångsjobb i sessionen startar 19.23 om sessionen är öppen; annars gör nästa session det.
- Videorna är omrenderade på `main` i `tools/social/out/oct29-nov7/<slug>/` (logg `C:/w/social-rerender.log`, som ska sluta med `render=0`, `covers=0`, `titlecard=0`). Kontrollera att varje video är under 10 MB innan uppladdningen.
- Datumen följer ordningen som redan är publicerad på de andra kanalerna, inte den nya `schedule.csv`: 29 okt mute-swan, 30 eurasian-wren, 31 bohemian-waxwing, 1 nov european-goldfinch, 2 northern-raven, 3 fieldfare, 4 eurasian-bullfinch, 5 european-herring-gull, 6 yellowhammer, 7 brambling. Titel och beskrivning: `node tools/social/caption-fields.mjs <slug>` (YTTITLE, YTDESC).
- Recept och fällor: `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md` (YouTube-receptet under "Kvällen 9 okt", tidszonen "(GMT+02:00) Stockholm" uttryckligen, aldrig Escape i uppladdningsdialogen).

### 3. Siffrorna varje måndag (från mån 12 okt)

Per kanal: YouTube Studio, TikTok Studio, Meta Business Suite, Search Console, Vercel Analytics och Play Console. En rad per kanal i #birdy-marknad och i tabellen i Albins Claude Docs-dokument "Birdy: läget, varumärket och 12–24 månader framåt". Ett engångsjobb i sessionen startar mån 12 okt 09.07 om sessionen är öppen. **Öppet:** Albin frågade om körningen kan ligga i Cockpit (memory vault i Supabase) i stället; agenten erbjöd en plan, inget svar än.

### 4. Nästa vecka: den betalda releasen

Planen: `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` (uppdateringen överst och punkt 14, djuplänkar). Grindar: BirdNET:s svar, köptestet med vC129, MapTiler Flex med en ny nyckel som ersätter den låsta Default key, Resend för supporten och **brytpunkten = 1.3.1:s go-live + 48 h** (bygget stoppar utan den). Premiumbitarna: Märken 1b (och 1a om Albin säger ja), PDF 1, Pop-up 1, Intro 1, månadsprenumerationen 49 kr. Varje del får egen plan och körs med SDD i egen worktree under `C:/w/`; huvudagenten slår ihop.

### 5. Artsidorna (tidigast tors 15 okt)

100 av 180 arter är live. Publiceringen är pausad med `website/reports/STOP` i huvudklonen (gitignorerad; rapporterna från loopen ligger bredvid). Före återstarten: kolla Search Console (indexerat, "genomsökt men inte indexerat"). Återskapa sedan loopens worktree, som togs bort i städningen:

```bash
git worktree add -B publish/main C:/w/birdy-publish origin/main
cp -r website/reports C:/w/birdy-publish/website/      # STOP och rapporterna följer med
cd C:/w/birdy-publish/website && npm ci
cd ../tools/content-pipeline && uv sync
```

Ta bort STOP, publicera de två klara, sedan undantagen i våg 2 (Albins beslut i arket; säkra standardval om inget sägs: V1 tom, V2 och V4 behåll, V3 "Delvis flyttfågel, häckar här"), sedan jämn takt cirka 3 arter om dagen (`PLAYWRIGHT_CHANNEL=chrome bash scripts/publish-loop.sh N`). Våg 3 (56 arter, cirka 25 USD) när API-budgeten räcker (100 USD i månaden, oktober cirka 76 använda).

### 6. Nästa sociala serie (från 8 nov)

- **En liten förloppsmätare överst i klippen** (Albin 10 okt), byggd före nästa schemaläggning och visad för Albin i en förhandsvisning. Detaljerna står överst i runbooken.
- Nya hookar och längre bildtexter ur Albins dokument.
- Valfritt: byta YouTube 10 till 18 okt till flockversionerna (kräver omrendering av `week1` och `week1-reserves`).

## Väntar på Albin

- Dra in AAB:n i Play-utkastet.
- Beslut: löftet i forumtexterna "alla som laddar ner innan Premium börjar kosta får Premium gratis för alltid" (kräver brytpunkten i den betalda releasen, install referrer för Android 12 och äldre, ny webbtext); Märken 1a i den betalda releasen (rekommendation: ja, ihop med 1b); Cockpit-planen; undantagsarket för våg 2 före 15 okt.
- Köptestet med vC129 på sin telefon, MapTiler Flex, Resend-kontot, BirdNET-brevet, Slack Pro (då flyttar agenten kanaltexterna till canvas och "Väntar på Albin" till en lista).
- Klippsidan `birdy.community/clips/` i bion på Instagram, och på TikTok efter bytet till företagskonto (bara i appen). Plattformsegendomarna TikTok och Instagram i Search Console.
- Skicka SV/EN-adresserna till de nya blogginläggen till albit.se-sessionen: `https://birdy.community/sv/blog/birdy-x-albit/`, `https://birdy.community/blog/birdy-x-albit/`, `https://birdy.community/sv/blog/why-birdy/`, `https://birdy.community/blog/why-birdy/`.
- Valfritt: Meta-utkasten och de röda raderna i Business Suite, Facebooks Sharing Debugger för `/` och `/sv/`.
- iOS: sim-check, device-verify och Apple Developer-enrollment (`docs/ios-release-checklist.md`, som nu också listar tre Cursor-fixar för iOS att granska i i5).

## Städningen 10 okt (vad som hände)

- Grenarna `release/1.3.0`, `play/butiksbilder-flock`, `social/see-the-song` och `website/blogg` slogs ihop med `main`; övriga grenar arkiverades som taggar och togs bort lokalt och på GitHub. Cursors tre iOS-PR:er (#37, #38, #41) stängdes osammanslagna och står nu i iOS-checklistan; de fyra Android-fynden (#30, #34, #39, #40) stängdes redan 5 okt, åtgärdade på eget sätt i 1.3.0.
- Alla worktrees under `C:/w/` och tre gamla mappar i `.worktrees/` togs bort efter en kontroll att varje källfil redan fanns i git (bara `local.properties` fanns inte, som väntat). En gammal `birdy-release.apks` (1 GB, maj) togs bort ur huvudklonen.
- **Misstag:** `git worktree remove --force C:/w/birdy-social` raderade de gitignorerade renderade videorna i `tools/social/out/`. De tio som behövs (YouTube 29 okt till 7 nov) renderades om på `main` med exakt samma slutkort som de publicerade; omrenderingen hittade och rättade att slutkortet hade fått siluettens kredit som en extra rad (`d6e7274e`). Regeln står nu i trap-katalogen i CLAUDE.md.

## Fällor

- **Vercel "Git information retrieval failed"** kan drabba även produktion: bygg om med `gh workflow run daily-site-build.yml --ref main` och kontrollera driftsättningen med `gh api repos/anonadrek/birdy/deployments`. Hamra inte birdy.community med curl (Vercels säkerhetskontroll svarar 403); kontrollera i Chrome.
- **Chrome i bakgrunden** (fönstret skymt): skärmbilder tar timeout, klick och skrivning är opålitliga. Läs sidan med `javascript_tool`, en åtgärd per anrop.
- **Play Console:** "Lämna sidan?" betyder osparade ändringar. Bildbiblioteket väljs i ordning med "Knappen Välj".
- **Heredocs i bash tappar backslash-escapes**: skriv patchskript till en fil med Write och bygg specialtecken med `chr()`.

## Var saker ligger

| Vad | Var |
|---|---|
| Koden för 1.3.1 | `main`, tagg `v1.3.1` (`ca9c5927`) |
| AAB:n | Albins skrivbord, `birdy-1.3.1-vc130-PRODUKTION.aab` |
| Butikstexterna | `docs/play-store/console-paste-v1.3.1.md`, `docs/play-store/store-listing-translations-v1.3.1.md` |
| Butiksbilderna | `docs/play-store/store-assets/1.3.1-flock/{sv,en}/`, renderare `website/tools/store-assets/1.3/` |
| Sociala verktyg och videor | `tools/social/`, videorna i `tools/social/out/` (gitignorerat) |
| Den betalda releasen | `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` |
| Artsidornas paus | `website/reports/STOP` i huvudklonen |
| Äldre statusposter | `docs/superpowers/status-arkiv/2026-10-10-claude-md-status.md` |
| Föregående överlämning | `docs/superpowers/plans/OVERLAMNING-2026-10-09.md` |
