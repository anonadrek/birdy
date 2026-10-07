# Artsidor fas 2: sidorna på birdy.community, implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Reviderad 2026-10-01** efter specens revision samma dag (faktablad, flera källor, datamoduler, inspelningar, förväxlingsarter, jämförelsesidor, om-sidan, publicering i vågor). Planen byggs **mot testdata** parallellt med pipelineplanen `docs/superpowers/plans/2026-10-01-artsidor-fas1b-faktablad.md`. Kontraktet mellan dem är specens bilaga C och D. Riktig data behövs först i Task 16.
>
> **Reviderad 2026-10-05:** Albin granskar inte längre varje arts faktablad för hand (spec Revision 2026-10-05). Fältet `review.facts` är ersatt av `verification = { method, at, model, spotChecked }`, och raden på sidan blir "Kontrollerad mot källorna {datum}" i stället för "Faktagranskad av Albin Abrahamsson {datum}". JSON-LD tappar `reviewedBy` (bara `lastReviewed` blir kvar), och "Så gör vi artsidorna" (Task 12) beskriver den automatiska kontrollen i stället för att säga att Albin granskar varje art. Berör Task 4 (zod-schemat och `species.ts`), Task 9 (artsidans creditblock), Task 11 (jämförelsesidan), Task 12, Task 14 (`check-seo.mjs`) och Task 16 till 18 (villkoren mot fas 1b:s data).
>
> **Reviderad 2026-10-05 (b):** Albin vill committa och pusha sida för sida ("Commit and push each page one by one; I don't need to go through each one manually") i stället för att samla en hel våg, förhandsvisa den och läsa igenom allt före en gemensam go-live (spec Revision 2026-10-05 (b)). **Task 15** får ett sista steg som slår ihop kod-grenen till `main` direkt när testdata-QA är grön, eftersom riktig artdata från nu på går direkt till `main`, inte via den här grenen. **Task 16 och 17 är slagna ihop och omskrivna** till en löpande publiceringsloop: ingen förhandsvisning, inget Albin läser igenom i förväg, bara automatiska kontroller per sida (bygge, `check-seo.mjs`, Playwright/axe), en commit och push per art eller jämförelse, en takt på cirka 5 minuter mellan pushar, en nödstopp (`--max-publish`, stopp vid upprepade fel) och ett stickprov efter publicering (2 arter per 40, 1 jämförelse per 10) i Albins ark. **Task 18** ("Senare vågor") tas bort som eget steg: loopen fortsätter automatiskt genom köns ordning, Albins återkommande uppgift är bara undantagsarkets flaggor (fas 1b R5) och stickprovet.

**Goal:** Ingångssida, gruppsidor, en artsida per publicerad art, jämförelsesidor för förväxlingspar och sidan "Så gör vi artsidorna", på svenska och engelska, byggda ur pipelinens datafiler och publicerade sida för sida, i vågornas körordning (ändrat 2026-10-05 (b)). Dessutom ny meny- och sidfotsnavigering, filter för egna besök i Vercel Analytics och SEO-reglerna som ett skript som stoppar bygget vid fel.

**Architecture:** Två innehållssamlingar (`species`, `comparisons`) läser JSON-filerna, och `src/lib/species-source.mjs` avgör i ren JS var datan ligger (riktig data eller testdata) och vilka poster som får en sida (publicerade, eller kontrollerade i ett förhandsbygge). `src/lib/species.ts` samlar all sidlogik. Komponenterna under `src/components/species/` renderar sidorna, och en dynamisk route per språk delegerar till rätt komponent. Diagram och länskarta ritas som SVG när sajten byggs. Inspelningarna kopieras in i `dist/` vid bygget, bara för arter som får en sida. `scripts/check-seo.mjs` och `scripts/check-preview-build.mjs` kontrollerar den byggda sajten.

**Tech Stack:** Astro 5 (content layer, `astro:assets`), TypeScript, Zod, `@astrojs/sitemap`, Playwright, Node 22 (`node --test` för enhetstester), sharp (finns redan), inga nya beroenden.

**Spec:** `docs/superpowers/specs/2026-09-25-artsidor-design.md` (avsnitt 4 till 8 och 10 till 15, bilaga A, C och D). Mockup: `docs/superpowers/specs/assets/2026-09-25-artsidor/helheten.html`.

---

## Avvikelser från specen (medvetna, små)

1. **Skriptet heter `npm run verify`**, eftersom `npm run check` redan är `astro check`. Testdatans variant heter `npm run verify:fixtures`.
2. **Approtan, spelarens etikett och textcrediten nämner inte artens namn i löptext.** "Birdy känner igen talgoxe på foto" blir fel böjning på svenska och datan har inte bestämd form. Approtan säger "arten" / "this species" i fyra varianter efter `identifiable`, spelarens etikett är "Inspelning: Talgoxe", och textcrediten är "Texten bygger på Wikipedia och får delas under CC BY-SA 4.0. Källor: svenska artikeln, engelska artikeln, tyska artikeln" med länkar till revisionerna. Kontrolleras i Task 15:s testdata-skärmdumpar (ändrat 2026-10-05 (b): ingen förhandsvisning av riktig data att läsa igenom längre).
3. **Sökfältet i kategoriraden skickar `q` till ingångssidan**, som gör filtreringen.
4. **Artkort och förväxlingsfoton har tom alt-text.** Namnet står som text i samma länk eller bredvid.
5. **Grupper utan någon byggd art får varken sida, chip eller kort.** Specen säger att chipsen gäller grupper med minst en publicerad art; samma regel används för gruppsidan och kortet, annars hade det funnits tomma sidor.
6. **Datameningarna visas som ett stycke under diagrammet och kartan tillsammans.** Pipelinen skriver dem som en lista utan uppdelning (bilaga C), så båda SVG:erna pekar på samma stycke med `aria-describedby`.
7. **Namnet på en förväxlingsart utan egen fil läses ur `facts[].other.scientific`.** En förväxlingsart utanför de 180 har inget annat namn i datan. Sajten läser bara `id`, `topic` och `other` ur faktalistan (bilaga C tillåter det).
8. **Inspelningens creditrad står direkt under spelaren**, inte i creditblocket längst ned.
9. **Inspelningarnas adresser har ett innehållshash** (`/audio/species/Q25485.3f9c0a1b2d.mp3`) och kopieras till `dist/` av en byggkrok, bara för arter som får en sida. En Vite-glob (`?url`) hade lagt alla inspelningar i bygget, även opublicerade.
10. **Jämförelsesidans engelska version ordnar arterna efter de engelska slugsen** (spec §4), så kolumnerna kan byta plats mellan språken. Tabellens celler följer med.

**Publiceringen** görs av pipelinen, inte av sajten: `uv run birdy-fetcher web publish --species <QID>` i `tools/content-pipeline` (fas 1b, Task 23) sätter `publish: true` på en skriven och kontrollerad art i taget, och på en jämförelse där båda arterna är publicerade. `--wave N` finns kvar som filter men väntar inte på att hela vågen blir klar (ändrat 2026-10-05 (b)). Kommandot avpublicerar aldrig. Sajten läser bara fältet.

## Förutsättningar

- **1.3-webben i fältbokens färger är live** (sedan 2026-09-28). Planen bygger på den koden.
- **Fas 1b behövs inte förrän Task 16.** Task 1 till 15 körs på testdata i `website/tests/fixtures/`.
- **Gren:** från `main`, i en egen worktree med kort sökväg (långa sökvägar failar på Windows):
  ```bash
  git worktree add C:/w/birdy-artsidor -b website/artsidor
  cd C:/w/birdy-artsidor/website && npm ci
  ```
  Alla kommandon nedan körs i `C:/w/birdy-artsidor/website` med Git Bash.
- **Bygg och testa på testdata:** `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test <fil>` (egen port så att en annan dev-server inte krockar). Playwright serverar `dist/`, så bygg alltid med testdata före Playwright.
- **Lägen:** `SPECIES_FIXTURES=1` läser testdatan i stället för `src/data/` och `src/assets/species/`. `SPECIES_PREVIEW=1` bygger även kontrollerade sidor som inte är publicerade (sätts i Vercels miljö Preview i Task 16, numera bara för kod-förhandsvisningar, ändrat 2026-10-05 (b)). `scripts/env-run.mjs` sätter variablerna på samma sätt på Windows och macOS.

## Filstruktur

**Skapas:**

| Fil | Ansvar |
|---|---|
| `scripts/env-run.mjs` | Kör ett kommando med extra miljövariabler (Windows och macOS). |
| `src/lib/species-source.mjs` | Var datan ligger, vem som får en sida, inspelningarnas adresser. Delas av config, sidor och skript. |
| `tests/fixtures/make-species-fixtures.mjs` | Skriver testdatan (20 arter, 4 jämförelser, testbilder, tysta mp3). |
| `tests/fixtures/species/*.json`, `tests/fixtures/comparisons/*.json`, `tests/fixtures/species-assets/**` | Testdatan (genererad, committad). |
| `tests/unit/species-source.unit.mjs` | Enhetstester med `node --test`. |
| `scripts/build-sweden-counties.mjs`, `src/data/sweden-counties.json` | Länsgränserna som SVG-banor (Natural Earth). |
| `src/lib/species.ts` | Typer, laddning, adresser, grupper, titlar, jämförelser, förväxlingsarter, datum, JSON-LD. |
| `src/lib/species-routes.ts` | Sökvägarna för den dynamiska routen. |
| `src/lib/species-sitemap.mjs` | `lastmod` och `noindex` för sitemapen, i ren JS. |
| `src/styles/species.css` | Delade stilar. |
| `src/components/species/CategoryBar.astro`, `SpeciesCard.astro`, `SpeciesHub.astro`, `GroupPage.astro`, `SpeciesArticle.astro`, `ComparisonPage.astro`, `AboutSpeciesPages.astro`, `SpeciesRoute.astro` | Sidorna. |
| `src/components/species/MonthChart.astro`, `CountyMap.astro`, `AudioPlayer.astro`, `Credits.astro` | Moduler. |
| `src/pages/species/index.astro`, `src/pages/species/[slug].astro`, `src/pages/species/about-these-pages.astro` | Engelska routes. |
| `src/pages/sv/arter/index.astro`, `src/pages/sv/arter/[slug].astro`, `src/pages/sv/arter/om-artsidorna.astro` | Svenska routes. |
| `scripts/check-seo.mjs`, `scripts/check-preview-build.mjs` | Kontroller av den byggda sajten. |
| `tests/species.spec.ts`, `tests/comparisons.spec.ts`, `tests/analytics.spec.ts` | Playwright. |

**Ändras:** `src/content.config.ts`, `src/layouts/Layout.astro`, `src/components/Nav.astro`, `src/components/Footer.astro`, `src/components/Guide.astro`, `src/components/ui/Icon.astro`, `src/content/copy.{en,sv}.json`, `astro.config.mjs`, `scripts/check-no-dashes.mjs`, `package.json`, `.gitignore`, `tests/home.spec.ts`.

---

### Task 1: Byggläget och testdatan

**Files:**
- Create: `website/scripts/env-run.mjs`
- Create: `website/src/lib/species-source.mjs`
- Create: `website/tests/unit/species-source.unit.mjs`
- Create: `website/tests/fixtures/make-species-fixtures.mjs` (och det den genererar)
- Modify: `website/package.json`, `website/.gitignore`

- [ ] **Step 1: Skriv enhetstestet för publiceringsregeln**

`tests/unit/species-source.unit.mjs`:

```js
// node --test "tests/unit/*.unit.mjs"  (the .unit.mjs suffix keeps Playwright from picking it up)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isComparisonBuilt, isSpeciesBuilt } from '../../src/lib/species-source.mjs';

const ok = {
  status: 'ok', publish: true,
  verification: { method: 'auto', at: '2026-11-20', model: 'claude-sonnet-5', spotChecked: false },
};

test('publicerad och kontrollerad art får sida', () => {
  assert.equal(isSpeciesBuilt(ok, false), true);
});

test('opublicerad art får sida bara i förhandsbygget', () => {
  const draft = { ...ok, publish: false };
  assert.equal(isSpeciesBuilt(draft, false), false);
  assert.equal(isSpeciesBuilt(draft, true), true);
});

test('pending och failed får aldrig sida', () => {
  for (const status of ['pending', 'failed']) assert.equal(isSpeciesBuilt({ ...ok, status }, true), false);
});

test('okontrollerat faktablad ger ingen sida, inte ens i förhandsbygget', () => {
  const { verification, ...unverified } = ok;
  assert.equal(isSpeciesBuilt(unverified, true), false);
});

test('jämförelse kräver båda arternas sidor', () => {
  const cmp = { status: 'ok', publish: true, a: 'Q1', b: 'Q2' };
  const both = new Set(['Q1', 'Q2']);
  assert.equal(isComparisonBuilt(cmp, both, false), true);
  assert.equal(isComparisonBuilt(cmp, new Set(['Q1']), false), false);
  assert.equal(isComparisonBuilt({ ...cmp, publish: false }, both, false), false);
  assert.equal(isComparisonBuilt({ ...cmp, publish: false }, both, true), true);
  assert.equal(isComparisonBuilt({ ...cmp, status: 'pending' }, both, true), false);
});
```

- [ ] **Step 2: Kör och se det faila**

Run: `node --test "tests/unit/*.unit.mjs"`
Expected: FAIL, `Cannot find module '.../src/lib/species-source.mjs'`.

- [ ] **Step 3: Skriv `src/lib/species-source.mjs`**

```js
// Where the species pages read their data, and which records get a page (spec 2026-09-25 §14).
// Plain JS so astro.config.mjs, content.config.ts, src/lib/species.ts and the check scripts share one rule.
//   SPECIES_FIXTURES=1  read the test data in tests/fixtures/ instead of src/data/ and src/assets/species/
//   SPECIES_PREVIEW=1   also build verified pages that are not published yet (Vercel Preview)
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const useFixtures = () => process.env.SPECIES_FIXTURES === '1';
export const isPreview = () => process.env.SPECIES_PREVIEW === '1';

/** Folders relative to the website root. */
export const speciesDir = () => (useFixtures() ? 'tests/fixtures/species' : 'src/data/species');
export const comparisonsDir = () => (useFixtures() ? 'tests/fixtures/comparisons' : 'src/data/comparisons');
export const assetsDir = () => (useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species');

/** A species page exists when its text is written (ok), its facts are verified (spec Revision 2026-10-05), and it is published or this is a preview build. */
export function isSpeciesBuilt(record, preview = isPreview()) {
  return record.status === 'ok' && Boolean(record.verification) && (record.publish === true || preview);
}

/** A comparison page exists when its text is written, it is published or previewed, and both species pages exist. */
export function isComparisonBuilt(record, builtQids, preview = isPreview()) {
  return record.status === 'ok' && (record.publish === true || preview) && builtQids.has(record.a) && builtQids.has(record.b);
}

/** Every *.json in a folder under the website root, parsed and sorted by file name. A missing folder is an empty list. */
export function readJsonDir(root, dir) {
  const abs = resolve(root, dir);
  if (!existsSync(abs)) return [];
  return readdirSync(abs)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => JSON.parse(readFileSync(resolve(abs, f), 'utf8')));
}

/** Public path of a species' recording. Content-hashed; astro.config.mjs copies the file into dist for built species only. */
export function audioPublicPath(root, record) {
  const file = resolve(root, assetsDir(), record.audio.file);
  const hash = createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, 10);
  return `/audio/species/${record.qid}.${hash}.mp3`;
}
```

- [ ] **Step 4: Kör testet igen**

Run: `node --test "tests/unit/*.unit.mjs"`
Expected: PASS (5 tester)

- [ ] **Step 5: Skriv `scripts/env-run.mjs`**

```js
#!/usr/bin/env node
// Runs a command with extra environment variables, the same way on Windows and macOS:
//   node scripts/env-run.mjs SPECIES_FIXTURES=1 SPECIES_PREVIEW=1 -- astro build --force
import { spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const split = args.indexOf('--');
if (split < 1 || split === args.length - 1) {
  console.error('Användning: node scripts/env-run.mjs NAMN=värde [NAMN=värde ...] -- kommando [argument]');
  process.exit(2);
}
const env = { ...process.env };
for (const pair of args.slice(0, split)) {
  const eq = pair.indexOf('=');
  if (eq < 1) {
    console.error(`Ogiltig variabel: ${pair}`);
    process.exit(2);
  }
  env[pair.slice(0, eq)] = pair.slice(eq + 1);
}
const result = spawnSync(args.slice(split + 1).join(' '), { stdio: 'inherit', env, shell: true });
process.exit(result.status ?? 1);
```

- [ ] **Step 6: Skriv testdatans generator**

`tests/fixtures/make-species-fixtures.mjs`:

Generatorn i repot ritar en enfärgad ruta utan text, en nyans per art, så bilderna blir identiska på alla operativsystem.

