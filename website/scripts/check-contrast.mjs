#!/usr/bin/env node
// WCAG 2.1 contrast guard for the text colour pairs the site uses (palette in src/styles/tokens.css).
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

const stripComments = (s) => {
  let out = '';
  let i = 0;
  while (i < s.length) {
    const start = s.indexOf('/*', i);
    if (start === -1) { out += s.slice(i); break; }
    out += s.slice(i, start);
    const end = s.indexOf('*/', start + 2);
    i = end === -1 ? s.length : end + 2;
  }
  return out;
};

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const css = stripComments(readFileSync(resolve(root, 'src/styles/tokens.css'), 'utf8'));
const tokens = Object.fromEntries(
  [...css.matchAll(/--([a-z0-9-]+):\s*(#[0-9a-fA-F]{6})\s*;/g)].map((m) => [m[1], m[2]]),
);

const channel = (v) => {
  const c = v / 255;
  return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
};
const luminance = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
};
const ratio = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

// [text, background, minimum ratio]
const pairs = [
  ['ink', 'paper', 4.5], ['muted', 'paper', 4.5], ['rust', 'paper', 4.5],
  ['ink', 'card', 4.5], ['muted', 'card', 4.5], ['rust', 'card', 4.5],
  ['cream', 'moss', 4.5], ['apricot', 'moss', 4.5], ['brass-hi', 'moss', 4.5],
  ['cream', 'moss-deep', 4.5], ['apricot', 'moss-deep', 4.5],
  ['cream', 'rust', 4.5], ['cream', 'rust-deep', 4.5],
  ['brass-ink', 'brass', 4.5],
];

let failed = false;
for (const [fg, bg, min] of pairs) {
  const missing = [fg, bg].find((name) => !tokens[name]);
  if (missing) {
    console.error(`contrast-guard FAILED: token --${missing} saknas eller är inte #RRGGBB i tokens.css`);
    failed = true;
    continue;
  }
  const r = ratio(tokens[fg], tokens[bg]);
  if (r < min) {
    console.error(`contrast-guard FAILED: --${fg} på --${bg} = ${r.toFixed(2)}:1 (kräver ${min}:1)`);
    failed = true;
  }
}
if (failed) process.exit(1);
console.log(`contrast-guard OK (${pairs.length} par)`);
