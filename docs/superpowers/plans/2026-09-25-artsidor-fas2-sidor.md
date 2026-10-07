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
2. **Approtan, spelarens etikett och textcrediten nämner inte artens namn i löptext.** "Birdy känner igen talgoxe på foto" blir fel böjning på svenska och datan har inte bestämd form. Approtan säger "arten" / "this species" i fyra varianter efter `identifiable`, spelarens etikett är "Inspelning: Talgoxe", och textcrediten är "Texten är skriven med AI ur fakta från Wikipedia och får delas under CC BY-SA 4.0. Källor: svenska artikeln, engelska artikeln, tyska artikeln" (ändrat 2026-10-07, controller-granskning: AI-märkning tillagd, samma beslut som appens `profile_text_credit`, var "Texten bygger på Wikipedia och får delas under CC BY-SA 4.0. ...") med länkar till revisionerna. Kontrolleras i Task 15:s testdata-skärmdumpar (ändrat 2026-10-05 (b): ingen förhandsvisning av riktig data att läsa igenom längre).
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
| `tests/fixtures/make-species-fixtures.mjs` | Skriver testdatan (21 arter, 4 jämförelser, testbilder, tysta mp3; uppdaterat 2026-10-07: Task 9s granskning lade till Blåkråka, en frånvarande art, som Task 10 publicerade). |
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
  { qid: 'Q25234', sv: 'Koltrast', en: 'Common Blackbird', sci: 'Turdus merula', fam: ['Turdidae', 'Trastar'], group: 'songbirds', slug: ['koltrast', 'common-blackbird'], iucn: 'LC', red: 'not_listed', id: [true, true], extra: true, extraPd: true, audio: 'trimmed', de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25334', sv: 'Rödhake', en: 'European Robin', sci: 'Erithacus rubecula', fam: ['Muscicapidae', 'Flugsnappare'], group: 'songbirds', slug: ['rodhake', 'european-robin'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q14683', sv: 'Gråsparv', en: 'House Sparrow', sci: 'Passer domesticus', fam: ['Passeridae', 'Sparvfinkar'], group: 'songbirds', slug: ['grasparv', 'house-sparrow'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25307', sv: 'Skata', en: 'Eurasian Magpie', sci: 'Pica pica', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['skata', 'eurasian-magpie'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25345384'] },
  { qid: 'Q25345384', sv: 'Kaja', en: 'Western Jackdaw', sci: 'Coloeus monedula', fam: ['Corvidae', 'Kråkfåglar'], group: 'songbirds', slug: ['kaja', 'western-jackdaw'], iucn: 'NE', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25307'] },
  { qid: 'Q25383', sv: 'Bofink', en: 'Eurasian Chaffinch', sci: 'Fringilla coelebs', fam: ['Fringillidae', 'Finkar'], group: 'songbirds', slug: ['bofink', 'eurasian-chaffinch'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25348', sv: 'Gräsand', en: 'Mallard', sci: 'Anas platyrhynchos', fam: ['Anatidae', 'Egentliga andfåglar'], group: 'waterfowl', slug: ['grasand', 'mallard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q26427', sv: 'Fiskmås', en: 'Common Gull', sci: 'Larus canus', fam: ['Laridae', 'Måsfåglar'], group: 'gulls_terns', slug: ['fiskmas', 'common-gull'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q25385', sv: 'Ormvråk', en: 'Common Buzzard', sci: 'Buteo buteo', fam: ['Accipitridae', 'Hökar'], group: 'raptors', slug: ['ormvrak', 'common-buzzard'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident' },
  { qid: 'Q4764', sv: 'Trana', en: 'Common Crane', sci: 'Grus grus', fam: ['Gruidae', 'Tranor'], group: 'cranes_rails', slug: ['trana', 'common-crane'], iucn: 'LC', red: 'not_listed', id: [true, false], de: true, months: SUMMER, status: 'breeding_migrant' },
  { qid: 'Q25756', sv: 'Kattuggla', en: 'Tawny Owl', sci: 'Strix aluco', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['kattuggla', 'tawny-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], audio: 'full', audioPd: true, de: true, months: YEAR_ROUND, status: 'resident', look: ['Strix uralensis'] },
  { qid: 'Q25384', sv: 'Hornuggla', en: 'Long-eared Owl', sci: 'Asio otus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['hornuggla', 'long-eared-owl'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', look: ['Q25769'] },
  // Minimal record: no audio, no report data, no extra photo, no behaviour or look-alikes, no size,
  // status or Swedish red list, Swedish article only.
  { qid: 'Q174466', sv: 'Pärluggla', en: 'Boreal Owl', sci: 'Aegolius funereus', fam: ['Strigidae', 'Egentliga ugglor'], group: 'owls', slug: ['parluggla', 'boreal-owl'], iucn: 'LC', red: null, id: [false, false], minimal: true },
  // The only seabird, so its group page gets noindex.
  { qid: 'Q25440', sv: 'Storskarv', en: 'Great Cormorant', sci: 'Phalacrocorax carbo', fam: ['Phalacrocoracidae', 'Skarvar'], group: 'seabirds', slug: ['storskarv', 'great-cormorant'], iucn: 'LC', red: 'not_listed', id: [false, true], de: true, months: YEAR_ROUND, status: 'resident' },
  // Reviewed and written but not published: only preview builds (SPECIES_PREVIEW=1) show them.
  { qid: 'Q26209', sv: 'Större hackspett', en: 'Great Spotted Woodpecker', sci: 'Dendrocopos major', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['storre-hackspett', 'great-spotted-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q210418'] },
  { qid: 'Q210418', sv: 'Tretåig hackspett', en: 'Eurasian Three-toed Woodpecker', sci: 'Picoides tridactylus', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['tretaig-hackspett', 'eurasian-three-toed-woodpecker'], iucn: 'LC', red: 'NT', id: [true, true], de: true, months: YEAR_ROUND, status: 'resident', publish: false, look: ['Q26209'] },
  // Absent in Sweden (controller review, Task 9): `data` exists (totalReports below the 200-report
  // threshold, spec §9.2, plus the presence sentence that becomes a written fact), but no months/counties
  // (so no chart or map) and no swedishRedList (a species with too few reports and no red-list entry is
  // not assessed, spec Revision 2026-10-07). Exercises `reportData={Boolean(s.data)}` (SpeciesArticle,
  // Task 10): the data credit must still show even though months/counties are both missing. Published
  // since Task 10 (was `publish: false` until then), so the absent-species page is built and tested like
  // the real ones (Koboltmes, Q10546857, is absent and written): it makes `other` an active group with one
  // species, so Task 7's and Task 8's counts went up by one consciously (hub `.groups a` 7 to 8, hub
  // `[data-item]` 16 to 17, a group page's `.catbar .chip` 8 to 9). Its look-alike is Större hackspett,
  // a species with a record but no page in the normal build (same as Koboltmes' look-alike Blåmes in the
  // real data): the name shows without a link, a photo or a comparison link.
  { qid: 'Q25411', sv: 'Blåkråka', en: 'European Roller', sci: 'Coracias garrulus', fam: ['Coraciidae', 'Blåkråkor'], group: 'other', slug: ['blakraka', 'european-roller'], iucn: 'LC', id: [false, false], de: true, status: 'absent', absent: true, look: ['Q26209'] },
  // Never a page: one failed, one pending (facts exist, text not written yet).
  { qid: 'Q166171', sv: 'Gröngöling', en: 'European Green Woodpecker', sci: 'Picus viridis', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['grongoling', 'european-green-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'failed' },
  { qid: 'Q143284', sv: 'Spillkråka', en: 'Black Woodpecker', sci: 'Dryocopus martius', fam: ['Picidae', 'Hackspettar'], group: 'woodpeckers', slug: ['spillkraka', 'black-woodpecker'], iucn: 'LC', red: 'not_listed', id: [true, true], recordStatus: 'pending' },
];

// extraPd (Koltrast's extra photo) and audioPd (Kattuggla's recording, no recordist) carry the pipeline's
// canonical "Public domain" without a licence link, so the credit lines' "public domain" label and the
// unknown-recordist fallback are tested on built pages (Task 9 re-review).
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
          sv: ['Rapporteras mest i maj.', 'Rapporteras sällan i november till mars.', 'Andelen av alla fågelrapporter är högst i Testlän, Provlän och Exempellän.'],
          en: ['Reported most in May.', 'Rarely reported in November to March.', 'Its share of all bird reports is highest in Testshire, Sampleshire and Exampleshire.'],
        }
      : {
          sv: ['Rapporteras året runt.', 'Rapporteras från alla 21 län.'],
          en: ['Reported all year round.', 'Reported from all 21 counties.'],
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
      ...(sp.extra ? [{
        role: 'extra', file: `${sp.qid}/extra.webp`, width: 1200, height: 800, author: 'Testfotograf två',
        ...(sp.extraPd ? { license: 'Public domain', licenseUrl: null } : { license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/' }),
        sourceUrl: commons('extra.jpg'),
      }] : []),
    ],
    ...(sp.audio ? {
      audio: {
        file: `${sp.qid}/voice.mp3`, durationSec: 1, trimmed: sp.audio === 'trimmed',
        ...(sp.audioPd
          ? { author: null, license: 'Public domain', licenseUrl: null }
          : { author: 'Testinspelare', license: 'CC BY-SA 4.0', licenseUrl: 'https://creativecommons.org/licenses/by-sa/4.0/' }),
        sourceUrl: commons('song.ogg'),
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

**Avvikelse vid genomförandet (Task 8:s granskning 2026-10-07):** Kaja:s (`Q25345384`) `fam[1]` i den faktiska generatorn är `'Kråkor'`, inte `'Kråkfåglar'` som i koden ovan, så att Kaja och Skata (båda `Corvidae` i latin) deliberat har OLIKA `family.sv` i testdatan. Det är en avsiktlig divergens, inte ett misstag: gruppsidan (Task 8) grupperar efter `family.latin` som ett skyddsnät, och fixturen behöver testa att skyddsnätet fungerar även om `family.sv` skulle skilja sig. Den verkliga pipelinen garanterar numera ett enda svenskt familjenamn per latinsk familj (`web/families.py`, BirdLife Sveriges NL20), så den här situationen uppstår inte i riktig data, men skyddsnätet (och testet av det) behålls ändå.

**Avvikelse vid genomförandet (controller-granskning 2026-10-07, Task 9):** `dataFor()`s mallmeningar ovan är uppdaterade till pipelinens faktiska ordval (`web/datamod.py`, fix wave 2026-10-07, efter att den här planen skrevs): "Rapporteras sällan i …" i stället för "Nästan aldrig i …", och "Rapporteras från alla 21 län."/"Andelen av alla fågelrapporter är högst i …" i stället för "Vanligast i rapporterna från …" (se spec §9.2, synkad samma dag). `SPECIES`-arrayen har dessutom en 21:a post, Blåkråka (`Q25411`, `absent: true`): `dataFor()` har en tidig retur för `sp.absent` som ger `data` utan `months`/`counties`, bara `totalReports: 0` och förekomstmeningen ("Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025." / samma på engelska), en fixture för `reportData={Boolean(s.data)}` (Task 10s tillägg). Posten var `publish: false` fram till Task 10, som publicerade den och höjde Task 7:s och 8:s räkningar med en (se Task 10:s avvikelser). Sedan Task 9:s omgranskning har dessutom Koltrastens extrafoto (`extraPd`) och Kattugglans inspelning (`audioPd`, utan upphovsperson) licensen "Public domain" utan länk, så att "public domain" och "okänd upphovsperson" testas på byggda sidor. `...(sp.months ? { data: dataFor(sp) } : {})` i `record()` nedan är därför `...(sp.months || sp.absent ? { data: dataFor(sp) } : {})` i den faktiska generatorn.

- [ ] **Step 7: Kör generatorn**

Run: `node tests/fixtures/make-species-fixtures.mjs && ls tests/fixtures/species | wc -l && ls tests/fixtures/comparisons && ls tests/fixtures/species-assets/Q25485`
Expected (uppdaterat 2026-10-07, Task 9s granskning, 20 → 21 arter): `fixtures: 21 arter och 4 jämförelser i tests/fixtures/`, `21`, fyra jämförelsefiler (`Q210418_Q26209.json Q25307_Q25345384.json Q25384_Q25756.json Q25404_Q25485.json`) och `extra.webp hero.webp voice.mp3`. Totalt cirka 350 KB.

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

/**
 * The comparison pages are built in Task 11, which comes after the first species pages go live (2026-10-09).
 * Until their route exists no page may link to one, so getComparisons() returns nothing: the hub hides its
 * comparison section and the look-alikes get no "Compare" link. Task 11 flips this to true (controller
 * decision, Task 10 review; added to this block afterwards).
 */
export const COMPARISONS_ENABLED = false;

let builtComparisons: Comparison[] | undefined;
/** The comparisons that get a page: written, published or previewed, and both species built. */
export async function getComparisons(): Promise<Comparison[]> {
  if (!COMPARISONS_ENABLED) return [];
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

/** The Creative Commons deed localized to the page's language (controller review, Task 9): the data
 * always stores the canonical (English) deed URL (pipeline's LICENSE_URLS), the website appends
 * `deed.sv` for Swedish pages only. JSON-LD (SpeciesArticle's `license`/`acquireLicensePage`) keeps the
 * canonical URL untouched, this is for the human-readable credit links only. */
export function localizedLicenseUrl(url: string, locale: Locale): string {
  return locale === 'sv' && url.startsWith('https://creativecommons.org/licenses/') ? `${url}deed.sv` : url;
}

/** The credit text for one licence value. The pipeline's canonical spelling for an unattributed licence
 * is "Public domain" (capital P, `canonical_license()`); the app shows it lowercase in both languages
 * ("public domain", `photo_credits_public_domain` in strings.xml) instead of translating it, and the
 * website matches that (controller review, Task 9). Every other licence (CC0, CC BY …) is shown as the
 * pipeline wrote it. */
export function licenseLabel(license: string, t: Copy): string {
  return license === 'Public domain' ? t.species.licensePublicDomain : license;
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
    "hubLead": "{n} vanliga fåglar med foton, kännetecken och läten. Alla finns också i appens uppslagsverk, och med appen kan du känna igen fåglar på plats.",
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
    "mapLegend": ["Inga rapporter", "Liten andel", "Mellanstor andel", "Stor andel"],
    "monthLetters": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"],
    "monthNames": ["januari", "februari", "mars", "april", "maj", "juni", "juli", "augusti", "september", "oktober", "november", "december"],
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
    "unknownRecordist": "okänd upphovsperson",
    "edited": "bearbetad",
    "trimmedEdited": "klippt och bearbetad",
    "resized": "nedskalad",
    "licensePublicDomain": "public domain",
    "textCredit": "Texten är skriven med AI ur fakta från Wikipedia och får delas under",
    "sources": "Källor",
    "articleSv": "svenska artikeln",
    "articleEn": "engelska artikeln",
    "articleDe": "tyska artikeln",
    "dataCreditReports": "Rapportdata: Artportalen (SLU Artdatabanken) via {gbif}, 2016 till 2025.",
    "dataCreditRedList": "Rödlista: Rödlistade arter i Sverige 2025, SLU Artdatabanken via {gbif}.",
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
    "title": "Så gör vi artsidorna: källor och kontroll | Birdy",
    "description": "Så skrivs Birdys artsidor: källorna, hur AI används, hur fakta kontrolleras, vilka licenser som gäller och hur du rapporterar fel.",
    "crumb": "Så gör vi artsidorna",
    "headline": "Så gör vi *artsidorna*",
    "lead": "Varje artsida bygger på flera källor och kontrolleras i flera steg innan den publiceras. Det mesta sköts automatiskt, och det som sticker ut avgörs av en människa. Så här går det till.",
    "sections": [
      {
        "heading": "Källorna",
        "paragraphs": [
          "Texterna bygger på Wikipedias artiklar om arten på svenska, engelska och tyska, i den mån arten har en artikel på språket.",
          "Diagrammet över när arten ses och kartan över var den rapporteras räknas fram ur fågelrapporterna i Artportalen 2016 till 2025. Artportalen är Sveriges rapportsystem för fynd av växter, djur och svampar, och SLU Artdatabanken delar rapporterna öppet via den internationella databasen GBIF. Siffrorna visar artens andel av alla fågelrapporter, så att arten inte ser vanligare ut i en viss månad eller ett visst län bara för att fler är ute och skådar där och då. Har arten färre än 200 rapporter visas varken diagram eller karta.",
          "Den svenska rödlistestatusen kommer från SLU Artdatabankens Rödlistade arter i Sverige 2025, också via GBIF, och den globala från IUCN:s rödlista via Wikidata. Foton och inspelningar kommer från Wikimedia Commons."
        ]
      },
      {
        "heading": "Så används AI",
        "paragraphs": [
          "Språkmodellen Claude Opus 5.5 från Anthropic läser artiklarna och plockar ut fakta om bland annat utseende, läte, miljö och förekomst i Sverige. Varje faktum ska ha ett ordagrant citat ur någon av artiklarna, och ett program kontrollerar att citatet verkligen finns där. Fakta utan giltigt citat stryks. Blir det fler än 30 behåller programmet 30 och tar dem turvis från ämnena, så att inget ämne trängs ut.",
          "Texten på sidan skrivs sedan av samma modell, som bara får se de fakta som har klarat kontrollen nedan, inte artiklarna. En annan modell, Claude Sonnet 5, läser därefter varje mening och jämför den med de fakta och citat den bygger på. En mening som inte stöds skrivs om en gång, och stöds den fortfarande inte stryks den.",
          "Diagrammet, kartan och meningarna under dem räknas fram av kod direkt ur datan, och rödlistestatusen hämtas på samma sätt, utan någon språkmodell."
        ]
      },
      {
        "heading": "Kontrollen",
        "paragraphs": [
          "Innan texten skrivs kontrollerar Claude Sonnet 5, alltså en annan modell än den som plockade ut fakta, varje faktum mot dess citat och stycket runt citatet i artikeln. Fakta som inte stöds fullt ut stryks. Kod jämför dessutom tal som längd, vingbredd, vikt och antal ägg mellan artiklarna på de olika språken, och artens status i Sverige, till exempel stannfågel eller vintergäst, med rapporterna i Artportalen och den svenska rödlistan.",
          "Inspelningen kontrolleras med ljudmodellen som känner igen läten i Birdy-appen. Känner modellen inte igen arten prövas upp till tre andra inspelningar från Wikimedia Commons, och annars visas ingen inspelning.",
          "En sida där ingen kontroll hittar något publiceras automatiskt. Det som inte går att avgöra automatiskt väntar på ett beslut av Albin Abrahamsson, som har byggt Birdy, innan sidan publiceras: till exempel en status som kontrollen strök, tal som skiljer sig mellan artiklarna, en status som inte stämmer med rapporterna, eller en art som ljudmodellen inte känner till eller bara känner igen svagt. Efter publiceringen läser han dessutom ett löpande stickprov, två av varje 40 publicerade artsidor. Datumet för den senaste kontrollen står längst ned på varje artsida."
        ]
      },
      {
        "heading": "Licenserna",
        "paragraphs": [
          "Texterna på art- och jämförelsesidorna är skrivna med AI ur fakta från Wikipedia och får därför delas under CC BY-SA 4.0, om du anger Birdy och Wikipediaartiklarna som källor och delar vidare under samma licens. Varje sida länkar till artiklarna i den version texten bygger på.",
          "Foton och inspelningar har sina egna licenser, och varje sida anger upphovsperson, licens och källa för dem. Fotona är nedskalade, i övrigt oförändrade. Inspelningarna är bearbetade (högst 20 sekunder, mono, utjämnad ljudnivå, MP3), och en bearbetad inspelning under CC BY-SA delas under samma licens som originalet.",
          "Rapportdatan från Artportalen och den svenska rödlistan är fri att använda under CC0 och hämtas via GBIF. CC0 kräver ingen källhänvisning, men varje sida anger ändå källan."
        ]
      },
      {
        "heading": "Rättelser",
        "paragraphs": [
          "Längst ned på varje artsida finns länken ”Hittade du ett fel? Skriv till oss.” Du kan också skriva direkt till {email}, gärna med en länk till sidan. Stämmer det rättar vi sidan och sätter ett nytt kontrolldatum."
        ]
      }
    ]
  },
```

(Rättat i efterhand, controller-granskning 2026-10-07: `trimmed`/`klippt` bytt mot `edited`/`trimmedEdited` enligt Tillägget nedan i Task 9, `unknownRecordist` bytt till "okänd upphovsperson", rubriken "Granskningen" bytt till "Kontrollen" enligt specens §8, och `hubLead`, `mapLegend[3]` samt alla fem stycken i `speciesAbout.sections` skrivna om för sanningshalt, se motsvarande ändring i Step 2 nedan och i `copy.sv.json`.)

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
    "hubLead": "{n} common birds with photos, field marks and calls. All of them are in the app's field guide too, and with the app you can identify birds on the spot.",
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
    "mapLegend": ["No reports", "Small share", "Medium share", "Large share"],
    "monthLetters": ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"],
    "monthNames": ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"],
    "moreFamily": "More in the {family} family",
    "moreGroup": "More {group}",
    "appHeadline": "Not sure what you are seeing?",
    "appTextBoth": "Birdy identifies this species from a photo or its song, right on your phone and without a signal.",
    "appTextSound": "Birdy identifies this species from its song, right on your phone and without a signal.",
    "appTextPhoto": "Birdy identifies this species from a photo, right on your phone and without a signal.",
    "appTextNone": "Birdy helps you identify the birds around you from photos and songs, right on your phone and without a signal.",
    "plate": "Pl. {n}",
    "photoCredit": "Photo:",
    "via": "via",
    "unknownAuthor": "unknown photographer",
    "recordingLabel": "Recording: {name}",
    "recordingCredit": "Recording:",
    "unknownRecordist": "unknown recordist",
    "edited": "edited",
    "trimmedEdited": "trimmed and edited",
    "resized": "resized",
    "licensePublicDomain": "public domain",
    "textCredit": "The text was written with AI from facts in Wikipedia and may be shared under",
    "sources": "Sources",
    "articleSv": "Swedish article",
    "articleEn": "English article",
    "articleDe": "German article",
    "dataCreditReports": "Report data: Artportalen (SLU Swedish Species Information Centre) via {gbif}, 2016 to 2025.",
    "dataCreditRedList": "Red list: The Swedish Red List 2025, SLU Swedish Species Information Centre via {gbif}.",
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
    "title": "How we make the species pages: sources and checks | Birdy",
    "description": "How the species pages on Birdy are made: the sources, how AI is used, how the facts are checked, the licences and how to report a mistake.",
    "crumb": "How we make these pages",
    "headline": "How we make the *species pages*",
    "lead": "Every species page is built from several sources and checked in several steps before it is published. Most of the checking is automatic, and anything that stands out is decided by a person. This is how it works.",
    "sections": [
      {
        "heading": "The sources",
        "paragraphs": [
          "The texts are based on Wikipedia's articles about the species in Swedish, English and German, as far as the species has an article in that language.",
          "The chart of when the species is seen and the map of where it is reported are calculated from the bird reports in Artportalen from 2016 to 2025. Artportalen is Sweden's reporting system for sightings of plants, animals and fungi, and the SLU Swedish Species Information Centre shares the reports openly through the international database GBIF. The figures show the species' share of all bird reports, so that it does not look more common in a month or a county just because more birdwatchers are out then or there. A species with fewer than 200 reports gets neither a chart nor a map.",
          "The Swedish red list status comes from The Swedish Red List 2025 by the SLU Swedish Species Information Centre, also through GBIF, and the global status from the IUCN Red List via Wikidata. Photos and recordings come from Wikimedia Commons."
        ]
      },
      {
        "heading": "How AI is used",
        "paragraphs": [
          "The language model Claude Opus 5.5 by Anthropic reads the articles and picks out facts about appearance, calls and song, habitat and occurrence in Sweden, among other things. Every fact needs a verbatim quote from one of the articles, and a program checks that the quote really is there. Facts without a valid quote are removed. If more than 30 remain, the program keeps 30, taking them from each topic in turn so that no topic is crowded out.",
          "The text on the page is then written by the same model, which only sees the facts that have passed the checks below, not the articles. Another model, Claude Sonnet 5, then reads every sentence and compares it with the facts and quotes it is based on. A sentence that is not supported is rewritten once, and if it is still not supported it is removed.",
          "The chart, the map and the sentences under them are calculated by code directly from the data, and the red list status is looked up the same way, without any language model."
        ]
      },
      {
        "heading": "The checks",
        "paragraphs": [
          "Before the text is written, Claude Sonnet 5, a different model from the one that picked out the facts, checks every fact against its quote and the paragraph around the quote in the article. Facts that are not fully supported are removed. Code also compares numbers such as length, wingspan, weight and clutch size between the articles in the different languages, and the species' status in Sweden, such as resident or winter visitor, with the reports in Artportalen and the Swedish red list.",
          "The recording is checked with the sound model that identifies calls and songs in the Birdy app. If the model does not recognise the species, up to three other recordings from Wikimedia Commons are tried, and otherwise no recording is shown.",
          "A page where no check finds anything is published automatically. Anything that cannot be settled automatically waits for a decision by Albin Abrahamsson, who built Birdy, before the page is published: for example a status that the checks removed, numbers that differ between the articles, a status that does not match the reports, or a species the sound model does not know or only recognises weakly. After publication, he also reads a running sample, two of every 40 published species pages. The date of the latest check is at the bottom of every species page."
        ]
      },
      {
        "heading": "The licences",
        "paragraphs": [
          "The texts on the species and comparison pages are written with AI from facts in Wikipedia and may therefore be shared under CC BY-SA 4.0, if you credit Birdy and the Wikipedia articles and share under the same licence. Every page links to the articles in the version the text is based on.",
          "Photos and recordings have their own licences, and every page names the author, licence and source for them. The photos are scaled down, otherwise unchanged. The recordings are edited (at most 20 seconds, mono, loudness evened out, MP3), and an edited CC BY-SA recording is shared under the same licence as the original.",
          "The report data from Artportalen and the Swedish red list are free to use under CC0 and come through GBIF. CC0 does not require a credit, but every page names the source anyway."
        ]
      },
      {
        "heading": "Corrections",
        "paragraphs": [
          "At the bottom of every species page there is a link, “Found a mistake? Write to us.” You can also write directly to {email}, ideally with a link to the page. If we confirm the mistake, we correct the page and set a new check date."
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

(Tillägg, controller-granskning 2026-10-07, samma commit som Task 9: `monthNames` (12 fulla månadsnamn, till `MonthChart`s nya per-stapel-titel), `resized` ("nedskalad"/"resized", sist i fotocrediten) och `licensePublicDomain` ("public domain", samma gemener-text på båda språken som appens `photo_credits_public_domain`, aldrig översatt) är tre nya nycklar. `textCredit` har en ny mening med AI-märkning, samma beslut som appens `profile_text_credit` ("Texten är skriven med AI ur fakta från Wikipedia och får delas under" + den länkade licensen, oförändrad kod). `dataCreditRedList` fick ett eget `{gbif}`-mönster, samma som `dataCreditReports` redan hade, så att rödlistecrediten också länkar till GBIF. `speciesAbout.sections` ("Licenserna") fick ordföljden "Fotona är nedskalade, i övrigt oförändrade." / "The photos are scaled down, otherwise unchanged." (var "Fotona visas oförändrade, bara nedskalade." / "The photos are shown unchanged, only scaled down."). Se Task 9s kodblock och tillägg för hur dessa nycklar används.)

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

interface Props { species: Species; locale: Locale; loading?: 'eager' | 'lazy' }
const { species: s, locale, loading = 'lazy' } = Astro.props;
---

<a class="scard" href={speciesHref(s, locale)}>
  <Image src={speciesImage(heroOf(s).file)} alt="" width={480} widths={[320, 480]} sizes="(max-width: 760px) 45vw, 220px" loading={loading} decoding="async" />
  <span class="scard-name">{s.names[locale]}</span>
  <span class="scard-latin" lang="la">{s.names.scientific}</span>
</a>

<style>
  .scard { display: block; height: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper), box-shadow .25s var(--ease-paper); }
  .scard:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(var(--dark-rgb), .12); }
  .scard :global(img) { display: block; width: 100%; height: auto; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 8px; }
  .scard-name { display: block; margin: 8px 4px 0; font-weight: 600; font-size: 14px; color: var(--ink); }
  .scard-latin { display: block; margin: 0 4px 4px; font-family: var(--font-script); font-size: 17px; line-height: 1.15; color: var(--muted); }
</style>
```

(Rättat i efterhand, controller-granskning 2026-10-07: `width={480}` på bildens `src`-fallback, `lang="la"` + `line-height: 1.15` på det vetenskapliga namnet om det radbryts.)

- [ ] **Step 4: Kategoriraden**

`src/components/species/CategoryBar.astro`:

```astro
---
import Icon from '../ui/Icon.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import { activeGroups, countLabel, getAllSpecies, groupHref, groupSizes, hubHref } from '../../lib/species';

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
// Mirrors Nav.astro's `here`: a chip is the literal current page (aria-current="page") only when its
// href is the page we're actually on (the hub chip on the hub, a group chip on its own group page).
// Elsewhere "active" marks the current section without being the page itself (controller review 2026-10-07).
const here = Astro.url.pathname.endsWith('/') ? Astro.url.pathname : `${Astro.url.pathname}/`;
---

<div class="catbar" data-catbar>
  <div class="catbar-inner">
    <nav class="chips" aria-label={t.species.groupsLabel} data-chips>
      {chips.map((c) => (
        <a
          class:list={['chip', { 'is-active': c.key === active }]}
          href={c.href}
          aria-current={c.key === active ? (c.href === here ? 'page' : 'true') : undefined}
          aria-label={`${c.label}, ${countLabel(c.n, t)}`}
        >
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
  // Bring the active chip into view on narrow screens, without scrolling the page itself, and show an
  // edge fade + scrollbar hint while the chip row overflows (controller review 2026-10-07).
  const chips = document.querySelector<HTMLElement>('[data-chips]');
  const current = chips?.querySelector<HTMLElement>('.chip.is-active');
  /** Width of the right-edge fade (the mask in the styles below) and the clear margin at the left edge. */
  const FADE = 32;
  const EDGE = 6;

  const updateFade = () => {
    if (!chips) return;
    const overflowing = chips.scrollWidth > chips.clientWidth + 1;
    const atEnd = chips.scrollLeft + chips.clientWidth >= chips.scrollWidth - 1;
    chips.classList.toggle('has-overflow', overflowing && !atEnd);
  };

  // The active chip must end up fully visible and clear of the fade, at either end of the row (controller
  // review 2026-10-07: the last chip was left half under the fade). Centred when it isn't, clamped to the
  // row's scroll range, so the first chip lands at the start and the last at the end, where there is no
  // fade. Instant, never smooth, so reduced motion needs nothing extra.
  const revealActive = () => {
    if (!chips || !current) return;
    const c = chips.getBoundingClientRect();
    const a = current.getBoundingClientRect();
    if (a.left >= c.left + EDGE && a.right <= c.right - FADE) return;
    const max = chips.scrollWidth - chips.clientWidth;
    const target = chips.scrollLeft + a.left - c.left - (c.width - a.width) / 2;
    chips.scrollLeft = Math.max(0, Math.min(max, target));
    updateFade();
  };
  revealActive();
  updateFade();
  // The web fonts change the chips' widths after this first pass, which left the last chip under the fade.
  // Run it again once they have loaded, unless the visitor has already scrolled the row.
  let touched = false;
  for (const type of ['pointerdown', 'wheel', 'keydown']) chips?.addEventListener(type, () => { touched = true; }, { once: true, passive: true });
  document.fonts?.ready.then(() => { if (!touched) revealActive(); updateFade(); });
  chips?.addEventListener('scroll', updateFade);
  window.addEventListener('resize', updateFade);

  // Scroll a focused chip into view inside the row itself, not the page (controller review 2026-10-07).
  chips?.addEventListener('focusin', (e) => {
    const el = (e.target as HTMLElement).closest('.chip');
    if (!el || !chips) return;
    const c = chips.getBoundingClientRect();
    const a = el.getBoundingClientRect();
    if (a.right > c.right - 40) chips.scrollLeft += a.right - (c.right - 40);
    else if (a.left < c.left + 6) chips.scrollLeft -= c.left + 6 - a.left;
  });
</script>

<style>
  .catbar { position: sticky; top: 76px; z-index: 90; background: var(--card); border-bottom: 1px solid var(--line); }
  .catbar-inner { max-width: 1320px; margin: 0 auto; display: flex; align-items: center; gap: 16px; padding: 4px 44px; }
  .chips { display: flex; flex-wrap: nowrap; gap: 8px; overflow-x: auto; scrollbar-width: none; flex: 1; min-width: 0; padding: 6px; margin-inline: -6px; scroll-padding-inline: 6px; }
  .chips::-webkit-scrollbar { display: none; }
  .chips.has-overflow { mask-image: linear-gradient(to right, #000 calc(100% - 32px), transparent); -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 32px), transparent); }
  @media (hover: hover) and (pointer: fine) {
    .chips { scrollbar-width: thin; scrollbar-color: var(--line) transparent; }
  }
  .chip { position: relative; flex: none; display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); font-size: 13px; font-weight: 600; color: var(--ink); white-space: nowrap; transition: border-color .2s; }
  .chip::after { content: ''; position: absolute; inset: -7px 0; }
  .chip:hover { border-color: var(--rust); }
  .chip .n { font-weight: 400; color: var(--muted); }
  .chip.is-active { background: var(--rust); border-color: var(--rust); color: var(--cream); }
  .chip.is-active .n { color: var(--cream); }
  .search { flex: none; display: flex; align-items: center; border: 1px solid var(--line); border-radius: 999px; background: var(--paper); padding: 0 4px 0 14px; }
  .search:focus-within { outline: 3px solid var(--rust); outline-offset: 2px; }
  .search input { border: 0; background: none; font: inherit; font-size: 13px; width: 150px; padding: 7px 0; color: var(--ink); outline: none; }
  .search input::placeholder { color: var(--muted); opacity: 1; }
  .search input::-webkit-search-cancel-button { appearance: none; }
  .search-btn { border: 0; background: none; color: var(--muted); width: 32px; height: 32px; display: grid; place-items: center; cursor: pointer; }
  @media (max-width: 1023px) {
    .catbar { top: 64px; }
    .catbar-inner { padding: 2px 20px; gap: 10px; }
  }
  @media (max-width: 760px) {
    .search { padding: 0; border-color: transparent; background: none; }
    /* 16px, not the 13px above: below that iOS Safari zooms the page in on focus (controller review 2026-10-07). */
    .search input { width: 0; padding: 0; font-size: 16px; }
    .search:has(input:focus) { padding: 0 4px 0 14px; border-color: var(--line); background: var(--paper); }
    .search:has(input:focus) input { width: 120px; padding: 7px 0; }
    .search-btn { width: 44px; height: 44px; }
  }
  :global(html:has(.catbar)) { scroll-padding-top: 9.25rem; }
  @media (max-width: 1023px) {
    :global(html:has(.catbar)) { scroll-padding-top: 8rem; }
  }
</style>
```

(Rättat i efterhand, controller-granskning 2026-10-07: fokusringen klipptes av `.chips`s `overflow-x` (padding på `.chips` + motsvarande negativ marginal löser det, barhöjden oförändrad via mindre padding på `.catbar-inner`); mobilens sökfält var osynligt men fokuserbart (`:has(input:focus)` visar det och förstorar knappen till 44 px); platshållarens kontrast; fokuserade mål gömda bakom den klistrande raden (`scroll-padding-top` på `html:has(.catbar)`, överstyr global.css:s 5,5rem); skrollskriptet använde fel offset (bytt mot `getBoundingClientRect`-differens, skrollar bara vid behov); kantton + tunn rullist när raden svämmar över, ingen radbrytning. Minor: `aria-current` är `'page'` bara när chipens adress är sidans egen, annars `'true'`; Task 10:s test vid rad ~2800 bytt till `.chip[aria-current="true"]` (Task 8:s test står kvar som `"page"`, eftersom gruppsidan själv är chipens adress); antalet läses med en enhet för skärmläsare (`countLabel`); chipens träffyta ~45 px via `::after` (`inset: -7px 0` innanför radens egen padding, var -5px).

(Rättat i efterhand, Task 7:s granskning 2026-10-07: en fokuserad chip skrollas in i raden själv på `focusin`, inte sidan (samma avgränsning som skrollskriptet ovan); den inbyggda `::-webkit-search-cancel-button` avstängd (`appearance: none`) så sökfältets eget kryss inte krockar med vår ikonknapp; sökfältets textstorlek 16px under 760 px, annars zoomar iOS Safari in vid fokus. Chipens tillgängliga namn kommer nu från `aria-label` på själva länken (`${c.label}, ${countLabel(c.n, t)}`) i stället för en osynlig syskon-span: en tidigare fixomgång, samma dag, flyttade mellanslaget mellan namnet och sifferspannen in i en `aria-hidden`-taggad span för att skärmläsaren inte skulle läsa "Tättingar , 8 arter"; den fixen är nu överflödig och borttagen, eftersom `aria-label` helt ersätter länkens beräknade namn och aldrig läser barnens text alls, stray space eller ej.)

- [ ] **Step 5: Bygg**

Run: `npm run build:fixtures && npm run test:palette`
Expected: bygget går igenom (komponenterna används inte än) och palettvakten är grön.

- [ ] **Step 6: Commit**

```bash
git add src/components/ui/Icon.astro src/styles/species.css src/components/species/CategoryBar.astro src/components/species/SpeciesCard.astro
git commit -m "feat(website): kategoriraden, artkortet och delade stilar för artsidorna"
```

(Tillägg, Task 8:s granskning 2026-10-07, egen commit skild från Task 8: `SpeciesCard.astro` fick en `loading` prop, `'eager' | 'lazy'`, default `'lazy'` så hubbens och gruppens befintliga användning är oförändrad; gruppsidan skickar `'eager'` för korten ovan vecket, se Task 8:s not.)

---

### Task 7: Ingångssidan

**Files:**
- Create: `website/tests/species.spec.ts`
- Create: `website/src/components/species/SpeciesHub.astro`
- Create: `website/src/pages/sv/arter/index.astro`, `website/src/pages/species/index.astro`
- Create (controller review 2026-10-07): `website/src/lib/species-search.mjs`, `website/tests/unit/species-search.unit.mjs`, `website/scripts/check-empty-hub.mjs`, `website/tests/fixtures/empty/{species,comparisons,species-assets}/.gitkeep`
- Modify (controller review 2026-10-07): `website/src/lib/species.ts` (`searchKey` byter till `normalizeSearch`), `website/src/components/species/CategoryBar.astro` (aria-label på chipen, mobilens textstorlek 16px), `website/src/content/copy.{en,sv}.json` (`hubLeadOne`, `hubLeadEmpty`, `descHubOne`), `website/package.json` (`build:empty`, `test:empty-hub`), `website/.gitignore` (`dist-empty/`)
- Modify (controller review 2026-10-07, andra granskningsrundan): `website/src/lib/species-source.mjs` (`useEmptyData()`, `SPECIES_EMPTY`), `website/tests/unit/species-source.unit.mjs`
- Modify (controller review 2026-10-07, tredje granskningsrundan, egen commit skild från Task 8): `website/src/components/species/SpeciesHub.astro` (kategoriraden gömd vid n = 0, egen titel vid n = 0, ingen `itemListJsonLd` vid n = 0, `history.replaceState` i try/catch, `<search>`s `display`-regler), `website/src/content/copy.{en,sv}.json` (`titleHubEmpty`, EN-paritet för `hubLeadEmpty`), `website/scripts/check-empty-hub.mjs` (kontroll att kategoriraden och `CollectionPage` inte visas vid n = 0), spec Bilaga A (nya rader)

- [ ] **Step 1: Skriv testerna för ingångssidan**

`tests/species.spec.ts`:

```ts
import { test, expect, type Page } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

// Runs against the TEST data (tests/fixtures/): build with `npm run build:fixtures` first.
// 17 species are published there, in 8 groups; two woodpeckers are unpublished, one is failed and one pending.
// (Blåkråka, absent in Sweden and the only species in "other", is published since Task 10: 16 and 7 before.)

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
      await expect(page.locator('.groups a')).toHaveCount(8);
      await expect(page.locator('[data-item]')).toHaveCount(17);
      // Comparisons are off until Task 11 builds their pages (COMPARISONS_ENABLED in lib/species.ts).
      await expect(page.locator('[data-compare-link]')).toHaveCount(0);
      await expect(page.locator(`a[href="${about}"]`)).toHaveCount(1);
      await expect(page.locator(`link[rel="alternate"][hreflang="${path.startsWith('/sv') ? 'en' : 'sv'}"]`)).toHaveAttribute('href', `https://birdy.community${other}`);
      expect(errors).toEqual([]);
    });
  }

  // Task 11 turns comparisons on and restores this test's original form:
  // await expect(page.locator('[data-compare-link]')).toHaveText(['Blåmes eller talgoxe', 'Kaja eller skata']);
  test('jämförelserna är avstängda tills deras sidor byggs (Task 11)', async ({ page }) => {
    await page.goto('/sv/arter/');
    await expect(page.locator('[data-compare-link]')).toHaveCount(0);
    await expect(page.locator('h2', { hasText: 'Lätta att blanda ihop' })).toHaveCount(0);
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

  test('sökningen struntar i ordordning ("tit great" hittar Talgoxe)', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('tit great');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Talgoxe');
  });

  test('sökningen struntar i bindestreck ("long eared owl" hittar Hornuggla)', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('long eared owl');
    await expect(page.locator('[data-item]:visible')).toHaveCount(1);
    await expect(page.locator('[data-item]:visible')).toContainText('Hornuggla');
  });

  test('bokstavsavsnitt utan träff döljs vid sökning', async ({ page }) => {
    await page.goto('/sv/arter/');
    await page.locator('#species-search').fill('talg');
    const visible = page.locator('section.letter:visible');
    await expect(visible).toHaveCount(1);
    await expect(visible.locator('h3')).toHaveText('T');
  });

  test('sökträffarna annonseras i en statusrad för skärmläsare', async ({ page }) => {
    await page.goto('/sv/arter/');
    const status = page.locator('[data-count]');
    await expect(status).toHaveAttribute('role', 'status');
    await expect(status).toHaveText('');
    await page.locator('#species-search').fill('talg');
    await expect(status).toHaveText('1 art');
    await page.locator('#species-search').fill('zzzz');
    await expect(status).toHaveText('0 arter');
    await page.locator('#species-search').fill('');
    await expect(status).toHaveText('');
  });

  test('grupperna och jämförelserna döljs medan man söker, så resultaten hamnar direkt under sökfältet', async ({ page }) => {
    await page.goto('/sv/arter/');
    await expect(page.locator('[data-browse]')).toBeVisible();
    await page.locator('#species-search').fill('talg');
    await expect(page.locator('[data-browse]')).toBeHidden();
    await page.locator('#species-search').fill('');
    await expect(page.locator('[data-browse]')).toBeVisible();
  });

  test('sökresultatet hamnar ovanför vikningen på 390 px', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/?q=talg');
    const box = await page.locator('[data-item]:visible').first().boundingBox();
    expect(box).not.toBeNull();
    expect(box!.y).toBeGreaterThanOrEqual(0);
    expect(box!.y).toBeLessThan(844);
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
// Grammar for n=1 ("1 vanlig fågel", not "1 vanliga fåglar"): own copy keys, not countLabel's {n} template,
// since these are full sentences, not just a number (controller review 2026-10-07). The title doesn't need
// one for n=1: it already goes through countLabel ("1 art" / "16 arter"), which is correct for every n.
// n=0 (production right after Task 15's merge, before the first species is published, spec Task 15 Step 4):
// no count-dependent sentence reads sensibly with "0", so the title gets its own dedicated copy key too
// (third controller review, same day) instead of rendering "...: 0 arter med foton | Birdy".
const title = n === 0 ? t.species.titleHubEmpty : t.species.titleHub.replace('{count}', countLabel(n, t));
const leadText = n === 0 ? t.species.hubLeadEmpty : n === 1 ? t.species.hubLeadOne : t.species.hubLead.replace('{n}', String(n));
// descHubEmpty, not hubLeadEmpty reused: the English lead ran to 156 characters, one over spec §12's
// 120-to-155 cap on meta descriptions (Task 8 fix wave); its own, shorter copy key stays in range.
const description = n === 0 ? t.species.descHubEmpty : n === 1 ? t.species.descHubOne : t.species.descHub.replace('{n}', String(n));
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: pathname },
];
// At n=0 there is no list to describe: an ItemList with zero items would be a CollectionPage claiming
// to list species it doesn't have (third controller review, same day). Breadcrumbs alone still apply.
const jsonLd = n === 0 ? [breadcrumbJsonLd(crumbs)] : [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, title.replace(/ \| Birdy$/, ''), locale, sorted)];
---

<Layout locale={locale} pathname={pathname} alternatePath={hubHref(other)} title={title} description={description} noindex={n === 0} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={hubHref(other)} />
  {/* The bar's only chip at n=0 would be "All species (0)": nothing to filter into, so it is hidden
      along with the rest of the browsing UI below (third controller review, same day). */}
  {n > 0 && <CategoryBar locale={locale} active="all" search={false} />}
  <main id="main" tabindex="-1" class="hub wrap">
    <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
      <ol>
        <li><a href={crumbs[0].href} data-crumb>{crumbs[0].name}</a></li>
        <li><span aria-current="page" data-crumb>{crumbs[1].name}</span></li>
      </ol>
    </nav>
    <Kicker text={t.species.kicker} />
    <JournalHeadline text={t.species.hubHeadline} level="h1" align="left" size="clamp(38px, 5vw, 60px)" />
    <p class="lead">{leadText}</p>

    {n > 0 && (
      <Fragment>
        {/* Hidden by default: without JavaScript this box can neither filter (the script below) nor
            submit (it isn't a <form>), so a no-JS visitor gets the plain A-to-Z list below instead of a
            dead control (controller review 2026-10-07). The script unhides it. A native <search> landmark,
            not a styling div. */}
        <search class="hub-search" data-hub-search hidden>
          <label class="sr-only" for="species-search">{t.species.searchLabel}</label>
          <input id="species-search" name="q" type="search" placeholder={t.species.searchPlaceholder} autocomplete="off" />
        </search>
        {/* Always in the DOM (not inserted by the script), so a screen reader has it registered before the
            first count change and can announce the ones after (controller review 2026-10-07). Empty, and
            so silent, outside of a search. */}
        <p class="sr-only" role="status" data-count data-count-one={t.species.countOne} data-count-many={t.species.countMany}></p>

        {/* The group grid and the comparison list are tall enough to push a short set of search results
            below the fold on a phone, so the script hides this block entirely while there's a query
            (controller review 2026-10-07): the A-to-Z results then sit right under the search field. */}
        <div data-browse>
          <h2 class="sp-h2">{t.species.hubGroups}</h2>
          <ul class="groups" role="list">
            {groups.map((g) => {
              const photo = groupPhoto(g, all);
              return (
                <li>
                  <a class="gcard" href={groupHref(g, locale)}>
                    {photo && <Image src={photo} alt="" width={480} widths={[320, 480]} sizes="(max-width: 760px) 45vw, 200px" loading="lazy" decoding="async" />}
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
        </div>

        <h2 class="sp-h2" id="a-o">{t.species.hubAll}</h2>
        <p class="no-results" data-no-results hidden>{t.species.noResults}</p>
        {letters.map((letter) => (
          <section class="letter" data-letter>
            <h3>{letter}</h3>
            <ul role="list">
              {sorted.filter((s) => firstLetter(s.names[locale]) === letter).map((s) => (
                <li data-item data-search={searchKey(s)}>
                  <a href={speciesHref(s, locale)}><span>{s.names[locale]}</span> <i lang="la">{s.names.scientific}</i></a>
                </li>
              ))}
            </ul>
          </section>
        ))}

        <p class="about-link"><a href={aboutHref(locale)}>{t.species.hubAbout}</a></p>
      </Fragment>
    )}
  </main>
  <Footer locale={locale} switchLangHref={hubHref(other)} />
</Layout>

<script>
  import { matchesSearch, normalizeSearch } from '../../lib/species-search.mjs';

  const wrap = document.querySelector<HTMLElement>('[data-hub-search]');
  const input = document.querySelector<HTMLInputElement>('#species-search');
  const browse = document.querySelector<HTMLElement>('[data-browse]');
  const items = [...document.querySelectorAll<HTMLElement>('[data-search]')];
  const sections = [...document.querySelectorAll<HTMLElement>('[data-letter]')];
  const empty = document.querySelector<HTMLElement>('[data-no-results]');
  const status = document.querySelector<HTMLElement>('[data-count]');

  // The search box only works with JavaScript running (see the markup comment above), so it starts
  // hidden and is unhidden here, the moment we know the filtering below can actually do something.
  if (wrap) wrap.hidden = false;

  const apply = () => {
    const q = normalizeSearch(input?.value ?? '');
    let shown = 0;
    for (const item of items) {
      const hit = matchesSearch(item.dataset.search ?? '', q);
      item.hidden = !hit;
      if (hit) shown += 1;
    }
    for (const section of sections) section.hidden = !section.querySelector('[data-item]:not([hidden])');
    if (empty) empty.hidden = shown > 0;
    if (browse) browse.hidden = Boolean(q);
    if (status) {
      const tpl = shown === 1 ? status.dataset.countOne : status.dataset.countMany?.replace('{n}', String(shown));
      status.textContent = q ? (tpl ?? '') : '';
    }
    const url = new URL(location.href);
    const raw = input?.value.trim() ?? '';
    if (raw) url.searchParams.set('q', raw);
    else url.searchParams.delete('q');
    try {
      // Safari throttles history.replaceState (SecurityError past a call-rate limit); keeping the URL
      // in sync with the search box is a nicety, not something the filtering above depends on
      // (third controller review, same day).
      history.replaceState(null, '', url);
    } catch {
      // Filtering already happened above; losing the URL sync is harmless.
    }
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
  /* <search> is a new HTML sectioning element: some engines don't yet default it to block, so it is set
     explicitly. That same rule would otherwise beat the UA [hidden] rule on specificity and keep the box
     visible before the script runs; the second declaration restores [hidden] (third controller review,
     same day). */
  search.hub-search { display: block; }
  .hub-search[hidden] { display: none; }
  .hub-search { margin: 22px 0 0; max-width: 420px; }
  .hub-search input { width: 100%; box-sizing: border-box; font: inherit; font-size: 15px; padding: 12px 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: var(--ink); }
  .hub-search input:focus-visible { outline: 3px solid var(--rust); outline-offset: 2px; }
  .hub-search input::placeholder { color: var(--muted); opacity: 1; }
  .groups { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 14px; }
  .gcard { display: block; height: 100%; background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 6px; transition: transform .25s var(--ease-paper), box-shadow .25s var(--ease-paper); }
  .gcard:hover { transform: translateY(-2px); box-shadow: 0 6px 16px rgba(var(--dark-rgb), .12); }
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
    /* 16px, not the 15px above: below that iOS Safari zooms the page in on focus (controller review 2026-10-07). */
    .hub-search input { font-size: 16px; }
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

(Rättat i efterhand, Task 7:s granskning 2026-10-07, hela tasken: **noll-arter-läget**: `noindex` och bara ingressen (ingen sökruta, grupplista, jämförelselista eller A-till-Ö-lista) när `getAllSpecies()` är tom, med egna "på väg"-texter `species.hubLeadEmpty` (sv/en); konsekvenser i Task 12, 13, 14, 15 Step 4 (se respektive tasks noter) och ett nytt verktyg `npm run test:empty-hub` (`scripts/check-empty-hub.mjs`, bygger via nytt skript `build:empty` mot en avsiktligt tom testdatamapp `tests/fixtures/empty/`, inte mot `src/data/`, se andra granskningsrundans rättelse vid Task 14 nedan; utökas i Task 14 med `check-seo.mjs`). **Sökresultat under vikningen**: grupplistan och jämförelselistan (`<div data-browse>`) döljs av skriptet medan det finns en sökfråga, så A-till-Ö-resultaten hamnar direkt under sökfältet i stället för långt ned på mobilen. **Statusrad**: en alltid närvarande, tyst `<p role="status" data-count>` annonserar antalet träffar (återanvänder `countOne`/`countMany` via data-attribut) för skärmläsare, tom utan sökning. **Ord- och bindestreckstolerant sökning**: ny delad modul `src/lib/species-search.mjs` (`normalizeSearch`/`matchesSearch`, inga Astro-importer så den funkar både i `species.ts`s `searchKey` och i sidans eget skript) byter varje icke-bokstav/siffra mot ett mellanslag och kräver att varje sökord finns någonstans i nyckeln, i valfri ordning: "tit great" hittar Talgoxe, "long eared owl" hittar Hornuggla trots bindestrecket i "Long-eared Owl". Enhetstester i `tests/unit/species-search.unit.mjs`. **Sökfältets kontrast**: `::placeholder` satt explicit (`var(--muted)`, `opacity: 1`), annars webbläsarens standardfärg. **Sökfältet utan JavaScript**: `<search data-hub-search hidden>` (en riktig `<search>`-landmärkestagg, inte en div) döljs tills skriptet vet att filtreringen faktiskt fungerar; utan JavaScript (ingen `<form>`, ingen server-filtrering) hade en synlig sökruta varit en död kontroll. **n=1-grammatik**: egna hela meningar `hubLeadOne`/`descHubOne` (sv/en) i stället för `{n}`-mallen ("1 vanlig fågel", inte "1 vanliga fåglar"); titeln behövde ingen egen nyckel, den går redan genom `countLabel`. **Minors:** `width={480}` på gruppkortets `<Image>` (saknades, `check-seo.mjs` kräver `width`/`height` på nya sidor från Task 14); `.gcard` fick `height: 100%` och samma hover-skugga som `SpeciesCard`; `lang="la"` på A-till-Ö-listans vetenskapliga namn; sökfältets textstorlek 16px under 760 px (annars zoomar iOS Safari in vid fokus, samma fix som kategoriradens eget sökfält, Task 6:s not ovan); frivillig `history.replaceState` håller `?q=` i adressfältet synkat med sökfältet utan att lägga till historikposter. Tester: 6 nya i `tests/species.spec.ts` (nedan), 11 nya i `tests/unit/species-search.unit.mjs`.)

- [ ] **Step 5: Kör testerna igen**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (14 tester, Task 7:s granskning 2026-10-07 lade till 6)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/SpeciesHub.astro src/pages/sv/arter/index.astro src/pages/species/index.astro tests/species.spec.ts
git commit -m "feat(website): ingångssidan för arterna med sökning och jämförelser"
```

(Tillägg, controller-granskning 2026-10-07, egen commit: `src/lib/species-search.mjs`, `tests/unit/species-search.unit.mjs`, `scripts/check-empty-hub.mjs`, `src/lib/species.ts`, `src/components/species/CategoryBar.astro`, `src/content/copy.en.json`, `src/content/copy.sv.json`, `package.json`, `.gitignore`: se den sammanfattande noten ovan. En andra granskningsrunda samma dag lade till en egen commit till: `src/lib/species-source.mjs` (`SPECIES_EMPTY`), `tests/unit/species-source.unit.mjs`, `tests/fixtures/empty/{species,comparisons,species-assets}/.gitkeep`, samt uppdaterade `package.json` och `scripts/check-empty-hub.mjs`: se Task 14:s "andra granskningsrundans rättelse" nedan.)

(Tillägg, tredje granskningsrundan 2026-10-07, egen commit skild från Task 8: sex småfynd kvar från noll-arter-granskningen. **Kategoriraden gömdes inte vid n = 0**: raden hade visat en enda chip, "Alla arter (0)", utan något att filtrera till; `SpeciesHub.astro` villkorar nu `<CategoryBar .../>` på `n > 0`, och `check-empty-hub.mjs` kontrollerar att `data-catbar` inte finns i det byggda n = 0-läget. **Titeln hade ingen egen n = 0-text**: `t.species.titleHub.replace('{count}', ...)` hade gett "...: 0 arter med foton | Birdy"; ny copy-nyckel `titleHubEmpty` (SV 56, EN 58 tecken, båda inom specens 40 till 60) används i stället, och `itemListJsonLd` (en `CollectionPage` med en tom `ItemList`) utelämnas helt vid n = 0: bara `breadcrumbJsonLd` kvar; samma kontrollskript verifierar att `"@type":"CollectionPage"` inte förekommer. **`hubLeadEmpty` saknade paritet**: den engelska texten nämnde "photos and field marks" men inte lätena som den svenska texten har ("med foton, kännetecken och läten"); EN rättad till "...with photos, field marks and calls." (samma ändring i spec Bilaga A). **`history.replaceState` ogardat**: Safari strypar (kastar `SecurityError`) efter ett antal anrop per tidsfönster; ett sökfält som uppdaterar `?q=` vid varje tangenttryckning kan nå det taket på en lång sökning. Omslutet i try/catch: filtreringen (huvudjobbet) är opåverkad, bara webbadressens synk med sökfältet tappas tyst. **`<search>`s `display` ogarderad**: elementet är en ny HTML-sektioneringstagg utan garanterat `display: block` i alla motorer, och `.hub-search[hidden]` (UA-regeln) hade annars kunnat slås ut av en egen `display: block`-regel med samma specificitet; `search.hub-search { display: block; }` och `.hub-search[hidden] { display: none; }` tillagda, i den ordningen. Spec Bilaga A fick fyra nya rader: ingress och description vid n = 1, ingress vid n = 0, titel vid n = 0.)

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
    await expect(page.locator('.catbar .chip')).toHaveCount(9);
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
import { getImage } from 'astro:assets';
import Layout from '../../layouts/Layout.astro';
import Nav from '../Nav.astro';
import Footer from '../Footer.astro';
import Kicker from '../ui/Kicker.astro';
import PlayStoreBadge from '../ui/PlayStoreBadge.astro';
import CategoryBar from './CategoryBar.astro';
import SpeciesCard from './SpeciesCard.astro';
import { getCopy, type Locale } from '../../lib/i18n';
import {
  appText, breadcrumbJsonLd, countLabel, getAllSpecies, groupHref, groupPhoto, groupTitle, hubHref, isGroupIndexed,
  itemListJsonLd, playHref, sortByName, type Group,
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
const description = members.length === 1
  ? t.species.descGroupOne.replace('{group}', group.name[locale])
  : t.species.descGroup.replace('{group}', group.name[locale]).replace('{count}', countLabel(members.length, t));
// Grouped by family.latin in both languages (same sections in SV and EN, like related() in species.ts),
// not by the locale's own display name: the pipeline now guarantees one Swedish family name per Latin
// family (web/families.py, BirdLife Sverige's world bird list NL20), but grouping by Latin stays as a
// safeguard here rather than trusting that every record agrees (controller review, Task 8 fix wave; the
// fixture generator still deliberately gives Kaja and Skata different family.sv values, both Corvidae, to
// exercise this safeguard). The SV label is the first member's family.sv in sorted order (members is
// already locale-sorted, and Array#sort is stable), so the choice is deterministic even if two records
// ever disagreed; EN always shows the Latin name itself, which is unambiguous by definition. Same rule
// applies to Task 10's family kicker row and "Fler {family}" heading, see the note there.
const families = new Map<string, { sv: string; items: typeof members }>();
for (const s of members) {
  const existing = families.get(s.family.latin);
  if (existing) existing.items.push(s);
  else families.set(s.family.latin, { sv: s.family.sv, items: [s] });
}
const byFamily = group.key === 'songbirds'
  ? [...families.entries()]
      .map(([latin, { sv, items }]) => ({ name: locale === 'sv' ? sv : latin, items }))
      .sort((a, b) => a.name.localeCompare(b.name, locale))
  : [];
const renderOrder = byFamily.length ? byFamily.flatMap((f) => f.items) : members;
// Only the species cards likely above the fold skip native lazy-loading (controller review, Task 8 fix wave).
const eagerQids = new Set(renderOrder.slice(0, 4).map((s) => s.qid));
const crumbs = [
  { name: t.species.crumbHome, href: locale === 'sv' ? '/sv/' : '/' },
  { name: t.species.crumbHub, href: hubHref(locale) },
  { name: group.name[locale], href: pathname },
];
const jsonLd = [breadcrumbJsonLd(crumbs), itemListJsonLd(pathname, group.name[locale], locale, renderOrder)];
const photo = groupPhoto(group, all);
const ogImage = photo ? await getImage({ src: photo, width: 1200, height: 630, fit: 'cover', format: 'jpg', quality: 82 }) : undefined;
---

<Layout locale={locale} pathname={pathname} alternatePath={groupHref(group, other)} title={title} description={description} noindex={!isGroupIndexed(group, all)} jsonLd={jsonLd} ogImage={ogImage?.src}>
  <Nav locale={locale} variant="solid" switchLangHref={groupHref(group, other)} />
  <CategoryBar locale={locale} active={group.key} />
  <main id="main" tabindex="-1" class="group wrap">
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
          {/* f.name is always Latin in English (the group key by definition), Swedish on the Swedish
              page: tagged for pronunciation on the English page only (controller review, Task 8 fix wave,
              same pattern as SpeciesCard's scientific name). */}
          <h3 lang={locale === 'en' ? 'la' : undefined}>{f.name}</h3>
          <ul class="sp-cards" role="list">
            {f.items.map((s) => <li data-item><SpeciesCard species={s} locale={locale} loading={eagerQids.has(s.qid) ? 'eager' : 'lazy'} /></li>)}
          </ul>
        </section>
      ))
    ) : (
      <ul class="sp-cards" role="list">
        {members.map((s) => <li data-item><SpeciesCard species={s} locale={locale} loading={eagerQids.has(s.qid) ? 'eager' : 'lazy'} /></li>)}
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
import { ABOUT_SLUG, GROUPS, activeGroups, assertUniqueSlugs, getAllSpecies } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  // Checked against all 15 GROUPS, not just the active ones (controller review, Task 8 fix wave): a slug
  // collision must fail the very first build, not wait for the publish that happens to activate the
  // colliding group, by which point the build has looked clean for however long the group sat empty.
  assertUniqueSlugs([...GROUPS.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  const groups = activeGroups(all);
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
Expected: PASS (25 tester, Task 8:s granskning 2026-10-07 lade till 5 i `gruppsidorna`, se tillägget nedan)

- [ ] **Step 6: Commit**

```bash
git add src/components/species/GroupPage.astro src/components/species/SpeciesRoute.astro src/lib/species-routes.ts "src/pages/sv/arter/[slug].astro" "src/pages/species/[slug].astro" tests/species.spec.ts
git commit -m "feat(website): gruppsidorna med familjer, noindex för små grupper och approta"
```

(Tillägg, Task 8:s granskning 2026-10-07, samma commit: fem nya tester i `gruppsidorna` utöver de sex i Step 1, `tests/species.spec.ts` har 25 totalt (var 14 efter Task 7). **Familjeskyddsnätet**: "familjer grupperas efter det latinska namnet, inte den svenska stavningen" bevisar att Kaja och Skata (olika `family.sv`, se Task 1:s avvikelse ovan) ändå hamnar i EN sektion, inte två. **Engelska familjer**: "Songbirds (EN) delas också upp i sex familjer, rubrikerna är latin" bevisar att `/species/songbirds/` delar upp på samma sätt och att rubrikerna har `lang="la"` (GroupPage.astro fick samma attribut på den engelska sidans `<h3>`, svenska sidans har det inte: "Tättingar delas upp i familjer" fick ett tillägg, `.family h3[lang="la"]` ska vara noll på svenska). **Eager/lazy**: "de fyra första artkorten laddas direkt, resten lat" bevisar att bara de fyra först renderade korten (över vikningen, oavsett familjeindelning) skippar `loading="lazy"` (`SpeciesCard.astro`s nya `loading`-prop, se Task 6:s tillägg). **n=1-grammatik**: "en grupp med en enda art undviker 'dem'/'them' i beskrivningen" bevisar `descGroupOne`/`descGroupOne`-liknande engelsk motsvarighet på en grupp med exakt en byggd art (Storskarv, `seabirds`). **og:image**: "gruppsidans og:image är artens eget foto, inte standardbilden" bevisar att `ogImage`-raden i koden ovan faktiskt används i stället för Layout-standarden. Alla fem kräver ingen ny kod utöver vad Step 3 redan visar; de bevisar beteende som redan fanns i den visade `GroupPage.astro`.)

---

### Task 9: Diagrammet, kartan, spelaren och creditblocket

**Tillägg (2026-10-06, fas 1b:s slutgranskning Minor 11; ordvalet godkänt av Albin 2026-10-06, gäller):** varje inspelning är bearbetad, inte bara de klippta: pipelinen gör om den till mono, ljudnivånormaliserar och kodar om den till MP3 (`convert_to_mp3`). CC BY och CC BY-SA kräver att creditraden säger att verket är ändrat, så raden ska alltid säga det. Förslag: nycklarna `trimmed` blir `edited` ("bearbetad" / "edited") och `trimmedEdited` ("klippt och bearbetad" / "trimmed and edited"), och spelarens bildtext slutar med `, ${audio.trimmed ? t.species.trimmedEdited : t.species.edited}` i stället för `{audio.trimmed && ...}`; Playwright-kontrollen nedan letar efter "bearbetad" i stället för "klippt". Specen är ändrad på samma sätt (avsnitt 2 punkt 5, avsnitt 8 om licenserna, avsnitt 10 punkt 3 och bilaga A:s två rader för inspelningscredit). Licensstycket på "Så gör vi artsidorna" (copy-nycklarna i Task 5) får samma mening som avsnitt 8: inspelningarna är bearbetade (högst 20 sekunder, mono, utjämnad ljudnivå, MP3), och en bearbetad CC BY-SA-inspelning delas under samma licens. Pipelinen avvisar sedan 2026-10-06 dessutom en CC BY- eller CC BY-SA-inspelning utan upphovsperson, så "okänd upphovsperson" (Task 5:s `unknownRecordist`, rättad i copy-granskningen 2026-10-07) förekommer bara för CC0 och public domain.

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
// month is 100. The text alternative is the paragraph that `describedBy` points at. Each bar also gets a
// hover title built from Series.label and the full month name ("Talgoxe, oktober"), controller review
// Task 9, for sighted mouse users who don't read the aria-describedby paragraph. No number: the values are
// scaled so the top month is 100, and "Talgoxe, oktober: 100" read as 100 per cent of the reports (Task 9
// re-review).
interface Series { label: string; values: number[]; tone: 'rust' | 'navy' }
interface Props { id: string; title: string; letters: string[]; monthNames: string[]; series: Series[]; describedBy: string }
const { id, title, letters, monthNames, series, describedBy } = Astro.props;
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
        const v = s.values[m] ?? 0;
        const h = heightOf(v);
        return (
          <rect data-month={m + 1} data-series={i} class={`bar bar--${s.tone}`} x={(m * SLOT + offset(i)).toFixed(1)} y={(BASE - h).toFixed(1)} width={barW} height={h.toFixed(1)} rx="2">
            <title>{`${s.label}, ${monthNames[m]}`}</title>
          </rect>
        );
      })}
      <text x={(m * SLOT + SLOT / 2).toFixed(1)} y={BASE + 16} class="month">{letter}</text>
    </g>
  ))}
</svg>

<style>
  .mchart { display: block; width: 100%; max-width: 26rem; height: auto; }
  .axis { stroke: var(--line); stroke-width: 1; }
  .bar--rust { fill: var(--rust); }
  .bar--navy { fill: var(--navy); }
  .month { font-family: var(--font-sans); font-size: 11px; fill: var(--muted); text-anchor: middle; }
</style>
```

(Tillägg, controller-granskning 2026-10-07: `monthNames` är en ny obligatorisk prop, 12 fulla månadsnamn (`t.species.monthNames`, Task 5s copy-filer), skild från `letters` som fortfarande är de förkortade bokstäverna under staplarna. Varje stapel kapslar nu en egen `<title>`, så Task 10 och 11s anrop av `<MonthChart>` måste skicka `monthNames={t.species.monthNames}` utöver `letters={t.species.monthLetters}`.)

- [ ] **Step 2: Länskartan**

`src/components/species/CountyMap.astro`:

```astro
---
// Sweden's 21 counties shaded by the species' share of all bird reports (spec 2026-09-25 §5 and §9.2).
// Geometry: src/data/sweden-counties.json (npm run assets:counties). Four shades: no reports (a hatch
// pattern, not a flat colour, controller review Task 9: a solid --card tile reads as "unstyled", not
// "zero"), 1 to 33, 34 to 66 and 67 to 100 per cent of the highest county.
import counties from '../../data/sweden-counties.json';
import type { Locale } from '../../lib/i18n';

interface Props { id: string; values: Record<string, number>; locale: Locale; title: string; legend: string[]; describedBy: string }
const { id, values, locale, title, legend, describedBy } = Astro.props;
const bucket = (v: number | undefined) => (!v ? 0 : v <= 33 ? 1 : v <= 66 ? 2 : 3);
const hatchId = `${id}-hatch`;
// The light counties (no reports, small share) get a darker border than the paper-coloured one, or two
// such neighbours merge into one patch (Task 9 re-review). Neighbours share a border, so the later path's
// stroke is the one that shows: the light counties are drawn last, so their borders win.
const drawOrder = [...counties.counties].sort((a, b) => bucket(values[b.code]) - bucket(values[a.code]));
---

<div class="cmap" data-map={id}>
  <svg viewBox={counties.viewBox} role="img" aria-labelledby={`${id}-t`} aria-describedby={describedBy}>
    <title id={`${id}-t`}>{title}</title>
    <defs>
      <pattern id={hatchId} width="5" height="5" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
        <rect width="5" height="5" fill="var(--card)" />
        <line x1="0" y1="0" x2="0" y2="5" stroke="var(--muted)" stroke-width="1.5" />
      </pattern>
    </defs>
    {drawOrder.map((c) => {
      const b = bucket(values[c.code]);
      const name = locale === 'sv' ? c.sv : c.en;
      return (
        <path d={c.d} class={`b${b}`} fill={b === 0 ? `url(#${hatchId})` : undefined} data-county={c.code}>
          <title>{`${name}: ${legend[b].toLowerCase()}`}</title>
        </path>
      );
    })}
  </svg>
  <ul class="legend" role="list">
    {legend.map((label, i) => <li><span class={`sw b${i}`} aria-hidden="true"></span>{label}</li>)}
  </ul>
</div>

<style>
  .cmap svg { display: block; width: 100%; height: auto; max-height: 420px; }
  /* A light border (--paper, the page background) so two adjacent --rust (b3) counties stay visually
     distinct from each other, not just from their neighbours (controller review, Task 9).
     vector-effect keeps the line the same width however the SVG is scaled. */
  path { stroke: var(--paper); stroke-width: .75px; vector-effect: non-scaling-stroke; stroke-linejoin: round; }
  path.b0, path.b1 { stroke: color-mix(in srgb, var(--muted) 45%, var(--paper)); }
  .b1 { fill: var(--peach); }
  .b2 { fill: var(--apricot); }
  .b3 { fill: var(--rust); }
  .legend { list-style: none; margin: 10px 0 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 12px; font-size: 12px; color: var(--muted); }
  .legend li { display: inline-flex; align-items: center; gap: 6px; }
  .sw { width: 12px; height: 12px; border-radius: 3px; border: 1px solid var(--muted); }
  /* -45deg: the same slant as the map's hatch pattern (rotate(45) on a vertical line, Task 9 re-review). */
  .sw.b0 { background: repeating-linear-gradient(-45deg, var(--card) 0 2px, var(--muted) 2px 3px); }
  .sw.b1 { background: var(--peach); }
  .sw.b2 { background: var(--apricot); }
  .sw.b3 { background: var(--rust); }
</style>
```

(Tillägg, controller-granskning 2026-10-07: "Inga rapporter" är ett diagonalt mönster, inte `var(--card)` som en platt fyllning, eftersom en platt kortfärgad ruta läses som "ostylad" snarare än "noll", samma mönster i legendens `.sw.b0`. Varje läns `<title>` säger nu nivån ("Stockholms län: stor andel"), inte bara namnet. Kantlinjen mellan länen är `var(--paper)` (sidans bakgrund) i stället för `var(--muted)`, så att två angränsande `--rust`-län (b3) syns som skilda län, inte en sammanhängande yta; `vector-effect: non-scaling-stroke` håller linjen lika tjock oavsett hur SVG:n skalas.)

- [ ] **Step 3: Spelaren**

`src/components/species/AudioPlayer.astro`:

```astro
---
// One recording with its credit line right under it (spec 2026-09-25 §5; deviation 8 in the plan).
// preload="none": nothing is downloaded until the visitor presses play. Every recording is processed
// (mono, level-normalised, re-encoded to MP3, spec Revision 2026-10-06), not only the clipped ones, so
// the credit line always says so: "edited" or, when also clipped, "trimmed and edited" (CC BY and
// CC BY-SA require the credit to say the work was changed).
import { getCopy, type Locale } from '../../lib/i18n';
import { audioHref, licenseLabel, localizedLicenseUrl, type Species } from '../../lib/species';

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
      {/* rel="license" is reserved for the text credit's own CC BY-SA 4.0 link (controller review, Task 9);
          "noopener" is dropped everywhere here since none of these links use target="_blank". */}
      {audio.licenseUrl ? <a href={localizedLicenseUrl(audio.licenseUrl, locale)}>{audio.license}</a> : licenseLabel(audio.license, t)},{' '}
      {t.species.via} <a href={audio.sourceUrl}>Wikimedia Commons</a>, {audio.trimmed ? t.species.trimmedEdited : t.species.edited}
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

(Tillägg, controller-granskning 2026-10-07: `localizedLicenseUrl`/`licenseLabel` är två nya exporter i `species.ts`, tillagda till Task 4s kodblock, se noten där ovan. `rel="license"` tas bort här, `rel="noopener"` också (ingen av länkarna har `target="_blank"`).)

**Obs:** inspelningarna kopieras in i `dist/audio/species/` först av byggkroken `astro:build:done` (Task 4), så spelaren blir tyst (404 på `<audio src>`) i `astro dev`; testa den med `npm run build:fixtures && npm run preview -- --port 4327` i stället.

- [ ] **Step 4: Creditblocket**

`src/components/species/Credits.astro`:

```astro
---
// Credits for every photo, the Wikipedia articles, the data sources, the verification line
// (spec Revision 2026-10-05: "Kontrollerad mot källorna {date}", no name) and the report link
// (spec 2026-09-25 §5, §7 and §10). scripts/check-seo.mjs fails the build when one is missing.
//
// rel="license" is used only on the text credit's own CC BY-SA 4.0 link (controller review, Task 9):
// it marks THIS page's own licence, not every external resource a photo or recording happens to be
// under, so it does not belong on the photo/recording licence links. "noopener" is dropped everywhere
// in this component: none of these links use target="_blank", so it has no effect.
import { getCopy, type Locale } from '../../lib/i18n';
import { CONTACT_EMAIL } from '../../lib/links';
import { aboutHref, formatDate, licenseLabel, localizedLicenseUrl, wikiUrl, type SpeciesImage, type WikiLang, type WikiRef } from '../../lib/species';

// GBIF dataset pages (not just the GBIF.org front page, controller review, Task 9), verified against the
// GBIF API (api.gbif.org/v1/dataset/<key>) 2026-10-07: 38b4c89f… is "Artportalen" (SLU Swedish Species
// Information Centre), 87e639cc… is "The Swedish Red List 2025" (same publisher).
const GBIF_REPORTS_URL = 'https://www.gbif.org/dataset/38b4c89f-584c-41bb-bd8f-cd1def33e92f';
const GBIF_RED_LIST_URL = 'https://www.gbif.org/dataset/87e639cc-30a9-4007-bd2c-b0cab60326b9';
const CC_BY_SA_4 = 'https://creativecommons.org/licenses/by-sa/4.0/';

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
const [gbifReportsBefore, gbifReportsAfter] = t.species.dataCreditReports.split('{gbif}');
const [gbifRedListBefore, gbifRedListAfter] = t.species.dataCreditRedList.split('{gbif}');
const [reviewedBefore, reviewedAfter] = t.species.reviewed.split('{date}');
---

<div class="credits">
  {photos.map(({ key, image }) => (
    <p data-credit-for={key}>
      {t.species.photoCredit} {image.author ?? t.species.unknownAuthor},{' '}
      {image.licenseUrl ? <a href={localizedLicenseUrl(image.licenseUrl, locale)}>{image.license}</a> : licenseLabel(image.license, t)},{' '}
      {t.species.via} <a href={image.sourceUrl}>Wikimedia Commons</a>, {t.species.resized}
    </p>
  ))}
  <p data-wiki-credit>
    {t.species.textCredit} <a href={localizedLicenseUrl(CC_BY_SA_4, locale)} rel="license">CC BY-SA 4.0</a>.
    {' '}{t.species.sources}:{' '}
    {articles.map((a, i) => (
      <Fragment>{i > 0 && ', '}<a href={wikiUrl(a.lang, a.ref)} data-wiki={a.lang}>{a.name ? `${labels[a.lang]} (${a.name})` : labels[a.lang]}</a></Fragment>
    ))}
  </p>
  {(reportData || redList) && (
    <p data-data-credit>
      {reportData && <Fragment>{gbifReportsBefore}<a href={GBIF_REPORTS_URL}>GBIF.org</a>{gbifReportsAfter}</Fragment>}
      {reportData && redList && ' '}
      {redList && <Fragment>{gbifRedListBefore}<a href={GBIF_RED_LIST_URL}>GBIF.org</a>{gbifRedListAfter}</Fragment>}
    </p>
  )}
  <p class="reviewed">
    <span data-checked>{reviewedBefore}<time datetime={reviewedAt} data-reviewed>{formatDate(reviewedAt, locale)}</time>{reviewedAfter}</span>
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

(Tillägg, controller-granskning 2026-10-07: fotocrediten slutar nu med `, {t.species.resized}` ("nedskalad"/"resized"), samma ordval som appens creditrad. Textcrediten har en ny mening med AI-märkning (Task 5s `textCredit`-copy ändrad, se noten vid Task 5 ovan) och licenslänken lokaliseras till `deed.sv` på svenska sidor. Datacrediten länkar nu till GBIF:s egna datasetsidor för BÅDE rapportdata och rödlistan (`dataCreditRedList` fick ett eget `{gbif}`-mönster, samma som `dataCreditReports` redan hade). `data-reviewed-by` döpt om till `data-checked` (mindre missvisande: ingen person, bara en kontroll); Task 10s test uppdaterat nedan.)

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
    await expect(page.locator('.sp-app:visible')).toContainText('på foto eller läte');
    await expect(page.locator('.note:visible')).toHaveText('Testanteckning i marginalen.');
    await expect(page.locator('h2')).toContainText(['Så känner du igen den', 'Läte', 'Var och när', 'Föda och beteende', 'Kan förväxlas med', 'Fler tättingar']);

    const audio = page.locator('audio');
    await expect(audio).toHaveAttribute('preload', 'none');
    const src = await audio.getAttribute('src');
    expect(src).toMatch(/^\/audio\/species\/Q25485\.[0-9a-f]{10}\.mp3$/);
    expect((await request.get(src!)).status()).toBe(200);
    await expect(page.locator('[data-credit-for="audio"]')).toContainText('bearbetad');

    await expect(page.locator('[data-chart] rect[data-month]')).toHaveCount(12);
    // The bar's hover title has no number: the values are scaled to a top month of 100, not per cent (Task 9 re-review).
    await expect(page.locator('[data-chart] rect[data-month="10"] title')).toHaveText('Talgoxe, oktober');
    await expect(page.locator('[data-map] path[data-county]')).toHaveCount(21);
    await expect(page.locator('.data-summary')).toHaveText('Rapporteras året runt. Rapporteras från alla 21 län.');
    await expect(page.locator('[data-data-credit]')).toContainText('Artportalen');

    const looks = page.locator('.looks li');
    await expect(looks).toHaveCount(1);
    await expect(looks.locator('.look-name a')).toHaveAttribute('href', '/sv/arter/blames/');
    // No compare link until Task 11 (COMPARISONS_ENABLED); Task 11 restores:
    // .look-compare href '/sv/arter/blames-eller-talgoxe/', text 'Jämför blåmes och talgoxe'.
    await expect(looks.locator('.look-compare')).toHaveCount(0);
    // One small thumbnail file, with its own size attributes (controller review, Task 10).
    await expect(looks.locator('img')).toHaveAttribute('width', '192');
    await expect(looks.locator('img')).not.toHaveAttribute('srcset', /.+/);

    const keys = await page.locator('[data-photo], [data-audio]').evaluateAll((els) => els.map((e) => e.getAttribute('data-photo') ?? e.getAttribute('data-audio')));
    expect(keys).toEqual(['hero', 'audio', 'extra']);
    for (const key of keys) await expect(page.locator(`[data-credit-for="${key}"]`)).toHaveCount(1);
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(3);
    await expect(page.locator('[data-wiki-credit]')).toContainText('CC BY-SA 4.0');
    await expect(page.locator('[data-checked]')).toContainText('Kontrollerad mot källorna 20 november 2026');
    await expect(page.locator('time[data-reviewed]')).toHaveAttribute('datetime', '2026-11-20');
    await expect(page.locator('.credits a[href="/sv/arter/om-artsidorna/"]')).toHaveCount(1);
    // Two copies in the DOM (left column, single column), one displayed (controller review, Task 10).
    await expect(page.locator('a[href*="utm_campaign%3Dtalgoxe"]')).toHaveCount(2);
    await expect(page.locator('a[href*="utm_campaign%3Dtalgoxe"]:visible')).toHaveCount(1);
    await expect(page.locator('#site-nav .links a[lang="en"]')).toHaveAttribute('href', '/species/great-tit/');
    // Not "page": the species page isn't the group's own page, so the active chip reads aria-current="true"
    // (controller review 2026-10-07, see Task 6's code block note).
    await expect(page.locator('.catbar .chip[aria-current="true"]')).toContainText('Tättingar');
    await expect(page.locator('[data-preview-banner]')).toHaveCount(0);
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('engelska sidan: kontrollraden och jämförelselänken', async ({ page }) => {
    await page.goto('/species/great-tit/');
    await expect(page.locator('h1')).toHaveText('Great Tit');
    await expect(page.locator('[data-checked]')).toContainText('Checked against sources on 20 November 2026.');
    // Task 11 restores: .look-compare text 'Compare the Eurasian Blue Tit and the Great Tit'.
    await expect(page.locator('.look-compare')).toHaveCount(0);
    await expect(page.locator('.sp-app:visible')).toContainText('from a photo or its song');
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
    await expect(page.locator('.sp-app:visible')).toContainText('hjälper dig känna igen fåglarna');
    await expect(page.locator('[data-wiki-credit] [data-wiki]')).toHaveCount(1);
    expect(errors).toEqual([]);
  });

  for (const [path, text] of [['/sv/arter/kaja/', 'på lätet,'], ['/sv/arter/trana/', 'på foto,'], ['/species/western-jackdaw/', 'from its song']] as const) {
    test(`approtan på ${path} säger bara vad appen klarar`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('.sp-app:visible')).toContainText(text);
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

  test('blåkråka: frånvarande art utan diagram och karta, men med datacredit och förväxlingsart utan sida', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const res = await page.goto('/sv/arter/blakraka/');
    expect(res?.status()).toBe(200);
    const facts = page.locator('.facts');
    await expect(facts).toContainText('I Sverige');
    await expect(facts).toContainText('Förekommer inte');
    await expect(facts).toContainText('Livskraftig (LC)');
    await expect(facts).not.toContainText('Svenska rödlistan 2025');
    await expect(page.locator('[data-chart], [data-map], audio')).toHaveCount(0);
    // data exists (the presence sentence, spec §9.2) without months/counties: the report credit shows, the red
    // list credit doesn't, and there is no summary paragraph (it describes a chart and a map; the written text
    // already states the presence fact), controller decision 2026-10-07.
    await expect(page.locator('.data-summary')).toHaveCount(0);
    await expect(page.locator('[data-data-credit]')).toHaveCount(1);
    await expect(page.locator('[data-data-credit]')).toContainText('Artportalen');
    await expect(page.locator('[data-data-credit]')).not.toContainText('Rödlista');
    await expect(page.locator('.sp-app:visible')).toContainText('hjälper dig känna igen fåglarna');
    // The look-alike has a record but no page in this build (unpublished): its name, no link, photo or comparison.
    const look = page.locator('.looks li');
    await expect(look).toHaveCount(1);
    await expect(look.locator('.look-name')).toHaveText('Större hackspett');
    await expect(look.locator('a, img')).toHaveCount(0);
    // The only species in "other": no "Fler ..." section.
    await expect(page.locator('.more')).toHaveCount(0);
    expect(errors).toEqual([]);
  });

  test('JSON-LD: brödsmulor och WebPage med taxon, foto, inspelning och kontrolldatum', async ({ page }) => {
    await page.goto('/sv/arter/talgoxe/');
    const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[];
    const crumbs = graph.find((n) => n['@type'] === 'BreadcrumbList')!;
    expect(crumbs.itemListElement.map((c: { name: string }) => c.name)).toEqual(['Birdy', 'Arter', 'Tättingar', 'Talgoxe']);
    expect(crumbs.itemListElement[3].item).toBe('https://birdy.community/sv/arter/talgoxe/');
    const web = graph.find((n) => n['@type'] === 'WebPage')!;
    expect(web.inLanguage).toBe('sv');
    expect(web.lastReviewed).toBe('2026-11-20');
    expect(web).not.toHaveProperty('reviewedBy');
    expect(web.about).toMatchObject({ '@type': 'Taxon', name: 'Parus major', sameAs: 'https://www.wikidata.org/wiki/Q25485' });
    expect(web.primaryImageOfPage).toMatchObject({ '@type': 'ImageObject', creditText: 'Testfotograf' });
    const src = await page.locator('audio').getAttribute('src');
    expect(web.associatedMedia).toMatchObject({ '@type': 'AudioObject', contentUrl: `https://birdy.community${src}`, license: 'https://creativecommons.org/licenses/by-sa/4.0/' });
    await page.goto('/sv/arter/parluggla/');
    const plain = (JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[]).find((n) => n['@type'] === 'WebPage')!;
    expect(plain).not.toHaveProperty('associatedMedia');
  });

  test('public domain: "public domain" i gemener utan licenslänk, okänd upphovsperson när namnet saknas', async ({ page }) => {
    await page.goto('/sv/arter/koltrast/');
    const extra = page.locator('[data-credit-for="extra"]');
    await expect(extra).toContainText('Foto: Testfotograf två, public domain, via Wikimedia Commons, nedskalad');
    await expect(extra.locator('a')).toHaveText(['Wikimedia Commons']);
    await page.goto('/sv/arter/kattuggla/');
    const audio = page.locator('[data-credit-for="audio"]');
    await expect(audio).toContainText('Inspelning: okänd upphovsperson, public domain, via Wikimedia Commons, bearbetad');
    await expect(audio.locator('a')).toHaveText(['Wikimedia Commons']);
    // JSON-LD says no more than the page: no licence link and no creator for this recording.
    const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[];
    const media = graph.find((n) => n['@type'] === 'WebPage')!.associatedMedia;
    expect(media['@type']).toBe('AudioObject');
    expect(media).not.toHaveProperty('license');
    expect(media).not.toHaveProperty('creator');
    await page.goto('/species/tawny-owl/');
    await expect(page.locator('[data-credit-for="audio"]')).toContainText('Recording: unknown recordist, public domain, via Wikimedia Commons, edited');
  });

  test('opublicerade, väntande och misslyckade arter ger 404', async ({ page }) => {
    for (const path of ['/sv/arter/storre-hackspett/', '/sv/arter/grongoling/', '/sv/arter/spillkraka/', '/species/black-woodpecker/']) {
      expect((await page.goto(path))?.status(), path).toBe(404);
    }
  });

  test('vänsterspalten följer med på dator när den får plats', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 1080 });
    await page.goto('/sv/arter/talgoxe/');
    await expect(page.locator('.left-inner')).toHaveClass(/is-sticky/);
    await page.evaluate(() => window.scrollTo({ top: 900, behavior: 'instant' }));
    const box = (await page.locator('.plate.hero').boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(76);
    expect(box.y).toBeLessThan(260);
  });

  // The column (photo, facts, app box, note) is about 820 px tall on the test data and up to 1 200 px with a
  // portrait photo: on a laptop it scrolls with the page, so the app box is reached by scrolling to it
  // instead of staying below the window edge until the end of the article (controller review, Task 10).
  for (const [width, height] of [[1440, 900], [1366, 657], [1024, 768]] as const) {
    test(`approtan nås genom att scrolla i ${width}x${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/arter/talgoxe/');
      await expect(page.locator('.left-inner')).not.toHaveClass(/is-sticky/);
      const app = page.locator('.sp-app:visible');
      const docTop = await app.evaluate((el) => el.getBoundingClientRect().top + window.scrollY);
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), docTop - 80);
      const box = (await app.boundingBox())!;
      expect(box.y).toBeGreaterThanOrEqual(0);
      expect(box.y + box.height).toBeLessThanOrEqual(height);
      // And it stays in the page: scrolling further moves it up, it does not stick below the edge.
      await page.evaluate(() => window.scrollBy({ top: 200, behavior: 'instant' }));
      expect((await app.boundingBox())!.y).toBeLessThan(box.y);
    });
  }

  test('mobilen: rubrik, foto, fakta, ingress och approta i den ordningen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/talgoxe/');
    const y = async (sel: string) => (await page.locator(sel).first().boundingBox())!.y;
    const order = [await y('h1'), await y('.plate.hero'), await y('.facts'), await y('.sp-lead'), await y('.looks-sec'), await y('.sp-app:visible'), await y('.note:visible'), await y('.more'), await y('.credits')];
    for (let i = 1; i < order.length; i += 1) expect(order[i]).toBeGreaterThan(order[i - 1]);
  });

  test('mobilen: tabbordningen följer det man ser, spelaren före Play-märket', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/arter/talgoxe/');
    const stops: string[] = [];
    // One pass through the page: stop when focus leaves the document (it reaches <body> before wrapping).
    for (let i = 0; i < 120 && stops.at(-1) !== 'body'; i += 1) {
      await page.keyboard.press('Tab');
      stops.push(await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el) return '';
        if (el.matches('[data-crumb]')) return 'crumb';
        if (el.tagName === 'AUDIO') return 'player';
        if (el.matches('a[href*="utm_campaign%3Dtalgoxe"]')) return 'badge';
        if (el.closest('.credits')) return 'credits';
        return el.tagName.toLowerCase();
      }));
    }
    const at = (stop: string) => stops.indexOf(stop);
    expect(at('crumb')).toBeGreaterThanOrEqual(0);
    expect(at('player')).toBeGreaterThan(at('crumb'));
    expect(at('badge')).toBeGreaterThan(at('player'));
    expect(at('credits')).toBeGreaterThan(at('badge'));
    expect(stops.filter((x) => x === 'badge')).toHaveLength(1);
  });

  test('"More in the … family" märker det latinska familjenamnet (engelska)', async ({ page }) => {
    await page.goto('/species/tawny-owl/');
    await expect(page.locator('.more h2')).toHaveText('More in the Strigidae family');
    await expect(page.locator('.more h2 span[lang="la"]')).toHaveText('Strigidae');
    await page.goto('/sv/arter/kattuggla/');
    await expect(page.locator('.more h2')).toHaveText('Fler egentliga ugglor');
    await expect(page.locator('.more h2 span[lang]')).toHaveCount(0);
  });

  for (const width of [360, 390, 430]) {
    test(`ingen sidledsscroll i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/arter/talgoxe/', '/species/great-tit/', '/sv/arter/parluggla/', '/sv/arter/blakraka/']) {
        await page.goto(path);
        await noSideScroll(page);
      }
    });
  }
});

test.describe('kategoriraden: den aktiva chipen syns helt, fri från kanttoningen', () => {
  // The active chip last in the row (Övriga fåglar), first (Alla arter on the hub) and second (Tättingar).
  // Before the fix the last chip was centred before the web fonts had loaded and ended up under the fade.
  for (const width of [1280, 390]) {
    for (const [path, label] of [['/sv/arter/blakraka/', 'Övriga fåglar'], ['/sv/arter/ovriga-faglar/', 'Övriga fåglar'], ['/sv/arter/', 'Alla arter'], ['/sv/arter/talgoxe/', 'Tättingar']] as const) {
      test(`${path} i ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        await expect(page.locator('.catbar .chip.is-active')).toContainText(label);
        await expect.poll(() => page.evaluate(() => {
          const row = document.querySelector('[data-chips]')!;
          const c = row.getBoundingClientRect();
          const a = row.querySelector('.chip.is-active')!.getBoundingClientRect();
          const fade = row.classList.contains('has-overflow') ? 32 : 0;
          return a.left >= c.left && a.right <= c.right - fade;
        })).toBe(true);
      });
    }
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
import MarginNote from '../ui/MarginNote.astro';
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
  reviewDate, shareHref, speciesHref, speciesImage, speciesTitle, taxonJsonLd, wikiSources,
  type Comparison, type LookAlikeView, type Species,
} from '../../lib/species';
import '../../styles/species.css';

interface Props { species: Species; locale: Locale }
const { species: s, locale } = Astro.props;
const t = getCopy(locale);
const other: Locale = locale === 'sv' ? 'en' : 'sv';
const text = s.text[locale];
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
// The whole photo on the paper colour, never cropped (the build draws it, see shareHref in species.ts).
const share = shareHref(s);
const content = await getImage({ src: heroImg, width: 1200, format: 'webp' });

const status = text.facts.swedenStatus ? t.species.statusLabels[text.facts.swedenStatus.value] : undefined;
const redCode = s.swedishRedList;
const redLabel = redCode
  ? redCode === 'not_listed' ? t.species.redListLabels.not_listed : `${t.species.redListLabels[redCode]} (${redCode})`
  : undefined;
const iucnLabel = s.iucn !== 'NE' ? (t.species.iucnLabels as Record<string, string>)[s.iucn] : undefined;
const familyShown = locale === 'sv' ? s.family.sv : s.family.latin;
const rel = related(s, all, locale);
// The group "other" ("Övriga fåglar" / "Other birds") has its own heading: the generic template gives
// "More other birds" in English (controller review 2026-10-07). GroupPage.astro has no "More" heading.
const moreFamily = locale === 'sv' ? s.family.sv.toLocaleLowerCase('sv') : s.family.latin;
const [moreBefore, moreAfter] = t.species.moreFamily.split('{family}');
const moreHeading = s.group === 'other'
  ? t.species.moreOther
  : t.species.moreGroup.replace('{group}', group.name[locale].toLocaleLowerCase(locale));
const marginalia = s.marginalia?.[locale];
// Latin names are tagged for pronunciation (same pattern as SpeciesCard and the English group page):
// the scientific name everywhere, the family name on the English page, where it is the Latin one.
const familyLang = locale === 'en' ? 'la' : undefined;

const months = s.data?.months;
const counties = s.data?.counties;
const summaryId = `data-summary-${s.qid}`;
// The sentences are the chart's and the map's text alternative, so they show only with them. An absent or
// rare species (data, but no months/counties, spec §9.2) gets no summary: its presence sentence is a data
// fact the written text already states (Koboltmes' fact d01), and the Artportalen credit still shows
// (reportData={Boolean(s.data)}), controller decision 2026-10-07.
const dataSentences = (s.data?.sentences[locale] ?? []).join(' ');
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

<Layout locale={locale} pathname={pathname} alternatePath={otherPath} title={title} description={text.metaDescription} ogImage={share} ogImageAlt={altHero} noindex={unpublished} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={otherPath} />
  <CategoryBar locale={locale} active={s.group} />
  <main id="main" tabindex="-1" class="sp wrap" data-species-page>
    {unpublished && <p class="sp-preview" data-preview-banner role="note">{t.species.previewBanner}</p>}
    <div class="spread">
      <header class="head">
        <nav class="sp-crumbs" aria-label={t.species.crumbLabel}>
          <ol>
            {crumbs.slice(0, -1).map((c) => <li><a href={c.href} data-crumb>{c.name}</a></li>)}
            <li><span aria-current="page" data-crumb>{name}</span></li>
          </ol>
        </nav>
        <Kicker text={familyShown} lang={familyLang} />
        <h1>{name}</h1>
        <p class="latin" lang="la">{s.names.scientific}</p>
      </header>

      <div class="left">
        <div class="left-inner">
          <figure class="plate hero" data-photo="hero">
            <Image src={heroImg} alt={altHero} widths={[480, 800, 1200]} sizes="(max-width: 1023px) 100vw, 440px" loading="eager" fetchpriority="high" decoding="async" />
            <figcaption><span>{t.species.plate.replace('{n}', '1')}, {name}</span><span>{t.species.photoCredit} {hero.author ?? t.species.unknownAuthor}</span></figcaption>
          </figure>
          <dl class="facts">
            <div><dt>{t.species.facts.scientific}</dt><dd><i lang="la">{s.names.scientific}</i></dd></div>
            <div><dt>{t.species.facts.family}</dt><dd lang={familyLang}>{familyShown}</dd></div>
            {status && <div><dt>{t.species.facts.sweden}</dt><dd>{status}</dd></div>}
            {text.facts.size && <div><dt>{t.species.facts.size}</dt><dd>{text.facts.size.value}</dd></div>}
            {redLabel && <div data-redlist><dt>{t.species.facts.swedishRedList}</dt><dd>{redLabel}</dd></div>}
            {iucnLabel && <div><dt>{t.species.facts.iucn}</dt><dd>{iucnLabel} ({s.iucn})</dd></div>}
          </dl>
          <aside class="sp-app sp-app--side">
            <p class="sp-app-h">{t.species.appHeadline}</p>
            <p>{appText(s, t)}</p>
            <PlayStoreBadge locale={locale} href={playHref(s.slug[locale], 'species')} alt={t.alt.playStoreBadge} size="small" />
          </aside>
          {marginalia && <div class="note note--side"><MarginNote text={marginalia} /></div>}
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
                    <MonthChart id={`chart-${s.qid}`} title={t.species.chartTitle} letters={t.species.monthLetters} monthNames={t.species.monthNames} series={[{ label: name, values: months, tone: 'rust' }]} describedBy={summaryId} />
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
              <p class="data-summary" id={summaryId}>{dataSentences}</p>
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
                        <Image src={photo} alt="" width={192} loading="lazy" decoding="async" />
                      </a>
                    )}
                    <div>
                      <p class="look-name">
                        {view.species ? <a href={speciesHref(view.species, locale)}>{view.name}</a> : view.scientificOnly ? <i lang="la">{view.name}</i> : view.name}
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
        {/* The same app box and note as in the left column, for the single-column layout below 1024 px,
            where they come after the look-alikes (spec §5). Only one copy is ever displayed: display: none
            takes the other out of the tab order and the accessibility tree, and its lazy badge image is not
            fetched (controller review, Task 10). */}
        <aside class="sp-app sp-app--flow">
          <p class="sp-app-h">{t.species.appHeadline}</p>
          <p>{appText(s, t)}</p>
          <PlayStoreBadge locale={locale} href={playHref(s.slug[locale], 'species')} alt={t.alt.playStoreBadge} size="small" />
        </aside>
        {marginalia && <div class="note note--flow"><MarginNote text={marginalia} /></div>}
        {rel.items.length > 0 && (
          <section class="more">
            {/* The family name is Latin on the English page and tagged so (controller review, Task 10). */}
            <h2>{rel.kind === 'family' ? <Fragment>{moreBefore}<span lang={familyLang}>{moreFamily}</span>{moreAfter}</Fragment> : moreHeading}</h2>
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
            reportData={Boolean(s.data)}
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
  /* Sticky only when the whole column fits in the window under the menu and the category bar (the script
     below sets .is-sticky), otherwise it scrolls with the page: on a laptop the app box would sit below the
     window edge until the end of the article (controller review, Task 10). The hero photos run from
     landscape to 2:1 portrait, so the column is 780 to 1 200 px tall and no fixed min-height fits.
     --sticky-top: the menu (76 px) and the category bar (about 55 px) plus a gap, close to species.css's
     scroll-padding-top (9.25rem); the script reads the same value. */
  .left-inner { --sticky-top: 150px; top: var(--sticky-top); display: flex; flex-direction: column; gap: 18px; }
  .left-inner.is-sticky { position: sticky; }
  .sp-app--flow, .note--flow { display: none; }
  .body { grid-area: body; padding-left: 36px; }
  h1 { font-size: clamp(40px, 4.6vw, 58px); line-height: 1.02; margin: 4px 0 0; overflow-wrap: anywhere; }
  .latin { margin: 2px 0 0; font-family: var(--font-script); font-size: 24px; color: var(--muted); }
  .sp-lead { font-size: 17px; line-height: 1.6; margin: 18px 0 0; max-width: 40rem; }
  .texts h2, .looks-sec h2, .more h2 { font-size: 24px; margin: 30px 0 8px; }
  .texts > p, .marks { font-size: 15.5px; line-height: 1.65; max-width: 40rem; margin: 0; }
  .marks { padding-left: 20px; list-style: disc; }
  .marks li { margin: 3px 0; }
  /* The summary sits under the chart, next to the taller map, instead of under both (no empty column). */
  .datafig { display: grid; grid-template-columns: minmax(0, 3fr) minmax(0, 2fr); grid-template-rows: auto 1fr; grid-template-areas: 'chart map' 'sum map'; gap: 18px 28px; align-items: start; margin-top: 16px; max-width: 40rem; }
  .datafig figure { margin: 0; }
  .fig-chart { grid-area: chart; }
  .fig-map { grid-area: map; }
  .datafig .data-summary { grid-area: sum; }
  .fig-h { font-family: var(--font-sans); font-size: 11.5px; letter-spacing: .14em; text-transform: uppercase; color: var(--rust); font-weight: 600; margin: 0 0 8px; }
  .fig-map { max-width: 220px; }
  .plate.extra { margin-top: 30px; max-width: 640px; }
  /* Four across on the desktop spread, as in the mockup (species.css's auto-fill gives 3 + 1 here). */
  @media (min-width: 1024px) {
    .more .sp-cards { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  }
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
    .datafig { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto; grid-template-areas: 'chart' 'map' 'sum'; }
  }
  @media (max-width: 1023px) {
    .spread { display: flex; flex-direction: column; gap: 18px; }
    .left, .left-inner, .body { display: contents; }
    /* No CSS order: the DOM order is the mobile order of spec §5, so focus follows what is seen. */
    .head { padding-left: 0; }
    .sp-lead { margin-top: 0; }
    .plate.extra { margin-top: 0; }
    .sp-app--side, .note--side { display: none; }
    .sp-app--flow, .note--flow { display: block; }
    .texts h2, .looks-sec h2, .more h2 { margin-top: 18px; }
  }
</style>

<script>
  // Sticky only when the column fits (see .left-inner in the styles): measured, because the photo's shape
  // decides the column's height. Re-measured when the column or the window changes size (photos, fonts).
  const inner = document.querySelector<HTMLElement>('.left-inner');
  if (inner) {
    const desktop = window.matchMedia('(min-width: 1024px)');
    const MARGIN = 16;
    // The fallback is --sticky-top's own value: a top that can't be read must not make a too tall column fit.
    const STICKY_TOP = 150;
    const update = () => {
      const top = parseFloat(getComputedStyle(inner).getPropertyValue('--sticky-top')) || STICKY_TOP;
      inner.classList.toggle('is-sticky', desktop.matches && inner.offsetHeight + top + MARGIN <= window.innerHeight);
    };
    update();
    new ResizeObserver(update).observe(inner);
    window.addEventListener('resize', update);
    desktop.addEventListener('change', update);
  }
</script>
```

- [ ] **Step 4: Routen med arter**

Ersätt `src/lib/species-routes.ts` med:

```ts
import type { Locale } from './i18n';
import { ABOUT_SLUG, GROUPS, activeGroups, assertUniqueSlugs, getAllRecords, getAllSpecies } from './species';
import { hasPageContract } from './species-source.mjs';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  // Checked against all 15 GROUPS, not just the active ones (controller review, Task 8 fix wave): a slug
  // collision must fail the very first build, not wait for the publish that happens to activate the
  // colliding group, by which point the build has looked clean for however long the group sat empty.
  // Every species that has the page contract, built in this build or not (controller review, Task 10): the
  // same reasoning, a collision with a verified but unpublished species must not wait for its publication.
  const contract = (await getAllRecords()).filter((r) => hasPageContract(r));
  assertUniqueSlugs([...contract.map((s) => s.slug[locale]), ...GROUPS.map((g) => g.slug[locale]), ABOUT_SLUG[locale]], locale);
  const groups = activeGroups(all);
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

**Tillägg (Task 8:s granskning 2026-10-07): samma familjeregel här.** Artsidans familjerad (`t.species.facts.family`) och "Fler {familj}"-rubriken (`moreFamily`) visar artens EGNA `family.sv`/`family.latin` direkt (koden ovan, raderna med `familyShown`). Det är ingen tvetydighet här som på gruppsidan, eftersom en artsida bara handlar om EN art, inte en lista av arter som kan ha olika `family.sv`-stavningar för samma `family.latin`. Samma `related()`-funktion (grupperar på `family.latin`, se Task 4) används för att hitta vilka andra arter som räknas som samma familj till "Fler"-länken; de visade NAMNEN kommer fortfarande från den aktuella arten själv, inte en aggregerad etikett. Gruppsidans `GroupPage.astro` (Task 8) löser den tvetydigheten med en stabil "första förekomst vinner"-regel, tills pipelinen skriver ett kanoniskt svenskt familjenamn per latinsk familj.

- [ ] **Step 5: Kör testerna**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/species.spec.ts`
Expected: PASS (alla). Om datumformatet skiljer (Node utan full ICU ger "November 20, 2026"): kontrollera `node -p "new Intl.DateTimeFormat('sv-SE',{day:'numeric',month:'long',year:'numeric'}).format(new Date('2026-11-20'))"`. Node 22 har full ICU som standard och ger `20 november 2026`.

- [ ] **Step 6: Commit**

```bash
git add src/components/species/SpeciesArticle.astro src/lib/species-routes.ts src/components/species/SpeciesRoute.astro tests/species.spec.ts src/components/ui/Kicker.astro src/content/copy.sv.json src/content/copy.en.json tests/fixtures/make-species-fixtures.mjs tests/fixtures/species/Q25411.json tests/unit/species-source.unit.mjs
git commit -m "feat(website): artsidan med diagram, karta, inspelning, förväxlingsarter, granskningsrad och JSON-LD"
```

(Tillägg, controller-granskning 2026-10-07: `reportData={Boolean(s.data)}` (kodblocket ovan, var `Boolean(months || counties)`) så att en art som förekommer så sällan att den inte får diagram eller karta (spec §9.2, "För lite data") ändå visar datacrediten: `data` finns (`totalReports` och förekomstmeningen), bara `months`/`counties` saknas. Task 1:s fixturgenerator har sedan samma granskning en art för just detta: Blåkråka (`Q25411`, `absent: true` i `SPECIES`-arrayen), `data.sentences` är bara "Förekommer inte i Sverige: inga rapporter i Artportalen 2016 till 2025." / "Does not occur in Sweden: no reports in Artportalen 2016 to 2025.", inget `months`/`counties`, inget `swedishRedList`. Den var `publish: false` (som de två hackspettarna) fram till Task 10, som publicerade den, se avvikelserna nedan. Datameningarnas ordval är dessutom ändrat rakt över (`Rapporteras sällan i …` i stället för `Nästan aldrig i …`, `Rapporteras från alla 21 län.`/`Andelen av alla fågelrapporter är högst i …` i stället för `Vanligast i rapporterna från …`, pipelinens fix wave 2026-10-07 efter att den här planen skrevs); Steg 1s `.data-summary`-rad ovan är uppdaterad till `'Rapporteras året runt. Rapporteras från alla 21 län.'`.)


**Avvikelser vid genomförandet (Task 10, 2026-10-07; kodblocken ovan är byte-identiska med filerna):**

1. **Blåkråka är publicerad i testdatan** (`publish: true`, var `false`), så att sidtypen "frånvarande art" byggs och testas som en vanlig sida; riktig data har en sådan art redan (Koboltmes, `Q10546857`). Den gör gruppen `other` aktiv med en art, så Task 7:s och 8:s räkningar steg med en (kodblocken där är ändrade: `.groups a` 8, ingångssidans `[data-item]` 17, gruppsidans `.catbar .chip` 9) och enhetstestet `builtSpeciesMedia` räknar 19 foton i `dist/`. Dess förväxlingsart är Större hackspett, en post utan sida i standardbygget (samma fall som Koboltmes och Blåmes i riktig data): namnet visas utan länk, foto och jämförelselänk.
2. **Datameningen visas bara med diagram eller karta**, som i planen, och står under diagrammet bredvid kartan (`grid-template-areas`), inte under båda, så att vänsterkolumnen inte lämnar ett tomt fält bredvid den höga kartan. Datacrediten visas däremot när arten har `data` (`reportData={Boolean(s.data)}`). (Controllern beslöt först att visa meningen även utan diagram och karta, men ändrade sig samma kväll: för en frånvarande art som Koboltmes står förekomstmeningen redan i texten, fakta `d01`, och hade stått två gånger efter varandra.)
3. **`moreOther`** ("Fler övriga fåglar" / "More birds", ny nyckel i båda copy-filerna och i specens bilaga A) för gruppen `other`, i stället för mallen som gav "More other birds". `GroupPage.astro` har ingen "Fler"-rubrik, så bara artsidan berörs.
4. **Marginalanteckningen är `MarginNote`** (specen §5 nämner komponenten) inuti `<div class="note">`, i stället för ett eget `<p class="note">` med egna stilar.
5. **`lang="la"`** på det vetenskapliga namnet (rubriken, faktalistan, förväxlingsarter utan fil) och på familjen på den engelska sidan (kickern och faktalistan), samma mönster som `SpeciesCard` och gruppsidan. `Kicker.astro` fick därför en valfri `lang`-prop.
6. **Punkter i kännetecknen** (`list-style: disc`; Tailwinds grundstilar tar bort dem) och **fyra kort i bredd** under "Fler ..." på dator, som i mockupen (`species.css`s `auto-fill` gav 3 + 1).
7. **Fler tester:** JSON-LD (brödsmulor, `WebPage` med taxon, foto, inspelning och `lastReviewed`, ingen `reviewedBy`), den frånvarande arten, "public domain" och "okänd upphovsperson" (Task 9:s omgranskning), stapelns titel utan siffra, och Blåkråka i sidledsscrollkontrollen.
8. **Kategoriraden (Task 6:s `CategoryBar.astro`, kodblocket där är uppdaterat):** den aktiva chipen hamnade delvis under högerkantens toning när den var sist i raden (Övriga fåglar på 1280 px), eftersom raden centrerades innan webbtypsnitten laddats och chipsen blev bredare efteråt. Nu ska chipen ligga helt synlig och fri från toningen (32 px) och vänsterkanten, annars centreras den, begränsat till radens scrollområde, och samma kontroll körs igen när `document.fonts.ready` löser sig, om besökaren inte redan rört raden. Fortfarande momentant, aldrig mjukt, så reducerad rörelse behöver inget extra. Testet "kategoriraden: den aktiva chipen syns helt" (sist, först och näst först, 1280 och 390 px) failade på den gamla koden för båda sista-fallen i båda bredderna.
9. **Task 10:s granskning (2026-10-07, `5f1197ba`):**
   - **I1, vänsterspalten är sticky bara när hela spalten får plats** i fönstret under menyn och kategoriraden. Granskningen föreslog en mediaregel (`min-height: 980px`), men huvudfotona går från liggande till 2:1 stående (32 av de 180 riktiga är högre än 0,8 : 1), så spalten är 780 till 1 200 px hög och ingen fast höjd passar. Ett litet skript mäter spalten (`ResizeObserver` och fönstrets storlek) och sätter `.is-sticky`; utan JavaScript scrollar spalten med sidan. Testerna "approtan nås genom att scrolla" i 1440x900, 1366x657 och 1024x768 failar med en spalt som alltid är sticky (provat), och sticky-testet körs nu i 1920x1080 (testdatans spalt, 818 px, får inte plats i 900 px).
   - **I2, approtan och marginalanteckningen finns två gånger**, i vänsterspalten (dold under 1024 px) och efter förväxlingsarterna (dold från 1024 px). `display: none` tar bort den dolda ur tabbordningen och tillgänglighetsträdet, och dess lata Play-bild hämtas inte. CSS `order` är borta: DOM-ordningen är specens mobilordning. Tester som räknar `.sp-app` räknar `:visible`, och ett tabbtest i 390 px kontrollerar brödsmulor, spelare, Play-märket och credits i den ordningen, märket en gång.
   - **I3, `COMPARISONS_ENABLED = false`** i `lib/species.ts` (Task 4:s block har fått flaggan): `getComparisons()` ger inget, ingångssidan döljer "Lätta att blanda ihop" och förväxlingsarterna får ingen jämförelselänk, tills Task 11 bygger sidorna. Task 7:s och 10:s tester är ändrade därefter, med de gamla raderna som kommentarer för Task 11.
   - **M1** förväxlingsartens miniatyr är `width={192}` (en fil, rätt `width`/`height`), **M2** slugvakten i `species-routes.ts` går över alla poster med sidkontrakt (`hasPageContract`), byggda eller inte, **M3** `lang="la"` på familjen i "More in the … family", **M5** `s.text[locale]` utan `!`.
10. **Granskningen av Task 10:s fixar och Task 12 (2026-10-07, "Approved with minors", egen commit efter Task 14):**
   - **Delningsbilden (`og:image`) beskär inte fågeln** (controllerns beslut): hela huvudfotot på papperets färg (`--paper` ur `src/styles/tokens.css`) i 1200 × 630. Astros `getImage` klarar inte det (`fit: 'contain'` fyller med svart, `background` plattar bara till genomskinlighet), så `astro.config.mjs` ritar bilderna med sharp i byggkroken `birdy-species-share`, bara för arter som får en sida, under `/og/species/<QID>.<hash>.jpg` (`sharePublicPath` i `species-source.mjs`, hashen täcker fotot, färgen, storleken och JPEG-kvaliteten; ritade bilder återanvänds ur `node_modules/.cache/birdy-share/`). Den virtuella modulen exporterar `share` (QID till adress), `shareHref(s)` i `species.ts` läser den. `check-seo.mjs` kräver att varje ny sidas `og:image` finns i bygget, `check-preview-build.mjs` att det finns exakt en delningsbild per byggd art i varje bygge, nytt enhetstest för `sharePublicPath`/`paperColour` och ett Playwright-test som läser bildens pixlar (papper i kanterna, fotot i mitten, 1200 × 630, samma bild på SV och EN).
   - **`--sticky-top`:** vänsterspaltens avstånd till fönstrets överkant är en egen variabel (150 px) som stilen och skriptet läser, och skriptets reservvärde är samma 150 (förut 0, som hade fått en för hög spalt att se ut att rymmas om värdet inte gick att läsa).
   - **Specen** §5 och §13 beskriver vänsterspalten som den fungerar (sticky bara när hela spalten ryms, skriptet sätter `.is-sticky`).

---

### Task 11: Jämförelsesidorna

**Tillägg (Task 10:s granskning 2026-10-07):** jämförelserna är avstängda med `COMPARISONS_ENABLED = false` i `lib/species.ts` tills den här tasken, eftersom Task 11 körs efter Task 14 och 16 (de första artsidorna går live 9 oktober). Första steget här är att sätta flaggan till `true` och återställa testerna som Task 10 ändrade: ingångssidans `[data-compare-link]` till 2, testet "jämförelserna har namnen i svensk ordning" (`['Blåmes eller talgoxe', 'Kaja eller skata']`), talgoxtestets `.look-compare` (`href` `/sv/arter/blames-eller-talgoxe/`, text "Jämför blåmes och talgoxe") och den engelska sidans `.look-compare` ("Compare the Eurasian Blue Tit and the Great Tit"). De gamla raderna står som kommentarer i `tests/species.spec.ts`.

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
  <main id="main" tabindex="-1" class="cmp wrap" data-comparison-page>
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
            monthNames={t.species.monthNames}
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

(Tillägg, controller-granskning 2026-10-07: `<MonthChart>` behöver `monthNames={t.species.monthNames}` (se Task 9s tillägg). **Håll seriernas ordning identisk i diagrammet och i `.clegend`:** båda bygger på samma `pair.map((p, i) => …)` med samma `tones`-lista, i samma `pair`-ordning (Task 4s `comparisonPair()`, låst till den svenska slug-ordningen), så den första stapelfärgen (rust) och den första legendraden pekar alltid på samma art. De två listorna är ändå separat kod (en `series`-array till `<MonthChart>`, en egen `<ul class="clegend">`-rendering): ändra aldrig den ena utan den andra, och lägg aldrig till ett sorterings- eller filtersteg på bara den ena. `MonthChart`s nya per-stapel-`<title>` (`Series.label`) gör en felaktig ordning mer synlig, inte mindre: hovrar man en stapel ser man artnamnet direkt, så en legend som pekar fel sticker ut.)

- [ ] **Step 4: Routen med jämförelser**

Ersätt `src/lib/species-routes.ts` med:

```ts
import type { Locale } from './i18n';
import { ABOUT_SLUG, GROUPS, activeGroups, assertUniqueSlugs, getAllSpecies, getComparisons } from './species';

/** Every page under /species/ and /sv/arter/ except the hub and the about page (spec §4). */
export async function speciesPaths(locale: Locale) {
  const all = await getAllSpecies();
  const comparisons = await getComparisons();
  // Checked against all 15 GROUPS, not just the active ones (carried over from Task 8's fix wave): a
  // slug collision must fail the very first build, not wait for the publish that activates the group.
  assertUniqueSlugs([
    ...all.map((s) => s.slug[locale]),
    ...GROUPS.map((g) => g.slug[locale]),
    ...comparisons.map((c) => c.slug[locale]),
    ABOUT_SLUG[locale],
  ], locale);
  const groups = activeGroups(all);
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
    ['/sv/arter/om-artsidorna/', 'Så gör vi artsidorna', '/species/about-these-pages/', ['Källorna', 'Så används AI', 'Kontrollen', 'Licenserna', 'Rättelser']],
    ['/species/about-these-pages/', 'How we make the species pages', '/sv/arter/om-artsidorna/', ['The sources', 'How AI is used', 'The checks', 'The licences', 'Corrections']],
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
  test('om-sidan är indexerbar när det finns arter och har JSON-LD med författare och utgivare', async ({ page }) => {
    await page.goto('/sv/arter/om-artsidorna/');
    await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    const graph = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent())!)['@graph'] as Record<string, any>[];
    const web = graph.find((n) => n['@type'] === 'WebPage')!;
    expect(web.author).toMatchObject({ '@type': 'Person', name: 'Albin Abrahamsson' });
    expect(web.publisher).toMatchObject({ '@type': 'Organization', name: 'AlbIT AB' });
    expect(graph.find((n) => n['@type'] === 'BreadcrumbList')!.itemListElement.map((c: { name: string }) => c.name)).toEqual(['Birdy', 'Arter', 'Så gör vi artsidorna']);
  });

  for (const [path, words] of [
    ['/sv/arter/om-artsidorna/', ['Claude Opus 5.5', 'Claude Sonnet 5', 'ljudmodell', 'stickprov', 'CC BY-SA 4.0', 'CC0', 'Hittade du ett fel?']],
    ['/species/about-these-pages/', ['Claude Opus 5.5', 'Claude Sonnet 5', 'sound model', 'sample', 'CC BY-SA 4.0', 'CC0', 'Found a mistake?']],
  ] as const) {
    test(`${path} beskriver modellerna, kontrollerna, licenserna och felrapporten`, async ({ page }) => {
      await page.goto(path);
      for (const word of words) await expect(page.locator('main')).toContainText(word);
    });
  }
});
```

(Rättat i efterhand, Task 7:s granskning 2026-10-07: testets rubriklista stod kvar som "Granskningen"/"The review" fast copy-texten redan hette "Kontrollen"/"The checks" sedan Task 5:s fixvåg, se noten vid Step 2 i Task 5 ovan. Verbatim-testet är det som skulle ha fångat det, inte lämnat det: rättat innan den här tasken ens är kodad. Komponentkoden i Step 3 nedan är också rättad med samma vågs andra fynd: `noindex` på om-sidan när `getAllSpecies()` är tom, samma regel och skäl som hubsidan (`SpeciesHub.astro`), men sidans eget innehåll döljs inte: bara `noindex` ändras. Komplettera `scripts/check-empty-hub.mjs` (finns sedan Task 7) med samma två kontroller för `sv/arter/om-artsidorna` och `species/about-these-pages` när den här tasken är klar.)

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
import { SITE, aboutHref, breadcrumbJsonLd, getAllSpecies, hubHref } from '../../lib/species';
import '../../styles/species.css';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const a = t.speciesAbout;
const other: Locale = locale === 'sv' ? 'en' : 'sv';
// Same rule as the hub (SpeciesHub.astro, controller review 2026-10-07): before Task 16 has published the
// first real species, nothing on the species pages is worth a search engine's attention yet. Unlike the
// hub, the content here doesn't depend on the species list, so only `noindex` changes, nothing is hidden.
const speciesCount = (await getAllSpecies()).length;
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

<Layout locale={locale} pathname={pathname} alternatePath={aboutHref(other)} title={a.title} description={a.description} noindex={speciesCount === 0} jsonLd={jsonLd}>
  <Nav locale={locale} variant="solid" switchLangHref={aboutHref(other)} />
  <main id="main" tabindex="-1" class="about wrap">
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

**Avvikelser vid genomförandet (Task 12, 2026-10-07; `048e070f`, kodblocket för komponenten och routerna oförändrat):**

1. **Texterna (`speciesAbout`, Task 5:s copyblock ovan är uppdaterade) är kontrollerade mening för mening mot pipelinen på `origin/data/artsidor`** (`tools/content-pipeline/src/birdy_fetcher/web/`), inte mot planens äldre ordval: Claude Opus 5.5 tar ut högst 30 fakta med ordagranna citat (`defaults.py`, `facts.py`: `MAX_FACTS`, `quote_in_sources`) och skriver texten bara ur verifierade fakta (`text_step.facts_verified`, `render_facts` visar bara faktatexten); Claude Sonnet 5 gör V1 (`verify.py`, `partial`/`unsupported` stryks) och meningskontrollen (`checker.py`, en omskrivning, sedan stryks det som fortfarande inte stöds, `checked_writer.py`); V2 jämför längd, vingbredd, vikt och kullstorlek med 15 % tolerans och flaggar; V3 jämför statusen med Artportalen och rödlistan och flaggar; V4 kör appens BirdNET-modell (`tools/ml-eval/flexref`, samma `.tflite` som appen), prövar upp till tre andra Commons-inspelningar (`MAX_ALTERNATIVES`) och flaggar en art som modellen inte täcker eller bara svagt känner igen; en art utan flaggor får `verification` direkt och publiceras automatiskt, en flaggad väntar på Albins beslut (`verify_step.py`); stickprovet är 2 per 40 publicerade (`review_sheet.py`); fotona nedskalas och sparas som WebP utan beskärning (`images.py`); inspelningarna högst 20 s, mono, `loudnorm`, MP3 (`audio.py`); Artportalens och rödlistans GBIF-dataset är CC0 (kontrollerat mot GBIF:s API 2026-10-07); diagram och karta kräver 200 rapporter (`datamod.MIN_REPORTS`). Ljudmodellen kallas "ljudmodellen som känner igen läten i Birdy-appen", inte "Birdys egen", eftersom det är BirdNET.
2. **Licensavsnittet** säger nu att varje sida länkar till artiklarnas revision och att rapportdatan är CC0 via GBIF; **Rättelser** nämner länken "Hittade du ett fel? Skriv till oss." längst ned på varje artsida.
3. **Titeln** säger "källor och kontroll" / "sources and checks" i stället för "granskning" / "review" (specens §12-tabell ändrad).
4. **Fler tester:** indexerbar med arter, JSON-LD med `author` och `publisher` och brödsmulor, och att sidan nämner modellerna, ljudmodellen, stickprovet, licenserna, CC0 och felrapporten.
5. **`scripts/check-empty-hub.mjs`** kontrollerar nu även om-sidorna vid noll arter: `noindex`, h1, alla fem avsnitt och mejllänken i `<main>`, och beskrivningens längd.
6. **Granskningen av Task 12 (2026-10-07, minors, egen commit efter Task 14):** licensstycket säger att texterna är skrivna **med AI** ur fakta från Wikipedia (som specens §8 och textcrediten), och AI-stycket säger vad pipelinen gör med gränsen 30: modellen plockar ut fakta, de utan giltigt citat stryks, och blir det fler än 30 behåller programmet 30 och tar dem turvis från ämnena (`MAX_FACTS` och `cap_by_topic` i `web/facts.py` på `data/artsidor`). Task 5:s copyblock är uppdaterade.

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

  // Only indexed groups (Task 13's review): on the test data six of the eight groups have one species each and
  // noindex, so the column has Songbirds, Owls and All species.
  test('sidfotens grupplänkar och Alla arter leder till indexerade sidor som finns', async ({ page, request }) => {
    await page.goto('/');
    const column = page.locator('footer.footer .col').first();
    await expect(column.locator('.fh')).toHaveText('Species');
    const links = column.locator('a');
    await expect(links).toHaveText(['Songbirds', 'Owls', 'All species A to Z']);
    for (const href of await links.evaluateAll((els) => els.map((e) => e.getAttribute('href')!))) {
      const res = await request.get(href);
      expect(res.status(), href).toBe(200);
      expect(await res.text(), href).not.toContain('content="noindex');
    }
  });

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

  // Task 11 adds '/sv/arter/blames-eller-talgoxe/' to this list when comparisons are turned on.
  test('Arter är markerad i menyn under hela /sv/arter/', async ({ page }) => {
    for (const path of ['/sv/arter/', '/sv/arter/ugglor/', '/sv/arter/talgoxe/', '/sv/arter/om-artsidorna/']) {
      await page.goto(path);
      await expect(page.locator('#site-nav .links a[aria-current="page"]'), path).toHaveText('Arter');
    }
  });

  test('menyraden får plats i 1024 px', async ({ page }) => {
    await page.setViewportSize({ width: 1024, height: 768 });
    for (const path of ['/sv/', '/', '/sv/arter/talgoxe/']) {
      await page.goto(path);
      const heights = await page.locator('#site-nav .links a').evaluateAll((els) => els.map((e) => e.getBoundingClientRect().height));
      for (const h of heights) expect(h, path).toBeLessThan(30);
      const [nav, cta] = await Promise.all([page.locator('#site-nav .links').boundingBox(), page.locator('#site-nav .nav-cta').boundingBox()]);
      expect(nav!.x + nav!.width, path).toBeLessThanOrEqual(cta!.x);
    }
  });

  test('hoppa till innehållet: första tabbstoppet, och nästa stopp ligger i main', async ({ page }) => {
    await page.goto('/sv/arter/talgoxe/');
    await expect(page.locator('.skip-link')).not.toBeInViewport();
    await page.keyboard.press('Tab');
    await expect(page.locator(':focus')).toHaveText('Hoppa till innehållet');
    await expect(page.locator(':focus')).toBeInViewport();
    await page.keyboard.press('Enter');
    // main takes focus (tabindex="-1"), without a ring around the whole page (Task 13's review).
    await expect(page.locator('main#main')).toBeFocused();
    expect(await page.locator('main#main').evaluate((el) => getComputedStyle(el).outlineStyle)).toBe('none');
    await page.keyboard.press('Tab');
    expect(await page.evaluate(() => Boolean(document.activeElement?.closest('main#main')))).toBe(true);
    for (const path of ['/', '/sv/blog/', '/legal/privacy/']) {
      await page.goto(path);
      await expect(page.locator('main#main')).toHaveCount(1);
    }
  });
});
```

- [ ] **Step 2: Kör och se dem faila**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test tests/home.spec.ts tests/species.spec.ts -g "meny|sidfot"`
Expected: FAIL (ingen länk "Arter" än)

- [ ] **Step 3: Menyn**

**Villkor (Task 7:s granskning 2026-10-07):** "Arter" ska bara synas när det finns något att visa. Innan Task 16 publicerat den första riktiga arten (Task 15 Step 4: produktionsbygget går igenom med noll publicerade arter) är `getAllSpecies()` tom: menyn ska då se ut som i dag, utan länken, i stället för att peka på en hub som själv bara visar en "på väg"-rad.

I `src/components/Nav.astro`, lägg till importen `import { hasSpecies as anySpecies, hubHref } from '../lib/species';` och ersätt `const links = [ ... ];` med:

```ts
const speciesHub = hubHref(locale);
const hasSpecies = await anySpecies();
const links = [
  ...(hasSpecies ? [{ href: speciesHub, label: t.nav.species }] : []),
  { href: `${home}#how-it-works`, label: t.nav.howItWorks },
  { href: `${home}#app`, label: t.nav.app },
  { href: `${home}#premium`, label: t.nav.premium },
  { href: fieldNotesHref(locale), label: t.nav.fieldNotes },
];
// "Species" is current on the hub, group, species, comparison and about pages.
const isCurrent = (href: string) => href === here || (hasSpecies && href === speciesHub && here.startsWith(speciesHub));
```

Byt båda förekomsterna av `aria-current={l.href === here ? 'page' : undefined}` mot `aria-current={isCurrent(l.href) ? 'page' : undefined}`.

Kontrollera i 1024 px att menyraden fortfarande får plats (fem länkar, språkbytet och knappen). Om den bryter: sänk `gap` i `.links` från `26px` till `20px` i `@media (max-width: 1180px)`.

Lägg till ett test som kör mot `npm run build:prod` (riktigt läge, `src/data/species/` är tom i dag) eller en egen testdatavariant med noll arter: menyn ska INTE innehålla "Arter"/"Species" då. `scripts/check-empty-hub.mjs` (finns sedan Task 7) är en bra plats att utöka: läs `dist-empty/`s startsida och kontrollera att `#site-nav` saknar en länk till `/sv/arter/`.

- [ ] **Step 4: Sidfoten**

I `src/components/Footer.astro`, lägg till importerna `import { footerSpecies } from '../lib/species-nav.mjs';` och `import { COMMON_QIDS, footerGroups, getAllSpecies, groupHref, hubHref, speciesHref } from '../lib/species';` och efter `const blogPrefix = ...`:

```ts
const allSpecies = await getAllSpecies();
const { column: speciesColumn, common } = footerSpecies(allSpecies, COMMON_QIDS);
// Indexed groups only (Task 13's review): a group under three species has noindex, so the footer of every page
// shouldn't point there. "All species" is always there.
const topGroups = footerGroups(allSpecies);
```

Lägg till en ny kolumn före `<div class="col">` med `t.footer.explore`, villkorad på `speciesColumn` (`hasSpeciesPages` i `species-nav.mjs`, samma skäl som menyns länk i Step 3: en kolumn med grupplänkar till en hub som bara visar "på väg" är värre än ingen kolumn alls, innan Task 16 publicerat den första arten):

```astro
      {speciesColumn && (
        <div class="col">
          <h2 class="fh">{t.footer.species}</h2>
          {topGroups.map((g) => <a href={groupHref(g, locale)}>{g.name[locale]}</a>)}
          <a href={hubHref(locale)}>{t.footer.allSpecies}</a>
        </div>
      )}
```

Lägg till före `<div class="fbot">`:

```astro
    {common.length > 0 && (
      <p class="fpop"><span class="fpop-h">{t.footer.commonSpecies}</span>{common.map((s) => <a href={speciesHref(s, locale)}>{s.names[locale]}</a>)}</p>
    )}
```

**Ändrat 2026-10-07 (Task 4:s fixvåg; sedan Task 13:s granskning gör `footerSpecies()` i `species-nav.mjs` det, `commonSpecies()` är borttagen):** `commonSpecies()` stoppar inte längre bygget när någon av de tolv saknar sida, utan returnerar de av de tolv som har en sida i bygget, i listans ordning. Arterna publiceras en i taget (Task 16) och koden ligger på `main` innan något är publicerat (Task 15 Step 4), så listan är tom eller ofullständig länge. Raden "Vanliga arter" döljs när listan är tom (villkoret ovan); testa både en tom och en ofullständig lista.

**Tillägg (Task 7:s granskning 2026-10-07, skild från ovanstående):** `common.length > 0` döljer bara raden "Vanliga arter". Den NYA kolumnen "Arter" (grupplänkarna + "Alla arter", ovan) döljs av ett eget villkor, `allSpecies.length > 0`: de två kan skilja sig åt: har bara en enda icke-vanlig art publicerats finns kolumnen (grupplänkar att visa) men inte raden (ingen av de tolv vanliga är med än). Testa alla tre lägen: noll arter (ingen kolumn, ingen rad), några arter men ingen av de tolv vanliga (kolumn, ingen rad), minst en av de tolv (kolumn och rad).

I `<style>`: ändra `.fgrid { display: grid; grid-template-columns: 1.6fr 1fr 1fr 1fr; gap: 40px; }` till `grid-template-columns: 1.6fr repeat(4, 1fr);` och lägg till:

```css
  .fpop { margin: 40px 0 0; display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px 16px; font-size: 13.5px; }
  .fpop-h { font-size: 11px; letter-spacing: .16em; text-transform: uppercase; color: var(--apricot); font-weight: 600; margin-right: 4px; }
  /* same colour as .col a (alpha checked in scripts/check-contrast.mjs) */
  .fpop a { color: rgba(233, 226, 210, .82); text-decoration: underline; text-decoration-color: rgba(233, 226, 210, .3); text-underline-offset: 3px; transition: color .2s; }
  .fpop a:hover { color: var(--apricot); }
```

- [ ] **Step 5: Startsidans länk**

I `src/components/Guide.astro`, lägg till importen `import { hasSpecies as anySpecies, hubHref } from '../lib/species';`, `const hasSpecies = await anySpecies();` i frontmattern, och efter `<CoverageMap locale={locale} />`, villkorad på samma sätt som menyn och sidfoten (Step 3 och 4): en länk till en hub som bara säger "på väg" hjälper ingen innan Task 16 publicerat den första arten.

```astro
    {hasSpecies && (
      <p class="browse"><a href={hubHref(locale)}>{t.guide.browse} <span aria-hidden="true">→</span></a></p>
    )}
```

Och i komponentens `<style>`:

```css
  .browse { margin: 28px 0 0; font-weight: 600; }
  .browse a { color: var(--rust); border-bottom: 1px solid currentColor; padding-bottom: 2px; }
```

**Tillägg (Task 7:s granskning 2026-10-07): en hoppa-till-innehållet-länk.** `CategoryBar.astro` (Task 6) lägger upp till 16 chips (en per grupp) i tab-ordningen före sökfältet och sidans eget innehåll: en tangentbords- eller skärmläsaranvändare som landar på en artsida måste nu tabba genom menyn OCH hela kategoriraden innan `<main>`. Lägg till en osynlig-tills-fokuserad hoppa-länk högst upp i `Layout.astro` (den delas av alla sidor, inte bara artsidorna, men kostar inget på de andra): `<a href="#main" class="skip-link">{t.nav.skipToContent}</a>` som första barn i `<body>`, pekar på ett `id="main"` tillagt på varje sidas `<main>`-element (de delade artsides-komponenterna har redan `<main class="hub wrap">` etc.: lägg till `id="main"` där; startsidan, bloggen och juridiksidorna har sina egna `<main>`, samma sak). Nya copy-nycklar `nav.skipToContent`: "Hoppa till innehållet" / "Skip to content". Stil (ändrad i Task 13:s granskning): `.skip-link { position: absolute; left: 12px; top: 12px; z-index: 300; ...; transform: translateY(calc(-100% - 24px)); transition: transform .2s; } .skip-link:focus { transform: none; }` (osynlig förrän den tabbas till, dyker sedan upp överst till vänster; flyttad med sin egen höjd, så att större text inte lämnar en kant synlig), och `<main id="main" tabindex="-1">` med `main[tabindex="-1"]:focus { outline: none; }` så att fokus hamnar i `main` utan en ram runt hela sidan. Test: `page.keyboard.press('Tab')` en gång på en artsida ska fokusera länken (`page.locator(':focus')` har texten "Hoppa till innehållet"), och att aktivera den (Enter eller `page.locator('#main')`-kontroll av fokus) hoppar förbi menyn och kategoriraden.

- [ ] **Step 6: Kör alla webbtester**

Run: `npm run build:fixtures && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS (hela sviten, även de gamla testerna)

- [ ] **Step 7: Commit**

```bash
git add src/components/Nav.astro src/components/Footer.astro src/components/Guide.astro tests/home.spec.ts tests/species.spec.ts src/layouts/Layout.astro src/styles/global.css src/content/copy.sv.json src/content/copy.en.json src/lib/species.ts src/lib/species-nav.mjs tests/unit/species-nav.unit.mjs scripts/check-empty-hub.mjs src/layouts/LegalLayout.astro src/pages/legal/index.astro src/components/HomePage.astro src/components/FieldNoteArticle.astro src/components/FieldNotesIndex.astro src/components/species/
git commit -m "feat(website): Arter i menyn, sidfoten och startsidans uppslagsverk"
```

**Avvikelser vid genomförandet (Task 13, 2026-10-07, `981cbe6f`):**

1. **Testblocket ovan är byte-identiskt med filen.** Jämförelsesidan `/sv/arter/blames-eller-talgoxe/` är struken ur menytestet tills Task 11 slår på `COMPARISONS_ENABLED` (Task 10:s granskning); Task 11 lägger tillbaka den. Nya tester: sidfotens kolumnlänkar (fem grupper och Alla arter) svarar 200, menyraden får plats i 1024 px på start- och artsidan (fem länkar, språkbytet och knappen; `gap` behövde inte sänkas), och hoppa-till-innehållet-länken (första tabbstoppet, synlig, nästa stopp efter Enter ligger i `main#main`, `main#main` finns på start-, blogg- och juridiksidorna).
2. **Sidfotens tre lägen testas som enhetstest:** `footerSpecies(built, commonQids)` i `src/lib/species-nav.mjs` (ren JS) ger `{ column, common }`; `Footer.astro` använder den och `commonSpecies()` i `species.ts` delegerar till den (`COMMON_QIDS` exporteras). `tests/unit/species-nav.unit.mjs` testar noll arter (ingen kolumn, ingen rad), arter utan någon av de tolv (kolumn, ingen rad) och några av de tolv i listans ordning (kolumn och rad). Byggda sidor testas för två av lägena: fixturbygget (kolumn och rad) och `dist-empty/` (inget).
3. **Sidfotens rutnät** har kvar sina tre kolumner när arter saknas: `.fgrid` är `1.6fr repeat(3, 1fr)` och modifieraren `.fgrid--species` (satt när kolumnen visas) `1.6fr repeat(4, 1fr)`, i stället för att alltid ha fem spår.
4. **`scripts/check-empty-hub.mjs`** kontrollerar nu också start- och bloggsidorna (`/`, `/sv/`, `/blog/`, `/sv/blog/`) i noll-arter-bygget: ingen länk till `/sv/arter/` eller `/species/`, ingen rad Vanliga arter, ingen artkolumn, ingen länk under kartan, och att hoppa-länken finns. Kontrollen failar med menylänken påtvingad (provat).
5. **Hoppa-länken:** stilen ligger i `src/styles/global.css` med `top: -64px`, `z-index: 300` (menyn har 100), en apricosfärgad fokusring och ingen övergång vid reducerad rörelse. Copy-nyckeln `nav.skipToContent` ("Hoppa till innehållet" / "Skip to content") finns i båda copy-filerna och i specens bilaga A. `id="main"` på `main` i `HomePage`, `FieldNoteArticle`, `FieldNotesIndex`, `LegalLayout`, `pages/legal/index.astro` och de fyra artsidekomponenterna.
6. **Task 13:s granskning (2026-10-07, "Approved with minors", egen commit efter Task 14):** sidfotens kolumn visar bara indexerade grupper (`footerGroups()` i `species.ts`: de fem största med minst tre arter, "Alla arter från A till Ö" alltid; testdatan ger Tättingar, Ugglor och Alla arter); mellan 761 och 1 000 px får `.fgrid--species` tre kolumner med varumärket på en egen rad (ingen överflödning i 761, 800, 880, 900 och 1 000 px, mätt); hoppa-länken göms med `transform` och `<main id="main" tabindex="-1">` tar fokus utan ram (Task 11:s `ComparisonPage`-block har fått samma attribut); en gemensam regel för "finns det arter?" (`hasSpeciesPages` i `species-nav.mjs`, `hasSpecies()` i `species.ts` för menyn och startsidan, `footerSpecies().column` för sidfoten); `commonSpecies()` borttagen (inga anrop kvar) och kommentaren står vid `COMMON_QIDS`; `check-seo.mjs` kräver exakt ett `id="main"` på varje sida med hoppa-länken. Testblocket ovan är byte-identiskt med filen igen.

---

### Task 14: Sitemap, SEO-reglerna som kod och kontrollen av förhandsbygget

**Tillägg (Task 10:s granskning 2026-10-07):** Task 14 körs före Task 11, så `COMPARISONS_ENABLED` är `false` (`lib/species-source.mjs` sedan Task 14, `lib/species.ts` exporterar den vidare) och inga jämförelsesidor byggs. Sitemapen och `check-seo.mjs` ska då varken vänta sig jämförelsesidor eller länkar till dem; kontrollerna av jämförelsesidorna aktiveras när Task 11 slår på flaggan (eller läser flaggan och hoppar över dem medan den är av).

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
import { COMPARISONS_ENABLED, comparisonsDir, isComparisonBuilt, isPreview, isSpeciesBuilt, readJsonDir, speciesDir } from './species-source.mjs';

export const MIN_GROUP_SIZE = 3;
const BASES = [['sv', '/sv/arter/'], ['en', '/species/']];
// Kept in sync by hand with species.ts's ABOUT_SLUG (that file imports astro:content and can't be loaded
// from this plain-JS module, see the file-level comment; Task 12 adds the about page these point to).
const ABOUT_SLUG = { sv: 'om-artsidorna', en: 'about-these-pages' };

/** @param {string} root the website folder */
export function readSpeciesSitemapInfo(root) {
  const groups = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).groups;
  const built = readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r));
  const builtQids = new Set(built.map((r) => r.qid));
  // No comparison pages until Task 11 turns them on (COMPARISONS_ENABLED), so none in the sitemap either.
  const comparisons = COMPARISONS_ENABLED ? readJsonDir(root, comparisonsDir()).filter((c) => isComparisonBuilt(c, builtQids)) : [];
  const preview = isPreview();
  /** @type {Map<string, string>} */
  const lastmod = new Map();
  /** @type {Set<string>} */
  const noindex = new Set();
  /** @type {Map<string, number>} */
  const sizes = new Map();
  let newest = '';
  // No species published yet (Task 15 Step 4: the production build goes through with zero of them):
  // the hub and the about page have nothing of their own to show a search engine either, same reason and
  // same noindex meta tag as SpeciesHub.astro and AboutSpeciesPages.astro (Task 7 and Task 12; keep the
  // three in sync, the filter below is what actually keeps them out of the sitemap, the meta tag alone
  // only hides them from being indexed, not from being listed).
  if (built.length === 0) {
    for (const [lang, base] of BASES) {
      noindex.add(base);
      noindex.add(`${base}${ABOUT_SLUG[lang]}/`);
    }
  }
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

**Tillägg (2026-10-07, Task 4:s fixvåg):** sidfoten kräver inte längre alla tolv vanliga arter (`footerSpecies()` i `species-nav.mjs` tar med de som har en sida). Lägg i `check-seo.mjs` till en **varning, inte ett fel**, som listar de av de tolv i `species-groups.json` (`common`) som saknar sida i bygget, till exempel `check-seo: varning, 3 av 12 vanliga arter saknar sida: Q25334, Q14683, Q4764`. Den får aldrig stoppa bygget eller publiceringsloopen.

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
// Present at all, with or without a value: Astro writes an empty alt as a bare `alt` (compressHTML).
const hasAttr = (tag, name) => new RegExp(`\\s${name}(?=[\\s=/>])`).test(tag);
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
    if (!hasAttr(tag, 'alt')) fail(path, `bild utan alt: ${tag.slice(0, 90)}`);
    if (isNew && (!attr(tag, 'width') || !attr(tag, 'height'))) fail(path, `bild utan width/height: ${tag.slice(0, 90)}`);
  }
  for (const tag of html.match(/<a\b[^>]*>/g) ?? []) {
    const target = internalTarget(attr(tag, 'href') ?? '');
    if (target && !exists(target)) fail(path, `död länk till ${attr(tag, 'href')}`);
  }
  // The skip link (Layout.astro, Task 13) needs its target on every page that has it, new page types included.
  if (/class="skip-link"/.test(html)) {
    const mains = (html.match(/\sid="main"/g) ?? []).length;
    if (mains !== 1) fail(path, `hoppa-länken behöver exakt ett id="main" (har ${mains})`);
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

  // The share image is in the build (the species pages' own is drawn by astro.config.mjs, not by Astro).
  const og = attr(html.match(/<meta property="og:image"[^>]*>/)?.[0] ?? '', 'content');
  if (!og) fail(path, 'og:image saknas');
  else if (og.startsWith(SITE) && !exists(new URL(og).pathname)) fail(path, `og:image finns inte i bygget: ${og}`);

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

// A warning, never an error (Task 4's fix wave): the footer lists those of the twelve common species that have
// a page, and species are published one at a time, so for a while some of them have none.
const common = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).common;
const builtQids = new Set(
  pages.filter((p) => p.html.includes('data-species-page')).flatMap((p) => [...p.html.matchAll(/wikidata\.org\/wiki\/(Q\d+)/g)].map((m) => m[1])),
);
const missingCommon = common.filter((qid) => !builtQids.has(qid));
if (missingCommon.length) console.warn(`check-seo: varning, ${missingCommon.length} av ${common.length} vanliga arter saknar sida: ${missingCommon.join(', ')}`);

if (errors.length) {
  console.error(`check-seo FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(`check-seo OK (${pages.length} sidor, ${pages.filter((p) => NEW.some((n) => p.path.startsWith(n))).length} artsidor, ${sitemap.size} adresser i sitemapen)`);
```

- [ ] **Step 4: Kontrollen av förhandsbygget**

**Tillägg (2026-10-07, Task 4:s fixvåg):** `scripts/check-preview-build.mjs` och npm-skriptet `test:preview-build` finns redan. Skriptet kontrollerar att inga foton eller inspelningar av arter utan sida hamnar i bygget (byte för byte, och som omskalade kopior via testfotonas egna färger, eftersom Astro tar bort original som bara används genom `<Image>`), och att de publicerade och de kontrollerade opublicerade arternas foton finns i rätt bygge när artsidorna finns. **Utöka skriptet** med sidkontrollerna (lägg dem före felutskriften och behåll fotokontrollen), skriv inte över det; i Step 6 finns `test:preview-build` redan. Kodblocket nedan är hela filen efter Task 14 (byte-identiskt med filen); Task 14:s del är avsnittet `// -- Pages (Task 14)`.

`scripts/check-preview-build.mjs`:

```js
#!/usr/bin/env node
// Checks the publishing rule (spec 2026-09-25 §14) on the TEST data: dist/ is the normal fixture build
// (npm run build:fixtures) and dist-preview/ the preview build (npm run build:preview-fixtures).
//
// Since Task 4's fix wave (2026-10-07): no photo or recording of a species without a page in a build may
// reach that build. Task 14 adds its page checks (unpublished pages, noindex, banner, sitemap) to this
// same script instead of replacing it.
//
// A test file is found in a build in two ways:
// - byte-identical anywhere in the build (photo originals in _astro/, recordings in audio/species/);
// - as any resized or re-encoded copy of a photo (<Image> variants, share images): every test photo is
//   one flat colour, and the test data gives each species its own hue (make-species-fixtures.mjs), so a
//   flat image in the build with the same colour is that photo. Astro deletes an original that is only
//   used through <Image>, so the byte check alone would miss those.
// The positive checks (published media in dist/, verified unpublished media in dist-preview/) only run
// once the build has species pages (Task 7 and on): until a page imports src/lib/species.ts, no species
// photo is in any build.
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';
import { COMPARISONS_ENABLED, isSpeciesBuilt, readJsonDir } from '../src/lib/species-source.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist');
const preview = resolve(root, 'dist-preview');
for (const dir of [dist, preview]) {
  if (!existsSync(dir)) {
    console.error(`check-preview-build: ${dir} saknas (npm run build:fixtures och npm run build:preview-fixtures)`);
    process.exit(1);
  }
}
const errors = [];

// -- Photos and recordings ---------------------------------------------------------------------------
const ASSETS = 'tests/fixtures/species-assets';
const IMAGE = /\.(webp|jpe?g|png|avif)$/i;
/** Largest per-channel difference (0-255) between two colours that still counts as the same photo. */
const TOLERANCE = 6;

const sha256 = (file) => createHash('sha256').update(readFileSync(file)).digest('hex');
/** Every file under `dir`, at any depth. */
const filesUnder = (dir) =>
  readdirSync(dir, { recursive: true, withFileTypes: true })
    .filter((entry) => entry.isFile())
    .map((entry) => join(entry.parentPath, entry.name));
/** The mean colour of an image that is one flat colour, or null for any other image. */
async function flatColour(file) {
  const { channels } = await sharp(file).stats();
  // Fewer than 3 channels means grayscale (+ maybe alpha), not RGB: slice(0, 3) on a 1- or 2-channel array
  // does not pad it back up to 3, it just returns that shorter array, so `rgb.every` below would silently
  // check only the single gray channel and could match an RGB photo's colour by chance.
  if (channels.length < 3) return null;
  const rgb = channels.slice(0, 3);
  return rgb.every((c) => c.stdev < 4) ? rgb.map((c) => c.mean) : null;
}
const near = (a, b) => a.every((v, i) => Math.abs(v - b[i]) <= TOLERANCE);

// The test data, sorted by what each build may show (isSpeciesBuilt, the same rule as the pages).
const records = readJsonDir(root, 'tests/fixtures/species');
const published = records.filter((r) => isSpeciesBuilt(r, false));
const previewOnly = records.filter((r) => isSpeciesBuilt(r, true) && !isSpeciesBuilt(r, false));
const never = records.filter((r) => !isSpeciesBuilt(r, true));
for (const [name, list] of [['publicerade', published], ['bara i förhandsbygget', previewOnly], ['aldrig byggda', never]]) {
  if (!list.length) errors.push(`testdatan har inga ${name} arter, kontrollen säger då ingenting`);
}

/** Every test file: its path under species-assets/, hash and (for a photo) colour. */
const media = [];
for (const record of records) {
  const dir = resolve(root, ASSETS, record.qid);
  if (!existsSync(dir)) continue;
  for (const file of filesUnder(dir)) {
    const colour = IMAGE.test(file) ? await flatColour(file) : null;
    if (IMAGE.test(file) && !colour) errors.push(`${ASSETS}: ${file} är inte en enfärgad testbild (make-species-fixtures.mjs)`);
    media.push({ qid: record.qid, file: `${record.qid}/${file.slice(dir.length + 1).replace(/\\/g, '/')}`, hash: sha256(file), colour });
  }
}
// Two test photos closer than twice the tolerance could be mistaken for each other.
const photos = media.filter((m) => m.colour);
for (const [i, a] of photos.entries()) {
  for (const b of photos.slice(i + 1)) {
    if (a.colour.every((v, k) => Math.abs(v - b.colour[k]) <= 2 * TOLERANCE)) errors.push(`testfotona ${a.file} och ${b.file} har nästan samma färg`);
  }
}

/** What a build holds: file hashes, and the colours of its flat images. */
async function scan(dir) {
  const files = filesUnder(dir);
  const colours = [];
  for (const file of files.filter((f) => IMAGE.test(f))) {
    const colour = await flatColour(file).catch(() => null);
    if (colour) colours.push(colour);
  }
  return { hashes: new Set(files.map(sha256)), colours, hasSpeciesPages: existsSync(join(dir, 'sv', 'arter')) };
}
const builds = { dist: await scan(dist), 'dist-preview': await scan(preview) };
const isIn = (m, build) => builds[build].hashes.has(m.hash) || (m.colour !== null && builds[build].colours.some((c) => near(c, m.colour)));

const mustBe = (list, build, present, why) => {
  const qids = new Set(list.map((r) => r.qid));
  for (const m of media.filter((x) => qids.has(x.qid))) {
    if (isIn(m, build) !== present) errors.push(`${build}: ${ASSETS}/${m.file} ${present ? 'saknas' : 'finns'} (${why})`);
  }
};
mustBe(never, 'dist', false, 'arten får aldrig en sida');
mustBe(never, 'dist-preview', false, 'arten får aldrig en sida');
mustBe(previewOnly, 'dist', false, 'opublicerad art, bara förhandsbygget visar den');
const positive = builds.dist.hasSpeciesPages && builds['dist-preview'].hasSpeciesPages;
if (positive) {
  mustBe(previewOnly, 'dist-preview', true, 'kontrollerad men opublicerad art, förhandsbygget visar den');
  mustBe(published, 'dist', true, 'publicerad art; är dist/ ett fixturbygge?');
}

// -- Pages (Task 14) -----------------------------------------------------------------------------------
const page = (dir, path) => {
  const file = join(dir, path, 'index.html');
  return existsSync(file) ? readFileSync(file, 'utf8') : null;
};
// The comparison pages come with Task 11 (COMPARISONS_ENABLED in species-source.mjs): until then neither
// build may have one, published or not.
const UNPUBLISHED_COMPARISONS = [
  'sv/arter/storre-hackspett-eller-tretaig-hackspett',
  'species/eurasian-three-toed-woodpecker-vs-great-spotted-woodpecker',
];
const UNPUBLISHED = ['sv/arter/storre-hackspett', 'species/great-spotted-woodpecker', ...(COMPARISONS_ENABLED ? UNPUBLISHED_COMPARISONS : [])];
const NEVER = [
  'sv/arter/grongoling',
  'sv/arter/spillkraka',
  'sv/arter/hornuggla-eller-kattuggla',
  'species/long-eared-owl-vs-tawny-owl',
  ...(COMPARISONS_ENABLED ? [] : [...UNPUBLISHED_COMPARISONS, 'sv/arter/blames-eller-talgoxe', 'species/eurasian-blue-tit-vs-great-tit']),
];

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

const sitemapOf = (dir) => readdirSync(dir).filter((f) => /^sitemap-\d+\.xml$/.test(f)).map((f) => readFileSync(join(dir, f), 'utf8')).join(' ');
if (sitemapOf(preview).includes('storre-hackspett')) errors.push('dist-preview: en opublicerad sida finns i sitemapen');
if (!COMPARISONS_ENABLED && /-eller-|-vs-/.test(sitemapOf(dist) + sitemapOf(preview))) errors.push('en jämförelsesida finns i sitemapen fast jämförelserna är avstängda (Task 11)');

// The share images (og/species/<QID>.<hash>.jpg, drawn by astro.config.mjs): one per built species, no other.
const shareQids = (dir) => (existsSync(join(dir, 'og/species')) ? readdirSync(join(dir, 'og/species')).map((f) => f.split('.')[0]).sort() : []);
for (const [name, dir, list] of [['dist', dist, published], ['dist-preview', preview, [...published, ...previewOnly]]]) {
  const want = list.map((r) => r.qid).sort();
  if (JSON.stringify(shareQids(dir)) !== JSON.stringify(want)) errors.push(`${name}/og/species: väntade delningsbilder för ${want.join(', ')}, fick ${shareQids(dir).join(', ')}`);
}

const audio = existsSync(join(dist, 'audio/species')) ? readdirSync(join(dist, 'audio/species')) : [];
if (audio.length !== 4) errors.push(`dist/audio/species: väntade 4 inspelningar (byggda arter med inspelning), fick ${audio.length}`);

if (errors.length) {
  console.error(`check-preview-build FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log(
  `check-preview-build OK (foton och inspelningar: ${never.map((r) => r.qid).join(', ')} i inget bygge, ` +
    `${previewOnly.map((r) => r.qid).join(', ')} inte i dist/` +
    (positive
      ? ` men i dist-preview/, alla ${published.length} publicerade arter i dist/)`
      : `; inga artsidor i bygget än, så bara frånvaron är kontrollerad)`) +
    `; sidor: opublicerat bara i förhandsbygget med noindex och banderoll${COMPARISONS_ENABLED ? '' : ', inga jämförelsesidor (avstängda till Task 11)'}`,
);
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

// One page or several, comma-separated: AXE_PATH="sv/arter/talgoxe/,species/great-tit/" (the publish loop
// checks both language versions of the page it just built, Task 16).
const paths = (process.env.AXE_PATH ?? 'sv/arter/').split(',').map((p) => p.trim()).filter(Boolean);

for (const path of paths) {
  test(`axe: /${path}`, async ({ page }) => {
    await page.goto(`/${path}`);
    const results = await new AxeBuilder({ page }).analyze();
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });
}
```

```json
    "test:a11y": "playwright test tests/a11y.spec.ts",
```

Kör mot en adress: `AXE_PATH="sv/arter/talgoxe/" PLAYWRIGHT_PORT=4327 npm run test:a11y`, eller flera kommaseparerade (`AXE_PATH="sv/arter/talgoxe/,species/great-tit/"`, ett test per adress; publiceringsloopen i Task 16 kontrollerar båda språken). Fel: rätta komponenten (samma regel som Step 7 nedan för SEO).

- [ ] **Step 7: Kör**

Run: `npm run verify:fixtures`
Expected: `check-seo OK (... sidor, ... artsidor, ... adresser i sitemapen)`, enhetstesterna, vakterna och `check-preview-build OK (...)` gröna.

Failar en regel på en **befintlig** sida (en h1, alt, döda länkar) är det ett riktigt SEO-fel: rätta sidan i samma task och skriv i commit-meddelandet vilken sida det gällde. Failar en regel på de nya sidorna: rätta komponenten, inte skriptet.

Run: `npm run build:fixtures && node scripts/check-seo.mjs && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: PASS (`dist/` är testdatans vanliga bygge igen efter förhandsbygget, som skrev till `dist-preview/`).

**Tillägg (Task 7:s granskning 2026-10-07, uppdaterad i en andra granskningsrunda samma dag): den varaktiga kontrollen av noll-arter-läget.** Task 7 bevisade noindex och "bara ingressen" för `SpeciesHub.astro` mot `dist-empty/` (`npm run build:empty`) med ett eget litet skript `scripts/check-empty-hub.mjs`, eftersom varken sitemap-uteslutningen eller `check-seo.mjs` fanns kodade än. `build:empty` sätter `SPECIES_EMPTY=1`: en egen miljövariabel i `src/lib/species-source.mjs` (`useEmptyData()`, samma Production-spärr som `isPreview()`) som pekar `speciesDir()`/`comparisonsDir()`/`assetsDir()` på en avsiktligt tom mapp `tests/fixtures/empty/` (bara `.gitkeep`-filer) i stället för `src/data/`. **Andra granskningsrundans rättelse:** `build:empty` pekade först mot det RIKTIGA, i dag tomma `src/data/species/`: det höll bara så länge Task 16 inte publicerat något, och hade blivit en fälla för CI och publiceringsloopen (`npm run test:empty-hub` hade tyst börjat faila den dag den första arten publicerades). Den egna miljövariabeln gör kontrollen oberoende av `src/data/`s verkliga innehåll, för alltid, inte bara idag.

Nu finns både sitemap-uteslutningen och `check-seo.mjs`. Kör, som en del av den här taskens Step 7: `npm run build:empty && node scripts/check-seo.mjs dist-empty`. `check-seo.mjs`s egen regel (noindex ⇔ saknas i sitemapen, körs för alla sidor under `/sv/arter/` och `/species/`, hubben och om-sidan inkluderade) bevisar nu sitemap-uteslutningen i noll-arter-läget, helt utan ny testkod: det är precis det `check-empty-hub.mjs` INTE kunde bevisa i Task 7 (sitemap-koden fanns inte då). **Fäll inte Task 7:s `check-empty-hub.mjs`:** behåll den (den kontrollerar markupen direkt, snabbare och mer riktad än att parsa en hel sitemap), men lägg till `node scripts/check-seo.mjs dist-empty` som ett andra, kompletterande steg i `npm run test:empty-hub` i `package.json` (`"test:empty-hub": "npm run build:empty && node scripts/check-empty-hub.mjs && node scripts/check-seo.mjs dist-empty"`). Till skillnad från den första granskningsrundans version av den här noten: `npm run test:empty-hub` förblir grönt även efter att Task 16 har publicerat den första riktiga arten på `main`: `tests/fixtures/empty/` ändras aldrig av publiceringsloopen, bara `src/data/` gör det.

- [ ] **Step 8: Commit**

```bash
git add src/lib/species-sitemap.mjs astro.config.mjs scripts/check-seo.mjs scripts/check-preview-build.mjs scripts/check-no-dashes.mjs tests/a11y.spec.ts package.json package-lock.json
git commit -m "feat(website): sitemap för artsidorna, SEO-reglerna som kod, axe-kontroll och kontroll av förhandsbygget"
```

**Avvikelser vid genomförandet (Task 14, 2026-10-07, `89d22306` + uppföljningen; kodblocken ovan är byte-identiska med filerna):**

1. **`COMPARISONS_ENABLED` ligger i `src/lib/species-source.mjs`** (flyttad från `species.ts`, som exporterar den vidare) så att sitemapen och kontrollskripten läser samma flagga som sidorna. Så länge den är `false` (till Task 11) tar `species-sitemap.mjs` inte med några jämförelser, och `check-preview-build.mjs` kräver att INGEN jämförelsesida finns i något av byggena och att ingen `-eller-`/`-vs-`-adress finns i någon sitemap; listorna `UNPUBLISHED`/`NEVER` får tillbaka jämförelsesidorna när flaggan slås på.
2. **`check-seo.mjs`:** ett tomt `alt` godtas utan värde (`hasAttr`): Astro skriver `alt=""` som bara `alt` med `compressHTML: true`, och planens `attr(tag, 'alt') === null` fällde då varje dekorativ bild. Varningen för vanliga arter utan sida (Step 3:s Tillägg) läser artsidorna i bygget (`data-species-page` + Wikidata-länken), så den fungerar för `dist/`, `dist-preview/` och `dist-empty/` utan att veta vilket läge som byggdes.
3. **`tests/home.spec.ts`:** sitemaptestet tillåter `lastmod` på artsidorna (och kontrollerar Talgoxens `2026-11-25`), alla andra sidor utom inläggen har fortfarande inget.
4. **`tests/a11y.spec.ts`** tar flera kommaseparerade adresser i `AXE_PATH` (ett test per adress), för att publiceringsloopen ska kunna kontrollera båda språken av en art i en körning.
5. **Kontroll:** `npm run verify:fixtures` (check-seo 64 sidor, 54 artsidor, 52 adresser i sitemapen; check-preview-build), `node scripts/check-seo.mjs dist-preview` (70 sidor), `npm run test:empty-hub` (inkl. `check-seo.mjs dist-empty`, med varningen 12 av 12 vanliga arter), hela Playwright, axe 0 fel på 13 sidor (hubb, art, grupp, liten grupp, om-sida, frånvarande art, start, SV och EN).

---

### Task 15: Full QA på testdatan

- [ ] **Step 1: Alla vakter och tester**

Run: `npm run verify:fixtures && npx astro check && npm run test:no-accuracy && PLAYWRIGHT_PORT=4327 npx playwright test`
Expected: allt grönt. (Det gamla Vite/Tailwind-typfelet i `astro.config.mjs`, som den här raden tidigare räknade bort, är borta sedan dependabot-fixen 2026-10-02 lyfte Astro till version 7: `astro check` ska rapportera noll fel. `tools/store-assets/render-feature.mjs` har fortfarande en oförändrad, orelaterad varning om `window.setScreen`.)

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

Riktig artdata går från nu på direkt till `main`, en art i taget (avsnitt 14, Task 16 och framåt). Koden måste alltså vara på `main` innan den första riktiga arten publiceras, annars finns inga mallar att rendera den med. **Produktionsbygget går igenom med noll publicerade arter** (ändrat 2026-10-07, Task 4:s fixvåg): `footerSpecies()` tar bara med de vanliga arter som har en sida, sidfoten döljer raden när den är tom, och poster som inte byggs valideras bara mot kuvertet (id, status, namn, adresser). Vercels förhandsbygge av grenen ska alltså vara grönt redan före sammanslagningen; failar det är det ett fel, inte väntat. **Tillägg (Task 7:s granskning 2026-10-07, samma noll-arter-läge):** i det här fönstret (mellan den här sammanslagningen och Task 16:s första publicering) visar hubsidorna (`/sv/arter/`, `/species/`) och om-sidan (Task 12) `noindex` och bara sin ingress/text, inte en tom sökruta eller tomma grupplistor, och menyn, sidfoten och startsidans uppslagsverkslänk (Task 13) visar ingen väg dit alls: allt redan testat mot en avsiktligt tom testdatamapp (Task 7:s `npm run test:empty-hub`, `SPECIES_EMPTY=1` mot `tests/fixtures/empty/`, utökad i Task 14 med `check-seo.mjs`), inte mot `src/data/`s tillfälliga tomhet. Det är väntat att sidorna ser sådana ut strax efter den här sammanslagningen; det är inte ett fel att undersöka.

```bash
git fetch origin && git merge origin/main
npm ci && npm run verify:fixtures && npx astro check && npm run test:no-accuracy && PLAYWRIGHT_PORT=4327 npx playwright test && npm run build:prod
git push
```

`npm run build:prod` körs sist, efter Playwright, inte före: den bygger i riktigt läge mot `src/data/`, där ingen art är publicerad än, och skriver då över `dist/` med ett artlöst bygge. Körde den tidigare i kedjan skulle Playwright-testerna (som förväntar sig testdatans arter) i tysthet testas mot det artlösa produktionsbygget i stället för mot `verify:fixtures`s bygge. Här är den bara ett rök-test: det ska gå igenom utan fel, inget mer. **Efter detta steg står `dist/` kvar som produktionsbygget** (artlöst), inte testdatans: kör `npm run build:fixtures` igen innan nästa Playwright-körning i samma arbetspass.

Sedan i huvudmappen för `main` (worktreen kan inte byta till `main`):

```bash
git switch main && git pull && git merge --ff-only website/artsidor && git push
```

Går `--ff-only` inte: ta in `main` i grenen igen, kör om verifieringen och försök på nytt. När Vercel har byggt produktion: sidorna under `/sv/arter/` och `/species/` svarar ännu bara med `noindex`-sidor eller 404 (ingen art har `publish: true` än), det är väntat tills Task 16.

---

### Task 16: Publiceringsloopen, en art eller jämförelse i taget (ändrat 2026-10-05 (b), var tidigare "Riktig data för våg 1" och "Förhandsvisning, Albins godkännande och go-live för våg 1" som två separata tasks)

**Tillägg (2026-10-06, Task 23-granskningen, uppdaterad efter omgranskningen samma dag):** `web publish`s eget urval (statusen `ok` + `verification`) var svagare än kommandots verkliga publiceringsvillkor (faktabladet fortfarande kontrollerat mot de nuvarande fakta, texten skriven ur dem, inga strukna citerade fakta): en kandidat som Step 2 nedan trodde var klar kunde falla på riktigt och blockera hela kön bakom sig. `publish-next.mjs` anropar därför **`uv run birdy-fetcher web publish --next`** istället för att själv välja kandidat (Step 3 punkt 2 nedan är ersatt: `web publish --next` gör urvalet, i samma könordning, och publicerar högst en post, eller skriver `none` på stdout om inget är klart). Uteslutna poster står i en sessionsfil **`reports/publish-loop-excluded.txt`** (en QID eller jämförelsestam per rad): `publish-next.mjs` läser filen vid start (en saknad fil behandlas som tom, inget fel) och skickar varje rad som `--exclude`, och lägger till en ny rad **efter en posts FÖRSTA fel** (inte tre) så att en enskild dålig sida aldrig blockerar de andra; `publish-loop.sh` tömmer filen vid start av en ny körning (en gammal uteslutning ska inte överleva till nästa dag). Loopens egna stopp ("tre fel i rad", se skriptet nedan) gäller fortfarande, men nu uttryckligen tre OLIKA poster i rad (en enskild post kan aldrig bidra med mer än ett fel, den är redan utesluten efter det); det stoppet är till för systematiska fel (trasig byggmiljö och liknande), inte för enstaka dåliga sidor. Skriver `--next` `none` avslutar `publish-next.mjs` med en egen kod **3** (skild från 0 = publicerad och 1 = fel); loopen tolkar kod 3 som "stoppa nu" och räknar det inte som en publicering (ingen sömn, ingen ökning av räknaren). Steg 7 (`git add`) gäller fortfarande bara filen/filerna för just den post `--next` skrev ut. Stickprovets republiceringsväg (Step 4) är **`uv run birdy-fetcher web write --species X --max-cost 2`**, sedan **`uv run birdy-fetcher web compare --max-cost 5`** (samma `--top` som tidigare: aktuella par hoppas över utan kostnad, par utanför `--top` flaggas av `compare`s egen sweep och behöver `publish: false` av Albin), sedan **`uv run birdy-fetcher web publish --species X`** (som även rapporterar om X:s redan publicerade jämförelser har blivit inaktuella); committa de omskrivna jämförelsefilerna tillsammans med X.

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

`MAX_PUBLISH` är nödstoppet (`--max-publish N` i spec-språket): kör till exempel `bash scripts/publish-loop.sh 5` för en liten testomgång innan hela kön släpps på. Tre OLIKA poster i rad (kod 1, inte kod 3) stoppar loopen helt: en enskild dålig post bidrar aldrig med mer än ett fel, den uteslöts redan efter sitt första (se Tillägget ovan); läs `reports/publish-loop-*.md` innan omstart. `website/reports/` (sessionsfilen + loopens egna `.md`-rapporter) är lokalt arbetsmaterial, inte artdata: lägg till raden `reports/` i `website/.gitignore` som en del av den här uppgiften, annars dyker filerna upp som ospårade i varje `git status`.

- [ ] **Step 4: Stickprovet var 40:e art och var 10:e jämförelse**

**Tillägg (2026-10-06, fas 1b:s slutgranskning C2 och I1):** `web spot-check` räknar nu per dragning i stället för att räkna alla obeslutade publicerade arter (det gamla villkoret drog om vid varannan publicering när de första 40 var nådda: 142 av 180 arter i stället för 8). `review/stickprov-state.json` håller arterna som redan räknats och varje dragning (id, frö, datum, arter); 2 arter dras per 40 arter publicerade sedan förra dragningen, en rest väntar till nästa hela 40. Raderna i `review/stickprov.csv` har kolumnen `Dragning` och ett tomt `Beslut` (förut förifyllt `behåll`, så en import innan Albin tittat räknades som kontrollerad); Albin skriver `behåll`, `stryk` eller `ändra` på varje faktarad och `behåll` eller `stryk` på inspelningsraden. `web import --file review/stickprov.csv` tillämpar bara artens öppna dragning, hoppar över äldre och redan avgjorda dragningar (arket är löpande och kan importeras om utan att äldre sidor får nytt datum), låter en art med tomma beslut vänta, och sätter nytt kontrolldatum bara när något ströks eller ändrades (ett `stryk` på inspelningen tar bort `audio` och `voice.mp3`). Har faktabladet ändrats sedan dragningen tillämpas inget; kör `web spot-check --extra <QID>` för en ny dragning. Efter en dragning: committa `review/stickprov-state.json`, `review/stickprov.csv` och de dragna arternas JSON i en egen commit (`data(artsidor): stickprov, dragning N`), aldrig tillsammans med en publicering. **Jämförelsernas stickprov (1 per 10) är fortfarande inte kodat.** När det byggs ska det följa samma design: en egen räknare i tillståndsfilen för jämförelser publicerade sedan förra dragningen (inte "alla obeslutade publicerade"), en `Dragning`-kolumn och ett tomt `Beslut`. Fas 1b-planens formulering "samma `SPOT_CHECK_BATCH`-mönster men batch 10 och drag 1" är ersatt av detta.

Efter varje lyckad publicering i loopen, kör `cd ../tools/content-pipeline && uv run birdy-fetcher web spot-check && cd ../../website`. Skriver den något (se fas 1b Task 16): ladda upp ~~`review/stickprov.csv`~~ **dragningens egen fil `review/stickprov-dragning-N.csv` som ett nytt ark eller en ny flik (ersatt 2026-10-06, se Tillägget om uppföljningen nedan)** till Albins Drive, samma instruktion som undantagsarket (fas 1b R5). Ett bekräftat fel: `uv run birdy-fetcher web import --file review/stickprov.csv`, ~~sedan kör loopen (Step 3) på nytt för just den arten~~ **(ersatt, se Tillägget ovan)** sedan, i `../tools/content-pipeline`: `uv run birdy-fetcher web write --species X --max-cost 2`, `uv run birdy-fetcher web compare --max-cost 5` (samma `--top` som tidigare: aktuella par kostar inget, par utanför `--top` flaggas av `compare`s egen sweep och behöver `publish: false` av Albin) och `uv run birdy-fetcher web publish --species X`; bygg, testa och pusha om X (och de omskrivna jämförelsefilerna) som i Step 3 punkt 5 och 7 med det nya kontrolldatumet; notera missen i rapporten.

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
