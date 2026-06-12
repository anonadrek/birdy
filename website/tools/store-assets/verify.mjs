// Asserts the output contract: 8 Play PNGs, each exactly 1080x1920 and ratio <=2:1
// (Play requirement), plus 8 transparent web cut-outs. Exit 1 on any failure.
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

  const web = join(webAssetsDir, `vc124_${card.id.slice(3)}.png`);
  try {
    const m = await sharp(web).metadata();
    if (!m.hasAlpha) fail(`${card.id} web cut-out is not transparent`);
  } catch { fail(`${card.id} web cut-out missing (${web})`); }
}

if (failures) { console.error(`${failures} failure(s)`); process.exit(1); }
console.log('all 8 Play PNGs 1080x1920 ≤2:1, all 8 web cut-outs transparent — OK');
