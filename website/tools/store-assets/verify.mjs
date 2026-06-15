// Asserts the output contract for one locale: 8 Play PNGs (exactly 1080x1920,
// ratio <=2:1 per Play) plus the matching 8 website card images. Exit 1 on failure.
// Usage: node verify.mjs [--locale=en|sv]
import sharp from 'sharp';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const locale = (process.argv.find(a => a.startsWith('--locale='))?.split('=')[1]) || 'en';
const cfg = JSON.parse(await readFile(join(here, `cards.${locale}.json`), 'utf8'));

// Derive playDir from rawDir in the JSON — single source of truth
const rawBase = resolve(here, cfg.rawDir);
const playDir = join(rawBase, 'Play Store');
const webAssetsDir = resolve(here, '../../src/assets/screens');

let failures = 0;
const fail = (m) => { console.error('FAIL: ' + m); failures++; };

for (const card of cfg.cards) {
  const play = join(playDir, `${card.id}-${locale}.png`);
  try {
    const m = await sharp(play).metadata();
    if (m.width !== 1080 || m.height !== 1920) fail(`${card.id} play is ${m.width}x${m.height}, want 1080x1920`);
    if (m.height / m.width > 2) fail(`${card.id} ratio ${(m.height/m.width).toFixed(2)} exceeds Play 2:1`);
  } catch { fail(`${card.id} play PNG missing (${play})`); }

  const web = join(webAssetsDir, `card_${locale}_${card.id.slice(3)}.png`);
  try {
    const m = await sharp(web).metadata();
    if (m.width !== 1080 || m.height !== 1920) fail(`${card.id} website card is ${m.width}x${m.height}, want 1080x1920`);
  } catch { fail(`${card.id} website card missing (${web})`); }
}

if (failures) { console.error(`${failures} failure(s)`); process.exit(1); }
console.log(`[${locale}] all 8 Play PNGs 1080x1920 ≤2:1 + 8 website cards present — OK`);
