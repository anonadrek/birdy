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
/** Today's bird is drawn this many times its size in the flock. */
export const LIT_SCALE = 1.8;
/** Today's bird's ring (the disc behind it) is drawn this many times its size in radius. */
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
 * @property {number} phase the wave's phase in cycles, close for neighbours, so the flock moves as one body
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
      amp: 6 + r() * 16, freq: 0.7 + r() * 0.6, phase: (height > 0 ? sy / height : 0) * 2.2 + r() * 0.25,
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
 * preserveAspectRatio matches fitView, so it lands where the canvas draws the same frame. Birds outside VIEW (the river
 * trailing off to the left, a few lead birds right of it, and the tail below it) are drawn too and would show if the
 * svg's overflow were visible; the hero clips them with overflow hidden.
 * @param {{ litIndex: number, className?: string }} input
 * @returns {string}
 */
export function flockSvg({ litIndex, className = 'flock-still' }) {
  if (!Number.isInteger(litIndex) || litIndex < 0 || litIndex >= FLOCK.birds.length) {
    throw new Error(`flockSvg: ingen fågel med index ${litIndex}`);
  }
  // className is interpolated into markup that Astro writes with set:html (Task 4), so only the characters a CSS
  // class name needs are allowed; anything else throws instead of reaching the page unescaped.
  if (!/^[\w-]+$/.test(className)) throw new Error(`flockSvg: className "${className}" innehåller otillåtna tecken`);
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
