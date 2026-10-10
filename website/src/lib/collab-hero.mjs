// The Birdy × AlbIT hero (the field note birdy-x-albit, 2026-10-10): AlbIT's black field meets Birdy's peach paper at
// a torn edge, and Birdy's flock flies across the seam, gold on AlbIT's side and in Birdy's own colours on the paper,
// where the 839 birds form Birdy's bird. Pure functions that return SVG markup, so the page (CollabHero.astro) and the
// share image (tools/render-collab-share.mjs) draw exactly the same picture. Seeded, so every build is identical.
import { FLOCK, MARK } from '../components/hero/flock-data.mjs';
import { COLOURS } from '../components/hero/flock.mjs';

/** AlbIT's brand (albit.se): ink and gold, the gold shades matching the flock's four (brass, copper, rust, bark). */
export const ALBIT = { ink: '#111111', golds: ['#E4C55F', '#C9A227', '#A88620', '#8A6E1A'] };
/** Birdy's paper and words (website/src/styles/tokens.css). */
export const BIRDY = { peach: '#FDE5CB', peachHi: '#FEEBD6', peachLo: '#F8D6B4', rust: '#9A4526' };
/** The AlbIT wordmark's own proportions (albit.se src/assets/logo/logo-white.png, 981 × 260). */
export const WORDMARK_RATIO = 260 / 981;

// Where the flock's bird sits in its own space (the profiles' cover flock, 1640 × 720): its smallest enclosing circle.
const BIRD = { cx: 1162, cy: 344, r: 300 };
// The cover flock's birds left of this x are its trail; the rest form the bird.
const TRAIL_X = 880;

const r1 = (n) => Math.round(n * 10) / 10;

