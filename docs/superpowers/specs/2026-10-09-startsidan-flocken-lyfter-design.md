# Startsidans hjälte: Flocken lyfter (design, 2026-10-09)

**Status:** godkänd av Albin 2026-10-09 ("remove, yes": ingen handskriven rad bredvid flocken; rubriken är sloganen). Bygger på rörelseprototypen som Albin godkände 2026-10-08 ("Ser kanon ut", "Sen är vi nöjda", "Mycket vackert"): `docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html` (version 3, utan pilen), publicerad som https://claude.ai/artifact/Y9PWpnzdTSkPrgyGoYtgY1. Albin 2026-10-09: "När gör vi flocken lyfter?" och "viktigt att ha lite kodat ... så den rör sig, lite sånt överallt, speciellt på landningssidan".

## Vad som byggs

Startsidans första vy (`website/src/components/Hero.astro`, båda språken) byts från dagens espressovägg med Dagens fågel som plansch till Flock-hjälten:

1. Persikopapper (de sociala profilernas färger: persika, espresso, rost, koppar, mässing, aprikos).
2. Rubriken till vänster (under flocken på telefon): kickern "Kamera, foto eller läte", rubriken "Känn igen fågeln." och den handskrivna raden "Bevara stunden." (engelska: "Know the bird." / "Keep the moment.", samma slogan som profilerna), brödtexten och Play- och App Store-märkena som i dag.
3. **Rörelsen, en gång per sidvisning:** 839 små fåglar (en per art i Birdy) flyger in nerifrån vänster längs strömmen under texten, virvlar kort och landar som Birdys fågel. Dagens fågel landar sist och tänds. Sedan lyfter Dagens fågel-fotot ur just den fågeln, flyger till sin plats, framkallas som en polaroid och tejpas fast. Därefter står allt still. Ingen pil och ingen anteckning ovanpå eller bredvid flocken: prototypens handskrivna rad "en fågel i flocken för varje art i Birdy" tas bort (Albin 2026-10-09). Idén bärs av rörelsen och av canvasens tillgängliga text.
4. **Minskade rörelser** (`prefers-reduced-motion`) och sidan utan JavaScript visar slutbilden direkt: flocken som Birdys fågel med dagens fågel tänd och polaroiden på plats.

## Dagens fågel

- Samma fågel och samma regler som dagens plansch: `siteDailyBird` och `plateImage` i `src/lib/daily-bird.mjs`, ombyggt varje natt av `daily-site-build.yml`. Fotot visas helt (aldrig beskuret, inget ritat på det), därför får det vara CC0, public domain, CC BY eller CC BY-SA, som i dag.
- Polaroidens text: "Dagens fågel: {namn}" och fotokrediten med samma rad som artsidan (`PhotoCredit.astro`). Polaroiden länkar till artsidan. Tejpen sitter på kortets kant, aldrig över fotot.
- Raden "samma fågel som i appen i dag" visas bara när `sameAsApp` är sant, med dagens vakt (`same-as-app-guard.ts`) kvar.
- **Vilken fågel i flocken som tänds** bestäms av artens QID med en fast avbildning (samma art får alltid samma plats i flocken), så att idén "en fågel per art" håller över tid och kan återanvändas på artsidorna senare.

## Teknik

- Ingen ny beroende. Flockdatan (`flock-data.js`, cirka 25 kB) flyttas in som modul under `website/src/components/hero/`. Rörelsen ritas på en `<canvas>` med en bitmap av Birdy-märket per färg (som prototypen), högst `devicePixelRatio` 2, cirka 4 sekunder, startar när hjälten syns och stoppar helt efteråt (ingen loop, ingen timer kvar).
- Slutbilden (för minskade rörelser, utan JavaScript och för delningsbilder) renderas i samma komponent: en statisk SVG eller förrenderad bild av den landade flocken med dagens fågel tänd, så att sidan aldrig visar en tom yta.
- Inga layoutskift: canvas och polaroid har reserverad plats. Rubriken är sidans största innehåll (LCP), inte bilden.
- Menyn (`Nav.astro`, varianten `overlay`) får mörk text på persika ovanför hjälten i stället för ljus text på espresso. Övriga sektioner, sidfoten och menyns stil i övrigt ändras inte i det här steget.

## Kontroller

- Vakterna: `test:i18n` (nya nycklar på båda språken), `test:no-dashes`, `test:contrast` (texten på persika), `test:palette`, `test:no-accuracy`.
- Playwright: hjälten finns på båda språken, canvasen finns, med `reducedMotion: 'reduce'` visas slutbilden direkt, polaroiden visar dagens fågel och länkar till artsidan, inga fel i konsolen, inget horisontellt skrollande vid 390 px.
- axe 0 fel; Lighthouse mobil på `/sv/` minst lika bra som i dag (93/100/96/100), CLS 0.
- Förhandsvisning på en Vercel-förhandsadress som Albin tittar på i telefonen innan något går live.

## Inte i det här steget

Menyns och sidfotens nya stil, inläggen, artsidorna, övriga sektioner på startsidan och galleriets små rörelser (egna steg i Flock-looken). Den öppna frågan om vad flocken betyder på de andra sidorna (fråga 1 i brainstormen) påverkar inte hjälten.

## Albins svar 2026-10-09

1. Den handskrivna raden bredvid flocken: bort.
2. Rubriken "Känn igen fågeln. Bevara stunden." / "Know the bird. Keep the moment.": ja.
