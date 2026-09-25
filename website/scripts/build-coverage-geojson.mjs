#!/usr/bin/env node
// Builds public/coverage/coverage-europe.geojson from Natural Earth's SWEDISH POINT OF VIEW
// 1:10m admin-0 countries dataset (https://www.naturalearthdata.com — public domain; "No
// permission is required to use Natural Earth. Crediting the authors is unnecessary." per its
// terms of use), pinned to the v5.1.2 tag on jsDelivr so the build is reproducible. We use the
// Swedish point-of-view file specifically: the default file draws de facto borders, so Crimea
// (Simferopol, Sevastopol, Kerch) renders as part of Russia there — and since Russia is out of
// scope, Crimea would show as a paper-coloured hole cut out of rust Ukraine. Not acceptable on a
// Swedish company's site. The Swedish file draws Crimea inside Ukraine and treats Cyprus as one
// island (no separate "Northern Cyprus" feature). The live map's rust overlay is built from this
// same output file, not just the static fallback. Keeps Birdy's core-Europe countries plus a
// carved-out Kaliningrad (see below), drops overseas exclaves (French Guiana, the Caribbean,
// Réunion, Mayotte, ...), rounds coordinates to 2 decimals, dedupes, then Douglas-Peucker
// simplifies each ring (the copper wash renders at maxZoom 8 / 0.42 opacity — country borders
// don't need full vertex density, so we trim the payload hard).
import { writeFileSync, mkdirSync } from 'node:fs';

const SRC = 'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@v5.1.2/geojson/ne_10m_admin_0_countries_swe.geojson';

// Natural Earth's CONTINENT field covers Birdy's Europe scope except Cyprus, which it files
// under Asia — added back explicitly. Russia is Europe in Natural Earth but out of scope for
// Birdy; its Kaliningrad exclave is carved out and re-added separately, below.
const EUROPE_EXTRA_INCLUDE = new Set(['Cyprus']);
const EUROPE_EXCLUDE = new Set(['Russia']);
// Regression guard: none of these may ever appear in the output, whichever set let them through.
const MUST_EXCLUDE = ['Russia', 'Turkey', 'Israel', 'Armenia', 'Azerbaijan', 'Georgia'];
const MUST_INCLUDE = ['Sweden', 'France', 'Germany', 'Spain', 'Poland', 'Italy', 'United Kingdom', 'Kosovo', 'Cyprus', 'Åland', 'Kaliningrad'];
// 1:10m has far more vertex density than the 1:50m file this used to be built from. At the live
// map's zoom range (1-8) country borders don't need 10m detail, so this is simplified hard —
// tuned empirically to land the output at <= 160 KB (see the size check at the bottom).
const SIMPLIFY_EPS = 0.06; // degrees (~6.6 km at Europe's latitudes)

// Overseas parts bundled into a European country's (Multi)Polygon (French Guiana, the
// Caribbean, Réunion, Mayotte, ...) read as noise on a Europe map. Any polygon whose bbox
// centre falls outside this box is dropped; it still keeps the Azores, Madeira, the Canaries,
// Svalbard and Jan Mayen, which all sit inside it.
const OVERSEAS_BOUNDS = { west: -32, east: 45, south: 27, north: 82 };

// Kaliningrad: Russia's exclave between Poland and Lithuania. Russia is excluded entirely (out
// of scope), but leaving Kaliningrad out too would tear a paper-coloured hole in the middle of
// covered Europe, so it is carved out of Russia's MultiPolygon and shipped as its own feature.
//
// A plain "centroid west of 30 degrees E" test sweeps in two unwanted things: Chukotka fragments
// across the antimeridian record as very negative longitude (e.g. -178), which reads as "west of
// 30" numerically despite being Russia's Far East; and a handful of small Russian islands near
// the Finnish border, around 27-30 E / 60 N, which are genuinely west of 30 but are not
// Kaliningrad. Fixed by (a) unwrapping longitudes below -90 by adding 360 before the threshold
// test, and (b) also requiring latitude < 58 (Kaliningrad sits at ~54.3-55.3 N; the Finland-
// border islands sit at ~60 N). Verified against the raw source: exactly one polygon part
// matches, bbox 19.6-22.8 E / 54.3-55.3 N — the real Kaliningrad Oblast, nothing else.
const unwrapLon = (lon) => (lon < -90 ? lon + 360 : lon);
const isKaliningradPart = ([lon, lat]) => unwrapLon(lon) < 30 && lat < 58;