```js
#!/usr/bin/env node
// Writes TEST data for the species pages (spec 2026-09-25 appendix C and D), so the pages can be built
// and tested before the pipeline has produced real files. Never real facts: every text says
// "Testtext" / "Test text". Output lives under tests/fixtures/ and is only read when the build runs
// with SPECIES_FIXTURES=1 (npm run build:fixtures). Re-run after changing this file:
//   node tests/fixtures/make-species-fixtures.mjs
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const here = dirname(fileURLToPath(import.meta.url));
const OUT = {
  species: resolve(here, 'species'),
  comparisons: resolve(here, 'comparisons'),
  assets: resolve(here, 'species-assets'),
};

const COUNTY_CODES = ['SE-AB', 'SE-AC', 'SE-BD', 'SE-C', 'SE-D', 'SE-E', 'SE-F', 'SE-G', 'SE-H', 'SE-I', 'SE-K', 'SE-M', 'SE-N', 'SE-O', 'SE-S', 'SE-T', 'SE-U', 'SE-W', 'SE-X', 'SE-Y', 'SE-Z'];
const YEAR_ROUND = [72, 58, 61, 55, 70, 79, 64, 68, 74, 100, 66, 69];
const SUMMER = [0, 0, 1, 36, 100, 51, 49, 65, 51, 10, 1, 0];

// The twelve "common species" in the footer must all be here, or the footer stops the build.
const SPECIES = [
  { qid: 'Q25485', sv: 'Talgoxe', en: 'Great Tit', sci: 'Parus major', fam: ['Paridae', 'Mesar'], group: 'songbirds', slug: ['talgoxe', 'great-tit'], iucn: 'LC', red: 'not_listed', id: [true, true], extra: true, audio: 'trimmed', marginalia: true, de: true, months: YEAR_ROUND, status: 'resident', size: ['Cirka 14 cm', 'About 14 cm'], look: ['Q25404'] },
  { qid: 'Q25404', sv: 'Blåmes', en: 'Eurasian Blue Tit', sci: 'Cyanistes caeruleus', fam: ['Paridae', 'Mesar'], group: 'songbirds', slug: ['blames', 'eurasian-blue-tit'], iucn: 'LC', red: 'not_listed', id: [true, true], audio: 'full', de: true, months: YEAR_ROUND, status: 'resident', size: ['Cirka 12 cm', 'About 12 cm'], look: ['Q25485'] },
  { qid: 'Q25234', sv: 'Koltrast', en: 'Common Blackbird', sci: 'Turdus merula', fam: ['Turdidae', 'Trastar'], group: 'songbirds', slug: ['koltrast', 'common-blackbird'], iucn: 'LC', red: 'not_listed', id: [true, true], extra: true, audio: 'trimmed', de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25334', sv: 'Rödhake', en: 'European Robin', sci: 'Erithacus rubecula', fam: ['Muscicapidae', 'Flugsnappare'], group: 'songbirds', slug: ['rodhake', 'european-robin'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q14683', sv: 'Gråsparv', en: 'House Sparrow', sci: 'Passer domesticus', fam: ['Passeridae', 'Sparvfinkar'], group: 'songbirds', slug: ['grasparv', 'house-sparrow'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25307', sv: 'Skata', en: 'Eurasian Magpie', sci: 'Pica pica', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['skata', 'eurasian-magpie'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25345384'] },
  { qid: 'Q25345384', sv: 'Kaja', en: 'Western Jackdaw', sci: 'Coloeus monedula', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['kaja', 'western-jackdaw'], iucn: 'NE', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25307'] },
  { qid: 'Q25383', sv: 'Bofink', en: 'Eurasian Chaffinch', sci: 'Fringilla coelebs', fam: ['Fringillidae', 'Finkar'], group: 'songbirds', slug: ['bofink', 'eurasian-chaffinch'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25348', sv: 'Gräsand', en: 'Mallard', sci: 'Anas platyrhynchos', fam: ['Anatidae', 'Egentliga andfåglar'], group: 'waterfowl', slug: ['grasand', 'mallard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q26427', sv: 'Fiskmås', en: 'Common Gull', sci: 'Larus canus', fam: ['Laridae', 'Måsfåglar'], group: 'gulls_terns', slug: ['fiskmas', 'common-gull'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25385', sv: 'Ormvråk', en: 'Common Buzzard', sci: 'Buteo buteo', fam: ['Accipitridae', 'Hökar'], group: 'raptors', slug: ['ormvrak', 'common-buzzard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q4764', sv: 'Trana', en: 'Common Crane', sci: 'Grus grus', fam: ['Gruidae', 'Tranor'], group: 'cranes_rails', slug: ['trana', 'common-crane'], iucn: 'LC', red: 'not_listed', id: [true, false], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25756', sv: 'Kattuggla', en: 'Tawny Owl', sci: 'Strix aluco', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['kattuggla', 'tawny-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], audio: 'full', de: true, months: YEAR_ROUND, status: 'resident', look: ['Strix uralensis'] },
  { qid: 'Q25384', sv: 'Hornuggla', en: 'Long-eared Owl', sci: 'Asio otus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['hornuggla', 'long-eared-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25769'] },
  // Minimal record: no audio, no report data, no extra photo, no behaviour or look-alikes, no size,
  // status or Swedish red list, Swedish article only.
  { qid: 'Q174466', sv: 'Pärluggla', en: 'Boreal Owl', sci: 'Aegolius funereus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['parluggla', 'boreal-owl'], iucn: 'LC', red: null, id: [false, false], minimal: true },
  // The only seabird, so its group page gets noindex.
  { qid: 'Q25440', sv: 'Storskarv', en: 'Great Cormorant', sci: 'Phalacrocorax carbo', fam: ['Phalacrocoracidae', 'Skarvar'], group: 'seabirds', slug: ['storskarv', 'great-cormorant'], iucn: 'LC', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident' },
  // Reviewed and written but not published: only preview builds (SPECIES_PREVIEW=1) show them.
  { qid: 'Q26209', sv: 'Större hackspett', en: 'Great Spotted Woodpecker', sci: 'Dendrocopos major', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['storre-hackspett', 'great-spotted-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q210418'] },
  { qid: 'Q210418', sv: 'Tretåig hackspett', en: 'Eurasian Three-toed Woodpecker', sci: 'Picoides tridactylus', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['tretaig-hackspett', 'eurasian-three-toed-woodpecker'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q26209'] },
  // Never a page: one failed, one pending (facts exist, text not written yet).
  { qid: 'Q166171', sv: 'Gröngöling', en: 'European Green Woodpecker', sci: 'Picus viridis', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['grongoling', 'european-green-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'failed' },
  { qid: 'Q143284', sv: 'Spillkråka', en: 'Black Woodpecker', sci: 'Dryocopus martius', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['spillkraka', 'black-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'pending' },
];

const S = (text, factIds) => ({ text, factIds });
const lowerSv = (name) => name.toLocaleLowerCase('sv');

function meta(name, lang) {
  let s = lang === 'sv'
    ? `${name}: testtext för artsidornas bygge. Kännetecken, läte och när arten syns i Sverige, med foton och karta.`
    : `${name}: test text for building the species pages. Field marks, call and when it is seen in Sweden, with photos.`;
  while (s.length < 120) s += lang === 'sv' ? ' Testdata.' : ' Test data.';
  if (s.length > 155) throw new Error(`metaDescription är ${s.length} tecken: ${s}`);
  return s;
}

function textFor(sp, lang) {
  const sv = lang === 'sv';
  const name = sv ? sp.sv : sp.en;
  return {
    lead: [S(sv ? `Testtext: ${name} används som exempel när artsidorna byggs och testas.` : `Test text: the ${name} is used as an example when the species pages are built and tested.`, ['f01'])],
    fieldMarks: (sv
      ? [`Testpunkt ett om hur ${lowerSv(name)} ser ut`, 'Testpunkt två om storlek och form', 'Testpunkt tre om beteende i fält']
      : ['Test point one about what it looks like', 'Test point two about size and shape', 'Test point three about behaviour in the field']
    ).map((m) => S(m, ['f02'])),
    voice: [S(sv ? 'Testtext om lätet, skriven så att sidan går att bygga utan riktig data.' : 'Test text about the call, written so the page can be built without real data.', ['f04'])],
    whereWhen: [S(sv ? 'Testtext om var och när arten syns i Sverige.' : 'Test text about where and when it is seen in Sweden.', ['f06', 'd01'])],
    ...(sp.minimal ? {} : { behaviour: [S(sv ? 'Testtext om föda och beteende.' : 'Test text about food and behaviour.', ['f07'])] }),
    lookAlikes: (sp.look ?? []).map((other) => ({ other, text: [S(sv ? 'Testtext om hur de skiljer sig åt.' : 'Test text about how to tell them apart.', ['f09'])] })),
    metaDescription: meta(name, lang),
    facts: {
      size: sp.size ? { value: sv ? sp.size[0] : sp.size[1], factIds: ['f03'] } : null,
      swedenStatus: sp.status ? { value: sp.status, factIds: ['s01'] } : null,
    },
  };
}

function lookalikeFact(other) {
  const known = SPECIES.find((x) => x.qid === other);
  const scientific = known?.sci ?? (other === 'Q25769' ? 'Asio flammeus' : other);
  return {
    id: 'f09', topic: 'lookalike', sv: 'Testfaktum om förväxling.',
    other: other.startsWith('Q') ? { scientific, qid: other } : { scientific },
    sources: [{ article: 'sv', quote: 'Testcitat som bara finns i testdata.' }],
  };
}

function dataFor(sp) {
  const summer = sp.months === SUMMER;
  return {
    fetchedAt: '2026-10-15',
    gbifTaxonKey: 1,
    totalReports: 1000,
    months: sp.months,
    counties: Object.fromEntries(COUNTY_CODES.map((c, i) => [c, (i * 37 + sp.qid.length * 11) % 101])),
    raw: { speciesByMonth: [], allBirdsByMonth: [], speciesByCounty: {}, allBirdsByCounty: {} },
    sentences: summer
      ? {
          sv: ['Rapporteras mest i maj.', 'Nästan aldrig i november till mars.', 'Vanligast i rapporterna från Testlän, Provlän och Exempellän.'],
          en: ['Reported most in May.', 'Almost never in November to March.', 'Most common in reports from Testshire, Sampleshire and Exampleshire.'],
        }
      : {
          sv: ['Rapporteras året runt.', 'Vanligast i rapporterna från Testlän, Provlän och Exempellän.'],
          en: ['Reported all year round.', 'Most common in reports from Testshire, Sampleshire and Exampleshire.'],
        },
    statusSignal: { contradicts: null },
  };
}

function record(sp) {
  const status = sp.recordStatus ?? 'ok';
  const commons = (suffix) => `https://commons.wikimedia.org/wiki/File:Fixture_${sp.qid}_${suffix}`;
  return {
    qid: sp.qid,
    status,
    publish: status === 'ok' && sp.publish !== false,
    slug: { sv: sp.slug[0], en: sp.slug[1] },
    names: { sv: sp.sv, en: sp.en, scientific: sp.sci },
    family: { latin: sp.fam[0], sv: sp.fam[1] },
    group: sp.group,
    iucn: sp.iucn,
    ...(sp.red ? { swedishRedList: sp.red } : {}),
    identifiable: { photo: sp.id[0], sound: sp.id[1] },
    ...(sp.marginalia ? { marginalia: { sv: 'Testanteckning i marginalen.', en: 'A test note in the margin.' } } : {}),
    images: [
      { role: 'hero', file: `${sp.qid}/hero.webp`, width: 1200, height: 800, author: 'Testfotograf', license: 'CC0', licenseUrl: null, sourceUrl: commons('hero.jpg') },
      ...(sp.extra ? [{ role: 'extra', file: `${sp.qid}/extra.webp`, width: 1200, height: 800, author: 'Testfotograf två', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', sourceUrl: commons('extra.jpg') }] : []),
    ],
    ...(sp.audio ? {
      audio: {
        file: `${sp.qid}/voice.mp3`, durationSec: 1, trimmed: sp.audio === 'trimmed',
        author: 'Testinspelare', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/', sourceUrl: commons('song.ogg'),
      },
    } : {}),
    wikipedia: {
      sv: { title: sp.sv, revision: '1000001' },
      ...(sp.minimal ? {} : { en: { title: sp.en, revision: '2000002' } }),
      ...(sp.de ? { de: { title: `${sp.en} (Testartikel)`, revision: '3000003' } } : {}),
    },
    ...(sp.months ? { data: dataFor(sp) } : {}),
    facts: [
      { id: 'f01', topic: 'appearance', sv: 'Testfaktum.', sources: [{ article: 'sv', quote: 'Testcitat som bara finns i testdata.' }] },
      ...(sp.look ?? []).map(lookalikeFact),
    ],
    review: { wave: status === 'pending' ? 2 : sp.publish === false ? 2 : 1 },
    ...(status !== 'pending' ? { verification: { method: 'auto', at: '2026-11-20', model: 'fixture', spotChecked: false } } : {}),
    text: status === 'ok' ? { sv: textFor(sp, 'sv'), en: textFor(sp, 'en') } : null,
    ...(status === 'failed' ? { rejectedText: null } : {}),
    generated: {
      facts: { model: 'fixture', prompt: 'fixture', effort: 'none', at: '2026-10-20' },
      ...(status === 'ok' ? { text: { model: 'fixture', prompt: 'fixture', effort: 'none', checker: 'fixture', at: '2026-11-25' } } : {}),
    },
    errors: status === 'failed' ? ['Testfel: kontrollen failade.'] : [],
  };
}

// a and b follow the Swedish slug order (appendix D); the file name has the QIDs in string order.
const COMPARISONS = [
  { file: 'Q25404_Q25485', a: 'Q25404', b: 'Q25485', slug: ['blames-eller-talgoxe', 'eurasian-blue-tit-vs-great-tit'], publish: true },
  { file: 'Q25307_Q25345384', a: 'Q25345384', b: 'Q25307', slug: ['kaja-eller-skata', 'eurasian-magpie-vs-western-jackdaw'], publish: true },
  { file: 'Q210418_Q26209', a: 'Q26209', b: 'Q210418', slug: ['storre-hackspett-eller-tretaig-hackspett', 'eurasian-three-toed-woodpecker-vs-great-spotted-woodpecker'], publish: false },
  { file: 'Q25384_Q25756', a: 'Q25384', b: 'Q25756', slug: ['hornuggla-eller-kattuggla', 'long-eared-owl-vs-tawny-owl'], status: 'pending' },
];

function comparison(c) {
  const [a, b] = [SPECIES.find((s) => s.qid === c.a), SPECIES.find((s) => s.qid === c.b)];
  const status = c.status ?? 'ok';
  const lang = (l) => {
    const sv = l === 'sv';
    const [na, nb] = sv ? [a.sv, lowerSv(b.sv)] : [a.en, b.en];
    let metaDescription = sv
      ? `${na} eller ${nb}? Testtext för jämförelsesidan: så skiljer du dem åt i fält, på storlek, färg och läte.`
      : `${na} or ${nb}? Test text for the comparison page: how to tell them apart by size, colour and call.`;
    while (metaDescription.length < 120) metaDescription += sv ? ' Testdata.' : ' Test data.';
    if (metaDescription.length > 155) throw new Error(`jämförelsens metaDescription är ${metaDescription.length} tecken`);
    return {
      shortAnswer: [S(sv ? `Testtext: det kortaste svaret på hur ${lowerSv(na)} och ${nb} skiljer sig åt.` : `Test text: the shortest answer to how the ${na} and the ${nb} differ.`, ['a:f01', 'b:f01'])],
      rows: (sv ? ['Storlek', 'Huvud', 'Läte'] : ['Size', 'Head', 'Call']).map((feature, i) => ({
        feature,
        a: S(sv ? `Testcell ${i + 1} för den första arten` : `Test cell ${i + 1} for the first species`, ['a:f02']),
        b: S(sv ? `Testcell ${i + 1} för den andra arten` : `Test cell ${i + 1} for the second species`, ['b:f02']),
      })),
      metaDescription,
    };
  };
  return {
    a: c.a,
    b: c.b,
    status,
    publish: status === 'ok' && c.publish === true,
    slug: { sv: c.slug[0], en: c.slug[1] },
    volumes: { sv: 100, en: 50 },
    text: status === 'ok' ? { sv: lang('sv'), en: lang('en') } : null,
    generated: { model: 'fixture', prompt: 'fixture', checker: 'fixture', at: '2026-11-26' },
    errors: [],
  };
}

async function photo(qid, role, label) {
  const dir = resolve(OUT.assets, qid);
  mkdirSync(dir, { recursive: true });
  const bg = role === 'hero' ? '#F2B27A' : '#FDE5CB';
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="800"><rect width="1200" height="800" fill="${bg}"/><text x="600" y="420" font-family="Georgia, serif" font-size="64" fill="#302019" text-anchor="middle">Testbild: ${label}</text></svg>`;
  await sharp(Buffer.from(svg)).webp({ quality: 60 }).toFile(resolve(dir, `${role}.webp`));
}

// About one second of silent MP3 (MPEG-1 Layer III, 128 kbit/s, 44.1 kHz, mono, empty side info):
// the audio player needs a real file to point at, laid out like the pipeline's voice.mp3.
function silentMp3(frames = 38) {
  const frame = Buffer.alloc(417);
  frame[0] = 0xff; frame[1] = 0xfb; frame[2] = 0x90; frame[3] = 0xc0;
  return Buffer.concat(Array.from({ length: frames }, () => frame));
}

for (const dir of Object.values(OUT)) {
  rmSync(dir, { recursive: true, force: true });
  mkdirSync(dir, { recursive: true });
}
for (const sp of SPECIES) {
  writeFileSync(resolve(OUT.species, `${sp.qid}.json`), `${JSON.stringify(record(sp), null, 2)}\n`);
  await photo(sp.qid, 'hero', sp.sv);
  if (sp.extra) await photo(sp.qid, 'extra', `${sp.sv}, extra`);
  if (sp.audio) writeFileSync(resolve(OUT.assets, sp.qid, 'voice.mp3'), silentMp3());
}
for (const c of COMPARISONS) writeFileSync(resolve(OUT.comparisons, `${c.file}.json`), `${JSON.stringify(comparison(c), null, 2)}\n`);
console.log(`fixtures: ${SPECIES.length} arter och ${COMPARISONS.length} jämförelser i tests/fixtures/`);
```

- [ ] **Step 7: Kör generatorn**

Run: `node tests/fixtures/make-species-fixtures.mjs && ls tests/fixtures/species | wc -l && ls tests/fixtures/comparisons && ls tests/fixtures/species-assets/Q25485`
Expected: `fixtures: 20 arter och 4 jämförelser i tests/fixtures/`, `20`, fyra jämförelsefiler (`Q210418_Q26209.json Q25307_Q25345384.json Q25384_Q25756.json Q25404_Q25485.json`) och `extra.webp hero.webp voice.mp3`. Totalt cirka 350 KB.

- [ ] **Step 8: Skript och gitignore**

I `package.json`, lägg till i `"scripts"`:

```json
    "build:fixtures": "node scripts/env-run.mjs SPECIES_FIXTURES=1 -- astro build --force",
    "build:preview-fixtures": "node scripts/env-run.mjs SPECIES_FIXTURES=1 SPECIES_PREVIEW=1 -- astro build --force --outDir dist-preview",
    "fixtures:species": "node tests/fixtures/make-species-fixtures.mjs",
    "test:unit": "node --test \"tests/unit/*.unit.mjs\"",
```

(`--force` tömmer innehållslagrets cache, så att bygget inte återanvänder data från det andra läget.)

I `website/.gitignore`, lägg till raden `dist-preview/` efter `dist/`.

- [ ] **Step 9: Kontrollera att det befintliga bygget är orört**

Run: `npm run build:fixtures && npm run test:unit`
Expected: bygget går igenom (testdatan läses inte av något än) och 5 enhetstester PASS.

- [ ] **Step 10: Commit**

```bash
git add scripts/env-run.mjs src/lib/species-source.mjs tests/unit/species-source.unit.mjs tests/fixtures package.json .gitignore
git commit -m "feat(website): testdata och bygglägen för artsidorna"
```

---

### Task 2: Länskartan

**Files:**
- Create: `website/scripts/build-sweden-counties.mjs`
- Create: `website/src/data/sweden-counties.json` (genererad)
- Modify: `website/package.json`

- [ ] **Step 1: Skriv skriptet**

Skriptet i repot har sedan granskningen sinusprojektion runt 15° O, kontroll av källfilens SHA-256 och exakt kontroll av de 21 länskoderna; utgå från det, inte från blocket nedan.

`scripts/build-sweden-counties.mjs`:

```js
#!/usr/bin/env node
// Builds src/data/sweden-counties.json: Sweden's 21 counties as SVG paths for the species pages'
// county map (spec 2026-09-25 §5). Source: Natural Earth 1:10m admin-1 (public domain, "No
// permission is required to use Natural Earth"), pinned to the v5.1.2 tag. raw.githubusercontent
// rather than jsDelivr, because the file (~40 MB) is over jsDelivr's size limit. Keys are ISO
// 3166-2:SE codes (SE-AB ...), the same codes the pipeline writes into data.counties.
// Run: npm run assets:counties   (writes the JSON; commit it, the build never downloads anything)
import { writeFileSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_admin_1_states_provinces.geojson';
const OUT = resolve(root, 'src/data/sweden-counties.json');
const WIDTH = 300; // SVG user units; the page scales the map with CSS
const LAT0 = 62; // projection centre: x = lon * cos(62°) keeps Sweden's shape close to a map's
const SIMPLIFY_EPS = 0.025; // degrees (~2.8 km): enough for a 200 to 300 px wide map
const MIN_PART_AREA = 0.02; // square degrees: drops skerries, keeps Öland, Gotland and the big islands

// --src <file> reads a local copy instead of downloading (used when testing the script).
const srcArg = process.argv.indexOf('--src');
const geo = srcArg > -1
  ? JSON.parse(readFileSync(process.argv[srcArg + 1], 'utf8'))
  : await (await fetch(SRC)).json();

const features = geo.features.filter((f) => f.properties.adm0_a3 === 'SWE');
if (features.length !== 21) throw new Error(`Väntade 21 län, fick ${features.length}`);

function perpDist(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  if (dx === 0 && dy === 0) return Math.hypot(p[0] - a[0], p[1] - a[1]);
  const t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy);
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
}
function douglasPeucker(points, eps) {
  if (points.length < 3) return points.slice();
  const keep = new Array(points.length).fill(false);
  keep[0] = keep[points.length - 1] = true;
  const stack = [[0, points.length - 1]];
  while (stack.length) {
    const [lo, hi] = stack.pop();
    let maxD = 0, idx = -1;
    for (let i = lo + 1; i < hi; i++) {
      const d = perpDist(points[i], points[lo], points[hi]);
      if (d > maxD) { maxD = d; idx = i; }
    }
    if (maxD > eps && idx !== -1) { keep[idx] = true; stack.push([lo, idx], [idx, hi]); }
  }
  return points.filter((_, i) => keep[i]);
}
const ringArea = (ring) => Math.abs(ring.reduce((s, [x1, y1], i) => {
  const [x2, y2] = ring[(i + 1) % ring.length];
  return s + (x1 * y2 - x2 * y1);
}, 0) / 2);

const cos0 = Math.cos((LAT0 * Math.PI) / 180);
const polygonsOf = (g) => (g.type === 'Polygon' ? [g.coordinates] : g.coordinates);
let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
const counties = features.map((f) => {
  const parts = polygonsOf(f.geometry).map((poly) => poly[0]); // outer rings only, counties have no holes worth drawing
  const largest = Math.max(...parts.map(ringArea));
  const kept = parts
    .filter((ring) => ringArea(ring) >= MIN_PART_AREA || ringArea(ring) === largest)
    .map((ring) => douglasPeucker(ring, SIMPLIFY_EPS).map(([lon, lat]) => [lon * cos0, -lat]))
    .filter((ring) => ring.length >= 4);
  for (const ring of kept) for (const [x, y] of ring) {
    minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y);
  }
  return { code: f.properties.iso_3166_2, sv: f.properties.name_sv, en: f.properties.name_en, rings: kept };
});

const scale = WIDTH / (maxX - minX);
const height = Math.round((maxY - minY) * scale);
const r1 = (n) => Math.round(n * 10) / 10;
const out = {
  source: 'Natural Earth 1:10m admin-1 v5.1.2 (public domain)',
  viewBox: `0 0 ${WIDTH} ${height}`,
  counties: counties
    .map(({ code, sv, en, rings }) => ({
      code, sv, en,
      d: rings.map((ring) => `M${ring.map(([x, y]) => `${r1((x - minX) * scale)} ${r1((y - minY) * scale)}`).join('L')}Z`).join(''),
    }))
    .sort((a, b) => a.code.localeCompare(b.code)),
};
const codes = new Set(out.counties.map((c) => c.code));
for (const must of ['SE-AB', 'SE-BD', 'SE-I', 'SE-M', 'SE-O', 'SE-T']) if (!codes.has(must)) throw new Error(`Länet ${must} saknas`);
const json = JSON.stringify(out);
writeFileSync(OUT, `${json}\n`);
console.log(`sweden-counties: ${out.counties.length} län, viewBox ${out.viewBox}, ${(json.length / 1024).toFixed(1)} KB -> ${OUT}`);
if (json.length > 80 * 1024) throw new Error('Kartfilen är över 80 KB, höj SIMPLIFY_EPS');
```

- [ ] **Step 2: Skriptet i `package.json`**

Lägg till i `"scripts"`: `"assets:counties": "node scripts/build-sweden-counties.mjs",`

- [ ] **Step 3: Kör**

Run: `npm run assets:counties`
Expected: `sweden-counties: 21 län, viewBox 0 0 300 713, 16.0 KB -> .../src/data/sweden-counties.json` (provkört 2026-10-07 mot samma källfil; storleken kan skilja på någon tiondel).

- [ ] **Step 4: Titta på kartan**

Run (ritar kartan till en PNG i fyra nyanser, bara för ögat, filen committas inte):

```bash
node -e "
const d=JSON.parse(require('fs').readFileSync('src/data/sweden-counties.json','utf8'));
const c=['#FFFAF1','#FDE5CB','#F2B27A','#9A4526'];
const svg='<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"'+d.viewBox+'\" width=\"300\" height=\"713\"><rect width=\"100%\" height=\"100%\" fill=\"#F6EFE2\"/>'+d.counties.map((x,i)=>'<path d=\"'+x.d+'\" fill=\"'+c[i%4]+'\" stroke=\"#6E584B\" stroke-width=\"0.6\"/>').join('')+'</svg>';
require('sharp')(Buffer.from(svg)).png().toFile('../county-check.png').then(()=>console.log('../county-check.png'))"
```

Expected: Sverige med 21 län, Gotland och Öland med, inga hål. Ta bort `../county-check.png` efteråt.

- [ ] **Step 5: Commit**

```bash
git add scripts/build-sweden-counties.mjs src/data/sweden-counties.json package.json
git commit -m "feat(website): länsgränser för artsidornas karta (Natural Earth)"
```

---

### Task 3: Layout får språkpar, noindex, extra JSON-LD och filtret för egna besök

**Files:**
- Modify: `website/src/layouts/Layout.astro`
- Create: `website/tests/analytics.spec.ts`

- [ ] **Step 1: Skriv testet för filtret**

`tests/analytics.spec.ts`:

```ts
import { test, expect } from '@playwright/test';

// Own visits (spec 2026-09-25 §15): ?va-ignore=1 marks the browser, ?va-ignore=0 removes the mark.
test('filtret för egna besök sätts och tas bort med en adressparameter', async ({ page }) => {
  await page.goto('/?va-ignore=1');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBe('1');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');
  expect(await page.evaluate(() => (window as unknown as { webAnalyticsBeforeSend: (e: unknown) => unknown }).webAnalyticsBeforeSend({ type: 'pageview', url: location.href }))).toBeNull();

  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('function');

  await page.goto('/?va-ignore=0');
  expect(await page.evaluate(() => localStorage.getItem('birdy-va-ignore'))).toBeNull();
  await page.goto('/sv/');
  expect(await page.evaluate(() => typeof (window as unknown as { webAnalyticsBeforeSend?: unknown }).webAnalyticsBeforeSend)).toBe('undefined');
});
```

- [ ] **Step 2: Kör och se det faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/analytics.spec.ts`
Expected: FAIL, `localStorage.getItem` ger `null` i stället för `'1'`.

- [ ] **Step 3: Utöka `Props`**

Layout använder sedan granskningen `serializeJsonLd` från `src/lib/json-ld.mjs` (escapar `<`); behåll det.

I `src/layouts/Layout.astro`, ersätt `interface Props { ... }` och raden `const { locale, pathname, ... } = Astro.props;` med:

```ts
interface Props {
  locale: Locale;
  pathname: string;
  noAlternateLocale?: boolean;
  /** The other language's path when the two languages use different slugs (species pages). */
  alternatePath?: string;
  /** Adds `<meta name="robots" content="noindex, follow">` (small groups and unpublished preview pages). */
  noindex?: boolean;
  /** Extra schema.org nodes appended to the page's @graph. */
  jsonLd?: Record<string, unknown>[];
  title?: string;
  description?: string;
  articleDate?: string;
  /** Site-relative or absolute URL of a 1200×630 share image. Defaults to the locale's og-field image. */
  ogImage?: string;
  ogImageAlt?: string;
}

const {
  locale, pathname, noAlternateLocale = false, alternatePath, noindex = false, jsonLd: extraJsonLd = [],
  title, description, articleDate, ogImage, ogImageAlt,
} = Astro.props;
```

- [ ] **Step 4: Använd dem**

Ersätt `const altPath = alternateHref(locale, pathname);` med:

```ts
const altPath = alternatePath ?? alternateHref(locale, pathname);
```

Lägg till `...extraJsonLd,` som sista element i `'@graph': [ ... ]` (efter FAQPage-grenen), så att det står:

```ts
    }] : []),
    ...extraJsonLd,
  ],
};
```

Lägg till direkt efter `<meta name="description" content={metaDescription} />`:

```astro
    {noindex && <meta name="robots" content="noindex, follow" />}
```

- [ ] **Step 5: Filtret för egna besök**

`@vercel/analytics` 2.0.1:s Astro-komponent (`node_modules/@vercel/analytics/dist/astro/index.astro`) skickar `window.webAnalyticsBeforeSend` som `beforeSend`, och en `beforeSend` som returnerar `null` gör att händelsen inte skickas. Ersätt `<Analytics />` i `<body>` med:

```astro
    <script is:inline>
      // Own visits (spec 2026-09-25 §15): ?va-ignore=1 marks this browser, ?va-ignore=0 removes the mark.
      // A marked browser sends nothing to Vercel Analytics. Runs before the analytics element below.
      (function () {
        try {
          var flag = new URLSearchParams(location.search).get('va-ignore');
          if (flag === '1') localStorage.setItem('birdy-va-ignore', '1');
          if (flag === '0') localStorage.removeItem('birdy-va-ignore');
          if (localStorage.getItem('birdy-va-ignore') === '1') {
            window.webAnalyticsBeforeSend = function () { return null; };
          }
        } catch (e) {
          // Storage blocked (private mode): the visit is counted as usual.
        }
      })();
    </script>
    <Analytics />
```

- [ ] **Step 6: Kör testerna**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/analytics.spec.ts tests/smoke.spec.ts tests/home.spec.ts`
Expected: PASS. De befintliga testerna är oförändrade, inga sidor skickar de nya fälten än.

- [ ] **Step 7: Commit**

```bash
git add src/layouts/Layout.astro tests/analytics.spec.ts
git commit -m "feat(website): Layout tar språkpar, noindex och extra JSON-LD, och egna besök kan filtreras bort"
```

---

### Task 4: Innehållssamlingarna, `lib/species.ts` och inspelningarna i bygget

**Files:**
- Modify: `website/src/content.config.ts`
- Create: `website/src/lib/species.ts`
- Create: `website/src/lib/virtual-species-media.d.ts` (tillägg vid genomförandet)
- Modify: `website/astro.config.mjs`

> **Avvikelser vid genomförandet (2026-10-07, Astro 7.3.5 och zod 4):** (1) `z` importeras från `astro/zod`, och `z.url()` ersätter det utfasade `z.string().url()`. (2) Båda samlingarna har `generateId` = filnamnet: glob-laddarens standard-id är datans `slug`, som här är ett objekt, så alla poster fick id:t `[object Object]` och alla utom en försvann. (3) Fotona laddas inte med `import.meta.glob` utan med den virtuella modulen `virtual:birdy-species-media` (först `virtual:birdy-species-images`) från `astro.config.mjs`, som bara importerar fotona för arter som får en sida i bygget (`isSpeciesBuilt`, som inspelningarna). Vite lägger varje globbad bild i `dist/_astro/` så fort den laddas, även lata globbar och oanvända bilder: med planens kod hade varje opublicerad arts foto gått ut, och ett vanligt bygge hade fått med alla testfoton (uppmätt: 22 testfoton i `dist/` i riktigt läge). `speciesImage()` och resten av `species.ts` är oförändrade utåt; top-level `await` behövs inte längre.

> **Fixvåg efter granskningen (2026-10-07):** (a) `commonSpecies()` returnerar de av de tolv som har en sida, i listans ordning, i stället för att stoppa bygget (Task 13, 14 och 15 är ändrade efter det). (b) Zod-schemat ligger i `src/lib/species-schema.mjs` (ren JS, delas av `content.config.ts` och enhetstesterna i `tests/unit/species-schema.unit.mjs`). Bara en post som får byggas (`hasPageContract` i `species-source.mjs`: status `ok` och `verification`) hålls mot hela sidkontraktet, publicerad eller inte; alla andra poster kontrolleras som ett kuvert (`qid`, `status`, `publish`, `slug`, `names`, `family`, `group`, `review`, `verification`, `text: null` för väntande och misslyckade) och sidfälten släpps, så att en trasig post som aldrig får en sida inte stoppar bygget av alla andra. `publish: true` på en sådan post är fortfarande ett fel. `species.ts` har typerna `SpeciesRecord` (alla poster) och `Species` (sidkontraktet). (c) `marginalia` kan sakna ett språk (`null`). (d) Länkar i krediterna är `z.httpUrl()`, `verification.at` är `z.iso.date()`, `generated.text.at` och jämförelsernas `generated.at` är datum eller pipelinens `isoformat()`. En arts foton och inspelning måste ligga under dess egen QID, en jämförelse får inte ha `a === b`, och `getAllRecords()`/`getComparisons()` stoppar bygget om ett filnamn inte stämmer med `qid` (eller `a` och `b` i strängordning). (e) `builtSpeciesMedia(root)` i `species-source.mjs` räknar ut de byggda arternas foton och inspelningar en gång per bygge med Astros `root` (inte `process.cwd()`); den virtuella modulen exporterar `images` och `audio` (QID till hashad adress), som `audioHref()` läser, och byggkroken kopierar samma filer till samma namn. (f) `scripts/check-preview-build.mjs` (`npm run test:preview-build`) kontrollerar att opublicerade och aldrig byggda arters foton och inspelningar inte finns i `dist/`; Task 14 utökar samma skript.

- [ ] **Step 1: Samlingarna**

I `src/content.config.ts`, ändra importerna överst till:

```ts
import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';
import { comparisonsDir, speciesDir } from './lib/species-source.mjs';
```

och lägg till före `export const collections`:

```ts
// Species pages (spec 2026-09-25 appendix C and D). The pipeline writes src/data/species/*.json and
// src/data/comparisons/*.json; SPECIES_FIXTURES=1 reads the test data in tests/fixtures/ instead.
// Not .strict(): the files carry more than the pages read (raw counts, sources, generation details).
const qid = z.string().regex(/^Q\d+$/);
const localized = z.object({ sv: z.string().min(1), en: z.string().min(1) });
const sentence = z.object({ text: z.string().min(1), factIds: z.array(z.string().min(1)).min(1) });
const sentences = z.array(sentence).min(1);
const statuses = ['resident', 'breeding_migrant', 'passage', 'winter_visitor', 'rare_visitor', 'absent'] as const;
const recordStatus = z.enum(['pending', 'ok', 'failed']);
const licensed = {
  author: z.string().nullable(),
  license: z.string().min(1),
  licenseUrl: z.string().url().nullable(),
  sourceUrl: z.string().url(),
};
const wikiRef = z.object({ title: z.string().min(1), revision: z.string().min(1) });
const langText = z.object({
  lead: sentences,
  fieldMarks: z.array(sentence).min(3).max(4),
  voice: sentences,
  whereWhen: sentences,
  behaviour: z.array(sentence).optional(),
  lookAlikes: z.array(z.object({ other: z.string().min(1), text: sentences })).max(3).default([]),
  metaDescription: z.string().min(120).max(155),
  facts: z.object({
    size: z.object({ value: z.string().min(1), factIds: z.array(z.string()) }).nullable(),
    swedenStatus: z.object({ value: z.enum(statuses), factIds: z.array(z.string()) }).nullable(),
  }),
});

const species = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${speciesDir()}` }),
  schema: z
    .object({
      qid,
      status: recordStatus,
      publish: z.boolean(),
      slug: localized,
      names: z.object({ sv: z.string().min(1), en: z.string().min(1), scientific: z.string().min(1) }),
      family: z.object({ latin: z.string().min(1), sv: z.string().min(1) }),
      group: z.string().min(1),
      iucn: z.string(),
      swedishRedList: z.enum(['RE', 'CR', 'EN', 'VU', 'NT', 'DD', 'not_listed']).optional(),
      identifiable: z.object({ photo: z.boolean(), sound: z.boolean() }).optional(),
      marginalia: localized.optional(),
      images: z.array(z.object({
        role: z.enum(['hero', 'extra']),
        file: z.string().regex(/^Q\d+\/(hero|extra)\.webp$/),
        width: z.number().int().positive(),
        height: z.number().int().positive(),
        ...licensed,
      })).default([]),
      audio: z.object({
        file: z.string().regex(/^Q\d+\/voice\.mp3$/),
        durationSec: z.number().positive(),
        trimmed: z.boolean(),
        ...licensed,
      }).optional(),
      wikipedia: z.object({ sv: wikiRef.optional(), en: wikiRef.optional(), de: wikiRef.optional() }).default({}),
      data: z.object({
        totalReports: z.number().int().nonnegative(),
        months: z.array(z.number().int().min(0).max(100)).length(12).optional(),
        counties: z.record(z.string().regex(/^SE-[A-Z]{1,2}$/), z.number().int().min(0).max(100)).optional(),
        sentences: z.object({ sv: z.array(z.string()), en: z.array(z.string()) }),
      }).optional(),
      // Only to name a look-alike that has no species file (deviation 7 in the plan).
      facts: z.array(z.object({
        id: z.string(),
        topic: z.string(),
        other: z.object({ scientific: z.string().min(1), qid: qid.optional() }).optional(),
      })).default([]),
      review: z.object({
        wave: z.number().int().min(1).optional(),
      }),
      // Revision 2026-10-05: replaces the old review.facts. Only `at` is read by the pages
      // (the credit line and lastReviewed); method, model and spotChecked exist so the
      // pipeline's own records are self-explaining, the site never branches on them.
      verification: z.object({
        method: z.literal('auto'),
        at: z.string().regex(/^\d{4}-\d{2}-\d{2}/),
        model: z.string().min(1),
        spotChecked: z.boolean(),
      }).optional(),
      text: z.object({ sv: langText, en: langText }).nullable(),
      generated: z.object({ text: z.object({ at: z.string() }).optional() }).optional(),
    })
    .superRefine((d, ctx) => {
      if (d.status === 'ok') {
        if (!d.text) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver text` });
        if (!d.images.some((i) => i.role === 'hero')) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver huvudfoto` });
        if (!d.identifiable) ctx.addIssue({ code: 'custom', message: `${d.qid}: status ok kräver identifiable` });
      } else if (d.text) {
        ctx.addIssue({ code: 'custom', message: `${d.qid}: status ${d.status} ska ha text: null` });
      }
      if (d.publish && (d.status !== 'ok' || !d.verification)) {
        ctx.addIssue({ code: 'custom', message: `${d.qid}: publish kräver status ok och kontrollerade fakta` });
      }
    }),
});

const compareText = z.object({
  shortAnswer: sentences,
  rows: z.array(z.object({ feature: z.string().min(1), a: sentence, b: sentence })).min(3).max(5),
  metaDescription: z.string().min(120).max(155),
});

const comparisons = defineCollection({
  loader: glob({ pattern: '*.json', base: `./${comparisonsDir()}` }),
  schema: z
    .object({
      a: qid,
      b: qid,
      status: recordStatus,
      publish: z.boolean(),
      slug: localized,
      text: z.object({ sv: compareText, en: compareText }).nullable(),
      generated: z.object({ at: z.string() }).optional(),
    })
    .superRefine((d, ctx) => {
      if (d.status === 'ok' && !d.text) ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: status ok kräver text` });
      if (d.status !== 'ok' && d.text) ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: status ${d.status} ska ha text: null` });
      if (d.publish && d.status !== 'ok') ctx.addIssue({ code: 'custom', message: `${d.slug.sv}: publish kräver status ok` });
    }),
});
```

Ändra sista raden till:

```ts
export const collections = { fieldNotes, species, comparisons };
```

- [ ] **Step 2: Skriv `src/lib/species.ts`**

```ts
import { getCollection, type CollectionEntry } from 'astro:content';
import type { ImageMetadata } from 'astro';
import groupData from '../data/species-groups.json';
import type { Copy, Locale } from './i18n';
import { audioPublicPath, isComparisonBuilt, isPreview, isSpeciesBuilt, useFixtures } from './species-source.mjs';

