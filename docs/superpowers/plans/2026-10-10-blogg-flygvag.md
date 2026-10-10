# Bloggen som en flygväg: implementationsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fåglarna som lämnar en bloggbilds flock flyger vidare och landar i nästa bilds flock (uppåt in i inlägget ovanför när korten staplas, åt höger när de står sida vid sida), och på ett enskilt inlägg stiger de mot Birdys logga.

**Architecture:** Rena funktioner i `website/src/lib/note-flight.mjs` (vilka bilder som hör ihop, kurvan, fåglarna längs den, vilka som döljs bakom text, SVG-transformen) testas med `node --test`. Varje flockbild får data om var dess spår lämnar bilden, var nya fåglar landar och vilka rutor (bildens egna ord) som ska undvikas; `tools/render-note-art.mjs` skriver dem till `src/data/note-art.json`. Komponenten `NoteFlight.astro` ritar ett SVG-lager per lista i webbläsaren och räknar om det när layouten ändras.

**Tech Stack:** Astro 7, vanilla TypeScript i `<script>`, SVG, Playwright, `node --test`.

**Spec:** `docs/superpowers/specs/2026-10-10-blogg-flygvag-och-namn-design.md`, del 2. Regeln "flocken flyger ihop" i CLAUDE.md gäller: inga fåglar upp och ner, en riktning per flygning.

**Arbetsplats:** worktree `C:/w/birdy-flyg` på grenen `website/flocken-flyger-vidare` (från `origin/main`). Alla kommandon nedan körs i `C:/w/birdy-flyg/website` om inget annat står. Windows, Git Bash.

---

## Filer

| Fil | Ansvar |
|---|---|
| `website/src/lib/note-flight.mjs` (ny) | Rena funktioner: `pairFlights`, `at`, `flightCurve`, `birdsAlong`, `clearOf`, `birdTransform`, `artKey` |
| `website/tests/unit/note-flight.unit.mjs` (ny) | Enhetstester för allt ovan |
| `website/src/data/note-art.json` (ny) | Per bildfil (namnet utan ändelse): `exit`, `land`, `avoid` i bildens 0 till 1-koordinater |
| `website/tools/render-note-art.mjs` | Skriver ut `exit`, `land`, `avoid` för sina bilder till `note-art.json` (behåller andra nycklar) |
| `website/src/components/NoteFlight.astro` (ny) | Markens `<path>` i `<defs>` och skriptet som ritar lagret |
| `website/src/components/NoteCard.astro` | `data-flight-art`, `data-exit`, `data-land`, `data-avoid` på bilden när bilden finns i `note-art.json` |
| `website/src/components/FieldNotesIndex.astro` | `data-note-flight` på listan och `<NoteFlight />` |
| `website/src/components/FieldNotesTeaser.astro` | `data-note-flight` på korten och `<NoteFlight />` |
| `website/src/components/FieldNoteArticle.astro` | Plåtbildens data och `data-note-flight="logo"` på huvudet |
| `website/tests/note-flight.spec.ts` (ny) | Lagret finns, är dolt för skärmläsare, ritar inte över text, antal flygningar per bredd |

---

### Task 1: De rena funktionerna

**Files:**
- Create: `website/src/lib/note-flight.mjs`
- Test: `website/tests/unit/note-flight.unit.mjs`

- [ ] **Step 1: Skriv de fallerande testerna**