const round = (n) => Math.round(n * 100) / 100;

// perpendicular distance from point p to segment a–b
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

function closeRing(points) {
  const first = points[0], last = points[points.length - 1];
  if (first && (first[0] !== last[0] || first[1] !== last[1])) points.push(first);
  return points;
}

function roundRing(ring) {
  const rounded = [];
  let prev = null;
  for (const [x, y] of ring) {
    const p = [round(x), round(y)];
    if (!prev || p[0] !== prev[0] || p[1] !== prev[1]) { rounded.push(p); prev = p; }
  }
  const simplified = closeRing(douglasPeucker(rounded, SIMPLIFY_EPS));
  if (simplified.length >= 4) return simplified;
  // Simplification collapsed it below a valid ring (4 points incl. the closing point) --
  // fall back to the deduped-but-unrounded ring.
  if (rounded.length >= 4) return rounded;
  // 1:10m resolves landmasses smaller than the ~1.1 km rounding grid (e.g. Vatican: 7 raw
  // points, all identical once rounded to 2 decimals) -- rounding itself destroyed the shape,
  // not simplification. Fall back further to full-precision coordinates, still simplified at
  // the same eps, so a country doesn't silently vanish or ship an invalid <4-point ring.
  const fullPrecision = closeRing(douglasPeucker(ring, SIMPLIFY_EPS));
  return fullPrecision.length >= 4 ? fullPrecision : ring;
}

function simplifyGeom(g) {
  if (g.type === 'Polygon') g.coordinates = g.coordinates.map(roundRing);
  else if (g.type === 'MultiPolygon') g.coordinates = g.coordinates.map((poly) => poly.map(roundRing));
  return g;
}

function polygonsOf(geometry) {
  if (geometry.type === 'Polygon') return [geometry.coordinates];
  if (geometry.type === 'MultiPolygon') return geometry.coordinates;
  return [];
}

function bboxCentroid(ring) {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  for (const [x, y] of ring) {
    if (x < minX) minX = x;
    if (x > maxX) maxX = x;
    if (y < minY) minY = y;
    if (y > maxY) maxY = y;
  }
  return [(minX + maxX) / 2, (minY + maxY) / 2];
}

const inOverseasBounds = ([lon, lat]) =>
  lon >= OVERSEAS_BOUNDS.west && lon <= OVERSEAS_BOUNDS.east && lat >= OVERSEAS_BOUNDS.south && lat <= OVERSEAS_BOUNDS.north;

// Drops any polygon (island/exclave) of a (Multi)Polygon whose bbox centre falls outside
// OVERSEAS_BOUNDS, e.g. France's French Guiana/Martinique/Guadeloupe/Réunion/Mayotte parts.
function dropOverseas(geometry, name) {
  const kept = polygonsOf(geometry).filter((poly) => inOverseasBounds(bboxCentroid(poly[0])));
  if (kept.length === 0) throw new Error(`dropOverseas removed every part of ${name}`);
  return geometry.type === 'Polygon' ? { type: 'Polygon', coordinates: kept[0] } : { type: 'MultiPolygon', coordinates: kept };
}

// --- point-in-polygon, for the regression guards at the bottom (ray casting, even-odd across a
// Polygon's own rings so holes are respected). ---
function pointInRing([x, y], ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const [xi, yi] = ring[i], [xj, yj] = ring[j];
    const crosses = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi;
    if (crosses) inside = !inside;
  }
  return inside;
}
function pointInPolygon(pt, rings) {
  if (!pointInRing(pt, rings[0])) return false;
  for (let i = 1; i < rings.length; i++) if (pointInRing(pt, rings[i])) return false;
  return true;
}
function pointInGeometry(pt, geometry) {
  return polygonsOf(geometry).some((poly) => pointInPolygon(pt, poly));
}