export type Species = CollectionEntry<'species'>['data'];
export type SpeciesImage = Species['images'][number];
export type SpeciesText = NonNullable<Species['text']>['sv'];
export type Sentence = SpeciesText['lead'][number];
export type LookAlike = SpeciesText['lookAlikes'][number];
export type Comparison = CollectionEntry<'comparisons'>['data'];
export type WikiLang = 'sv' | 'en' | 'de';
export interface WikiRef { title: string; revision: string }

export interface Group {
  key: string;
  slug: Record<Locale, string>;
  name: Record<Locale, string>;
  photo: string;
  intro: Record<Locale, string>;
}

/** The app's 15 groups in the app's order (src/data/species-groups.json, shared with the pipeline). */
export const GROUPS: Group[] = groupData.groups;
/** Groups with fewer built species get noindex (spec §2). Same rule in src/lib/species-sitemap.mjs. */
export const MIN_GROUP_SIZE = 3;
export const SITE = 'https://birdy.community';
export const PLAY_URL = 'https://play.google.com/store/apps/details?id=se.birdy.android';
export const ABOUT_SLUG: Record<Locale, string> = { sv: 'om-artsidorna', en: 'about-these-pages' };

// Photos: src/assets/species/<QID>/*.webp, or the test images under tests/fixtures/ (SPECIES_FIXTURES=1).
// The test images are imported lazily, so a normal build only loads them if fixture mode asks for them.
const realImages = import.meta.glob<{ default: ImageMetadata }>('../assets/species/*/*.webp', { eager: true });
const fixtureImages = import.meta.glob<{ default: ImageMetadata }>('../../tests/fixtures/species-assets/*/*.webp');
const images = new Map<string, ImageMetadata>(
  useFixtures()
    ? await Promise.all(
        Object.entries(fixtureImages).map(async ([path, load]) => [path.replace('../../tests/fixtures/species-assets/', ''), (await load()).default] as const),
      )
    : Object.entries(realImages).map(([path, mod]) => [path.replace('../assets/species/', ''), mod.default] as const),
);

export function speciesImage(file: string): ImageMetadata {
  const hit = images.get(file);
  if (!hit) throw new Error(`Artbilden saknas: ${file} (${useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species'})`);
  return hit;
}

export function heroOf(s: Species): SpeciesImage {
  const hero = s.images.find((i) => i.role === 'hero');
  if (!hero) throw new Error(`${s.qid} saknar huvudfoto`);
  return hero;
}

/** Where the species' recording is served (astro.config.mjs copies it there), or undefined. */
export function audioHref(s: Species): string | undefined {
  return s.audio ? audioPublicPath(process.cwd(), s) : undefined;
}

let records: Species[] | undefined;
/** Every species file, whatever its status. Used for names (look-alikes), never for pages. */
export async function getAllRecords(): Promise<Species[]> {
  records ??= (await getCollection('species')).map((e) => e.data);
  return records;
}

let built: Species[] | undefined;
/** The species that get a page in this build (spec §14): written, reviewed, and published or previewed. */
export async function getAllSpecies(): Promise<Species[]> {
  built ??= (await getAllRecords()).filter((s) => isSpeciesBuilt(s));
  return built;
}

let builtComparisons: Comparison[] | undefined;
/** The comparisons that get a page: written, published or previewed, and both species built. */
export async function getComparisons(): Promise<Comparison[]> {
  if (!builtComparisons) {
    const qids = new Set((await getAllSpecies()).map((s) => s.qid));
    builtComparisons = (await getCollection('comparisons')).map((e) => e.data).filter((c) => isComparisonBuilt(c, qids));
  }
  return builtComparisons;
}

/** Built only because this is a preview build: noindex and the preview banner. */
export const isUnpublished = (item: { publish: boolean }): boolean => isPreview() && !item.publish;

export const hubHref = (locale: Locale): string => (locale === 'sv' ? '/sv/arter/' : '/species/');
export const speciesHref = (s: Species, locale: Locale): string => `${hubHref(locale)}${s.slug[locale]}/`;
export const groupHref = (g: Group, locale: Locale): string => `${hubHref(locale)}${g.slug[locale]}/`;
export const comparisonHref = (c: Comparison, locale: Locale): string => `${hubHref(locale)}${c.slug[locale]}/`;
export const aboutHref = (locale: Locale): string => `${hubHref(locale)}${ABOUT_SLUG[locale]}/`;

export function groupByKey(key: string): Group {
  const group = GROUPS.find((g) => g.key === key);
  if (!group) throw new Error(`Okänd grupp: ${key}`);
  return group;
}

export function sortByName(list: Species[], locale: Locale): Species[] {
  return [...list].sort((a, b) => a.names[locale].localeCompare(b.names[locale], locale));
}

export function groupSizes(list: Species[]): Map<string, number> {
  const sizes = new Map<string, number>();
  for (const s of list) sizes.set(s.group, (sizes.get(s.group) ?? 0) + 1);
  return sizes;
}

/** Groups with at least one page in this build, in the app's order. Empty groups get no page, chip or card. */
export function activeGroups(list: Species[]): Group[] {
  const sizes = groupSizes(list);
  return GROUPS.filter((g) => (sizes.get(g.key) ?? 0) > 0);
}

export function isGroupIndexed(group: Group, list: Species[]): boolean {
  return (groupSizes(list).get(group.key) ?? 0) >= MIN_GROUP_SIZE;
}

/** The `n` groups with the most built species, ties in the app's order (footer column). */
export function largestGroups(list: Species[], n: number): Group[] {
  const sizes = groupSizes(list);
  return activeGroups(list).sort((a, b) => (sizes.get(b.key) ?? 0) - (sizes.get(a.key) ?? 0)).slice(0, n);
}

/** The footer's "Common species" row. A listed species without a page stops the build (all twelve are in wave 1). */
export function commonSpecies(list: Species[]): Species[] {
  return groupData.common.map((qid) => {
    const hit = list.find((s) => s.qid === qid);
    if (!hit) throw new Error(`Vanliga arter: ${qid} saknar sida i det här bygget (skriven, kontrollerad och publicerad krävs).`);
    return hit;
  });
}

/** The group's fixed photo species, or its first built species in Swedish alphabetical order (spec §6). */
export function groupPhoto(group: Group, list: Species[]): ImageMetadata | undefined {
  const members = sortByName(list.filter((s) => s.group === group.key), 'sv');
  const pick = members.find((s) => s.qid === group.photo) ?? members[0];
  return pick ? speciesImage(heroOf(pick).file) : undefined;
}

/** Up to four other built species in the same family, or in the same group when the family is too small. */
export function related(s: Species, list: Species[], locale: Locale): { kind: 'family' | 'group'; items: Species[] } {
  const others = list.filter((x) => x.qid !== s.qid);
  const family = sortByName(others.filter((x) => x.family.latin === s.family.latin), locale);
  if (family.length >= 2) return { kind: 'family', items: family.slice(0, 4) };
  return { kind: 'group', items: sortByName(others.filter((x) => x.group === s.group), locale).slice(0, 4) };
}

export function countLabel(n: number, t: Copy): string {
  return n === 1 ? t.species.countOne : t.species.countMany.replace('{n}', String(n));
}

const fits = (title: string): boolean => title.length <= 60;

export function speciesTitle(s: Species, locale: Locale, t: Copy): string {
  const long = t.species.titleSpecies.replace('{name}', s.names[locale]);
  return fits(long) ? long : t.species.titleSpeciesShort.replace('{name}', s.names[locale]);
}

export function groupTitle(g: Group, n: number, locale: Locale, t: Copy): string {
  const long = t.species.titleGroup.replace('{group}', g.name[locale]).replace('{count}', countLabel(n, t));
  return fits(long) ? long : t.species.titleGroupShort.replace('{group}', g.name[locale]);
}

export interface PairSide { side: 'a' | 'b'; species: Species }

/** The comparison's two species in this language's slug order (spec §4), each with its side in the record. */
export function comparisonPair(c: Comparison, locale: Locale, list: Species[]): [PairSide, PairSide] {
  const find = (qid: string): Species => {
    const hit = list.find((s) => s.qid === qid);
    if (!hit) throw new Error(`Jämförelsen ${c.slug.sv} saknar arten ${qid}`);
    return hit;
  };
  const a: PairSide = { side: 'a', species: find(c.a) };
  const b: PairSide = { side: 'b', species: find(c.b) };
  return a.species.slug[locale] <= b.species.slug[locale] ? [a, b] : [b, a];
}

/** "Blåmes" and "talgoxe": in Swedish the second name is in lower case, as in running text. */
export function pairNames(pair: [PairSide, PairSide], locale: Locale): [string, string] {
  const first = pair[0].species.names[locale];
  const second = pair[1].species.names[locale];
  return [first, locale === 'sv' ? second.toLocaleLowerCase('sv') : second];
}

export function comparisonTitle(names: [string, string], t: Copy): string {
  const fill = (tpl: string) => tpl.replace('{a}', names[0]).replace('{b}', names[1]);
  const long = fill(t.species.titleCompare);
  return fits(long) ? long : fill(t.species.titleCompareShort);
}

export function joinSentences(list?: Sentence[]): string {
  return (list ?? []).map((x) => x.text).join(' ');
}

export interface LookAlikeView {
  name: string;
  /** No species file: the name is the scientific one and is shown in italics. */
  scientificOnly: boolean;
  /** The other species when it has a page in this build. */
  species?: Species;
  text: string;
  comparison?: Comparison;
}

/** What the "Can be confused with" section shows for one look-alike (spec §5, deviation 7). */
export function lookAlikeView(s: Species, item: LookAlike, locale: Locale, builtList: Species[], records: Species[], comps: Comparison[]): LookAlikeView | undefined {
  const isQid = /^Q\d+$/.test(item.other);
  const record = isQid ? records.find((x) => x.qid === item.other) : undefined;
  const page = isQid ? builtList.find((x) => x.qid === item.other) : undefined;
  const fact = s.facts.find((f) => f.topic === 'lookalike' && (f.other?.qid === item.other || f.other?.scientific === item.other));
  const name = record ? record.names[locale] : (fact?.other?.scientific ?? (isQid ? undefined : item.other));
  if (!name) return undefined;
  const comparison = page
    ? comps.find((c) => (c.a === s.qid && c.b === page.qid) || (c.b === s.qid && c.a === page.qid))
    : undefined;
  return { name, scientificOnly: !record, species: page, text: joinSentences(item.text), comparison };
}

/** The date the species' facts were verified, YYYY-MM-DD (spec Revision 2026-10-05). Built species always have one. */
export const reviewDate = (s: Species): string => (s.verification?.at ?? '').slice(0, 10);
export const laterDate = (a: string, b: string): string => (a > b ? a : b);

export function formatDate(iso: string, locale: Locale): string {
  return new Intl.DateTimeFormat(locale === 'sv' ? 'sv-SE' : 'en-GB', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' })
    .format(new Date(`${iso.slice(0, 10)}T00:00:00Z`));
}

/** The app box says only what the app can do for this species (spec §5); a group or comparison gets the general line. */
export function appText(s: Species | undefined, t: Copy): string {
  const id = s?.identifiable;
  if (id?.photo && id.sound) return t.species.appTextBoth;
  if (id?.sound) return t.species.appTextSound;
  if (id?.photo) return t.species.appTextPhoto;
  return t.species.appTextNone;
}

/** Play link with UTM tags, readable in Play Console's acquisition report (spec §15). */
export function playHref(campaign: string, medium: 'species' | 'group' | 'hub' | 'compare'): string {
  const referrer = `utm_source=birdy.community&utm_medium=${medium}&utm_campaign=${campaign}`;
  return `${PLAY_URL}&referrer=${encodeURIComponent(referrer)}`;
}

/** Lowercase, accents removed: "Gök" and "gok" both match. The page script normalises the same way. */
export function searchKey(s: Species): string {
  return [s.names.sv, s.names.en, s.names.scientific].join(' ').normalize('NFD').replace(/\p{M}/gu, '').toLowerCase();
}

export function wikiUrl(lang: WikiLang, ref: WikiRef): string {
  const title = encodeURIComponent(ref.title.replace(/ /g, '_'));
  return `https://${lang}.wikipedia.org/w/index.php?title=${title}&oldid=${ref.revision}`;
}

/** The Wikipedia articles a species' text is based on, in the order Swedish, English, German. */
export function wikiSources(s: Species): { lang: WikiLang; ref: WikiRef }[] {
  return (['sv', 'en', 'de'] as const).flatMap((lang) => {
    const ref = s.wikipedia[lang];
    return ref ? [{ lang, ref }] : [];
  });
}

export interface Crumb { name: string; href: string }

export function breadcrumbJsonLd(crumbs: Crumb[]): Record<string, unknown> {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: crumbs.map((c, i) => ({ '@type': 'ListItem', position: i + 1, name: c.name, item: new URL(c.href, SITE).toString() })),
  };
}

export function itemListJsonLd(pathname: string, name: string, locale: Locale, items: Species[]): Record<string, unknown> {
  return {
    '@type': 'CollectionPage',
    url: new URL(pathname, SITE).toString(),
    name,
    inLanguage: locale,
    mainEntity: {
      '@type': 'ItemList',
      numberOfItems: items.length,
      itemListElement: items.map((s, i) => ({
        '@type': 'ListItem', position: i + 1, name: s.names[locale], url: new URL(speciesHref(s, locale), SITE).toString(),
      })),
    },
  };
}

export function taxonJsonLd(s: Species): Record<string, unknown> {
  return {
    '@type': 'Taxon',
    name: s.names.scientific,
    alternateName: [s.names.sv, s.names.en],
    taxonRank: 'species',
    sameAs: `https://www.wikidata.org/wiki/${s.qid}`,
  };
}

export function audioJsonLd(s: Species): Record<string, unknown> | undefined {
  const href = audioHref(s);
  if (!s.audio || !href) return undefined;
  return {
    '@type': 'AudioObject',
    contentUrl: new URL(href, SITE).toString(),
    encodingFormat: 'audio/mpeg',
    ...(s.audio.licenseUrl ? { license: s.audio.licenseUrl } : {}),
    acquireLicensePage: s.audio.sourceUrl,
    ...(s.audio.author ? { creator: { '@type': 'Person', name: s.audio.author }, creditText: s.audio.author } : {}),
  };
}

/** Throws when two pages in one language would get the same address (spec §4). */
export function assertUniqueSlugs(slugs: string[], locale: Locale): void {
  const seen = new Set<string>();
  for (const slug of slugs) {
    if (seen.has(slug)) throw new Error(`Samma adress två gånger (${locale}): ${slug}`);
    seen.add(slug);
  }
}
```

- [ ] **Step 3: Inspelningarna kopieras in vid bygget**

I `astro.config.mjs`:

Ändra importraden `import { readFileSync, readdirSync } from 'node:fs';` till:

```js
import { copyFileSync, mkdirSync, readFileSync, readdirSync } from 'node:fs';
```

och lägg till efter `import { dirname, resolve } from 'node:path';`:

```js
import { assetsDir, audioPublicPath, isSpeciesBuilt, readJsonDir, speciesDir } from './src/lib/species-source.mjs';
```

Lägg till före `export default defineConfig({`:

```js
// Recordings live beside the photos (src/assets/species/<QID>/voice.mp3) and are copied into dist only
// for species that get a page in this build, under the content-hashed name the page links to, so an
// unpublished recording is never served (spec 2026-09-25 §9.9; deviation 9 in the plan).
const speciesAudio = {
  name: 'birdy-species-audio',
  hooks: {
    'astro:build:done': ({ dir, logger }) => {
      const out = fileURLToPath(new URL('audio/species/', dir));
      let copied = 0;
      for (const record of readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r) && r.audio)) {
        mkdirSync(out, { recursive: true });
        const name = audioPublicPath(root, record).split('/').pop();
        copyFileSync(resolve(root, assetsDir(), record.audio.file), resolve(out, name));
        copied += 1;
      }
      logger.info(`${copied} inspelningar kopierade till audio/species/`);
    },
  },
};
```

och ändra `integrations: [sitemap({ ... })],` så att `speciesAudio` läggs till sist i listan:

```js
  integrations: [sitemap({
    serialize(item) {
      const d = noteDates.get(new URL(item.url).pathname);
      if (d) item.lastmod = d;
      return item;
    },
  }), speciesAudio],
```

- [ ] **Step 4: Typkontroll och bygge**

Run: `npx astro check && npm run build:fixtures`
Expected: `astro check` utan nya fel (det kända Vite/Tailwind-typfelet i `astro.config.mjs` räknas inte; nya fel om `t.species` åtgärdas i Task 5, kör i så fall bara bygget nu). Bygget läser 20 artfiler och 4 jämförelser utan schemafel och loggar `4 inspelningar kopierade till audio/species/`.

Run: `ls dist/audio/species`
Expected: fyra filer, `Q25234.<hash>.mp3`, `Q25404.<hash>.mp3`, `Q25485.<hash>.mp3` och `Q25756.<hash>.mp3`.

Om bygget klagar på top-level `await` i `src/lib/species.ts`: byt de två glob-raderna och `images` mot en eager-glob även för testbilderna:

```ts
const fixtureImages = import.meta.glob<{ default: ImageMetadata }>('../../tests/fixtures/species-assets/*/*.webp', { eager: true });
const images = new Map<string, ImageMetadata>(
  useFixtures()
    ? Object.entries(fixtureImages).map(([path, mod]) => [path.replace('../../tests/fixtures/species-assets/', ''), mod.default] as const)
    : Object.entries(realImages).map(([path, mod]) => [path.replace('../assets/species/', ''), mod.default] as const),
);
```

Testbilderna är små (cirka 10 KB styck), så det enda priset är att de kan följa med som oanvända originalfiler i `dist/_astro/`. Notera valet i commit-meddelandet.

Run (bara i riktigt läge, utan testdata): `npm run build`
Expected: bygget går igenom med varningar om att `src/data/species` och `src/data/comparisons` saknar filer. Inga artsidor finns än, så inget läser datan.

- [ ] **Step 5: Commit**

```bash
git add src/content.config.ts src/lib/species.ts astro.config.mjs
git commit -m "feat(website): samlingarna species och comparisons, lib/species.ts och inspelningar i bygget"
```

---

### Task 5: Texterna (SV och EN)

**Files:**
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json`

- [ ] **Step 1: Lägg till nycklarna i `copy.sv.json`**

I objektet `nav`, lägg till `"species": "Arter",`. I `footer`, lägg till `"species": "Arter", "allSpecies": "Alla arter från A till Ö", "commonSpecies": "Vanliga arter",`. I `guide`, lägg till `"browse": "Bläddra bland arterna",`. Lägg till två nya toppnivåobjekt före `footer`:

```json
  "species": {
    "allChip": "Alla arter",
    "groupsLabel": "Grupper",
    "searchLabel": "Sök art",
    "searchPlaceholder": "Sök art",
    "searchSubmit": "Sök",
    "kicker": "Uppslagsverket",
    "hubHeadline": "Fåglar i Sverige och *Europa*",
    "hubLead": "{n} vanliga fåglar med foton, kännetecken och läten. Samma uppslagsverk som i appen, där du också kan känna igen fågeln på plats.",
    "hubGroups": "Grupperna",
    "hubCompare": "Lätta att blanda ihop",
    "hubAll": "Alla arter från A till Ö",
    "hubAbout": "Så gör vi artsidorna",
    "noResults": "Ingen art matchar sökningen.",
    "groupSpecies": "Arterna",
    "countOne": "1 art",
    "countMany": "{n} arter",
    "titleSpecies": "{name}: kännetecken, läte och foton | Birdy",
    "titleSpeciesShort": "{name}: kännetecken och läte | Birdy",
    "titleGroup": "{group}: {count} med foton och kännetecken | Birdy",
    "titleGroupShort": "{group}: arter och kännetecken | Birdy",
    "titleHub": "Fåglar i Sverige och Europa: {count} med foton | Birdy",
    "titleCompare": "{a} eller {b}? Så skiljer du dem åt | Birdy",
    "titleCompareShort": "{a} eller {b}? | Birdy",
    "descGroup": "{group}: {count} med foton, kännetecken och läten. Lär dig skilja dem åt i fält, och känn igen dem på plats med appen Birdy.",
    "descHub": "Bläddra bland {n} vanliga fåglar i Sverige och Europa. Foton, kännetecken och läten, sorterade i samma grupper som i appen Birdy.",
    "crumbLabel": "Brödsmulor",
    "crumbHome": "Birdy",
    "crumbHub": "Arter",
    "facts": {
      "scientific": "Vetenskapligt namn",
      "family": "Familj",
      "sweden": "I Sverige",
      "size": "Storlek",
      "swedishRedList": "Svenska rödlistan 2025",
      "iucn": "Global rödlista (IUCN)"
    },
    "statusLabels": {
      "resident": "Stannfågel",
      "breeding_migrant": "Flyttfågel, häckar här",
      "passage": "Ses under flyttningen",
      "winter_visitor": "Vintergäst",
      "rare_visitor": "Sällsynt gäst",
      "absent": "Förekommer inte"
    },
    "redListLabels": {
      "RE": "Nationellt utdöd",
      "CR": "Akut hotad",
      "EN": "Starkt hotad",
      "VU": "Sårbar",
      "NT": "Nära hotad",
      "DD": "Kunskapsbrist",
      "not_listed": "Inte rödlistad"
    },
    "iucnLabels": {
      "LC": "Livskraftig",
      "NT": "Nära hotad",
      "VU": "Sårbar",
      "EN": "Starkt hotad",
      "CR": "Akut hotad",
      "EW": "Utdöd i vilt tillstånd",
      "EX": "Utdöd",
      "DD": "Kunskapsbrist"
    },
    "headMarks": "Så känner du igen den",
    "headVoice": "Läte",
    "headWhere": "Var och när",
    "headBehaviour": "Föda och beteende",
    "headLookAlikes": "Kan förväxlas med",
    "compareLink": "Jämför {a} och {b}",
    "chartTitle": "När ses den i Sverige?",
    "chartCaption": "Andel av alla fågelrapporter per månad i Artportalen 2016 till 2025.",
    "mapTitle": "Var rapporteras den?",
    "mapCaption": "Andel av alla fågelrapporter per län i Artportalen 2016 till 2025.",
    "mapLegend": ["Inga rapporter", "Liten andel", "Mellanstor andel", "Störst andel"],
    "monthLetters": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"],
    "moreFamily": "Fler {family}",
    "moreGroup": "Fler {group}",
    "appHeadline": "Osäker på vad du ser?",
    "appTextBoth": "Birdy känner igen arten på foto eller läte, direkt i telefonen och utan täckning.",
    "appTextSound": "Birdy känner igen arten på lätet, direkt i telefonen och utan täckning.",
    "appTextPhoto": "Birdy känner igen arten på foto, direkt i telefonen och utan täckning.",
    "appTextNone": "Birdy hjälper dig känna igen fåglarna omkring dig på foto och läte, direkt i telefonen och utan täckning.",
    "plate": "Pl. {n}",
    "photoCredit": "Foto:",
    "via": "via",
    "unknownAuthor": "okänd fotograf",
    "recordingLabel": "Inspelning: {name}",
    "recordingCredit": "Inspelning:",
    "unknownRecordist": "okänd inspelare",
    "trimmed": "klippt",
    "textCredit": "Texten bygger på Wikipedia och får delas under",
    "sources": "Källor",
    "articleSv": "svenska artikeln",
    "articleEn": "engelska artikeln",
    "articleDe": "tyska artikeln",
    "dataCreditReports": "Rapportdata: Artportalen (SLU Artdatabanken) via {gbif}, 2016 till 2025.",
    "dataCreditRedList": "Rödlista: Rödlistade arter i Sverige 2025, SLU Artdatabanken.",
    "reviewed": "Kontrollerad mot källorna {date}.",
    "aboutLink": "Så gör vi artsidorna",
    "reportError": "Hittade du ett fel? Skriv till oss.",
    "reportSubject": "Fel på artsidan: {name}",
    "reportSubjectCompare": "Fel på jämförelsesidan: {name}",
    "altHero": "{name} ({scientific})",
    "altExtra": "{name}, ytterligare foto",
    "previewBanner": "Förhandsvisning, inte publicerad",
    "compareKicker": "Lätta att blanda ihop",
    "compareHeadline": "{a} eller *{b}*?",
    "compareItem": "{a} eller {b}",
    "compareTable": "Så skiljer du dem åt",
    "compareFeature": "Kännetecken",
    "compareChart": "När ses de?",
    "compareApp": "Fortfarande osäker?",
    "chartSeriesSentence": "{name}: {text}"
  },
  "speciesAbout": {
    "title": "Så gör vi artsidorna: källor och granskning | Birdy",
    "description": "Så skrivs Birdys artsidor: källorna, hur AI används, hur fakta kontrolleras, vilka licenser som gäller och hur du rapporterar fel.",
    "crumb": "Så gör vi artsidorna",
    "headline": "Så gör vi *artsidorna*",
    "lead": "Varje artsida bygger på flera källor och kontrolleras i flera steg, mest automatiskt och vid undantag av en människa, innan den publiceras. Så här går det till.",
    "sections": [
      {
        "heading": "Källorna",
        "paragraphs": [
          "Texterna bygger på artiklarna om varje art på svenska, engelska och tyska Wikipedia.",
          "Diagrammet över när arten syns och kartan över var den rapporteras räknas fram ur Artportalen, Sveriges rapportsystem för fynd av växter och djur, som SLU Artdatabanken delar via den internationella databasen GBIF. Siffrorna visar artens andel av alla fågelrapporter, så att en månad eller ett län med många fågelskådare inte ser ut att ha fler fåglar än det har.",
          "Den svenska rödlistestatusen kommer från Rödlistade arter i Sverige 2025, från SLU Artdatabanken. Foton och inspelningar kommer från Wikimedia Commons."
        ]
      },
      {
        "heading": "Så används AI",
        "paragraphs": [
          "En språkmodell läser artiklarna och plockar ut fakta om utseende, läte, miljö och förekomst i Sverige. Varje faktum ska ha ett ordagrant citat ur artikeln, och ett program kontrollerar att citatet verkligen finns där. Fakta utan giltigt citat stryks.",
          "Texten på sidan skrivs sedan av en modell som bara får se de fakta som har godkänts, inte artiklarna. En annan modell läser därefter varje mening och jämför den med fakta. Meningar som inte stöds skrivs om eller stryks.",
          "Diagrammet, kartan och rödlistestatusen räknas fram direkt ur datan, utan någon språkmodell."
        ]
      },
      {
        "heading": "Granskningen",
        "paragraphs": [
          "En andra modell kontrollerar varje faktum mot sitt citat i artikeln. Kod jämför siffrorna mellan de olika språkens artiklar och jämför förekomsten i Sverige med Artportalen och den svenska rödlistan. Inspelningen kontrolleras med Birdys egen ljudmodell. Det som inte går att avgöra automatiskt granskas av Albin Abrahamsson, som har byggt Birdy, innan sidan publiceras. Efter publicering granskar han dessutom ett löpande stickprov av redan publicerade sidor. Datumet för den senaste kontrollen står längst ned på varje artsida."
        ]
      },
      {
        "heading": "Licenserna",
        "paragraphs": [
          "Texterna på artsidorna bygger på Wikipedia och får därför delas under CC BY-SA 4.0, om du anger Birdy som källa och delar vidare på samma villkor. Foton och inspelningar har sina egna licenser, och varje sida anger upphovsperson, licens och källa för dem."
        ]
      },
      {
        "heading": "Rättelser",
        "paragraphs": [
          "Hittar du ett fel? Skriv till {email}, gärna med en länk till sidan. Vi rättar sidan och sätter ett nytt kontrolldatum."
        ]
      }
    ]
  },
```

- [ ] **Step 2: Samma nycklar i `copy.en.json`**

`nav.species`: `"Species"`. `footer`: `"species": "Species", "allSpecies": "All species A to Z", "commonSpecies": "Common species"`. `guide.browse`: `"Browse the species"`. Objekten:

