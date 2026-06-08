#!/usr/bin/env node
// Builds public/coverage/coverage-europe.geojson from a public Europe dataset:
// keep Birdy's core-Europe countries, drop bbox-clipped/non-core ones, round
// coordinates to 2 decimals (country-level wash doesn't need more), dedupe.
import { writeFileSync, mkdirSync } from 'node:fs';

const SRC = 'https://cdn.jsdelivr.net/gh/leakyMirror/map-of-europe@master/GeoJSON/europe.geojson';
const EXCLUDE = new Set(['Russia', 'Turkey', 'Israel', 'Armenia', 'Azerbaijan', 'Georgia']);
const MUST_INCLUDE = ['Sweden', 'France', 'Germany', 'Spain', 'Poland', 'Italy', 'United Kingdom'];

const round = (n) => Math.round(n * 100) / 100;
function roundRing(ring) {
  const out = [];
  let prev = null;
  for (const [x, y] of ring) {
    const p = [round(x), round(y)];
    if (!prev || p[0] !== prev[0] || p[1] !== prev[1]) { out.push(p); prev = p; }
  }
  return out;
}
function roundGeom(g) {
  if (g.type === 'Polygon') g.coordinates = g.coordinates.map(roundRing);
  else if (g.type === 'MultiPolygon') g.coordinates = g.coordinates.map((poly) => poly.map(roundRing));
  return g;
}

const res = await fetch(SRC);
if (!res.ok) throw new Error(`source fetch failed: ${res.status}`);
const gj = await res.json();

gj.features = gj.features
  .filter((f) => f.geometry && !EXCLUDE.has(f.properties?.NAME))
  .map((f) => ({ type: 'Feature', properties: { name: f.properties.NAME }, geometry: roundGeom(f.geometry) }));

const names = new Set(gj.features.map((f) => f.properties.name));
for (const must of MUST_INCLUDE) if (!names.has(must)) throw new Error(`expected country missing: ${must}`);
for (const no of EXCLUDE) if (names.has(no)) throw new Error(`country should be excluded: ${no}`);

mkdirSync(new URL('../public/coverage/', import.meta.url), { recursive: true });
const out = new URL('../public/coverage/coverage-europe.geojson', import.meta.url);
writeFileSync(out, JSON.stringify({ type: 'FeatureCollection', features: gj.features }));
console.log(`wrote ${gj.features.length} features -> public/coverage/coverage-europe.geojson`);
