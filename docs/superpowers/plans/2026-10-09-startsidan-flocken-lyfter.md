# Flocken lyfter: the start page hero · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the first view of birdy.community (both languages) with the approved "Flocken lyfter" hero: peach paper, the slogan "Känn igen fågeln. / Bevara stunden.", and a flock of 839 small birds (one per species in Birdy) that flies in once, lands as Birdy's bird with today's bird lit, and lets Dagens fågel's photo lift out of that bird as a taped polaroid; reduced motion and no JavaScript show the landed frame at once.

**Architecture:** Pure, Node-testable modules under `website/src/components/hero/` hold the flock data (generated from the prototype's data file) and all flock logic (which bird lights up for a QID, how the flock fits a box, the seeded flight, the landed flock as SVG). The hero draws the flight and the still frame on one `<canvas>` (`flock-motion.ts`, the prototype's motion), puts the same landed frame in a `<noscript>` SVG for visitors without JavaScript, and reuses that SVG for the share images. CSS places a "fit box" for the bird and the polaroid so that nothing ever covers the words or the lit bird; Playwright checks that geometry at seven widths with the same functions the canvas uses.

**Tech Stack:** Astro 7 (`.astro` components, scoped styles, `astro:assets` `<Picture>`), plain JS modules with JSDoc (`.mjs`), TypeScript client script, Canvas 2D + Web Animations API, `node --test` unit tests, Playwright 1.60 + `@axe-core/playwright`, the site's guard scripts, `sharp` + Playwright for the share images, Lighthouse 12 via `npx` (no new dependency).

---

## Context for the implementer

