#!/usr/bin/env node
// Builds public/coverage/coverage-europe.geojson from a public Europe dataset:
// keep Birdy's core-Europe countries, drop bbox-clipped/non-core ones, round
// coordinates to 2 decimals, dedupe, then Douglas-Peucker simplify each ring
// (the copper wash renders at maxZoom 8 / 0.42 opacity — country borders don't
// need full vertex density, so we trim the payload hard).
import { writeFileSync, mkdirSync } from 'node:fs';

const SRC = 'https://cdn.jsdelivr.net/gh/leakyMirror/map-of-europe@master/GeoJSON/europe.geojson';
const EXCLUDE = new Set(['Russia', 'Turkey', 'Israel', 'Armenia', 'Azerbaijan', 'Georgia']);
const MUST_INCLUDE = ['Sweden', 'France', 'Germany', 'Spain', 'Poland', 'Italy', 'United Kingdom'];
const SIMPLIFY_EPS = 0.02; // degrees (~2 km) — invisible at country-wash zoom

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

const res = await fetch(SRC);
if (!res.ok) throw new Error(`source fetch failed: ${res.status}`);
const gj = await res.json();

gj.features = gj.features
  .filter((f) => f.geometry && !EXCLUDE.has(f.properties?.NAME))
  .map((f) => ({ type: 'Feature', properties: { name: f.properties.NAME }, geometry: simplifyGeom(f.geometry) }));

const names = new Set(gj.features.map((f) => f.properties.name));
for (const must of MUST_INCLUDE) if (!names.has(must)) throw new Error(`expected country missing: ${must}`);
for (const no of EXCLUDE) if (names.has(no)) throw new Error(`country should be excluded: ${no}`);

mkdirSync(new URL('../public/coverage/', import.meta.url), { recursive: true });
const out = new URL('../public/coverage/coverage-europe.geojson', import.meta.url);
const json = JSON.stringify({ type: 'FeatureCollection', features: gj.features });
writeFileSync(out, json);
console.log(`wrote ${gj.features.length} features, ${(json.length / 1024).toFixed(0)} KB -> public/coverage/coverage-europe.geojson`);
