// Where the species pages read their data, and which records get a page (spec 2026-09-25 §14).
// Plain JS so astro.config.mjs, content.config.ts, src/lib/species.ts and the check scripts share one rule.
//   SPECIES_FIXTURES=1  read the test data in tests/fixtures/ instead of src/data/ and src/assets/species/
//   SPECIES_PREVIEW=1   also build verified pages that are not published yet (Vercel Preview)
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const useFixtures = () => process.env.SPECIES_FIXTURES === '1';
export const isPreview = () => process.env.SPECIES_PREVIEW === '1';

/** Folders relative to the website root. */
export const speciesDir = () => (useFixtures() ? 'tests/fixtures/species' : 'src/data/species');
export const comparisonsDir = () => (useFixtures() ? 'tests/fixtures/comparisons' : 'src/data/comparisons');
export const assetsDir = () => (useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species');

/** A species page exists when its text is written (ok), its facts are verified (spec Revision 2026-10-05), and it is published or this is a preview build. */
export function isSpeciesBuilt(record, preview = isPreview()) {
  return record.status === 'ok' && Boolean(record.verification) && (record.publish === true || preview);
}

/** A comparison page exists when its text is written, it is published or previewed, and both species pages exist. */
export function isComparisonBuilt(record, builtQids, preview = isPreview()) {
  return record.status === 'ok' && (record.publish === true || preview) && builtQids.has(record.a) && builtQids.has(record.b);
}

/** Every *.json in a folder under the website root, parsed and sorted by file name. A missing folder is an empty list. */
export function readJsonDir(root, dir) {
  const abs = resolve(root, dir);
  if (!existsSync(abs)) return [];
  return readdirSync(abs)
    .filter((f) => f.endsWith('.json'))
    .sort()
    .map((f) => JSON.parse(readFileSync(resolve(abs, f), 'utf8')));
}

/** Public path of a species' recording. Content-hashed; astro.config.mjs copies the file into dist for built species only. */
export function audioPublicPath(root, record) {
  const file = resolve(root, assetsDir(), record.audio.file);
  const hash = createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, 10);
  return `/audio/species/${record.qid}.${hash}.mp3`;
}