```json
  "species": {
    "allChip": "All species",
    "groupsLabel": "Groups",
    "searchLabel": "Search species",
    "searchPlaceholder": "Search species",
    "searchSubmit": "Search",
    "kicker": "Field guide",
    "hubHeadline": "Birds of Sweden and *Europe*",
    "hubLead": "{n} common birds with photos, field marks and calls. The same field guide as in the app, where you can also identify the bird on the spot.",
    "hubGroups": "The groups",
    "hubCompare": "Easy to mix up",
    "hubAll": "All species A to Z",
    "hubAbout": "How we make these pages",
    "noResults": "No species match your search.",
    "groupSpecies": "The species",
    "countOne": "1 species",
    "countMany": "{n} species",
    "titleSpecies": "{name}: identification, song and photos | Birdy",
    "titleSpeciesShort": "{name}: identification | Birdy",
    "titleGroup": "{group}: {count} with photos and ID tips | Birdy",
    "titleGroupShort": "{group}: species and ID tips | Birdy",
    "titleHub": "Birds of Sweden and Europe: {count} with photos | Birdy",
    "titleCompare": "{a} vs {b}: how to tell them apart | Birdy",
    "titleCompareShort": "{a} vs {b} | Birdy",
    "descGroup": "{group}: {count} with photos, field marks and calls. Learn to tell them apart, and identify them on the spot with the Birdy app.",
    "descHub": "Browse {n} common birds of Sweden and Europe. Photos, field marks and calls, sorted in the same groups as in the Birdy app.",
    "crumbLabel": "Breadcrumb",
    "crumbHome": "Birdy",
    "crumbHub": "Species",
    "facts": {
      "scientific": "Scientific name",
      "family": "Family",
      "sweden": "In Sweden",
      "size": "Size",
      "swedishRedList": "Swedish Red List 2025",
      "iucn": "Global Red List (IUCN)"
    },
    "statusLabels": {
      "resident": "Resident all year",
      "breeding_migrant": "Summer visitor, breeds here",
      "passage": "Seen on migration",
      "winter_visitor": "Winter visitor",
      "rare_visitor": "Rare visitor",
      "absent": "Does not occur"
    },
    "redListLabels": {
      "RE": "Regionally extinct",
      "CR": "Critically endangered",
      "EN": "Endangered",
      "VU": "Vulnerable",
      "NT": "Near threatened",
      "DD": "Data deficient",
      "not_listed": "Not red-listed"
    },
    "iucnLabels": {
      "LC": "Least concern",
      "NT": "Near threatened",
      "VU": "Vulnerable",
      "EN": "Endangered",
      "CR": "Critically endangered",
      "EW": "Extinct in the wild",
      "EX": "Extinct",
      "DD": "Data deficient"
    },
    "headMarks": "How to recognise it",
    "headVoice": "Call and song",
    "headWhere": "Where and when",
    "headBehaviour": "Food and behaviour",
    "headLookAlikes": "Can be confused with",
    "compareLink": "Compare the {a} and the {b}",
    "chartTitle": "When is it seen in Sweden?",
    "chartCaption": "Share of all bird reports per month in Artportalen, 2016 to 2025.",
    "mapTitle": "Where is it reported?",
    "mapCaption": "Share of all bird reports per county in Artportalen, 2016 to 2025.",
    "mapLegend": ["No reports", "Small share", "Medium share", "Largest share"],
    "monthLetters": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"],
    "moreFamily": "More in the {family} family",
    "moreGroup": "More {group}",
    "appHeadline": "Not sure what you are seeing?",
    "appTextBoth": "Birdy identifies this species from a photo or its song, right on your phone and without signal.",
    "appTextSound": "Birdy identifies this species from its song, right on your phone and without signal.",
    "appTextPhoto": "Birdy identifies this species from a photo, right on your phone and without signal.",
    "appTextNone": "Birdy helps you identify the birds around you from photos and songs, right on your phone and without signal.",
    "plate": "Pl. {n}",
    "photoCredit": "Photo:",
    "via": "via",
    "unknownAuthor": "unknown photographer",
    "recordingLabel": "Recording: {name}",
    "recordingCredit": "Recording:",
    "unknownRecordist": "unknown recordist",
    "trimmed": "trimmed",
    "textCredit": "The text is based on Wikipedia and may be shared under",
    "sources": "Sources",
    "articleSv": "Swedish article",
    "articleEn": "English article",
    "articleDe": "German article",
    "dataCreditReports": "Report data: Artportalen (SLU Swedish Species Information Centre) via {gbif}, 2016 to 2025.",
    "dataCreditRedList": "Red list: The Swedish Red List 2025, SLU Swedish Species Information Centre.",
    "reviewed": "Checked against sources on {date}.",
    "aboutLink": "How we make these pages",
    "reportError": "Found a mistake? Write to us.",
    "reportSubject": "Mistake on the species page: {name}",
    "reportSubjectCompare": "Mistake on the comparison page: {name}",
    "altHero": "{name} ({scientific})",
    "altExtra": "{name}, another photo",
    "previewBanner": "Preview, not published",
    "compareKicker": "Easy to mix up",
    "compareHeadline": "{a} or *{b}*?",
    "compareItem": "{a} or {b}",
    "compareTable": "How to tell them apart",
    "compareFeature": "Feature",
    "compareChart": "When are they seen?",
    "compareApp": "Still not sure?",
    "chartSeriesSentence": "{name}: {text}"
  },
  "speciesAbout": {
    "title": "How we make the species pages: sources and review | Birdy",
    "description": "How the species pages on Birdy are made: the sources, how AI is used, how the facts are checked, the licences and how to report a mistake.",
    "crumb": "How we make these pages",
    "headline": "How we make the *species pages*",
    "lead": "Every species page is built from several sources and checked in several steps, mostly automatically and by a person when something needs it, before it is published. This is how it works.",
    "sections": [
      {
        "heading": "The sources",
        "paragraphs": [
          "The texts are based on the articles about each species on Swedish, English and German Wikipedia.",
          "The chart of when the species is seen and the map of where it is reported are calculated from Artportalen, Sweden's reporting system for sightings of plants and animals, which the SLU Swedish Species Information Centre shares through the international database GBIF. The figures show the species' share of all bird reports, so that a month or a county with many birdwatchers does not look as if it had more birds than it does.",
          "The Swedish red list status comes from The Swedish Red List 2025 by the SLU Swedish Species Information Centre. Photos and recordings come from Wikimedia Commons."
        ]
      },
      {
        "heading": "How AI is used",
        "paragraphs": [
          "A language model reads the articles and picks out facts about appearance, call, habitat and occurrence in Sweden. Every fact needs a word for word quote from the article, and a program checks that the quote really is there. Facts without a valid quote are removed.",
          "The text on the page is then written by a model that only sees the approved facts, not the articles. Another model then reads every sentence and compares it with the facts. Sentences that are not supported are rewritten or removed.",
          "The chart, the map and the red list status are calculated directly from the data, without any language model."
        ]
      },
      {
        "heading": "The review",
        "paragraphs": [
          "A second model checks every fact against its quote in the article. Code compares the numbers between the different language articles, and compares the occurrence in Sweden with Artportalen and the Swedish red list. The recording is checked with Birdy's own sound model. What cannot be settled automatically is reviewed by Albin Abrahamsson, who built Birdy, before the page is published. After publishing, he also reviews an ongoing spot check of already published pages. The date of the latest check is at the bottom of every species page."
        ]
      },
      {
        "heading": "The licences",
        "paragraphs": [
          "The texts on the species pages are based on Wikipedia and may therefore be shared under CC BY-SA 4.0, if you credit Birdy and share on the same terms. Photos and recordings have their own licences, and every page names the author, licence and source for them."
        ]
      },
      {
        "heading": "Corrections",
        "paragraphs": [
          "Found a mistake? Write to {email}, ideally with a link to the page. We correct the page and set a new check date."
        ]
      }
    ]
  },
```

- [ ] **Step 3: Kör textvakterna**

Run: `npm run test:i18n && npm run test:no-dashes && npm run test:palette && npm run test:no-accuracy && npx astro check`
Expected: paritet OK, inga streck, ingen noggrannhetssiffra och inga nya typfel (nu känner `Copy` till `species` och `speciesAbout`).

- [ ] **Step 4: Commit**

```bash
git add src/content/copy.sv.json src/content/copy.en.json
git commit -m "feat(website): texter för artsidorna, jämförelserna och om-sidan (SV och EN)"
```

---

### Task 6: Kategoriraden, artkortet och de delade stilarna

**Files:**
- Modify: `website/src/components/ui/Icon.astro` (ny ikon `search`)
- Create: `website/src/styles/species.css`
- Create: `website/src/components/species/CategoryBar.astro`
- Create: `website/src/components/species/SpeciesCard.astro`

- [ ] **Step 1: Sökikonen**

I `src/components/ui/Icon.astro`, lägg till i `paths` efter `menu`:

```ts
  search: '<circle cx="11" cy="11" r="6"/><path d="m20 20-4.5-4.5"/>',
```

- [ ] **Step 2: Delade stilar**

`src/styles/species.css`:

```css
/* Shared by the species hub, group, species, comparison and about pages (spec 2026-09-25 §5 to §8).
   Plain CSS, so it reaches markup in every component; palette tokens only (npm run test:palette). */
.sp-crumbs ol { display: flex; flex-wrap: wrap; gap: 6px; list-style: none; margin: 0 0 16px; padding: 0; font-size: 12.5px; color: var(--muted); }
.sp-crumbs li + li::before { content: '›'; margin-right: 6px; }
.sp-crumbs a { color: var(--muted); border-bottom: 1px solid transparent; }
.sp-crumbs a:hover { color: var(--rust); border-bottom-color: currentColor; }
.sp-h2 { font-size: clamp(24px, 2.6vw, 30px); margin: 44px 0 16px; }
.sp-cards { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 14px; }
.sp-app { background: var(--dark); color: var(--cream); border-radius: 14px; padding: 18px 20px; --jh-ink: var(--cream); }
.sp-app .sp-app-h { margin: 0 0 6px; font-family: var(--font-serif); font-style: italic; font-size: 21px; color: var(--apricot); }
.sp-app p { margin: 0 0 12px; font-size: 14px; line-height: 1.55; color: var(--cream); }
.sp-app :focus-visible { outline-color: var(--apricot); }
.sp-preview { margin: 0 0 18px; padding: 8px 14px; border: 1px dashed var(--rust); border-radius: 10px; background: var(--peach); color: var(--ink); font-size: 13px; font-weight: 600; }
.plate { margin: 0; background: var(--card); border: 1px solid var(--line); padding: 9px 9px 5px; box-shadow: 0 2px 0 var(--line); }
.plate img { display: block; width: 100%; height: auto; }
.plate figcaption { display: flex; justify-content: space-between; gap: 12px; padding-top: 4px; font-family: var(--font-script); font-size: 17px; color: var(--muted); }
.facts { margin: 0; }
.facts div { display: flex; justify-content: space-between; gap: 12px; padding: 8px 0; border-bottom: 1px solid var(--line); font-size: 14px; }
.facts dt { color: var(--muted); }
.facts dd { margin: 0; font-weight: 600; text-align: right; }
.data-summary { font-size: 14.5px; line-height: 1.6; color: var(--ink); margin: 0; }
.fig-caption { margin-top: 6px; font-size: 12px; line-height: 1.45; color: var(--muted); }
@media (max-width: 760px) {
  .sp-cards { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
}
```

- [ ] **Step 3: Artkortet**

`src/components/species/SpeciesCard.astro`:

```astro
---
import { Image } from 'astro:assets';
import type { Locale } from '../../lib/i18n';
import { heroOf, speciesHref, speciesImage, type Species } from '../../lib/species';

interface Props { species: Species; locale: Locale }
const { species: s, locale } = Astro.props;
---

<a class="scard" href={speciesHref(s, locale)}>
  <Image src={speciesImage(heroOf(s).file)} alt="" widths={[320, 480]} sizes="(max-width: 760px) 45vw, 220px" loading="lazy" decoding="async" />
  <span class="scard-name">{s.names[locale]}</span>
  <span class="scard-latin">{s.names.scientific}</span>
</a>

<style>
  .scard { display: block; height: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper), box-shadow .25s var(--ease-paper); }
  .scard:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(var(--dark-rgb), .12); }
  .scard :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 8px; }
  .scard-name { display: block; margin: 8px 4px 0; font-weight: 600; font-size: 14px; color: var(--ink); }
  .scard-latin { display: block; margin: 0 4px 4px; font-family: var(--font-script); font-size: 17px; color: var(--muted); }
</style>
```

- [ ] **Step 4: Kategoriraden**

`src/components/species/CategoryBar.astro`:

```astro
---
import Icon from '../ui/Icon.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import { activeGroups, getAllSpecies, groupHref, groupSizes, hubHref } from '../../lib/species';

interface Props {
  locale: Locale;
  /** 'all' on the hub, a group key on group and species pages, '' when no chip is current. */
  active: string;
  /** The hub has its own search field, so it hides this one. */
  search?: boolean;
}
const { locale, active, search = true } = Astro.props;
const t = getCopy(locale);
const all = await getAllSpecies();
const sizes = groupSizes(all);
const chips = [
  { key: 'all', href: hubHref(locale), label: t.species.allChip, n: all.length },
  ...activeGroups(all).map((g) => ({ key: g.key, href: groupHref(g, locale), label: g.name[locale], n: sizes.get(g.key) ?? 0 })),
];
---

<div class="catbar" data-catbar>
  <div class="catbar-inner">
    <nav class="chips" aria-label={t.species.groupsLabel} data-chips>
      {chips.map((c) => (
        <a class:list={['chip', { 'is-active': c.key === active }]} href={c.href} aria-current={c.key === active ? 'page' : undefined}>
          {c.label} <span class="n">{c.n}</span>
        </a>
      ))}
    </nav>
    {search && (
      <form class="search" action={hubHref(locale)} method="get" role="search">
        <label class="sr-only" for="cat-q">{t.species.searchLabel}</label>
        <input id="cat-q" name="q" type="search" placeholder={t.species.searchPlaceholder} autocomplete="off" />
        <button type="submit" class="search-btn" aria-label={t.species.searchSubmit}><Icon name="search" size={18} /></button>
      </form>
    )}
  </div>
</div>

<script>
  // Bring the active chip into view on narrow screens, without scrolling the page itself.
  const chips = document.querySelector<HTMLElement>('[data-chips]');
  const current = chips?.querySelector<HTMLElement>('.chip.is-active');
  if (chips && current) chips.scrollLeft = current.offsetLeft - (chips.clientWidth - current.offsetWidth) / 2;
</script>

<style>
  .catbar { position: sticky; top: 76px; z-index: 90; background: var(--card); border-bottom: 1px solid var(--line); }
  .catbar-inner { max-width: 1320px; margin: 0 auto; display: flex; align-items: center; gap: 16px; padding: 10px 44px; }
  .chips { display: flex; gap: 8px; overflow-x: auto; scrollbar-width: none; flex: 1; min-width: 0; }
  .chips::-webkit-scrollbar { display: none; }
  .chip { flex: none; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); font-size: 13px; font-weight: 600; color: var(--ink); white-space: nowrap; transition: border-color .2s; }
  .chip:hover { border-color: var(--rust); }
  .chip .n { font-weight: 400; color: var(--muted); }
  .chip.is-active { background: var(--rust); border-color: var(--rust); color: var(--cream); }
  .chip.is-active .n { color: var(--cream); }
  .search { flex: none; display: flex; align-items: center; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); padding: 0 4px 0 14px; }
  .search:focus-within { outline: 3px solid var(--rust); outline-offset: 2px; }
  .search input { border: 0; background: none; font: inherit; font-size: 13px; width: 150px; padding: 7px 0; color: var(--ink); outline: none; }
  .search-btn { border: 0; background: none; color: var(--muted); width: 32px; height: 32px; display: grid; place-items: center; cursor: pointer; }
  @media (max-width: 1023px) {
    .catbar { top: 64px; }
    .catbar-inner { padding: 8px 20px; gap: 10px; }
  }
  @media (max-width: 760px) {
    .search { padding: 0; border-color: transparent; background: none; }
    .search input { width: 0; padding: 0; }
  }
</style>
```

- [ ] **Step 5: Bygg**

Run: `npm run build:fixtures && npm run test:palette`
Expected: bygget går igenom (komponenterna används inte än) och palettvakten är grön.

- [ ] **Step 6: Commit**

```bash
git add src/components/ui/Icon.astro src/styles/species.css src/components/species/CategoryBar.astro src/components/species/SpeciesCard.astro
git commit -m "feat(website): kategoriraden, artkortet och delade stilar för artsidorna"
```

---

### Task 7: Ingångssidan

**Files:**
- Create: `website/tests/species.spec.ts`
- Create: `website/src/components/species/SpeciesHub.astro`
- Create: `website/src/pages/sv/arter/index.astro`, `website/src/pages/species/index.astro`

- [ ] **Step 1: Skriv testerna för ingångssidan**

`tests/species.spec.ts`:

```ts
import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Runs against the TEST data (tests/fixtures/): build with `npm run build:fixtures` first.
// 16 species are published there, in 7 groups; two woodpeckers are unpublished, one is failed and one pending.

async function noSideScroll(page: Page): Promise<void> {
  const [scroll, client] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(scroll).toBeLessThanOrEqual(client);
}

test.describe('ingångssidan', () => {
  for (const [path, h1, other, about] of [
    ['/sv/arter/', 'Europa', '/species/', '/sv/arter/om-artsidorna/'],
    ['/species/', 'Europe', '/sv/arter/', '/species/about-these-pages/'],
  ] as const) {
    test(`${path} visar grupper, jämförelser och hela listan`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toContainText(h1);
      await expect(page.locator('.groups a')).toHaveCount(7);
      await expect(page.locator('[data-item]')).toHaveCount(16);
      await expect(page.locator('[data-compare-link]')).toHaveCount(2);
      await expect(page.locator(`a[href="${about}"]`)).toHaveCount(1);
      await expect(page.locator(`link[rel="alternate"][hreflang="${path.startsWith('/sv') ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${other}`);
      expect(errors).toEqual([]);
    });
  }

  test('jämförelserna har namnen i svensk ordning', async ({ page }) => {
    await page.goto('/sv/arter/');
    await expect(page.locator('[data-compare-link]')).toHaveText(['Blåmes eller talgoxe', 'Kaja eller skata']);
  });

  test('opublicerade, väntande och misslyckade arter syns inte', async ({ page }) => {
    await page.goto('/sv/arter/');
    for (const name of ['Större hackspett', 'Tretåig hackspett', 'Gröngöling', 'Spillkråka']) {
      await expect(page.locator('[data-item]', { hasText: name })).toHaveCount(0);
    }
    await expect(page.locator('.catbar .chip', { hasText: 'Hackspettar' })).toHaveCount(0);
  });

  test('sökningen filtrerar och klarar å, ä och ö', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Talgoxe');
    await page.locator('#species-search').fill('blames');
    await expect(page.locator('[data-item]:visible').first()).toContainText('Blåmes');
    await page.locator('#species-search').fill('zzzz');
    await expect(page.locator('[data-no-results]')).toBeVisible();
  });

  test('?q= fyller i sökfältet', async ({ page }) => {
    await page.goto('/sv/arter/?q=talg');
    await expect(page.locator('#species-search')).toHaveValue('talg');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
  });

  test('390 px utan sidledsscroll', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/');
    await noSideScroll(page);
  });
});

