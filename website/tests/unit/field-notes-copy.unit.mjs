// The field notes' own words (Albin 2026-10-10): a note never quotes the internal rule about running without asking
// for permission, and never talks about "full auto". Read straight from the Markdown, both languages.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';

const dir = resolve(import.meta.dirname, '../../src/content/field-notes');
const notes = ['en', 'sv'].flatMap((locale) => readdirSync(join(dir, locale)).filter((f) => f.endsWith('.md')).map((f) => join(dir, locale, f)));

test('no field note quotes the permission rule or mentions full auto', () => {
  assert.ok(notes.length >= 6, `${notes.length} notes`);
  for (const file of notes) {
    const text = readFileSync(file, 'utf8').toLowerCase();
    for (const banned of ['permission', 'tillåtelse', 'full auto', 'fullt automatisk', 'helt automatisk', 'helt auto']) {
      assert.ok(!text.includes(banned), `${file} mentions "${banned}"`);
    }
  }
});
