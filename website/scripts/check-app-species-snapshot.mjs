#!/usr/bin/env node
// Checks that the branch's species YAML (shared/content/species/) and the shipped app's frozen species list
// (src/data/app-species-<version>.json, see src/lib/release.mjs) agree on the fields Dagens fågel is picked from
// (plan 2026-10-08 Task 1, review C1). A release-checklist step (Plan 3 Task 11 on release/1.3.0): on the release
// branch, and on main once the release is merged, this must be green. The build itself never runs it, so a branch
// whose YAML is ahead of or behind the app still builds, picking from the app's list.
//   npm run check:app-species
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadAppSpecies, loadAppSpeciesSnapshot } from '../src/lib/daily-bird.mjs';
import { APP_VERSION } from '../src/lib/release.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const fields = ['abundance', 'iucn_status', 'regions', 'season'];
const byId = (list) => new Map(list.map((s) => [s.id, s]));
const yaml = byId(loadAppSpecies(root));
const app = byId(loadAppSpeciesSnapshot(root));

const diffs = [];
for (const id of [...yaml.keys()].filter((q) => !app.has(q)).sort()) diffs.push(`${id}: finns i YAML men inte i appens lista`);
for (const id of [...app.keys()].filter((q) => !yaml.has(q)).sort()) diffs.push(`${id}: finns i appens lista men inte i YAML`);
for (const id of [...app.keys()].filter((q) => yaml.has(q)).sort()) {
  const a = yaml.get(id);
  const b = app.get(id);
  for (const f of fields) {
    const [x, y] = [JSON.stringify(a[f]), JSON.stringify(b[f])];
    if (x !== y) diffs.push(`${id}: ${f} ${x} i YAML, ${y} i appen`);
  }
}

if (diffs.length) {
  console.error(`check-app-species FAILED: grenens YAML (shared/content/species/) och appens lista (src/data/app-species-${APP_VERSION}.json) skiljer sig på ${diffs.length} punkter:`);
  for (const d of diffs) console.error(`  ${d}`);
  console.error(`Sajten bygger ändå, ur appens lista. På main är det väntat tills release-grenen är sammanslagen (release/1.3.0 satte tre mesar till ovanlig i 12c7526f). På release-grenen ska det vara noll: efter en ändring i appens arter, kör golden-generatorn där och sedan npm run app-species:snapshot.`);
  process.exit(1);
}
console.log(`check-app-species OK (${app.size} arter, grenens YAML och appens lista ${APP_VERSION} är samma)`);