test.describe('ingångssidan utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });
  test('hela listan syns', async ({ page }) => {
    await page.goto('/sv/arter/');
    const total = await page.locator('[data-item]').count();
    await expect(page.locator('[data-item]:visible')).toHaveCount(total);
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: FAIL, `/sv/arter/` ger 404.

- [ ] **Step 3: Skriv komponenten**

`src/components/species/SpeciesHub.astro`:

```astro
---
import { Image } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import JournalHeadline from '../ui/JournalHeadline.astro';
import CategoryBar from './CategoryBar.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  aboutHref, activeGroups, breadcrumbJsonLd, comparisonHref, comparisonPair, countLabel, getAllSpecies, getComparisons,
  groupHref, groupPhoto, groupSizes, hubHref, itemListJsonLd, pairNames, searchKey, sortByName, speciesHref,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const all = await getAllSpecies();
const sizes = groupSizes(all);
const groups = activeGroups(all);
const sorted = sortByName(all, locale);
const comparisons = (await getComparisons())
  .map((c) => {
    const names = pairNames(comparisonPair(c, locale, all), locale);
    return { href: comparisonHref(c, locale), label: t.species.compareItem.replace('{a}', names[0]).replace('{b}', names[1]) };
  })
  .sort((a, b) => a.label.localeCompare(b.label, locale));
const firstLetter = (name: string) => name.charAt(0).toLocaleUpperCase(locale);
const letters = [...new Set(sorted.map((s) => firstLetter(s.names[locale])))];
const pathname = hubHref(locale);
const n = all.length;
const title = t.species.titleHub.replace('{count}', countLabel(n, t));
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: pathname },
];
const jsonLd = [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, title.replace(/ \| Birdy$/, ''), locale, sorted)];
---

<Layout locale={locale} pathname={pathname} alternatePath={hubHref(other)} title={title} description={t.species.descHub.replace('{n}', String(n))} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={hubHref(other)} />
  <CategoryBar locale={locale} active="all" search={false} />
  <main class="hub wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[1].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <JournalHeadline text={t.species.hubHeadline} level="h1" align="left" size="clamp(38px, 5vw, 60px)" />
    <p class="lead">{t.species.hubLead.replace('{n}', String(n))}</p>
    <div class="hub-search">
      <label class="sr-only" for="species-search">{t.species.searchLabel}</label>
      <input id="species-search" name="q" type="search" placeholder={t.species.searchPlaceholder} autocomplete="off" />
    </div>

    <h2 class="sp-h2">{t.species.hubGroups}</h2>
    <ul class="groups" role="list">
      {groups.map((g) => {
        const photo = groupPhoto(g, all);
        return (
          <li>
            <a class="gcard" href={groupHref(g, locale)}>
              {photo && <Image src={photo} alt="" widths={[320, 480]} sizes="(max-width: 760px) 45vw, 200px" loading="lazy" decoding="async" />}
              <span class="gcard-name">{g.name[locale]}</span>
              <span class="gcard-n">{countLabel(sizes.get(g.key) ?? 0, t)}</span>
            </a>
          </li>
        );
      })}
    </ul>

    {comparisons.length > 0 && (
      <Fragment>
        <h2 class="sp-h2">{t.species.hubCompare}</h2>
        <ul class="compare-list" role="list">
          {comparisons.map((c) => <li><a href={c.href} data-compare-link>{c.label}</a></li>)}
        </ul>
      </Fragment>
    )}

    <h2 class="sp-h2" id="a-o">{t.species.hubAll}</h2>
    <p class="no-results" data-no-results hidden>{t.species.noResults}</p>
    {letters.map((letter) => (
      <section class="letter" data-letter>
        <h3>{letter}</h3>
        <ul role="list">
          {sorted.filter((s) => firstLetter(s.names[locale]) === letter).map((s) => (
            <li data-item data-search={searchKey(s)}>
              <a href={speciesHref(s, locale)}><span>{s.names[locale]}</span> <i>{s.names.scientific}</i></a>
            </li>
          ))}
        </ul>
      </section>
    ))}

    <p class="about-link"><a href={aboutHref(locale)}>{t.species.hubAbout}</a></p>
  </main>
  <Footer locale={locale} switchLangHref={hubHref(other)} />
</Layout>

<script>
  const input = document.querySelector<HTMLInputElement>('#species-search');
  const items = [...document.querySelectorAll<HTMLElement>('[data-search]')];
  const sections = [...document.querySelectorAll<HTMLElement>('[data-letter]')];
  const empty = document.querySelector<HTMLElement>('[data-no-results]');
  const norm = (s: string) => s.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
  const apply = () => {
    const q = norm(input?.value ?? '');
    let shown = 0;
    for (const item of items) {
      const hit = !q || (item.dataset.search ?? '').includes(q);
      item.hidden = !hit;
      if (hit) shown += 1;
    }
    for (const section of sections) section.hidden = !section.querySelector('[data-item]:not([hidden])');
    if (empty) empty.hidden = shown > 0;
  };
  const params = new URLSearchParams(location.search);
  if (input && params.has('q')) {
    input.value = params.get('q') ?? '';
    input.focus();
  }
  apply();
  input?.addEventListener('input', apply);
</script>

<style>
  .hub { padding-top: 26px; padding-bottom: 88px; }
  .hub-search { margin: 22px 0 0; max-width: 420px; }
  .hub-search input { width: 100%; box-sizing: border-box; font: inherit; font-size: 15px; padding: 12px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: var(--ink); }
  .hub-search input:focus-visible { outline: 3px solid var(--rust); outline-offset: 2px; }
  .groups { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
  .gcard { display: block; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper); }
  .gcard:hover { transform: translateY(-2px); }
  .gcard :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 8px; }
  .gcard-name { display: block; margin: 8px 4px 0; font-weight: 600; font-size: 14px; }
  .gcard-n { display: block; margin: 0 4px 4px; font-size: 12.5px; color: var(--muted); }
  .compare-list { list-style: none; margin: 0; padding: 0; columns: 2 260px; column-gap: 28px; }
  .compare-list li { break-inside: avoid; padding: 6px 0; border-bottom: 1px dotted var(--line); font-size: 15px; }
  .compare-list a:hover { color: var(--rust); }
  .letter { margin-top: 22px; }
  .letter h3 { font-size: 26px; color: var(--rust); margin: 0 0 8px; }
  .letter ul { list-style: none; margin: 0; padding: 0; columns: 3 220px; column-gap: 28px; }
  .letter li { break-inside: avoid; padding: 5px 0; border-bottom: 1px dotted var(--line); font-size: 14.5px; }
  .letter li[hidden], .letter[hidden] { display: none; }
  .letter a:hover span { color: var(--rust); }
  .letter i { font-family: var(--font-script); font-style: normal; font-size: 16px; color: var(--muted); margin-left: 4px; }
  .no-results { color: var(--muted); }
  .about-link { margin: 48px 0 0; font-weight: 600; }
  .about-link a { color: var(--rust); border-bottom: 1px solid currentColor; padding-bottom: 2px; }
  @media (max-width: 760px) {
    .groups { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  }
</style>
```

- [ ] **Step 4: Routes**

`src/pages/sv/arter/index.astro`:

```astro
---
import SpeciesHub from '../../../components/species/SpeciesHub.astro';
---
<SpeciesHub locale="sv" />
```

`src/pages/species/index.astro`:

```astro
---
import SpeciesHub from '../../components/species/SpeciesHub.astro';
---
<SpeciesHub locale="en" />
```

- [ ] **Step 5: Kör testerna igen**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (8 tester)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/SpeciesHub.astro src/pages/sv/arter/index.astro src/pages/species/index.astro tests/species.spec.ts
git commit -m "feat(website): ingångssidan för arterna med sökning och jämförelser"
```

---

### Task 8: Gruppsidorna

**Files:**
- Create: `website/src/components/species/GroupPage.astro`
- Create: `website/src/components/species/SpeciesRoute.astro`
- Create: `website/src/lib/species-routes.ts`
- Create: `website/src/pages/sv/arter/[slug].astro`, `website/src/pages/species/[slug].astro`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Skriv testerna**

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('gruppsidorna', () => {
  test('/sv/arter/ugglor/ har aktiv chip, arter och approta', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/ugglor/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Ugglor');
    await expect(page.locator('.catbar .chip[aria-current="page"]')).toContainText('Ugglor');
    await expect(page.locator('.catbar .chip')).toHaveCount(8);
    await expect(page.locator('[data-item]')).toHaveCount(3);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    await expect(page.locator('a[href*="utm_medium%3Dgroup"]')).toHaveCount(1);
    await expect(page.locator('.sp-app')).toContainText('hjälper dig känna igen fåglarna');
    expect(errors).toEqual([]);
  });

  test('Tättingar delas upp i familjer', async ({ page }) => {
    await page.goto('/sv/arter/tattingar/');
    await expect(page.locator('.family h3')).toHaveCount(6);
  });

  test('små grupper har noindex', async ({ page }) => {
    await page.goto('/sv/arter/havsfaglar/');
    await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex, follow');
  });

  test('en grupp utan byggda arter får ingen sida', async ({ page }) => {
    expect((await page.goto('/sv/arter/hackspettar/'))?.status()).toBe(404);
  });

  test('kategoriraden sveps i sidled på 390 px utan att sidan gör det', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/ugglor/');
    const [scroll, client] = await page.locator('[data-chips]').evaluate((el) => [el.scrollWidth, el.clientWidth]);
    expect(scroll).toBeGreaterThan(client);
    await noSideScroll(page);
  });

  test('engelska gruppsidan och språkbytet', async ({ page }) => {
    const res = await page.goto('/species/owls/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('link[rel="alternate"][hreflang="sv"]')).toHaveAttribute('href', 'https://birdy.community/sv/arter/ugglor/');
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g gruppsidorna`
Expected: FAIL (404 på `/sv/arter/ugglor/`)

- [ ] **Step 3: Skriv gruppsidan**

`src/components/species/GroupPage.astro`:

```astro
---
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import SpeciesCard from './SpeciesCard.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  appText, breadcrumbJsonLd, countLabel, getAllSpecies, groupHref, groupTitle, hubHref, isGroupIndexed,
  itemListJsonLd, playHref, sortByName, type Group, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { group: Group; locale: Locale }
const { group, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const all = await getAllSpecies();
const members = sortByName(all.filter((s) => s.group === group.key), locale);
const pathname = groupHref(group, locale);
const title = groupTitle(group, members.length, locale, t);
const description = t.species.descGroup.replace('{group}', group.name[locale]).replace('{count}', countLabel(members.length, t));
const familyName = (s: Species) => (locale === 'sv' ? s.family.sv : s.family.latin);
const byFamily = group.key === 'songbirds'
  ? [...new Set(members.map(familyName))].sort((a, b) => a.localeCompare(b, locale)).map((name) => ({ name, items: members.filter((s) => familyName(s) === name) }))
  : [];
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: group.name[locale], href: pathname },
];
const jsonLd = [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, group.name[locale], locale, byFamily.length ? byFamily.flatMap((f) => f.items) : members)];
---

<Layout locale={locale} pathname={pathname} alternatePath={groupHref(group, other)} title={title} description={description} noindex={!isGroupIndexed(group, all)} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={groupHref(group, other)} />
  <CategoryBar locale={locale} active={group.key} />
  <main class="group wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><a href={crumbs[1].href} data-crumb>{crumbs[1].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[2].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <h1>{group.name[locale]}</h1>
    <p class="lead">{group.intro[locale]}</p>

    <h2 class="sp-h2">{t.species.groupSpecies} <span class="count">{countLabel(members.length, t)}</span></h2>
    {byFamily.length > 0 ? (
      byFamily.map((f) => (
        <section class="family">
          <h3>{f.name}</h3>
          <ul class="sp-cards" role="list">
            {f.items.map((s) => <li data-item><SpeciesCard species={s} locale={locale} /></li>)}
          </ul>
        </section>
      ))
    ) : (
      <ul class="sp-cards" role="list">
        {members.map((s) => <li data-item><SpeciesCard species={s} locale={locale} /></li>)}
      </ul>
    )}

    <aside class="sp-app group-app">
      <p class="sp-app-h">{t.species.appHeadline}</p>
      <p>{appText(undefined, t)}</p>
      <PlayStoreBadge locale={locale} href={playHref(group.slug[locale], 'group')} alt={t.alt.playStoreBadge} size="small" />
    </aside>
  </main>
  <Footer locale={locale} switchLangHref={groupHref(group, other)} />
</Layout>

<style>
  .group { padding-top: 26px; padding-bottom: 88px; }
  h1 { font-size: clamp(38px, 5vw, 60px); line-height: 1.04; }
  .count { font-family: var(--font-sans); font-size: 14px; color: var(--muted); margin-left: 8px; }
  .family h3 { font-size: 22px; margin: 30px 0 12px; }
  .group-app { margin-top: 48px; max-width: 520px; }
</style>
```

- [ ] **Step 4: Routen (grupper nu, arter i Task 10 och jämförelser i Task 11)**

`src/lib/species-routes.ts`:

```ts
import type { Locale } from './i18n';
import { ABOUT_SLUG, activeGroups, assertUniqueSlugs, getAllSpecies } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  const groups = activeGroups(all);
  assertUniqueSlugs([...groups.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  return groups.map((group) => ({ params: { slug: group.slug[locale] }, props: { group } }));
}
```

`src/components/species/SpeciesRoute.astro`:

```astro
---
import GroupPage from './GroupPage.astro';
import type { Locale } from '../../lib/i18n';
import type { Group } from '../../lib/species';

interface Props { locale: Locale; group?: Group }
const { locale, group } = Astro.props;
---
{group && <GroupPage group={group} locale={locale} />}
```

`src/pages/sv/arter/[slug].astro`:

```astro
---
import SpeciesRoute from '../../../components/species/SpeciesRoute.astro';
import { speciesPaths } from '../../../lib/species-routes';

export async function getStaticPaths() {
  return speciesPaths('sv');
}
---
<SpeciesRoute {...Astro.props} locale="sv" />
```

`src/pages/species/[slug].astro`:

```astro
---
import SpeciesRoute from '../../components/species/SpeciesRoute.astro';
import { speciesPaths } from '../../lib/species-routes';

export async function getStaticPaths() {
  return speciesPaths('en');
}
---
<SpeciesRoute {...Astro.props} locale="en" />
```

- [ ] **Step 5: Kör testerna igen**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (14 tester)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/GroupPage.astro src/components/species/SpeciesRoute.astro src/lib/species-routes.ts "src/pages/sv/arter/[slug].astro" "src/pages/species/[slug].astro" tests/species.spec.ts
git commit -m "feat(website): gruppsidorna med familjer, noindex för små grupper och approta"
```

---

### Task 9: Diagrammet, kartan, spelaren och creditblocket

**Tillägg (2026-10-06, fas 1b:s slutgranskning Minor 11; ordvalet godkänt av Albin 2026-10-06, gäller):** varje inspelning är bearbetad, inte bara de klippta: pipelinen gör om den till mono, ljudnivånormaliserar och kodar om den till MP3 (`convert_to_mp3`). CC BY och CC BY-SA kräver att creditraden säger att verket är ändrat, så raden ska alltid säga det. Förslag: nycklarna `trimmed` blir `edited` ("bearbetad" / "edited") och `trimmedEdited` ("klippt och bearbetad" / "trimmed and edited"), och spelarens bildtext slutar med `, ${audio.trimmed ? t.species.trimmedEdited : t.species.edited}` i stället för `{audio.trimmed && ...}`; Playwright-kontrollen nedan letar efter "bearbetad" i stället för "klippt". Specen är ändrad på samma sätt (avsnitt 2 punkt 5, avsnitt 8 om licenserna, avsnitt 10 punkt 3 och bilaga A:s två rader för inspelningscredit). Licensstycket på "Så gör vi artsidorna" (copy-nycklarna i Task 5) får samma mening som avsnitt 8: inspelningarna är bearbetade (högst 20 sekunder, mono, utjämnad ljudnivå, MP3), och en bearbetad CC BY-SA-inspelning delas under samma licens. Pipelinen avvisar sedan 2026-10-06 dessutom en CC BY- eller CC BY-SA-inspelning utan upphovsperson, så "okänd inspelare" förekommer bara för CC0 och public domain.

**Files:**
- Create: `website/src/components/species/MonthChart.astro`
- Create: `website/src/components/species/CountyMap.astro`
- Create: `website/src/components/species/AudioPlayer.astro`
- Create: `website/src/components/species/Credits.astro`

Komponenterna testas genom artsidan i Task 10 och jämförelsesidan i Task 11.

- [ ] **Step 1: Månadsdiagrammet**

`src/components/species/MonthChart.astro`:

```astro
---
// Bars per month as SVG, drawn when the site is built (spec 2026-09-25 §5 and §7). One series on species
// pages, two on comparison pages. Values are the species' share of all bird reports, scaled so the top
// month is 100. The text alternative is the paragraph that `describedBy` points at.
interface Series { label: string; values: number[]; tone: 'rust' | 'navy' }
interface Props { id: string; title: string; letters: string[]; series: Series[]; describedBy: string }
const { id, title, letters, series, describedBy } = Astro.props;
const W = 360;
const TOP = 8;
const H = 112;
const BASE = TOP + H;
const SLOT = W / 12;
const barW = series.length === 1 ? 18 : 11;
const offset = (i: number) => (series.length === 1 ? (SLOT - barW) / 2 : (SLOT - 2 * barW - 2) / 2 + i * (barW + 2));
const heightOf = (v: number) => (v > 0 ? Math.max(1.5, (v / 100) * H) : 0);
---

<svg class="mchart" viewBox={`0 0 ${W} ${BASE + 22}`} role="img" aria-labelledby={`${id}-t`} aria-describedby={describedBy} data-chart={id}>
  <title id={`${id}-t`}>{title}</title>
  <line x1="0" x2={W} y1={BASE} y2={BASE} class="axis" />
  {letters.map((letter, m) => (
    <g>
      {series.map((s, i) => {
        const h = heightOf(s.values[m] ?? 0);
        return <rect data-month={m + 1} data-series={i} class={`bar bar--${s.tone}`} x={(m * SLOT + offset(i)).toFixed(1)} y={(BASE - h).toFixed(1)} width={barW} height={h.toFixed(1)} rx="2" />;
      })}
      <text x={(m * SLOT + SLOT / 2).toFixed(1)} y={BASE + 16} class="month">{letter}</text>
    </g>
  ))}
</svg>

<style>
  .mchart { display: block; width: 100%; height: auto; }
  .axis { stroke: var(--line); stroke-width: 1; }
  .bar--rust { fill: var(--rust); }
  .bar--navy { fill: var(--navy); }
  .month { font-family: var(--font-sans); font-size: 11px; fill: var(--muted); text-anchor: middle; }
</style>
```

- [ ] **Step 2: Länskartan**

`src/components/species/CountyMap.astro`:

```astro
---
// Sweden's 21 counties shaded by the species' share of all bird reports (spec 2026-09-25 §5 and §9.2).
// Geometry: src/data/sweden-counties.json (npm run assets:counties). Four shades: no reports, 1 to 33,
// 34 to 66 and 67 to 100 per cent of the highest county.
import counties from '../../data/sweden-counties.json';
import type { Locale } from '../../lib/i18n';

interface Props { id: string; values: Record<string, number>; locale: Locale; title: string; legend: string[]; describedBy: string }
const { id, values, locale, title, legend, describedBy } = Astro.props;
const bucket = (v: number | undefined) => (!v ? 0 : v <= 33 ? 1 : v <= 66 ? 2 : 3);
---

<div class="cmap" data-map={id}>
  <svg viewBox={counties.viewBox} role="img" aria-labelledby={`${id}-t`} aria-describedby={describedBy}>
    <title id={`${id}-t`}>{title}</title>
    {counties.counties.map((c) => (
      <path d={c.d} class={`b${bucket(values[c.code])}`} data-county={c.code}><title>{locale === 'sv' ? c.sv : c.en}</title></path>
    ))}
  </svg>
  <ul class="legend" role="list">
    {legend.map((label, i) => <li><span class={`sw b${i}`} aria-hidden="true"></span>{label}</li>)}
  </ul>
</div>

<style>
  .cmap svg { display: block; width: 100%; height: auto; max-height: 420px; }
  path { stroke: var(--muted); stroke-width: .6; stroke-linejoin: round; }
  .b0 { fill: var(--card); }
  .b1 { fill: var(--peach); }
  .b2 { fill: var(--apricot); }
  .b3 { fill: var(--rust); }
  .legend { list-style: none; margin: 10px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 12px; font-size: 12px; color: var(--muted); }
  .legend li { display: inline-flex; align-items: center; gap: 6px; }
  .sw { width: 12px; height: 12px; border-radius: 3px; border: 1px solid var(--muted); }
  .sw.b0 { background: var(--card); }
  .sw.b1 { background: var(--peach); }
  .sw.b2 { background: var(--apricot); }
  .sw.b3 { background: var(--rust); }
</style>
```

- [ ] **Step 3: Spelaren**

`src/components/species/AudioPlayer.astro`:

```astro
---
// One recording with its credit line right under it (spec 2026-09-25 §5; deviation 8 in the plan).
// preload="none": nothing is downloaded until the visitor presses play.
import { getCopy, type Locale } from '../../lib/i18n';
import { audioHref, type Species } from '../../lib/species';

interface Props { species: Species; locale: Locale; creditKey: string }
const { species: s, locale, creditKey } = Astro.props;
const t = getCopy(locale);
const audio = s.audio;
const src = audioHref(s);
---

{audio && src && (
  <figure class="player" data-audio={creditKey}>
    <audio controls preload="none" src={src} aria-label={t.species.recordingLabel.replace('{name}', s.names[locale])}></audio>
    <figcaption data-credit-for={creditKey}>
      {t.species.recordingCredit} {audio.author ?? t.species.unknownRecordist},{' '}
      {audio.licenseUrl ? <a href={audio.licenseUrl} rel="license noopener">{audio.license}</a> : audio.license},{' '}
      {t.species.via} <a href={audio.sourceUrl} rel="noopener">Wikimedia Commons</a>{audio.trimmed && `, ${t.species.trimmed}`}
    </figcaption>
  </figure>
)}

<style>
  .player { margin: 12px 0 0; max-width: 40rem; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 10px 12px 8px; }
  .player audio { display: block; width: 100%; }
  .player figcaption { margin-top: 6px; font-size: 12px; line-height: 1.45; color: var(--muted); }
  .player a { color: var(--muted); text-decoration: underline; text-underline-offset: 2px; }
  .player a:hover { color: var(--rust); }
</style>
```

- [ ] **Step 4: Creditblocket**

`src/components/species/Credits.astro`:

```astro
---
// Credits for every photo, the Wikipedia articles, the data sources, the verification line
// (spec Revision 2026-10-05: "Kontrollerad mot källorna {date}", no name) and the report link
// (spec 2026-09-25 §5, §7 and §10). scripts/check-seo.mjs fails the build when one is missing.
import { getCopy, type Locale } from '../../lib/i18n';
import { CONTACT_EMAIL } from '../../lib/links';
import { aboutHref, formatDate, wikiUrl, type SpeciesImage, type WikiLang, type WikiRef } from '../../lib/species';

interface Props {
  locale: Locale;
  photos: { key: string; image: SpeciesImage }[];
  /** `name` tells two species' articles apart on comparison pages. */
  articles: { lang: WikiLang; ref: WikiRef; name?: string }[];
  reportData: boolean;
  redList: boolean;
  /** YYYY-MM-DD */
  reviewedAt: string;
  reportSubject: string;
}
const { locale, photos, articles, reportData, redList, reviewedAt, reportSubject } = Astro.props;
const t = getCopy(locale);
const labels: Record<WikiLang, string> = { sv: t.species.articleSv, en: t.species.articleEn, de: t.species.articleDe };
const [gbifBefore, gbifAfter] = t.species.dataCreditReports.split('{gbif}');
const [reviewedBefore, reviewedAfter] = t.species.reviewed.split('{date}');
---

<div class="credits">
  {photos.map(({ key, image }) => (
    <p data-credit-for={key}>
      {t.species.photoCredit} {image.author ?? t.species.unknownAuthor},{' '}
      {image.licenseUrl ? <a href={image.licenseUrl} rel="license noopener">{image.license}</a> : image.license},{' '}
      {t.species.via} <a href={image.sourceUrl} rel="noopener">Wikimedia Commons</a>
    </p>
  ))}
  <p data-wiki-credit>
    {t.species.textCredit} <a href="https://creativecommons.org/licenses/by-sa/4.0/" rel="license noopener">CC BY-SA 4.0</a>.
    {' '}{t.species.sources}:{' '}
    {articles.map((a, i) => (
      <Fragment>{i > 0 && ', '}<a href={wikiUrl(a.lang, a.ref)} rel="noopener" data-wiki={a.lang}>{a.name ? `${labels[a.lang]} (${a.name})` : labels[a.lang]}</a></Fragment>
    ))}
  </p>
  {(reportData || redList) && (
    <p data-data-credit>
      {reportData && <Fragment>{gbifBefore}<a href="https://www.gbif.org/" rel="noopener">GBIF.org</a>{gbifAfter}</Fragment>}
      {reportData && redList && ' '}
      {redList && t.species.dataCreditRedList}
    </p>
  )}
  <p class="reviewed">
    <span data-reviewed-by>{reviewedBefore}<time datetime={reviewedAt} data-reviewed>{formatDate(reviewedAt, locale)}</time>{reviewedAfter}</span>
    {' '}<a href={aboutHref(locale)}>{t.species.aboutLink}</a>
  </p>
  <p class="report"><a href={`mailto:${CONTACT_EMAIL}?subject=${encodeURIComponent(reportSubject)}`}>{t.species.reportError}</a></p>
</div>

<style>
  .credits { margin-top: 40px; padding-top: 14px; border-top: 1px solid var(--line); font-size: 12.5px; line-height: 1.6; color: var(--muted); }
  .credits p { margin: 0 0 6px; }
  .credits a { color: var(--muted); text-decoration: underline; text-underline-offset: 2px; }
  .credits a:hover { color: var(--rust); }
  .reviewed { margin-top: 12px !important; color: var(--ink); }
  .reviewed a { color: var(--rust); }
  .report { margin-top: 12px !important; font-weight: 600; }
  .report a { color: var(--rust); }
</style>
```

- [ ] **Step 5: Bygg och kontrollera paletten**

Run: `npm run build:fixtures && npm run test:palette && npx astro check`
Expected: bygget går igenom, palettvakten grön, inga nya typfel.

- [ ] **Step 6: Commit**

```bash
git add src/components/species/MonthChart.astro src/components/species/CountyMap.astro src/components/species/AudioPlayer.astro src/components/species/Credits.astro
git commit -m "feat(website): månadsdiagram, länskarta, spelare och creditblock för artsidorna"
```

---

### Task 10: Artsidan (layout B med de nya modulerna)

**Files:**
- Create: `website/src/components/species/SpeciesArticle.astro`
- Modify: `website/src/lib/species-routes.ts`, `website/src/components/species/SpeciesRoute.astro`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Skriv testerna**

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('artsidan', () => {
  test('talgoxe: rubrik, fakta, moduler, förväxlingsart, credits och språkbyte', async ({ page, request }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/talgoxe/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Talgoxe');
    await expect(page.locator('.latin')).toHaveText('Parus major');

    const facts = page.locator('.facts');
    for (const text of ['Vetenskapligt namn', 'Mesar', 'Stannfågel', 'Cirka 14 cm', 'Livskraftig (LC)']) await expect(facts).toContainText(text);
    await expect(facts.locator('[data-redlist]')).toContainText('Inte rödlistad');
    await expect(page.locator('.sp-app')).toContainText('på foto eller läte');
    await expect(page.locator('h2')).toContainText(['Så känner du igen den', 'Läte', 'Var och när', 'Föda och beteende', 'Kan förväxlas med', 'Fler tättingar']);

    const audio = page.locator('audio');
    await expect(audio).toHaveAttribute('preload', 'none');
    const src = await audio.getAttribute('src');
    expect(src).toMatch(/^\/audio\/species\/Q25485\.[0-9a-f]{10}\.mp3$/);
    expect((await request.get(src!)).status()).toBe(200);
    await expect(page.locator('[data-credit-for="audio"]')).toContainText('klippt');

    await expect(page.locator('[data-chart] rect[data-month]')).toHaveCount(12);
    await expect(page.locator('[data-map] path[data-county]')).toHaveCount(21);
    await expect(page.locator('.data-summary')).toHaveText('Rapporteras året runt. Vanligast i rapporterna från Testlän, Provlän och Exempellän.');
    await expect(page.locator('[data-data-credit]')).toContainText('Artportalen');

    const looks = page.locator('.looks li');
    await expect(looks).toHaveCount(1);
    await expect(looks.locator('.look-name a')).toHaveAttribute('href', '/sv/arter/blames/');
    await expect(looks.locator('.look-compare')).toHaveAttribute('href', '/sv/arter/blames-eller-talgoxe/');
    await expect(looks.locator('.look-compare')).toHaveText('Jämför blåmes och talgoxe');

    const keys = await page.locator('[data-photo], [data-audio]').evaluateAll((els) => els.map((e) => e.getAttribute('data-photo') ?? e.getAttribute('data-audio')));
    expect(keys).toEqual(['hero', 'audio', 'extra']);
    for (const key of keys) await expect(page.locator(`[data-credit-for="${key}"]`)).toHaveCount(1);
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(3);
    await expect(page.locator('[data-wiki-credit]')).toContainText('CC BY-SA 4.0');
    await expect(page.locator('[data-reviewed-by]')).toContainText('Kontrollerad mot källorna 20 november 2026');
    await expect(page.locator('time[data-reviewed]')).toHaveAttribute('datetime', '2026-11-20');
    await expect(page.locator('.credits a[href="/sv/arter/om-artsidorna/"]')).toHaveCount(1);
    await expect(page.locator('a[href*="utm_campaign%3Dtalgoxe"]')).toHaveCount(1);
    await expect(page.locator('#site-nav .links a[lang="en"]')).toHaveAttribute('href', '/species/great-tit/');
    await expect(page.locator('.catbar .chip[aria-current="page"]')).toContainText('Tättingar');
    await expect(page.locator('[data-preview-banner]')).toHaveCount(0);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('engelska sidan: kontrollraden och jämförelselänken', async ({ page }) => {
    await page.goto('/species/great-tit/');
    await expect(page.locator('h1')).toHaveText('Great Tit');
    await expect(page.locator('[data-reviewed-by]')).toContainText('Checked against sources on 20 November 2026.');
    await expect(page.locator('.look-compare')).toHaveText('Compare the Eurasian Blue Tit and the Great Tit');
    await expect(page.locator('.sp-app')).toContainText('from a photo or its song');
  });

  test('pärluggla: utan inspelning, data, extrafoto, föda och förväxlingsarter', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/arter/parluggla/');
    await expect(page.locator('audio')).toHaveCount(0);
    await expect(page.locator('[data-chart], [data-map]')).toHaveCount(0);
    await expect(page.locator('[data-data-credit]')).toHaveCount(0);
    await expect(page.locator('[data-photo]')).toHaveCount(1);
    await expect(page.locator('h2', { hasText: 'Föda och beteende' })).toHaveCount(0);
    await expect(page.locator('h2', { hasText: 'Kan förväxlas med' })).toHaveCount(0);
    for (const label of ['I Sverige', 'Storlek', 'Svenska rödlistan 2025']) await expect(page.locator('.facts')).not.toContainText(label);
    await expect(page.locator('.sp-app')).toContainText('hjälper dig känna igen fåglarna');
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(1);
    expect(errors).toEqual([]);
  });

  for (const [path, text] of [['/sv/arter/kaja/', 'på lätet,'], ['/sv/arter/trana/', 'på foto,'], ['/species/western-jackdaw/', 'from its song']] as const) {
    test(`approtan på ${path} säger bara vad appen klarar`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('.sp-app')).toContainText(text);
    });
  }

  test('global rödlista döljs för NE, svensk rödlista visas med kod', async ({ page }) => {
    await page.goto('/sv/arter/kaja/');
    await expect(page.locator('.facts')).not.toContainText('Global rödlista');
    await page.goto('/sv/arter/fiskmas/');
    await expect(page.locator('.facts [data-redlist]')).toContainText('Nära hotad (NT)');
  });

  test('förväxlingsarter utan egen sida får vetenskapligt namn utan länk', async ({ page }) => {
    await page.goto('/sv/arter/kattuggla/');
    await expect(page.locator('.looks .look-name')).toHaveText('Strix uralensis');
    await expect(page.locator('.looks .look-name a')).toHaveCount(0);
    await page.goto('/sv/arter/hornuggla/');
    await expect(page.locator('.looks .look-name')).toHaveText('Asio flammeus');
  });

  test('opublicerade, väntande och misslyckade arter ger 404', async ({ page }) => {
    for (const path of ['/sv/arter/storre-hackspett/', '/sv/arter/grongoling/', '/sv/arter/spillkraka/', '/species/black-woodpecker/']) {
      expect((await page.goto(path))?.status(), path).toBe(404);
    }
  });

  test('vänsterspalten följer med på dator', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/arter/talgoxe/');
    await page.evaluate(() => window.scrollTo({ top: 900, behavior: 'instant' }));
    const box = (await page.locator('.plate.hero').boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(76);
    expect(box.y).toBeLessThan(260);
  });

  test('mobilen: rubrik, foto, fakta, ingress och approta i den ordningen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/talgoxe/');
    const y = async (sel: string) => (await page.locator(sel).first().boundingBox())!.y;
    const order = [await y('h1'), await y('.plate.hero'), await y('.facts'), await y('.sp-lead'), await y('.looks-sec'), await y('.sp-app')];
    for (let i = 1; i < order.length; i += 1) expect(order[i]).toBeGreaterThan(order[i - 1]);
  });

  for (const width of [360, 390, 430]) {
    test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/arter/talgoxe/', '/species/great-tit/', '/sv/arter/parluggla/']) {
        await page.goto(path);
        await noSideScroll(page);
      }
    });
  }
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g artsidan`
Expected: FAIL (404 på `/sv/arter/talgoxe/`)

- [ ] **Step 3: Artsidan**

`src/components/species/SpeciesArticle.astro`:

```astro
---
import { Image, getImage } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import SpeciesCard from './SpeciesCard.astro';
import Credits from './Credits.astro';
import AudioPlayer from './AudioPlayer.astro';
import MonthChart from './MonthChart.astro';
import CountyMap from './CountyMap.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  SITE, appText, audioJsonLd, breadcrumbJsonLd, comparisonHref, comparisonPair, getAllRecords, getAllSpecies, getComparisons,
  groupByKey, groupHref, heroOf, hubHref, isUnpublished, joinSentences, lookAlikeView, pairNames, playHref, related,
  reviewDate, speciesHref, speciesImage, speciesTitle, taxonJsonLd, wikiSources,
  type Comparison, type LookAlikeView, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { species: Species; locale: Locale }
const { species: s, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const text = s.text![locale];
const [all, records, comps] = await Promise.all([getAllSpecies(), getAllRecords(), getComparisons()]);
const group = groupByKey(s.group);
const name = s.names[locale];
const pathname = speciesHref(s, locale);
const otherPath = speciesHref(s, other);
const title = speciesTitle(s, locale, t);
const unpublished = isUnpublished(s);

const hero = heroOf(s);
const extra = s.images.find((i) => i.role === 'extra');
const heroImg = speciesImage(hero.file);
const extraImg = extra ? speciesImage(extra.file) : undefined;
const photos = [{ key: 'hero', image: hero }, ...(extra ? [{ key: 'extra', image: extra }] : [])];
const altHero = t.species.altHero.replace('{name}', name).replace('{scientific}', s.names.scientific);
const share = await getImage({ src: heroImg, width: 1200, height: 630, fit: 'cover', format: 'jpg', quality: 82 });
const content = await getImage({ src: heroImg, width: 1200, format: 'webp' });

const status = text.facts.swedenStatus ? t.species.statusLabels[text.facts.swedenStatus.value] : undefined;
const redCode = s.swedishRedList;
const redLabel = redCode
  ? redCode === 'not_listed' ? t.species.redListLabels.not_listed : `${t.species.redListLabels[redCode]} (${redCode})`
  : undefined;
const iucnLabel = s.iucn !== 'NE' ? (t.species.iucnLabels as Record<string, string>)[s.iucn] : undefined;
const familyShown = locale === 'sv' ? s.family.sv : s.family.latin;
const rel = related(s, all, locale);
const moreHeading = rel.kind === 'family'
  ? t.species.moreFamily.replace('{family}', locale === 'sv' ? s.family.sv.toLocaleLowerCase('sv') : s.family.latin)
  : t.species.moreGroup.replace('{group}', group.name[locale].toLocaleLowerCase(locale));
const marginalia = s.marginalia?.[locale];

const months = s.data?.months;
const counties = s.data?.counties;
const summaryId = `data-summary-${s.qid}`;
const behaviour = joinSentences(text.behaviour);
const looks = text.lookAlikes.flatMap((item) => {
  const view = lookAlikeView(s, item, locale, all, records, comps);
  return view ? [view] : [];
});
const compareText = (c: Comparison): string => {
  const [na, nb] = pairNames(comparisonPair(c, locale, all), locale);
  return t.species.compareLink.replace('{a}', locale === 'sv' ? na.toLocaleLowerCase('sv') : na).replace('{b}', nb);
};
const lookPhoto = (view: LookAlikeView) => (view.species ? speciesImage(heroOf(view.species).file) : undefined);
const reviewed = reviewDate(s);
const audioNode = audioJsonLd(s);

const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: group.name[locale], href: groupHref(group, locale) },
  { name, href: pathname },
];
const jsonLd = [
  breadcrumbJsonLd(crumbs),
  {
    '@type': 'WebPage',
    url: new URL(pathname, SITE).toString(),
    name: title.replace(/ \| Birdy$/, ''),
    inLanguage: locale,
    about: taxonJsonLd(s),
    primaryImageOfPage: {
      '@type': 'ImageObject',
      contentUrl: new URL(content.src, SITE).toString(),
      ...(hero.licenseUrl ? { license: hero.licenseUrl } : {}),
      acquireLicensePage: hero.sourceUrl,
      ...(hero.author ? { creator: { '@type': 'Person', name: hero.author }, creditText: hero.author } : {}),
    },
    ...(audioNode ? { associatedMedia: audioNode } : {}),
    lastReviewed: reviewed,
  },
];
---

<Layout locale={locale} pathname={pathname} alternatePath={otherPath} title={title} description={text.metaDescription} ogImage={share.src} ogImageAlt={altHero} noindex={unpublished} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={otherPath} />
  <CategoryBar locale={locale} active={s.group} />
  <main class="sp wrap" data-species-page>
    {unpublished && <p class="sp-preview" data-preview-banner role="note">{t.species.previewBanner}</p>}
    <div class="spread">
      <header class="head">
        <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
          <ol>
            {crumbs.slice(0, -1).map((c) => <li><a href={c.href} data-crumb>{c.name}</a></li>)}
            <li><span aria-current="page" data-crumb>{name}</span></li>
          </ol>
        </nav>
        <Kicker text={familyShown} />
        <h1>{name}</h1>
        <p class="latin">{s.names.scientific}</p>
      </header>

      <div class="left">
        <div class="left-inner">
          <figure class="plate hero" data-photo="hero">
            <Image src={heroImg} alt={altHero} widths={[480, 800, 1200]} sizes="(max-width: 1023px) 100vw, 440px" loading="eager" fetchpriority="high" decoding="async" />
            <figcaption><span>{t.species.plate.replace('{n}', '1')}, {name}</span><span>{t.species.photoCredit} {hero.author ?? t.species.unknownAuthor}</span></figcaption>
          </figure>
          <dl class="facts">
            <div><dt>{t.species.facts.scientific}</dt><dd><i>{s.names.scientific}</i></dd></div>
            <div><dt>{t.species.facts.family}</dt><dd>{familyShown}</dd></div>
            {status && <div><dt>{t.species.facts.sweden}</dt><dd>{status}</dd></div>}
            {text.facts.size && <div><dt>{t.species.facts.size}</dt><dd>{text.facts.size.value}</dd></div>}
            {redLabel && <div data-redlist><dt>{t.species.facts.swedishRedList}</dt><dd>{redLabel}</dd></div>}
            {iucnLabel && <div><dt>{t.species.facts.iucn}</dt><dd>{iucnLabel} ({s.iucn})</dd></div>}
          </dl>
          <aside class="sp-app">
            <p class="sp-app-h">{t.species.appHeadline}</p>
            <p>{appText(s, t)}</p>
            <PlayStoreBadge locale={locale} href={playHref(s.slug[locale], 'species')} alt={t.alt.playStoreBadge} size="small" />
          </aside>
          {marginalia && <p class="note">{marginalia}</p>}
        </div>
      </div>

      <div class="body">
        <p class="sp-lead">{joinSentences(text.lead)}</p>
        <div class="texts">
          <h2>{t.species.headMarks}</h2>
          <ul class="marks">{text.fieldMarks.map((m) => <li>{m.text}</li>)}</ul>
          <h2>{t.species.headVoice}</h2>
          <p>{joinSentences(text.voice)}</p>
          <AudioPlayer species={s} locale={locale} creditKey="audio" />
          <h2>{t.species.headWhere}</h2>
          <p>{joinSentences(text.whereWhen)}</p>
          {(months || counties) && (
            <div class="datafig">
              {months && (
                <div class="fig-chart">
                  <h3 class="fig-h">{t.species.chartTitle}</h3>
                  <figure>
                    <MonthChart id={`chart-${s.qid}`} title={t.species.chartTitle} letters={t.species.monthLetters} series={[{ label: name, values: months, tone: 'rust' }]} describedBy={summaryId} />
                    <figcaption class="fig-caption">{t.species.chartCaption}</figcaption>
                  </figure>
                </div>
              )}
              {counties && (
                <div class="fig-map">
                  <h3 class="fig-h">{t.species.mapTitle}</h3>
                  <figure>
                    <CountyMap id={`map-${s.qid}`} values={counties} locale={locale} title={t.species.mapTitle} legend={t.species.mapLegend} describedBy={summaryId} />
                    <figcaption class="fig-caption">{t.species.mapCaption}</figcaption>
                  </figure>
                </div>
              )}
              <p class="data-summary" id={summaryId}>{(s.data?.sentences[locale] ?? []).join(' ')}</p>
            </div>
          )}
          {behaviour && (
            <Fragment>
              <h2>{t.species.headBehaviour}</h2>
              <p>{behaviour}</p>
            </Fragment>
          )}
        </div>
        {extra && extraImg && (
          <figure class="plate extra" data-photo="extra">
            <Image src={extraImg} alt={t.species.altExtra.replace('{name}', name)} widths={[480, 800, 1200]} sizes="(max-width: 1023px) 100vw, 640px" loading="lazy" decoding="async" />
            <figcaption><span>{t.species.plate.replace('{n}', '2')}</span><span>{t.species.photoCredit} {extra.author ?? t.species.unknownAuthor}</span></figcaption>
          </figure>
        )}
        {looks.length > 0 && (
          <section class="looks-sec">
            <h2>{t.species.headLookAlikes}</h2>
            <ul class="looks" role="list">
              {looks.map((view) => {
                const photo = lookPhoto(view);
                return (
                  <li class:list={[{ 'no-photo': !photo }]}>
                    {photo && view.species && (
                      <a href={speciesHref(view.species, locale)} tabindex="-1" aria-hidden="true">
                        <Image src={photo} alt="" widths={[192]} sizes="96px" loading="lazy" decoding="async" />
                      </a>
                    )}
                    <div>
                      <p class="look-name">
                        {view.species ? <a href={speciesHref(view.species, locale)}>{view.name}</a> : view.scientificOnly ? <i>{view.name}</i> : view.name}
                      </p>
                      <p class="look-text">{view.text}</p>
                      {view.comparison && <a class="look-compare" href={comparisonHref(view.comparison, locale)}>{compareText(view.comparison)}</a>}
                    </div>
                  </li>
                );
              })}
            </ul>
          </section>
        )}
        {rel.items.length > 0 && (
          <section class="more">
            <h2>{moreHeading}</h2>
            <ul class="sp-cards" role="list">
              {rel.items.map((x) => <li><SpeciesCard species={x} locale={locale} /></li>)}
            </ul>
          </section>
        )}
        <div class="credits-wrap">
          <Credits
            locale={locale}
            photos={photos}
            articles={wikiSources(s)}
            reportData={Boolean(months || counties)}
            redList={Boolean(redCode)}
            reviewedAt={reviewed}
            reportSubject={t.species.reportSubject.replace('{name}', name)}
          />
        </div>
      </div>
    </div>
  </main>
  <Footer locale={locale} switchLangHref={otherPath} />
</Layout>

<style>
  .sp { padding-top: 26px; padding-bottom: 88px; }
  .spread { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); grid-template-rows: auto 1fr; grid-template-areas: 'left head' 'left body'; }
  .head { grid-area: head; padding-left: 36px; }
  .left { grid-area: left; padding-right: 28px; border-right: 1px dashed var(--line); }
  .left-inner { position: sticky; top: 150px; display: flex; flex-direction: column; gap: 18px; }
  .body { grid-area: body; padding-left: 36px; }
  h1 { font-size: clamp(40px, 4.6vw, 58px); line-height: 1.02; margin: 4px 0 0; overflow-wrap: anywhere; }
  .latin { margin: 2px 0 0; font-family: var(--font-script); font-size: 24px; color: var(--muted); }
  .note { margin: 4px 0 0; font-family: var(--font-script); font-size: 22px; line-height: 1.2; color: var(--rust); transform: rotate(-2deg); }
  .sp-lead { font-size: 17px; line-height: 1.6; margin: 18px 0 0; max-width: 40rem; }
  .texts h2, .looks-sec h2, .more h2 { font-size: 24px; margin: 30px 0 8px; }
  .texts > p, .marks { font-size: 15.5px; line-height: 1.65; max-width: 40rem; margin: 0; }
  .marks { padding-left: 20px; }
  .marks li { margin: 3px 0; }
  .datafig { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); gap: 18px 28px; align-items: start; margin-top: 16px; max-width: 40rem; }
  .datafig figure { margin: 0; }
  .datafig .data-summary { grid-column: 1 / -1; }
  .fig-h { font-family: var(--font-sans); font-size: 11.5px; letter-spacing: .14em; text-transform: uppercase; color: var(--rust); font-weight: 600; margin: 0 0 8px; }
  .fig-map { max-width: 220px; }
  .plate.extra { margin-top: 30px; max-width: 640px; }
  .looks { list-style: none; margin: 0; padding: 0; display: grid; gap: 16px; max-width: 40rem; }
  .looks li { display: grid; grid-template-columns: 96px minmax(0, 1fr); gap: 14px; align-items: start; }
  .looks li.no-photo { grid-template-columns: minmax(0, 1fr); }
  .looks :global(img) { display: block; width: 96px; height: 72px; object-fit: cover; border-radius: 8px; border: 1px solid var(--line); }
  .look-name { margin: 0; font-weight: 600; font-size: 16px; }
  .look-name a:hover { color: var(--rust); }
  .look-name i { font-family: var(--font-script); font-style: normal; font-size: 19px; font-weight: 400; }
  .look-text { margin: 2px 0 0; font-size: 15px; line-height: 1.6; }
  .look-compare { display: inline-block; margin-top: 4px; font-size: 13.5px; font-weight: 600; color: var(--rust); border-bottom: 1px solid currentColor; }
  @media (max-width: 600px) {
    .datafig { grid-template-columns: minmax(0, 1fr); }
  }
  @media (max-width: 1023px) {
    .spread { display: flex; flex-direction: column; gap: 18px; }
    .left, .left-inner, .body { display: contents; }
    .head { order: 1; padding-left: 0; }
    .plate.hero { order: 2; }
    .facts { order: 3; }
    .sp-lead { order: 4; margin-top: 0; }
    .texts { order: 5; }
    .plate.extra { order: 6; margin-top: 0; }
    .looks-sec { order: 7; }
    .sp-app { order: 8; }
    .note { order: 9; }
    .more { order: 10; }
    .credits-wrap { order: 11; }
    .texts h2, .looks-sec h2, .more h2 { margin-top: 18px; }
  }
</style>
```

- [ ] **Step 4: Routen med arter**

Ersätt `src/lib/species-routes.ts` med:

```ts
import type { Locale } from './i18n';
import { ABOUT_SLUG, activeGroups, assertUniqueSlugs, getAllSpecies } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  const groups = activeGroups(all);
  assertUniqueSlugs([...all.map((s) => s.slug[locale]), ...groups.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  return [
    ...all.map((species) => ({ params: { slug: species.slug[locale] }, props: { species } })),
    ...groups.map((group) => ({ params: { slug: group.slug[locale] }, props: { group } })),
  ];
}
```

Ersätt `src/components/species/SpeciesRoute.astro` med:

```astro
---
import GroupPage from './GroupPage.astro';
import SpeciesArticle from './SpeciesArticle.astro';
import type { Locale } from '../../lib/i18n';
import type { Group, Species } from '../../lib/species';

interface Props { locale: Locale; species?: Species; group?: Group }
const { locale, species, group } = Astro.props;
---
{species && <SpeciesArticle species={species} locale={locale} />}
{group && <GroupPage group={group} locale={locale} />}
```

- [ ] **Step 5: Kör testerna**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (alla). Om datumformatet skiljer (Node utan full ICU ger "November 20, 2026"): kontrollera `node -p "new Intl.DateTimeFormat('sv-SE',{day:'numeric',month:'long',year:'numeric'}).format(new Date('2026-11-20'))"`. Node 22 har full ICU som standard och ger `20 november 2026`.

- [ ] **Step 6: Commit**

```bash
git add src/components/species/SpeciesArticle.astro src/lib/species-routes.ts src/components/species/SpeciesRoute.astro tests/species.spec.ts
git commit -m "feat(website): artsidan med diagram, karta, inspelning, förväxlingsarter, granskningsrad och JSON-LD"
```

---

### Task 11: Jämförelsesidorna

**Files:**
- Create: `website/tests/comparisons.spec.ts`
- Create: `website/src/components/species/ComparisonPage.astro`
- Modify: `website/src/lib/species-routes.ts`, `website/src/components/species/SpeciesRoute.astro`

- [ ] **Step 1: Skriv testerna**

`tests/comparisons.spec.ts`:

```ts
import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Runs against the TEST data (tests/fixtures/): build with `npm run build:fixtures` first.

async function noSideScroll(page: Page): Promise<void> {
  const [scroll, client] = await page.evaluate(() => [document.documentElement.scrollWidth, document.documentElement.clientWidth]);
  expect(scroll).toBeLessThanOrEqual(client);
}

test.describe('jämförelsesidorna', () => {
  test('blåmes eller talgoxe: båda arterna, tabell, diagram, approta och credits', async ({ page, request }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/blames-eller-talgoxe/');
    expect(res?.status()).toBe(200);
    await expect(page.locator('h1')).toHaveText('Blåmes eller talgoxe?');
    await expect(page.locator('.side h2 a')).toHaveText(['Blåmes', 'Talgoxe']);
    await expect(page.locator('.side h2 a').first()).toHaveAttribute('href', '/sv/arter/blames/');
    await expect(page.locator('.ctable thead th')).toHaveText(['Kännetecken', 'Blåmes', 'Talgoxe']);
    await expect(page.locator('.ctable tbody tr')).toHaveCount(3);
    await expect(page.locator('[data-chart] rect[data-month]')).toHaveCount(24);
    const audios = page.locator('audio');
    await expect(audios).toHaveCount(2);
    for (const src of await audios.evaluateAll((els) => els.map((e) => e.getAttribute('src')!))) expect((await request.get(src)).status()).toBe(200);
    await expect(page.locator('.sp-app')).toContainText('Fortfarande osäker?');
    await expect(page.locator('a[href*="utm_medium%3Dcompare"]')).toHaveCount(1);
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(6);
    await expect(page.locator('time[data-reviewed]')).toHaveAttribute('datetime', '2026-11-20');
    await expect(page.locator('.sp-crumbs [aria-current="page"]')).toHaveText('Blåmes eller talgoxe?');
    await expect(page.locator('link[rel="alternate"][hreflang="en"]')).toHaveAttribute('href', 'https://birdy.community/species/eurasian-blue-tit-vs-great-tit/');
    await expect(page.locator('title')).toHaveText('Blåmes eller talgoxe? Så skiljer du dem åt | Birdy');
    expect(errors).toEqual([]);
  });

  test('engelska sidan följer engelsk slug-ordning, och cellerna följer med', async ({ page }) => {
    await page.goto('/species/eurasian-magpie-vs-western-jackdaw/');
    await expect(page.locator('h1')).toHaveText('Eurasian Magpie or Western Jackdaw?');
    await expect(page.locator('.ctable thead th')).toHaveText(['Feature', 'Eurasian Magpie', 'Western Jackdaw']);
    await expect(page.locator('.ctable tbody tr').first().locator('td').first()).toHaveText('Test cell 1 for the second species');
    await expect(page.locator('title')).toHaveText('Eurasian Magpie vs Western Jackdaw | Birdy');
    await page.goto('/sv/arter/kaja-eller-skata/');
    await expect(page.locator('h1')).toHaveText('Kaja eller skata?');
    await expect(page.locator('.ctable tbody tr').first().locator('td').first()).toHaveText('Testcell 1 för den första arten');
  });

  test('opublicerade och väntande jämförelser ger 404', async ({ page }) => {
    for (const path of ['/sv/arter/storre-hackspett-eller-tretaig-hackspett/', '/sv/arter/hornuggla-eller-kattuggla/', '/species/long-eared-owl-vs-tawny-owl/']) {
      expect((await page.goto(path))?.status(), path).toBe(404);
    }
  });

  for (const width of [360, 390]) {
    test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/sv/arter/blames-eller-talgoxe/');
      await noSideScroll(page);
    });
  }
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/comparisons.spec.ts`
Expected: FAIL (404 på `/sv/arter/blames-eller-talgoxe/`)

- [ ] **Step 3: Jämförelsesidan**

`src/components/species/ComparisonPage.astro`:

```astro
---
import { Image } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import JournalHeadline from '../ui/JournalHeadline.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import Credits from './Credits.astro';
import AudioPlayer from './AudioPlayer.astro';
import MonthChart from './MonthChart.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  SITE, appText, breadcrumbJsonLd, comparisonHref, comparisonPair, comparisonTitle, getAllSpecies, heroOf, hubHref,
  isUnpublished, joinSentences, laterDate, pairNames, playHref, reviewDate, speciesHref, speciesImage,
  taxonJsonLd, wikiSources, type Comparison, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { comparison: Comparison; locale: Locale }
const { comparison: c, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const text = c.text![locale];
const all = await getAllSpecies();
const pair = comparisonPair(c, locale, all);
const names = pairNames(pair, locale);
// The address must follow the slug rule in spec §4; a mismatch means the data and the pages disagree.
const expected = `${pair[0].species.slug[locale]}${locale === 'sv' ? '-eller-' : '-vs-'}${pair[1].species.slug[locale]}`;
if (expected !== c.slug[locale]) throw new Error(`Jämförelsens adress ${c.slug[locale]} följer inte slug-ordningen (väntade ${expected})`);

const pathname = comparisonHref(c, locale);
const otherPath = comparisonHref(c, other);
const title = comparisonTitle(names, t);
const headline = t.species.compareHeadline.replace('{a}', names[0]).replace('{b}', names[1]);
const crumbName = `${t.species.compareItem.replace('{a}', names[0]).replace('{b}', names[1])}?`;
const sharedGroup = pair[0].species.group === pair[1].species.group ? pair[0].species.group : '';
const reviewed = laterDate(reviewDate(pair[0].species), reviewDate(pair[1].species));
const unpublished = isUnpublished(c);
const bothMonths = pair.every((p) => Boolean(p.species.data?.months));
const summaryId = `compare-summary-${c.a}-${c.b}`;
const tones = ['rust', 'navy'] as const;
const statusOf = (s: Species) => {
  const v = s.text![locale].facts.swedenStatus;
  return v ? t.species.statusLabels[v.value] : undefined;
};
const cell = (row: (typeof text.rows)[number], side: 'a' | 'b') => (side === 'a' ? row.a.text : row.b.text);

const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: crumbName, href: pathname },
];
const jsonLd = [
  breadcrumbJsonLd(crumbs),
  {
    '@type': 'WebPage',
    url: new URL(pathname, SITE).toString(),
    name: title.replace(/ \| Birdy$/, ''),
    inLanguage: locale,
    about: pair.map((p) => taxonJsonLd(p.species)),
    lastReviewed: reviewed,
  },
];
---

<Layout locale={locale} pathname={pathname} alternatePath={otherPath} title={title} description={text.metaDescription} noindex={unpublished} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={otherPath} />
  <CategoryBar locale={locale} active={sharedGroup} />
  <main class="cmp wrap" data-comparison-page>
    {unpublished && <p class="sp-preview" data-preview-banner role="note">{t.species.previewBanner}</p>}
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        {crumbs.slice(0, -1).map((cr) => <li><a href={cr.href} data-crumb>{cr.name}</a></li>)}
        <li><span aria-current="page" data-crumb>{crumbName}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.compareKicker} />
    <JournalHeadline text={headline} level="h1" align="left" size="clamp(36px, 4.6vw, 56px)" />
    <p class="answer">{joinSentences(text.shortAnswer)}</p>

    <div class="pair">
      {pair.map((p, i) => {
        const s = p.species;
        const hero = heroOf(s);
        const size = s.text![locale].facts.size?.value;
        const status = statusOf(s);
        return (
          <section class="side">
            <figure class="plate" data-photo={`${p.side}-hero`}>
              <Image src={speciesImage(hero.file)} alt={t.species.altHero.replace('{name}', s.names[locale]).replace('{scientific}', s.names.scientific)} widths={[480, 800]} sizes="(max-width: 760px) 100vw, 45vw" loading={i === 0 ? 'eager' : 'lazy'} decoding="async" />
              <figcaption><span>{t.species.plate.replace('{n}', String(i + 1))}</span><span>{t.species.photoCredit} {hero.author ?? t.species.unknownAuthor}</span></figcaption>
            </figure>
            <h2><a href={speciesHref(s, locale)}>{s.names[locale]}</a></h2>
            <p class="side-latin">{s.names.scientific}</p>
            {(size || status) && (
              <dl class="facts">
                {size && <div><dt>{t.species.facts.size}</dt><dd>{size}</dd></div>}
                {status && <div><dt>{t.species.facts.sweden}</dt><dd>{status}</dd></div>}
              </dl>
            )}
            <AudioPlayer species={s} locale={locale} creditKey={`${p.side}-audio`} />
          </section>
        );
      })}
    </div>

    <h2 class="sp-h2">{t.species.compareTable}</h2>
    <table class="ctable">
      <thead>
        <tr>
          <th scope="col">{t.species.compareFeature}</th>
          {pair.map((p) => <th scope="col">{p.species.names[locale]}</th>)}
        </tr>
      </thead>
      <tbody>
        {text.rows.map((row) => (
          <tr>
            <th scope="row">{row.feature}</th>
            {pair.map((p) => <td>{cell(row, p.side)}</td>)}
          </tr>
        ))}
      </tbody>
    </table>

    {bothMonths && (
      <section class="cmp-chart">
        <h2 class="sp-h2">{t.species.compareChart}</h2>
        <figure>
          <MonthChart
            id={`chart-${c.a}-${c.b}`}
            title={t.species.compareChart}
            letters={t.species.monthLetters}
            series={pair.map((p, i) => ({ label: p.species.names[locale], values: p.species.data!.months!, tone: tones[i] }))}
            describedBy={summaryId}
          />
          <figcaption class="fig-caption">{t.species.chartCaption}</figcaption>
        </figure>
        <ul class="clegend" role="list">
          {pair.map((p, i) => <li><span class={`sw sw--${tones[i]}`} aria-hidden="true"></span>{p.species.names[locale]}</li>)}
        </ul>
        <p class="data-summary" id={summaryId}>
          {pair.map((p) => t.species.chartSeriesSentence.replace('{name}', p.species.names[locale]).replace('{text}', (p.species.data?.sentences[locale] ?? []).join(' '))).join(' ')}
        </p>
      </section>
    )}

    <aside class="sp-app cmp-app">
      <p class="sp-app-h">{t.species.compareApp}</p>
      <p>{appText(undefined, t)}</p>
      <PlayStoreBadge locale={locale} href={playHref(c.slug[locale], 'compare')} alt={t.alt.playStoreBadge} size="small" />
    </aside>

    <Credits
      locale={locale}
      photos={pair.map((p) => ({ key: `${p.side}-hero`, image: heroOf(p.species) }))}
      articles={pair.flatMap((p) => wikiSources(p.species).map((w) => ({ ...w, name: p.species.names[locale] })))}
      reportData={bothMonths}
      redList={false}
      reviewedAt={reviewed}
      reportSubject={t.species.reportSubjectCompare.replace('{name}', crumbName)}
    />
  </main>
  <Footer locale={locale} switchLangHref={otherPath} />
</Layout>

<style>
  .cmp { padding-top: 26px; padding-bottom: 88px; }
  .answer { font-size: 18px; line-height: 1.6; max-width: 44rem; margin: 14px 0 0; }
  .pair { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; margin-top: 30px; }
  .side h2 { font-size: 28px; margin: 14px 0 0; }
  .side h2 a:hover { color: var(--rust); }
  .side-latin { margin: 0 0 8px; font-family: var(--font-script); font-size: 20px; color: var(--muted); }
  .ctable { width: 100%; max-width: 52rem; border-collapse: collapse; font-size: 15px; }
  .ctable th, .ctable td { text-align: left; vertical-align: top; padding: 10px 12px; border-bottom: 1px solid var(--line); }
  .ctable thead th { font-size: 12px; letter-spacing: .12em; text-transform: uppercase; color: var(--muted); font-weight: 600; }
  .ctable tbody th { font-weight: 600; color: var(--rust); width: 22%; }
  .cmp-chart figure { margin: 0; max-width: 36rem; }
  .clegend { list-style: none; margin: 10px 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 18px; font-size: 13.5px; }
  .clegend li { display: inline-flex; align-items: center; gap: 8px; }
  .sw { width: 12px; height: 12px; border-radius: 3px; }
  .sw--rust { background: var(--rust); }
  .sw--navy { background: var(--navy); }
  .cmp-chart .data-summary { max-width: 40rem; }
  .cmp-app { margin-top: 44px; max-width: 520px; }
  @media (max-width: 760px) {
    .pair { grid-template-columns: minmax(0, 1fr); }
  }
  @media (max-width: 600px) {
    .ctable { font-size: 14px; }
    .ctable th, .ctable td { padding: 8px 6px; }
  }
</style>
```

- [ ] **Step 4: Routen med jämförelser**

Ersätt `src/lib/species-routes.ts` med:

```ts
import type { Locale } from './i18n';
import { ABOUT_SLUG, activeGroups, assertUniqueSlugs, getAllSpecies, getComparisons } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  const groups = activeGroups(all);
  const comparisons = await getComparisons();
  assertUniqueSlugs([
    ...all.map((s) => s.slug[locale]),
    ...groups.map((g) => g.slug[locale]),
    ...comparisons.map((c) => c.slug[locale]),
    ABOUT_SLUG[locale],
  ], locale);
  return [
    ...all.map((species) => ({ params: { slug: species.slug[locale] }, props: { species } })),
    ...groups.map((group) => ({ params: { slug: group.slug[locale] }, props: { group } })),
    ...comparisons.map((comparison) => ({ params: { slug: comparison.slug[locale] }, props: { comparison } })),
  ];
}
```

Ersätt `src/components/species/SpeciesRoute.astro` med:

```astro
---
import ComparisonPage from './ComparisonPage.astro';
import GroupPage from './GroupPage.astro';
import SpeciesArticle from './SpeciesArticle.astro';
import type { Locale } from '../../lib/i18n';
import type { Comparison, Group, Species } from '../../lib/species';

interface Props { locale: Locale; species?: Species; group?: Group; comparison?: Comparison }
const { locale, species, group, comparison } = Astro.props;
---
{species && <SpeciesArticle species={species} locale={locale} />}
{group && <GroupPage group={group} locale={locale} />}
{comparison && <ComparisonPage comparison={comparison} locale={locale} />}
```

- [ ] **Step 5: Kör testerna**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/comparisons.spec.ts tests/species.spec.ts`
Expected: PASS (alla)

- [ ] **Step 6: Commit**

```bash
git add tests/comparisons.spec.ts src/components/species/ComparisonPage.astro src/lib/species-routes.ts src/components/species/SpeciesRoute.astro
git commit -m "feat(website): jämförelsesidorna för förväxlingspar"
```

---

### Task 12: Sidan "Så gör vi artsidorna"

**Files:**
- Create: `website/src/components/species/AboutSpeciesPages.astro`
- Create: `website/src/pages/sv/arter/om-artsidorna.astro`, `website/src/pages/species/about-these-pages.astro`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Skriv testerna**

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('om-sidan', () => {
  for (const [path, h1, other, sections] of [
    ['/sv/arter/om-artsidorna/', 'Så gör vi artsidorna', '/species/about-these-pages/', ['Källorna', 'Så används AI', 'Granskningen', 'Licenserna', 'Rättelser']],
    ['/species/about-these-pages/', 'How we make the species pages', '/sv/arter/om-artsidorna/', ['The sources', 'How AI is used', 'The review', 'The licences', 'Corrections']],
  ] as const) {
    test(`${path} har rubrikerna, mejladressen och språkparet`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      const res = await page.goto(path);
      expect(res?.status()).toBe(200);
      await expect(page.locator('h1')).toHaveText(h1);
      await expect(page.locator('main h2')).toHaveText([...sections]);
      await expect(page.locator('main a[href^="mailto:"]')).toHaveCount(1);
      await expect(page.locator(`link[rel="alternate"][hreflang="${path.startsWith('/sv') ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${other}`);
      expect(errors).toEqual([]);
    });
  }
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g om-sidan`
Expected: FAIL (404)

- [ ] **Step 3: Komponenten**

`src/components/species/AboutSpeciesPages.astro`:

```astro
---
// "How we make the species pages" (spec 2026-09-25 §8). Text in copy.{sv,en}.json → speciesAbout.
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import JournalHeadline from '../ui/JournalHeadline.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import { ALBIN_URL, ALBIT_URL, CONTACT_EMAIL } from '../../lib/links';
import { SITE, aboutHref, breadcrumbJsonLd, hubHref } from '../../lib/species';
import '../../styles/species.css';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const a = t.speciesAbout;
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const pathname = aboutHref(locale);
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: a.crumb, href: pathname },
];
const jsonLd = [
  breadcrumbJsonLd(crumbs),
  {
    '@type': 'WebPage',
    url: new URL(pathname, SITE).toString(),
    name: a.title.replace(/ \| Birdy$/, ''),
    inLanguage: locale,
    author: { '@type': 'Person', name: 'Albin Abrahamsson', url: ALBIN_URL },
    publisher: { '@type': 'Organization', name: 'AlbIT AB', url: ALBIT_URL },
  },
];
---

