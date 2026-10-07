// Where the species pages read their data, and which records get a page (spec 2026-09-25 §14).
// Plain JS so astro.config.mjs, content.config.ts, src/lib/species.ts and the check scripts share one rule.
//   SPECIES_FIXTURES=1  read the test data in tests/fixtures/ instead of src/data/ and src/assets/species/
//   SPECIES_PREVIEW=1   also build verified pages that are not published yet (Vercel Preview)
// Both are off unless exactly '1' (unset, '0' or anything else means off); `npm run build:prod` pins
// both to '0' explicitly, so an inherited shell variable can never silently change a production build.
import { createHash } from 'node:crypto';
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const useFixtures = () => process.env.SPECIES_FIXTURES === '1';

/**
 * Preview (unpublished-but-verified) pages must never build in a Vercel Production deploy, even if
 * SPECIES_PREVIEW leaks in from a misconfigured environment. Vercel sets VERCEL_ENV to 'production'
 * only for Production deploys (Preview deploys get 'preview'), so this is a safe, narrow check.
 */
export function isPreview() {
  const preview = process.env.SPECIES_PREVIEW === '1';
  if (preview && process.env.VERCEL_ENV === 'production') {
    throw new Error(
      'SPECIES_PREVIEW=1 är satt men VERCEL_ENV=production: förhandsgranskade (opublicerade) sidor får aldrig byggas i Vercels Production.',
    );
  }
  return preview;
}

/** Folders relative to the website root. */
export const speciesDir = () => (useFixtures() ? 'tests/fixtures/species' : 'src/data/species');
export const comparisonsDir = () => (useFixtures() ? 'tests/fixtures/comparisons' : 'src/data/comparisons');
export const assetsDir = () => (useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species');

/**
 * Written (ok) and verified (spec Revision 2026-10-05): a record the site may build, so the one held to
 * the full page contract in src/lib/species-schema.mjs. Every other record is only checked as an envelope.
 * @param {unknown} record
 * @returns {boolean}
 */
export function hasPageContract(record) {
  return typeof record === 'object' && record !== null && /** @type {any} */ (record).status === 'ok' && Boolean(/** @type {any} */ (record).verification);
}

/** A species page exists when its text is written (ok), its facts are verified (spec Revision 2026-10-05), and it is published or this is a preview build. */
export function isSpeciesBuilt(record, preview = isPreview()) {
  return hasPageContract(record) && (record.publish === true || preview);
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
    .map((f) => {
      const text = readFileSync(resolve(abs, f), 'utf8');
      try {
        return JSON.parse(text);
      } catch (e) {
        throw new Error(`${dir}/${f}: ${text.startsWith('\uFEFF') ? 'starts with a UTF-8 BOM; ' : ''}${e.message}`, { cause: e });
      }
    });
}

/** Public path of a species' recording. Content-hashed; astro.config.mjs copies the file into dist for built species only. */
export function audioPublicPath(root, record) {
  const file = resolve(root, assetsDir(), record.audio.file);
  const hash = createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, 10);
  return `/audio/species/${record.qid}.${hash}.mp3`;
}
