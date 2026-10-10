// The flight between the notes' pictures (Albin 2026-10-10, "Into the post above"; spec
// docs/superpowers/specs/2026-10-10-blogg-flygvag-och-namn-design.md): the birds that leave one picture's flock fly on
// and land in the next picture's flock, up into the note above when the cards are stacked and on to the right when
// they sit side by side, so they always fly the way the flocks face. Where a card's text stands between two stacked
// pictures (the phone's single column, or the blog's large card above its grid), the birds climb the page's right
// margin instead of vanishing behind the text.
// Pure functions; NoteFlight.astro measures the page and draws what these return.
import { MARK } from '../components/hero/flock-data.mjs';
import { COLOURS, rng } from '../components/hero/flock.mjs';

/** @typedef {{ x: number, y: number, w: number, h: number }} Rect */
/** @typedef {{ x: number, y: number }} Point */
// A cubic Bézier: where it starts, its two control points, where it ends.
/** @typedef {[Point, Point, Point, Point]} Cubic */
// A bird along a flight: where (its middle), how wide, turned how far, mirrored or not, its colour and opacity, and
// how far along the whole flight it sits (0 to 1, by arc length).
/** @typedef {{ x: number, y: number, size: number, rot: number, mirror: boolean, colour: string, alpha: number, t: number }} Bird */

/** Pictures whose tops differ by less than this many pixels sit in the same row. */
const ROW_TOLERANCE = 8;
/** The narrowest margin (px) a climb up the margin fits in: a small bird and a little air on both sides. */
const MIN_LANE = 12;
/** The largest bird (px) drawn in the margin, however wide the margin is. */
const LANE_SIZE_MAX = 11;
/** Within this many degrees of straight up or down a bird keeps the side its neighbour faces, so a climb never
 *  flickers between mirrored and not; past it the usual rule holds (mirrored when heading left). */
const UPRIGHT_BAND = 12;
/** The share of an up flight's direct curve that may lie behind text before the flight takes the margin instead. */
const HIDDEN_MAX = 0.25;

/**
 * Which pictures fly to which, for the pictures in the list's order (newest first). For each picture after the first:
 * when the one before it lies above, this one flies up into it; when the one before it lies to its left in the same
 * row, that one flies right into this one. Any other placement gets no flight.
 * @param {Rect[]} rects
 * @returns {{ from: number, to: number, dir: 'up' | 'right' }[]}
 */
export function pairFlights(rects) {
  /** @type {{ from: number, to: number, dir: 'up' | 'right' }[]} */
  const out = [];
  for (let k = 1; k < rects.length; k++) {
    const q = rects[k - 1];
    const p = rects[k];
    if (q.y + q.h <= p.y + 1) out.push({ from: k, to: k - 1, dir: 'up' });
    else if (Math.abs(q.y - p.y) < ROW_TOLERANCE && q.x + q.w <= p.x + 1) out.push({ from: k - 1, to: k, dir: 'right' });
  }
  return out;
}

/**
 * A point inside a rect, from the picture's own 0 to 1 coordinates.
 * @param {Rect} rect
 * @param {Point} rel
 * @returns {Point}
 */
export function at(rect, rel) {
  return { x: rect.x + rel.x * rect.w, y: rect.y + rel.y * rect.h };
}

/**
 * The flight's curve, a cubic Bézier from where the birds leave to where they land. Up: it keeps climbing the way the
 * picture's own trail leaves and comes into the flock from below; right: it arcs up over the gap (above the words
 * that sit low in the pictures) and drops into the flock.
 * @param {Point} start
 * @param {Point} end
 * @param {'up' | 'right'} dir
 * @returns {Cubic}
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

/**
 * The text that stands across an up flight's direct way: the text boxes the direct curve (flightCurve) would pass
 * behind, when they would hide more than a quarter of it (the upper card's text when the cards are stacked, or the
 * row of texts between two rows of the grid). The flight then takes the margin instead (laneFlight).
 * @param {Point} start where the birds leave the lower picture
 * @param {Point} end where they land in the upper picture
 * @param {Rect[]} boxes the cards' text boxes
 * @returns {Rect | null} the boxes in the way as one rect, or null when the direct curve can pass
 */
export function textInTheWay(start, end, boxes) {
  const curve = flightCurve(start, end, 'up');
  const steps = 100;
  /** @type {Set<Rect>} */
  const hit = new Set();
  let hidden = 0;
  for (let i = 0; i <= steps; i++) {
    const p = point(curve, i / steps);
    const box = boxes.find((b) => p.x >= b.x && p.x <= b.x + b.w && p.y >= b.y && p.y <= b.y + b.h);
    if (box) { hidden++; hit.add(box); }
  }
  if (hidden / (steps + 1) <= HIDDEN_MAX) return null;
  const across = [...hit];
  const x = Math.min(...across.map((b) => b.x));
  const y = Math.min(...across.map((b) => b.y));
  return { x, y, w: Math.max(...across.map((b) => b.x + b.w)) - x, h: Math.max(...across.map((b) => b.y + b.h)) - y };
}

