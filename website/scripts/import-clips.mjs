#!/usr/bin/env node
// Writes the clips page's data (spec docs/superpowers/specs/2026-10-09-klippsidan-design.md) from the See the song
// schedule, so the page and the posts always have the same days:
//   node scripts/import-clips.mjs [social-folder]        (npm run clips:import -- [social-folder])
// social-folder is tools/social on the branch social/see-the-song (default: the worktree C:/w/birdy-social on Albin's
// Windows machine). The script reads out/<group>/schedule.csv for every scheduled group (a schedule.csv straight in
// out/ belongs to a test render and is never read), each clip's out/<group>/<slug>/caption.json (the names, the
// scientific one included) and cover/covers.json (the silhouette's credit). It writes src/data/clips.json, oldest
// first, and copies each clip's flock cover (out/<group>/<slug>/cover.jpg) to src/assets/clips/<slug>.jpg. A cover
// that no clip uses any more is removed, so the script can simply be run again when the schedule grows.
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

export const DEFAULT_SOURCE = 'C:/w/birdy-social/tools/social';
const ABOUT = 'Written by scripts/import-clips.mjs from the See the song schedule (tools/social on the branch social/see-the-song). Do not edit by hand: run the script again when the schedule changes.';

// Characters built with fromCharCode, not escapes, so the source is unambiguous on disk (as in check-no-dashes.mjs).
const BOM = String.fromCharCode(0xfeff);
const CR = String.fromCharCode(13);
const LF = String.fromCharCode(10);
const DASH_CHARS = `[${String.fromCharCode(0x2013)}${String.fromCharCode(0x2014)}]`;
const HAS_DASH = new RegExp(DASH_CHARS);
const ALL_DASHES = new RegExp(DASH_CHARS, 'g');
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const QID = /^Q\d+$/;
const SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

/**
 * RFC 4180 CSV as rows of fields: a quoted field may hold commas, line breaks and doubled quotes. A leading BOM is
 * dropped (the schedules start with one).
 * @param {string} text
 * @returns {string[][]}
 */
export function parseCsv(text) {
  const src = text.startsWith(BOM) ? text.slice(1) : text;
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < src.length; i += 1) {
    const ch = src[i];
    if (quoted) {
      if (ch !== '"') field += ch;
      else if (src[i + 1] === '"') {
        field += '"';
        i += 1;
      } else quoted = false;
    } else if (ch === '"') quoted = true;
    else if (ch === ',') {
      row.push(field);
      field = '';
    } else if (ch === CR || ch === LF) {
      if (ch === CR && src[i + 1] === LF) i += 1;
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else field += ch;
  }
  if (quoted) throw new Error('CSV: ett citattecken stängs aldrig');
  if (field !== '' || row.length > 0) {
    row.push(field);
    rows.push(row);
  }
  return rows;
}

/**
 * The CSV's rows as objects keyed by its header row. Blank lines are skipped; a row with another number of fields
 * than the header is an error.
 * @param {string} text
 * @returns {Record<string, string>[]}
 */
export function csvRecords(text) {
  const [header, ...rows] = parseCsv(text).filter((r) => !(r.length === 1 && r[0] === ''));
  if (!header) return [];
  return rows.map((r, n) => {
    if (r.length !== header.length) throw new Error(`CSV: rad ${n + 2} har ${r.length} fält, rubrikraden ${header.length}`);
    return Object.fromEntries(header.map((key, i) => [key, r[i]]));
  });
}

/**
 * "adapted" for a CC BY or CC BY-SA silhouette: the cover recolours and resizes it. The captions' rule
 * (tools/social/lib/captions.mjs, silhouetteCreditPart); CC0 and the public domain mark ask for nothing.
 * @param {string} licence
 */
export const isAdapted = (licence) => /^cc by\b/i.test(String(licence).trim());

/**
 * A person's name as the captions write it (captions.mjs, cleanName): trimmed, any dash written as a hyphen.
 * @param {unknown} name
 */
export const cleanName = (name) => String(name ?? '').trim().replace(ALL_DASHES, '-');

/**
 * One clip for src/data/clips.json from its schedule row, its caption.json and its covers.json entry. Throws, naming
 * the clip, on anything the page would show wrong.
 * @param {Record<string, string>} row
 * @param {{ qid?: string, names?: { sv?: string, en?: string, scientific?: string } }} caption
 * @param {{ silhouette?: { author?: string, licence?: string, url?: string } } | undefined} cover
 */
export function clipFromSources(row, caption, cover) {
  const where = `${row.date} ${row.slug}`;
  if (!ISO_DATE.test(row.date ?? '')) throw new Error(`${where}: datumet ska vara YYYY-MM-DD`);
  if (!QID.test(row.qid ?? '')) throw new Error(`${where}: ${row.qid} är inget QID`);
  if (!SLUG.test(row.slug ?? '')) throw new Error(`${where}: ogiltig slug`);
  if (caption.qid !== row.qid) throw new Error(`${where}: caption.json gäller ${caption.qid}, schemat ${row.qid}`);
  const names = { sv: caption.names?.sv ?? '', en: caption.names?.en ?? '', scientific: caption.names?.scientific ?? '' };
  for (const [key, value] of Object.entries(names)) {
    if (!value.trim()) throw new Error(`${where}: names.${key} saknas i caption.json`);
    if (HAS_DASH.test(value)) throw new Error(`${where}: names.${key} har ett tankstreck`);
  }
  if (names.sv !== row.name_sv || names.en !== row.name_en) {
    throw new Error(`${where}: namnen i caption.json (${names.sv}, ${names.en}) är inte schemats (${row.name_sv}, ${row.name_en})`);
  }
  const s = cover?.silhouette;
  if (!s) throw new Error(`${where}: covers.json saknar silhuetten för ${row.qid}`);
  const author = cleanName(s.author);
  const licence = String(s.licence ?? '').trim();
  const url = String(s.url ?? '').trim();
  if (!author || !licence) throw new Error(`${where}: silhuetten saknar upphovsperson eller licens`);
  if (!url.startsWith('https://www.phylopic.org/')) throw new Error(`${where}: silhuettens adress ska vara en sida på PhyloPic, inte ${url}`);
  return { date: row.date, qid: row.qid, slug: row.slug, names, silhouette: { author, licence, url, adapted: isAdapted(licence) } };
}

