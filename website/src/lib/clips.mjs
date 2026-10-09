// The clips page, /clips/ and /sv/klipp/ (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md): every See the
// song clip posted so far, newest first, each linking to its bird's page. Plain JS, so astro.config.mjs (the virtual
// module virtual:birdy-clips), the page, the footer and the unit tests (tests/unit/clips.unit.mjs) share one rule. The
// data is src/data/clips.json, written by scripts/import-clips.mjs.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const LF = String.fromCharCode(10);
const CC0_DEED = 'https://creativecommons.org/publicdomain/zero/1.0/';
const PDM_DEED = 'https://creativecommons.org/publicdomain/mark/1.0/';

/**
 * @typedef {{ author: string, licence: string, url: string, adapted: boolean }} Silhouette
 * @typedef {{ date: string, qid: string, slug: string, names: { sv: string, en: string, scientific: string }, silhouette: Silhouette, clue: string }} ClipData
 */

/**
 * The clips page in each language.
 * @param {'sv' | 'en'} locale
 */
export const clipsHref = (locale) => (locale === 'sv' ? '/sv/klipp/' : '/clips/');

/**
 * src/data/clips.json's path under `websiteRoot`. The one place this path is written, so loadClips below and
 * astro.config.mjs's file watch (clipsModule, which rebuilds virtual:birdy-clips whenever the import script
 * rewrites this file) can never quietly drift apart and watch the wrong file.
 * @param {string} websiteRoot
 * @returns {string}
 */
export function clipsDataFile(websiteRoot) {
  return resolve(websiteRoot, 'src', 'data', 'clips.json');
}

/**
 * src/data/clips.json's clips, oldest first as the import script writes them.
 * @param {string} websiteRoot
 * @returns {ClipData[]}
 */
export function loadClips(websiteRoot) {
  const file = clipsDataFile(websiteRoot);
  const data = JSON.parse(readFileSync(file, 'utf8'));
  if (!Array.isArray(data.clips)) throw new Error(`${file}: clips saknas (kör npm run clips:import)`);
  return data.clips;
}

/**
 * The clips posted on or before `todayIso` (YYYY-MM-DD in Europe/Stockholm, the build's date), newest first. A clip
 * shows from its posting day, so the nightly rebuild adds each day's clip by itself.
 * @template {{ date: string }} T
 * @param {T[]} clips
 * @param {string} todayIso
 * @returns {T[]}
 */
export function visibleClips(clips, todayIso) {
  return clips.filter((c) => c.date <= todayIso).sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
}

/**
 * The source of virtual:birdy-clips (astro.config.mjs): the clips visible on `todayIso`, newest first, each with its
 * cover imported for astro:assets. Only their covers are imported: Vite emits every image a module imports into
 * dist/_astro/, used or not, so a later clip's cover would otherwise be online before its day.
 * @param {ClipData[]} clips
 * @param {string} todayIso
 */
export function clipsModuleSource(clips, todayIso) {
  const shown = visibleClips(clips, todayIso);
  const imports = shown.map((c, n) => `import cover${n} from ${JSON.stringify(`/src/assets/clips/${c.slug}.jpg`)};`);
  const entries = shown.map((c, n) => `{ ...${JSON.stringify(c)}, cover: cover${n} }`);
  return [...imports, `export const clips = [${entries.join(', ')}];`, ''].join(LF);
}

/**
 * What a card shows for its bird: the name and the address of its species page when this build has that page, in the
 * page's language; otherwise the clip's own name and no link (the same rule as the field notes' species links).
 * @template {{ names: { sv: string, en: string } }} S
 * @param {{ names: { sv: string, en: string } }} clip
 * @param {S | undefined} species the clip's species, when it has a page in this build
 * @param {'sv' | 'en'} locale
 * @param {(species: S, locale: 'sv' | 'en') => string} hrefOf
 * @returns {{ name: string, href: string | undefined }}
 */
export function clipLink(clip, species, locale, hrefOf) {
  if (!species) return { name: clip.names[locale], href: undefined };
  return { name: species.names[locale], href: hrefOf(species, locale) };
}

/**
 * The deed of a silhouette's licence, the Swedish deed on the Swedish page, or undefined for a licence the site does
 * not know (the credit then names it without a link).
 * @param {string} licence
 * @param {'sv' | 'en'} locale
 * @returns {string | undefined}
 */
export function licenceDeed(licence, locale) {
  const l = licence.trim();
  const by = l.match(/^CC (BY(?:-SA)?) ([1-4]\.0)$/i);
  let url;
  if (by) url = `https://creativecommons.org/licenses/${by[1].toLowerCase()}/${by[2]}/`;
  else if (/^CC0(?: 1\.0)?$/i.test(l)) url = CC0_DEED;
  else if (/^Public domain mark(?: 1\.0)?$/i.test(l)) url = PDM_DEED;
  if (!url) return undefined;
  return locale === 'sv' ? `${url}deed.sv` : url;
}

/**
 * The silhouette's credit under a card, in the captions' words ("Silhouette: Andy Wilson, CC0, via PhyloPic", and
 * ", adapted" after a CC BY silhouette, which the cover recolours), as text and links: the licence links to its deed,
 * "PhyloPic" to the silhouette's page.
 * @param {Silhouette} silhouette
 * @param {'sv' | 'en'} locale
 * @param {{ label: string, adapted: string }} words copy clips.silhouette and clips.adapted
 * @returns {{ text: string, href?: string }[]}
 */
export function creditParts(silhouette, locale, words) {
  const deed = licenceDeed(silhouette.licence, locale);
  return [
    { text: `${words.label}: ${silhouette.author}, ` },
    deed ? { text: silhouette.licence, href: deed } : { text: silhouette.licence },
    { text: ', via ' },
    { text: 'PhyloPic', href: silhouette.url },
    ...(silhouette.adapted ? [{ text: `, ${words.adapted}` }] : []),
  ];
}