```js
// The flight between the notes' pictures (src/lib/note-flight.mjs), spec 2026-10-10 "Bloggen som en flygväg".
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { pairFlights, at, flightCurve, birdsAlong, clearOf, birdTransform, artKey } from '../../src/lib/note-flight.mjs';
import { markMatrix } from '../../src/components/hero/flock.mjs';

const R = (x, y, w = 300, h = 158) => ({ x, y, w, h });

test('staplade kort: varje bild flyger upp in i bilden ovanför', () => {
  const rects = [R(16, 100), R(16, 500), R(16, 900)];
  assert.deepEqual(pairFlights(rects), [
    { from: 1, to: 0, dir: 'up' },
    { from: 2, to: 1, dir: 'up' },
  ]);
});

test('kort sida vid sida: flygningen går åt höger in i nästa kort', () => {
  const rects = [R(100, 200), R(450, 200), R(800, 203)];
  assert.deepEqual(pairFlights(rects), [
    { from: 0, to: 1, dir: 'right' },
    { from: 1, to: 2, dir: 'right' },
  ]);
});

test('bloggen på bred skärm: det stora kortet överst, två under sida vid sida', () => {
  const rects = [R(184, 390, 634, 333), R(184, 747, 525, 275), R(732, 747, 525, 275)];
  assert.deepEqual(pairFlights(rects), [
    { from: 1, to: 0, dir: 'up' },
    { from: 1, to: 2, dir: 'right' },
  ]);
});

test('en enda bild har ingen flygning', () => {
  assert.deepEqual(pairFlights([R(0, 0)]), []);
});

test('at: en punkt i bildens egna koordinater', () => {
  assert.deepEqual(at(R(100, 200, 400, 200), { x: 0.5, y: 0.25 }), { x: 300, y: 250 });
});

/** The direction a drawn bird faces, from what birdsAlong returns. */
const facing = (b) => (b.mirror ? b.rot + 180 : b.rot);
const norm = (d) => ((d % 360) + 540) % 360 - 180;

test('fåglarna längs en flygning uppåt pekar uppåt längs kurvan, ingen upp och ner', () => {
  const curve = flightCurve({ x: 200, y: 600 }, { x: 260, y: 150 }, 'up');
  const birds = birdsAlong(curve, { seed: 3 });
  assert.ok(birds.length >= 6);
  for (const b of birds) {
    const f = norm(facing(b));
    assert.ok(f < -20 && f > -160, `pekar uppåt: ${f}`);
    // Mirrored exactly when the heading points left, so the mark is never drawn belly up.
    assert.equal(b.mirror, Math.cos((f * Math.PI) / 180) < 0);
  }
});

test('fåglarna längs en flygning åt höger pekar åt höger och speglas inte', () => {
  const curve = flightCurve({ x: 100, y: 300 }, { x: 700, y: 320 }, 'right');
  for (const b of birdsAlong(curve, { seed: 5 })) {
    assert.equal(b.mirror, false);
    assert.ok(Math.abs(norm(facing(b))) < 90);
  }
});

test('ändarna hör till flockarna: ingen fågel närmare än 8 px start eller slut', () => {
  const start = { x: 0, y: 500 }, end = { x: 0, y: 0 };
  const birds = birdsAlong(flightCurve(start, end, 'up'), { seed: 1 });
  for (const b of birds) {
    assert.ok(Math.hypot(b.x - start.x, b.y - start.y) > 8);
    assert.ok(Math.hypot(b.x - end.x, b.y - end.y) > 8);
  }
});

test('samma frö ger samma fåglar', () => {
  const c = flightCurve({ x: 10, y: 400 }, { x: 90, y: 20 }, 'up');
  assert.deepEqual(birdsAlong(c, { seed: 9 }), birdsAlong(c, { seed: 9 }));
});

test('clearOf: fåglar över en textruta ritas inte', () => {
  const birds = [{ x: 50, y: 50, size: 10 }, { x: 200, y: 200, size: 10 }];
  assert.deepEqual(clearOf(birds, [{ x: 0, y: 0, w: 100, h: 100 }]), [birds[1]]);
});

test('birdTransform: utan spegling samma matris som flockens egen markMatrix', () => {
  assert.equal(birdTransform(120, 80, 14, -30, false), markMatrix(120, 80, 14, -30));
});

test('birdTransform: speglad vänder bara x-axeln (a och b byter tecken)', () => {
  const nums = (s) => s.slice(7, -1).split(' ').map(Number);
  const [a, b, c, d] = nums(birdTransform(0, 0, 14, 20, false));
  const [ma, mb, mc, md] = nums(birdTransform(0, 0, 14, 20, true));
  assert.deepEqual([ma, mb, mc, md], [-a, -b, c, d]);
});

test('artKey: bildfilens namn ur bygget och ur dev-servern', () => {
  assert.equal(artKey('/_astro/see-the-song-flock-q25404-en.DYGdC81L.webp'), 'see-the-song-flock-q25404-en');
  assert.equal(artKey('/@fs/C:/w/birdy-flyg/website/src/assets/photos/why-birdy-flock-q25334-sv.webp?origWidth=3200&origHeight=1680&origFormat=webp'), 'why-birdy-flock-q25334-sv');
  assert.equal(artKey('/_astro/birdy-x-albit.Ab12Cd34.webp'), 'birdy-x-albit');
});
```

- [ ] **Step 2: Kör testerna och se dem falla**

Run: `node --test tests/unit/note-flight.unit.mjs`
Expected: FAIL, `Cannot find module '../../src/lib/note-flight.mjs'`.

- [ ] **Step 3: Skriv modulen**