/**
 * The clips oldest first. Two clips with the same date, slug or species is an error: one clip a day, and the slug
 * names the cover's file.
 * @template {{ date: string, slug: string, qid: string }} T
 * @param {T[]} clips
 * @returns {T[]}
 */
export function sortClips(clips) {
  for (const key of /** @type {const} */ (['date', 'slug', 'qid'])) {
    const seen = new Set();
    for (const clip of clips) {
      if (seen.has(clip[key])) throw new Error(`två klipp har samma ${key}: ${clip[key]}`);
      seen.add(clip[key]);
    }
  }
  return [...clips].sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

/**
 * Reads the schedule under `source` (the tools/social folder) and writes src/data/clips.json and
 * src/assets/clips/<slug>.jpg under `websiteRoot`. Every clip is checked before anything is written.
 * @param {string} source
 * @param {string} websiteRoot
 * @returns {{ clips: ReturnType<typeof clipFromSources>[], removed: string[] }}
 */
export function importClips(source, websiteRoot) {
  const out = join(source, 'out');
  const coversFile = join(source, 'cover', 'covers.json');
  if (!existsSync(out) || !existsSync(coversFile)) {
    throw new Error(`${source} har inte out/ och cover/covers.json: ange mappen tools/social på grenen social/see-the-song`);
  }
  let covers;
  try {
    covers = JSON.parse(readFileSync(coversFile, 'utf8'));
  } catch (e) {
    throw new Error(`cover/covers.json: ${e instanceof Error ? e.message : e}`);
  }
  const groups = readdirSync(out, { withFileTypes: true })
    .filter((d) => d.isDirectory() && existsSync(join(out, d.name, 'schedule.csv')))
    .map((d) => d.name)
    .sort();
  const clips = [];
  /** @type {Map<string, string>} slug to the cover file in the social folder */
  const coverFiles = new Map();
  for (const group of groups) {
    const dir = join(out, group);
    const scheduleFile = `${group}/schedule.csv`;
    let rows;
    try {
      rows = csvRecords(readFileSync(join(dir, 'schedule.csv'), 'utf8'));
    } catch (e) {
      throw new Error(`${scheduleFile}: ${e instanceof Error ? e.message : e}`);
    }
    for (const row of rows) {
      if (basename(row.cover ?? '') !== 'cover.jpg') throw new Error(`${scheduleFile}: ${row.slug} har omslaget ${row.cover}, ska vara flockomslaget cover.jpg`);
      if (row.cover !== `${row.slug}/cover.jpg`) throw new Error(`${scheduleFile}: ${row.slug} pekar på ett annat klipps omslag (${row.cover})`);
      const coverFile = join(dir, row.cover);
      if (!existsSync(coverFile)) throw new Error(`${scheduleFile}: omslaget ${row.cover} saknas`);
      let caption;
      try {
        caption = JSON.parse(readFileSync(join(dir, row.caption_json), 'utf8'));
      } catch (e) {
        throw new Error(`${group}/${row.caption_json}: ${e instanceof Error ? e.message : e}`);
      }
      clips.push(clipFromSources(row, caption, covers[row.qid]));
      coverFiles.set(row.slug, coverFile);
    }
  }
  let sorted;
  try {
    sorted = sortClips(clips);
  } catch (e) {
    throw new Error(`${out}: ${e instanceof Error ? e.message : e}`);
  }
  if (sorted.length === 0) throw new Error(`${out}: inget schema har några klipp`);

  const assets = join(websiteRoot, 'src', 'assets', 'clips');
  mkdirSync(assets, { recursive: true });
  for (const [slug, file] of coverFiles) copyFileSync(file, join(assets, `${slug}.jpg`));
  const removed = readdirSync(assets).filter((f) => f.endsWith('.jpg') && !coverFiles.has(f.slice(0, -'.jpg'.length))).sort();
  for (const f of removed) rmSync(join(assets, f));

  const dataFile = join(websiteRoot, 'src', 'data', 'clips.json');
  mkdirSync(dirname(dataFile), { recursive: true });
  writeFileSync(dataFile, `${JSON.stringify({ _about: ABOUT, clips: sorted }, null, 2)}${LF}`);
  return { clips: sorted, removed };
}

function main() {
  const websiteRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
  const source = resolve(process.argv[2] ?? DEFAULT_SOURCE);
  const { clips, removed } = importClips(source, websiteRoot);
  const gone = removed.length ? `; borttagna omslag: ${removed.join(', ')}` : '';
  console.log(`${clips.length} klipp, ${clips[0].date} till ${clips.at(-1).date}, omslagen i src/assets/clips/${gone}`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  try {
    main();
  } catch (e) {
    console.error(`import-clips: ${e instanceof Error ? e.message : e}`);
    process.exit(1);
  }
}