/** mulberry32, as hero/flock.mjs and the store images: the same birds in every build. */
export function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s + 0x6d2b79f5) >>> 0;
    let t = Math.imul(s ^ (s >>> 15), s | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** One small bird: Birdy's mark centred on (x, y) by its smallest enclosing circle, `size` wide, turned `rot` degrees. */
function bird(x, y, size, rot, fill, op = 1) {
  const k = (size / MARK.w).toFixed(5);
  const o = op < 1 ? ` fill-opacity="${op}"` : '';
  return `<use href="#bxa-m" fill="${fill}"${o} transform="translate(${r1(x)} ${r1(y)}) rotate(${Math.round(rot)}) scale(${k}) translate(${-MARK.cx} ${-MARK.cy})"/>`;
}

/** The flock's colour index in its own proportions (mostly copper), as hero/flock.mjs draws them. */
const colourIndex = (u) => (u < 0.08 ? 0 : u < 0.7 ? 1 : u < 0.95 ? 2 : 3);

/**
 * Defined once per page: the mark and the bird formed by the flock (in the cover flock's own coordinates, Birdy's
 * colours). Both art variants place it with a transform. Kept in a zero-size SVG that is never display:none, so the
 * references work in every browser whichever variant shows.
 */
export function collabDefs() {
  const birds = FLOCK.birds
    .filter(([x]) => x >= TRAIL_X)
    .map(([x, y, size, rot, c, op = 1]) => bird(x, y, size, rot, COLOURS[c], op))
    .join('');
  return `<svg class="bxa-defs" width="0" height="0" aria-hidden="true" focusable="false"><defs><path id="bxa-m" d="${MARK.path}"/><g id="bxa-bird">${birds}</g></defs></svg>`;
}

/** A torn paper edge through `from` → `to` along one axis, jagged like the site's DeckleEdge (seeded). */
function tornEdge(axis, at, from, to, seed) {
  const r = rng(seed);
  const pts = [];
  for (let p = from; p <= to; p += 14 + r() * 22) {
    const j = (r() - 0.45) * 13;
    pts.push(axis === 'x' ? [at + j, p] : [p, at + j]);
  }
  pts.push(axis === 'x' ? [at, to] : [to, at]);
  return pts;
}

/** A point on a cubic Bézier. */
function cubic(P, t) {
  const u = 1 - t;
  const a = u * u * u, b = 3 * u * u * t, c = 3 * u * t * t, d = t * t * t;
  return [a * P[0][0] + b * P[1][0] + c * P[2][0] + d * P[3][0], a * P[0][1] + b * P[1][1] + c * P[2][1] + d * P[3][1]];
}

/** Small birds along a cubic: a loose skein flying towards the bird, coloured by which side of the seam they are on. */
function skein(P, n, seed, onAlbit, { spread = 14, min = 7, max = 14 } = {}) {
  const r = rng(seed);
  let out = '';
  for (let i = 0; i < n; i += 1) {
    const t = (i + r()) / n;
    const [x, y0] = cubic(P, t);
    const [xa, ya] = cubic(P, Math.min(1, t + 0.01));
    const [xb, yb] = cubic(P, Math.max(0, t - 0.01));
    const heading = (Math.atan2(ya - yb, xa - xb) * 180) / Math.PI;
    const y = y0 + (r() + r() - 1) * spread * (0.5 + t);
    const near = r();
    const c = colourIndex(r());
    const op = Math.round((0.55 + near * 0.45) * 100) / 100;
    out += bird(x, y, min + near * (max - min), heading * 0.8 + (r() - 0.5) * 18, onAlbit(x, y) ? ALBIT.golds[c] : COLOURS[c], op);
  }
  return out;
}

/** The two-tone ×: gold on AlbIT's side of the seam, rust on Birdy's. */
function cross(cx, cy, half, id, clipA, clipB) {
  const lines = `<path d="M${cx - half} ${cy - half}L${cx + half} ${cy + half}M${cx - half} ${cy + half}L${cx + half} ${cy - half}" fill="none" stroke-width="3.6" stroke-linecap="round"/>`;
  return `<g class="bxa-x"><clipPath id="${id}-a">${clipA}</clipPath><clipPath id="${id}-b">${clipB}</clipPath>`
    + `<g clip-path="url(#${id}-a)" stroke="${ALBIT.golds[1]}">${lines}</g><g clip-path="url(#${id}-b)" stroke="${BIRDY.rust}">${lines}</g></g>`;
}

/**
 * One art variant: 'wide' (1600 × 840, AlbIT left, Birdy right, the seam vertical) or 'tall' (800 × 1100, AlbIT on
 * top, Birdy below, the seam horizontal). `wordmarkSrc` is the URL of AlbIT's white wordmark.
 */
export function collabArt(variant, wordmarkSrc) {
  const wide = variant === 'wide';
  const W = wide ? 1600 : 800;
  const H = wide ? 840 : 1100;
  const id = wide ? 'bxa-w' : 'bxa-t';
  // The seam: where AlbIT's field ends and Birdy's paper begins.
  const seam = wide ? 800 : 500;
  const onAlbit = wide ? (x) => x < seam : (_x, y) => y < seam;
  // Wordmark, ×, bird and word, balanced on the seam.
  const wm = wide ? { w: 400, cx: 458, cy: 380 } : { w: 440, cx: 400, cy: 250 };
  wm.h = wm.w * WORDMARK_RATIO;
  const x = wide ? { cx: 800, cy: 380 } : { cx: 400, cy: 500 };
  const s = wide ? 0.66 : 0.62;
  const b = wide ? { cx: 1122, cy: 366 } : { cx: 400, cy: 790 };
  const ox = b.cx - BIRD.cx * s;
  const oy = b.cy - BIRD.cy * s;
  const word = wide ? { x: b.cx, y: b.cy + BIRD.r * s + 78, size: 70 } : { x: b.cx, y: b.cy + BIRD.r * s + 76, size: 64 };

  // Birdy's paper over AlbIT's black, torn along the seam.
  const edge = wide ? tornEdge('x', seam, -10, H + 10, 839) : tornEdge('y', seam, -10, W + 10, 2026);
  const paper = wide
    ? [...edge, [W + 10, H + 10], [W + 10, -10]]
    : [...edge, [W + 10, H + 10], [-10, H + 10]];
  const pts = (list) => list.map(([px, py]) => `${r1(px)},${r1(py)}`).join(' ');

  // The stream across the seam. Wide: a lead-in from the left edge into the cover flock's own trail. Tall: an S from
  // the top left, past the wordmark, over the seam and into the bird's tail.
  let stream;
  if (wide) {
    const trail = FLOCK.birds
      .filter(([cx]) => cx < TRAIL_X)
      .map(([cx, cy, size, rot, c, op = 1]) => {
        const px = ox + cx * s, py = oy + cy * s;
        return bird(px, py, size * s, rot, onAlbit(px, py) ? ALBIT.golds[c] : COLOURS[c], op);
      })
      .join('');
    const start = [ox + 78 * s, oy + 781 * s];
    const lead = skein([[-40, 716], [120, 712], [300, 690], start], 34, 1640, onAlbit, { spread: 12, min: 6, max: 12 });
    stream = lead + trail;
  } else {
    const tail = [ox + TRAIL_X * s, oy + 600 * s];
    stream = skein([[-30, 96], [420, 250], [-150, 640], tail], 104, 720, onAlbit, { spread: 22, min: 7, max: 14 });
  }

  const night = `<radialGradient id="${id}-night" cx="${wide ? 0.3 : 0.5}" cy="${wide ? 0.45 : 0.25}" r="${wide ? 0.6 : 0.75}"><stop offset="0" stop-color="#1D1C1A"/><stop offset=".6" stop-color="${ALBIT.ink}"/><stop offset="1" stop-color="#0A0A0A"/></radialGradient>`;
  const glow = `<radialGradient id="${id}-paper" cx="${wide ? 0.7 : 0.55}" cy="${wide ? 0.35 : 0.68}" r="${wide ? 0.62 : 0.7}"><stop offset="0" stop-color="${BIRDY.peachHi}"/><stop offset=".5" stop-color="${BIRDY.peach}"/><stop offset="1" stop-color="${BIRDY.peachLo}"/></radialGradient>`;
  const clipA = wide ? `<rect x="0" y="0" width="${seam}" height="${H}"/>` : `<rect x="0" y="0" width="${W}" height="${seam}"/>`;
  const clipB = wide ? `<rect x="${seam}" y="0" width="${W - seam}" height="${H}"/>` : `<rect x="0" y="${seam}" width="${W}" height="${H - seam}"/>`;

  return `<svg class="bxa-art bxa-${variant}" viewBox="0 0 ${W} ${H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">`
    + `<defs>${night}${glow}</defs>`
    + `<rect width="${W}" height="${H}" fill="url(#${id}-night)"/>`
    + `<polygon points="${pts(paper)}" fill="url(#${id}-paper)"/>`
    + `<polyline points="${pts(edge)}" fill="none" stroke="#FFF8EE" stroke-opacity=".7" stroke-width="1.4" stroke-linejoin="round"/>`
    + `<g class="bxa-stream">${stream}</g>`
    + `<use href="#bxa-bird" transform="translate(${r1(ox)} ${r1(oy)}) scale(${s})"/>`
    + `<image href="${wordmarkSrc}" x="${r1(wm.cx - wm.w / 2)}" y="${r1(wm.cy - wm.h / 2)}" width="${wm.w}" height="${r1(wm.h)}"/>`
    + `<text class="bxa-word" x="${word.x}" y="${r1(word.y)}" text-anchor="middle" font-size="${word.size}" fill="${BIRDY.rust}">Birdy.</text>`
    + cross(x.cx, x.cy, 30, id, clipA, clipB)
    + '</svg>';
}
