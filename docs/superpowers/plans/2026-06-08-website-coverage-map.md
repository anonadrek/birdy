# Birdy Coverage Map (website) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an interactive "Where Birdy works" section to birdy.community that mirrors the app's ink-on-paper map, fills supported European countries in copper under the labels, and leaves the rest as bare map.

**Architecture:** A new Astro section component (`Coverage.astro`) lazy-initialises a MapLibre GL map using a MapTiler **vector** style (`dataviz-light`) recoloured at runtime to Field-Journal ink-on-paper. A curated, locally-bundled Europe GeoJSON is drawn as a copper fill+line layer inserted **below the first label layer** so place names stay on top. A canvas-built wax-seal icon (cream disc · copper ring · navy Birdy bird · copper point) is placed as a **symbol layer** at a few decorative European coordinates. The map is loaded only when scrolled into view; a static poster image is the no-JS / pre-load fallback.

**Tech Stack:** Astro 5, MapLibre GL JS, MapTiler vector tiles, Playwright (smoke), plain JS canvas for the icon.

**Spec:** `docs/superpowers/specs/2026-06-08-website-coverage-map-design.md`

**Working dir for all commands:** `website/` (the Astro project). Paths below are relative to `website/` unless they start with `../`.

**Key environment note:** the map reads `import.meta.env.PUBLIC_MAPTILER_KEY`. Create a `website/.env` for local dev (already git-ignored via `.env`), e.g. `PUBLIC_MAPTILER_KEY=yJ7wwJtvTes1n5wNVovA`. The script MUST no-op gracefully when the key is absent (so CI without the key stays green) — see Task 6.

---

### Task 1: Add the MapLibre dependency

**Files:**
- Modify: `website/package.json`

- [ ] **Step 1: Install maplibre-gl**

Run (from `website/`):
```bash
npm install maplibre-gl@^4.7.1
```
Expected: `package.json` gains `"maplibre-gl": "^4.7.1"` under `dependencies`; `package-lock.json` updates.

- [ ] **Step 2: Verify the build still passes**

Run:
```bash
npm run build
```
Expected: `astro build` completes with no errors (exit 0).

- [ ] **Step 3: Commit**

```bash
git add package.json package-lock.json
git commit -m "build(website): add maplibre-gl dependency"
```

---

### Task 2: Generate the curated coverage GeoJSON

A committed, build-time script fetches the public Europe GeoJSON, drops countries outside Birdy's core-Europe range (and ones that produce ugly bbox clips), rounds coordinates to shrink the file, and writes it into `public/`. The script validates its own output and exits non-zero on failure.

**Files:**
- Create: `website/scripts/build-coverage-geojson.mjs`
- Create (generated): `website/public/coverage/coverage-europe.geojson`

- [ ] **Step 1: Write the generation script**

Create `website/scripts/build-coverage-geojson.mjs`:
```js
#!/usr/bin/env node
// Builds public/coverage/coverage-europe.geojson from a public Europe dataset:
// keep Birdy's core-Europe countries, drop bbox-clipped/non-core ones, round
// coordinates to 2 decimals (country-level wash doesn't need more), dedupe.
import { writeFileSync, mkdirSync } from 'node:fs';

const SRC = 'https://cdn.jsdelivr.net/gh/leakyMirror/map-of-europe@master/GeoJSON/europe.geojson';
const EXCLUDE = new Set(['Russia', 'Turkey', 'Israel', 'Armenia', 'Azerbaijan', 'Georgia']);
const MUST_INCLUDE = ['Sweden', 'France', 'Germany', 'Spain', 'Poland', 'Italy', 'United Kingdom'];

const round = (n) => Math.round(n * 100) / 100;
function roundRing(ring) {
  const out = [];
  let prev = null;
  for (const [x, y] of ring) {
    const p = [round(x), round(y)];
    if (!prev || p[0] !== prev[0] || p[1] !== prev[1]) { out.push(p); prev = p; }
  }
  return out;
}
function roundGeom(g) {
  if (g.type === 'Polygon') g.coordinates = g.coordinates.map(roundRing);
  else if (g.type === 'MultiPolygon') g.coordinates = g.coordinates.map((poly) => poly.map(roundRing));
  return g;
}

const res = await fetch(SRC);
if (!res.ok) throw new Error(`source fetch failed: ${res.status}`);
const gj = await res.json();

gj.features = gj.features
  .filter((f) => f.geometry && !EXCLUDE.has(f.properties?.NAME))
  .map((f) => ({ type: 'Feature', properties: { name: f.properties.NAME }, geometry: roundGeom(f.geometry) }));

const names = new Set(gj.features.map((f) => f.properties.name));
for (const must of MUST_INCLUDE) if (!names.has(must)) throw new Error(`expected country missing: ${must}`);
for (const no of EXCLUDE) if (names.has(no)) throw new Error(`country should be excluded: ${no}`);

mkdirSync(new URL('../public/coverage/', import.meta.url), { recursive: true });
const out = new URL('../public/coverage/coverage-europe.geojson', import.meta.url);
writeFileSync(out, JSON.stringify({ type: 'FeatureCollection', features: gj.features }));
console.log(`wrote ${gj.features.length} features -> public/coverage/coverage-europe.geojson`);
```