```js
// The flight between the notes' pictures (Albin 2026-10-10, "Into the post above"; spec
// docs/superpowers/specs/2026-10-10-blogg-flygvag-och-namn-design.md): the birds that leave one picture's flock fly on
// and land in the next picture's flock, up into the note above when the cards are stacked and on to the right when
// they sit side by side, so they always fly the way the flocks face. Pure functions; NoteFlight.astro measures the page
// and draws what these return.
import { MARK } from '../components/hero/flock-data.mjs';
import { COLOURS, rng } from '../components/hero/flock.mjs';

/** @typedef {{ x: number, y: number, w: number, h: number }} Rect */
/** @typedef {{ x: number, y: number }} Point */

/** Pictures whose tops differ by less than this many pixels sit in the same row. */
const ROW_TOLERANCE = 8;

/**
 * Which pictures fly to which, for the pictures in the list's order (newest first). For each picture after the first:
 * when the one before it lies above, this one flies up into it; when the one before it lies to its left in the same
 * row, that one flies right into this one. Any other placement gets no flight.
 * @param {Rect[]} rects
 * @returns {{ from: number, to: number, dir: 'up' | 'right' }[]}
 */
export function pairFlights(rects) {
  const out = [];
  for (let k = 1; k < rects.length; k++) {
    const q = rects[k - 1];
    const p = rects[k];
    if (q.y + q.h <= p.y + 1) out.push({ from: k, to: k - 1, dir: 'up' });
    else if (Math.abs(q.y - p.y) < ROW_TOLERANCE && q.x + q.w <= p.x + 1) out.push({ from: k - 1, to: k, dir: 'right' });
  }
  return out;
}

/** A point inside a rect, from the picture's own 0 to 1 coordinates. @returns {Point} */
export function at(rect, rel) {
  return { x: rect.x + rel.x * rect.w, y: rect.y + rel.y * rect.h };
}

/**
 * The flight's curve, a cubic Bézier from where the birds leave to where they land. Up: it keeps climbing the way the
 * picture's own trail leaves and comes into the flock from below; right: it arcs up over the gap (above the words
 * that sit low in the pictures) and drops into the flock.
 * @returns {[Point, Point, Point, Point]}
 */
export function flightCurve(start, end, dir) {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  if (dir === 'up') {
    const lift = Math.max(60, Math.abs(dy) * 0.45);
    return [start, { x: start.x + dx * 0.15, y: start.y - lift }, { x: end.x - dx * 0.1, y: end.y + lift * 0.6 }, end];
  }
  const top = Math.min(start.y, end.y) - Math.max(40, Math.abs(dx) * 0.25);
  return [start, { x: start.x + dx * 0.35, y: top }, { x: end.x - dx * 0.35, y: top }, end];
}

function point(c, t) {
  const u = 1 - t;
  const a = u * u * u, b = 3 * u * u * t, d = 3 * u * t * t, e = t * t * t;
  return { x: a * c[0].x + b * c[1].x + d * c[2].x + e * c[3].x, y: a * c[0].y + b * c[1].y + d * c[2].y + e * c[3].y };
}

function tangent(c, t) {
  const u = 1 - t;
  return {
    x: 3 * u * u * (c[1].x - c[0].x) + 6 * u * t * (c[2].x - c[1].x) + 3 * t * t * (c[3].x - c[2].x),
    y: 3 * u * u * (c[1].y - c[0].y) + 6 * u * t * (c[2].y - c[1].y) + 3 * t * t * (c[3].y - c[2].y),
  };
}

/**
 * The birds along a flight: about `gap` pixels apart (at least six birds), each turned along the curve with a few
 * degrees of slack, a little smaller and lighter in the middle where they are furthest from both flocks. The very ends
 * are left out: those birds belong to the flocks. A bird heading left is mirrored instead of turned belly up.
 * @returns {{ x: number, y: number, size: number, rot: number, mirror: boolean, colour: string, alpha: number, t: number }[]}
 */
export function birdsAlong(curve, { gap = 26, size = 11, seed = 1 } = {}) {
  const steps = 240;
  const table = [{ t: 0, len: 0 }];
  let prev = point(curve, 0);
  let len = 0;
  for (let i = 1; i <= steps; i++) {
    const t = i / steps;
    const p = point(curve, t);
    len += Math.hypot(p.x - prev.x, p.y - prev.y);
    table.push({ t, len });
    prev = p;
  }
  const n = Math.max(6, Math.round(len / gap));
  const r = rng(seed);
  const birds = [];
  for (let i = 1; i < n; i++) {
    const want = (i / n) * len;
    const t = (table.find((e) => e.len >= want) ?? table[table.length - 1]).t;
    const p = point(curve, t);
    const v = tangent(curve, t);
    const heading = (Math.atan2(v.y, v.x) * 180) / Math.PI + (r() - 0.5) * 6;
    const mirror = Math.cos((heading * Math.PI) / 180) < 0;
    const mid = 1 - Math.abs(t - 0.5) * 2;
    birds.push({
      x: p.x,
      y: p.y,
      size: size * (1 - 0.25 * mid) * (0.9 + r() * 0.2),
      rot: mirror ? heading - 180 : heading,
      mirror,
      colour: COLOURS[1 + Math.floor(r() * 2)],
      alpha: 0.9 - 0.25 * mid,
      t,
    });
  }
  return birds;
}

function overlaps(a, b) {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

/** The birds that cover none of the blocking rects (the cards' text, the pictures' own words): the flight goes behind
 *  those and comes out on the other side. */
export function clearOf(birds, blockers, pad = 2) {
  return birds.filter((b) => {
    const box = { x: b.x - b.size / 2 - pad, y: b.y - b.size / 2 - pad, w: b.size + 2 * pad, h: b.size + 2 * pad };
    return !blockers.some((r) => overlaps(box, r));
  });
}

const f5 = (n) => Math.round(n * 1e5) / 1e5;
const f2 = (n) => Math.round(n * 100) / 100;

/** The SVG transform that draws the mark at (x, y), `size` wide, turned `rot` degrees and mirrored when asked:
 *  translate(x y) rotate(rot) scale(±k k) translate(-cx -cy) as one matrix, the same as markMatrix when not mirrored. */
export function birdTransform(x, y, size, rot, mirror) {
  const k = size / MARK.w;
  const a = (rot * Math.PI) / 180;
  const c = Math.cos(a);
  const s = Math.sin(a);
  const sx = mirror ? -k : k;
  const A = c * sx, B = s * sx, C = -s * k, D = c * k;
  return `matrix(${f5(A)} ${f5(B)} ${f5(C)} ${f5(D)} ${f2(x - A * MARK.cx - C * MARK.cy)} ${f2(y - B * MARK.cx - D * MARK.cy)})`;
}

/** The picture's file name without folders, hash, extension or query: the key in src/data/note-art.json. */
export function artKey(src) {
  return String(src).split('?')[0].split('/').pop().split('.')[0];
}
```

