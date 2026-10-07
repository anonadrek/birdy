#!/usr/bin/env node
// Checks the species hub's zero-species state (spec 2026-09-25 §14, controller review 2026-10-07): the
// production build must go through cleanly with zero published species (Task 15 Step 4, the window
// between this branch's code merging to `main` and Task 16 publishing the first real one). The hub must
// not then offer an indexable, empty page: it gets noindex and shows only its lead paragraph, no search
// box, group grid, comparison list or A-to-Z list (all of which would be empty or dead anyway).
//
// This reads `dist-empty/` (npm run build:empty, SPECIES_EMPTY=1, which points src/lib/species-source.mjs
// at the intentionally empty tests/fixtures/empty/ rather than src/data/species/, a deliberate test
// fixture, not the real data's current, temporary emptiness, so this check stays meaningful and green
// forever, including after Task 16 has published real species on `main`). Not the fixtures build in
// dist/: the two must never be confused, since dist/ holds the 16-species fixture build the rest of the
// Playwright suite depends on.
//
// NOTE for whoever picks up Task 12 and Task 14: once AboutSpeciesPages.astro exists, extend this script
// with the same two checks for sv/arter/om-artsidorna/ and species/about-these-pages/ (Task 12 applies
// the same zero-species noindex rule there).
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const dist = resolve(root, 'dist-empty');
if (!existsSync(dist)) {
  console.error(`check-empty-hub: ${dist} saknas (npm run build:empty)`);
  process.exit(1);
}

const page = (path) => {
  const file = join(dist, path, 'index.html');
  if (!existsSync(file)) {
    console.error(`check-empty-hub: ${file} saknas`);
    process.exit(1);
  }
  return readFileSync(file, 'utf8');
};

// Astro HTML-escapes attribute values; decode the handful of entities a meta description could contain
// so the measured length matches what a search result actually shows (Task 8 fix wave: reusing
// hubLeadEmpty as the n=0 description made the English one 156 characters, one over spec §12's 155-char
// cap: descHubEmpty is its own, shorter copy key now, and this check keeps that true for good).
const decodeEntities = (s) => s.replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>');
const metaDescription = (html) => {
  const m = html.match(/<meta name="description" content="([^"]*)"/);
  return m ? decodeEntities(m[1]) : null;
};

const errors = [];
const fail = (where, why) => errors.push(`${where}: ${why}`);

for (const path of ['sv/arter', 'species']) {
  const html = page(path);
  // Markup only, not the compiled client script's own `[data-item]`/`[data-browse]` selector strings
  // (those two substrings are always present, script and all, whether or not any element carries them).
  if (!html.includes('<meta name="robots" content="noindex, follow"')) fail(path, 'saknar noindex trots noll byggda arter');
  if (/<div\s+data-browse/.test(html)) fail(path, 'visar grupper eller jämförelser trots noll byggda arter');
  if (html.includes('id="species-search"')) fail(path, 'visar sökfältet trots noll byggda arter');
  if (/<li\s+data-item/.test(html)) fail(path, 'visar en art trots noll byggda arter');
  if (!html.includes('<h1')) fail(path, 'saknar h1');
  // Third controller review, same day: the category bar's only chip at n=0 is "All species (0)" (nothing
  // to filter into), and an ItemList with zero items would be a CollectionPage claiming a list it doesn't have.
  if (html.includes('data-catbar')) fail(path, 'visar kategoriraden trots noll byggda arter');
  if (html.includes('"@type":"CollectionPage"')) fail(path, 'har en ItemList i JSON-LD trots noll byggda arter');
  const desc = metaDescription(html);
  if (!desc) fail(path, 'saknar meta description');
  else if (desc.length < 120 || desc.length > 155) fail(path, `meta description är ${desc.length} tecken (ska vara 120 till 155): ${desc}`);
}

const sitemap = existsSync(join(dist, 'sitemap-index.xml')) ? 'present' : 'missing';
if (sitemap === 'missing') fail('sitemap-index.xml', 'saknas helt');

if (errors.length) {
  console.error(`check-empty-hub FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log('check-empty-hub OK (hubsidorna har noindex och visar bara ingressen med noll byggda arter)');