- [ ] **Step 2: Run the generator**

Run (from `website/`):
```bash
node scripts/build-coverage-geojson.mjs
```
Expected: prints `wrote NN features ...` (NN ≈ 45) and creates `public/coverage/coverage-europe.geojson`. No throw.

- [ ] **Step 3: Sanity-check the output size + content**

Run:
```bash
node -e "const g=require('./public/coverage/coverage-europe.geojson');console.log(g.features.length,'features');console.log('has Russia?',g.features.some(f=>f.properties.name==='Russia'));console.log('has Sweden?',g.features.some(f=>f.properties.name==='Sweden'))"
```
Expected: `~45 features`, `has Russia? false`, `has Sweden? true`.

- [ ] **Step 4: Commit**

```bash
git add scripts/build-coverage-geojson.mjs public/coverage/coverage-europe.geojson
git commit -m "feat(website): generate curated Europe coverage GeoJSON"
```

---

### Task 3: Add the transparent Birdy-bird asset for the pin

The wax-seal needs the transparent bird silhouette (the app tints it navy). Reuse the app's `hero_bird.png`.

**Files:**
- Create: `website/public/coverage/seal-bird.png` (copy of the app asset)

- [ ] **Step 1: Copy the asset**

Run (from `website/`):
```bash
cp ../composeApp/src/commonMain/composeResources/files/branding/hero_bird.png public/coverage/seal-bird.png
```
Expected: `public/coverage/seal-bird.png` exists (~16 KB, transparent copper bird).

- [ ] **Step 2: Commit**

```bash
git add public/coverage/seal-bird.png
git commit -m "feat(website): add transparent Birdy bird for the coverage pin"
```

---

### Task 4: Add the section copy (EN + SV)

**Files:**
- Modify: `website/src/content/copy.en.json`
- Modify: `website/src/content/copy.sv.json`

- [ ] **Step 1: Add the `coverage` block to `copy.en.json`**

Add this as a new top-level key (e.g. right after the `"inside"` block) in `website/src/content/copy.en.json`:
```json
  "coverage": {
    "eyebrow": "Coverage",
    "headline": "Where Birdy *works*",
    "sub": "Europe today — the rest of the world is on its way.",
    "caption": "839 species across Europe. Drag the map to explore.",
    "fallbackAlt": "Map of Europe filled in copper, marking where Birdy works today",
    "attribution": "© MapTiler © OpenStreetMap contributors"
  },
```

- [ ] **Step 2: Add the matching `coverage` block to `copy.sv.json`**

Add the same key shape (identical keys) to `website/src/content/copy.sv.json`:
```json
  "coverage": {
    "eyebrow": "Täckning",
    "headline": "Där Birdy *funkar*",
    "sub": "Europa idag — resten av världen är på väg.",
    "caption": "839 arter i Europa. Dra i kartan för att utforska.",
    "fallbackAlt": "Karta över Europa ifylld i koppar, markerar var Birdy funkar idag",
    "attribution": "© MapTiler © OpenStreetMap contributors"
  },
```

- [ ] **Step 3: Verify i18n parity**

Run:
```bash
npm run test:i18n
```
Expected: `i18n parity OK (NN keys)` — exit 0. (If it lists missing keys, the two `coverage` blocks differ in shape; align them.)

