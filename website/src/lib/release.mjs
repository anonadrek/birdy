// The app release the website describes (plan 2026-10-08). The release checklist (Plan 3 Task 11 on release/1.3.0, and
// every app release after it) updates this file together with the frozen species snapshot it names.
import { resolve } from 'node:path';

/** The shipped app's version, which names the frozen species list the home page picks Dagens fågel from. */
export const APP_VERSION = '1.3.0';

/**
 * src/data/app-species-<version>.json: the shipped app's species list (id, abundance, iucn_status, season, regions),
 * written from tests/fixtures/daily-bird-golden.json by scripts/app-species-snapshot.mjs. Regenerated per app release.
 * @param {string} websiteRoot
 */
export const appSpeciesSnapshotPath = (websiteRoot) => resolve(websiteRoot, 'src', 'data', `app-species-${APP_VERSION}.json`);
