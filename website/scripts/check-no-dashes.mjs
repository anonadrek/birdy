#!/usr/bin/env node
// Public copy must not use dash punctuation (see BLOG.md). Guards both copy decks and every field note.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const notesDir = resolve(root, 'src/content/field-notes');
const files = [
  'src/content/copy.en.json',
  'src/content/copy.sv.json',
  ...readdirSync(notesDir, { recursive: true })
    .filter((f) => String(f).endsWith('.md'))
    .map((f) => join('src/content/field-notes', String(f))),
];
const banned = [
  { re: /\u2014/, name: 'tankstreck (—)' },
  { re: /\s\u2013\s/, name: 'tankstreck ( – )' },
];

let failed = false;
for (const file of files) {
  const lines = readFileSync(resolve(root, file), 'utf8').split('\n');
  lines.forEach((line, i) => {
    for (const { re, name } of banned) {
      if (re.test(line)) {
        console.error(`no-dashes FAILED: ${file}:${i + 1} innehåller ${name}`);
        failed = true;
      }
    }
  });
}
if (failed) process.exit(1);
console.log(`no-dashes OK (${files.length} filer)`);
