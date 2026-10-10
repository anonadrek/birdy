# Överlämning 2026-10-10 (natten till lördag)

Skriven lör 10 okt 02.10 på Windows-maskinen. **Nästa session börjar här.** Chatten med Albin är på engelska, repot och Slack på svenska.

## Läget på en minut

- **1.3.1 är byggd, testad och nästan inskickad.** vC130, gratis (Premium öppet för alla som i juni, brytpunkt 0). AAB:n ligger på skrivbordet: `birdy-1.3.1-vc130-PRODUKTION.aab` (496 MB, byggd 9 okt 23.03). Emulatortestet är godkänt (API 36 och API 30). Play-utkastet "130 (1.3.1)" har What's new (en-US, sv-SE) och butikssidan på alla tio språk med ny text. **Inget är inskickat.**
- **Två saker saknas före inskicket:** (1) butiksbilderna i Flock-looken, som Albin vill ha omgjorda med bilder som visar appen som den ser ut i dag, i ultra premium; (2) AAB:n i utkastet, som Albin drar in själv (agentens filuppladdning klarar högst 10 MB).
- **Webbgalleriet är live** sedan lör 10 okt 02.19 (Albins OK).
- **Sociala medier** är schemalagda till 7 nov, utom YouTube 29 okt till 7 nov.

## Gör i den här ordningen

### 1. Butiksbilderna i Flock-looken, ultra premium

Albin 10 okt: "Fixa flock till detta släpp, få in bilder som visar mer hur det ser ut idag, även statistiksidan och annat" och "designen ska vara ultra premium design, vi bygger något extremt stort här". Han stoppade agenten efter 01.40 för att ta resten i en ny session.

**Var:** worktree `C:/w/birdy-butik-flock`, gren `play/butiksbilder-flock` (pushad).

- `90b56d59`: första Flock-varianten (variant F, sex kort med 1.3.0-fångster; skärm 01 har ett tankstreck i appens rad, vilket 1.3.1 inte har). Förhandsvisning: https://claude.ai/artifact/NU19joWszL2D9DPLtewjaG
- `5c0b3a80` (WIP): åtta egna kort i `website/tools/store-assets/1.3/copy.json` under `flock.cards` (Identifiera, Lyssna, Match, Artprofil med Tallbit, Mina arter, Säsongsstatistik med Premium, Karta med Premium, Uppslagsverk). `template.html`, `render.mjs` och `preview-flock.mjs` är påbörjade. 1 av 8 färska fångster finns: `docs/play-store/screenshots/1.3.1/sv/01-identifiera.png`.

**Kvar:**

1. Fånga resten ur 1.3.1-bygget på emulatorn `pg-api36` (en emulator och ett Gradle-jobb åt gången, stoppa daemonerna efteråt): sv 02 till 08 och en 01 till 08, med appens språk sv-SE respektive en-US. Använd fynd med fria foton (CC0, public domain, CC BY, CC BY-SA, som galleriets skärm 12). Statistiken behöver fynd över flera månader och kartan fynd med plats. Inga privata foton eller riktiga platser i bild.
2. Rendera båda språken och feature-grafiken. Ribban är "ultra premium": en idé per kort, lugn komposition, inga dekorationer ovanpå skärmen. Reglerna står i `copy.json`: appens egna ord i kickern, inga tankstreck, inga utropstecken, inga precisionssiffror, inget "gratis".
3. Ny förhandsvisning som artefakt med båda språken, länken till Albin, vänta på OK.
4. Byt bilderna i Play-utkastet (en-US och sv-SE; standardgrafiken gäller de övriga språken). Välj bilderna ur biblioteket i rätt ordning med "Knappen Välj" och spara utkastet.

**Fråga i korten:** "Uppslagsverk · 839 arter" och "Europas fåglar" binder Birdy till Europa, något profilerna undvek 8 okt. Fråga Albin eller välj en formulering utan siffran.

### 2. AAB:n och inskicket

1. Albin drar `birdy-1.3.1-vc130-PRODUKTION.aab` från skrivbordet till produktionsutkastet "130 (1.3.1)" i Play Console (Chrome-fliken står kvar på utkastet).
2. Agenten läser varningarna (väntat: saknade felsökningssymboler; inget om 16 KB, kontrollen gav 15 av 15), kontrollerar What's new, butikssidans tio språk och de nya bilderna, och **skickar in med 100 % utrullning** (Albins val 9 okt). En rad i #birdy-bygge.
3. När Google har godkänt: sätt `APP_1_3_LIVE_FROM` i `website/src/lib/release.mjs` till dagen, kör grinden, slå ihop och kontrollera live.

### 3. Webbgalleriet: live

- Albin gav OK natten till lör 10 okt ("Ok make it live"). `website/galleri` slogs ihop med `main` som `208028ef` och var live 02.19, kontrollerat på `/` och `/sv/` (tre Premium-kort, ingen sävsångare).
- Grinden kördes på exakt den kombinationen: verify:fixtures (check-seo 68/58/56, unit, i18n 473 nycklar, inga tankstreck, palett, kontrast 33 par, förhandsbygget), accuracy, tomma hubben, astro check 0 fel, Playwright 273 (ett arbetarkrasch-fel, Windows-kod 3221225477, omkört grönt tillsammans med hela `faltbok.spec.ts` och `home.spec.ts`, 131 av 131).
- Albin såg först den gamla versionen eftersom Vercels bygge av `b3e7ee4f` föll på "Git information retrieval failed for this deployment"; en tom commit (`cf7090a7`) byggde om förhandsvisningen.
- Grenen `website/galleri` ligger kvar på GitHub.

