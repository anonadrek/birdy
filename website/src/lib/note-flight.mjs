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
