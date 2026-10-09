# Klippsidan: alla klipp med länk till artsidan (design, 2026-10-09)

**Status:** godkänd av Albin 2026-10-09 ("Kör") på förslaget i chatten: en sida på birdy.community som listar klippen, var och en med länk till sin artsida, och som blir bio-länken på Instagram och TikTok. Länkar från artsidorna till klippen görs inte nu.

## Varför

Inläggen på Facebook och YouTube länkar redan direkt till artsidan. Instagram och TikTok kan inte ha klickbara länkar i texten och säger "Link in bio", men bion går till startsidan, så den som just sett tranan får leta. Klippsidan blir bio-länken: ett tryck till klippen, ett till artsidan.

## Vad som byggs

1. **Två sidor:** `/clips/` (engelska, som bildtexterna) och `/sv/klipp/` (svenska), varandras språkversioner (hreflang), i sitemapen, med egen titel och beskrivning per språk. Rubriken bär seriens namn "See the song" som blogginlägget med samma namn, och en kort ingress om vad klippen är (hör fågeln först, se den sedan).
2. **Ett kort per klipp, nyaste först:** klippets eget flockomslag (samma bild som inläggets omslag, 9:16), fågelns namn på sidans språk, publiceringsdagen och en länk till artsidan på sidans språk. Under kortet silhuettens kredit som i bildtexterna ("Silhouette: {namn}, {licens}, via PhyloPic", CC BY-silhuetter märkta som bearbetade).
3. **Sidan sköter sig själv:** ett klipp syns från och med sin publiceringsdag (Europe/Stockholm), med samma datumkälla som Dagens fågel (`virtual:birdy-daily-bird`, `BIRDY_TODAY` i testbygget). Nattbygget (`daily-site-build.yml`, 00.05) lägger alltså till dagens klipp utan handpåläggning. Inga inläggsadresser behövs.
4. **Bara publicerade artsidor länkas.** Ett klipp vars art saknar publicerad sida visas utan länk (samma vakt som blogginläggets länklista). Namnet tas då ur klippdatan.
5. **Sidfoten** får en länk till klippsidan ("Klipp" / "Clips") bredvid de sociala kanalerna, så att sidan inte står föräldralös.
6. **Utan klipp** (bara i testbyggen före första datumet) visar sidan en rad om att det första klippet kommer snart.

## Data och bilder

- `website/src/data/clips.json`: ett objekt per klipp med datum, QID, slug, namn (sv, en, vetenskapligt) och silhuettens kredit (författare, licens, adress, bearbetad). Skrivs av ett skript ur `tools/social/out/*/schedule.csv` och `tools/social/cover/covers.json` på `social/see-the-song` (worktree `C:/w/birdy-social`), så att sidan och inläggen alltid har samma dagar. Omslagen kopieras till `website/src/assets/clips/<slug>.jpg` och visas med `astro:assets` i lagom storlekar (lat laddning utom första raden).
- De 30 klippen 9 oktober till 7 november. Nya klipp senare läggs till genom att köra skriptet igen.

## Ramar

- Inga inbäddade spelare, inga skript eller kakor från plattformarna, ingen spårning utöver sajtens befintliga Vercel Analytics. Löftet om att nästan inget samlas in gäller.
- Flock-looken: persikopapper som de sociala profilerna (`--peach` finns på `main`), sajtens typsnitt, inga tankstreck i texterna, kontrast enligt vakten.
- Ingen ny beroende.

## Kontroller

- Vakterna: `test:i18n`, `test:no-dashes`, `test:contrast`, `test:palette`, `test:no-accuracy`.
- Playwright: båda språken; testbygget (15 okt) visar klippen 9 till 15 oktober och inga senare, nyaste först; varje länk går till en publicerad artsida på rätt språk; krediterna finns; inga anrop till andra värdar än sajten; inget horisontellt skrollande vid 390 px; sidfotslänken finns.
- axe 0 fel på båda sidorna.
- Förhandsvisning på en Vercel-förhandsadress som Albin tittar på innan något går live.

## Efter att sidan är live (Albins steg)

- Instagram: Redigera profil, Länkar, lägg in `birdy.community/clips/` (bara i appen).
- TikTok: när kontot är ett företagskonto, Redigera profil, Webbplats, samma adress (bara i appen).

## Inte i det här steget

Länkar från artsidorna till klippen (senare kanske klippet självt på artsidan, som egen fil som spelas vid tryck), inläggsadresser, inbäddade spelare.