<Layout locale={locale} pathname={pathname} alternatePath={aboutHref(other)} title={a.title} description={a.description} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={aboutHref(other)} />
  <main class="about wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><a href={crumbs[1].href} data-crumb>{crumbs[1].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[2].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <JournalHeadline text={a.headline} level="h1" align="left" size="clamp(38px, 5vw, 60px)" />
    <p class="lead">{a.lead}</p>
    {a.sections.map((section) => (
      <section class="about-sec">
        <h2>{section.heading}</h2>
        {section.paragraphs.map((paragraph) => {
          const [before, after] = paragraph.split('{email}');
          return (
            <p>
              {before}
              {after !== undefined && <Fragment><a href={`mailto:${CONTACT_EMAIL}`}>{CONTACT_EMAIL}</a>{after}</Fragment>}
            </p>
          );
        })}
      </section>
    ))}
  </main>
  <Footer locale={locale} switchLangHref={aboutHref(other)} />
</Layout>

<style>
  .about { padding-top: 26px; padding-bottom: 88px; max-width: 46rem; }
  .about-sec h2 { font-size: 26px; margin: 36px 0 10px; }
  .about-sec p { font-size: 16px; line-height: 1.7; margin: 0 0 12px; }
  .about-sec a { color: var(--rust); text-decoration: underline; text-underline-offset: 3px; }