**What is approved (spec `docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md`, Albin 2026-10-09):**
- The hero becomes: peach paper; kicker "Kamera, foto eller läte" / "Camera, photo or song" (the English text already exists as `howItWorks.note`); headline "Känn igen fågeln." + handwritten "Bevara stunden." (en "Know the bird." + "Keep the moment."); today's sub text; the Play and App Store badges; the flock; the Dagens fågel polaroid ("Dagens fågel: {name}" + the species page's photo credit line, linking to the species page; tape on the card's edge, never over the photo; the photo whole, never cropped); "samma fågel som i appen i dag" only when `sameAsApp`, with today's stale-day guard.
- **Nothing is written next to or over the flock, and no arrows** (Albin: "remove, yes"). The old margin note under the headline ("en ny fågel varje dag…") and the arrow to the plate go too.
- Motion once per page view, starting when the hero is visible, about 4 seconds of flight, then nothing runs. Canvas, DPR at most 2, no new dependency. Reduced motion and no JavaScript show the final frame. No layout shift. The headline (the hero's words) is the LCP element, never the photo.
- The lit bird: a fixed mapping from the species QID to one bird of the flock (`flockIndexFor(qid)`), the same on every page and day.
- The nav (`Nav.astro`, overlay variant) gets dark text on the peach hero. Nothing else in the nav, the footer or other sections changes; the only touch outside the hero is the torn edge under it (`MonthBirds.astro`'s `DeckleEdge`), whose colour is the hero's bottom colour and so becomes peach.
- The share images `og-field-{en,sv}.jpg` are redrawn in the same look (spec: the final frame is also for share images), with the same SVG builder.

**Phone order (decided here, flagged to Albin).** The spec's line "Rubriken till vänster (under flocken på telefon)" would put the flock above the words on phones, but the approved prototype has the words first and the flock below them, and with the flock first the polaroid photo can become the largest element in a small phone's first view (the spec forbids the photo as LCP). This plan follows the prototype: words first on narrow screens. Putting the flock first later is a separate, small change (stage before the words, the nav marker moved with it).

**The motion and its data** are the approved prototype's: `docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html` (version 3, without the arrow) and its `flock-data.js` (the social profiles' flocks; the hero uses `cover`, 839 birds, the first `edge` = 178 form the heart of the bird). Read the prototype once before Task 5: the flight plan, the sprites, the landing, the ring pop and `revealToday()` are ported 1:1 (same seed, same order of random numbers, same timings). The prototype's handwritten note ("en fågel i flocken för varje art i Birdy") is **not** ported.

**Why canvas plus a `<noscript>` SVG** (the spec allows either): every visitor with JavaScript gets the canvas, both for the flight and, with reduced motion, for the still frame, from one drawing path. An inline SVG of the landed flock is 839 `<use>` elements; measured in a scratch copy it costs a 35 to 90 ms render task on a 4x-throttled CPU and doubles the page's DOM, so it lives inside `<noscript>`, which browsers with JavaScript never parse. Its bytes still travel with the HTML (about 84 kB raw, about 12 kB compressed). The same `flockSvg()` string draws the share images. The SVG uses `preserveAspectRatio="xMidYMid meet"` on the same `VIEW` that `fitView()` maps for the canvas, so both land every bird on the same pixel (checked in Task 4).

**Geometry (validated before this plan was written).** `VIEW = { x: 830, y: 0, w: 680, h: 720 }` (flock units) is Birdy's bird with a little air; the element `[data-flock-fit]` is the box it fits into ("meet"). Narrow screens (< 1024 px): words first, then a stage as wide as the screen (at most 460 px) whose fit box is 680:720; the polaroid hangs below the bird's heart (`margin-top: 88%` of the stage's width = 83 % of the box's height). From 1024 px: words on the left (column `clamp(420px, 34vw, 480px)`), the fit box on the right, vertically centred under the menu, `height: min(100% - 128px, 50vw - 40px)`, right edge at `max(12px, (100% - 1320px) / 2)`; the polaroid's right edge at 32 % of the box (`right: 68%`), left of the bird's heart. A scratch copy of exactly this CSS passed at 19 window sizes from 320×700 to 2560×1300, in both languages, with a 3:2 and a 0.47 (Turkduva) photo: landed birds stay at least 45 px from the words, every possible lit bird's disc at least 14 px from the polaroid and its tape, the polaroid at least 30 px from the words, the tape never on the photo, no horizontal scroll, the photo's area under 80 % of the largest text block. If a geometry test in Task 4 fails on the real site, adjust only these knobs (the narrow `margin-top`, the wide `right`, the column width, the stage height formula), never `VIEW` or the data.

**Builds and the test data.** Playwright serves `dist/` with `astro preview`, so run `npm run build:fixtures` before every Playwright run. The fixture build pins 2026-10-15: the app's Dagens fågel is Hornuggla (Q25384), its fixture photo is 1200×800 "Testfotograf, CC BY 4.0", its flock index is 2, `sameAsApp` is true. The empty build (`npm run test:empty-hub`) has no species page: the polaroid shows the site's own CC0 great tit (Talgoxe, Q25485, flock index 107) without a link and without the app line.

**Commands.** Work in bash in the worktree `C:/w/birdy-flock`, branch `website/flocken-lyfter` (the controller creates it in Task 0). All website commands run in `C:/w/birdy-flock/website`. Playwright: `PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test <files> --workers=2` (Playwright reuses a server already listening on 4741; if another session's preview might be there, check `curl -s localhost:4741/sv/ | grep -c data-flock-fit` after Task 4, expect `1`, or use a free port such as 4751 for every run of this plan). Run at most one heavy job (build, Playwright, Lighthouse) at a time. Renders can be checked headless with `website/node_modules/playwright` (`reducedMotion: 'reduce'` gives the final frame).

**House rules.** No dashes (U+2013/U+2014) in copy (`test:no-dashes`); code comments in English like the surrounding code; commit messages in Swedish (the commands below show the subject; end each message with the attribution trailer your session's instructions give); no new dependency; do not edit `CLAUDE.md` on this branch (the controller updates the status on `main`); never push to or merge into `main`: Task 11 pushes this branch for a Vercel preview and stops there.

**Out of scope:** the nav's and footer's new style, the posts, the species pages, the other home sections, the gallery's small motions, the `theme-color` meta (stays espresso).

## File structure

| File | Responsibility | Task |
|---|---|---|
| `website/scripts/import-flock-data.mjs` | Writes the flock module from the prototype's `flock-data.js` (the `cover` flock) | 1 |
| `website/src/components/hero/flock-data.mjs` | Generated: `MARK` (the bird mark's path and centre) and `FLOCK` (839 birds) | 1 |
| `website/tests/unit/flock-data.unit.mjs` | The module is the prototype's flock, bird for bird | 1 |
| `website/src/components/hero/flock.mjs` | `flockIndexFor`, `VIEW`, `fitView`, `landing`, `rng`, `flightPlan`, `flockSvg`, colours | 2 |
| `website/tests/unit/flock.unit.mjs` | Unit tests for `flock.mjs` | 2 |
| `website/src/styles/tokens.css` | `--peach-hi`, `--peach-lo` | 3 |
| `website/scripts/check-contrast.mjs` | Text pairs on the peach tones; the hero leaves the espresso pair | 3 |
| `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` | New `hero.kicker`, `hero.flockLabel`, `daily.polaroid`; the old plate's keys out; `alt.shareImage` (Task 8) | 4, 8 |
| `website/src/components/hero/Polaroid.astro` | Dagens fågel as a polaroid: photo, caption link, app line, credit, tape | 4 |
| `website/src/components/Hero.astro` | The hero: words, canvas, fit box with the `<noscript>` SVG, polaroid, layout | 4, 5 |
| `website/src/components/MonthBirds.astro` | The torn edge under the hero in peach | 4 |
| `website/src/components/hero/DailyBirdPlate.astro`, `BirdyBird.astro`, `hero-motion.ts` | Deleted (the old plate, the bird on it, its scroll flight) | 4 |
| `website/scripts/check-empty-hub.mjs` | Empty build: the fallback polaroid without a link, no app line | 4 |
| `website/tests/home.spec.ts` | Hero content, geometry, no JavaScript, motion, LCP, nav on peach, contrast | 4, 5, 6, 7 |
| `website/tests/faltbok.spec.ts` | Hero is peach, margin notes without the hero, the torn edge, share image `?v=4` | 4, 8 |
| `website/src/components/hero/flock-motion.ts` | The flight on the canvas, the still frame, the polaroid's reveal | 5 |
| `website/src/components/Nav.astro`, `website/src/components/HomePage.astro` | `surface="peach"`: ink on the transparent bar | 6 |
| `website/tools/generate-og.mjs`, `website/public/og-field-{en,sv}.jpg`, `website/src/layouts/Layout.astro` | Share images in the Flock look, `?v=4` | 8 |

---

### Task 0: Worktree and baselines (controller)

**Files:** none in the repo; QA output goes to `C:/w/birdy-flock-qa/` (outside the repo).

- [ ] **Step 1: Create the worktree and install**

```bash
cd C:/Users/abbea/dev/1-mina-projekt/birdy && git fetch origin
git worktree add C:/w/birdy-flock -b website/flocken-lyfter origin/main
cd C:/w/birdy-flock/website && npm ci
mkdir -p C:/w/birdy-flock-qa
```

Expected: `npm ci` ends with `added N packages` and no error.

- [ ] **Step 2: The test suites are green before any change**

```bash
cd C:/w/birdy-flock/website && npm run test:unit && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test --workers=2
```

Expected: all unit tests pass; Playwright ends with `N passed` and no failures. Note N in the task report.

- [ ] **Step 3: `astro check` baseline**

```bash
cd C:/w/birdy-flock/website && npx astro check 2>&1 | tail -3
```

Expected: a line `Result (… files):` with an error count. Note the count; Task 9 must not add errors.

- [ ] **Step 4: Lighthouse baseline on `/sv/` (three runs)**

Save this summariser as `C:/w/birdy-flock-qa/lh-summary.mjs` (outside the repo):

```js
// Summarises Lighthouse JSON reports: the four category scores, LCP, Speed Index, TBT and CLS per run, the median, and
// the LCP element. Usage: node lh-summary.mjs <report.json>...
import { readFileSync } from 'node:fs';

const findSnippet = (node) => {
  if (!node || typeof node !== 'object') return undefined;
  if (typeof node.snippet === 'string') return node.snippet;
  for (const value of Object.values(node)) {
    const hit = findSnippet(value);
    if (hit) return hit;
  }
  return undefined;
};
const rows = process.argv.slice(2).map((file) => {
  const r = JSON.parse(readFileSync(file, 'utf8'));
  const score = (id) => Math.round(r.categories[id].score * 100);
  return {
    file: file.split(/[\\/]/).pop(),
    perf: score('performance'),
    a11y: score('accessibility'),
    bp: score('best-practices'),
    seo: score('seo'),
    lcp: Math.round(r.audits['largest-contentful-paint'].numericValue),
    si: Math.round(r.audits['speed-index'].numericValue),
    tbt: Math.round(r.audits['total-blocking-time'].numericValue),
    cls: Number(r.audits['cumulative-layout-shift'].numericValue.toFixed(3)),
    lcpElement: (findSnippet(r.audits['largest-contentful-paint-element']?.details) ?? '?').slice(0, 80),
  };
});
console.table(rows);
const median = (key) => rows.map((row) => row[key]).sort((a, b) => a - b)[Math.floor(rows.length / 2)];
console.log('median', Object.fromEntries(['perf', 'a11y', 'bp', 'seo', 'lcp', 'si', 'tbt', 'cls'].map((k) => [k, median(k)])));
```

Start the preview of the fixture build from Step 2 in the background (Bash `run_in_background`, or a second terminal) and keep it running for the three runs:

```bash
cd C:/w/birdy-flock/website && npm run preview -- --port 4743
```

Then:

```bash
for i in 1 2 3; do npx -y lighthouse@12 http://localhost:4743/sv/ --only-categories=performance,accessibility,best-practices,seo --chrome-flags="--headless=new" --output=json --output-path=C:/w/birdy-flock-qa/baseline-sv-$i.json --quiet; done
node C:/w/birdy-flock-qa/lh-summary.mjs C:/w/birdy-flock-qa/baseline-sv-1.json C:/w/birdy-flock-qa/baseline-sv-2.json C:/w/birdy-flock-qa/baseline-sv-3.json
```

Expected: a table of three runs and a `median` line, about `perf 93, a11y 100, bp 96, seo 100` (96 comes from the local `/_vercel/insights` 404). An `EPERM` from chrome-launcher after a run is harmless. Stop the preview. Note the median line; Task 10 compares against it.

---

### Task 1: The flock data as a module

**Files:**
- Create: `website/scripts/import-flock-data.mjs`
- Create (generated): `website/src/components/hero/flock-data.mjs`
- Test: `website/tests/unit/flock-data.unit.mjs`

- [ ] **Step 1: Write the failing test**

Create `website/tests/unit/flock-data.unit.mjs`:

```js
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';
import { FLOCK, MARK } from '../../src/components/hero/flock-data.mjs';

// The flock Albin approved: the social profiles' "cover" flock, which the motion prototype draws. The source file is a
// browser script (window.FLOCK = ...), so it runs in a sandbox like scripts/import-flock-data.mjs does, and is copied
// through JSON so its arrays compare with this realm's.
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(new URL('../../../docs/superpowers/specs/assets/2026-10-08-flocken-webben/flock-data.js', import.meta.url), 'utf8'), sandbox);
const source = JSON.parse(JSON.stringify(sandbox.window.FLOCK));

test('flockdatan är prototypens flock, fågel för fågel', () => {
  assert.equal(MARK.path, source.path);
  assert.deepEqual([MARK.w, MARK.h, MARK.cx, MARK.cy], [source.w, source.h, source.mec.cx, source.mec.cy]);
  assert.deepEqual([FLOCK.w, FLOCK.h, FLOCK.edge], [source.flocks.cover.w, source.flocks.cover.h, source.flocks.cover.edge]);
  assert.deepEqual(FLOCK.birds, source.flocks.cover.birds);
});

test('flockdatan: en fågel per art i Birdy, 178 i hjärtat, rimliga värden', () => {
  assert.equal(FLOCK.birds.length, 839);
  assert.equal(FLOCK.edge, 178);
  for (const [i, bird] of FLOCK.birds.entries()) {
    const [x, y, size, rot, colour, opacity] = bird;
    assert.equal(bird.length, 6, `fågel ${i}`);
    assert.ok(x >= 0 && x <= FLOCK.w, `fågel ${i}: x ${x}`);
    assert.ok(y >= 0 && y <= FLOCK.h * 1.2, `fågel ${i}: y ${y}`);
    assert.ok(size > 0 && size < 40, `fågel ${i}: storlek ${size}`);
    assert.ok(Number.isFinite(rot), `fågel ${i}: vridning ${rot}`);
    assert.ok([0, 1, 2, 3].includes(colour), `fågel ${i}: färg ${colour}`);
    assert.ok(opacity > 0 && opacity <= 1, `fågel ${i}: opacitet ${opacity}`);
  }
});
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd C:/w/birdy-flock/website && node --test tests/unit/flock-data.unit.mjs`
Expected: FAIL with `ERR_MODULE_NOT_FOUND` for `src/components/hero/flock-data.mjs`.

- [ ] **Step 3: Write the import script**

Create `website/scripts/import-flock-data.mjs`:

```js
#!/usr/bin/env node
// Writes src/components/hero/flock-data.mjs from the flock of Birdy's social profiles, which the approved motion
// prototype also draws (docs/superpowers/specs/assets/2026-10-08-flocken-webben/flock-data.js, the "cover" flock), so
// the website shows exactly the bird Albin approved. Run it again only if that file changes:
//   node scripts/import-flock-data.mjs
// tests/unit/flock-data.unit.mjs checks the module against the source file bird for bird.
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';
import vm from 'node:vm';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const source = resolve(root, '../docs/superpowers/specs/assets/2026-10-08-flocken-webben/flock-data.js');
const out = resolve(root, 'src/components/hero/flock-data.mjs');

// The source file is a browser script (window.FLOCK = {...}); run it in a sandbox to read the object.
const sandbox = { window: {} };
vm.runInNewContext(readFileSync(source, 'utf8'), sandbox);
const { path, w, h, mec, flocks } = sandbox.window.FLOCK;
const cover = flocks.cover;
if (cover.birds.length !== 839) throw new Error(`${source}: väntade 839 fåglar, fick ${cover.birds.length}`);

const lines = [
  '// Generated by scripts/import-flock-data.mjs from docs/superpowers/specs/assets/2026-10-08-flocken-webben/flock-data.js',
  "// (the \"cover\" flock of Birdy's social profiles and of the approved motion prototype). Do not edit by hand.",
  '',
  "/** Birdy's bird mark: an SVG path in a w × h box; (cx, cy) is the centre of its smallest enclosing circle. */",
  `export const MARK = ${JSON.stringify({ path, w, h, cx: mec.cx, cy: mec.cy })};`,
  '',
  '/**',
  ' * The flock: 839 birds, one for every species in Birdy, in a w × h box. A bird is [x, y, size, rotation in',
  " * degrees, colour index, opacity]; (x, y) is where it lands and size is the mark's width. The first `edge` birds",
  " * form the heart of Birdy's bird.",
  ' */',
  'export const FLOCK = {',
  `  w: ${cover.w},`,
  `  h: ${cover.h},`,
  `  edge: ${cover.edge},`,
  '  birds: [',
  ...cover.birds.map((b) => `    ${JSON.stringify(b)},`),
  '  ],',
  '};',
  '',
];
writeFileSync(out, lines.join('\n'));
console.log(`${out}: ${cover.birds.length} fåglar, varav ${cover.edge} i hjärtat`);
```

- [ ] **Step 4: Generate the module**

Run: `cd C:/w/birdy-flock/website && node scripts/import-flock-data.mjs`
Expected: `C:\w\birdy-flock\website\src\components\hero\flock-data.mjs: 839 fåglar, varav 178 i hjärtat`, and the file is about 31 kB (`wc -c src/components/hero/flock-data.mjs`).

- [ ] **Step 5: Run the test and the palette guard**

Run: `cd C:/w/birdy-flock/website && node --test tests/unit/flock-data.unit.mjs && npm run test:palette`
Expected: the test summary shows `pass 2` and `fail 0`, then `palette-guard OK (99 filer, inget mossgrönt)` (the new module is scanned; it has no moss colours).

- [ ] **Step 6: Commit**

```bash
cd C:/w/birdy-flock && git add website/scripts/import-flock-data.mjs website/src/components/hero/flock-data.mjs website/tests/unit/flock-data.unit.mjs
git commit -m "feat(webb): flockdatan som modul, 839 fåglar ur de sociala profilernas flock"
```

---

### Task 2: The flock logic (`flock.mjs`)

**Files:**
- Create: `website/src/components/hero/flock.mjs`
- Test: `website/tests/unit/flock.unit.mjs`

- [ ] **Step 1: Write the failing tests**

Create `website/tests/unit/flock.unit.mjs`:

```js
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { FLOCK, MARK } from '../../src/components/hero/flock-data.mjs';
import { COLOURS, DISC_SCALE, LIT, LIT_SCALE, VIEW, fitView, flightPlan, flockIndexFor, flockSvg, landing, rng } from '../../src/components/hero/flock.mjs';
import { loadAppSpeciesSnapshot } from '../../src/lib/daily-bird.mjs';

const appSpecies = loadAppSpeciesSnapshot(fileURLToPath(new URL('../..', import.meta.url)));

test('flockIndexFor: samma art får alltid samma fågel, alltid en i hjärtat', () => {
  assert.equal(flockIndexFor('Q25384'), 2); // Hornuggla, testbyggets Dagens fågel
  assert.equal(flockIndexFor('Q25485'), 107); // Talgoxe, reservfågeln när ingen art har sida
  assert.equal(flockIndexFor('Q26026'), 84); // Ringduva, prototypens Dagens fågel
  assert.equal(flockIndexFor('Q25334'), 74); // Rödhake, delningsbilden
  for (const s of appSpecies) {
    const i = flockIndexFor(s.id);
    assert.ok(Number.isInteger(i) && i >= 0 && i < FLOCK.edge, `${s.id} -> ${i}`);
  }
});

test('flockIndexFor: appens arter sprids över hela hjärtat, grannar hamnar isär', () => {
  assert.equal(appSpecies.length, FLOCK.birds.length, 'en fågel i flocken för varje art i appen');
  const perBird = new Array(FLOCK.edge).fill(0);
  for (const s of appSpecies) perBird[flockIndexFor(s.id)] += 1;
  assert.equal(perBird.filter((n) => n > 0).length, FLOCK.edge, 'varje fågel i hjärtat står för minst en art');
  assert.ok(Math.max(...perBird) <= 14, `högst ${Math.max(...perBird)} arter på samma fågel (snitt ${(appSpecies.length / FLOCK.edge).toFixed(1)})`);
  const neighbours = ['Q25480', 'Q25481', 'Q25482', 'Q25483', 'Q25484', 'Q25485', 'Q25486', 'Q25487', 'Q25488', 'Q25489'].map(flockIndexFor);
  assert.equal(new Set(neighbours).size, neighbours.length, 'QID som skiljer på en siffra får olika fåglar');
});

test('fitView: VIEW i en ruta som xMidYMid meet', () => {
  assert.deepEqual(VIEW, { x: 830, y: 0, w: 680, h: 720 });
  assert.deepEqual(fitView({ left: 0, top: 0, width: 680, height: 720 }), { s: 1, ox: -830, oy: 0 });
  assert.deepEqual(fitView({ left: 100, top: 50, width: 1360, height: 720 }), { s: 1, ox: -390, oy: 50 });
  const tall = fitView({ left: 0, top: 0, width: 340, height: 1000 });
  assert.equal(tall.s, 0.5);
  assert.equal(tall.oy, (1000 - 360) / 2);
});

test('landing: fågelns plats, storlek och vridning i rutans pixlar', () => {
  const fit = fitView({ left: 0, top: 0, width: 680, height: 720 });
  const [x, y, size, rot] = FLOCK.birds[0];
  assert.deepEqual(landing(FLOCK.birds[0], fit), { x: x - 830, y, size, rot });
});

test('rng: samma frö ger samma följd', () => {
  const a = rng(839);
  const b = rng(839);
  const first = [a(), a(), a()];
  assert.deepEqual(first, [b(), b(), b()]);
  assert.equal(first[0], 0.8682231577113271);
  for (const v of first) assert.ok(v >= 0 && v < 1);
});

test('flightPlan: samma flykt varje gång, dagens fågel landar sist', () => {
  const fit = fitView({ left: 700, top: 100, width: 654, height: 692 });
  const input = { fit, width: 1440, height: 820, litIndex: 2 };
  const plan = flightPlan(input);
  assert.deepEqual(flightPlan(input), plan);
  assert.equal(plan.birds.length, FLOCK.birds.length - 1);
  const lastOther = Math.max(...plan.birds.map((p) => p.delay + p.dur));
  assert.equal(plan.lit.delay + plan.lit.dur, plan.total);
  assert.ok(plan.total > lastOther, 'dagens fågel landar sist');
  assert.ok(plan.total > 4500 && plan.total < 5500, `flykten tar ${Math.round(plan.total)} ms`);
  const lit = landing(FLOCK.birds[2], fit);
  assert.equal(plan.lit.fx, lit.x);
  assert.equal(plan.lit.fy, lit.y);
  for (const p of plan.birds) assert.ok(p.sx < 0 && p.sy > 820 * 0.8, 'alla startar nere till vänster');
  assert.throws(() => flightPlan({ ...input, litIndex: 9999 }), /ingen fågel/);
});

test('flockSvg: den landade flocken med dagens fågel tänd, i VIEW:s koordinater', () => {
  const svg = flockSvg({ litIndex: 2 });
  assert.ok(svg.startsWith('<svg class="flock-still" viewBox="830 0 680 720" preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false">'));
  assert.equal((svg.match(/<use /g) ?? []).length, FLOCK.birds.length);
  assert.equal((svg.match(/<circle /g) ?? []).length, 1);
  for (const colour of COLOURS) assert.ok(svg.includes(`<g fill="${colour}">`), colour);
  assert.ok(!svg.includes('NaN'));
  const [x, y, size, rot] = FLOCK.birds[2];
  assert.ok(svg.includes(`<circle cx="${x}" cy="${y}" r="${Math.round(size * DISC_SCALE * 100) / 100}" fill="${LIT.disc}" stroke="${LIT.ring}"`));
  // Today's bird is drawn last, bigger, in rust: its matrix puts the mark's centre on the bird's place.
  const m = svg.match(/<use href="#flock-mark" fill="#9A4526" transform="matrix\(([^)]+)\)"\/><\/svg>$/);
  assert.ok(m, 'dagens fågel sist');
  const [a, b, c, d, e, f] = m[1].split(' ').map(Number);
  assert.ok(Math.abs(a * MARK.cx + c * MARK.cy + e - x) < 0.05, 'x');
  assert.ok(Math.abs(b * MARK.cx + d * MARK.cy + f - y) < 0.05, 'y');
  assert.ok(Math.abs(Math.hypot(a, b) - (size * LIT_SCALE) / MARK.w) < 1e-4, 'storlek');
  assert.ok(Math.abs((Math.atan2(b, a) * 180) / Math.PI - rot) < 0.01, 'vridning');
});
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd C:/w/birdy-flock/website && node --test tests/unit/flock.unit.mjs`
Expected: FAIL with `ERR_MODULE_NOT_FOUND` for `src/components/hero/flock.mjs`.

- [ ] **Step 3: Write `flock.mjs`**

Create `website/src/components/hero/flock.mjs`:

```js
// The flock on the home page (spec docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md): which bird
// lights up for a species, where the flock lands in a box, how each bird flies there, and the landed flock as SVG (the
// page without JavaScript and the share images). Plain JS, so node --test, the Astro frontmatter, the browser script
// (flock-motion.ts) and tools/generate-og.mjs all use the same code. The motion is the approved prototype's
// (docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html, version 3).
import { FLOCK, MARK } from './flock-data.mjs';

/** The flock's four colours (the social profiles'): brass, copper, deep rust and bark. A bird's colour index picks one. */
export const COLOURS = ['#B8893A', '#A8552D', '#72301A', '#4A1F12'];
/** Today's bird: rust, on a cream disc with a copper ring. */
export const LIT = { bird: '#9A4526', disc: 'rgba(255, 248, 238, 0.96)', ring: '#A8552D' };
/** Today's bird is drawn this many times its size in the flock, on a disc of this many times its size in radius. */
export const LIT_SCALE = 1.8;
export const DISC_SCALE = 1.6;

/** The part of the flock (flock units) that every layout fits into its box: Birdy's bird with a little air. */
export const VIEW = { x: 830, y: 0, w: 680, h: 720 };

/**
 * One bird's flight, in the fit's pixels and in milliseconds.
 * @typedef {object} Flyer
 * @property {number} fx landing x
 * @property {number} fy landing y
 * @property {number} size the mark's width when landed
 * @property {number} rot rotation in degrees when landed
 * @property {number} colour index into COLOURS
 * @property {number} op opacity
 * @property {number} sx start x
 * @property {number} sy start y
 * @property {number} cx the curve's control point x
 * @property {number} cy the curve's control point y
 * @property {number} delay ms before it sets off
 * @property {number} dur ms in the air
 * @property {number} amp the wave's amplitude in px
 * @property {number} freq the wave's frequency in Hz
 * @property {number} phase the wave's phase, close for neighbours, so the flock moves as one body
 */

/**
 * The bird in the flock that stands for a species: the same bird for the same QID on every page and every day. It is
 * always one of the first FLOCK.edge birds (the heart of Birdy's bird), so the light sits on the bird itself. FNV-1a
 * over the QID, then MurmurHash3's finaliser, so QIDs that differ in one digit land far apart.
 * @param {string} qid
 * @returns {number}
 */
export function flockIndexFor(qid) {
  let h = 0x811c9dc5;
  for (let i = 0; i < qid.length; i += 1) {
    h ^= qid.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  h ^= h >>> 16;
  h = Math.imul(h, 0x85ebca6b);
  h ^= h >>> 13;
  h = Math.imul(h, 0xc2b2ae35);
  h ^= h >>> 16;
  return (h >>> 0) % FLOCK.edge;
}

/**
 * Where VIEW lands in a box of CSS pixels, like SVG's preserveAspectRatio="xMidYMid meet": a point (x, y) of the flock
 * is drawn at (ox + x * s, oy + y * s).
 * @param {{ left: number, top: number, width: number, height: number }} box
 * @returns {{ s: number, ox: number, oy: number }}
 */
export function fitView(box) {
  const s = Math.min(box.width / VIEW.w, box.height / VIEW.h);
  return {
    s,
    ox: box.left + (box.width - VIEW.w * s) / 2 - VIEW.x * s,
    oy: box.top + (box.height - VIEW.h * s) / 2 - VIEW.y * s,
  };
}

/**
 * Where a bird lands, in the fit's pixels: centre, size (the mark's width) and rotation in degrees.
 * @param {number[]} bird
 * @param {{ s: number, ox: number, oy: number }} fit
 * @returns {{ x: number, y: number, size: number, rot: number }}
 */
export function landing(bird, fit) {
  return { x: fit.ox + bird[0] * fit.s, y: fit.oy + bird[1] * fit.s, size: bird[2] * fit.s, rot: bird[3] };
}

/**
 * Seeded randomness (mulberry32), so the flight is the same on every visit.
 * @param {number} seed
 * @returns {() => number}
 */
export function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = s;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * The flight (the prototype's buildPlan, the same random numbers in the same order): every bird starts below and left
 * of the area, flies a curve that stays low (under the words) and lands on its place; the river fills from the left and
 * the heart arrives a little later. Today's bird is taken out of the list and lands last.
 * @param {{ fit: { s: number, ox: number, oy: number }, width: number, height: number, litIndex: number }} input
 *   width and height: the canvas in CSS px
 * @returns {{ birds: Flyer[], lit: Flyer, total: number }} total: ms from the start until today's bird has landed
 */
export function flightPlan({ fit, width, height, litIndex }) {
  const r = rng(839);
  const xs = FLOCK.birds.map((b) => b[0]);
  const minX = Math.min(...xs);
  const maxX = Math.max(...xs);
  /** @type {Flyer[]} */
  const birds = [];
  /** @type {Flyer | null} */
  let lit = null;
  let latest = 0;
  FLOCK.birds.forEach((b, i) => {
    const fx = fit.ox + b[0] * fit.s;
    const fy = fit.oy + b[1] * fit.s;
    const xn = (b[0] - minX) / (maxX - minX);
    const delay = xn * 1150 + r() * 260 + (i < FLOCK.edge ? 300 : 0);
    const dur = 1500 + r() * 700;
    const sx = -40 - r() * width * 0.45;
    const sy = height * (0.88 + r() * 0.28);
    const low = Math.max(fy, height * 0.72);
    const cx = sx + (fx - sx) * (0.55 + r() * 0.2);
    const cy = low + 20 + r() * 60;
    /** @type {Flyer} */
    const p = {
      fx, fy, size: b[2] * fit.s, rot: b[3], colour: b[4], op: b[5],
      sx, sy, cx, cy, delay, dur,
      amp: 6 + r() * 16, freq: 0.7 + r() * 0.6, phase: (sy / height) * 2.2 + r() * 0.25,
    };
    if (i === litIndex) {
      lit = p;
      return;
    }
    birds.push(p);
    latest = Math.max(latest, delay + dur);
  });
  if (!lit) throw new Error(`flightPlan: ingen fågel med index ${litIndex}`);
  /** @type {Flyer} */
  const today = lit;
  today.delay = latest - today.dur * 0.35 + 250;
  today.dur = 1800;
  return { birds, lit: today, total: today.delay + today.dur };
}

const r2 = (n) => Math.round(n * 100) / 100;
const r5 = (n) => Math.round(n * 100000) / 100000;

/** The SVG transform that draws MARK at a bird's place (flock units): centre (x, y), width `size`, rotated `rot` deg. */
function markMatrix(x, y, size, rot) {
  const k = size / MARK.w;
  const a = (rot * Math.PI) / 180;
  const c = Math.cos(a) * k;
  const s = Math.sin(a) * k;
  // translate(x y) rotate(rot) scale(k) translate(-cx -cy), as one matrix: the same transform the canvas draws.
  return `matrix(${r5(c)} ${r5(s)} ${r5(-s)} ${r5(c)} ${r2(x - c * MARK.cx + s * MARK.cy)} ${r2(y - s * MARK.cx - c * MARK.cy)})`;
}

/**
 * The landed flock with today's bird lit, as SVG markup in VIEW's coordinates. The box it is drawn in decides the size;
 * preserveAspectRatio matches fitView, so it lands where the canvas draws the same frame. Birds outside VIEW (the river)
 * are drawn too and show when the svg's overflow is visible.
 * @param {{ litIndex: number, className?: string }} input
 * @returns {string}
 */
export function flockSvg({ litIndex, className = 'flock-still' }) {
  const groups = COLOURS.map((colour, ci) => {
    const uses = FLOCK.birds
      .map((b, i) => (i === litIndex || b[4] !== ci ? '' : `<use href="#flock-mark" transform="${markMatrix(b[0], b[1], b[2], b[3])}"${b[5] < 1 ? ` fill-opacity="${b[5]}"` : ''}/>`))
      .join('');
    return `<g fill="${colour}">${uses}</g>`;
  }).join('');
  const lit = FLOCK.birds[litIndex];
  const disc = `<circle cx="${lit[0]}" cy="${lit[1]}" r="${r2(lit[2] * DISC_SCALE)}" fill="${LIT.disc}" stroke="${LIT.ring}" stroke-width="2" vector-effect="non-scaling-stroke"/>`;
  const bird = `<use href="#flock-mark" fill="${LIT.bird}" transform="${markMatrix(lit[0], lit[1], lit[2] * LIT_SCALE, lit[3])}"/>`;
  return `<svg class="${className}" viewBox="${VIEW.x} ${VIEW.y} ${VIEW.w} ${VIEW.h}" preserveAspectRatio="xMidYMid meet" aria-hidden="true" focusable="false"><defs><path id="flock-mark" d="${MARK.path}"/></defs>${groups}${disc}${bird}</svg>`;
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd C:/w/birdy-flock/website && node --test tests/unit/flock.unit.mjs && npm run test:unit`
Expected: for `flock.unit.mjs` the summary shows `pass 7` and `fail 0`; the whole unit suite passes. (This exact test file and module were run against the prototype's data before the plan was written: 7 of 7 pass.)

- [ ] **Step 5: Commit**

```bash
cd C:/w/birdy-flock && git add website/src/components/hero/flock.mjs website/tests/unit/flock.unit.mjs
git commit -m "feat(webb): flockens logik, vilken fågel som tänds, var flocken landar, flykten och slutbilden som SVG"
```

---

### Task 3: The peach tones and the contrast guard

**Files:**
- Modify: `website/src/styles/tokens.css` (after `--peach: #FDE5CB;`, line 12)
- Modify: `website/scripts/check-contrast.mjs` (the `pairs` list, lines 63 to 71, and the Hero/FinalCta composited pair, lines 112 to 113)

- [ ] **Step 1: Add the pairs to the guard (failing)**

In `website/scripts/check-contrast.mjs`, replace

```js
  ['ink', 'peach', 4.5], ['muted', 'peach', 4.5], ['rust', 'peach', 4.5],
```

with

```js
  ['ink', 'peach', 4.5], ['muted', 'peach', 4.5], ['rust', 'peach', 4.5],
  // The home hero's peach paper (spec 2026-10-09-startsidan-flocken-lyfter): the words sit on it from its lightest
  // tone to its deepest.
  ['ink', 'peach-hi', 4.5], ['muted', 'peach-hi', 4.5], ['rust', 'peach-hi', 4.5],
  ['ink', 'peach-lo', 4.5], ['muted', 'peach-lo', 4.5], ['rust', 'peach-lo', 4.5],
```

and replace

```js
  // Hero.astro .sub (.86) and FinalCta.astro .sub (.85) on the espresso wall, over its lightest point (#3D2C22).
  { label: 'Hero/FinalCta .sub', fg: [255, 248, 238], alpha: 0.85, bg: [61, 44, 34], min: 4.5 },
```

with

```js
  // FinalCta.astro .sub (.85) on the espresso wall, over its lightest point (#3D2C22). The hero left the wall on 2026-10-09.
  { label: 'FinalCta .sub', fg: [255, 248, 238], alpha: 0.85, bg: [61, 44, 34], min: 4.5 },
```

- [ ] **Step 2: Run the guard to verify it fails**

Run: `cd C:/w/birdy-flock/website && npm run test:contrast`
Expected: FAIL with `contrast-guard FAILED: token --peach-hi saknas eller är inte #RRGGBB i tokens.css` (and the same for `--peach-lo`).

- [ ] **Step 3: Add the tokens**

In `website/src/styles/tokens.css`, replace

```css
  --peach: #FDE5CB;
```

with

```css
  --peach: #FDE5CB;
  /* The home hero's peach paper (spec 2026-10-09): lighter where the light falls, deeper at the edges. */
  --peach-hi: #FEEBD6;
  --peach-lo: #F8D6B4;
```

- [ ] **Step 4: Run the guards to verify they pass**

Run: `cd C:/w/birdy-flock/website && npm run test:contrast && npm run test:palette`
Expected: `contrast-guard OK (33 par)` (muted on `--peach-lo` is the lowest new pair, 4.83:1) and `palette-guard OK`.

- [ ] **Step 5: Commit**

```bash
cd C:/w/birdy-flock && git add website/src/styles/tokens.css website/scripts/check-contrast.mjs
git commit -m "feat(webb): persikopapprets toner som tokens, kontrastvakten täcker orden på persika"
```

---

### Task 4: The hero's words, the landed flock and the polaroid (no motion yet)

**Files:**
- Modify: `website/tests/home.spec.ts` (imports at the top; the `utan JavaScript` describe, lines 169 to 178; replace lines 180 to 310, the `första vyn` and `Birdy-fågeln flyger` describes)
- Modify: `website/tests/faltbok.spec.ts` (lines 7 to 8, 18 to 20, 95 to 117, 183)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (the `hero` and `daily` blocks, lines 25 to 40)
- Create: `website/src/components/hero/Polaroid.astro`
- Replace: `website/src/components/Hero.astro`
- Modify: `website/src/components/MonthBirds.astro:30`
- Delete: `website/src/components/hero/DailyBirdPlate.astro`, `website/src/components/hero/BirdyBird.astro`, `website/src/components/hero/hero-motion.ts`
- Modify: `website/scripts/check-empty-hub.mjs` (lines 97 to 112)

- [ ] **Step 1: Write the failing tests in `home.spec.ts`**

(Line numbers in this task refer to the files as they are before the task; the edits are anchored on the quoted text.)

At the top of `website/tests/home.spec.ts`, replace

```ts
import { trackConsoleErrors } from './test-helpers';
```

with

```ts
import { trackConsoleErrors } from './test-helpers';
import { FLOCK } from '../src/components/hero/flock-data.mjs';
import { DISC_SCALE, fitView, landing } from '../src/components/hero/flock.mjs';

type Box = { left: number; top: number; right: number; bottom: number };
```

In the top-level `utan JavaScript` describe, after the test `startsidans meny är espressobrun och den döda menyknappen dold` (before the describe's closing `});`), add:

```ts

  // Without JavaScript the <noscript> SVG shows the landed flock with Hornuggla's bird lit, in exactly the fit box the
  // canvas uses, and the polaroid hangs at once.
  test('hjälten visar den landade flocken och polaroiden utan JavaScript', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const still = page.locator('[data-hero] [data-flock-fit] svg.flock-still');
    await expect(still).toBeVisible();
    await expect(still.locator('use')).toHaveCount(FLOCK.birds.length);
    await expect(still.locator('circle')).toHaveCount(1);
    expect(await still.boundingBox()).toEqual(await page.locator('[data-hero] [data-flock-fit]').boundingBox());
    await expect(page.locator('[data-hero] [data-polaroid]')).toBeVisible();
    await expect(page.locator('[data-hero] [data-polaroid] .pol-name')).toHaveText('Dagens fågel: Hornuggla');
  });
```

Replace everything from the line `test.describe('första vyn', () => {` (line 180) through the closing `});` of `test.describe('Birdy-fågeln flyger', …)` (line 310, the line before `test.describe('fåglarna i månaden', () => {`) with:

```ts
test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  // The fixture build pins BIRDY_TODAY=2026-10-15 (package.json build:fixtures); every build picks from the shipped app's
  // list (src/data/app-species-1.3.0.json), so the app's Dagens fågel is Hornuggla (Q25384), which has a fixture page, on
  // the day 1.3.0 goes out: the polaroid shows it with the line about the app.
  for (const [path, line1, line2, kicker, caption, same, credit, label] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Kamera, foto eller läte', 'Dagens fågel: Hornuggla', 'samma fågel som i appen i dag', 'Foto: Testfotograf, CC BY 4.0, via Wikimedia Commons, nedskalad', 'Dagens fågel, Hornuggla, lyser i flocken.'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Camera, photo or song', 'Bird of the day: Long-eared Owl', 'the same bird as in the app today', 'Photo: Testfotograf, CC BY 4.0, via Wikimedia Commons, resized', 'The bird of the day, Long-eared Owl, is lit up in the flock.'],
  ] as const) {
    test(`rubrik, flocken och Dagens fågel som polaroid på ${path}`, async ({ page, request }) => {
      const errors = trackConsoleErrors(page);
      // The browser's day is the build's day, so the stale-day guard (hero/same-as-app-guard.ts) keeps the app line.
      await page.clock.setFixedTime(new Date('2026-10-15T12:00:00+02:00'));
      await page.goto(path);
      const hero = page.locator('[data-hero]');
      await expect(hero.locator('h1')).toContainText(line1);
      await expect(hero.locator('h1 em')).toHaveText(line2);
      await expect(hero.locator('.intro .kick')).toHaveText(kicker);
      await expect(hero).toHaveAttribute('data-date', '2026-10-15');
      await expect(hero).toHaveAttribute('data-app-bird', 'Q25384');
      await expect(hero).toHaveAttribute('data-daily-bird', 'Q25384');
      // Hornuggla's own bird in the flock (flockIndexFor in hero/flock.mjs, pinned in tests/unit/flock.unit.mjs).
      await expect(hero).toHaveAttribute('data-flock-index', '2');
      const canvas = hero.locator('canvas[data-flock-canvas]');
      await expect(canvas).toHaveAttribute('role', 'img');
      expect(await canvas.getAttribute('aria-label')).toContain(label);
      const polaroid = hero.locator('[data-polaroid]');
      await expect(polaroid).toBeVisible();
      await expect(polaroid.locator('.pol-name')).toHaveText(caption);
      const href = await polaroid.locator('a.pol-name').getAttribute('href');
      expect(href).toMatch(path === '/sv/' ? /^\/sv\/arter\/hornuggla\/$/ : /^\/species\/long-eared-owl\/$/);
      expect((await request.get(href!)).status()).toBe(200);
      await expect(polaroid.locator('a.pol-photo')).toHaveAttribute('href', href!);
      // Hornuggla's test photo is CC BY and the only one it has: it may hang whole, credited like the species page.
      await expect(polaroid.locator('[data-credit]')).toHaveText(credit);
      await expect(polaroid.locator('[data-credit] a[href^="https://creativecommons.org/licenses/by/4.0/"]')).toHaveText('CC BY 4.0');
      await expect(polaroid.locator('[data-credit] a[href^="https://commons.wikimedia.org/"]')).toHaveCount(1);
      await expect(hero.locator('[data-same-as-app]')).toHaveText(same);
      await expect(hero.locator('[data-same-as-app]')).toBeVisible();
      const img = polaroid.locator('img');
      await expect(img).toHaveAttribute('width', /^\d+$/);
      await expect(img).toHaveAttribute('height', /^\d+$/);
      // The words are the page's largest content, not the photo (spec): the photo loads early but never first.
      await expect(img).toHaveAttribute('fetchpriority', 'low');
      // Nothing is written next to or over the flock (Albin 2026-10-09): no margin note, no arrow.
      await expect(hero.locator('.mnote')).toHaveCount(0);
      await expect(hero.locator('.same-arrow')).toHaveCount(0);
      expect(errors).toEqual([]);
    });
  }

  // A page built yesterday (the nightly build did not run) must not claim today's bird is the app's: the guard hides the
  // line when the browser's day in Stockholm is not the build's day (review 2026-10-08).
  test('raden "samma fågel som i appen i dag" döljs när bygget är från en annan dag', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-10-16T09:00:00+02:00'));
    await page.goto('/sv/');
    const line = page.locator('[data-hero] [data-same-as-app]');
    await expect(line).toHaveCount(1);
    await expect(line).toBeHidden();
  });

  // Every width (spec: no layout shift, the photo whole, the tape never on it, nothing over the words): the polaroid stays
  // inside the hero and off the words; every bird that can be lit (the FLOCK.edge birds of the heart, one of which stands
  // for each species) stays inside the hero and out from under the polaroid; no landed bird lies under the words.
  // Positions come from the same fitView as the canvas and the <noscript> SVG (hero/flock.mjs).
  for (const path of ['/sv/', '/'] as const) {
    for (const [width, height] of [[320, 700], [390, 844], [768, 1024], [1024, 768], [1280, 800], [1440, 900], [1920, 1080]] as const) {
      test(`polaroiden, flocken och orden går fria från varandra på ${path} i ${width}×${height}`, async ({ page }) => {
        await page.setViewportSize({ width, height });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        await page.locator('[data-hero] [data-polaroid] img').evaluate((i: HTMLImageElement) => i.decode());
        const g = await page.evaluate(() => {
          const box = (el: Element) => {
            const r = el.getBoundingClientRect();
            return { left: r.left, top: r.top + scrollY, right: r.right, bottom: r.bottom + scrollY };
          };
          const $ = (selector: string) => document.querySelector(selector) as HTMLElement;
          const card = $('[data-hero] [data-polaroid]');
          const img = card.querySelector('img') as HTMLImageElement;
          const tape = card.querySelector('.tape') as HTMLElement;
          const out = {
            hero: box($('[data-hero]')),
            canvas: box($('[data-hero] [data-flock-canvas]')),
            fit: box($('[data-hero] [data-flock-fit]')),
            navHeight: $('#site-nav').getBoundingClientRect().height,
            words: ['.intro .kick', 'h1', '.intro .sub', '.intro .badges'].map((s) => box($(`[data-hero] ${s}`))),
            card: box(card),
            tape: box(tape),
            photo: { width: img.offsetWidth, height: img.offsetHeight, ratio: img.naturalWidth / img.naturalHeight },
            scrollWidth: document.documentElement.scrollWidth,
          };
          // The tape against the photo in the card's own frame (the card leans 2 degrees on the page).
          card.style.transform = 'none';
          const own = { tape: box(tape), photo: box(img) };
          card.style.transform = '';
          return { ...out, own };
        });
        const overlaps = (a: Box, b: Box, gap: number) => a.left < b.right + gap && a.right > b.left - gap && a.top < b.bottom + gap && a.bottom > b.top - gap;
        const touches = (x: number, y: number, r: number, b: Box) => {
          const cx = Math.max(b.left, Math.min(x, b.right));
          const cy = Math.max(b.top, Math.min(y, b.bottom));
          return (x - cx) ** 2 + (y - cy) ** 2 < r * r;
        };
        expect(Math.abs(g.photo.width / g.photo.height - g.photo.ratio), 'fotot visas helt').toBeLessThan(0.02);
        expect(g.own.tape.bottom, 'tejpen sitter på kortets kant, ovanför fotot').toBeLessThanOrEqual(g.own.photo.top);
        const card: Box = {
          left: Math.min(g.card.left, g.tape.left),
          top: Math.min(g.card.top, g.tape.top),
          right: Math.max(g.card.right, g.tape.right),
          bottom: Math.max(g.card.bottom, g.tape.bottom),
        };
        expect(card.left, 'polaroiden inom hjälten').toBeGreaterThanOrEqual(0);
        expect(card.right, 'polaroiden inom hjälten').toBeLessThanOrEqual(width);
        expect(card.top, 'polaroiden under menyn').toBeGreaterThanOrEqual(g.hero.top + g.navHeight);
        expect(card.bottom, 'polaroiden inom hjälten').toBeLessThanOrEqual(g.hero.bottom);
        for (const [i, word] of g.words.entries()) expect(overlaps(card, word, 8), `polaroiden över orden (${i})`).toBe(false);
        const fit = fitView({ left: g.fit.left - g.canvas.left, top: g.fit.top - g.canvas.top, width: g.fit.right - g.fit.left, height: g.fit.bottom - g.fit.top });
        for (let i = 0; i < FLOCK.edge; i += 1) {
          const b = landing(FLOCK.birds[i], fit);
          const x = b.x + g.canvas.left;
          const y = b.y + g.canvas.top;
          const r = b.size * DISC_SCALE + 3;
          expect(touches(x, y, r, card), `fågel ${i} under polaroiden`).toBe(false);
          expect(x - r >= 0 && x + r <= width && y - r >= g.hero.top + g.navHeight && y + r <= g.hero.bottom, `fågel ${i} inom hjälten`).toBe(true);
        }
        const under = FLOCK.birds.filter((bird) => {
          const b = landing(bird, fit);
          return g.words.some((word) => touches(b.x + g.canvas.left, b.y + g.canvas.top, b.size / 2, word));
        });
        expect(under.length, 'landade fåglar under orden').toBe(0);
        expect(g.scrollWidth, 'inget sidledes scroll').toBeLessThanOrEqual(width);
      });
    }
  }

  for (const [width, height] of [[390, 844], [1024, 768], [1440, 900]] as const) {
    test(`menyn blir espressobrun innan texten når den i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const nav = page.locator('#site-nav');
      const navH = await nav.evaluate((n) => n.getBoundingClientRect().height);
      const top = await page.locator('[data-hero] .intro').evaluate((c) => c.getBoundingClientRect().top + scrollY);
      const settle = () => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), Math.max(0, top - navH - 30));
      await settle();
      await expect(nav).not.toHaveClass(/is-solid/);
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), top - navH + 2);
      await expect(nav).toHaveClass(/is-solid/);
    });
  }

  test('orden börjar under menyn och den handskrivna raden ryms på en rad på dator', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      for (const width of [1024, 1280, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        const nav = (await page.locator('#site-nav').boundingBox())!;
        const kick = (await page.locator('[data-hero] .intro .kick').boundingBox())!;
        expect(kick.y, `${path} ${width} px`).toBeGreaterThanOrEqual(nav.y + nav.height);
        const em = await page.locator('[data-hero] h1 em').evaluate((e) => ({
          lines: Math.round((e as HTMLElement).offsetHeight / parseFloat(getComputedStyle(e).lineHeight)),
          over: e.scrollWidth - (e.parentElement as HTMLElement).clientWidth,
        }));
        expect(em.lines, `${path} ${width} px`).toBe(1);
        expect(em.over, `${path} ${width} px: raden går utanför spalten`).toBeLessThanOrEqual(0);
      }
    }
  });
});
```

- [ ] **Step 2: Update `faltbok.spec.ts`**

In `website/tests/faltbok.spec.ts`, replace

```ts
const ESPRESSO_DEEP = 'rgb(30, 20, 16)';
```

with

```ts
const ESPRESSO_DEEP = 'rgb(30, 20, 16)';
// The home page's hero is peach paper since 2026-10-09 (spec 2026-10-09-startsidan-flocken-lyfter).
const PEACH = 'rgb(253, 229, 203)';
```

replace

```ts
  test('de mörka partierna på /sv/ är espresso', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('[data-hero]')).toHaveCSS('background-color', ESPRESSO);
