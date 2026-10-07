// Where the species pages read their data, and which records get a page (spec 2026-09-25 §14).
// Plain JS so astro.config.mjs, content.config.ts, src/lib/species.ts and the check scripts share one rule.
//   SPECIES_FIXTURES=1  read the test data in tests/fixtures/ instead of src/data/ and src/assets/species/
//   SPECIES_PREVIEW=1   also build verified pages that are not published yet (Vercel Preview)
//   SPECIES_EMPTY=1     read the intentionally empty tests/fixtures/empty/ instead, overriding
//                       SPECIES_FIXTURES, for testing the zero-species state (see useEmptyData below)
// All three are off unless exactly '1' (unset, '0' or anything else means off); `npm run build:prod` pins
// SPECIES_FIXTURES and SPECIES_PREVIEW to '0' explicitly, so an inherited shell variable can never
// silently change a production build.
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

/**
 * An explicit, intentionally empty data set (tests/fixtures/empty/), for testing the zero-species
 * state (controller review 2026-10-07: SpeciesHub.astro's noindex + lead-only rendering, the
 * sitemap exclusion in Task 14, the hidden nav/footer/guide links in Task 13) without depending on
 * src/data/species/ being empty for real. That emptiness is only temporary (Task 16 fills it in one
 * species at a time), and a check tied to it would start failing the moment the first species is
 * published, a trap for both CI and the publish loop. Same production guard as isPreview(): this
 * should never reach Vercel at all (no build script sets it there), but Production must not honour
 * it even if it leaked in.
 */
export function useEmptyData() {
  const empty = process.env.SPECIES_EMPTY === '1';
  if (empty && process.env.VERCEL_ENV === 'production') {
    throw new Error(
      'SPECIES_EMPTY=1 är satt men VERCEL_ENV=production: det tomma testläget för noll arter får aldrig byggas i Vercels Production.',
    );
  }
  return empty;
}

/** Folders relative to the website root. SPECIES_EMPTY wins over SPECIES_FIXTURES if both are set. */
export const speciesDir = () => (useEmptyData() ? 'tests/fixtures/empty/species' : useFixtures() ? 'tests/fixtures/species' : 'src/data/species');
export const comparisonsDir = () => (useEmptyData() ? 'tests/fixtures/empty/comparisons' : useFixtures() ? 'tests/fixtures/comparisons' : 'src/data/comparisons');
export const assetsDir = () => (useEmptyData() ? 'tests/fixtures/empty/species-assets' : useFixtures() ? 'tests/fixtures/species-assets' : 'src/assets/species');

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

/**
 * The photos and recordings of the species built in this build (`isSpeciesBuilt`), read once from the
 * data files with `root` = the website folder (Astro's config root, never process.cwd()). astro.config.mjs
 * turns this into the virtual module the pages import (virtual:birdy-species-media) and copies the
 * recordings into dist under the same hashed names, so a page link and the copied file always agree.
 * @param {string} root
 * @param {boolean} [preview]
 * @returns {{ images: string[], audio: { qid: string, file: string, href: string }[] }}
 */
export function builtSpeciesMedia(root, preview = isPreview()) {
  const built = readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r, preview));
  return {
    images: built.flatMap((r) => (r.images ?? []).map((/** @type {{ file: string }} */ i) => i.file)),
    audio: built.filter((r) => r.audio).map((r) => ({ qid: r.qid, file: r.audio.file, href: audioPublicPath(root, r) })),
  };
}
