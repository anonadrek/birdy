// Species data for astro.config.mjs (sitemap lastmod and noindex), in plain JS so the config can load it.
// Same publishing rule as the pages (src/lib/species-source.mjs). The group rule must match MIN_GROUP_SIZE
// in src/lib/species.ts; scripts/check-seo.mjs fails if a noindex page shows up in the sitemap.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { COMPARISONS_ENABLED, comparisonsDir, isComparisonBuilt, isPreview, isSpeciesBuilt, readJsonDir, speciesDir } from './species-source.mjs';

export const MIN_GROUP_SIZE = 3;
const BASES = [['sv', '/sv/arter/'], ['en', '/species/']];
// Kept in sync by hand with species.ts's ABOUT_SLUG (that file imports astro:content and can't be loaded
// from this plain-JS module, see the file-level comment; Task 12 adds the about page these point to).
const ABOUT_SLUG = { sv: 'om-artsidorna', en: 'about-these-pages' };

/** @param {string} root the website folder */
export function readSpeciesSitemapInfo(root) {
  const groups = JSON.parse(readFileSync(resolve(root, 'src/data/species-groups.json'), 'utf8')).groups;
  const built = readJsonDir(root, speciesDir()).filter((r) => isSpeciesBuilt(r));
  const builtQids = new Set(built.map((r) => r.qid));
  // No comparison pages until Task 11 turns them on (COMPARISONS_ENABLED), so none in the sitemap either.
  const comparisons = COMPARISONS_ENABLED ? readJsonDir(root, comparisonsDir()).filter((c) => isComparisonBuilt(c, builtQids)) : [];
  const preview = isPreview();
  /** @type {Map<string, string>} */
  const lastmod = new Map();
  /** @type {Set<string>} */
  const noindex = new Set();
  /** @type {Map<string, number>} */
  const sizes = new Map();
  let newest = '';
  // No species published yet (Task 15 Step 4: the production build goes through with zero of them):
  // the hub and the about page have nothing of their own to show a search engine either, same reason and
  // same noindex meta tag as SpeciesHub.astro and AboutSpeciesPages.astro (Task 7 and Task 12; keep the
  // three in sync, the filter below is what actually keeps them out of the sitemap, the meta tag alone
  // only hides them from being indexed, not from being listed).
  if (built.length === 0) {
    for (const [lang, base] of BASES) {
      noindex.add(base);
      noindex.add(`${base}${ABOUT_SLUG[lang]}/`);
    }
  }
  for (const r of built) {
    const at = r.generated?.text?.at ?? r.verification?.at ?? '';
    for (const [lang, base] of BASES) {
      const path = `${base}${r.slug[lang]}/`;
      if (at) lastmod.set(path, at);
      if (preview && !r.publish) noindex.add(path);
    }
    sizes.set(r.group, (sizes.get(r.group) ?? 0) + 1);
    if (at > newest) newest = at;
  }
  for (const c of comparisons) {
    for (const [lang, base] of BASES) {
      const path = `${base}${c.slug[lang]}/`;
      if (c.generated?.at) lastmod.set(path, c.generated.at);
      if (preview && !c.publish) noindex.add(path);
    }
  }
  for (const g of groups) {
    const n = sizes.get(g.key) ?? 0;
    if (n === 0) continue; // no page at all
    for (const [lang, base] of BASES) {
      const path = `${base}${g.slug[lang]}/`;
      if (n < MIN_GROUP_SIZE) noindex.add(path);
      else if (newest) lastmod.set(path, newest);
    }
  }
  if (newest) for (const [, base] of BASES) lastmod.set(base, newest);
  return { lastmod, noindex };
}