</style>
```

- [ ] **Step 4: Routes**

`src/pages/sv/arter/om-artsidorna.astro`:

```astro
---
import AboutSpeciesPages from '../../../components/species/AboutSpeciesPages.astro';
---
<AboutSpeciesPages locale="sv" />
```

`src/pages/species/about-these-pages.astro`:

```astro
---
import AboutSpeciesPages from '../../components/species/AboutSpeciesPages.astro';
---
<AboutSpeciesPages locale="en" />
```

- [ ] **Step 5: Kör testerna**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts tests/comparisons.spec.ts`
Expected: PASS (alla)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/AboutSpeciesPages.astro src/pages/sv/arter/om-artsidorna.astro src/pages/species/about-these-pages.astro tests/species.spec.ts
git commit -m "feat(website): sidan Så gör vi artsidorna (SV och EN)"
```

---

### Task 13: Menyn, sidfoten och startsidans länk

**Files:**
- Modify: `website/src/components/Nav.astro`
- Modify: `website/src/components/Footer.astro`
- Modify: `website/src/components/Guide.astro`
- Modify: `website/tests/home.spec.ts`
- Modify: `website/tests/species.spec.ts` (lägg till i slutet)

- [ ] **Step 1: Uppdatera de befintliga testerna**

I `tests/home.spec.ts`:
- I `for (const [path, label, getApp] of [['/sv/', 'Så funkar det', 'Hämta appen'], ['/', 'How it works', 'Get the app']] as const)`: byt `'Så funkar det'` mot `'Arter'` och `'How it works'` mot `'Species'`.
- I testet `sidfoten har kolumnerna`: byt `['Utforska', 'Läs', 'Information']` mot `['Arter', 'Utforska', 'Läs', 'Information']`.

Lägg till i slutet av `tests/species.spec.ts`:

```ts
test.describe('meny och sidfot för arterna', () => {
  for (const path of ['/sv/', '/sv/blog/', '/sv/arter/talgoxe/']) {
    test(`sidfoten på ${path} har Arter och tolv vanliga arter`, async ({ page, request }) => {
      await page.goto(path);
      const footer = page.locator('footer.footer');
      await expect(footer.locator('.fh').first()).toHaveText('Arter');
      const common = footer.locator('.fpop a');
      await expect(common).toHaveCount(12);
      for (const href of await common.evaluateAll((els) => els.map((e) => e.getAttribute('href')!))) {
        expect((await request.get(href)).status(), href).toBe(200);
      }
    });
  }

  test('startsidans uppslagsverk länkar till arterna', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('#guide a[href="/sv/arter/"]')).toBeVisible();
  });

  test('mobilmenyn har Arter först', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await page.locator('#site-nav .menu-toggle').click();
    await expect(page.locator('#mobile-menu a').first()).toHaveText('Arter');
  });

  test('Arter är markerad i menyn under hela /sv/arter/', async ({ page }) => {
    for (const path of ['/sv/arter/', '/sv/arter/ugglor/', '/sv/arter/talgoxe/', '/sv/arter/blames-eller-talgoxe/', '/sv/arter/om-artsidorna/']) {
      await page.goto(path);
      await expect(page.locator('#site-nav .links a[aria-current="page"]'), path).toHaveText('Arter');
    }
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/home.spec.ts tests/species.spec.ts -g "meny|sidfot"`
Expected: FAIL (ingen länk "Arter" än)

- [ ] **Step 3: Menyn**

I `src/components/Nav.astro`, lägg till importen `import { hubHref } from '../lib/species';` och ersätt `const links = [ ... ];` med:

```ts
const speciesHub = hubHref(locale);
const links = [
  { href: speciesHub, label: t.nav.species },
  { href: `${home}#how-it-works`, label: t.nav.howItWorks },
  { href: `${home}#app`, label: t.nav.app },
  { href: `${home}#premium`, label: t.nav.premium },
  { href: fieldNotesHref(locale), label: t.nav.fieldNotes },
];
// "Species" is current on the hub, group, species, comparison and about pages.
const isCurrent = (href: string) => href === here || (href === speciesHub && here.startsWith(speciesHub));
```

Byt båda förekomsterna av `aria-current={l.href === here ? 'page' : undefined}` mot `aria-current={isCurrent(l.href) ? 'page' : undefined}`.

Kontrollera i 1024 px att menyraden fortfarande får plats (fem länkar, språkbytet och knappen). Om den bryter: sänk `gap` i `.links` från `26px` till `20px` i `@media (max-width: 1180px)`.

- [ ] **Step 4: Sidfoten**

I `src/components/Footer.astro`, lägg till importen `import { commonSpecies, getAllSpecies, groupHref, hubHref, largestGroups, speciesHref } from '../lib/species';` och efter `const blogPrefix = ...`:

```ts
const allSpecies = await getAllSpecies();
const topGroups = largestGroups(allSpecies, 5);
const common = commonSpecies(allSpecies);
```

Lägg till en ny kolumn före `<div class="col">` med `t.footer.explore`:

```astro
      <div class="col">
        <h2 class="fh">{t.footer.species}</h2>
        {topGroups.map((g) => <a href={groupHref(g, locale)}>{g.name[locale]}</a>)}
        <a href={hubHref(locale)}>{t.footer.allSpecies}</a>
      </div>
```

Lägg till före `<div class="fbot">`:

```astro
    {common.length > 0 && (
      <p class="fpop"><span class="fpop-h">{t.footer.commonSpecies}</span>{common.map((s) => <a href={speciesHref(s, locale)}>{s.names[locale]}</a>)}</p>
    )}
```

**Ändrat 2026-10-07 (Task 4:s fixvåg):** `commonSpecies()` stoppar inte längre bygget när någon av de tolv saknar sida, utan returnerar de av de tolv som har en sida i bygget, i listans ordning. Arterna publiceras en i taget (Task 16) och koden ligger på `main` innan något är publicerat (Task 15 Step 4), så listan är tom eller ofullständig länge. Raden "Vanliga arter" döljs när listan är tom (villkoret ovan); testa både en tom och en ofullständig lista.

I `<style>`: ändra `.fgrid { display: grid; grid-template-columns: 1.6fr 1fr 1fr 1fr; gap: 40px; }` till `grid-template-columns: 1.6fr repeat(4, 1fr);` och lägg till:

```css
  .fpop { margin: 40px 0 0; display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 16px; font-size: 13.5px; }
  .fpop-h { font-size: 11px; letter-spacing: .16em; text-transform: uppercase; color: var(--apricot); font-weight: 600; margin-right: 4px; }
  /* same colour as .col a (alpha checked in scripts/check-contrast.mjs) */
  .fpop a { color: rgba(233, 226, 210, .82); text-decoration: underline; text-decoration-color: rgba(233, 226, 210, .3); text-underline-offset: 3px; transition: color .2s; }
  .fpop a:hover { color: var(--apricot); }
```

- [ ] **Step 5: Startsidans länk**

I `src/components/Guide.astro`, lägg till importen `import { hubHref } from '../lib/species';` och efter `<CoverageMap locale={locale} />`:

```astro
    <p class="browse"><a href={hubHref(locale)}>{t.guide.browse} <span aria-hidden="true">→</span></a></p>
```

Och i komponentens `<style>`:

```css
  .browse { margin: 28px 0 0; font-weight: 600; }
  .browse a { color: var(--rust); border-bottom: 1px solid currentColor; padding-bottom: 2px; }
```

- [ ] **Step 6: Kör alla webbtester**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS (hela sviten, även de gamla testerna)

- [ ] **Step 7: Commit**

```bash
git add src/components/Nav.astro src/components/Footer.astro src/components/Guide.astro tests/home.spec.ts tests/species.spec.ts
git commit -m "feat(website): Arter i menyn, sidfoten och startsidans uppslagsverk"
```

---

### Task 14: Sitemap, SEO-reglerna som kod och kontrollen av förhandsbygget

**Files:**
- Create: `website/src/lib/species-sitemap.mjs`
- Modify: `website/astro.config.mjs`
- Create: `website/scripts/check-seo.mjs`
- Create: `website/scripts/check-preview-build.mjs`
- Modify: `website/scripts/check-no-dashes.mjs`
- Create: `website/tests/a11y.spec.ts` (ändrat 2026-10-05 (b), se Step 6b)
- Modify: `website/package.json`

- [ ] **Step 1: Sitemap-data**

`src/lib/species-sitemap.mjs`:

```js
// Species data for astro.config.mjs (sitemap lastmod and noindex), in plain JS so the config can load it.
// Same publishing rule as the pages (src/lib/species-source.mjs). The group rule must match MIN_GROUP_SIZE
// in src/lib/species.ts; scripts/check-seo.mjs fails if a noindex page shows up in the sitemap.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { comparisonsDir, isComparisonBuilt, isPreview, isSpeciesBuilt, readJsonDir, speciesDir } from './species-source.mjs';

export const MIN_GROUP_SIZE = 3;
const BASES = [['sv', '/sv/arter/'], ['en', '/species/']];

/** @param {string} root the website folder */
export function readSpeciesSitemapInfo(root) {
  const groups = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).groups;
  const built = readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r));
  const builtQids = new Set(built.map((r) => r.qid));
  const comparisons = readJsonDir(root, comparisonsDir()).filter((c) => isComparisonBuilt(c, builtQids));
  const preview = isPreview();
  /** @type {Map<string, string>} */
  const lastmod = new Map();
  /** @type {Set<string>} */
  const noindex = new Set();
  /** @type {Map<string, number>} */
  const sizes = new Map();
  let newest = '';
  for (const r of built) {
    const at = r.generated?.text?.at ?? r.verification?.at ?? '';
    for (const [lang, base] of BASES) {
      const path = `${base}${r.slug[lang]}/`;
      if (at) lastmod.set(path, at);
      if (preview && !r.publish) noindex.add(path);
    }
    sizes.set(r.group, (sizes.get(r.group) ?? 0) + 1);
    if (at > newest) newest = at;
  }
  for (const c of comparisons) {
    for (const [lang, base] of BASES) {
      const path = `${base}${c.slug[lang]}/`;
      if (c.generated?.at) lastmod.set(path, c.generated.at);
      if (preview && !c.publish) noindex.add(path);
    }
  }
  for (const g of groups) {
    const n = sizes.get(g.key) ?? 0;
    if (n === 0) continue; // no page at all
    for (const [lang, base] of BASES) {
      const path = `${base}${g.slug[lang]}/`;
      if (n < MIN_GROUP_SIZE) noindex.add(path);
      else if (newest) lastmod.set(path, newest);
    }
  }
  if (newest) for (const [, base] of BASES) lastmod.set(base, newest);
  return { lastmod, noindex };
}
```

- [ ] **Step 2: Koppla in i `astro.config.mjs`**

Lägg till importen `import { readSpeciesSitemapInfo } from './src/lib/species-sitemap.mjs';` och efter `for`-loopen som fyller `noteDates`:

```js
// Species pages: lastmod from each page's data, small groups and unpublished preview pages left out (spec §12 and §14).
const speciesInfo = readSpeciesSitemapInfo(root);
```

Ersätt `integrations: [sitemap({ ... }), speciesAudio],` med:

```js
  integrations: [sitemap({
    filter: (page) => !speciesInfo.noindex.has(new URL(page).pathname),
    serialize(item) {
      const path = new URL(item.url).pathname;
      const d = noteDates.get(path) ?? speciesInfo.lastmod.get(path);
      if (d) item.lastmod = new Date(d).toISOString();
      return item;
    },
  }), speciesAudio],
```

- [ ] **Step 3: SEO-skriptet**

**Tillägg (2026-10-07, Task 4:s fixvåg):** sidfoten kräver inte längre alla tolv vanliga arter (`commonSpecies()` returnerar de som har en sida). Lägg i `check-seo.mjs` till en **varning, inte ett fel**, som listar de av de tolv i `species-groups.json` (`common`) som saknar sida i bygget, till exempel `check-seo: varning, 3 av 12 vanliga arter saknar sida: Q25334, Q14683, Q4764`. Den får aldrig stoppa bygget eller publiceringsloopen.

`scripts/check-seo.mjs`:

```js
#!/usr/bin/env node
// SEO rules as code (spec 2026-09-25 §12). Runs on the built site (dist/, or the folder given as the first
// argument) and lists every failure. New pages (/species/, /sv/arter/) get the full list; every page gets
// one h1, alt on images and no dead links.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, process.argv[2] ?? 'dist');
const SITE = 'https://birdy.community';
const NEW = ['/species/', '/sv/arter/'];

if (!existsSync(dist)) {
  console.error(`check-seo: ${dist} saknas, bygg först`);
  process.exit(1);
}

const decode = (s) => s.replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&#39;|&#x27;/g, "'");
const attr = (tag, name) => { const m = tag.match(new RegExp(`\\s${name}="([^"]*)"`)); return m ? decode(m[1]) : null; };
const text = (html) => decode(html.replace(/<[^>]+>/g, '')).replace(/\s+/g, ' ').trim();

const files = readdirSync(dist, { recursive: true, encoding: 'utf8' }).filter((f) => f.endsWith('.html')).map((f) => join(dist, f));
const pathOf = (file) => {
  const rel = relative(dist, file).split(sep).join('/');
  if (rel === 'index.html') return '/';
  return rel.endsWith('/index.html') ? `/${rel.slice(0, -'index.html'.length)}` : `/${rel}`;
};
const pages = files.map((file) => ({ path: pathOf(file), html: readFileSync(file, 'utf8') }));
const byPath = new Map(pages.map((p) => [p.path, p]));

const sitemap = new Set();
for (const f of readdirSync(dist).filter((f) => /^sitemap-\d+\.xml$/.test(f))) {
  for (const m of readFileSync(join(dist, f), 'utf8').matchAll(/<loc>([^<]+)<\/loc>/g)) sitemap.add(new URL(m[1]).pathname);
}

const errors = [];
const fail = (path, msg) => errors.push(`${path}: ${msg}`);
const seenTitles = new Map();
const seenDescs = new Map();

const internalTarget = (href) => {
  if (!href.startsWith('/') || href.startsWith('//')) return null;
  return href.split('#')[0].split('?')[0] || '/';
};
const exists = (target) => byPath.has(target) || byPath.has(`${target}/`) || existsSync(join(dist, target));