- [ ] **Step 4: Kör testerna**

Run: `node --test tests/unit/note-flight.unit.mjs`
Expected: PASS, alla 13. Faller `birdTransform ... markMatrix`: jämför avrundningen i `markMatrix` (`src/components/hero/flock.mjs`, funktionerna `r5` och `r2`) och använd exakt samma avrundning (fem decimaler för a till d, två för e och f).

- [ ] **Step 5: Hela enhetssviten**

Run: `npm run test:unit`
Expected: PASS (tidigare antal plus 13).

- [ ] **Step 6: Commit**

```bash
git add src/lib/note-flight.mjs tests/unit/note-flight.unit.mjs
git commit -m "website(blogg): flygvägens rena funktioner, par, kurva och fåglar längs den

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Bildernas data (`note-art.json`)

**Files:**
- Modify: `website/tools/render-note-art.mjs` (funktionen `silhouetteFlock`, blocket `window.__ready`, och huvudloopen)
- Create: `website/src/data/note-art.json`

Data per bild, i bildens egna 0 till 1-koordinater (bilden är 1600 × 840 i ritningen):
- `exit`: där bildens eget spår slutar (den sista av de sju fåglarna som lämnar flocken).
- `land`: där ankommande fåglar landar: den inre fågel i flocken som ligger närmast punkten 45 % in och 35 % ned i silhuettens ruta (ryggen, framför den tända fågeln).
- `avoid`: ordkolumnens ruta (`.col`) med 16 px marginal, som en lista med en ruta (tom lista när bilden saknar ord).

- [ ] **Step 1: Låt `silhouetteFlock` lämna tillbaka `exit` och `land`**

I `silhouetteFlock(f)`, spara den sista lämnande fågelns läge och välj landningsfågeln innan `lit` tas bort ur `inner` är inte nödvändigt; välj den efter att alla fåglar ritats:

```js
  // (in the LEAVE loop, after bird(ctx, lx, ly, ...):)
    lastLeave = { x: lx, y: ly };

  // (after the lit bird is drawn, before the return:)
  const aim = { x: x0 + w * 0.45, y: y0 + h * 0.35 };
  const landing = inner.reduce((a, p) => ((p[0] - aim.x) ** 2 + (p[1] - aim.y) ** 2 < (a[0] - aim.x) ** 2 + (a[1] - aim.y) ** 2 ? p : a), inner[0] ?? lit);
  return { count: edge.length + inner.length + 1 + LEAVE, exit: { x: lastLeave.x / W, y: lastLeave.y / H }, land: { x: landing[0] / W, y: landing[1] / H } };
```

Deklarera `let lastLeave = null;` före LEAVE-loopen. Byt i `window.__ready` `count = await silhouetteFlock(PIC.flock)` mot `flight = await silhouetteFlock(PIC.flock)` och returnera `{ count: flight?.count ?? null, exit: flight?.exit ?? null, land: flight?.land ?? null, avoid }`, där `avoid` räknas efter att ordkolumnen placerats:

```js
  let avoid = [];
  // (after col.style.top is set:)
    const b = col.getBoundingClientRect();
    avoid = [{ x: (b.left - 16) / W, y: (b.top - 16) / H, w: (b.width + 32) / W, h: (b.height + 32) / H }];
