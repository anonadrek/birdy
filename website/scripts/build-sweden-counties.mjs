#!/usr/bin/env node
// Builds src/data/sweden-counties.json: Sweden's 21 counties as SVG paths for the species pages'
// county map (spec 2026-09-25 §5). Source: Natural Earth 1:10m admin-1 (public domain, "No
// permission is required to use Natural Earth"), pinned to the v5.1.2 tag. raw.githubusercontent
// rather than jsDelivr, because the file (~40 MB) is over jsDelivr's size limit, and its bytes are
// verified against a pinned SHA-256 before parsing (the pinned tag alone does not guarantee the
// bytes behind the URL never change). Keys are ISO 3166-2:SE codes (SE-AB ...), the same codes the
// pipeline writes into data.counties.
// Run: npm run assets:counties   (writes the JSON; commit it, the build never downloads anything)
import { createHash } from 'node:crypto';
import { writeFileSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const SRC = 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_admin_1_states_provinces.geojson';
const SRC_SHA256 = '22d0e3ad85eb3e27f17cabf8ba2d50e554fbc27a87796ff891d958185da62fb5';
const OUT = resolve(root, 'src/data/sweden-counties.json');
const WIDTH = 300; // SVG user units; the page scales the map with CSS
// Sinusoidal projection, x = (lon - 15) * cos(lat): a single cos(62°) factor (the old approach)
// projected every point at the same latitude, so it squeezed Skåne (lat ~56°) and bloated
// Norrbotten (lat ~67°) by about 1.48x relative to each other. Projecting each point at its own
// latitude keeps Sweden's north-south proportions recognisable without adding a dependency.
const CENTRAL_LON = 15; // degrees E, roughly Sweden's own meridian
const SIMPLIFY_EPS = 0.025; // degrees (~2.8 km): enough for a 200 to 300 px wide map
const MIN_PART_AREA = 0.02; // square degrees: drops skerries, keeps Öland, Gotland and the big islands
const MAX_BYTES = 80 * 1024;
// The same 21 codes the fixtures hard-code (tests/fixtures/make-species-fixtures.mjs's
// COUNTY_CODES) -- every one of them, exactly, must come out of the source file.
const EXPECTED_CODES = ['SE-AB', 'SE-AC', 'SE-BD', 'SE-C', 'SE-D', 'SE-E', 'SE-F', 'SE-G', 'SE-H', 'SE-I', 'SE-K', 'SE-M', 'SE-N', 'SE-O', 'SE-S', 'SE-T', 'SE-U', 'SE-W', 'SE-X', 'SE-Y', 'SE-Z'];

// Reads the source bytes, either downloaded or from a local --src copy (used when testing the
// script), and returns them alongside a label for error messages. Both paths are hash-verified
// below -- a local --src file is not a trusted shortcut around the integrity check.
async function loadSourceBytes() {
  const srcArg = process.argv.indexOf('--src');
  if (srcArg > -1) {
    const path = process.argv[srcArg + 1];
    return { bytes: readFileSync(path), label: path };
  }
  const res = await fetch(SRC, { signal: AbortSignal.timeout(120_000) });
  if (!res.ok) throw new Error(`källfilens hämtning misslyckades: ${res.status} ${res.statusText}`);
  return { bytes: Buffer.from(await res.arrayBuffer()), label: SRC };
}

const { bytes, label } = await loadSourceBytes();
const digest = createHash('sha256').update(bytes).digest('hex');
if (digest !== SRC_SHA256) {
  throw new Error(`${label}: SHA-256 är ${digest}, väntade ${SRC_SHA256} (källfilen har ändrats eller är fel fil)`);
}
const geo = JSON.parse(bytes.toString('utf8'));

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

const project = ([lon, lat]) => [(lon - CENTRAL_LON) * Math.cos((lat * Math.PI) / 180), -lat];
const polygonsOf = (g) => (g.type === 'Polygon' ? [g.coordinates] : g.coordinates);
let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
const counties = features.map((f) => {
  const parts = polygonsOf(f.geometry).map((poly) => poly[0]); // outer rings only, counties have no holes worth drawing
  const largest = Math.max(...parts.map(ringArea));
  const kept = parts
    .filter((ring) => ringArea(ring) >= MIN_PART_AREA || ringArea(ring) === largest)
    .map((ring) => douglasPeucker(ring, SIMPLIFY_EPS).map(project))
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
    .sort((a, b) => (a.code < b.code ? -1 : a.code > b.code ? 1 : 0)),
};

const codes = out.counties.map((c) => c.code);
const codeSet = new Set(codes);
const codesMatch = codeSet.size === EXPECTED_CODES.length && EXPECTED_CODES.every((c) => codeSet.has(c));
if (!codesMatch) {
  const missing = EXPECTED_CODES.filter((c) => !codeSet.has(c));
  const extra = codes.filter((c) => !EXPECTED_CODES.includes(c));
  throw new Error(`Länskoderna matchar inte den väntade listan. Saknas: ${missing.length ? missing.join(', ') : 'inga'}. Oväntade: ${extra.length ? extra.join(', ') : 'inga'}`);
}
for (const c of out.counties) if (!c.d) throw new Error(`Länet ${c.code} fick ingen bana (d är tom)`);

const json = JSON.stringify(out);
if (json.length > MAX_BYTES) {
  throw new Error(`Kartfilen är över ${(MAX_BYTES / 1024).toFixed(0)} KB (${(json.length / 1024).toFixed(1)} KB), höj SIMPLIFY_EPS`);
}
writeFileSync(OUT, `${json}\n`);
console.log(`sweden-counties: ${out.counties.length} län, viewBox ${out.viewBox}, ${(json.length / 1024).toFixed(1)} KB -> ${OUT}`);
