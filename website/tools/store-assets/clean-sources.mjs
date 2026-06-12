// Crops the Samsung OS status bar (top) and Android nav bar (bottom) off each
// raw screenshot, keeping the app's own bottom tab bar. Output: PNG in screens/.
import sharp from 'sharp';
import { readFile, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const cfg = JSON.parse(await readFile(join(here, 'cards.en.json'), 'utf8'));
const rawDir = resolve(here, cfg.rawDir);
const outDir = join(here, 'screens');
await mkdir(outDir, { recursive: true });

for (const card of cfg.cards) {
  const inPath = join(rawDir, card.raw);
  const meta = await sharp(inPath).metadata();
  const top = cfg.crop.top;
  const height = meta.height - cfg.crop.top - cfg.crop.bottom;
  const outPath = join(outDir, `${card.id}.png`);
  await sharp(inPath)
    .extract({ left: 0, top, width: meta.width, height })
    .png()
    .toFile(outPath);
  console.log(`cleaned ${card.id}: ${meta.width}x${height}`);
}