### 4. Sociala medier

- **YouTube 29 okt till 7 nov** (10 Shorts) när dagsgränsen har släppt, cirka 19.15 lör 10 okt. Titlarna kommer från `youtubeTitle` på `social/see-the-song` (`960f8328`): "Would you recognise this bird by its sound? {EN} ({SV}) #shorts". Recept och fällor: `docs/superpowers/runbooks/2026-10-08-sociala-schemalaggning.md`.
- **Albin:** `birdy.community/clips/` i bion på Instagram, och på TikTok efter bytet till företagskonto (bara i appen).
- **Från 8 nov:** nästa omgång får de nya hookarna och de längre bildtexterna ur Albins doc "Birdy: läget, varumärket och 12–24 månader framåt" (i hans artefaktlista).
- Valfritt: de gamla Meta-utkasten (12 till 28 okt) kan tas bort; de publiceras inte.

### 5. Siffrorna varje måndag

Albin 10 okt vill att agenten hämtar siffrorna per kanal varje måndag (YouTube Studio, TikTok Studio, Meta Business Suite, Search Console, Vercel Analytics, Play Console) och skriver en rad per kanal i #birdy-marknad och i tabellen i Albins doc. Första gången mån 12 okt.

**Öppet:** Albin frågade om körningen kan ligga utanför datorn, i Cockpit (memory vault i Supabase). Agentens svar: ja, ett veckojobb (GitHub Actions eller en Supabase-funktion på timer) som hämtar via plattformarnas API:er och skriver en rad per kanal i en tabell som Cockpit läser. YouTube, Search Console, Meta och Play är lätta att koppla; Vercels besökssiffror saknar tydligt publikt API och TikTok kräver en godkänd utvecklarapp. Agenten erbjöd en plan för Cockpit-versionen, inget svar än.

### 6. Öppna beslut för Albin

- **Löftet i forumtexterna** ("alla som laddar ner innan Premium börjar kosta får Premium gratis för alltid") kräver att den betalda releasen sätter brytpunkten, install referrer för Android 12 och äldre (nätverkstiden finns bara från API 33) och ny webbtext. Beslut före forumpostningen.
- **Svenska skärmbilder per språk i Play** (i dag visar alla språk standardgrafiken).
- **Märken 1a** i den betalda releasen (agentens rekommendation: ja, ihop med 1b).
- **Cockpit-planen** för siffrorna (punkt 5).

### 7. Nästa vecka: den betalda releasen

Planen: `docs/superpowers/plans/2026-10-08-1.3.1-samlad-release.md` på `release/1.3.0` (uppdateringen överst och punkt 14, djuplänkar). Grindar: BirdNET:s svar, köptestet med vC129, MapTiler Flex med en ny nyckel (som ersätter den låsta Default key), brytpunkten (bygget stoppar utan den) och Resend för supporten. Premiumbitarna: Märken 1b, PDF 1, Pop-up 1, Intro 1 och månadsprenumerationen 49 kr.

### 8. Artsidorna

Pausade till tidigast tors 15 okt (`reports/STOP` i `C:/w/birdy-publish/website`). Före återstarten: worktreen ligger 86 commits efter `origin/main`, så uppdatera den först (när ingen loop går), kolla Search Console och följ sedan ⏸️-posten i CLAUDE.md.

## Fällor från natten

- **Vercel "Git information retrieval failed":** förhandsvisningen visar då förra versionen, och det syns inte på sidan. Kontrollera alltid `gh api repos/anonadrek/birdy/commits/<sha>/status` (Vercel ska vara success) innan en länk skickas. En tom commit bygger om.
- **Chrome i bakgrunden** (fönstret skymt): klick, klistra och skriv är opålitliga och timers stryps. Använd JavaScript med inbyggda värdesättare och input-händelser, en åtgärd per anrop, och läs tillbaka.
- **Play Console:** "Lämna sidan?" betyder osparade ändringar: välj "Stanna kvar" och spara. Bildbiblioteket väljs i ordning med "Knappen Välj" (förra gången hamnade bilderna i ordningen 02, 03, 01). Lokaliserad grafik i sv-SE-vyn var otydlig; kontrollera en-US efteråt.
- **Filuppladdningen i Chrome-verktyget** klarar högst 10 MB totalt, så AAB:er laddar Albin upp.
- **Klockslag och veckodagar** tas ur `date` (11 okt är en söndag).

## Var saker ligger

| Vad | Var |
|---|---|
| 1.3.1-koden | `C:/w/birdy-130`, `release/1.3.0` `c4d2ab49` |
| AAB:n | skrivbordet, `birdy-1.3.1-vc130-PRODUKTION.aab` |
| Butiksbilderna | `C:/w/birdy-butik-flock`, `play/butiksbilder-flock` `5c0b3a80` |
| Webbgalleriet | `C:/w/birdy-galleri`, `website/galleri` `cf7090a7` |
| Sociala verktyg | `C:/w/birdy-social`, `social/see-the-song` `960f8328` |
| Artsidornas loop | `C:/w/birdy-publish` (pausad) |
| Butikstexterna | `docs/play-store/console-paste-v1.3.1.md`, `docs/play-store/store-listing-translations-v1.3.1.md` |
| Föregående överlämning | `docs/superpowers/plans/OVERLAMNING-2026-10-09.md` |
