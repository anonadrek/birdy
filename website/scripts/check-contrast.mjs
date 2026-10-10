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
const hexToRgb = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
};

let failed = false;

// --dark-rgb måste vara samma triplet som --dark: några CSS-regler skriver rgba(var(--dark-rgb), a)
// eftersom rgba() inte kan ta en #hex-variabel direkt, så de två får aldrig gå isär.
const darkRgbMatch = css.match(/--dark-rgb:\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*;/);
if (!tokens.dark || !darkRgbMatch) {
  console.error('contrast-guard FAILED: --dark eller --dark-rgb saknas i tokens.css');
  failed = true;
} else {
  const darkRgbToken = darkRgbMatch.slice(1, 4).map(Number);
  const darkRgbFromHex = hexToRgb(tokens.dark);
  if (darkRgbToken.some((v, i) => v !== darkRgbFromHex[i])) {
    console.error(
      `contrast-guard FAILED: --dark-rgb (${darkRgbToken.join(', ')}) matchar inte --dark (${darkRgbFromHex.join(', ')})`,
    );
    failed = true;
  }
}

// [text, background, minimum ratio]
const pairs = [
  ['ink', 'paper', 4.5], ['muted', 'paper', 4.5], ['rust', 'paper', 4.5],
  ['ink', 'card', 4.5], ['muted', 'card', 4.5], ['rust', 'card', 4.5],
  ['ink', 'peach', 4.5], ['muted', 'peach', 4.5], ['rust', 'peach', 4.5],
  // The home hero's peach paper (spec 2026-10-09-startsidan-flocken-lyfter): the words sit on it from its lightest
  // tone to its deepest.
  ['ink', 'peach-hi', 4.5], ['muted', 'peach-hi', 4.5], ['rust', 'peach-hi', 4.5],
  ['ink', 'peach-lo', 4.5], ['muted', 'peach-lo', 4.5], ['rust', 'peach-lo', 4.5],
  ['cream', 'dark', 4.5], ['apricot', 'dark', 4.5], ['brass-hi', 'dark', 4.5],
  ['cream', 'dark-deep', 4.5], ['apricot', 'dark-deep', 4.5],
  ['cream', 'rust', 4.5], ['cream', 'rust-deep', 4.5],
  ['brass-ink', 'brass', 4.5], ['brass-ink', 'brass-hi', 4.5],
  // AppTour.astro on the light gallery wall (2026-10-10): the Premium plate labels in deep brass, over the wall's peach
  // from its pool of light to its edges (the muted labels and the rust plate numbers are the peach pairs above).
  ['brass-deep', 'peach-hi', 4.5], ['brass-deep', 'peach', 4.5], ['brass-deep', 'peach-lo', 4.5],
];

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
// eller bakgrunden i Footer.astro/Premium.astro, uppdatera paret här också — varje CSS-regel har
// en kommentar ("alpha checked in scripts/check-contrast.mjs") som pekar tillbaka hit.
const luminanceRgb = ([r, g, b]) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
const ratioRgb = (a, b) => {
  const [hi, lo] = [luminanceRgb(a), luminanceRgb(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};
const compositeOver = (fg, alpha, bg) => fg.map((c, i) => alpha * c + (1 - alpha) * bg[i]);

const darkDeep = tokens['dark-deep'] ? hexToRgb(tokens['dark-deep']) : null;
// Ljusaste punkten i Premiums espressogradient: mässingsglöden från .prem::before (10 % av
// rgba(226, 192, 126)) över --dark, mitt i den radiella höjdpunkten. Finns inte som egen token,
// så den härleds ur --dark (rundar till #3C2D21) istället för att stå hårdkodad separat.
const premiumGradientLight = tokens.dark ? compositeOver([226, 192, 126], 0.1, hexToRgb(tokens.dark)) : null;

const compositedPairs = [
  { label: 'Footer .fbot', fg: [233, 226, 210], alpha: 0.55, bg: darkDeep, min: 4.5 },
  { label: 'Footer .sib-kick', fg: [233, 226, 210], alpha: 0.6, bg: darkDeep, min: 4.5 },
  { label: 'Premium .pnote', fg: [242, 234, 220], alpha: 0.62, bg: premiumGradientLight, min: 4.5 },
  { label: 'Premium .feat p', fg: [242, 234, 220], alpha: 0.66, bg: premiumGradientLight, min: 4.5 },
  // Nav.astro: the links (.82) on the solid paper bar (Albin 2026-10-10: the light bar replaced the espresso one).
  { label: 'Nav .links a (paper bar)', fg: tokens.ink ? hexToRgb(tokens.ink) : null, alpha: 0.82, bg: tokens.paper ? hexToRgb(tokens.paper) : null, min: 4.5 },
  // FinalCta.astro .sub (.85) on the espresso wall, over its lightest point (#3D2C22). The hero left the wall on 2026-10-09.
  { label: 'FinalCta .sub', fg: [255, 248, 238], alpha: 0.85, bg: [61, 44, 34], min: 4.5 },
  // PremiumPage.astro: the faintest text (.fine, .66) on the dark bands, the card text (.78) on the chosen price card's
  // brass tint, and the breadcrumbs (.78) in the hero's brass glow.
  { label: 'PremiumPage .fine', fg: [242, 234, 220], alpha: 0.66, bg: premiumGradientLight, min: 4.5 },
  { label: 'PremiumPage .pcard.sel', fg: [242, 234, 220], alpha: 0.78, bg: tokens.dark ? compositeOver([226, 192, 126], 0.07, hexToRgb(tokens.dark)) : null, min: 4.5 },
  { label: 'PremiumPage .crumbs', fg: [242, 234, 220], alpha: 0.78, bg: tokens.dark ? compositeOver([226, 192, 126], 0.14, hexToRgb(tokens.dark)) : null, min: 4.5 },
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