```

- [ ] **Step 2: Skriv `note-art.json` från huvudloopen**

I Node-delen: läs befintlig fil om den finns (`src/data/note-art.json`, annars `{}`), och efter varje bild och språk:

```js
const artFile = join(root, 'src/data/note-art.json');
const art = existsSync(artFile) ? JSON.parse(readFileSync(artFile, 'utf8')) : {};
const r4 = (n) => Math.round(n * 1e4) / 1e4;
const pt = (p) => p && { x: r4(p.x), y: r4(p.y) };
// (inside the loop, after `const result = await tab.evaluate(() => window.__ready);` and the count check:)
if (result.exit && !outDir) art[base] = { exit: pt(result.exit), land: pt(result.land), avoid: result.avoid.map((a) => ({ x: r4(a.x), y: r4(a.y), w: r4(a.w), h: r4(a.h) })) };
// (after the loop, before closing the browser:)
if (!outDir) writeFileSync(artFile, JSON.stringify(art, null, 2) + '\n');
```

Byt kontrollen `count !== 839` mot `result.count !== 839`. Importera `existsSync` från `node:fs`.

- [ ] **Step 3: Kör renderaren**

Run: `node tools/render-note-art.mjs` (stoppa en eventuell `astro preview` först, Windows låser filerna)
Expected: fyra rader `...webp 3200x1680` och en ny `src/data/note-art.json` med nycklarna `see-the-song-flock-q25404-en`, `see-the-song-flock-q25404-sv`, `why-birdy-flock-q25334-en`, `why-birdy-flock-q25334-sv`. Kontrollera med `git diff --stat` att webp-filerna är byte för byte oförändrade (renderaren är fröstyrd); ändras de, har steg 1 ändrat ritordningen eller slumpföljden och ska rättas.

- [ ] **Step 4: Lägg till Birdy × AlbIT för hand**

Bilden `src/assets/photos/birdy-x-albit.webp` (1200 × 630) ritas av ett annat verktyg. Öppna den (Read-verktyget visar bilder) och mät: `land` mitt i Birdys fågelkropp på persikosidan, `exit` där fåglarna efter näbben slutar till höger, `avoid` två rutor: AlbIT-ordmärket och ordet "Birdy." under fågeln (16 px marginal, räknat i bildens egna pixlar och delat med 1200 respektive 630). Lägg in nyckeln `birdy-x-albit` i `note-art.json` i samma form.

- [ ] **Step 5: Commit**

```bash
git add tools/render-note-art.mjs src/data/note-art.json
git commit -m "website(blogg): bildernas flygdata, var spåret lämnar och var fåglarna landar

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Korten bär bildens data

**Files:**
- Modify: `website/src/components/NoteCard.astro`

- [ ] **Step 1: Slå upp bilden och skriv attributen**

I frontmattern:

```ts
import noteArt from '../data/note-art.json';
import { artKey } from '../lib/note-flight.mjs';

type NoteArt = { exit: { x: number; y: number }; land: { x: number; y: number }; avoid: { x: number; y: number; w: number; h: number }[] };
const art = (noteArt as Record<string, NoteArt>)[artKey(image.src)];
```

På `<div class="ncard-img">`:

```astro
  <div
    class="ncard-img"
    data-flight-art={art ? '' : undefined}
    data-exit={art ? JSON.stringify(art.exit) : undefined}
    data-land={art ? JSON.stringify(art.land) : undefined}
    data-avoid={art ? JSON.stringify(art.avoid) : undefined}
  >
```

- [ ] **Step 2: Bygg och kontrollera attributen**

Run: `npm run build && grep -o 'data-flight-art[^>]*' dist/blog/index.html | head -3`
Expected: tre träffar med `data-exit=` och `data-land=`.

- [ ] **Step 3: Commit**

```bash
git add src/components/NoteCard.astro
git commit -m "website(blogg): korten bär bildens flygdata

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Lagret som ritar flygningarna

**Files:**
- Create: `website/src/components/NoteFlight.astro`
- Modify: `website/src/components/FieldNotesIndex.astro`, `website/src/components/FieldNotesTeaser.astro`

- [ ] **Step 1: Skriv komponenten**

```astro
---
// The flight between the notes' pictures (spec 2026-10-10, "Bloggen som en flygväg"): one decorative SVG layer per
// list marked data-note-flight, drawn from src/lib/note-flight.mjs. The cards' text lies in front of the flight, so the
// birds go behind it and come out in the next picture. Without JavaScript the cards are as before.
import { MARK } from './hero/flock-data.mjs';
---

