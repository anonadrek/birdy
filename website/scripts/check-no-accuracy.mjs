#!/usr/bin/env node
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

// Cross-cutting rule (DP B): never communicate accuracy numbers.
// Guards both copy decks against the "~72%" regression.
const __dirname = dirname(fileURLToPath(import.meta.url));
const root = resolve(__dirname, '..');
const banned = [/\b72\b/, /accuracy/i, /träffsäker/i];

let failed = false;
for (const lang of ['en', 'sv']) {
  const raw = readFileSync(resolve(root, `src/content/copy.${lang}.json`), 'utf8');
  for (const re of banned) {
    if (re.test(raw)) {
      console.error(`accuracy-guard FAILED: copy.${lang}.json matches ${re}`);
      failed = true;
    }
  }
}
if (failed) process.exit(1);
console.log('accuracy-guard OK (no accuracy number in either deck)');