/**
 * Which picture a climb up the margin leaves from: the one nearest the margin in the row of picture k (k itself on the
 * phone's single column; the right hand card when two sit under the blog's large card, so the climb does not cross
 * the flight between them).
 * @param {Rect[]} rects
 * @param {number} k
 * @returns {number}
 */
export function nearestTheMargin(rects, k) {
  let best = k;
  rects.forEach((r, i) => {
    if (Math.abs(r.y - rects[k].y) < ROW_TOLERANCE && r.x + r.w > rects[best].x + rects[best].w) best = i;
  });
  return best;
}

/**
 * The lane up the page's right margin, between the cards' right edge and the page's: its middle, and the bird size
 * that fits it with a little air on both sides. Null when the margin is too narrow for a bird.
 * @param {number} cardsRight the cards' right edge
 * @param {number} pageRight the last x a bird may reach (the page's width less a hair, never the scrollbar)
 * @returns {{ x: number, size: number } | null}
 */
export function marginLane(cardsRight, pageRight) {
  const room = pageRight - cardsRight;
  if (!Number.isFinite(room) || room < MIN_LANE) return null;
  return { x: cardsRight + room / 2, size: Math.min(LANE_SIZE_MAX, room * 0.55) };
}

/**
 * The way round text that stands between two stacked pictures: from the lower picture's exit out to the margin lane
 * (across the picture's empty top at `level`, clear of the card's edge), up the lane past the text and in through the
 * upper picture's right part to its landing point, coming into the flock from below like the direct climb. Three cubic
 * Béziers that meet heading the same way (straight up), so the birds turn smoothly at the joins.
 * @param {Point} start the lower picture's exit
 * @param {Point} end the upper picture's landing point
 * @param {Rect} box the text in the way (textInTheWay)
 * @param {number} lane the lane's x (marginLane)
 * @param {number} [level] the height the birds cross to the margin at (by default the exit's own)
 * @returns {Cubic[]}
 */
export function laneFlight(start, end, box, lane, level = start.y) {
  const foot = { x: lane, y: Math.min(box.y + box.h, start.y) };
  const head = { x: lane, y: Math.max(box.y, end.y) };
  const climb = foot.y - head.y;
  const run = Math.max(level, start.y);
  // A shallow swoop rather than a ruled line: down a few pixels after the exit, then up into the lane.
  /** @type {Cubic} */
  const out = [start, { x: start.x + (lane - start.x) * 0.45, y: run + 6 }, { x: lane, y: run }, foot];
  /** @type {Cubic} */
  const up = [foot, { x: lane, y: foot.y - climb / 3 }, { x: lane, y: head.y + climb / 3 }, head];
  const rise = Math.max(24, (head.y - end.y) * 0.5);
  /** @type {Cubic} */
  const into = [head, { x: lane, y: head.y - rise }, { x: end.x + (lane - end.x) * 0.5, y: end.y + (head.y - end.y) * 0.3 }, end];
  return [out, up, into];
}

/**
 * @param {Cubic} c
 * @param {number} t
 * @returns {Point}
 */
function point(c, t) {
  const u = 1 - t;
  const a = u * u * u, b = 3 * u * u * t, d = 3 * u * t * t, e = t * t * t;
  return { x: a * c[0].x + b * c[1].x + d * c[2].x + e * c[3].x, y: a * c[0].y + b * c[1].y + d * c[2].y + e * c[3].y };
}

/**
 * @param {Cubic} c
 * @param {number} t
 * @returns {Point}
 */
function tangent(c, t) {
  const u = 1 - t;
  return {
    x: 3 * u * u * (c[1].x - c[0].x) + 6 * u * t * (c[2].x - c[1].x) + 3 * t * t * (c[3].x - c[2].x),
    y: 3 * u * u * (c[1].y - c[0].y) + 6 * u * t * (c[2].y - c[1].y) + 3 * t * t * (c[3].y - c[2].y),
  };
}

/**
 * One curve or a path of curves, as a path.
 * @param {Cubic | Cubic[]} path
 * @returns {Cubic[]}
 */
function segments(path) {
  return Array.isArray(path[0]) ? /** @type {Cubic[]} */ (path) : [/** @type {Cubic} */ (path)];
}

/**
 * The birds along a flight, one curve or a path of curves (laneFlight): spaced by arc length over the whole way, about
 * `gap` pixels apart (at least six birds, at most `max`), each turned along the way with a few degrees of slack, a
 * little smaller and lighter in the middle where they are furthest from both flocks. `thin` (0 to below 1) spreads the
 * middle out and draws the birds closer together near both flocks, like a flock leaving and arriving; 0 spaces them
 * evenly. The very ends are left out: those birds belong to the flocks. A bird heading left is mirrored instead of
 * turned belly up; near straight up a bird keeps its neighbour's side, so a climb never flickers.
 * @param {Cubic | Cubic[]} path
 * @param {{ gap?: number, size?: number, seed?: number, max?: number, thin?: number }} [options]
 * @returns {Bird[]}
 */