const res = await fetch(SRC);
if (!res.ok) throw new Error(`source fetch failed: ${res.status}`);
const gj = await res.json();

const europeFeatures = gj.features.filter((f) => {
  const name = f.properties?.NAME;
  if (!f.geometry) return false;
  if (EUROPE_EXTRA_INCLUDE.has(name)) return true;
  return f.properties?.CONTINENT === 'Europe' && !EUROPE_EXCLUDE.has(name);
});

// Carve Kaliningrad out of Russia's (excluded) MultiPolygon.
const russia = gj.features.find((f) => f.properties?.NAME === 'Russia');
if (!russia) throw new Error('Russia feature not found in source (needed to derive Kaliningrad)');
const kaliningradParts = polygonsOf(russia.geometry).filter((poly) => isKaliningradPart(bboxCentroid(poly[0])));
if (kaliningradParts.length !== 1) {
  throw new Error(`expected exactly 1 Kaliningrad part in the source, found ${kaliningradParts.length}`);
}
// Adjusted exclusion check (name-based alone isn't enough once we deliberately keep a
// Russia-derived feature under a different name): every part we are about to ship as
// "Kaliningrad" must itself be west of 30 E, i.e. nothing from mainland Russia leaked in.
for (const poly of kaliningradParts) {
  const centroid = bboxCentroid(poly[0]);
  if (unwrapLon(centroid[0]) >= 30) throw new Error('Kaliningrad extraction leaked a part east of 30E (mainland Russia)');
}
const kaliningradFeature = {
  type: 'Feature',
  properties: { NAME: 'Kaliningrad' },
  geometry: { type: 'MultiPolygon', coordinates: kaliningradParts },
};

const outFeatures = [...europeFeatures, kaliningradFeature].map((f) => ({
  type: 'Feature',
  properties: { name: f.properties.NAME },
  geometry: simplifyGeom(dropOverseas(f.geometry, f.properties.NAME)),
}));

const names = new Set(outFeatures.map((f) => f.properties.name));
for (const must of MUST_INCLUDE) if (!names.has(must)) throw new Error(`expected country missing: ${must}`);
for (const no of MUST_EXCLUDE) if (names.has(no)) throw new Error(`country should be excluded: ${no}`);

// Regression guards on the FINAL (rounded + simplified) output, so a future eps bump can't
// silently reopen the Crimea hole or thin Kaliningrad down to nothing.
const SIMFEROPOL = [34.1, 44.95]; // Crimea must render as part of Ukraine, not a hole in it
const KALININGRAD_CITY = [20.51, 54.71]; // the exclave must actually be covered
const MOSCOW = [37.62, 55.75]; // mainland Russia must never be covered, under any name

const ukraineOut = outFeatures.find((f) => f.properties.name === 'Ukraine');
if (!ukraineOut || !pointInGeometry(SIMFEROPOL, ukraineOut.geometry)) {
  throw new Error('guard failed: Simferopol/Crimea is not inside the Ukraine feature');
}
if (!outFeatures.some((f) => pointInGeometry(KALININGRAD_CITY, f.geometry))) {
  throw new Error('guard failed: Kaliningrad city is not covered by any feature');
}
if (outFeatures.some((f) => pointInGeometry(MOSCOW, f.geometry))) {
  throw new Error('guard failed: Moscow is covered by a feature (mainland Russia leaked in)');
}

mkdirSync(new URL('../public/coverage/', import.meta.url), { recursive: true });
const out = new URL('../public/coverage/coverage-europe.geojson', import.meta.url);
const json = JSON.stringify({ type: 'FeatureCollection', features: outFeatures });
writeFileSync(out, json);
console.log(`wrote ${outFeatures.length} features, ${(json.length / 1024).toFixed(0)} KB -> public/coverage/coverage-europe.geojson (eps=${SIMPLIFY_EPS})`);
