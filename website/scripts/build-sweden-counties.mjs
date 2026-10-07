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
