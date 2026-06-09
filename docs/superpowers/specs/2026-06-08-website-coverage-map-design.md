# Birdy täckningskarta för webben — design

**Datum:** 2026-06-08
**Status:** Design klar, väntar på review → implementationsplan
**Yta:** `website/` (Astro 5 + Tailwind v4), live på birdy.community

## Bakgrund & mål

En interaktiv världskarta på birdy.community som **ser ut exakt som appens kartvy** (Field
Journal, bläck-på-papper), men som illustrerar **var Birdy faktiskt fungerar**: stödda
länder/regioner fylls i Birdys koppar, resten lämnas som ren karta. Syftet är att kommunicera
nuvarande räckvidd (Europa, 839 arter) och signalera kommande expansion utan att skräpa ner
med text.

Designen är validerad genom 7 mockup-iterationer i en levande MapLibre-karta (riktiga MapTiler-
tiles + appens duotone) och verifierad med headless-skärmdumpar (Playwright) — inte gissningar.

### Låsta beslut

| Fråga | Beslut |
|---|---|
| Placering | **Egen sektion på startsidan** (i flödet, t.ex. mellan `Inside` och `Premium`) |
| Fynd-pins | **Dekorativa exempel-wax-seals** i det stödda området — INTE riktiga användarfynd |
| Täckning | **Kurerad, versionshanterad Europa-lista** (vi styr exakt) |
| Interaktion | **Dra & zooma** (besökaren utforskar själv) |

## Visuellt språk (matchar appen 1:1)

Tokens (samma som `composeApp/.../ui/theme/Color.kt`):

| Token | Hex | Roll på kartan |
|---|---|---|
| Paper | `#EFE7D6` | Land/bakgrund |
| Ink | `#2E2417` | Kustlinjer, gränser, vägar, etiketter (duotone-bläcket) |
| Copper (AccentCopper) | `#A8552D` | Fyllning av stödda länder + ring/spets på pin |
| Navy (StampNavy) | `#1F3A5F` | Birdy-fågeln i pin:en |
| Water-tint | `#DACFB2` | Hav/sjöar (en aning mörkare än papper) |
| Cream hi/lo | `#F4EDDC`→`#E5DBC4` | Wax-seal-discens radialgradient |

- **Wax-seal-pin** = spegel av `buildBirdySealMarker`: cream-disc (radialgradient) + 3 px
  kopparring + navy-tonad `hero_bird` (~60 % av discen) + koppar-spets nedåt; spetsens topp
  markerar koordinaten.
- **Rubrik** i DM Serif Display Italic (sajtens `JournalHeadline`), eyebrow + intro + bildtext i
  befintliga Field Journal-stilar, ornament `❦`.

## Teknisk arkitektur

### Kartmotor: vektor + runtime-omfärgning (INTE raster + CSS-filter)

Appen använder raster `toner-v2` + ett duotone-`ColorMatrix`-filter. På webben vore den literala
motsvarigheten raster + CSS-`feColorMatrix`, **men** då hamnar allt på ett filtrerat canvas, och
en koppar-fyllning som läggs ovanpå antingen (a) täcker kartans etiketter, eller (b) tonas bort
av filtret. Vi bekräftade dessutom att MapTiler **inte** exponerar toner-etiketter som eget lager
(`toner-v2-labels` → 404), så raster kan aldrig få kopparn *under* texten.

**Lösning:** MapLibre GL JS med MapTiler **vektor**-stilen `dataviz-light`, **omfärgad i runtime**
till bläck-på-papper. Inget CSS-filter behövs → koppar-fyllningar renderas i sant färg och kan
ligga **under etikett-lagren** (landsnamn överst, skarpa). Omfärgningen är en loop över
`map.getStyle().layers`:

- `background` → Paper
- `fill` som matchar `/water|sea|ocean|lake|river/` → Water-tint; övriga fill → Paper
- `line` → Ink
- `symbol` → `text-color` Ink, `text-halo-color` Paper, `text-halo-width` ~1.4

### Stödda områden (koppar-fyllning)

- En **lokalt buntad, förenklad GeoJSON** med kurerade europeiska länder
  (`website/src/data/coverage-europe.geojson`). Hämtas INTE från tredjepart i runtime
  (mockupens jsDelivr-hämtning var bara demo); buntas i repo:t, förenklad med mapshaper för
  liten payload.
- Läggs som **fill-lager** (Copper, opacity ~0.42) + **line-lager** (mörkare koppar-outline
  `#8f4422`), bägge insatta **före första `symbol`-lagret** → etiketterna ligger överst.

### Pins (dekorativa)

- Wax-seal som **ikon i ett `symbol`-lager** (`icon-anchor: 'bottom'`), placerad på några få
  representativa europeiska koordinater. Ikonen genereras antingen (a) förrenderad PNG @2x-asset,
  eller (b) ritad till en offscreen-canvas från befintlig bird-asset och adderad via
  `map.addImage(..., {pixelRatio:2})`.
- **Varför symbol-lager och inte HTML-markörer:** custom-element-HTML-markörer drev systematiskt
  söderut (Paris hamnade i Afrika vid utzoomning) p.g.a. container-timing/ankar-quirk; ett
  symbol-lager använder kartans egen projektion och landar exakt rätt på alla zoomnivåer. Detta
  är också närmare appen (osmdroid ritar en bitmap-markör).

### Attribution

- `© MapTiler © OpenStreetMap contributors` nere till vänster (krav från MapTiler ToS).