<svg class="nflight-defs" aria-hidden="true" focusable="false" width="0" height="0">
  <defs><path id="nf-mark" d={MARK.path}></path></defs>
</svg>

<script>
  import { pairFlights, at, flightCurve, birdsAlong, clearOf, birdTransform } from '../lib/note-flight.mjs';

  const NS = 'http://www.w3.org/2000/svg';
  type Rect = { x: number; y: number; w: number; h: number };

  const json = <T,>(s: string | undefined): T | null => {
    try { return s ? (JSON.parse(s) as T) : null; } catch { return null; }
  };

  function rectIn(el: Element, origin: DOMRect): Rect {
    const r = el.getBoundingClientRect();
    return { x: r.left - origin.left, y: r.top - origin.top, w: r.width, h: r.height };
  }

  function draw(list: HTMLElement) {
    list.querySelector(':scope > svg.nflight')?.remove();
    const origin = list.getBoundingClientRect();
    const arts = [...list.querySelectorAll<HTMLElement>('[data-flight-art]')];
    const rects = arts.map((el) => rectIn(el, origin));
    const blockers: Rect[] = [...list.querySelectorAll('.ncard-body, .ctitle')].map((el) => rectIn(el, origin));
    arts.forEach((el, i) => {
      for (const a of json<Rect[]>(el.dataset.avoid) ?? []) blockers.push({ x: rects[i].x + a.x * rects[i].w, y: rects[i].y + a.y * rects[i].h, w: a.w * rects[i].w, h: a.h * rects[i].h });
    });

    type Flight = { start: { x: number; y: number }; end: { x: number; y: number }; dir: 'up' | 'right' };
    const flights: Flight[] = [];
    if (list.dataset.noteFlight === 'logo') {
      // A single note: its picture's own trail rises on toward Birdy's logo and goes in under the menu bar.
      const brand = document.querySelector('.nav .brand');
      const nav = document.querySelector('.nav');
      const exit = arts[0] && json<{ x: number; y: number }>(arts[0].dataset.exit);
      if (brand && nav && exit) {
        const b = rectIn(brand, origin);
        const n = rectIn(nav, origin);
        flights.push({ start: at(rects[0], exit), end: { x: b.x + b.w / 2, y: n.y + n.h - 4 }, dir: 'up' });
      }
    } else {
      for (const p of pairFlights(rects)) {
        const exit = json<{ x: number; y: number }>(arts[p.from].dataset.exit);
        const land = json<{ x: number; y: number }>(arts[p.to].dataset.land);
        if (exit && land) flights.push({ start: at(rects[p.from], exit), end: at(rects[p.to], land), dir: p.dir });
      }
    }
    if (!flights.length) return;

    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('class', 'nflight');
    svg.setAttribute('aria-hidden', 'true');
    svg.setAttribute('focusable', 'false');
    flights.forEach((f, fi) => {
      const g = document.createElementNS(NS, 'g');
      g.setAttribute('class', 'nf-flight');
      const birds = clearOf(birdsAlong(flightCurve(f.start, f.end, f.dir), { seed: 101 + fi * 7 }), blockers);
      birds.forEach((b, i) => {
        const use = document.createElementNS(NS, 'use');
        use.setAttribute('href', '#nf-mark');
        use.setAttribute('transform', birdTransform(b.x, b.y, b.size, b.rot, b.mirror));
        use.setAttribute('fill', b.colour);
        use.setAttribute('fill-opacity', String(Math.round(b.alpha * 100) / 100));
        use.style.setProperty('--i', String(i));
        g.append(use);
      });
      svg.append(g);
    });
    list.append(svg);
  }

  const lists = [...document.querySelectorAll<HTMLElement>('[data-note-flight]')];
  let frame = 0;
  const redraw = () => { cancelAnimationFrame(frame); frame = requestAnimationFrame(() => lists.forEach(draw)); };
  if (lists.length) {
    redraw();
    document.fonts?.ready.then(redraw);
    const ro = new ResizeObserver(redraw);
    lists.forEach((l) => ro.observe(l));
    const io = new IntersectionObserver((entries) => {
      for (const e of entries) if (e.isIntersecting) { e.target.classList.add('nflight-go'); io.unobserve(e.target); }
    }, { threshold: 0.2 });
    lists.forEach((l) => io.observe(l));
  }
</script>

<style is:global>
  .nflight-defs { position: absolute; width: 0; height: 0; overflow: hidden; }
  [data-note-flight] { position: relative; }
  [data-note-flight] > svg.nflight { position: absolute; left: 0; top: 0; width: 100%; height: 100%; overflow: visible; pointer-events: none; z-index: 1; }
  @media (prefers-reduced-motion: no-preference) {
    html.motion-ready [data-note-flight] > svg.nflight use { opacity: 0; }
    html.motion-ready [data-note-flight].nflight-go > svg.nflight use { animation: nf-in .5s ease-out both; animation-delay: calc(var(--i) * 70ms); }
  }
  @keyframes nf-in { from { opacity: 0; } to { opacity: 1; } }
