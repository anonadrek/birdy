// The Premium band's picture (Albin 2026-10-10, direction P3 "The flock around the year"): Birdy's whole flock, the
// 839 birds of the home hero (one per species in the app), flying one full circle, a year, around the Premium seal.
// Brass gathers where the year is now, and one bird is lit for today: the build's date places it, so the nightly
// build moves it a day at a time. Pure and seeded, so a day always gives the same markup (the page, the tests).
import { MARK } from '../components/hero/flock-data.mjs';
import { COLOURS, LIT, markMatrix, rng } from '../components/hero/flock.mjs';

/** The drawing's own square, the ring's centre and radius, how wide the flock spreads across it, and the months' ring. */
export const RING = { size: 480, cx: 240, cy: 240, r: 170, band: 50, labelR: 224 };
/** One bird per species in Birdy, the same number as the home hero's flock. */
export const BIRDS = 839;
/** The month initials, January at the top, clockwise; the same letters in Swedish and English. */
export const MONTHS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];
/** Brass first (the light and the deep brass), then the flock's copper, rust and bark. */
const PALETTE = ['#E2C07E', COLOURS[0], COLOURS[1], COLOURS[2], COLOURS[3]];
/** Near today the flock turns brass; elsewhere it keeps the flock's own colours with a little brass in it. */
const NEAR = 0.07;
const WEIGHTS_NEAR = [0.34, 0.4, 0.16, 0.07, 0.03];
const WEIGHTS_FAR = [0.03, 0.13, 0.5, 0.24, 0.1];

const r1 = (n) => Math.round(n * 10) / 10;

/**
 * Where in the year a day falls, from 0 at the start of 1 January to just under 1 at the end of 31 December (the middle
 * of the day, so 1 January is not at the very top).
 * @param {string} iso YYYY-MM-DD
 */
export function yearFraction(iso) {
  const m = String(iso).match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) throw new Error(`yearFraction: inte ett datum: ${iso}`);
  const [year, month, day] = [Number(m[1]), Number(m[2]), Number(m[3])];
  const start = Date.UTC(year, 0, 1);
  const days = (Date.UTC(year + 1, 0, 1) - start) / 86400000;
  const index = (Date.UTC(year, month - 1, day) - start) / 86400000;
  return (index + 0.5) / days;
}

/** A point on the ring at a fraction of the year (0 at the top, clockwise) and a distance from the centre. */
function at(fraction, radius) {
  const a = fraction * Math.PI * 2 - Math.PI / 2;
  return [RING.cx + radius * Math.cos(a), RING.cy + radius * Math.sin(a), a];
}

/**
 * The ring of the year as SVG markup: 839 birds grouped by colour, the lit bird for the day, the month initials with
 * the current month in rust. Decorative (aria-hidden); the figure around it carries the description.
 * @param {{ iso: string }} input the build's date (virtual:birdy-daily-bird's date.iso)
 * @returns {string}
 */
export function yearFlockSvg({ iso }) {
  const now = yearFraction(iso);
  const r = rng(20261010);
  const groups = PALETTE.map(() => []);
  // Every bird but today's, which is drawn lit on its own: 839 in all, one per species.
  for (let i = 0; i < BIRDS - 1; i++) {
    const u = (i + r() * 0.9) / (BIRDS - 1);
    // Across the ring: three uniforms summed, so the flock is dense at the ring and thins at its edges.
    let off = ((r() + r() + r() - 1.5) / 1.5) * (RING.band / 2);
    const d = Math.abs(((u - now + 1.5) % 1) - 0.5);
    // Leave room for the lit bird's disc: the birds right by it fly just inside or just outside it.
    if (d < 0.012 && Math.abs(off) < 14) off = off < 0 ? off - 14 : off + 14;
    const [x, y, a] = at(u, RING.r + off);
    const size = 9 + r() * 8;
    // Flying clockwise along the ring: the mark faces along the tangent, each a little off it.
    const rot = (a * 180) / Math.PI + 90 + (r() - 0.5) * 30;
    const weights = d < NEAR ? WEIGHTS_NEAR : WEIGHTS_FAR;
    const pick = r();
    let c = 0;
    for (let acc = weights[0]; c < weights.length - 1 && pick >= acc; acc += weights[++c]);
    groups[c].push(`<use href="#yf-mark" transform="${markMatrix(x, y, size, rot)}"/>`);
  }
  const birds = groups.map((uses, c) => `<g fill="${PALETTE[c]}">${uses.join('')}</g>`).join('');
  const [lx, ly] = at(now, RING.r);
  const lit = `<circle cx="${r1(lx)}" cy="${r1(ly)}" r="17" fill="${LIT.disc}" stroke="${LIT.ring}" stroke-width="2.5"/>`
    + `<use href="#yf-mark" fill="${LIT.bird}" transform="${markMatrix(lx, ly, 22, -8)}"/>`;
  // The month from the date itself: twelve equal sectors would put 1 October in September.
  const month = Number(iso.slice(5, 7)) - 1;
  const labels = MONTHS.map((letter, m) => {
    const [x, y] = at((m + 0.5) / 12, RING.labelR);
    return `<text x="${r1(x)}" y="${r1(y + 4)}"${m === month ? ' class="yf-now"' : ''}>${letter}</text>`;
  }).join('');
  return `<svg class="yf" viewBox="0 0 ${RING.size} ${RING.size}" aria-hidden="true" focusable="false">`
    + `<defs><path id="yf-mark" d="${MARK.path}"/></defs>`
    + `<g class="yf-birds">${birds}</g><g class="yf-lit">${lit}</g><g class="yf-months">${labels}</g></svg>`;
}