export function birdsAlong(path, { gap = 26, size = 11, seed = 1, max = 16, thin = 0 } = {}) {
  const segs = segments(path);
  const steps = 240;
  /** @type {{ seg: number, t: number, len: number }[]} */
  const table = [{ seg: 0, t: 0, len: 0 }];
  let len = 0;
  segs.forEach((c, seg) => {
    let prev = point(c, 0);
    for (let i = 1; i <= steps; i++) {
      const t = i / steps;
      const p = point(c, t);
      len += Math.hypot(p.x - prev.x, p.y - prev.y);
      table.push({ seg, t, len });
      prev = p;
    }
  });
  const n = Math.min(Math.max(6, Math.round(len / gap)), Math.max(6, max));
  const spread = Math.min(Math.max(thin, 0), 0.9);
  const spots = [];
  for (let i = 1; i < n; i++) {
    const u = i / n;
    const want = (u - (spread * Math.sin(2 * Math.PI * u)) / (2 * Math.PI)) * len;
    const e = table.find((x) => x.len >= want) ?? table[table.length - 1];
    const c = segs[e.seg];
    const v = tangent(c, e.t);
    spots.push({ p: point(c, e.t), base: (Math.atan2(v.y, v.x) * 180) / Math.PI, s: len > 0 ? want / len : 0 });
  }
  const band = Math.sin((UPRIGHT_BAND * Math.PI) / 180);
  const cos = (deg) => Math.cos((deg * Math.PI) / 180);
  const clear = spots.find((q) => Math.abs(cos(q.base)) >= band);
  let side = clear ? cos(clear.base) < 0 : false;
  const r = rng(seed);
  return spots.map(({ p, base, s }) => {
    if (Math.abs(cos(base)) >= band) side = cos(base) < 0;
    const heading = base + (r() - 0.5) * 6;
    const mid = 1 - Math.abs(s - 0.5) * 2;
    return {
      x: p.x,
      y: p.y,
      size: size * (1 - 0.25 * mid) * (0.9 + r() * 0.2),
      rot: side ? heading - 180 : heading,
      mirror: side,
      colour: COLOURS[1 + Math.floor(r() * 2)],
      alpha: 0.9 - 0.25 * mid,
      t: s,
    };
  });
}

/**
 * @param {Rect} a
 * @param {Rect} b
 * @returns {boolean}
 */
function overlaps(a, b) {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

/**
 * The birds that cover none of the blocking rects (the cards' text, the pictures' own words, the page's edges): the
 * flight goes behind those and comes out on the other side.
 * @template {{ x: number, y: number, size: number }} B
 * @param {B[]} birds
 * @param {Rect[]} blockers
 * @param {number} [pad] extra room around each bird, in px
 * @returns {B[]}
 */
export function clearOf(birds, blockers, pad = 2) {
  return birds.filter((b) => {
    const box = { x: b.x - b.size / 2 - pad, y: b.y - b.size / 2 - pad, w: b.size + 2 * pad, h: b.size + 2 * pad };
    return !blockers.some((r) => overlaps(box, r));
  });
}

/** @param {number} n */
const f5 = (n) => Math.round(n * 1e5) / 1e5;
/** @param {number} n */
const f2 = (n) => Math.round(n * 100) / 100;

/**
 * The SVG transform that draws the mark at (x, y), `size` wide, turned `rot` degrees and mirrored when asked:
 * translate(x y) rotate(rot) scale(±k k) translate(-cx -cy) as one matrix, the same as markMatrix when not mirrored.
 * @param {number} x
 * @param {number} y
 * @param {number} size
 * @param {number} rot
 * @param {boolean} mirror
 * @returns {string}
 */
export function birdTransform(x, y, size, rot, mirror) {
  const k = size / MARK.w;
  const a = (rot * Math.PI) / 180;
  const c = Math.cos(a);
  const s = Math.sin(a);
  const sx = mirror ? -k : k;
  const A = c * sx, B = s * sx, C = -s * k, D = c * k;
  return `matrix(${f5(A)} ${f5(B)} ${f5(C)} ${f5(D)} ${f2(x - A * MARK.cx - C * MARK.cy)} ${f2(y - B * MARK.cx - D * MARK.cy)})`;
}

/**
 * The picture's file name without folders, hash, extension or query: the key in src/data/note-art.json.
 * @param {unknown} src
 * @returns {string}
 */
export function artKey(src) {
  return String(src).split('?')[0].split('/').pop().split('.')[0];
}