- [ ] **Step 4: Commit**

```bash
git add src/content/copy.en.json src/content/copy.sv.json
git commit -m "feat(website): add coverage section copy (EN+SV)"
```

---

### Task 5: Static section shell + wire into both pages (no map yet)

Render the section with eyebrow/headline/sub/caption/attribution and a map container that, for now, shows only the static fallback placeholder. Add a Playwright test first (TDD), watch it fail, then implement.

**Files:**
- Create: `website/tests/coverage.spec.ts`
- Create: `website/src/components/Coverage.astro`
- Modify: `website/src/pages/index.astro`
- Modify: `website/src/pages/sv/index.astro`

- [ ] **Step 1: Write the failing test**

Create `website/tests/coverage.spec.ts`:
```ts
import { test, expect } from '@playwright/test';

for (const { path, headline } of [
  { path: '/', headline: 'works' },
  { path: '/sv/', headline: 'funkar' },
]) {
  test(`coverage section renders on ${path}`, async ({ page }) => {
    const consoleErrors: string[] = [];
    page.on('pageerror', (e) => consoleErrors.push(e.message));
    page.on('console', (msg) => { if (msg.type() === 'error') consoleErrors.push(msg.text()); });

    await page.goto(path);
    const section = page.locator('section#coverage');
    await expect(section).toBeAttached();
    await expect(section.locator('h2')).toContainText(headline);
    await expect(section.locator('[data-coverage-map]')).toBeAttached();

    // map is lazy; scroll it into view so the script (if any) runs
    await section.scrollIntoViewIfNeeded();
    await page.waitForTimeout(1500);
    expect(consoleErrors, `Console errors: ${consoleErrors.join('\n')}`).toEqual([]);
  });
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:
```bash
npm run build && npm run test:smoke -- coverage.spec.ts
```
Expected: FAIL — `section#coverage` not found (component not added yet).

- [ ] **Step 3: Create the section component**

Create `website/src/components/Coverage.astro`:
```astro
---
import EyebrowLabel from './ui/EyebrowLabel.astro';
import JournalHeadline from './ui/JournalHeadline.astro';
import OrnamentRule from './ui/OrnamentRule.astro';
import { type Locale, getCopy } from '../lib/i18n';

interface Props {
  locale: Locale;
}
const { locale } = Astro.props;
const t = getCopy(locale);
---

<section id="coverage" class="coverage">
  <div class="head" data-reveal>
    <EyebrowLabel text={t.coverage.eyebrow} />
    <JournalHeadline text={t.coverage.headline} level="h2" />
    <p class="sub">{t.coverage.sub}</p>
    <OrnamentRule />
  </div>

  <figure class="map-frame" data-reveal>
    <div class="map" data-coverage-map>
      <img
        class="fallback"
        src="/coverage/coverage-fallback.webp"
        alt={t.coverage.fallbackAlt}
        width="1600"
        height="900"
        loading="lazy"
        decoding="async"
      />
      <span class="attrib">{t.coverage.attribution}</span>
    </div>
    <figcaption class="caption">{t.coverage.caption}</figcaption>
  </figure>
</section>

<style>
  .coverage {
    padding: var(--section-padding-y) 1.5rem;
    max-width: var(--container-max);
    margin: 0 auto;
  }
  .head { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 0.75rem; }
  .sub {
    font-family: var(--font-script);
    font-size: 1.25rem;
    color: var(--color-marginalia-ink);
    margin: 0;
  }
  .map-frame { margin: 2rem auto 0; max-width: 56rem; }
  .map {
    position: relative;
    aspect-ratio: 16 / 9;
    border-radius: 16px;
    overflow: hidden;
    border: 2px solid var(--color-paper-edge, #d8cdb4);
    box-shadow: 0 10px 34px rgba(42, 53, 32, 0.22);
    background: #efe7d6;
    cursor: grab;
  }
  .map:active { cursor: grabbing; }
  .fallback { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; display: block; }
  .attrib {
    position: absolute; left: 9px; bottom: 7px; z-index: 3;
    font-family: var(--font-sans); font-size: 0.625rem;
    color: var(--color-marginalia-ink); opacity: 0.85;
  }
  .caption {
    text-align: center; margin-top: 0.75rem;
    font-family: var(--font-script); font-size: 1.125rem;
    color: var(--color-marginalia-ink);
  }
  /* MapLibre injects a canvas as a child of .map; let it cover the fallback */
  .map :global(.maplibregl-canvas) { position: absolute; inset: 0; }
</style>
```

