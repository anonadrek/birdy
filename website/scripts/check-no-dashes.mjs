#!/usr/bin/env node
// Public copy must not contain dash punctuation that reaches the rendered page (see BLOG.md).
// Copy decks (JSON) are parsed and every string value is checked, so a dash written as a JSON
// escape is caught once JSON.parse has decoded it. Field notes (Markdown, rendered through
// Astro/smartypants) are checked line by line for literal dash characters, HTML dash entities,
// and "--" sequences that smartypants turns into a dash; pure-hyphen fence/thematic-break lines
// (frontmatter delimiters, thematic breaks) are skipped. The legal pages under /legal/ are rendered
// from docs/play-store/ (src/lib/markdown.ts, LEGAL_DOCS) and are checked the same way.
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { basename, dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const notesDir = resolve(root, 'src/content/field-notes');
const deckFiles = ['src/content/copy.en.json', 'src/content/copy.sv.json'];
const noteFiles = readdirSync(notesDir, { recursive: true })
  .filter((f) => String(f).endsWith('.md'))
  .map((f) => join('src/content/field-notes', String(f)));
// The three files LEGAL_DOCS in src/lib/markdown.ts renders on /legal/ (keep the two lists in step).
const legalFiles = ['privacy-policy.md', 'terms.md', 'data-safety-form.md'].map((f) => join('..', 'docs', 'play-store', f));
const files = [...deckFiles, ...noteFiles, ...legalFiles];

// Built via fromCharCode, not source escapes, so the regex source is unambiguous on disk.
const NEWLINE = String.fromCharCode(10);
const BACKSLASH = String.fromCharCode(92);
const EM_DASH = String.fromCharCode(0x2014);
const EN_DASH = String.fromCharCode(0x2013);

const emDashRe = new RegExp(EM_DASH);
const spacedEnDashRe = new RegExp(BACKSLASH + 's' + EN_DASH + BACKSLASH + 's');
const fenceRe = new RegExp('^' + BACKSLASH + 's*-{3,}' + BACKSLASH + 's*$');
// Flags a bare "--" (smartypants turns it into a dash) but not "---" fences, "<!--"/"-->", or table rules "|---|".
const doubleHyphenRe = /(?<![-!<|])--(?![->|])/;
const entityRe = /&(?:m|n)dash;|&#x?(?:8212|8211|2014|2013);/i;

let failed = false;
const fail = (where, why) => {
  console.error(`no-dashes FAILED: ${where} innehåller ${why}`);
  failed = true;
};

const walkStrings = (value, path, cb) => {
  if (typeof value === 'string') {
    cb(value, path);
  } else if (Array.isArray(value)) {
    value.forEach((v, i) => walkStrings(v, `${path}[${i}]`, cb));
  } else if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) {
      walkStrings(value[key], path ? `${path}.${key}` : key, cb);
    }
  }
};

// Copy decks: parse as JSON so a dash written as a JSON escape is caught after decoding.
for (const file of deckFiles) {
  const parsed = JSON.parse(readFileSync(resolve(root, file), 'utf8'));
  walkStrings(parsed, '', (text, path) => {
    if (emDashRe.test(text)) fail(`${file}:${path}`, `tankstreck (${EM_DASH})`);
    if (spacedEnDashRe.test(text)) fail(`${file}:${path}`, `tankstreck ( ${EN_DASH} )`);
  });
}

// Species and comparison data (spec 2026-09-25): rendered text only. Quotes are Wikipedia's own words and
// are not shown; the top-level fact list, raw counts and generation details are not shown either.
const SKIP = new Set(['quote', 'sourceUrl', 'licenseUrl', 'file', 'revision', 'title', 'model', 'prompt', 'at', 'effort', 'checker', 'qid', 'slug', 'factIds']);
// 'flags' added 2026-10-06 (fas 1b final review I7): the flag messages are never shown and can hold the
// checking model's own English reasons.
const SKIP_TOP = new Set(['facts', 'raw', 'generated', 'rejectedText', 'errors', 'review', 'verification', 'flags']);
const walkRendered = (value, path, cb) => {
  if (typeof value === 'string') cb(value, path);
  else if (Array.isArray(value)) value.forEach((v, i) => walkRendered(v, `${path}[${i}]`, cb));
  else if (value && typeof value === 'object') {
    for (const key of Object.keys(value)) {
      if (SKIP.has(key) || (!path && SKIP_TOP.has(key))) continue;
      walkRendered(value[key], path ? `${path}.${key}` : key, cb);
    }
  }
};
const dataDirs = ['src/data/species', 'src/data/comparisons', 'tests/fixtures/species', 'tests/fixtures/comparisons'];
const dataFiles = [
  'src/data/species-groups.json',
  ...dataDirs.flatMap((dir) => (existsSync(resolve(root, dir)) ? readdirSync(resolve(root, dir)).filter((f) => f.endsWith('.json')).map((f) => join(dir, f)) : [])),
];
// The publish loop (scripts/publish-next.mjs) checks only what goes online: the published records in src/data/
// plus the one it is publishing (NO_DASHES_PUBLISHED_ONLY=1, NO_DASHES_RECORD=<QID or comparison stem>), so a
// dash in a record nobody publishes yet can't fail, and be blamed on, every other record's pick. A normal run
// (npm run test:no-dashes) checks every record.
const publishedOnly = process.env.NO_DASHES_PUBLISHED_ONLY === '1';
const currentRecord = process.env.NO_DASHES_RECORD ?? '';
let skippedRecords = 0;
for (const file of dataFiles) {
  const data = JSON.parse(readFileSync(resolve(root, file), 'utf8'));
  const isRecord = /^src[\\/]data[\\/](species|comparisons)[\\/]/.test(file);
  if (publishedOnly && isRecord && data.publish !== true && basename(file, '.json') !== currentRecord) {
    skippedRecords += 1;
    continue;
  }
  walkRendered(data, '', (value, path) => {
    if (emDashRe.test(value)) fail(`${file}:${path}`, `tankstreck (${EM_DASH})`);
    if (spacedEnDashRe.test(value)) fail(`${file}:${path}`, `tankstreck ( ${EN_DASH} )`);
  });
}
files.push(...dataFiles);

// Field notes and legal pages: check every rendered line, skipping pure-hyphen fence/thematic-break lines.
for (const file of [...noteFiles, ...legalFiles]) {
  const lines = readFileSync(resolve(root, file), 'utf8').split(NEWLINE);
  lines.forEach((line, i) => {
    if (fenceRe.test(line)) return;
    const where = `${file}:${i + 1}`;
    if (emDashRe.test(line)) fail(where, `tankstreck (${EM_DASH})`);
    if (spacedEnDashRe.test(line)) fail(where, `tankstreck ( ${EN_DASH} )`);
    if (doubleHyphenRe.test(line)) fail(where, 'dubbelt bindestreck (blir tankstreck via smartypants)');
    if (entityRe.test(line)) fail(where, 'HTML-entitet för tankstreck');
  });
}

if (failed) process.exit(1);
console.log(`no-dashes OK (${files.length - skippedRecords} filer${publishedOnly ? `, ${skippedRecords} opublicerade poster utanför kontrollen` : ''})`);
