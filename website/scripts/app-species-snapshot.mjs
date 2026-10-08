#!/usr/bin/env node
// Writes src/data/app-species-<version>.json, the shipped app's species list the home page picks Dagens fågel from
// (plan 2026-10-08 Task 1, review C1), from the golden file the app's own Kotlin code wrote
// (tests/fixtures/daily-bird-golden.json, shared/domain/src/jvmTest/.../DailyBirdGoldenGenerator.kt). Run at every app
// release, after regenerating the golden on the release branch and bumping APP_VERSION in src/lib/release.mjs:
//   npm run app-species:snapshot
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { APP_VERSION, appSpeciesSnapshotPath } from '../src/lib/release.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const golden = JSON.parse(readFileSync(resolve(root, 'tests/fixtures/daily-bird-golden.json'), 'utf8'));
const fields = ['id', 'abundance', 'iucn_status', 'regions', 'season'];
const species = golden.species
  .map((s) => Object.fromEntries(fields.map((f) => [f, s[f]])))
  .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
for (const s of species) {
  for (const f of fields) if (s[f] === undefined) throw new Error(`golden-filen: ${s.id} saknar ${f}`);
}
const about = `The species list of Birdy ${APP_VERSION} as shipped: what the home page picks Dagens fågel from (src/lib/daily-bird.mjs, loadAppSpeciesSnapshot). Written by scripts/app-species-snapshot.mjs from tests/fixtures/daily-bird-golden.json, which the app's own Kotlin code wrote over the app's species.db. Not the branch's YAML: npm run check:app-species tells whether the two agree. Regenerate at every app release.`;
const out = `{\n  "_about": ${JSON.stringify(about)},\n  "app": ${JSON.stringify(APP_VERSION)},\n  "species": [\n${species.map((s) => `    ${JSON.stringify(s)}`).join(',\n')}\n  ]\n}\n`;
JSON.parse(out);
const file = appSpeciesSnapshotPath(root);
writeFileSync(file, out);
console.log(`${relative(root, file)}: ${species.length} arter ur golden-filen (Birdy ${APP_VERSION})`);