## MapTiler-nyckel

- Vektor-tiles kräver en API-nyckel klient-sidan. Skapa en **separat webb-nyckel**, **domänlåst**
  till `birdy.community` (+ Vercel preview-domäner + `localhost` för dev). Återanvänd INTE appens
  nyckel rakt av — domänlåsning på en delad nyckel kan brösta Android-trafiken (appen skickar inga
  HTTP-referrers).
- Lagras som `PUBLIC_MAPTILER_KEY` (Astro public env / Vercel env-var). Aldrig committad.
- Kostnad: täckningskartans trafik är låg; MapTiler gratisnivå räcker sannolikt. Övervaka i
  MapTiler-dashboarden.

## Interaktivitet & robusthet

- Pan + zoom (scroll/drag). `dragRotate` av, rotation av. `minZoom` ~1.1, `maxZoom` ~6–8.
  Default Europa-vänd vy (center ~`[12, 40]`, zoom ~2.1), dragbar till hela världen.
- **`map.resize()`** efter att containern fått sin höjd via aspect-tricket — annars ligger
  canvasens storlek i otakt med containern och pins/projektion driver söderut (verifierad bugg).
  Använd `ResizeObserver` + `load` + `requestAnimationFrame`.
- **`cooperativeGestures`** (eller motsvarande "två fingrar / klicka för att interagera") så att
  mobil-scroll inte fastnar i kartan.
- **Progressiv förbättring / prestanda:** maplibre-gl är ~200 KB gzippat. Lazy-initiera kartan när
  sektionen scrollas in (`IntersectionObserver`) för att skydda startsidans LCP. Visa en
  **statisk förrenderad poster** (WebP av kartan) tills dess och som `<noscript>`-fallback.

## i18n (EN/SV)

- Rubrik, eyebrow, intro, "dra för att utforska"-hint och bildtext via befintliga
  `copy.{en,sv}.json`.
  - Roadmap-rad: EN "Europe today — the rest of the world is coming." /
    SV "Europa idag — resten av världen är på väg."
- Kartans ortnamn kommer från MapTiler (lokala/engelska namn) — inte vår i18n.

## Integritet

Pinsen är **dekorativa exempel**, inte riktiga användarfynd. Copy + `aria` får inte antyda att vi
publicerar användarens platser. Kartan är en ren täcknings-/marknadsillustration och rör därmed
**inte** löftet "almost nothing collected, data stays on phone".

## Komponenter / filstruktur (Astro)

- `website/src/components/Coverage.astro` — sektionen (eyebrow + `JournalHeadline` + intro +
  map-container + bildtext + attribution). Återanvänder `EyebrowLabel`, `JournalHeadline`,
  `OrnamentRule`, ev. `CornerBrackets`.
- Klient-`<script>` i komponenten: initierar MapLibre, omfärgar stilen, lägger koppar-fill +
  pin-lager; lazy via `IntersectionObserver`.
- `website/src/data/coverage-europe.geojson` — kurerad, förenklad.
- Wax-seal-ikon: `website/src/assets/seal@2x.png` (förrenderad) **eller** canvas-bygge från
  befintlig bird-asset (`src/assets/birdy-icon.png` / `hero_bird`).
- Statisk fallback: `website/src/assets/coverage-fallback.webp` (förrenderad kart-poster).
- npm-dep: `maplibre-gl` (+ dess CSS).
- Wire in i `src/pages/index.astro` och `src/pages/sv/index.astro` i sektionsflödet.

### Kurerad täckningslista (utgångspunkt — justeras mot species-täckningen i implementationen)

**Inkludera:** EU27 + Norge, Island, Storbritannien, Schweiz, Liechtenstein, mikrostater (Andorra,
Monaco, San Marino, Vatikanstaten, Malta, Luxemburg), Västra Balkan (Serbien, Bosnien-Hercegovina,
Montenegro, Nordmakedonien, Albanien, Kosovo), Moldavien, Ukraina, Vitryssland, Färöarna, Cypern.

**Exkludera:** Ryssland, Turkiet, Armenien, Azerbajdzjan, Georgien, Israel (skapar fula bbox-klipp
och ligger utanför "kärn-Europa").

## Testning

- **Playwright smoke** (utöka `website/tests/smoke.spec.ts`): sektionen renderas, map-canvas finns,
  attribution finns, inga console-errors; fallback-poster syns utan JS.
- **i18n-paritet** (`npm run test:i18n`): nya copy-nycklar finns i både EN och SV.
- **Visuell verifiering** via headless-skärmdump (samma Playwright-metod som i brainstormen):
  bekräfta koppar-Europa, etiketter överst, pins korrekt placerade, inget i Afrika.

## Utanför scope (YAGNI)

- Riktiga användarfynd / molndata på kartan (bryter integritetslöftet).
- Fullständig `/regions`-sida med ✅/🟡/⬜-status — det är en v2-roadmap-grej; den här sektionen
  kan så fröet senare.
- Klickbara pins/popups, sök, geolokalisering, fler regioner än Europa (läggs till när content-
  pipelinen täcker dem).

## Öppna punkter att bekräfta i implementationen

1. Exakt kurerad landlista mot `species_list.yaml`-täckningen.
2. Förrenderad seal-PNG vs runtime-canvas (bägge funkar; PNG är enklast/skarpast).
3. Separat MapTiler-webbnyckel skapad + domänlåst (manuellt steg i MapTiler-dashboarden).