- [ ] **Step 4: Wire the section into the EN homepage**

In `website/src/pages/index.astro`, add the import alongside the others and place `<Coverage>` after `<Inside>`:
```astro
import Coverage from '../components/Coverage.astro';
```
and in the `<main>` flow:
```astro
    <Inside locale={locale} />
    <Coverage locale={locale} />
    <Premium locale={locale} />
```

- [ ] **Step 5: Wire the section into the SV homepage**

Apply the identical import + placement in `website/src/pages/sv/index.astro` (same position, `locale` is already `'sv'` there).

- [ ] **Step 6: Add a temporary fallback placeholder image**

The component references `/coverage/coverage-fallback.webp`, which Task 9 generates. For now, copy the bird asset as a stand-in so the build/test don't 404:
```bash
cp public/coverage/seal-bird.png public/coverage/coverage-fallback.webp
```
(Task 9 replaces this with the real poster.)

- [ ] **Step 7: Run the test to verify it passes**

Run:
```bash
npm run build && npm run test:smoke -- coverage.spec.ts
```
Expected: PASS on both `/` and `/sv/`.

- [ ] **Step 8: Commit**

```bash
git add src/components/Coverage.astro src/pages/index.astro src/pages/sv/index.astro tests/coverage.spec.ts public/coverage/coverage-fallback.webp
git commit -m "feat(website): coverage section shell + page wiring + test"
```

---

### Task 6: Lazy MapLibre init with ink-on-paper recolour

Add the client script: when the section scrolls into view AND a MapTiler key is present, dynamically import MapLibre, build the map on the `dataviz-light` vector style, and recolour every layer to ink-on-paper. Guard on the key so CI without a key stays silent.

**Files:**
- Modify: `website/src/components/Coverage.astro` (add a `<script>` block + import the CSS)

- [ ] **Step 1: Add the MapLibre CSS import + client script**

At the very end of `website/src/components/Coverage.astro` (after the `</style>`), add:
```astro
<script>
  import 'maplibre-gl/dist/maplibre-gl.css';

  const KEY = import.meta.env.PUBLIC_MAPTILER_KEY as string | undefined;
  const PAPER = '#EFE7D6', INK = '#2E2417', WATER = '#DACFB2', COPPER = '#A8552D', NAVY = '#1F3A5F';

  const container = document.querySelector<HTMLElement>('[data-coverage-map]');
  if (container && KEY) {
    const io = new IntersectionObserver((entries, obs) => {
      for (const e of entries) {
        if (e.isIntersecting) { obs.disconnect(); initMap(container, KEY); }
      }
    }, { rootMargin: '200px' });
    io.observe(container);
  }

  async function initMap(el: HTMLElement, key: string) {
    const maplibregl = (await import('maplibre-gl')).default;
    const map = new maplibregl.Map({
      container: el,
      attributionControl: false,
      center: [12, 40], zoom: 2.1, minZoom: 1.1, maxZoom: 8,
      dragRotate: false, pitchWithRotate: false, touchPitch: false,
      cooperativeGestures: true,
      style: `https://api.maptiler.com/maps/dataviz-light/style.json?key=${key}`,
    });
    map.touchZoomRotate.disableRotation();

    // the .map box gets its height from aspect-ratio after layout — keep the
    // canvas in sync or markers/projection drift.
    const fit = () => { try { map.resize(); } catch {} };
    requestAnimationFrame(fit);
    new ResizeObserver(fit).observe(el);

    map.on('load', () => {
      fit();
      recolour(map);
    });

    (window as any).__coverageMap = map; // handy for manual debugging
  }

  function recolour(map: any) {
    for (const l of map.getStyle().layers) {
      try {
        if (l.type === 'background') map.setPaintProperty(l.id, 'background-color', PAPER);
        else if (l.type === 'fill') {
          if (/water|sea|ocean|lake|river|bath/i.test(l.id)) map.setPaintProperty(l.id, 'fill-color', WATER);
          else { map.setPaintProperty(l.id, 'fill-color', PAPER); map.setPaintProperty(l.id, 'fill-opacity', 1); }
        } else if (l.type === 'line') map.setPaintProperty(l.id, 'line-color', INK);
        else if (l.type === 'symbol') {
          map.setPaintProperty(l.id, 'text-color', INK);
          map.setPaintProperty(l.id, 'text-halo-color', PAPER);
          map.setPaintProperty(l.id, 'text-halo-width', 1.4);
        }
      } catch {}
    }
  }
