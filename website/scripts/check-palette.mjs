#!/usr/bin/env node
// Palettvakt (spec 2026-09-28-webb-faltboksfarger §3): webbens egen yta har inget mossgrönt och
// ingen olivton. Appens färger får bara finnas i telefonerna (src/styles/phone.css och
// src/components/phone/), som visar appen som den är.
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const scanDirs = ['src', 'tools'];
const allowed = (rel) => rel === 'src/styles/phone.css' || rel.startsWith('src/components/phone/');
const banned = [
  [/#1f2a19/i, 'appens mossa #1F2A19'],
  [/#2b3a23/i, 'appens mossa-2 #2B3A23'],
  [/#172013/i, 'appens djupa mossa #172013'],
  [/#18200f/i, 'Premiums mossgradient #18200F'],
  [/#26301f/i, 'olivbläck #26301F'],
  [/#5b6350/i, 'olivgrå #5B6350'],
  [/#3b4434/i, 'olivgrå brödtext #3B4434'],
  [/(?<!\d)31[,\s]+42[,\s]+25\b/, 'mossa som rgb (31, 42, 25)'],
  [/-moss\b/, 'mossnamn (--moss*, --color-moss)'],
];

const files = scanDirs.flatMap((dir) =>
  readdirSync(resolve(root, dir), { recursive: true })
    .map((f) => join(dir, String(f)).split('\\').join('/'))
    .filter((rel) => /\.(astro|css|ts|mjs|js|svg)$/.test(rel)),
);

const hits = [];
for (const rel of files) {
  if (allowed(rel)) continue;
  readFileSync(resolve(root, rel), 'utf8').split(/\r?\n/).forEach((line, i) => {
    for (const [re, what] of banned) if (re.test(line)) hits.push(`${rel}:${i + 1}  ${what}`);
  });
}

if (hits.length) {
  console.error(`palette-guard FAILED: ${hits.length} ställen med appens gröna färger utanför telefonerna`);
  for (const h of hits) console.error(`  ${h}`);
  process.exit(1);
}
console.log(`palette-guard OK (${files.length} filer, mossgrönt bara i telefonerna)`);