</style>
```

- [ ] **Step 2: Koppla in bloggens lista**

I `FieldNotesIndex.astro`: importera `NoteFlight` och ändra listan:

```astro
    <div class="wrap list" data-note-flight>
      {latest && <div class="first"><NoteCard note={latest} size="large" headingLevel="h2" priority /></div>}
      {rest.length > 0 && <div class="grid" style={`--cols:${cols}`}>{rest.map((note) => <NoteCard note={note} headingLevel="h2" />)}</div>}
    </div>
    <NoteFlight />
```

- [ ] **Step 3: Koppla in startsidans anteckningar**

I `FieldNotesTeaser.astro`: importera `NoteFlight`, sätt `data-note-flight` på `<div class="cards" ...>` och lägg `<NoteFlight />` direkt efter den.

- [ ] **Step 4: Bygg och titta**

Run: `npm run build && npx astro preview --port 4761` (i bakgrunden), öppna `http://localhost:4761/blog/` och startsidan i 1440 och 390 px bredd med Playwright (`channel: 'chrome'`, `reducedMotion: 'reduce'`, Playwright laddas med `createRequire` mot `website/package.json`) och spara skärmbilder i `C:/Users/abbea/AppData/Local/Temp/claude/C--Users-abbea-dev-1-mina-projekt-birdy/a82744b8-a4a1-439e-bbd9-65d4ce31c940/scratchpad/flyg/`.
Expected: på bloggen i 1440 px en flygning från blåmesens spår upp in i Birdy-fågeln i det stora kortet och en åt höger in i rödhaken; i 390 px två flygningar uppåt som försvinner bakom kortens text och kommer fram i bilden ovanför; på startsidan två flygningar åt höger.

- [ ] **Step 5: Commit**

```bash
git add src/components/NoteFlight.astro src/components/FieldNotesIndex.astro src/components/FieldNotesTeaser.astro
git commit -m "website(blogg): flocken flyger vidare från bild till bild i bloggen och på startsidan

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Det enskilda inlägget mot loggan

**Files:**
- Modify: `website/src/components/FieldNoteArticle.astro` (huvudet med `.aplate`, rad cirka 54 till 66)

- [ ] **Step 1: Plåtens data och lagret**

I artikelns frontmatter:

```ts
import NoteFlight from './NoteFlight.astro';
import noteArt from '../data/note-art.json';
import { artKey } from '../lib/note-flight.mjs';

type NoteArt = { exit: { x: number; y: number }; land: { x: number; y: number }; avoid: { x: number; y: number; w: number; h: number }[] };
const plateArt = (noteArt as Record<string, NoteArt>)[artKey(image.src)];
```

I huvudet med plåten:

```astro
    <header class="phead" data-note-flight={plateArt ? 'logo' : undefined}>
      <figure class="wrap pfig">
        <div
          class="aplate"
          data-flight-art={plateArt ? '' : undefined}
          data-exit={plateArt ? JSON.stringify(plateArt.exit) : undefined}
          data-land={plateArt ? JSON.stringify(plateArt.land) : undefined}
          data-avoid={plateArt ? JSON.stringify(plateArt.avoid) : undefined}
        >
```

och `{plateArt && <NoteFlight />}` direkt efter `</header>`. Birdy × AlbIT-inlägget har ingen plåt (den har sin egen hjälte) och berörs inte.

- [ ] **Step 2: Titta**

Skärmbild av `http://localhost:4761/blog/why-birdy/` och `/blog/see-the-song/` i 1440 och 390 px (efter `npm run build` och omstart av preview).
Expected: spåret fortsätter från bildens topp upp mot loggan och går in under menyraden; inga fåglar över menyns länkar eller rubriken. Går flygningen inte att rita snyggt (till exempel för kort väg i 390 px), hoppa över den i den bredden: `draw` ritar då inget när `end.y` ligger mindre än 40 px ovanför `start.y`.

- [ ] **Step 3: Commit**

```bash
git add src/components/FieldNoteArticle.astro
git commit -m "website(blogg): på ett inlägg stiger spåret mot Birdys logga

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Playwright

**Files:**
- Create: `website/tests/note-flight.spec.ts`

- [ ] **Step 1: Skriv testerna**

```ts
import { test, expect, type Page } from '@playwright/test';