```

with

```ts
  test('hjälten på /sv/ är persikopapper och de mörka partierna espresso', async ({ page }) => {
    await page.goto('/sv/');
    await expect(page.locator('[data-hero]')).toHaveCSS('background-color', PEACH);
```

replace

```ts
    '/sv/': [copy.sv.hero.note, copy.sv.monthBirds.note,
```

with

```ts
    '/sv/': [copy.sv.monthBirds.note,
```

replace

```ts
    '/': [copy.en.hero.note, copy.en.monthBirds.note,
```

with

```ts
    '/': [copy.en.monthBirds.note,
```

replace

```ts
      // Peach on the hero's and the last section's wall (mockup lift-c.html), apricot on the Premium band.
      await expect(page.locator('[data-hero] .mnote')).toHaveCSS('color', 'rgb(253, 229, 203)');
```

with

```ts
      // Peach on the last section's wall (mockup lift-c.html), apricot on the Premium band. The hero has no margin note
      // since 2026-10-09: nothing is written next to the flock.
      await expect(page.locator('[data-hero] .mnote')).toHaveCount(0);
```

and replace

```ts
      ['#season > .deckle path', 'rgb(42, 29, 23)'],          // hero → Fåglarna i oktober
```

with

```ts
      ['#season > .deckle path', PEACH],                      // hero (persikopapper) → Fåglarna i oktober
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts tests/faltbok.spec.ts --workers=2 -g "första vyn|utan JavaScript|espresso|persikopapper|marginalanteckningar|papperskanter"`
Expected: FAIL. The hero tests fail on `.intro .kick` (`Fågelguide och fältdagbok`), on missing `[data-polaroid]` / `data-flock-index` / `[data-flock-fit]`, the faltbok tests on the espresso hero, the hero's margin note and the espresso torn edge. The `menyn blir espressobrun` tests may already pass.

- [ ] **Step 4: Update the copy**

In `website/src/content/copy.sv.json`, replace the `hero` and `daily` blocks (lines 25 to 40) with:

```json
  "hero": {
    "kicker": "Kamera, foto eller läte",
    "headline": "Känn igen fågeln.",
    "line2": "Bevara stunden.",
    "sub": "Rikta kameran, välj ett foto eller låt fågeln sjunga. Birdy föreslår arten direkt i telefonen, och du sparar fyndet i din egen fältdagbok.",
    "flockLabel": "En flock små fåglar, en för varje art i Birdy, som tillsammans bildar Birdys fågel. Dagens fågel, {name}, lyser i flocken."
  },
  "daily": {
    "polaroid": "Dagens fågel: {name}",
    "sameAsApp": "samma fågel som i appen i dag"
  },
```

In `website/src/content/copy.en.json`, replace the `hero` and `daily` blocks (lines 25 to 40) with:

```json
  "hero": {
    "kicker": "Camera, photo or song",
    "headline": "Know the bird.",
    "line2": "Keep the moment.",
    "sub": "Point the camera, pick a photo or let the bird sing. Birdy suggests the species right on your phone, and you save the sighting in your own field journal.",
    "flockLabel": "A flock of small birds, one for every species in Birdy, that together form Birdy's bird. The bird of the day, {name}, is lit up in the flock."
  },
  "daily": {
    "polaroid": "Bird of the day: {name}",
    "sameAsApp": "the same bird as in the app today"
  },
```

(The removed keys `hero.note`, `hero.noteFallback`, `daily.kicker`, `daily.weekdays`, `daily.months`, `daily.readMore` and `daily.chartLabel` were used only by the old hero, the old plate and the two checks updated in this task.)

- [ ] **Step 5: Create the polaroid**

Create `website/src/components/hero/Polaroid.astro`:

```astro
---
// Dagens fågel as a polaroid (spec docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md): the photo
// whole on a cream card, never cropped and with nothing drawn on it, which is why CC0, public domain, CC BY and CC BY-SA
// photos may all hang here (plateImage in src/lib/daily-bird.mjs); under it "Dagens fågel: {name}" linking to the
// species page, the line about the app on the days it is true, and the species page's photo credit (PhotoCredit.astro).
// The tape sits on the card's top edge, above the photo. hero/flock-motion.ts lifts the card out of today's bird.
import { Picture } from 'astro:assets';
import type { ImageMetadata } from 'astro';
import PhotoCredit from '../species/PhotoCredit.astro';
import { getCopy, type Locale } from '../../lib/i18n';

export interface PolaroidBird {
  name: string;
  scientific: string;
  /** Species page, when the bird has one in this build. */
  href?: string;
  image: ImageMetadata;
  author: string | null;
  license: string;
  licenseUrl: string | null;
  sourceUrl: string;
}

interface Props {
  locale: Locale;
  bird: PolaroidBird;
  /** Show "samma fågel som i appen i dag" (hero/same-as-app-guard.ts hides it again on a page built another day). */
  sameAsApp: boolean;
}
const { locale, bird, sameAsApp } = Astro.props;
const t = getCopy(locale);
const caption = t.daily.polaroid.replace('{name}', bird.name);
// A tall photo gets narrower inside the card instead of taller than --pol-ph (the photo is never cropped).
const ratio = bird.image.width / bird.image.height;
const alt = t.species.altHero.replace('{name}', bird.name).replace('{scientific}', bird.scientific);
const widths = [240, 360, 480, 640];
const sizes = '(min-width: 1024px) 216px, 200px';
---

<figure class="polaroid" data-polaroid style={`--ar:${ratio.toFixed(4)}`}>
  {bird.href ? (
    <a class="pol-photo" href={bird.href} tabindex="-1" aria-hidden="true">
      <Picture src={bird.image} formats={['avif', 'webp']} widths={widths} sizes={sizes} alt="" loading="eager" fetchpriority="low" decoding="async" />
    </a>
  ) : (
    <div class="pol-photo">
      <Picture src={bird.image} formats={['avif', 'webp']} widths={widths} sizes={sizes} alt={alt} loading="eager" fetchpriority="low" decoding="async" />
    </div>
  )}
  <figcaption class="pol-cap">
    {bird.href ? <a class="pol-name" href={bird.href}>{caption}</a> : <span class="pol-name">{caption}</span>}
    {sameAsApp && <p class="pol-same" data-same-as-app>{t.daily.sameAsApp}</p>}
    <p class="pol-credit" data-credit><PhotoCredit locale={locale} image={bird} /></p>
  </figcaption>
  <span class="tape" aria-hidden="true"></span>
</figure>

<style>
  /* A cream card leaning 2 degrees, the photo on top and the caption in the wide bottom margin. --ar is the photo's
     width / height; --pol-ph caps the photo's height. The hero places the card (Hero.astro). */
  .polaroid { position: relative; z-index: 3; width: clamp(172px, 48%, 220px); margin: 0; padding: 12px 10px 0; background: var(--card); box-shadow: 0 14px 34px rgba(60, 34, 20, .22), 0 1px 3px rgba(60, 34, 20, .14); transform: rotate(2deg); --pol-ph: 150px; }
  .pol-photo { display: block; }
  .pol-photo :global(img) { display: block; width: min(100%, calc(var(--pol-ph) * var(--ar))); height: auto; margin: 0 auto; }
  .pol-cap { display: grid; gap: 3px; padding: 8px 2px 11px; }
  .pol-name { font-family: var(--font-script); font-weight: 700; font-size: 21px; line-height: 1.05; color: var(--ink); }
  a.pol-name:hover { color: var(--rust); }
  .pol-same { margin: 0; font-family: var(--font-script); font-weight: 700; font-size: 17px; line-height: 1.05; color: var(--rust); }
  .pol-same[hidden] { display: none; }
  .pol-credit { margin: 3px 0 0; font-size: 10.5px; line-height: 1.45; color: var(--muted); }
  .pol-credit :global(a) { text-decoration: underline; text-underline-offset: 2px; }
  .pol-credit :global(a:hover) { color: var(--rust); }
  /* On the card's top edge: the card's 12 px top padding keeps the tilted tape off the photo (tests/home.spec.ts). */
  .tape { position: absolute; top: -18px; left: 50%; width: 84px; height: 22px; margin-left: -42px; transform: rotate(-8deg); background: linear-gradient(180deg, #F5C99B, #F0BB86); box-shadow: 0 1px 3px rgba(80, 50, 30, .2); opacity: .94; pointer-events: none; }
  @media (min-width: 1024px) {
    .polaroid { width: clamp(190px, 16vw, 236px); --pol-ph: 180px; }
  }
</style>
```

- [ ] **Step 6: Replace the hero**

Replace the whole of `website/src/components/Hero.astro` with:

```astro
---
// The first view, "Flocken lyfter" (spec docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md): peach
// paper, the slogan on the left (above the flock on narrow screens), and a flock of 839 small birds, one for every
// species in Birdy, that lands as Birdy's bird with today's bird lit (flockIndexFor in hero/flock.mjs). Dagens fågel
// hangs beside it as a polaroid (hero/Polaroid.astro): the same bird as the app's Dagens fågel when it has a species
// page with a photo that may be shown whole (plateImage in src/lib/daily-bird.mjs), rebuilt every night. The canvas is
// drawn by hero/flock-motion.ts; without JavaScript the <noscript> SVG shows the landed flock. Nothing is written next to
// or over the flock.
import PlayStoreBadge from './ui/PlayStoreBadge.astro';
import AppStoreBadge from './ui/AppStoreBadge.astro';
import Kicker from './ui/Kicker.astro';
import Polaroid, { type PolaroidBird } from './hero/Polaroid.astro';
import fallbackPhoto from '../assets/photos/talgoxe-q25485.webp';
import { type Locale, getCopy } from '../lib/i18n';
import { getAllSpecies, speciesHref, speciesImage, type Species } from '../lib/species';
import { plateImage, siteDailyBird } from '../lib/daily-bird.mjs';
import { flockIndexFor, flockSvg } from './hero/flock.mjs';
import { appQid, date } from 'virtual:birdy-daily-bird';

interface Props { locale: Locale }
const { locale } = Astro.props;
const t = getCopy(locale);
const playUrl = 'https://play.google.com/store/apps/details?id=se.birdy.android';

// Only species with a page and a photo that may be shown whole (plan house rules). The app's bird is replaced by another
// species only when it has no such photo.
const eligible = (await getAllSpecies()).filter((s) => plateImage(s.images));
const pick = siteDailyBird({ appQid, pageQids: eligible.map((s) => s.qid), date });
const species: Species | undefined = pick ? eligible.find((s) => s.qid === pick.qid) : undefined;

let bird: PolaroidBird;
if (species) {
  const photo = plateImage(species.images)!;
  bird = {
    name: species.names[locale],
    scientific: species.names.scientific,
    href: speciesHref(species, locale),
    image: speciesImage(photo.file),
    author: photo.author,
    license: photo.license,
    licenseUrl: photo.licenseUrl,
    sourceUrl: photo.sourceUrl,
  };
} else {
  // No species page with a photo that may be shown whole in this build (only the empty test build): the site's own CC0
  // great tit, without a link (src/assets/photos/SOURCES.md).
  bird = {
    name: locale === 'sv' ? 'Talgoxe' : 'Great Tit',
    scientific: 'Parus major',
    image: fallbackPhoto,
    author: 'Hobbyfotowiki',
    license: 'CC0',
    licenseUrl: null,
    sourceUrl: 'https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg',
  };
}
// "Samma fågel som i appen i dag" only when the polaroid shows the app's bird on a day 1.3 is live (review I4).
const sameAsApp = pick?.sameAsApp ?? false;
// The species' own bird in the flock lights up: the same bird on every day this species is Dagens fågel.
const litIndex = flockIndexFor(species?.qid ?? 'Q25485');
const flockLabel = t.hero.flockLabel.replace('{name}', bird.name);
---

<header class="hero" data-hero data-daily-bird={species?.qid ?? ''} data-app-bird={appQid ?? ''} data-date={date.iso} data-flock-index={litIndex}>
  <canvas class="flock-canvas" data-flock-canvas role="img" aria-label={flockLabel}></canvas>
  <div class="wrap lead">
    <div class="intro">
      <span class="nav-until" data-nav-until aria-hidden="true"></span>
      <Kicker text={t.hero.kicker} />
      <h1>{t.hero.headline} <em>{t.hero.line2}</em></h1>
      <p class="sub">{t.hero.sub}</p>
      <div class="badges">
        <PlayStoreBadge locale={locale} href={playUrl} alt={t.alt.playStoreBadge} size="small" loading="eager" />
        <AppStoreBadge locale={locale} size="small" />
      </div>
    </div>
  </div>
  <div class="stage">
    <div class="fit" data-flock-fit><noscript set:html={flockSvg({ litIndex })} /></div>
    <Polaroid locale={locale} bird={bird} sameAsApp={sameAsApp} />
  </div>
</header>

<script>
  import './hero/same-as-app-guard';
</script>

<style>
  /* Peach paper, lighter where the light falls (the social profiles' colours). The last 120 px fade to plain peach, the
     colour of the torn edge the next section tears down from (MonthBirds.astro's DeckleEdge). */
  .hero { position: relative; overflow: hidden; padding-top: 64px; color: var(--ink); background-color: var(--peach); background-image: linear-gradient(0deg, var(--peach) 0, rgba(253, 229, 203, 0) 120px), radial-gradient(130% 120% at 70% 40%, var(--peach-hi) 0%, var(--peach) 45%, var(--peach-lo) 100%); }
  /* The canvas covers the whole hero: the birds fly in from below left, under the words, to Birdy's bird. */
  .flock-canvas { position: absolute; inset: 0; z-index: 0; display: block; width: 100%; height: 100%; pointer-events: none; }
  .lead { position: relative; z-index: 2; padding-top: 40px; }
  .intro { position: relative; }
  .nav-until { position: absolute; left: 0; bottom: 100%; width: 1px; height: 100vh; pointer-events: none; }
  h1 { margin-top: 2px; font-size: clamp(42px, 11vw, 56px); line-height: .98; letter-spacing: -.02em; color: var(--ink); text-wrap: balance; }
  /* The handwritten half of the slogan, the same accent as JournalHeadline's (spec §5.1), tilted like on the profiles. */
  h1 em { display: block; width: fit-content; margin-top: 6px; color: var(--rust); font-family: var(--font-script); font-style: normal; font-weight: 700; font-size: 1.1em; letter-spacing: 0; line-height: .95; transform: rotate(-1.6deg); transform-origin: left; }
  .sub { max-width: 30rem; margin: 18px 0 0; font-size: 15.5px; line-height: 1.65; color: var(--muted); }
  .badges { margin-top: 22px; }
  /* Narrow screens: Birdy's bird under the words. .fit is the box the flock's VIEW (hero/flock.mjs, 680 × 720) fits
     into, the same box for the canvas and the <noscript> SVG. The polaroid hangs below the bird's heart (88 % of the
     stage's width is 83 % of the box's height), so it never covers the lit bird; flow-root keeps its margin inside. */
  .stage { position: relative; z-index: 1; display: flow-root; width: min(100%, 460px); margin: 6px auto 0; padding-bottom: 28px; }
  .fit { position: absolute; top: 0; left: 0; width: 100%; aspect-ratio: 680 / 720; }
  .fit :global(.flock-still) { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
  .stage :global(.polaroid) { margin: 88% 6% 0 auto; }

  /* 1024 px and up: the words on the left, the bird on the right, vertically centred under the menu and at most half the
     window wide; the polaroid to the left of the bird's heart (its right edge at 32 % of the box), clear of the words.
     tests/home.spec.ts checks the geometry at seven widths. */
  @media (min-width: 1024px) {
    .hero { padding-top: 76px; min-height: clamp(660px, calc(100svh - 40px), 820px); }
    .lead { padding-top: 56px; }
    .intro { max-width: clamp(420px, 34vw, 480px); }
    h1 { font-size: clamp(48px, 5vw, 72px); }
    h1 em { font-size: 1.04em; margin-top: 8px; white-space: nowrap; }
    .sub { font-size: 17px; max-width: 28rem; }
    .stage { position: absolute; top: calc(76px + 12px); bottom: 12px; right: max(12px, calc((100% - 1320px) / 2)); display: block; width: auto; height: min(calc(100% - 128px), calc(50vw - 40px)); aspect-ratio: 680 / 720; margin: auto 0; padding: 0; }
    .fit { inset: 0; width: 100%; height: 100%; aspect-ratio: auto; }
    .stage :global(.polaroid) { position: absolute; right: 68%; bottom: 3%; margin: 0; }
  }
</style>
```

- [ ] **Step 7: The torn edge under the hero in peach**

In `website/src/components/MonthBirds.astro`, replace

```astro
    <DeckleEdge color="var(--dark)" />
```

with

```astro
    <DeckleEdge color="var(--peach)" />
```

- [ ] **Step 8: Delete the old hero parts**

```bash
cd C:/w/birdy-flock && git rm website/src/components/hero/DailyBirdPlate.astro website/src/components/hero/BirdyBird.astro website/src/components/hero/hero-motion.ts
```

Expected: three `rm '…'` lines. (`public/brand/birdy-bird.png` stays: the Premium page uses it.)

- [ ] **Step 9: The empty build's hero**

In `website/scripts/check-empty-hub.mjs`, replace the block from `// The home page's Dagens fågel with zero species pages` through the end of its `for` loop (lines 97 to 112) with:

```js
// The home page's Dagens fågel with zero species pages (spec 2026-10-09-startsidan-flocken-lyfter): no page can hang in
// the polaroid, so the site shows its own great tit, without a link (the check above already fails on any species link),
// and never the line about the app.
for (const [path, locale] of [['sv', 'sv'], ['', 'en']]) {
  const html = page(path);
  const where = path || '/';
  const caption = copy[locale].daily.polaroid.replace('{name}', locale === 'sv' ? 'Talgoxe' : 'Great Tit');
  const shown = html.match(/<span class="pol-name"[^>]*>([^<]*)<\/span>/)?.[1];
  if (shown !== caption) fail(where, `polaroiden säger "${shown}", ska vara "${caption}" när ingen art har en sida`);
  // Markup only: the hero's inlined guard script (hero/same-as-app-guard.ts) names the attribute in a selector.
  const markup = html.replace(/<script\b[\s\S]*?<\/script>/gi, '');
  if (markup.includes('data-same-as-app')) fail(where, 'raden "samma fågel som i appen" visas utan appens fågel');
}
```

- [ ] **Step 10: Run the guards, the build checks and the tests**

Run:

```bash
cd C:/w/birdy-flock/website && npm run test:i18n && npm run test:no-dashes && npm run test:no-accuracy && npm run test:palette && npm run test:contrast && npm run build:fixtures && npm run test:seo && npm run test:empty-hub
```

Expected: `i18n parity OK (432 keys)`, `no-dashes OK`, `accuracy-guard OK`, `palette-guard OK (98 filer, inget mossgrönt)` (three hero files deleted, `Polaroid.astro` added), `contrast-guard OK (33 par)`, the build completes, `check-seo` reports no errors, `check-empty-hub OK`.

Then:

```bash
cd C:/w/birdy-flock/website && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts tests/faltbok.spec.ts tests/premium.spec.ts --workers=2
```

Expected: all pass, including the 14 geometry tests, the no-JavaScript test, the axe tests on `/` and `/sv/` at 390 and 1440 px (premium.spec.ts) and the reduced-motion carousel test (`karusellen och planschen står still`). If a geometry test fails, read the message (which bird, which word), adjust only the knobs named in the Context section, rebuild and rerun.

- [ ] **Step 11: Commit**

```bash
cd C:/w/birdy-flock && git add website/tests/home.spec.ts website/tests/faltbok.spec.ts website/src/content/copy.sv.json website/src/content/copy.en.json website/src/components/hero/Polaroid.astro website/src/components/Hero.astro website/src/components/MonthBirds.astro website/scripts/check-empty-hub.mjs
git commit -m "feat(webb): startsidans hjälte i Flock-looken, orden, den landade flocken och Dagens fågel som polaroid"
```

(The deletions from Step 8 are already staged.)

---

### Task 5: The motion (`flock-motion.ts`)

**Files:**
- Create: `website/src/components/hero/flock-motion.ts`
- Modify: `website/src/components/Hero.astro` (the `<header>` tag, the `<script>`, the `<style>`)
- Test: `website/tests/home.spec.ts` (a new describe before `test.describe('fåglarna i månaden', () => {`)

- [ ] **Step 1: Write the failing tests**

In `website/tests/home.spec.ts`, insert directly before the line `test.describe('fåglarna i månaden', () => {`:

```ts
test.describe('flocken lyfter', () => {
  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    test('flocken står landad direkt, med dagens fågel tänd', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/');
      const hero = page.locator('[data-hero]');
      await expect(hero).toHaveAttribute('data-flock', 'done');
      await expect(hero.locator('[data-polaroid]')).toBeVisible();
      const geo = await page.evaluate(() => {
        const c = (document.querySelector('[data-flock-canvas]') as HTMLElement).getBoundingClientRect();
        const f = (document.querySelector('[data-flock-fit]') as HTMLElement).getBoundingClientRect();
        return { left: f.left - c.left, top: f.top - c.top, width: f.width, height: f.height };
      });
      const lit = landing(FLOCK.birds[2], fitView(geo));
      const alpha = (x: number, y: number) =>
        page.locator('[data-flock-canvas]').evaluate((canvas: HTMLCanvasElement, [px, py]) => {
          const k = canvas.width / canvas.getBoundingClientRect().width;
          return canvas.getContext('2d')!.getImageData(Math.round(px * k), Math.round(py * k), 1, 1).data[3];
        }, [x, y] as const);
      expect(await alpha(lit.x, lit.y), 'dagens fågel är ritad').toBeGreaterThan(230);
      expect(await alpha(2, 2), 'resten av ytan är tom').toBe(0);
      expect(await hero.evaluate((h) => h.getAnimations({ subtree: true }).length), 'inget rör sig').toBe(0);
    });
  });

  test('flocken flyger in en gång, landar och står sedan still', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect(hero).toHaveAttribute('data-flock', 'flying');
    await expect(hero.locator('[data-polaroid]')).toBeHidden();
    await expect(hero).toHaveAttribute('data-flock', 'done', { timeout: 15_000 });
    await expect(hero.locator('[data-polaroid]')).toBeVisible();
    expect(await hero.evaluate((h) => h.getAnimations({ subtree: true }).length), 'inga animationer kvar').toBe(0);
    const frame = () => page.locator('[data-flock-canvas]').evaluate((c: HTMLCanvasElement) => c.toDataURL());
    const landed = await frame();
    await page.waitForTimeout(600);
    expect(await frame(), 'inget ritas om efter landningen').toBe(landed);
    const shift = await page.evaluate(() => new Promise<number>((resolve) => {
      let sum = 0;
      new PerformanceObserver((list) => {
        for (const e of list.getEntries() as (PerformanceEntry & { value: number; hadRecentInput: boolean })[]) if (!e.hadRecentInput) sum += e.value;
      }).observe({ type: 'layout-shift', buffered: true });
      setTimeout(() => resolve(sum), 50);
    }));
    expect(shift, 'inga layoutskift').toBeLessThan(0.01);
    expect(errors).toEqual([]);
  });

  test('flocken väntar tills hjälten syns', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    // Scrolled to the bottom before the page's scripts run, as when a link opens the page further down. (A #fragment
    // does not do: Chrome reports the top as visible for a frame before it scrolls.)
    await page.addInitScript(() => {
      document.addEventListener('readystatechange', () => {
        if (document.readyState !== 'interactive') return;
        document.documentElement.style.scrollBehavior = 'auto';
        window.scrollTo(0, document.documentElement.scrollHeight);
      });
    });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await page.waitForTimeout(1000);
    await expect(hero).toHaveAttribute('data-flock', 'waiting');
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await expect(hero).toHaveAttribute('data-flock', /^(flying|landed|done)$/);
  });

  for (const [width, height] of [[390, 844], [1440, 900]] as const) {
    test(`det största innehållet är orden, inte fotot (${width}×${height})`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      await expect(page.locator('[data-hero]')).toHaveAttribute('data-flock', 'done', { timeout: 15_000 });
      const lcp = await page.evaluate(() => new Promise<string>((resolve) => {
        new PerformanceObserver((list) => {
          const entries = list.getEntries() as (PerformanceEntry & { element?: Element | null })[];
          const el = entries[entries.length - 1]?.element;
          resolve(!el ? 'inget' : el.closest('[data-hero] .intro') ? 'orden' : el.closest('[data-polaroid]') ? 'fotot' : el.tagName);
        }).observe({ type: 'largest-contentful-paint', buffered: true });
      }));
      expect(lcp).toBe('orden');
    });
  }
});

```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts --workers=2 -g "flocken"`
Expected: FAIL: `data-flock` is missing on the hero (`toHaveAttribute` times out). The LCP tests may already pass.

- [ ] **Step 3: Write the motion script**

Create `website/src/components/hero/flock-motion.ts`:

```ts
// Flocken lyfter (spec docs/superpowers/specs/2026-10-09-startsidan-flocken-lyfter-design.md): once per page view, when
// the hero comes into sight, the 839 birds fly in from below left, under the words, and land as Birdy's bird; today's
// bird lands last and lights up, and Dagens fågel's photo lifts out of it, develops like a polaroid and is taped down.
// Then nothing moves: no loop and no timer is left (the ResizeObserver only redraws the still frame after a resize).
// The motion is the approved prototype's (docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html,
// version 3), on a canvas with one bitmap of the mark per colour, at most devicePixelRatio 2. Reduced motion draws the
// landed flock at once; without JavaScript the hero's <noscript> SVG shows the same frame. data-flock on the hero says
// where it is (waiting, flying, landed, done); the hero's CSS hides the polaroid until the flock has landed.
import { MARK } from './flock-data.mjs';
import { COLOURS, DISC_SCALE, LIT, LIT_SCALE, fitView, flightPlan } from './flock.mjs';

type Plan = ReturnType<typeof flightPlan>;
type Flyer = Plan['birds'][number];

/** The mark drawn at 12 % of its 1000-unit width (about 120 px): sharp at every bird size. */
const SPRITE_K = 0.12;
/** ms: the first frame already shows the river of birds arriving. */
const HEAD_START = 450;
/** ms: today's bird's ring pops once when it has landed. */
const RING_MS = 480;

const easeOut = (u: number) => 1 - Math.pow(1 - u, 3);
const easeOutBack = (k: number) => 1 + 2.70158 * Math.pow(k - 1, 3) + 1.70158 * Math.pow(k - 1, 2);

function run(hero: HTMLElement, canvas: HTMLCanvasElement, fitBox: HTMLElement, polaroid: HTMLElement, ctx: CanvasRenderingContext2D) {
  const setState = (state: 'flying' | 'landed' | 'done') => {
    hero.dataset.flock = state;
  };
  const litIndex = Number(hero.dataset.flockIndex);
  const mark = new Path2D(MARK.path);
  // One small bitmap of the mark per colour; every bird is a rotated, scaled copy.
  const sprite = (colour: string): HTMLCanvasElement => {
    const c = document.createElement('canvas');
    c.width = Math.ceil(MARK.w * SPRITE_K) + 2;
    c.height = Math.ceil(MARK.h * SPRITE_K) + 2;
    const g = c.getContext('2d');
    if (g) {
      g.scale(SPRITE_K, SPRITE_K);
      g.fillStyle = colour;
      g.fill(mark);
    }
    return c;
  };
  const sprites = COLOURS.map(sprite);
  const litSprite = sprite(LIT.bird);

  let dpr = 1;
  let t0 = -1;
  let raf = 0;
  // The canvas covers the hero; the flock fits the [data-flock-fit] box, the same box as the <noscript> SVG.
  const layout = (): Plan => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    const c = canvas.getBoundingClientRect();
    const f = fitBox.getBoundingClientRect();
    canvas.width = Math.round(c.width * dpr);
    canvas.height = Math.round(c.height * dpr);
    const fit = fitView({ left: f.left - c.left, top: f.top - c.top, width: f.width, height: f.height });
    return flightPlan({ fit, width: c.width, height: c.height, litIndex });
  };
  let plan = layout();

  const drawBird = (x: number, y: number, size: number, rot: number, img: HTMLCanvasElement, alpha: number) => {
    const k = (size / MARK.w / SPRITE_K) * dpr;
    const a = (rot * Math.PI) / 180;
    const cos = Math.cos(a) * k;
    const sin = Math.sin(a) * k;
    ctx.globalAlpha = alpha;
    ctx.setTransform(cos, sin, -sin, cos, x * dpr, y * dpr);
    ctx.drawImage(img, -MARK.cx * SPRITE_K, -MARK.cy * SPRITE_K);
  };

  const fly = (p: Flyer, t: number, scale: number, img: HTMLCanvasElement) => {
    const u = (t - p.delay) / p.dur;
    if (u <= 0) return;
    if (u >= 1) {
      drawBird(p.fx, p.fy, p.size * scale, p.rot, img, p.op);
      return;
    }
    const e = easeOut(u);
    const m = 1 - e;
    // A quadratic curve from the start, low under the words, to the landing place.
    let x = m * m * p.sx + 2 * m * e * p.cx + e * e * p.fx;
    let y = m * m * p.sy + 2 * m * e * p.cy + e * e * p.fy;
    const dx = 2 * m * (p.cx - p.sx) + 2 * e * (p.fx - p.cx);
    const dy = 2 * m * (p.cy - p.sy) + 2 * e * (p.fy - p.cy);
    const len = Math.hypot(dx, dy) || 1;
    // A shared wave across neighbours: the flock moves like one body, then calms as it lands.
    const wave = p.amp * Math.sin(2 * Math.PI * ((p.freq * t) / 1000 + p.phase)) * m * m;
    x += (-dy / len) * wave;
    y += (dx / len) * wave;
    const heading = (Math.atan2(dy, dx) * 180) / Math.PI;
    drawBird(x, y, p.size * scale * (0.55 + 0.45 * e), p.rot + m * heading * 0.35, img, Math.min(1, u * 6) * p.op);
  };

  const clear = () => {
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  };
  const drawFlock = () => {
    for (const p of plan.birds) drawBird(p.fx, p.fy, p.size, p.rot, sprites[p.colour], p.op);
  };
  // Today's bird on its cream disc with the copper ring; `ring` grows the disc from 0 to 1 when it lands.
  const drawLit = (ring: number) => {
    const lit = plan.lit;
    ctx.globalAlpha = 1;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.beginPath();
    ctx.arc(lit.fx, lit.fy, lit.size * DISC_SCALE * ring, 0, Math.PI * 2);
    ctx.fillStyle = LIT.disc;
    ctx.fill();
    ctx.lineWidth = Math.max(1.8, lit.size * 0.14);
    ctx.strokeStyle = LIT.ring;
    ctx.stroke();
    drawBird(lit.fx, lit.fy, lit.size * LIT_SCALE, lit.rot, litSprite, 1);
  };
  const drawStill = () => {
    clear();
    drawFlock();
    drawLit(1);
  };

  // Dagens fågel arrives after the flock: the photo lifts out of the lit bird and flies to its place, develops like a
  // polaroid, and the tape lands on it.
  const reveal = () => {
    const img = polaroid.querySelector('img');
    const caption = polaroid.querySelector('figcaption');
    const tape = polaroid.querySelector<HTMLElement>('.tape');
    const c = canvas.getBoundingClientRect();
    const r = polaroid.getBoundingClientRect();
    const dx = plan.lit.fx - (r.left - c.left + r.width / 2);
    const dy = plan.lit.fy - (r.top - c.top + r.height / 2);
    const runs: Animation[] = [
      polaroid.animate([
        { transform: `translate(${dx.toFixed(1)}px, ${dy.toFixed(1)}px) scale(.06) rotate(-16deg)`, opacity: 0 },
        { opacity: 1, offset: 0.18 },
        { transform: 'translate(0px, 0px) scale(1.035) rotate(3.4deg)', offset: 0.76 },
        { transform: 'translate(0px, 0px) scale(1) rotate(2deg)', opacity: 1 },
      ], { duration: 1100, delay: 140, easing: 'cubic-bezier(.22, .9, .24, 1)', fill: 'both' }),
    ];
    if (img) {
      runs.push(img.animate([
        { filter: 'saturate(0) brightness(1.55) contrast(.7)', opacity: 0.3 },
        { filter: 'saturate(0) brightness(1.55) contrast(.7)', opacity: 0.3, offset: 0.3 },
        { filter: 'saturate(1) brightness(1) contrast(1)', opacity: 1 },
      ], { duration: 2000, delay: 140, easing: 'ease-out', fill: 'both' }));
    }
    if (caption) runs.push(caption.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 500, delay: 1150, fill: 'both' }));
    // The tape is part of the card, which leans 2 degrees, so -8 here is -6 on the page.
    if (tape) {
      runs.push(tape.animate([
        { transform: 'translateY(-18px) rotate(-18deg) scale(1.25)', opacity: 0 },
        { transform: 'translateY(0px) rotate(-8deg) scale(1)', opacity: 0.94 },
      ], { duration: 360, delay: 1100, easing: 'cubic-bezier(.3, 1.5, .5, 1)', fill: 'both' }));
    }
    // Landed: the animations let go (the CSS rest state is the same frame), so nothing is left running.
    const settle = () => {
      for (const a of runs) a.cancel();
      setState('done');
    };
    Promise.all(runs.map((a) => a.finished)).then(settle, settle);
  };

  const frame = (now: number) => {
    if (t0 < 0) t0 = now - HEAD_START;
    const t = now - t0;
    if (t < plan.total) {
      clear();
      for (const p of plan.birds) fly(p, t, 1, sprites[p.colour]);
      fly(plan.lit, t, 1.25, litSprite);
      raf = requestAnimationFrame(frame);
      return;
    }
    // Landed: the flock is still; today's bird pops its ring once, and its photo comes out of it.
    const k = Math.min(1, (t - plan.total) / RING_MS);
    clear();
    drawFlock();
    drawLit(Math.max(0, easeOutBack(k)));
    if (hero.dataset.flock === 'flying') {
      setState('landed');
      reveal();
    }
    raf = k < 1 ? requestAnimationFrame(frame) : 0;
  };

  // A new size moves the fit box: lay the flock out again, and redraw the still frame once it has landed.
  if ('ResizeObserver' in window) {
    let pending = 0;
    new ResizeObserver(() => {
      if (pending) return;
      pending = requestAnimationFrame(() => {
        pending = 0;
        plan = layout();
        if (!raf && (hero.dataset.flock === 'landed' || hero.dataset.flock === 'done')) drawStill();
      });
    }).observe(hero);
  }

  if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
    drawStill();
    setState('done');
  } else if ('IntersectionObserver' in window) {
    // Once, when a fifth of the hero is in sight.
    const io = new IntersectionObserver((entries) => {
      if (!entries.some((e) => e.isIntersecting)) return;
      io.disconnect();
      setState('flying');
      raf = requestAnimationFrame(frame);
    }, { threshold: 0.2 });
    io.observe(hero);
  } else {
    setState('flying');
    raf = requestAnimationFrame(frame);
  }
}

const hero = document.querySelector<HTMLElement>('[data-hero]');
const canvas = hero?.querySelector<HTMLCanvasElement>('[data-flock-canvas]');
const fitBox = hero?.querySelector<HTMLElement>('[data-flock-fit]');
const polaroid = hero?.querySelector<HTMLElement>('[data-polaroid]');
const ctx = canvas?.getContext('2d');
if (hero && canvas && fitBox && polaroid && ctx) run(hero, canvas, fitBox, polaroid, ctx);
// Without a canvas to draw on, the polaroid is shown at once rather than kept waiting.
else if (hero) hero.dataset.flock = 'done';
```

- [ ] **Step 4: Wire it into the hero**

In `website/src/components/Hero.astro`:

Replace

```astro
<header class="hero" data-hero data-daily-bird={species?.qid ?? ''} data-app-bird={appQid ?? ''} data-date={date.iso} data-flock-index={litIndex}>
```

with

```astro
<header class="hero" data-hero data-daily-bird={species?.qid ?? ''} data-app-bird={appQid ?? ''} data-date={date.iso} data-flock-index={litIndex} data-flock="waiting">
```

replace

```astro
<script>
  import './hero/same-as-app-guard';
</script>
```

with

```astro
<script>
  import './hero/flock-motion';
  import './hero/same-as-app-guard';
</script>
```

and in the `<style>`, after the closing `}` of `@media (min-width: 1024px) { … }` (the last rule before `</style>`), add:

```css
  /* Until the flock has landed the polaroid waits out of sight (hero/flock-motion.ts lifts it out of the lit bird). Only
     with JavaScript and motion: reduced motion and no JavaScript show it at once. */
  @media (prefers-reduced-motion: no-preference) {
    :global(html.js) .hero:is([data-flock='waiting'], [data-flock='flying']) :global(.polaroid) { visibility: hidden; }
  }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts tests/faltbok.spec.ts tests/premium.spec.ts --workers=2`
Expected: all pass, including the five new `flocken lyfter` tests (the full flight takes about 7 s per test: the flock lands about 4.5 s after it starts, the polaroid has developed about 2.5 s later).

- [ ] **Step 6: Commit**

```bash
cd C:/w/birdy-flock && git add website/src/components/hero/flock-motion.ts website/src/components/Hero.astro website/tests/home.spec.ts
git commit -m "feat(webb): flocken lyfter, rörelsen en gång per sidvisning och den landade flocken vid minskad rörelse"
```

---

### Task 6: The nav on the peach hero

**Files:**
- Modify: `website/src/components/Nav.astro` (Props lines 9 to 18, the `<nav>` and `<Wordmark>` lines 44 to 46, the `<style>` lines 105 to 122)
- Modify: `website/src/components/HomePage.astro:24`
- Test: `website/tests/home.spec.ts` (a new describe before the top-level `test.describe('utan JavaScript', () => {`)

- [ ] **Step 1: Write the failing tests**

In `website/tests/home.spec.ts`, insert directly before the top-level line `test.describe('utan JavaScript', () => {` (at column 0, the describe whose first test is `startsidans meny är espressobrun och den döda menyknappen dold`; the carousel has an indented describe with the same title):

```ts
test.describe('menyn över persikopappret', () => {
  for (const [path, label] of [['/sv/', 'Arter'], ['/', 'Species']] as const) {
    test(`menyn på ${path} har mörk text över hjälten och ljus text när den blir espressobrun`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(path);
      const nav = page.locator('#site-nav');
      await expect(nav).not.toHaveClass(/is-solid/);
      await expect(nav.locator('.links a').first()).toHaveText(label);
      await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
      await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(48, 32, 25)');
      await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
      await expect(nav).toHaveClass(/is-solid/);
      await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(255, 248, 238)');
      await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(255, 248, 238)');
    });
  }

  test('menyknappen på mobilen är mörk och utan espressoskiva över hjälten', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const toggle = page.locator('#site-nav .menu-toggle');
    await expect(toggle).toHaveCSS('color', 'rgb(48, 32, 25)');
    await expect(toggle).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  });

  test('Premium-sidans meny behåller ljus text över sin mörka hjälte', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/premium/');
    await expect(page.locator('#site-nav')).not.toHaveClass(/is-solid/);
    await expect(page.locator('#site-nav .links a').first()).toHaveCSS('color', 'rgb(255, 248, 238)');
  });

  test.describe('kontrast', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    test('den första menylänken klarar 4.5:1 mot hjälten', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/');
      await page.addStyleTag({ content: '#site-nav .links a { visibility: hidden !important; }' });
      await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await expect(page.locator('#site-nav')).not.toHaveClass(/is-solid/);
      const ratio = await textContrastAgainstBackground(page, page.locator('#site-nav .links a').first());
      expect(ratio, `första menylänken mot hjälten: ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
    });
  });
});

```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts --workers=2 -g "persikopappret"`
Expected: FAIL: the links and the wordmark are `rgb(255, 248, 238)` over the hero, the menu button has the espresso disc, and the contrast test measures cream on peach (about 1.1:1). The Premium test passes.

- [ ] **Step 3: Give the nav a `surface`**

In `website/src/components/Nav.astro`:

Replace

```ts
  /** overlay = transparent over a dark first view until it scrolls past; solid = espresso bar in the flow. */
  variant?: 'overlay' | 'solid';
```

with

```ts
  /** overlay = transparent over the first view until it scrolls past; solid = espresso bar in the flow. */
  variant?: 'overlay' | 'solid';
  /** What the transparent overlay bar lies on: 'dark' (espresso heroes and photos: cream text) or 'peach' (the home
   * page's peach hero, spec 2026-10-09: ink text). The solid bar is espresso with cream text either way. */
  surface?: 'dark' | 'peach';
```

replace

```ts
const { locale, switchLangHref, variant = 'solid', scrim = false } = Astro.props;
```

with

```ts
const { locale, switchLangHref, variant = 'solid', scrim = false, surface = 'dark' } = Astro.props;
```

replace

```astro
<nav id="site-nav" class:list={['nav', `nav--${variant}`, { 'nav--scrim': scrim }]} aria-label={t.nav.label} data-variant={variant}>
  <div class="nav-inner">
    <a href={home} class="brand" aria-label="Birdy"><Wordmark size="32px" alt={t.alt.wordmark} color="var(--cream)" /></a>
```

with

```astro
<nav id="site-nav" class:list={['nav', `nav--${variant}`, { 'nav--scrim': scrim, 'nav--peach': surface === 'peach' }]} aria-label={t.nav.label} data-variant={variant}>
  <div class="nav-inner">
    <a href={home} class="brand" aria-label="Birdy"><Wordmark size="32px" alt={t.alt.wordmark} color="currentColor" /></a>
```

replace

```css
  .nav { z-index: 100; color: var(--cream); font-size: 13px; font-weight: 600; transition: background-color .3s var(--ease-paper), box-shadow .3s var(--ease-paper); }
```

with

```css
  .nav { z-index: 100; color: var(--cream); font-size: 13px; font-weight: 600; transition: background-color .3s var(--ease-paper), box-shadow .3s var(--ease-paper), color .3s var(--ease-paper); }
```

and directly after the line

```css
  :global(html.js) .nav--overlay.nav--scrim:not(.is-solid):not(.is-open) { background: linear-gradient(180deg, rgba(var(--dark-rgb), .85), rgba(var(--dark-rgb), .6)); }
```

add

```css
  /* The home page's peach hero (surface="peach", spec 2026-10-09): ink on the transparent bar (the wordmark follows,
     it is currentColor), rust focus rings, and below 1024 px no espresso disc behind the menu button and no shadow
     under the wordmark. The solid bar (scrolled past, menu open, no JavaScript) stays espresso with cream text. */
  :global(html.js) .nav--overlay.nav--peach:not(.is-solid):not(.is-open) { color: var(--ink); }
  :global(html.js) .nav--overlay.nav--peach:not(.is-solid):not(.is-open) :global(:focus-visible) { outline-color: var(--rust); }
  @media (max-width: 1023px) {
    :global(html.js) .nav--overlay.nav--peach:not(.is-solid):not(.is-open) .menu-toggle { background: none; }
    :global(html.js) .nav--overlay.nav--peach:not(.is-solid):not(.is-open) .brand { filter: none; }
  }
```

- [ ] **Step 4: The home page asks for it**

In `website/src/components/HomePage.astro`, replace

```astro
  <Nav locale={locale} variant="overlay" />
```

with

```astro
  <Nav locale={locale} variant="overlay" surface="peach" />
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts tests/premium.spec.ts tests/species.spec.ts --workers=2`
Expected: all pass, including the old `meny och sidfot` tests (transparent at the top, espresso after scrolling), the no-JavaScript menu test (espresso), the post's nav contrast test and the species pages' menu tests.

- [ ] **Step 6: Commit**

```bash
cd C:/w/birdy-flock && git add website/src/components/Nav.astro website/src/components/HomePage.astro website/tests/home.spec.ts
git commit -m "feat(webb): menyn har mörk text över startsidans persikopapper"
```

---

### Task 7: Contrast of the hero's words on the peach

**Files:**
- Test: `website/tests/home.spec.ts` (a new describe before `test.describe('fåglarna i månaden', () => {`)

- [ ] **Step 1: Write the tests**

In `website/tests/home.spec.ts`, insert directly before the line `test.describe('fåglarna i månaden', () => {`:

```ts
test.describe('kontrast i hjälten mot persikopappret', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  // Pixel contrast (like the #download guard): hide the hero's words, screenshot what lies behind them (peach, and any
  // bird of the landed flock), and check the kicker and the sub text (4.5:1) and the handwritten line (large, 3:1).
  for (const path of ['/sv/', '/'] as const) {
    for (const width of [390, 1024, 1440] as const) {
      test(`kicker, underrad och handskriven rad klarar kontrasten i ${width}px på ${path}`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const hero = page.locator('[data-hero]');
        await expect(hero).toHaveAttribute('data-flock', 'done');
        await page.addStyleTag({ content: '[data-hero] .intro * { visibility: hidden !important; }' });
        await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
        const kick = await textContrastAgainstBackground(page, hero.locator('.intro .kick'));
        expect(kick, `kicker: ${kick.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
        const sub = await textContrastAgainstBackground(page, hero.locator('.intro .sub'));
        expect(sub, `underrad: ${sub.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
        const em = await textContrastAgainstBackground(page, hero.locator('h1 em'));
        expect(em, `handskriven rad: ${em.toFixed(2)}:1`).toBeGreaterThanOrEqual(3);
      });
    }
  }
});

```

- [ ] **Step 2: Run them**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/home.spec.ts --workers=2 -g "kontrast i hjälten"`
Expected: 6 passed (rust and muted on the peach measure about 5:1, the words never lie over the flock). These guard the design: if one fails, fix the colour or the layout in `Hero.astro`, never the test.

- [ ] **Step 3: Commit**

```bash
cd C:/w/birdy-flock && git add website/tests/home.spec.ts
git commit -m "test(webb): kontrasten för orden i hjälten mot persikopappret"
```

---

### Task 8: The share images in the Flock look

**Files:**
- Modify: `website/tests/faltbok.spec.ts` (the `delningsbilder` describe, lines 212 to 221)
- Modify: `website/tools/generate-og.mjs` (header comment lines 2 to 4, imports, the `field` template lines 34 to 56, the two `og-field` jobs lines 77 to 78)
- Modify: `website/src/content/copy.sv.json`, `website/src/content/copy.en.json` (`alt.shareImage`, line 531)
- Modify: `website/src/layouts/Layout.astro:36-38`
- Regenerate: `website/public/og-field-en.jpg`, `website/public/og-field-sv.jpg`

- [ ] **Step 1: Point the test at version 4 (failing)**

In `website/tests/faltbok.spec.ts`, replace

```ts
      image: /\/og-field-sv\.jpg\?v=3$/,
```

with

```ts
      image: /\/og-field-sv\.jpg\?v=4$/,
```

and replace

```ts
      image: /\/og-field-en\.jpg\?v=3$/,
```

with

```ts
      image: /\/og-field-en\.jpg\?v=4$/,
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/faltbok.spec.ts --workers=2 -g "delningsbildens"`
Expected: FAIL: `og:image` ends in `?v=3`.

- [ ] **Step 3: Draw the field images from the flock**

In `website/tools/generate-og.mjs`:

Replace the header lines

```js
//   og-field-{en,sv}.jpg   the home pages and every page without its own: the espresso wall with a real, public domain
//                          robin photo hung as a plate beside the words. Nothing is laid over the photo (plan
//                          2026-10-08 house rules), and the photo is shown whole.
```

with

```js
//   og-field-{en,sv}.jpg   the home pages and every page without its own: the home page's first view in the Flock look
//                          (spec 2026-10-09): peach paper, the slogan, the landed flock as Birdy's bird (flockSvg in
//                          src/components/hero/flock.mjs, the frame the page shows) with the robin's own bird lit, and
//                          a real, public domain robin photo as a taped polaroid, shown whole with nothing on it.
```

replace

```js
import { fileURLToPath, pathToFileURL } from 'node:url';
```

with

```js
import { fileURLToPath, pathToFileURL } from 'node:url';
import { flockIndexFor, flockSvg } from '../src/components/hero/flock.mjs';
```

replace the whole `field` template, from `const field = (v) => \`<style>${fonts}` through its closing `</div>\`;` (the line before `const premium = (v) =>`), with:

```js
// The robin's own bird in the flock (flockIndexFor), lit like Dagens fågel on the page.
const robinFlock = flockSvg({ litIndex: flockIndexFor('Q25334') });

const field = (v) => `<style>${fonts}
#card { color: #302019; background: radial-gradient(130% 120% at 70% 40%, #FEEBD6 0%, #FDE5CB 45%, #F8D6B4 100%); }
.wm { color: #302019; }
.kick { top: 168px; color: #9A4526; }
h1 { top: 206px; width: 560px; font-size: 74px; }
h1 em { color: #9A4526; font-size: 84px; transform: rotate(-1.6deg); transform-origin: left; }
.url { color: #6E584B; }
.fit { position: absolute; right: 34px; top: 22px; width: 552px; height: 584px; }
.fit svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.pol { position: absolute; right: 67%; bottom: 4%; width: 230px; padding: 12px 10px 0; background: #FFFAF1; box-shadow: 0 14px 34px rgba(60, 34, 20, .22), 0 1px 3px rgba(60, 34, 20, .14); transform: rotate(2deg); }
.pol img { display: block; width: 100%; height: auto; }
.pol b { display: block; padding: 8px 2px 12px; font-family: 'Caveat'; font-weight: 700; font-size: 26px; line-height: 1; color: #302019; }
.tape { position: absolute; top: -18px; left: 50%; width: 84px; height: 22px; margin-left: -42px; transform: rotate(-8deg); background: linear-gradient(180deg, #F5C99B, #F0BB86); box-shadow: 0 1px 3px rgba(80, 50, 30, .2); opacity: .94; }
</style><div id="card">
<div class="wm">Birdy.</div>
<p class="kick">${v.kicker}</p>
<h1>${v.line1}<em>${v.line2}</em></h1>
<p class="url">birdy.community</p>
<div class="fit">${robinFlock}<figure class="pol"><img src="${robin}" alt=""><b>${v.label}</b><span class="tape"></span></figure></div>
</div>`;
```

and replace the two field jobs

```js
  { file: 'og-field-en.jpg', html: field({ locale: 'en', kicker: 'Bird guide and field journal', line1: 'Know the bird.', line2: 'Keep the moment.', label: 'European Robin', latin: 'Erithacus rubecula' }) },
  { file: 'og-field-sv.jpg', html: field({ locale: 'sv', kicker: 'Fågelguide och fältdagbok', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', label: 'Rödhake', latin: 'Erithacus rubecula' }) },
```

with

```js
  { file: 'og-field-en.jpg', html: field({ kicker: 'Camera, photo or song', line1: 'Know the bird.', line2: 'Keep the moment.', label: 'European Robin' }) },
  { file: 'og-field-sv.jpg', html: field({ kicker: 'Kamera, foto eller läte', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', label: 'Rödhake' }) },
```

- [ ] **Step 4: The alt text**

In `website/src/content/copy.sv.json`, replace

```json
    "shareImage": "Ett foto av en rödhake, inramat som en plansch på en mörk vägg, bredvid orden Känn igen fågeln. Bevara stunden."
```

with

```json
    "shareImage": "Ett foto av en rödhake som en tejpad polaroid på persikofärgat papper, bredvid en flock små fåglar som bildar Birdys fågel och orden Känn igen fågeln. Bevara stunden."
```

In `website/src/content/copy.en.json`, replace

```json
    "shareImage": "A photo of a European robin, framed as a plate on a dark wall, beside the words Know the bird. Keep the moment."
```

with

```json
    "shareImage": "A photo of a European robin as a taped polaroid on peach paper, beside a flock of small birds that form Birdy's bird and the words Know the bird. Keep the moment."
```

- [ ] **Step 5: Bump the version**

In `website/src/layouts/Layout.astro`, replace

```js
// ?v=3: the share images (tools/generate-og.mjs) became a real robin photo hung as a plate (2026-10-08, the AI robin
// retired); bump when they are regenerated so platforms refetch them.
const ogImageUrl = new URL(ogImage ?? `/og-field-${locale}.jpg?v=3`, Astro.site).toString();
```

with

```js
// ?v=4: the share images (tools/generate-og.mjs) are the home page's first view in the Flock look (2026-10-09); bump
// when they are regenerated so platforms refetch them.
const ogImageUrl = new URL(ogImage ?? `/og-field-${locale}.jpg?v=4`, Astro.site).toString();
```

- [ ] **Step 6: Regenerate and look at them**

Run: `cd C:/w/birdy-flock/website && PLAYWRIGHT_CHANNEL=chrome npm run assets:og && git status --short public`
Expected: the four file names are printed; `git status` shows `public/og-field-en.jpg` and `public/og-field-sv.jpg` modified (if the two `og-premium-*.jpg` also show as modified, restore them with `git checkout -- public/og-premium-en.jpg public/og-premium-sv.jpg`: they are not part of this change).

Open both images (Read tool). Check: peach paper; "Birdy." top left; the kicker, the headline and the handwritten line on the left, clear of the flock; Birdy's bird on the right with one lit bird (cream disc, rust bird); the robin polaroid at the bird's lower left with the tape above the photo and "Rödhake" / "European Robin" under it; the river of small birds at the bottom left; nothing cut off at the edges.

- [ ] **Step 7: Run the tests and the guards**

Run: `cd C:/w/birdy-flock/website && npm run test:i18n && npm run test:no-dashes && npm run test:palette && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test tests/faltbok.spec.ts tests/home.spec.ts --workers=2 -g "delningsbild|strukturerade data"`
Expected: guards OK (`i18n parity OK (432 keys)`), the share image tests pass with `?v=4` and the new alt text, the JSON-LD test still finds the app node.

- [ ] **Step 8: Commit**

```bash
cd C:/w/birdy-flock && git add website/tests/faltbok.spec.ts website/tools/generate-og.mjs website/src/content/copy.sv.json website/src/content/copy.en.json website/src/layouts/Layout.astro website/public/og-field-en.jpg website/public/og-field-sv.jpg
git commit -m "feat(webb): delningsbilderna i Flock-looken, flocken och rödhaken som polaroid (v=4)"
```

---

### Task 9: The whole gate and a look at the result

**Files:** none expected; fixes found here get their own commits with exact paths.

- [ ] **Step 1: Every guard, the builds and the unit tests**

Run:

```bash
cd C:/w/birdy-flock/website && npm run verify:fixtures && npm run test:no-accuracy && npm run test:empty-hub
```

Expected: everything green (`verify:fixtures` runs the fixture build, `check-seo`, the unit tests, i18n, no-dashes, palette, contrast and the preview build), `accuracy-guard OK`, `check-empty-hub OK`.

- [ ] **Step 2: The whole Playwright suite**

Run: `cd C:/w/birdy-flock/website && npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4741 npx playwright test --workers=2`
Expected: `N passed`, no failures (N = Task 0's count, minus the removed old hero tests, plus the new ones).

- [ ] **Step 3: `astro check`**

Run: `cd C:/w/birdy-flock/website && npx astro check 2>&1 | tail -3`
Expected: no more errors than Task 0's baseline. Fix any new error in the files this plan touched.

- [ ] **Step 4: Look at it**

Save as `C:/w/birdy-flock-qa/shots.mjs` (outside the repo):

```js
// Screenshots of the hero: the final frame (reduced motion) at four widths in both languages, and one flight.
// Usage: node shots.mjs [base URL, default http://localhost:4743]
import { createRequire } from 'node:module';

const require = createRequire('C:/w/birdy-flock/website/package.json');
const { chromium } = require('playwright');
const base = process.argv[2] ?? 'http://localhost:4743';
const out = 'C:/w/birdy-flock-qa';
const browser = await chromium.launch({ channel: 'chrome' });
for (const path of ['/sv/', '/']) {
  for (const [width, height] of [[390, 844], [1024, 768], [1440, 900], [1920, 1080]]) {
    const page = await browser.newPage({ viewport: { width, height }, reducedMotion: 'reduce' });
    await page.goto(base + path);
    await page.evaluate(() => document.fonts.ready);
    await page.waitForTimeout(300);
    await page.screenshot({ path: `${out}/final-${path === '/' ? 'en' : 'sv'}-${width}.png` });
    await page.close();
  }
}
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.goto(`${base}/sv/`);
await page.waitForTimeout(1500);
await page.screenshot({ path: `${out}/flight-sv-1440.png` });
await page.waitForFunction(() => document.querySelector('[data-hero]')?.getAttribute('data-flock') === 'done', null, { timeout: 15000 });
await page.screenshot({ path: `${out}/landed-sv-1440.png` });
await browser.close();
```

Start the preview of the fixture build in the background (`cd C:/w/birdy-flock/website && npm run preview -- --port 4743`), then run `node C:/w/birdy-flock-qa/shots.mjs` and open every image. Compare with the prototype (open `docs/superpowers/specs/assets/2026-10-08-flocken-webben/flocken-lyfter.html` from the worktree in Chrome with `img/ringduva.webp` beside it, a copy of `website/src/assets/species/Q26026/hero.webp`). Check: the composition matches (words left, the bird right, the polaroid at the bird's lower left, the river under the words); no handwritten note and no arrow anywhere near the flock; the menu's text is dark over the peach; the torn edge under the hero is peach with no seam; mid-flight the birds stream in under the words. Stop the preview. Anything off: fix, rerun Steps 1 and 2, and commit the fix with exact paths and a Swedish message that starts with `fix(webb):` and names what was fixed.

---

### Task 10: Lighthouse on `/sv/` (mobile)

**Files:** none expected.

- [ ] **Step 1: Three runs on the new hero**

With the fixture build from Task 9 Step 2 in `dist/` and the preview running in the background on port 4743 (`cd C:/w/birdy-flock/website && npm run preview -- --port 4743`):

```bash
for i in 1 2 3; do npx -y lighthouse@12 http://localhost:4743/sv/ --only-categories=performance,accessibility,best-practices,seo --chrome-flags="--headless=new" --output=json --output-path=C:/w/birdy-flock-qa/after-sv-$i.json --quiet; done
node C:/w/birdy-flock-qa/lh-summary.mjs C:/w/birdy-flock-qa/after-sv-1.json C:/w/birdy-flock-qa/after-sv-2.json C:/w/birdy-flock-qa/after-sv-3.json
```

Expected: the median at least `perf 93, a11y 100, bp 96, seo 100` and not below Task 0's median performance by more than 2 (run-to-run noise), `cls` 0, and an `lcpElement` from the hero's words (the `<h1>`, the `<em>` or the `class="sub"` paragraph), never the polaroid's `<img>`. Stop the preview afterwards.

- [ ] **Step 2: If it falls short**

Read the three reports' `largest-contentful-paint-element`, `speed-index` and `total-blocking-time`:
- LCP element is the polaroid photo: make the card smaller in `Polaroid.astro` (`clamp(172px, 48%, 220px)` and `clamp(190px, 16vw, 236px)`), rerun Task 4's geometry tests and this task.
- Total blocking time rose: check that every canvas frame stays a short task (Chrome DevTools Performance, CPU 4x); the frame draws 839 sprites and must not exceed about 20 ms.
- Only Speed Index fell (the flock and the polaroid keep changing the first view for about 7 s): do not shorten or change the approved motion. Stop and report both medians and the Speed Index numbers to the controller; it is Albin's trade-off.

Commit any fix with exact paths and a Swedish message that starts with `perf(webb):` and names what changed.

---

### Task 11: A Vercel preview for Albin

**Files:** none.

- [ ] **Step 1: Push the branch**

```bash
cd C:/w/birdy-flock && git status --short && git log --oneline origin/main..HEAD && git push -u origin website/flocken-lyfter
```

Expected: a clean tree, this plan's commits listed, and the push creates the branch on GitHub. Vercel builds a preview for every pushed branch of this project.

- [ ] **Step 2: Fetch the preview address**

Vercel reports the preview as a GitHub deployment for the pushed commit, with a status that carries the address; it usually takes 1 to 3 minutes. Run this until it prints `success` (wait between tries with your harness's wait mechanism, for example Monitor with an until-loop, not a foreground `sleep`):

```bash
cd C:/w/birdy-flock && ID=$(gh api "repos/anonadrek/birdy/deployments?sha=$(git rev-parse HEAD)" --jq '.[0].id') && gh api "repos/anonadrek/birdy/deployments/$ID/statuses" --jq '.[0] | "\(.state) \(.environment_url)"'
```

Expected at the end: `success https://birdy-…-albtab.vercel.app` (Vercel names the deployment). Before Vercel has registered the deployment the first call prints nothing and the second fails; that only means "not yet". If the state is `failure`, open the build log in the Vercel dashboard (or ask the controller to), fix, commit, push and repeat.

- [ ] **Step 3: Hand over and stop**

The preview answers `302` to curl (Vercel Authentication): Albin opens it logged in to Vercel, on his phone too. Report to the controller: the preview address (`environment_url` plus `/sv/`), the branch and its last commit, the test counts, the Lighthouse medians before and after, and anything left open. **Stop here.** Merging `website/flocken-lyfter` into `main` (which takes the hero live) happens only after Albin has looked at the preview, and the controller does it, together with the status line in `CLAUDE.md` on `main`.
