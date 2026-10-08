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
// The about pages (Task 12) follow the same zero-species noindex rule, but nothing on them depends on the
// species list, so their content must stay: all five sections and the mail link.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const copy = {
  sv: JSON.parse(readFileSync(resolve(root, 'src/content/copy.sv.json'), 'utf8')),
  en: JSON.parse(readFileSync(resolve(root, 'src/content/copy.en.json'), 'utf8')),
};
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

for (const path of ['sv/arter/om-artsidorna', 'species/about-these-pages']) {
  const html = page(path);
  if (!html.includes('<meta name="robots" content="noindex, follow"')) fail(path, 'saknar noindex trots noll byggda arter');
  if (!html.includes('<h1')) fail(path, 'saknar h1');
  const main = html.slice(html.indexOf('<main'), html.indexOf('</main>'));
  const sections = (main.match(/<h2[\s>]/g) ?? []).length;
  if (sections < 5) fail(path, `har ${sections} h2, ska ha alla fem avsnitten även med noll byggda arter`);
  if (!main.includes('href="mailto:')) fail(path, 'saknar mejllänken');
  const desc = metaDescription(html);
  if (!desc) fail(path, 'saknar meta description');
  else if (desc.length < 120 || desc.length > 155) fail(path, `meta description är ${desc.length} tecken (ska vara 120 till 155): ${desc}`);
}

// The menu, the footer and the home page's field guide section (Task 13) link to the species pages only once
// a species is built: at zero no page on the site may link to them (the hub only says they are coming).
// The field notes too (2026-10-08): a note links to species pages from its Markdown, and the links to pages a build
// doesn't have become plain text (src/lib/note-links.mjs), so at zero a note has none left.
const notePages = readdirSync(join(dist, 'blog'), { withFileTypes: true })
  .filter((d) => d.isDirectory())
  .flatMap((d) => [`blog/${d.name}`, `sv/blog/${d.name}`]);
if (notePages.length === 0) fail('blog', 'inga inlägg byggda');
for (const path of ['', 'sv', 'blog', 'sv/blog', ...notePages]) {
  const html = page(path);
  const where = path || '/';
  if (/href="\/(sv\/arter|species)\//.test(html)) fail(where, 'länkar till artsidorna trots noll byggda arter');
  if (html.includes('class="fpop"')) fail(where, 'sidfoten har raden Vanliga arter trots noll byggda arter');
  if (html.includes('fgrid--species')) fail(where, 'sidfoten har kolumnen Arter trots noll byggda arter');
  if (html.includes('class="browse"')) fail(where, 'startsidan länkar till arterna trots noll byggda arter');
  if (!html.includes('class="skip-link"')) fail(where, 'saknar hoppa-till-innehållet-länken');
}

// The home page's Dagens fågel with zero species pages (plan 2026-10-08 Task 2, review I4): no page can hang on the
// wall, so the site shows another bird than the app's, and the handwritten note under the headline must then be the
// one without "och i appen" / "and in the app" (it only says that on days the plate shows the app's bird, from the day
// 1.3 is live). The same-as-app line is never shown here.
for (const [path, locale] of [['sv', 'sv'], ['', 'en']]) {
  const html = page(path);
  const where = path || '/';
  const note = copy[locale].hero.noteFallback;
  if (!note) fail(where, 'copy saknar hero.noteFallback');
  const shown = html.match(/<p class="mnote[^"]*"[^>]*>([^<]*)<\/p>/)?.[1];
  if (shown !== note) fail(where, `heronoten är "${shown}", ska vara reservnoten "${note}" när planschen inte visar appens fågel`);
  if (/och i appen|and in the app/.test(shown ?? '')) fail(where, 'heronoten säger "och i appen" fast planschen inte visar appens fågel');
  // Markup only: the hero's inlined guard script (hero/same-as-app-guard.ts) names the attribute in a selector.
  const markup = html.replace(/<script\b[\s\S]*?<\/script>/gi, '');
  if (markup.includes('data-same-as-app')) fail(where, 'raden "samma fågel som i appen" visas utan appens fågel');
}

const sitemap = existsSync(join(dist, 'sitemap-index.xml')) ? 'present' : 'missing';
if (sitemap === 'missing') fail('sitemap-index.xml', 'saknas helt');

if (errors.length) {
  console.error(`check-empty-hub FAILED (${errors.length} fel):\n${errors.join('\n')}`);
  process.exit(1);
}
console.log('check-empty-hub OK (hubsidorna har noindex och visar bara ingressen, om-sidorna har noindex och hela innehållet, startsidorna och bloggen länkar inte till artsidorna, med noll byggda arter)');