async function flightState(page: Page) {
  return page.evaluate(() => {
    const svg = document.querySelector('[data-note-flight] > svg.nflight');
    const boxes = [...document.querySelectorAll('[data-note-flight] .ncard-body, [data-note-flight] .ctitle')].map((e) => e.getBoundingClientRect());
    const birds = svg ? [...svg.querySelectorAll('use')].map((u) => u.getBoundingClientRect()) : [];
    const overText = birds.filter((b) => boxes.some((t) => b.left < t.right && t.left < b.right && b.top < t.bottom && t.top < b.bottom)).length;
    return { hidden: svg?.getAttribute('aria-hidden'), flights: svg ? svg.querySelectorAll('g.nf-flight').length : 0, birds: birds.length, overText };
  });
}

test.describe('flocken flyger vidare mellan bloggens bilder', () => {
  test.use({ reducedMotion: 'reduce' });

  test('bloggen på bred skärm: upp in i det stora kortet och åt höger in i nästa', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/blog/');
    await expect(page.locator('[data-note-flight] > svg.nflight')).toHaveCount(1);
    const s = await flightState(page);
    expect(s.hidden).toBe('true');
    expect(s.flights).toBe(2);
    expect(s.birds).toBeGreaterThanOrEqual(8);
    expect(s.overText).toBe(0);
  });

  test('bloggen på telefon: varje bild flyger upp in i bilden ovanför, bakom texten', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/blog/');
    const s = await flightState(page);
    expect(s.flights).toBe(2);
    expect(s.overText).toBe(0);
  });

  test('startsidan: anteckningarna sida vid sida flyger åt höger', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/');
    await page.locator('#field-notes').scrollIntoViewIfNeeded();
    const s = await flightState(page);
    expect(s.flights).toBe(2);
    expect(s.overText).toBe(0);
  });

  test('lagret tar inga klick: korten går att öppna genom fåglarna', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/blog/');
    const pe = await page.locator('[data-note-flight] > svg.nflight').evaluate((e) => getComputedStyle(e).pointerEvents);
    expect(pe).toBe('none');
  });
});
```

Antalet `flights` förutsätter de tre inläggen som finns i dag. Kommer ett fjärde till ändras raderna; skriv då om förväntan som "antal bilder minus ett" på telefon.

- [ ] **Step 2: Kör**

Run: `npm run build:fixtures && PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4762 npx playwright test tests/note-flight.spec.ts --reporter=line`
Expected: 4 passed. Faller `overText` i 390 px: kontrollera att `.ncard-body` finns som blockerare och att `clearOf` får rutor i samma koordinatsystem som fåglarna (listans origo).

- [ ] **Step 3: Commit**

```bash
git add tests/note-flight.spec.ts
git commit -m "website(blogg): Playwright för flygvägen, dold för skärmläsare och aldrig över text

Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Finjustering och förhandsvisning

- [ ] **Step 1: Skärmbilder i fyra bredder**

`/blog/`, `/sv/blog/`, startsidans `#field-notes` och `/blog/why-birdy/` i 1440, 1024, 768 och 390 px, plus närbilder (zoom 2) på varje flygning. Spara i scratchpad-mappen `flyg/`.

- [ ] **Step 2: Bedöm mot kriterierna och justera**

Kriterier: (1) varje flygning börjar där bildens eget spår slutar och slutar inne i nästa flock; (2) ingen fågel över text, över bildernas ord eller över menyn; (3) ingen fågel upp och ner, alla i en flygning åt samma håll som kurvan; (4) lugnt: inga klumpar, jämna avstånd, mindre och ljusare mitt på vägen. Justera i första hand `land` och `avoid` i `note-art.json` (och `aim` i Task 2 om alla bilder landar fel), i andra hand konstanterna i `flightCurve` (`lift`, bågens höjd) och `gap`. Kör om enhetstesterna efter varje ändring i `note-flight.mjs`.

- [ ] **Step 3: Hela grinden**

Run: `npm run test:i18n && npm run test:no-accuracy && npm run test:contrast && npm run test:no-dashes && npm run test:palette && npm run test:unit && npm run build`, sedan `npm run build:fixtures` och `PLAYWRIGHT_CHANNEL=chrome PLAYWRIGHT_PORT=4762 npx playwright test --reporter=line`.
Expected: allt grönt (samma antal som på `main` plus de nya).

- [ ] **Step 4: Pusha och kontrollera förhandsvisningen**

```bash
git push -u origin website/flocken-flyger-vidare
gh api repos/anonadrek/birdy/commits/$(git rev-parse HEAD)/status --jq '.statuses[] | .context + " " + .state + " " + .description'
```

Expected: Vercel `success`. Vid "Git information retrieval failed": en tom commit `chore: bygg förhandsvisningen igen` och pusha igen. Förhandsvisningen: `https://birdy-git-website-flocken-flyger-vidare-albtab.vercel.app/blog/`. Huvudagenten visar den för Albin; grenen slås inte ihop före hans OK.