</script>
```

- [ ] **Step 2: Build + verify no console errors (CI-safe path)**

Run:
```bash
npm run build && npm run test:smoke -- coverage.spec.ts
```
Expected: PASS. (With no `.env` key, `initMap` never runs → no errors. With a key, the map loads but logs no errors.)

- [ ] **Step 3: Manual visual check**

Create `website/.env` with `PUBLIC_MAPTILER_KEY=yJ7wwJtvTes1n5wNVovA`, then:
```bash
npm run dev
```
Open `http://localhost:4321/`, scroll to the coverage section. Expected: a sepia ink-on-paper map of Europe/world appears, place labels in dark ink, water slightly darker than land. No copper yet (Task 7).

- [ ] **Step 4: Commit**

```bash
git add src/components/Coverage.astro
git commit -m "feat(website): lazy MapLibre init + ink-on-paper recolour"
```

---

### Task 7: Copper coverage fill under the labels

Load the curated GeoJSON and add a copper fill + outline **below the first symbol (label) layer** so country names stay on top.

**Files:**
- Modify: `website/src/components/Coverage.astro` (extend the `map.on('load')` handler)

- [ ] **Step 1: Find the first label layer + add the copper layers**

In the `map.on('load', ...)` callback in `Coverage.astro`, after `recolour(map);` add:
```js
      let firstSymbol;
      for (const l of map.getStyle().layers) { if (l.type === 'symbol') { firstSymbol = l.id; break; } }

      fetch('/coverage/coverage-europe.geojson')
        .then((r) => r.json())
        .then((gj) => {
          map.addSource('coverage', { type: 'geojson', data: gj });
          map.addLayer({ id: 'coverage-fill', type: 'fill', source: 'coverage',
            paint: { 'fill-color': COPPER, 'fill-opacity': 0.42 } }, firstSymbol);
          map.addLayer({ id: 'coverage-line', type: 'line', source: 'coverage',
            paint: { 'line-color': '#8f4422', 'line-width': 1, 'line-opacity': 0.9 } }, firstSymbol);
        })
        .catch(() => {});
```

- [ ] **Step 2: Build + smoke (CI-safe)**

Run:
```bash
npm run build && npm run test:smoke -- coverage.spec.ts
```
Expected: PASS, no console errors.

- [ ] **Step 3: Manual visual check**

`npm run dev` → coverage section. Expected: European countries are washed in muted copper, the wash sits UNDER the place labels (country names readable on top), and the rest of the world is bare ink-on-paper. No copper block over Russia.

- [ ] **Step 4: Commit**

```bash
git add src/components/Coverage.astro
git commit -m "feat(website): copper coverage fill under the map labels"
```

---

### Task 8: Wax-seal pins as a symbol layer

Build the wax-seal (cream disc · copper ring · navy bird · copper point) on an offscreen canvas from `seal-bird.png`, register it as a map image, and place it at a few decorative European coordinates via a symbol layer (`icon-anchor: 'bottom'` — projection-accurate at every zoom).

**Files:**
- Modify: `website/src/components/Coverage.astro` (extend the `map.on('load')` handler)

- [ ] **Step 1: Add the seal builder + symbol layer**

In `Coverage.astro`, inside the `map.on('load', ...)` callback, after the coverage-fill `.then(...)` chain, add:
```js
      const bird = new Image();
      bird.onload = () => {
        const data = buildSeal(bird);
        if (data && !map.hasImage('seal')) map.addImage('seal', data, { pixelRatio: 2 });
        map.addSource('finds', { type: 'geojson', data: {
          type: 'FeatureCollection',
          features: [[18.07, 59.33], [13.40, 52.52], [2.35, 48.85], [12.49, 41.90], [-3.70, 40.42]]
            .map((c) => ({ type: 'Feature', properties: {}, geometry: { type: 'Point', coordinates: c } })),
        } });
        map.addLayer({ id: 'finds', type: 'symbol', source: 'finds',
          layout: { 'icon-image': 'seal', 'icon-anchor': 'bottom', 'icon-allow-overlap': true, 'icon-size': 0.55 } });
      };
      bird.src = '/coverage/seal-bird.png';
```

