// Renders public/coverage/coverage-fallback.webp from coverage-europe.geojson in the field journal
// colours (spec §5.6). Shown before the live map loads, without JavaScript, and wherever the map key
// is missing (CI, previews). No map service is needed. Run: npm run assets:coverage
//
// Europe would otherwise float as a rust island on flat beige, so a context layer of neighbouring
// land is drawn underneath it first, from the same pinned Natural Earth 1:50m admin-0 countries
// file the coverage GeoJSON is built from (see scripts/build-coverage-geojson.mjs) — public domain,
// no attribution required.
import sharp from 'sharp';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, dirname } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const gj = JSON.parse(readFileSync(resolve(root, 'public/coverage/coverage-europe.geojson'), 'utf8'));

const NE_SRC = 'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@v5.1.2/geojson/ne_50m_admin_0_countries.geojson';
const CONTEXT_SIMPLIFY_EPS = 0.03; // degrees — context land is backdrop only, coarser than the coverage wash

const W = 1600;
const H = 700;
const PAD = 24;
const WATER = '#D9CCAF';
const FILL = '#9A4526';
const STROKE = '#72301A';
const PAPER = '#F1E8D6';
const CONTEXT_FILL = '#EFE6D3';
const CONTEXT_STROKE = '#D6C9AB';
const BOUNDS = { west: -25, east: 42, south: 34, north: 71.5 };
// Render bounds padded +-5 degrees so land just outside the frame is still there for the img's
// object-fit: cover crop (mobile uses a taller 4:3 box than desktop's 16:7).
const CONTEXT_BOUNDS = { west: BOUNDS.west - 5, east: BOUNDS.east + 5, south: BOUNDS.south - 5, north: BOUNDS.north + 5 };

// Web Mercator, fitted to BOUNDS and centred in the canvas.
const mx = (lon) => (lon * Math.PI) / 180;
const my = (lat) => Math.log(Math.tan(Math.PI / 4 + (lat * Math.PI) / 360));
const x0 = mx(BOUNDS.west);
const x1 = mx(BOUNDS.east);
const y0 = my(BOUNDS.north);
const y1 = my(BOUNDS.south);
const scale = Math.min((W - 2 * PAD) / (x1 - x0), (H - 2 * PAD) / (y0 - y1));
const offX = (W - scale * (x1 - x0)) / 2;
const offY = (H - scale * (y0 - y1)) / 2;
const project = ([lon, lat]) => [offX + (mx(lon) - x0) * scale, offY + (y0 - my(lat)) * scale];

const ringPath = (ring) =>
  ring.map((pt, i) => { const [x, y] = project(pt); return `${i ? 'L' : 'M'}${x.toFixed(1)} ${y.toFixed(1)}`; }).join('') + 'Z';
const pathFromPolygons = (polygons) => polygons.map((poly) => poly.map(ringPath).join('')).join('');

const coveragePolygons = gj.features.flatMap((f) => (f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates));
const coverageD = pathFromPolygons(coveragePolygons);

// --- context land: same pinned Natural Earth file, every polygon part whose bbox intersects
// CONTEXT_BOUNDS, simplified coarser than the coverage wash so the in-memory SVG stays small. ---

// perpendicular distance from point p to segment a-b
function perpDist(p, a, b) {
  const dx = b[0] - a[0], dy = b[1] - a[1];
  if (dx === 0 && dy === 0) return Math.hypot(p[0] - a[0], p[1] - a[1]);
  const t = ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / (dx * dx + dy * dy);
  return Math.hypot(p[0] - (a[0] + t * dx), p[1] - (a[1] + t * dy));
}

// iterative Douglas-Peucker on an open polyline
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

function simplifyContextRing(ring) {
  const rounded = [];
  let prev = null;
  for (const [x, y] of ring) {
    const p = [Math.round(x * 100) / 100, Math.round(y * 100) / 100];
    if (!prev || p[0] !== prev[0] || p[1] !== prev[1]) { rounded.push(p); prev = p; }
  }
  const simplified = douglasPeucker(rounded, CONTEXT_SIMPLIFY_EPS);
  const first = simplified[0], last = simplified[simplified.length - 1];
  if (first && (first[0] !== last[0] || first[1] !== last[1])) simplified.push(first);
  return simplified.length >= 4 ? simplified : rounded;
}

function polygonsOf(geometry) {
  if (geometry.type === 'Polygon') return [geometry.coordinates];
  if (geometry.type === 'MultiPolygon') return geometry.coordinates;
  return [];
}

function bboxOf(ring) {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const [x, y] of ring) {
    if (x < minX) minX = x;
    if (x > maxX) maxX = x;
    if (y < minY) minY = y;
    if (y > maxY) maxY = y;
  }
  return [minX, minY, maxX, maxY];
}

const intersectsContext = ([minX, minY, maxX, maxY]) =>
  minX <= CONTEXT_BOUNDS.east && maxX >= CONTEXT_BOUNDS.west && minY <= CONTEXT_BOUNDS.north && maxY >= CONTEXT_BOUNDS.south;

const neRes = await fetch(NE_SRC);
if (!neRes.ok) throw new Error(`Natural Earth source fetch failed: ${neRes.status}`);
const neGj = await neRes.json();

// A whole-country bbox test breaks for countries whose geometry crosses the antimeridian (e.g.
// the USA: Aleutian islands push its raw bbox to roughly [-178, 179], which "intersects" nearly
// any window). Testing per polygon PART instead of per country sidesteps that, and as a side
// effect only draws the nearby slice of a huge country like Russia rather than all the way to
// the Pacific.
const contextPolygons = [];
for (const f of neGj.features) {
  if (!f.geometry) continue;
  for (const poly of polygonsOf(f.geometry)) {
    if (intersectsContext(bboxOf(poly[0]))) contextPolygons.push(poly.map(simplifyContextRing));
  }
}
const contextD = pathFromPolygons(contextPolygons);

// Decorative sample pins, the same five cities as the live map (not real sightings).
const pins = [[18.07, 59.33], [13.4, 52.52], [2.35, 48.85], [12.49, 41.9], [-3.7, 40.42]].map(project);

const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="${WATER}"/>
  <path d="${contextD}" fill="${CONTEXT_FILL}" fill-rule="evenodd" stroke="${CONTEXT_STROKE}" stroke-width="0.75" stroke-linejoin="round"/>
  <path d="${coverageD}" fill="${FILL}" fill-opacity="0.55" fill-rule="evenodd" stroke="${STROKE}" stroke-width="1.2" stroke-linejoin="round"/>
  ${pins.map(([x, y]) => `<circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="11" fill="${PAPER}" stroke="${FILL}" stroke-width="4"/>`).join('')}
</svg>`;

await sharp(Buffer.from(svg)).webp({ quality: 82 }).toFile(resolve(root, 'public/coverage/coverage-fallback.webp'));
console.log(`coverage-fallback.webp ${W}x${H}`);
