#!/usr/bin/env node
// Builds public/coverage/coverage-europe.geojson from Natural Earth's 1:50m admin-0 countries
// dataset (https://www.naturalearthdata.com — public domain; "No permission is required to use
// Natural Earth. Crediting the authors is unnecessary." per its terms of use), pinned to the
// v5.1.2 tag on jsDelivr so the build is reproducible. Keeps Birdy's core-Europe countries, drops
// overseas exclaves (French Guiana, the Caribbean, Réunion, Mayotte, ...), rounds coordinates to
// 2 decimals, dedupes, then Douglas-Peucker simplifies each ring (the copper wash renders at
// maxZoom 8 / 0.42 opacity — country borders don't need full vertex density, so we trim the
// payload hard).
import { writeFileSync, mkdirSync } from 'node:fs';

const SRC = 'https://cdn.jsdelivr.net/gh/nvkelso/natural-earth-vector@v5.1.2/geojson/ne_50m_admin_0_countries.geojson';

// Natural Earth's CONTINENT field covers Birdy's Europe scope except Cyprus and Northern Cyprus,
// which it files under Asia — added back explicitly. Russia is Europe in Natural Earth but out
// of scope for Birdy, so it is the one Europe country actively excluded.
const EUROPE_EXTRA_INCLUDE = new Set(['Cyprus', 'N. Cyprus']);
const EUROPE_EXCLUDE = new Set(['Russia']);
// Regression guard: none of these may ever appear in the output, whichever set let them through.
const MUST_EXCLUDE = ['Russia', 'Turkey', 'Israel', 'Armenia', 'Azerbaijan', 'Georgia'];
const MUST_INCLUDE = ['Sweden', 'France', 'Germany', 'Spain', 'Poland', 'Italy', 'United Kingdom', 'Kosovo', 'Cyprus', 'N. Cyprus', 'Åland'];
const SIMPLIFY_EPS = 0.02; // degrees (~2 km) — invisible at country-wash zoom

// Overseas parts bundled into a European country's (Multi)Polygon (French Guiana, the
// Caribbean, Réunion, Mayotte, ...) read as noise on a Europe map. Any polygon whose bbox
// centre falls outside this box is dropped; it still keeps the Azores, Madeira, the Canaries,
// Svalbard and Jan Mayen, which all sit inside it.
const OVERSEAS_BOUNDS = { west: -32, east: 45, south: 27, north: 82 };

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

function roundRing(ring) {
  const rounded = [];
  let prev = null;
  for (const [x, y] of ring) {
    const p = [round(x), round(y)];
    if (!prev || p[0] !== prev[0] || p[1] !== prev[1]) { rounded.push(p); prev = p; }
  }
  const simplified = douglasPeucker(rounded, SIMPLIFY_EPS);
  // keep the ring closed; fall back to the rounded ring if simplification
  // collapsed it below a valid polygon (4 points incl. the closing point)
  const first = simplified[0], last = simplified[simplified.length - 1];
  if (first && (first[0] !== last[0] || first[1] !== last[1])) simplified.push(first);
  return simplified.length >= 4 ? simplified : rounded;
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

const res = await fetch(SRC);
if (!res.ok) throw new Error(`source fetch failed: ${res.status}`);
const gj = await res.json();

gj.features = gj.features
  .filter((f) => {
    const name = f.properties?.NAME;
    if (!f.geometry) return false;
    if (EUROPE_EXTRA_INCLUDE.has(name)) return true;
    return f.properties?.CONTINENT === 'Europe' && !EUROPE_EXCLUDE.has(name);
  })
  .map((f) => ({
    type: 'Feature',
    properties: { name: f.properties.NAME },
    geometry: simplifyGeom(dropOverseas(f.geometry, f.properties.NAME)),
  }));

const names = new Set(gj.features.map((f) => f.properties.name));
for (const must of MUST_INCLUDE) if (!names.has(must)) throw new Error(`expected country missing: ${must}`);
for (const no of MUST_EXCLUDE) if (names.has(no)) throw new Error(`country should be excluded: ${no}`);

mkdirSync(new URL('../public/coverage/', import.meta.url), { recursive: true });
const out = new URL('../public/coverage/coverage-europe.geojson', import.meta.url);
const json = JSON.stringify({ type: 'FeatureCollection', features: gj.features });
writeFileSync(out, json);
console.log(`wrote ${gj.features.length} features, ${(json.length / 1024).toFixed(0)} KB -> public/coverage/coverage-europe.geojson`);