Then add this module-scope helper (place it next to `recolour`, outside any handler), reusing the colour constants already declared at the top of the script:
```js
  function buildSeal(birdImg: HTMLImageElement) {
    const S = 2, R = 40, ring = 6, pt = 18, pad = 6;       // logical px
    const w = 2 * (R + pad), h = 2 * (R + pad) + pt;
    const cvs = document.createElement('canvas');
    cvs.width = w * S; cvs.height = h * S;
    const x = cvs.getContext('2d');
    if (!x) return null;
    x.scale(S, S);
    const cx = w / 2, cy = R + pad, r = R;
    x.fillStyle = COPPER;                                   // downward point
    x.beginPath(); x.moveTo(cx - 7, cy + r - 2); x.lineTo(cx + 7, cy + r - 2); x.lineTo(cx, cy + r + pt); x.closePath(); x.fill();
    const g = x.createRadialGradient(cx - r * 0.25, cy - r * 0.3, r * 0.2, cx, cy, r * 1.25); // cream disc
    g.addColorStop(0, '#F4EDDC'); g.addColorStop(1, '#E5DBC4');
    x.fillStyle = g; x.beginPath(); x.arc(cx, cy, r, 0, 7); x.fill();
    x.lineWidth = ring; x.strokeStyle = COPPER; x.beginPath(); x.arc(cx, cy, r - ring / 2, 0, 7); x.stroke(); // copper ring
    const t = document.createElement('canvas'); t.width = birdImg.width; t.height = birdImg.height; // navy bird
    const tx = t.getContext('2d');
    if (tx) {
      tx.drawImage(birdImg, 0, 0);
      tx.globalCompositeOperation = 'source-in';
      tx.fillStyle = NAVY; tx.fillRect(0, 0, t.width, t.height);
      const bw = r * 1.25, bh = bw * (birdImg.height / birdImg.width);
      x.drawImage(t, cx - bw / 2, cy - bh / 2, bw, bh);
    }
    return x.getImageData(0, 0, cvs.width, cvs.height);
  }
```

- [ ] **Step 2: Build + smoke (CI-safe)**

Run:
```bash
npm run build && npm run test:smoke -- coverage.spec.ts
```
Expected: PASS, no console errors.

- [ ] **Step 3: Manual visual check (incl. zoom-out)**

`npm run dev` → coverage section. Expected: wax-seal pins (cream disc, copper ring, navy bird, copper point) sit on Stockholm / Berlin / Paris / Rome / Madrid — all in Europe, on the copper. Scroll-zoom all the way out: the pins stay in Europe (NONE drift into Africa). Drag the map around freely.

- [ ] **Step 4: Commit**

```bash
git add src/components/Coverage.astro
git commit -m "feat(website): wax-seal find pins as a projection-accurate symbol layer"
```

---

### Task 9: Real static fallback poster

Replace the stand-in `coverage-fallback.webp` with a real pre-rendered poster of the working map (used for no-JS and as the pre-init background).

**Files:**
- Create (temp, not committed): `website/_poster.cjs`
- Replace: `website/public/coverage/coverage-fallback.webp`

- [ ] **Step 1: Write a one-off poster screenshotter**

Create `website/_poster.cjs` (a dev-only helper; deleted in Step 4):
```js
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, deviceScaleFactor: 1 });
  await page.goto('http://localhost:4321/', { waitUntil: 'load' });
  const section = page.locator('section#coverage [data-coverage-map]');
  await section.scrollIntoViewIfNeeded();
  await page.waitForTimeout(6000); // let tiles + copper + pins settle
  await section.screenshot({ path: 'public/coverage/coverage-fallback.webp', type: 'webp', quality: 90 });
  await browser.close();
  console.log('wrote public/coverage/coverage-fallback.webp');
})();
```

- [ ] **Step 2: Build, preview, and capture the poster**

