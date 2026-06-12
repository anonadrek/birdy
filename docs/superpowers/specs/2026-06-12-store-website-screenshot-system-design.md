# Store & website screenshot system — design

**Datum:** 2026-06-12
**Status:** Spec (väntar på användargranskning)
**Relaterat:** CLAUDE.md follow-up #9 (färska v1.2-skärmdumpar/feature-graphic), #8 (store-listing), [[project_prelaunch_growth_goals]] (mjuk-blocker inför publik launch)

## Mål

Ta de 11 råa v1.2-skärmdumparna (`Birdy AB/Birdy Screendumps v.1.2rc/*.jpg`) och göra ett **kod-drivet, reproducerbart designsystem** som genererar polerade marknadsbilder för **två kanaler samtidigt**:

1. **Google Play** — 8 telefonbilder (1080×1920, EN) i Field Journal-stil.
2. **Birdy-hemsidan** — bakgrundsfria telefon-urklipp för Glimpse-carousellen.

Allt renderas från HTML/CSS via Playwright (redan beroende i `website/`), så bilderna är versionshanterade och kan regenereras när UI eller copy ändras. Inga manuella Figma/Photoshop-steg.

## Visuell design (LÅST i brainstorm 2026-06-12)

Kompositionsmall **A — "Plåt & bildtext"**, master-kort godkänt via visual companion. Per Play-kort, uppifrån och ner på pappersbakgrund:

- **Hörn-brackets** (koppar, ytterhörn upp-vänster + ner-höger).
- **Räknare** uppe till höger: `0X / 08` (siffran fet i bläckgrön, "/ 08" i moss).
- **Copy-block** (vänsterställt, överst):
  - Eyebrow — Inter 700, versaler, letterspacing, koppar.
  - Rubrik — DM Serif Display, bläckgrön (`--ink`), där **sista ordet är en Caveat-accent i koppar** (större).
  - Underrad — Caveat 600, moss.
  - Premium-kort har en liten koppar-"Premium"-pill efter underraden.
- **Skärm** — den riktiga skärmdumpen, centrerad i den fria ytan under copyn, 5px vit kant + rundade hörn + mjuk skugga.
- **Wordmark** — "Birdy." i koppar-Caveat nere till vänster (ingen logga/cirkel — explicit bortvald).

### Tokens & typsnitt
Field Journal-paletten (CLAUDE.md): Paper `#EFE7D6`, PaperEdge `#E5DCC7`, Ink `#2A3520`, Moss `#3F4F30`, Copper `#A8552D`. Typsnitt: DM Serif Display (rubrik), Caveat (accent/underrad/wordmark), Inter (eyebrow). Laddas via Google Fonts i mallen (samma familjer som appen/sajten).

### Proportioner (relativt 1080px bredd)
Master-kortet godkändes vid 340px → skalas linjärt till 1080px. Riktvärden vid 1080: eyebrow ~41px, rubrik ~127px, Caveat-accent ~159px, underrad ~73px, wordmark ~108px, brackets ~108px, räknar-siffra ~57px. (Findjusteras visuellt vid första render.)

## De 8 korten (ordning + copy, EN)

De första 3 syns i Play-sökresultaten → bär hooken.

| # | Skärm | Källfil | Eyebrow | Rubrik (accent **fet**) | Underrad | Premium |
|---|-------|---------|---------|-------------------------|----------|---------|
| 1 | Identify | 1000039462 | Identify · in the field | Three ways to **catch.** | Camera, photo or call. | — |
| 2 | Listen | 1000039460 | Audio ID · on-device | Just **let it sing.** | A 3-second song is enough. | — |
| 3 | Journal | 1000039468 | Your field book | Every find, **a page.** | Yours. On your phone. | — |
| 4 | Archive | 1000039464 | Encyclopedia · 839 species | Know your **birds.** | Search, filter, learn. | — |
| 5 | Badges | 1000039470 | Stamps to chase | Come home with **a stamp.** | 34 to collect, 27 free. | — |
| 6 | Map | 1000039478 | Your private map | Everywhere you've **been.** | On-device, private. | ✓ |
| 7 | Weekly recap | 1000039476 | Field report | A week in the **field.** | Your sightings, recapped. | — |
| 8 | Season stats | 1000039474 | A year in the field | Patterns **emerge.** | Your year, charted. | ✓ |

