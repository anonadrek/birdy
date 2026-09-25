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

// Genomskinliga textfärger: några komponenter skriver texten som rgba(...) direkt i <style>
// (inte en token), så vakten ovan ser dem aldrig. De alfa-blandas här mot den riktiga bakgrunden
// (c = a*fg + (1-a)*bg per kanal) innan samma WCAG-kontroll körs. Ändras en av rgba()-färgerna
// eller bakgrunden i Footer.astro/Premium.astro/AppTour.astro, uppdatera paret här också — varje CSS-regel har
// en kommentar ("alpha checked in scripts/check-contrast.mjs") som pekar tillbaka hit.
const hexToRgb = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};
const luminanceRgb = ([r, g, b]) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
const ratioRgb = (a, b) => {
  const [hi, lo] = [luminanceRgb(a), luminanceRgb(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};
const compositeOver = (fg, alpha, bg) => fg.map((c, i) => alpha * c + (1 - alpha) * bg[i]);

const mossDeep = tokens['moss-deep'] ? hexToRgb(tokens['moss-deep']) : null;
const moss2 = tokens['moss-2'] ? hexToRgb(tokens['moss-2']) : null;
// Ljusaste punkten i Premiums mossgröna gradient (mossa + mässingsglöden från .prem::before,
// mätt mitt i den radiella höjdpunkten) — finns inte som token, bara ett uppmätt läge.
const premiumGradientLight = hexToRgb('#323822');

const compositedPairs = [
  { label: 'Footer .fbot', fg: [233, 226, 210], alpha: 0.55, bg: mossDeep, min: 4.5 },
  { label: 'Footer .sib-kick', fg: [233, 226, 210], alpha: 0.6, bg: mossDeep, min: 4.5 },
  { label: 'Premium .pnote', fg: [242, 234, 220], alpha: 0.62, bg: premiumGradientLight, min: 4.5 },
  { label: 'Premium .feat p', fg: [242, 234, 220], alpha: 0.66, bg: premiumGradientLight, min: 4.5 },
  { label: 'AppTour .cap-text', fg: [242, 234, 220], alpha: 0.72, bg: moss2, min: 4.5 },
  { label: 'AppTour .tour-lead', fg: [242, 234, 220], alpha: 0.72, bg: moss2, min: 4.5 },
];

for (const { label, fg, alpha, bg, min } of compositedPairs) {
  if (!bg) {
    console.error(`contrast-guard FAILED: bakgrund saknas för ${label}`);
    failed = true;
    continue;
  }
  const composited = compositeOver(fg, alpha, bg);
  const r = ratioRgb(composited, bg);
  if (r < min) {
    console.error(`contrast-guard FAILED: ${label} = ${r.toFixed(2)}:1 (kräver ${min}:1)`);
    failed = true;
  }
}

const totalPairs = pairs.length + compositedPairs.length;
if (failed) process.exit(1);
console.log(`contrast-guard OK (${totalPairs} par)`);