for (const { path, html } of pages) {
  const isNew = NEW.some((p) => path.startsWith(p));

  const h1 = (html.match(/<h1[\s>]/g) ?? []).length;
  if (h1 !== 1) fail(path, `ska ha exakt en h1 (har ${h1})`);
  for (const tag of html.match(/<img\b[^>]*>/g) ?? []) {
    if (attr(tag, 'alt') === null) fail(path, `bild utan alt: ${tag.slice(0, 90)}`);
    if (isNew && (!attr(tag, 'width') || !attr(tag, 'height'))) fail(path, `bild utan width/height: ${tag.slice(0, 90)}`);
  }
  for (const tag of html.match(/<a\b[^>]*>/g) ?? []) {
    const target = internalTarget(attr(tag, 'href') ?? '');
    if (target && !exists(target)) fail(path, `död länk till ${attr(tag, 'href')}`);
  }
  if (!isNew) continue;

  // Comparison titles may fall back to "{A} vs {B} | Birdy", which is under 40 for short names (spec §12).
  const isComparison = html.includes('data-comparison-page');
  const title = text(html.match(/<title>([\s\S]*?)<\/title>/)?.[1] ?? '');
  if ((!isComparison && title.length < 40) || title.length > 60) fail(path, `titeln är ${title.length} tecken: ${title}`);
  if (seenTitles.has(title)) fail(path, `samma titel som ${seenTitles.get(title)}`);
  seenTitles.set(title, path);

  const desc = attr(html.match(/<meta name="description"[^>]*>/)?.[0] ?? '', 'content') ?? '';
  if (desc.length < 120 || desc.length > 155) fail(path, `description är ${desc.length} tecken`);
  if (seenDescs.has(desc)) fail(path, `samma description som ${seenDescs.get(desc)}`);
  seenDescs.set(desc, path);

  const levels = [...html.matchAll(/<h([1-6])[\s>]/g)].map((m) => Number(m[1]));
  for (let i = 1; i < levels.length; i += 1) {
    if (levels[i] > levels[i - 1] + 1) fail(path, `rubriknivån hoppar från h${levels[i - 1]} till h${levels[i]}`);
  }

  const canonical = attr(html.match(/<link rel="canonical"[^>]*>/)?.[0] ?? '', 'href');
  if (canonical !== SITE + path) fail(path, `canonical är ${canonical}`);
  const alternates = Object.fromEntries([...html.matchAll(/<link rel="alternate" hreflang="([^"]+)" href="([^"]+)"/g)].map((m) => [m[1], m[2]]));
  const lang = path.startsWith('/sv/') ? 'sv' : 'en';
  const other = lang === 'sv' ? 'en' : 'sv';
  if (alternates[lang] !== SITE + path) fail(path, `hreflang ${lang} pekar inte på sidan själv`);
  const otherPath = alternates[other] ? new URL(alternates[other]).pathname : null;
  const otherPage = otherPath ? byPath.get(otherPath) : undefined;
  if (!otherPage) fail(path, `hreflang ${other} pekar på en sida som inte finns: ${alternates[other]}`);
  else if (!otherPage.html.includes(`hreflang="${lang}" href="${SITE + path}"`)) fail(path, `${otherPath} pekar inte tillbaka med hreflang ${lang}`);
  const english = lang === 'en' ? SITE + path : alternates.en;
  if (alternates['x-default'] !== english) fail(path, 'x-default ska peka på den engelska sidan');

  const noindex = /<meta name="robots" content="noindex/.test(html);
  if (!noindex && !sitemap.has(path)) fail(path, 'saknas i sitemapen');
  if (noindex && sitemap.has(path)) fail(path, 'har noindex men finns i sitemapen');

  const graph = [];
  for (const m of html.matchAll(/<script type="application\/ld\+json">([\s\S]*?)<\/script>/g)) {
    try {
      const data = JSON.parse(m[1]);
      graph.push(...(data['@graph'] ?? [data]));
    } catch {
      fail(path, 'JSON-LD går inte att tolka');
    }
  }
  const crumbs = [...html.matchAll(/data-crumb[^>]*>([^<]*)</g)].map((m) => text(m[1]));
  const breadcrumb = graph.find((n) => n['@type'] === 'BreadcrumbList');
  if (!breadcrumb) fail(path, 'BreadcrumbList saknas');
  else if (JSON.stringify(breadcrumb.itemListElement.map((i) => i.name)) !== JSON.stringify(crumbs)) {
    fail(path, `BreadcrumbList (${breadcrumb.itemListElement.map((i) => i.name).join(' › ')}) matchar inte brödsmulorna (${crumbs.join(' › ')})`);
  }
  const list = graph.find((n) => n['@type'] === 'CollectionPage')?.mainEntity;
  if (list) {
    const shown = (html.match(/data-item[\s>=]/g) ?? []).length;
    if (list.numberOfItems !== shown || list.itemListElement.length !== shown) fail(path, `ItemList har ${list.numberOfItems} poster men sidan visar ${shown}`);
  }

  // The date in JSON-LD must be the one the page shows (spec §11, Revision 2026-10-05:
  // no more reviewedBy, the page no longer names a reviewer, only a verification date).
  const webPage = graph.find((n) => n['@type'] === 'WebPage');
  const reviewedAt = attr(html.match(/<time\b[^>]*data-reviewed[^>]*>/)?.[0] ?? '', 'datetime');
  if (reviewedAt && webPage?.lastReviewed !== reviewedAt) {
    fail(path, `lastReviewed (${webPage?.lastReviewed}) är inte datumet på sidan (${reviewedAt})`);
  }
  if (webPage?.reviewedBy) fail(path, 'reviewedBy finns kvar i JSON-LD (borttaget 2026-10-05, bara lastReviewed ska finnas)');

  // Credits and media on species and comparison pages (spec §10, rule 5).
  if (html.includes('data-species-page') || isComparison) {
    if (!reviewedAt) fail(path, 'kontrollraden saknas');
    const keys = new Set([...html.matchAll(/data-(?:photo|audio)="([^"]+)"/g)].map((m) => m[1]));
    const credits = new Set([...html.matchAll(/data-credit-for="([^"]+)"/g)].map((m) => m[1]));
    for (const key of keys) if (!credits.has(key)) fail(path, `${key} saknar creditrad`);
    if (!/data-wiki-credit[\s\S]*?data-wiki="/.test(html)) fail(path, 'Wikipediaraden saknas eller har inga artiklar');
    if (/data-(?:chart|map|redlist)\b/.test(html) && !html.includes('data-data-credit')) fail(path, 'diagram, karta eller rödlista utan datakälla');
    const audios = [...html.matchAll(/<audio\b[^>]*>/g)].map((m) => attr(m[0], 'src'));
    for (const src of audios) if (!src || !exists(src)) fail(path, `inspelningen finns inte i bygget: ${src}`);
    for (const media of [webPage?.associatedMedia].flat().filter(Boolean)) {
      if (!audios.includes(new URL(media.contentUrl).pathname)) fail(path, `AudioObject pekar på ${media.contentUrl}, som inte spelas på sidan`);
    }
  }

  // Every chart and map has its sentences as text (spec §12, rule 6).
  for (const m of html.matchAll(/<svg\b[^>]*aria-describedby="([^"]+)"[^>]*>/g)) {
    const desc = text(html.match(new RegExp(`id="${m[1]}"[^>]*>([\\s\\S]*?)</p>`))?.[1] ?? '');
    if (!desc) fail(path, `diagrammet eller kartan har ingen mening som text (#${m[1]})`);
  }
}

if (errors.length) {
  console.error(`check-seo FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(`check-seo OK (${pages.length} sidor, ${pages.filter((p) => NEW.some((n) => p.path.startsWith(n))).length} artsidor, ${sitemap.size} adresser i sitemapen)`);
```

- [ ] **Step 4: Kontrollen av förhandsbygget**

**Tillägg (2026-10-07, Task 4:s fixvåg):** `scripts/check-preview-build.mjs` och npm-skriptet `test:preview-build` finns redan. Skriptet kontrollerar att inga foton eller inspelningar av arter utan sida hamnar i bygget (byte för byte, och som omskalade kopior via testfotonas egna färger, eftersom Astro tar bort original som bara används genom `<Image>`), och att de publicerade och de kontrollerade opublicerade arternas foton finns i rätt bygge när artsidorna finns. **Utöka skriptet** med sidkontrollerna nedan (lägg dem före felutskriften och behåll fotokontrollen), skriv inte över det; i Step 6 finns `test:preview-build` redan.

`scripts/check-preview-build.mjs`:

```js
#!/usr/bin/env node
// Checks the publishing rule (spec 2026-09-25 §14) on the TEST data: dist/ is the normal fixture build
// (npm run build:fixtures) and dist-preview/ the preview build (npm run build:preview-fixtures).
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist');
const preview = resolve(root, 'dist-preview');
for (const dir of [dist, preview]) {
  if (!existsSync(dir)) {
    console.error(`check-preview-build: ${dir} saknas (npm run build:fixtures och npm run build:preview-fixtures)`);
    process.exit(1);
  }
}
const page = (dir, path) => {
  const file = join(dir, path, 'index.html');
  return existsSync(file) ? readFileSync(file, 'utf8') : null;
};
const errors = [];

const UNPUBLISHED = [
  'sv/arter/storre-hackspett',
  'species/great-spotted-woodpecker',
  'sv/arter/storre-hackspett-eller-tretaig-hackspett',
  'species/eurasian-three-toed-woodpecker-vs-great-spotted-woodpecker',
];
const NEVER = ['sv/arter/grongoling', 'sv/arter/spillkraka', 'sv/arter/hornuggla-eller-kattuggla', 'species/long-eared-owl-vs-tawny-owl'];

for (const p of UNPUBLISHED) {
  if (page(dist, p)) errors.push(`dist/${p}: opublicerad sida finns i det vanliga bygget`);
  const html = page(preview, p);
  if (!html) {
    errors.push(`dist-preview/${p}: saknas i förhandsbygget`);
    continue;
  }
  if (!html.includes('<meta name="robots" content="noindex, follow"')) errors.push(`dist-preview/${p}: saknar noindex`);
  if (!html.includes('data-preview-banner')) errors.push(`dist-preview/${p}: saknar förhandsbanderollen`);
}
for (const p of NEVER) {
  for (const [name, dir] of [['dist', dist], ['dist-preview', preview]]) if (page(dir, p)) errors.push(`${name}/${p}: ska aldrig få en sida`);
}

const talgoxe = page(preview, 'sv/arter/talgoxe');
if (!talgoxe || talgoxe.includes('data-preview-banner') || talgoxe.includes('noindex')) errors.push('dist-preview/sv/arter/talgoxe: en publicerad sida ska se ut som vanligt');

if (page(dist, 'sv/arter/hackspettar')) errors.push('dist/sv/arter/hackspettar: en grupp utan byggda arter ska inte få en sida');
const woodpeckers = page(preview, 'sv/arter/hackspettar');
if (!woodpeckers || !woodpeckers.includes('noindex')) errors.push('dist-preview/sv/arter/hackspettar: ska finnas med noindex (färre än tre arter)');

const sitemap = readdirSync(preview).filter((f) => /^sitemap-\d+\.xml$/.test(f)).map((f) => readFileSync(join(preview, f), 'utf8')).join('\n');
if (sitemap.includes('storre-hackspett')) errors.push('dist-preview: en opublicerad sida finns i sitemapen');

const audio = existsSync(join(dist, 'audio/species')) ? readdirSync(join(dist, 'audio/species')) : [];
if (audio.length !== 4) errors.push(`dist/audio/species: väntade 4 inspelningar (byggda arter med inspelning), fick ${audio.length}`);

if (errors.length) {
  console.error(`check-preview-build FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log('check-preview-build OK (opublicerat bara i förhandsbygget, med noindex och banderoll)');
```

- [ ] **Step 5: Streckvakten täcker artdatan**

I `scripts/check-no-dashes.mjs`, ändra importraden högst upp till `import { existsSync, readFileSync, readdirSync } from 'node:fs';` och lägg till efter loopen över `deckFiles` (före loopen över `noteFiles`):

```js
// Species and comparison data (spec 2026-09-25): rendered text only. Quotes are Wikipedia's own words and
// are not shown; the top-level fact list, raw counts and generation details are not shown either.
const SKIP = new Set(['quote', 'sourceUrl', 'licenseUrl', 'file', 'revision', 'title', 'model', 'prompt', 'at', 'effort', 'checker', 'qid', 'slug', 'factIds']);
// 'flags' added 2026-10-06 (fas 1b final review I7): the flag messages are never shown and can hold the
// checking model's own English reasons.
const SKIP_TOP = new Set(['facts', 'raw', 'generated', 'rejectedText', 'errors', 'review', 'verification', 'flags']);
const walkRendered = (value, path, cb) => {
  if (typeof value === 'string') cb(value, path);
  else if (Array.isArray(value)) value.forEach((v, i) => walkRendered(v, `${path}[${i}]`, cb));
  else if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) {
      if (SKIP.has(key) || (!path && SKIP_TOP.has(key))) continue;
      walkRendered(value[key], path ? `${path}.${key}` : key, cb);
    }
  }
};
const dataDirs = ['src/data/species', 'src/data/comparisons', 'tests/fixtures/species', 'tests/fixtures/comparisons'];
const dataFiles = [
  'src/data/species-groups.json',
  ...dataDirs.flatMap((dir) => (existsSync(resolve(root, dir)) ? readdirSync(resolve(root, dir)).filter((f) => f.endsWith('.json')).map((f) => join(dir, f)) : [])),
];
for (const file of dataFiles) {
  walkRendered(JSON.parse(readFileSync(resolve(root, file), 'utf8')), '', (value, path) => {
    if (emDashRe.test(value)) fail(`${file}:${path}`, `tankstreck (${EM_DASH})`);
    if (spacedEnDashRe.test(value)) fail(`${file}:${path}`, `tankstreck ( ${EN_DASH} )`);
  });
}
files.push(...dataFiles);
```

(`files` är en `const`-array och går att pusha till; den används bara i slutraden `no-dashes OK (${files.length} filer)`.)

- [ ] **Step 6: Skripten i `package.json`**

Lägg till i `"scripts"`:

```json
    "test:seo": "node scripts/check-seo.mjs",
    "test:preview-build": "npm run build:preview-fixtures && node scripts/check-preview-build.mjs",
    "verify": "npm run build && npm run test:seo && npm run test:i18n && npm run test:no-dashes && npm run test:palette && npm run test:contrast",
    "verify:fixtures": "npm run build:fixtures && npm run test:seo && npm run test:unit && npm run test:i18n && npm run test:no-dashes && npm run test:palette && npm run test:contrast && npm run test:preview-build",
```

- [ ] **Step 6b: En axe-kontroll för en enskild sida (ändrat 2026-10-05 (b): publiceringsloopen i Task 16 behöver kontrollera exakt den sida den just byggde, inte hela sajten)**

Det finns ingen axe-uppsättning i `website/` ännu (den tidigare "axe 0 fel" i CLAUDE.md var en engångsgranskning, inte ett skript). Lägg till `@axe-core/playwright` som devDependency (`npm install -D @axe-core/playwright`) och `tests/a11y.spec.ts`:

```ts
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const path = process.env.AXE_PATH ?? 'sv/arter/';

test(`axe: /${path}`, async ({ page }) => {
  await page.goto(`/${path}`);
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
});
```

```json
    "test:a11y": "playwright test tests/a11y.spec.ts",
```

Kör mot en adress: `AXE_PATH="sv/arter/talgoxe/" PLAYWRIGHT_PORT=4327 npm run test:a11y`. Fel: rätta komponenten (samma regel som Step 7 nedan för SEO).

- [ ] **Step 7: Kör**

Run: `npm run verify:fixtures`
Expected: `check-seo OK (... sidor, ... artsidor, ... adresser i sitemapen)`, enhetstesterna, vakterna och `check-preview-build OK (...)` gröna.

Failar en regel på en **befintlig** sida (en h1, alt, döda länkar) är det ett riktigt SEO-fel: rätta sidan i samma task och skriv i commit-meddelandet vilken sida det gällde. Failar en regel på de nya sidorna: rätta komponenten, inte skriptet.

Run: `npm run build:fixtures && node scripts/check-seo.mjs && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS (`dist/` är testdatans vanliga bygge igen efter förhandsbygget, som skrev till `dist-preview/`).

- [ ] **Step 8: Commit**

```bash
git add src/lib/species-sitemap.mjs astro.config.mjs scripts/check-seo.mjs scripts/check-preview-build.mjs scripts/check-no-dashes.mjs tests/a11y.spec.ts package.json package-lock.json
git commit -m "feat(website): sitemap för artsidorna, SEO-reglerna som kod, axe-kontroll och kontroll av förhandsbygget"
```

---

### Task 15: Full QA på testdatan

- [ ] **Step 1: Alla vakter och tester**

Run: `npm run verify:fixtures && npx astro check && npm run test:no-accuracy && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: allt grönt (utom det kända typfelet i `astro.config.mjs`).

- [ ] **Step 2: Skärmdumpar av testdatan (för ögat, committas inte)**

Starta `npm run preview -- --port 4327` i bakgrunden och kör:

```bash
mkdir -p ../.superpowers/artsidor-testdata
for page in "sv/arter/" "sv/arter/ugglor/" "sv/arter/talgoxe/" "sv/arter/parluggla/" "sv/arter/blames-eller-talgoxe/" "sv/arter/om-artsidorna/" "species/great-tit/"; do
  name=$(echo "$page" | tr '/' '-' | sed 's/-$//')
  npx playwright screenshot --viewport-size=390,844 --full-page "http://localhost:4327/$page" "../.superpowers/artsidor-testdata/$name-390.png"
  npx playwright screenshot --viewport-size=1440,900 --full-page "http://localhost:4327/$page" "../.superpowers/artsidor-testdata/$name-1440.png"
done
```

Kontrollera i bilderna:
- artsidan på dator: vänsterspalten med foto, fakta, approta och marginalanteckning; högerspalten med texterna, spelaren under Läte, diagrammet och kartan bredvid varandra under Var och när, förväxlingsarten och credits
- artsidan i mobil: diagram och karta under varandra, ingen sidledsscroll
- jämförelsesidan: två kolumner på dator, en i mobil, tabellen läsbar i 390 px
- inget mossgrönt någonstans (bara rost, persika, aprikos, mässing, marinblått och espresso)

Avvikelser som inte står under "Avvikelser från specen" rättas och testas om.

- [ ] **Step 3: Pusha grenen**

```bash
git push -u origin website/artsidor
```

- [ ] **Step 4: Slå ihop koden till `main` (ändrat 2026-10-05 (b))**

Riktig artdata går från nu på direkt till `main`, en art i taget (avsnitt 14, Task 16 och framåt). Koden måste alltså vara på `main` innan den första riktiga arten publiceras, annars finns inga mallar att rendera den med. **Produktionsbygget går igenom med noll publicerade arter** (ändrat 2026-10-07, Task 4:s fixvåg): `commonSpecies()` returnerar bara de vanliga arter som har en sida, sidfoten döljer raden när den är tom, och poster som inte byggs valideras bara mot kuvertet (id, status, namn, adresser). Vercels förhandsbygge av grenen ska alltså vara grönt redan före sammanslagningen; failar det är det ett fel, inte väntat.

```bash
git fetch origin && git merge origin/main
npm ci && npm run verify:fixtures && npm run build:prod && npx astro check && npm run test:no-accuracy && PLAYWRIGHT_PORT=4327 npx playwright test
git push
```

`npm run build:prod` bygger i riktigt läge mot `src/data/`, där ingen art är publicerad än: det ska gå igenom utan fel.

Sedan i huvudmappen för `main` (worktreen kan inte byta till `main`):

```bash
git switch main && git pull && git merge --ff-only website/artsidor && git push
```

Går `--ff-only` inte: ta in `main` i grenen igen, kör om verifieringen och försök på nytt. När Vercel har byggt produktion: sidorna under `/sv/arter/` och `/species/` svarar ännu bara med `noindex`-sidor eller 404 (ingen art har `publish: true` än), det är väntat tills Task 16.

---

### Task 16: Publiceringsloopen, en art eller jämförelse i taget (ändrat 2026-10-05 (b), var tidigare "Riktig data för våg 1" och "Förhandsvisning, Albins godkännande och go-live för våg 1" som två separata tasks)

**Tillägg (2026-10-06, Task 23-granskningen, uppdaterad efter omgranskningen samma dag):** `web publish`s eget urval (statusen `ok` + `verification`) var svagare än kommandots verkliga publiceringsvillkor (faktabladet fortfarande kontrollerat mot de nuvarande fakta, texten skriven ur dem, inga strukna citerade fakta) -- en kandidat som Step 2 nedan trodde var klar kunde falla på riktigt och blockera hela kön bakom sig. `publish-next.mjs` anropar därför **`uv run birdy-fetcher web publish --next`** istället för att själv välja kandidat (Step 3 punkt 2 nedan är ersatt: `web publish --next` gör urvalet, i samma könordning, och publicerar högst en post, eller skriver `none` på stdout om inget är klart). Uteslutna poster står i en sessionsfil **`reports/publish-loop-excluded.txt`** (en QID eller jämförelsestam per rad): `publish-next.mjs` läser filen vid start (en saknad fil behandlas som tom, inget fel) och skickar varje rad som `--exclude`, och lägger till en ny rad **efter en posts FÖRSTA fel** (inte tre) så att en enskild dålig sida aldrig blockerar de andra; `publish-loop.sh` tömmer filen vid start av en ny körning (en gammal uteslutning ska inte överleva till nästa dag). Loopens egna stopp ("tre fel i rad", se skriptet nedan) gäller fortfarande, men nu uttryckligen tre OLIKA poster i rad (en enskild post kan aldrig bidra med mer än ett fel, den är redan utesluten efter det); det stoppet är till för systematiska fel (trasig byggmiljö och liknande), inte för enstaka dåliga sidor. Skriver `--next` `none` avslutar `publish-next.mjs` med en egen kod **3** (skild från 0 = publicerad och 1 = fel); loopen tolkar kod 3 som "stoppa nu" och räknar det inte som en publicering (ingen sömn, ingen ökning av räknaren). Steg 7 (`git add`) gäller fortfarande bara filen/filerna för just den post `--next` skrev ut. Stickprovets republiceringsväg (Step 4) är **`uv run birdy-fetcher web write --species X --max-cost 2`**, sedan **`uv run birdy-fetcher web compare --max-cost 5`** (samma `--top` som tidigare -- aktuella par hoppas över utan kostnad, par utanför `--top` flaggas av `compare`s egen sweep och behöver `publish: false` av Albin), sedan **`uv run birdy-fetcher web publish --species X`** (som även rapporterar om X:s redan publicerade jämförelser har blivit inaktuella); committa de omskrivna jämförelsefilerna tillsammans med X.

**Tillägg (2026-10-06, fas 1b:s slutgranskning I7 och I8):** (a) **Steg 6 återställer bara den publicerade posten.** `git checkout -- src/data` raderade ocommittade ändringar för andra arter (ett stickprovs `spotChecked`, en import); kör i stället `git checkout -- src/data/species/<QID>.json` för en art eller `git checkout -- src/data/comparisons/<STEM>.json` för en jämförelse, alltså exakt filen `--next` skrev ut. (b) **Stickprov och import får egna commits.** Efter `web spot-check` (när något drogs): committa de dragna arternas JSON, `../tools/content-pipeline/review/stickprov.csv` och `../tools/content-pipeline/review/stickprov-state.json` som `data(artsidor): stickprov, dragning N`. Efter `web import`: committa de ändrade arternas JSON och borttagna `voice.mp3` som `data(artsidor): stickprovsbeslut {datum}` (eller `undantagsbeslut`). Steg 7 committar fortfarande bara den publicerade postens filer. (c) **`web import` säger vilka sidor som ska publiceras om.** Efter importen listar kommandot varje publicerad art som inte längre är klar ("Publicerad men inte längre klar"), med skälen och de exakta kommandona: normalt `uv run birdy-fetcher web write --species X --max-cost 2`, `uv run birdy-fetcher web compare --max-cost 5`, `uv run birdy-fetcher web publish --species X`; när faktabladet väntar på ett beslut (en status Albin satte i stickprovet som rapportdatan motsäger) står `"publish": false` först, eftersom zod-schemat inte bygger en publicerad art utan `verification`. Finns någon sådan art avslutar `web import` med kod 1. En publicerad art som importen ändrade men som fortfarande är klar (till exempel en struken inspelning) listas som "Publicerad sida ändrad men klar": bygg, testa och pusha om den som i Step 3 punkt 5 och 7. (d) **Streckvakten.** `flags` ligger nu i `SKIP_TOP` (koden i Task 14 Step 5 ovan är ändrad); pipelinen tvättar dessutom själv bort streck som vakten vägrar (em-streck, en-streck med mellanslag runt, `--`) ur `marginalia`, upphovspersoner för foton och inspelningar och flaggornas meddelanden (de blir komma; ett streck mellan två tal blir bindestreck). Koboltmesens godkända marginalia från appen hade em-streck och hade annars fällt `npm run verify` för varje sida.

**Tillägg (2026-10-06, uppföljning efter fas 1b:s våg B, Albins beslut):** (a) **En fil per dragning.** `web spot-check` skriver `review/stickprov-dragning-N.csv` i stället för att lägga till i `review/stickprov.csv`; committa den filen (inte `stickprov.csv`) med tillståndsfilen och de dragna arternas JSON, och ladda upp den som ett eget ark eller en egen flik. Importera med `web import --file review/stickprov-dragning-N.csv` efter att ha laddat ner just den fliken som CSV över filen. (b) **Artraden.** Varje art i dragningen inleds med en rad med Typ `art`: `behåll` där räcker när allt stämmer; ett beslut på en enskild rad gäller före. (c) **En status som rapportdatan motsäger avvisas på en publicerad art.** Importen stannar med "sätt publish: false först (eller välj en annan status)" i stället för att lämna en publicerad art utan `verification` (det fällde hela bygget). Avpublicera arten (commit + push), importera igen, och låt sedan undantagsarket och loopen ta den. (d) **Föräldralösa inspelningar.** `web import` (och `web sources`, `web verify`) tar bort varje `voice.mp3` vars art saknar `audio`; går en fil inte att ta bort avslutar `web import` med kod 1 och namnger filen. Ta bort den för hand innan något committas, så att en inspelning aldrig hamnar på sajten utan creditrad.

**Villkor:** Task 15 Steg 4 har slagit ihop kodgrenen till `main`. Fas 1b har skrivit minst en arts text till `main` (`status: "ok"`, `verification` satt). Körs i huvudklonen (`C:\Users\abbea\dev\1-mina-projekt\birdy\website`), inte i worktreen `website/artsidor`, som bara behövdes för kodarbetet i Task 1 till 15: all riktig artdata går nu direkt mot `main`.

- [ ] **Step 1: `SPECIES_PREVIEW=1` i Vercels miljö Preview (valfritt, för kod-förhandsvisningar, inte en release-grind längre)**

Vercel-projektet ligger i teamet `loop-lead-ab` (Albit AB). I `website/`:

```bash
vercel link --yes --scope loop-lead-ab --project birdy
printf '1' | vercel env add SPECIES_PREVIEW preview --scope loop-lead-ab
vercel env ls preview --scope loop-lead-ab | grep SPECIES_PREVIEW
```

Expected: `SPECIES_PREVIEW` finns för Preview. (Saknas Vercel CLI eller inloggning: be Albin lägga in variabeln i Vercel, Settings → Environment Variables, värde `1`, bara Preview.) `SPECIES_FIXTURES` får aldrig sättas på Vercel. Variabeln används bara om en senare kodändring behöver förhandsgranskas mot kontrollerad-men-opublicerad data; ingen art väntar på den.

- [ ] **Step 2: Kontrollera Vercel-projektets byggkvot**

Takten nedan (cirka en push var 5 minuter, ~230 pushar totalt) kräver att planen tillåter det. Kontrollera i Vercels dashboard (Settings → General, teamet `loop-lead-ab`) om projektet är på Hobby (100 bygg per dag) eller Pro (betydligt fler). Hobby räcker, men sprider körningen över flera dagar (cirka 230 bygg / 100 om dagen ≈ 3 dagar); det är okej, bara loopen (Step 3) kan köras i flera omgångar.

- [ ] **Step 3: Publiceringsloopen**

Skapa (med SDD, enligt plan-skelettets vanliga TDD-mönster) `scripts/publish-next.mjs`, ett skript som körs en gång och publicerar **högst en** art eller jämförelse:

1. Läs `src/data/species/*.json` och `src/data/comparisons/*.json`. Hoppa över allt som redan har `publish: true`.
2. ~~Bland arterna: välj den med lägst `review.wave` ..., som har `status: "ok"` och `verification` satt. Finns ingen sådan art: samma urval bland jämförelser ...~~ **(ersatt, se Tillägget ovan: det egna urvalet är helt borttaget. `web publish --next` väljer själv, med det riktiga publiceringsvillkoret, i `review/waves.json`s egen ordning inom en våg.)**
3. ~~Finns ingen kandidat: skriv "Inget att publicera" och avsluta med kod 0 ...~~ **(ersatt, se Tillägget ovan: `--next` skriver självt `none` på stdout när inget är klart; `publish-next.mjs` avslutar då med kod 3, inte 0, och loopen stoppar i stället för att sova.)**
4. ~~Kör `uv run birdy-fetcher web publish --species <QID>` (art) eller `--species <A> --species <B>` ...~~ **(ersatt, se Tillägget ovan: kör `uv run birdy-fetcher web publish --next --exclude ...` (en rad per uteslutning från sessionsfilen) i `../tools/content-pipeline`; läs dess stdout-rad (`species <QID>`, `comparison <STEM>` eller `none`) för att veta vilket spår, 5 till 7, gäller.)**
5. Bygg produktionsläget (`npm run build:prod`, pinnar `SPECIES_FIXTURES=0 SPECIES_PREVIEW=0` så ett ärvt skalvärde aldrig kan ändra bygget tyst), kör `node scripts/check-seo.mjs`, kör den sidans egna Playwright-test (`PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts -g <slug>` för en art, `tests/comparisons.spec.ts -g <slug>` för en jämförelse) och axe-kontrollen mot samma adress (`AXE_PATH="sv/arter/<slug>/" PLAYWRIGHT_PORT=4327 npm run test:a11y`, Task 14 Step 6b).
6. Något steg i 4 eller 5 failar: ~~`git checkout -- src/data`~~ **(ersatt 2026-10-06, se Tillägget om I7 och I8 ovan: återställ bara den post `--next` skrev ut, `git checkout -- src/data/species/<QID>.json` eller `git checkout -- src/data/comparisons/<STEM>.json`)** (ångra `publish`-ändringen), skriv felet till `reports/publish-loop-<datum>.md`, lägg till den misslyckade postens QID eller jämförelsestam på en ny rad i `reports/publish-loop-excluded.txt` **(tillagt, se Tillägget ovan: efter detta FÖRSTA felet, inte efter tre)**, avsluta med kod 1. Inget annat ändras.
7. Allt grönt: `git add` bara de filer som ändrades för just den här arten eller jämförelsen (dess JSON i `src/data/`, foton och inspelning under `src/assets/species/<QID>/` om de är nya), commit `data(artsidor): {namn} ({QID})` (jämförelse: `data(artsidor): {A} eller {B} ({QID-A}+{QID-B})`), push, avsluta med kod 0. **(Tillägg 2026-10-06: ändringar från `web spot-check` och `web import` committas aldrig här utan i egna commits, se Tillägget om I7 och I8 ovan.)**

Spara loopen som `scripts/publish-loop.sh`:

```bash
#!/usr/bin/env bash
set -u
MAX_PUBLISH=${1:-9999}
mkdir -p reports
: > reports/publish-loop-excluded.txt  # tom sessionsfil vid varje ny körning
published=0
consecutive_failures=0
while [ "$published" -lt "$MAX_PUBLISH" ] && [ "$consecutive_failures" -lt 3 ]; do
  node scripts/publish-next.mjs
  code=$?
  if [ "$code" -eq 0 ]; then
    consecutive_failures=0
    published=$((published + 1))
    [ "$published" -lt "$MAX_PUBLISH" ] && sleep 300
  elif [ "$code" -eq 3 ]; then
    echo "Inget mer att publicera just nu."
    break
  else
    consecutive_failures=$((consecutive_failures + 1))
  fi
done
echo "$published publicerade, $consecutive_failures fel i rad vid stopp."
```

`MAX_PUBLISH` är nödstoppet (`--max-publish N` i spec-språket): kör till exempel `bash scripts/publish-loop.sh 5` för en liten testomgång innan hela kön släpps på. Tre OLIKA poster i rad (kod 1, inte kod 3) stoppar loopen helt -- en enskild dålig post bidrar aldrig med mer än ett fel, den uteslöts redan efter sitt första (se Tillägget ovan); läs `reports/publish-loop-*.md` innan omstart. `website/reports/` (sessionsfilen + loopens egna `.md`-rapporter) är lokalt arbetsmaterial, inte artdata -- lägg till raden `reports/` i `website/.gitignore` som en del av den här uppgiften, annars dyker filerna upp som ospårade i varje `git status`.

- [ ] **Step 4: Stickprovet var 40:e art och var 10:e jämförelse**

**Tillägg (2026-10-06, fas 1b:s slutgranskning C2 och I1):** `web spot-check` räknar nu per dragning i stället för att räkna alla obeslutade publicerade arter (det gamla villkoret drog om vid varannan publicering när de första 40 var nådda: 142 av 180 arter i stället för 8). `review/stickprov-state.json` håller arterna som redan räknats och varje dragning (id, frö, datum, arter); 2 arter dras per 40 arter publicerade sedan förra dragningen, en rest väntar till nästa hela 40. Raderna i `review/stickprov.csv` har kolumnen `Dragning` och ett tomt `Beslut` (förut förifyllt `behåll`, så en import innan Albin tittat räknades som kontrollerad); Albin skriver `behåll`, `stryk` eller `ändra` på varje faktarad och `behåll` eller `stryk` på inspelningsraden. `web import --file review/stickprov.csv` tillämpar bara artens öppna dragning, hoppar över äldre och redan avgjorda dragningar (arket är löpande och kan importeras om utan att äldre sidor får nytt datum), låter en art med tomma beslut vänta, och sätter nytt kontrolldatum bara när något ströks eller ändrades (ett `stryk` på inspelningen tar bort `audio` och `voice.mp3`). Har faktabladet ändrats sedan dragningen tillämpas inget; kör `web spot-check --extra <QID>` för en ny dragning. Efter en dragning: committa `review/stickprov-state.json`, `review/stickprov.csv` och de dragna arternas JSON i en egen commit (`data(artsidor): stickprov, dragning N`), aldrig tillsammans med en publicering. **Jämförelsernas stickprov (1 per 10) är fortfarande inte kodat.** När det byggs ska det följa samma design: en egen räknare i tillståndsfilen för jämförelser publicerade sedan förra dragningen (inte "alla obeslutade publicerade"), en `Dragning`-kolumn och ett tomt `Beslut`. Fas 1b-planens formulering "samma `SPOT_CHECK_BATCH`-mönster men batch 10 och drag 1" är ersatt av detta.

Efter varje lyckad publicering i loopen, kör `cd ../tools/content-pipeline && uv run birdy-fetcher web spot-check && cd ../../website`. Skriver den något (se fas 1b Task 16): ladda upp ~~`review/stickprov.csv`~~ **dragningens egen fil `review/stickprov-dragning-N.csv` som ett nytt ark eller en ny flik (ersatt 2026-10-06, se Tillägget om uppföljningen nedan)** till Albins Drive, samma instruktion som undantagsarket (fas 1b R5). Ett bekräftat fel: `uv run birdy-fetcher web import --file review/stickprov.csv`, ~~sedan kör loopen (Step 3) på nytt för just den arten~~ **(ersatt, se Tillägget ovan)** sedan, i `../tools/content-pipeline`: `uv run birdy-fetcher web write --species X --max-cost 2`, `uv run birdy-fetcher web compare --max-cost 5` (samma `--top` som tidigare -- aktuella par kostar inget, par utanför `--top` flaggas av `compare`s egen sweep och behöver `publish: false` av Albin) och `uv run birdy-fetcher web publish --species X`; bygg, testa och pusha om X (och de omskrivna jämförelsefilerna) som i Step 3 punkt 5 och 7 med det nya kontrolldatumet; notera missen i rapporten.

- [ ] **Step 5: Periodisk kontroll, inte per sida**

Lighthouse (mål 90 eller mer på performance, accessibility, best-practices, seo) och en jämförelse mot mockupen (`docs/superpowers/specs/assets/2026-09-25-artsidor/helheten.html`) körs en gång efter de första publicerade sidorna (en art, en jämförelse, ingångssidan) och sedan ungefär var 40:e art, inte vid varje push (230 Lighthouse-körningar är inte värt det). Samma kommandon som tidigare:

```bash
npm run preview -- --port 4327 &
for p in "sv/arter/<en-publicerad-slug>/" "sv/arter/<en-publicerad-jämförelse-slug>/" "sv/arter/"; do
  name=$(echo "$p" | tr '/' '-' | sed 's/-$//')
  npx lighthouse "http://localhost:4327/$p" --form-factor=mobile --screenEmulation.mobile --only-categories=performance,accessibility,best-practices,seo --output=json --output=html --output-path="../docs/superpowers/screenshots/artsidor/lighthouse-$name" --chrome-flags="--headless=new"
done
```

Under 90 i Performance: kontrollera att huvudfotot har `fetchpriority="high"` och att `sizes` stämmer, och sänk `widths` i artkorten.

- [ ] **Step 6: Kontrollera live, efter de första sidorna och sedan stickprovsvis**

```bash
for p in /sv/arter/ /species/ /sv/arter/om-artsidorna/ /sitemap-0.xml; do
  echo "$p $(curl -s -o /dev/null -w '%{http_code}' https://birdy.community$p)"
done
curl -s https://birdy.community/sitemap-0.xml | grep -c '/arter/'
```

Expected: 200 överallt, och sitemapen växer med varje publicerad sida.

- [ ] **Step 7: Search Console, i omgångar (med Albins ok per inskickning)**

Inte efter varje enskild sida. Skicka in `https://birdy.community/sitemap-index.xml` igen och begär indexering av de nypublicerade sidorna ungefär var 20:e till 40:e publicerade art, och alltid efter ingångssidan, de tolv vanliga arterna och den första gruppsidan.

- [ ] **Step 8: Baslinjen (en gång, när den första artens sida är live)**

Hämta Search Console för `sc-domain:birdy.community`, de senaste 3 månaderna (klick, visningar, indexerade sidor), och skriv in dem i raden "Go-live (fas 2)" i `docs/superpowers/research/2026-09-30-artsidor-baslinje.md` med dagens datum. Committa och pusha filen direkt på `main`.

- [ ] **Step 9: Utkast till utskick för länkar (när våg 1:s arter är publicerade)**

Skriv `docs/marketing/2026-artsidor-utskick.md` med utkasten nedan. Fyll i antalet publicerade arter och jämförelser (`grep -l '"publish": true' src/data/species/*.json | wc -l` och samma för `src/data/comparisons/`). Albin skickar dem i eget namn; agenten skickar inget.

```markdown
# Utskick för artsidorna, våg 1

Albin skickar. Länka alltid till ingångssidan eller en jämförelsesida, aldrig till appen direkt.

## birdforum.net (engelska)

**Title:** Free field guide pages for common Swedish winter and garden birds

Hi all, I build Birdy, a small bird ID app from Sweden. We have just published field guide pages for {antal arter} of the most common winter and garden birds in Sweden and Europe: field marks, calls with recordings, when each species is reported in Sweden month by month (from Artportalen data via GBIF), and side by side comparisons of species that are easy to mix up, like the blue tit and the great tit. Every fact sheet is checked against its sources before publishing, and the texts may be reused under CC BY-SA. Corrections are very welcome. https://birdy.community/species/

## Lokala ornitologiska föreningar (svenska, mejl)

**Ämne:** Gratis artsidor om vinterfåglar till era medlemmar

Hej! Jag heter Albin Abrahamsson och har byggt Birdy, en app för att känna igen fåglar. Inför Vinterfåglar inpå knuten har vi publicerat artsidor om {antal arter} av de vanligaste vinter- och trädgårdsfåglarna, med kännetecken, läten, när arten rapporteras i Sverige månad för månad (ur Artportalen) och jämförelser av arter som är lätta att blanda ihop, som blåmes och talgoxe. Allt är gratis och texterna får delas vidare. Får jag tipsa om sidorna i ert nyhetsbrev eller på er webbplats? https://birdy.community/sv/arter/

Hälsningar, Albin Abrahamsson

## Svenska fågelgrupper på Facebook

Inför Vinterfåglar inpå knuten: vi har gjort gratis artsidor om de vanligaste fåglarna vid fågelbordet, med läten, när de rapporteras i Sverige och jämförelser av arter som är lätta att blanda ihop. Hittar ni ett fel får ni gärna säga till. https://birdy.community/sv/arter/

## Lärare och naturskolor (svenska, mejl)

**Ämne:** Gratis jämförelser av fåglar som är lätta att blanda ihop

Hej! Vi har publicerat {antal jämförelser} jämförelser av fåglar som är lätta att blanda ihop, till exempel blåmes och talgoxe, med foton, läten och en tabell över skillnaderna. De passar bra inför en fågelräkning med klassen och får användas fritt i undervisningen. https://birdy.community/sv/arter/

Hälsningar, Albin Abrahamsson

## AlbIT-caset

Skicka adresserna `https://birdy.community/sv/arter/` och `https://birdy.community/species/` till albit.se-sessionen, tillsammans med Lighthouse-resultaten i `docs/superpowers/screenshots/artsidor/`.
```

Committa och pusha filen direkt på `main` när våg 1:s arter är publicerade.

- [ ] **Step 10: Synka status (löpande, inte bara vid en vågs slut)**

Uppdatera 🔎-posten om artsidorna i CLAUDE.md efter hand:
- antal publicerade artsidor och jämförelser, uppdaterat ungefär var 40:e art
- Lighthouse-resultaten
- baslinjefilen och triggrarna: kontroll efter 6 veckor (datum) och 12 veckor (datum) enligt spec §15, räknat från den första artens go-live (Step 8)
- att `SPECIES_PREVIEW=1` ligger i Vercels miljö Preview
- att egna besök filtreras med `?va-ignore=1` (Albin öppnar `https://birdy.community/?va-ignore=1` en gång per webbläsare och enhet)

Committa och pusha. Ta bort worktreen när koden är stabil och inget mer väntas i den: `git worktree remove C:/w/birdy-artsidor`.

**Löpande drift, våg 2 och 3 (ändrat 2026-10-05 (b), var tidigare en egen Task 18 med en ny förhandsgren per våg):** loopen i Step 3 fortsätter automatiskt genom hela kön, oavsett vågnummer, så länge fas 1b fortsätter skriva fler arters `status: "ok"` + `verification` till `main` (fas 1b R2 till R9). Det finns ingen ny gren, ingen ny förhandsvisning och inget nytt godkännande per våg. Det som återkommer per våg är fas 1b:s R5 (Albin beslutar om undantagsarkets flaggor när nästa vågs arter har körts genom kontrollen) och stickprovet i Step 4 ovan. Efter 6 och 12 veckor sedan en vågs första sida gick live: skriv in mätvärdena i baslinjefilen och följ triggrarna i spec §15 innan nästa våg.