**Utanför urvalet (3):** Archive-listan (1000039466, dublett av #4), Troférum-detaljen (1000039472, dublett av #5), kart-Norden (1000039480 — vi tog inzoomade Field Journal-kartan istället).

## Canvas-storlekar & källstädning

- **Play-kort:** 1080×1920 (9:16). De råa dumparna är 1080×2316 (2,14:1) → **för avlångt för Play (max 2:1)**. Kortets pappers-canvas är 1080×1920; skärmdumpen sitter inuti med sitt eget förhållande.
- **Website-urklipp:** transparent PNG, bara den vit-inramade skärmen (samma `.screen`-element som i Play-kortet, utan papper/copy/brackets/wordmark). Glimpse-CSS lägger på drop-shadow. ~900×1500 med transparent marginal för skuggan.
- **Källstädning:** beskär bort OS-statusraden (övre ~88px: klocka/batteri/notiser) och Android-navfältet (nedre ~130px: ◁ ○ ▢) ur varje skärmdump, **behåll appens egen bottenflik-rad**. Görs med `sharp` (redan via Astro) som ett försteg; exakta pixelvärden findjusteras per dump.

## Arkitektur — generatorn

Allt under **`website/tools/store-assets/`** (återanvänder `website/`:s Playwright + sharp; ny npm-script `assets:store`):

```
website/tools/store-assets/
  template.html        # parametriserad A-mall (1 kort). Läser ?card=01 → fyller från data
  cards.en.json        # [{id, eyebrow, headlineHead, headlineAccent, body, premium, src}]
  cards.sv.json        # tom mall, samma nycklar — fylls vid SV-rendering (out of scope nu)
  screens/             # de 8 städade källdumparna (efter sharp-crop)
  render.mjs           # Playwright: per kort → 1080×1920 PNG (Play) + transparent cut-out (web)
  clean-sources.mjs    # sharp: crop OS-chrome från råa dumpar → screens/
```

**`render.mjs`-flöde:**
1. Läs `cards.en.json`.
2. För varje kort: öppna `template.html` med kort-data injicerat, viewport 1080×1920 (deviceScaleFactor 2 för skärpa → nedskala vid behov), vänta på fonts, `page.screenshot()` → **Play-PNG**.
3. Rendera samma skärm som enbart `.screen`-elementet på transparent bg (`omitBackground:true`, element-screenshot) → **website-urklipp**.

**Datadrivet & SV-redo:** copy + filnamn bor i `cards.*.json`. SV = kopiera `cards.en.json` → `cards.sv.json`, översätt strängar, kör `render.mjs --locale sv`. Ingen mall- eller kodändring.

## Output & wiring

- **Play:** `Birdy AB/Birdy Screendumps v.1.2rc/Play Store/01-identify-en.png` … `08-season-en.png`.
- **Website (granskning):** `Birdy AB/Birdy Screendumps v.1.2rc/Website/01-identify.png` … (transparenta urklipp).
- **Website (live-wiring):** samma urklipp skrivs även till `website/src/assets/screens/` och `Glimpse.astro` uppdateras till den nya v1.2-uppsättningen (8 shots + alt-texter i `i18n`). Dagens 5 `dev_*.png` ersätts/utökas.

## Utanför scope (medvetet)
- **Feature graphic** (1024×500) — separat pass.
- **SV-renderade bilder** — mallen + datafilen görs SV-redo, men SV-PNG:er genereras senare.
- De 3 bortvalda skärmarna.
- Play Console-uppladdning (manuellt UI-steg, [[project_final_v1_2_build_checklist]]).

## Verifiering
- Alla 8 Play-PNG:er är 1080×1920, ≤8 st, förhållande ≤2:1 (Play-krav).
- Fonts renderade (inga fallback-serifer) — visuell kontroll av första batch.
- OS-chrome bortstädad, appens bottenflik kvar.
- `cd website && npm run build` grön efter Glimpse-wiring; `npm run test:smoke` grön.
- Visuell granskning av alla 8 + urklippen mot master-kortet.

## Öppna följdpunkter
- SV-render när vi vill (datafil + `--locale sv`).
- Feature graphic-pass.
- Ev. byta in en bortvald skärm om någon känns starkare i skarpt läge.