Run (needs `.env` with the key from Task 6):
```bash
npm run build
npm run preview -- --port 4321 &
sleep 4
node _poster.cjs
```
Expected: `wrote public/coverage/coverage-fallback.webp` — a 1600×900 webp showing copper Europe + pins. Stop the preview server afterwards (`kill %1` or close it).

- [ ] **Step 3: Eyeball the poster**

Open `public/coverage/coverage-fallback.webp`. Expected: it looks like the live map (ink-on-paper, copper Europe, wax-seal pins, no Africa drift). If it captured before tiles loaded, increase the `waitForTimeout` and re-run.

- [ ] **Step 4: Remove the helper + commit the poster**

```bash
rm _poster.cjs
git add public/coverage/coverage-fallback.webp
git commit -m "feat(website): real static fallback poster for the coverage map"
```

---

### Task 10: Full test suite + i18n + build green

**Files:** none (verification only)

- [ ] **Step 1: i18n parity**

Run:
```bash
npm run test:i18n
```
Expected: `i18n parity OK`.

- [ ] **Step 2: Full smoke suite**

Run:
```bash
npm run build && npm run test:smoke
```
Expected: all specs PASS (existing landing/legal specs + the new coverage spec), zero console errors.

- [ ] **Step 3: Type check**

Run:
```bash
npm run check
```
Expected: `astro check` reports 0 errors. (Fix any TS issues in the `<script>` — e.g. missing `?`/types — if reported.)

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "test(website): green build + smoke + i18n for coverage map" || echo "nothing to commit"
```

---

### Task 11: Env + ops documentation

Document the MapTiler key handling so deploys work and the app key isn't accidentally locked.

**Files:**
- Modify: `website/.env.example` (create if missing)
- Modify: `../CLAUDE.md` (update follow-up note)

- [ ] **Step 1: Add an env example**

Create `website/.env.example`:
```
# Domain-locked MapTiler key for the public website (NOT the Android app key).
# Create a separate key in the MapTiler dashboard, restricted to birdy.community
# + Vercel preview domains + localhost. Set the real value in Vercel env + local .env.
PUBLIC_MAPTILER_KEY=
```

- [ ] **Step 2: Verify `.env` is git-ignored**

Run:
```bash
git check-ignore .env && echo "ignored OK" || echo "WARNING: add .env to .gitignore"
```
Expected: `ignored OK`. If not, add `.env` to `website/.gitignore` (or the repo root `.gitignore`).

- [ ] **Step 3: Note the manual ops steps in CLAUDE.md**

In `../CLAUDE.md`, under "Pending follow-ups (post-launch)", add a bullet:
```
- **Coverage map MapTiler key (website):** create a SEPARATE MapTiler key domain-locked to birdy.community (+ Vercel preview + localhost), set it as `PUBLIC_MAPTILER_KEY` in Vercel env (and local `website/.env`). Do NOT reuse/lock the Android app key. The "Where Birdy works" homepage section needs it to render tiles; without it the static poster shows.
```

- [ ] **Step 4: Commit**

```bash
git add .env.example
git add ../CLAUDE.md
git commit -m "docs(website): document PUBLIC_MAPTILER_KEY ops for the coverage map"
```

- [ ] **Step 5: Push the branch + open a PR**

```bash
git push -u origin feat/website-coverage-map
gh pr create --title "Coverage map: Where Birdy works (website)" --body "Adds the Field-Journal coverage map section to birdy.community. Spec + plan in docs/superpowers/. Needs PUBLIC_MAPTILER_KEY (domain-locked) set in Vercel before the live map renders; static poster is the fallback."
```

---

## Self-review notes (for the implementer)

- **Manual / external steps** (cannot be done by code): creating the domain-locked MapTiler web key in the MapTiler dashboard and setting `PUBLIC_MAPTILER_KEY` in Vercel. The site builds and tests pass without it (static poster fallback); only the live interactive tiles need it.
- **CI safety:** every automated gate (`build`, `test:i18n`, `test:smoke`) passes with no key present, because `initMap` is gated on `KEY` and never runs in CI. Do not add a coverage test that hard-requires the live canvas.
- **Privacy:** the pins are decorative sample coordinates, not user data — keep it that way (do not wire real diary/finds data into this public map).
