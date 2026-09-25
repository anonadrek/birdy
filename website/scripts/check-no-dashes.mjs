#!/usr/bin/env node
// Public copy must not contain dash punctuation that reaches the rendered page (see BLOG.md).
// Copy decks (JSON) are parsed and every string value is checked, so a dash written as a JSON
// escape is caught once JSON.parse has decoded it. Field notes (Markdown, rendered through
// Astro/smartypants) are checked line by line for literal dash characters, HTML dash entities,
// and "--" sequences that smartypants turns into a dash; pure-hyphen fence/thematic-break lines
// (frontmatter delimiters, thematic breaks) are skipped.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const notesDir = resolve(root, 'src/content/field-notes');
const deckFiles = ['src/content/copy.en.json', 'src/content/copy.sv.json'];
const noteFiles = readdirSync(notesDir, { recursive: true })
  .filter((f) => String(f).endsWith('.md'))
  .map((f) => join('src/content/field-notes', String(f)));
const files = [...deckFiles, ...noteFiles];

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

// Field notes: check every rendered line, skipping pure-hyphen fence/thematic-break lines.
for (const file of noteFiles) {
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
console.log(`no-dashes OK (${files.length} filer)`);
