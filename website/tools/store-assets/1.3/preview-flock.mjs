// The images for the review page docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/index.html: the 1.3.1 set
// (eight screenshots and the feature graphic per language, website/tools/store-assets/1.3 variant F) as JPEGs of at most
// 300 KB, plus each language's screenshots side by side as Play shows them (the flock joins from card to card).
// Run from website/ after rendering variant F:
//   node tools/store-assets/1.3/render.mjs --final --variants=f --out=../docs/play-store/store-assets/1.3.1-flock
//   node tools/store-assets/1.3/preview-flock.mjs
import sharp from 'sharp';
import { mkdir, readdir, rm, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../../..');
const setDir = join(repo, 'docs/play-store/store-assets/1.3.1-flock');
const out = join(repo, 'docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/img');
const cards = ['01-identifiera', '02-lyssna', '03-match', '04-artprofil', '05-mina-arter', '06-statistik', '07-karta', '08-uppslagsverk'];
const BUDGET = 300 * 1024;

// Writes the image as a JPEG at the highest quality (from 86 down) that stays within the budget.
async function jpeg(input, file) {
  for (let q = 86; q >= 50; q -= 4) {
    const buf = await sharp(input).jpeg({ quality: q, mozjpeg: true }).toBuffer();
    if (buf.length <= BUDGET) {
      await writeFile(join(out, file), buf);
      console.log(`  ${file} q${q} ${(buf.length / 1024).toFixed(0)} KB`);
      return;
    }
  }
  throw new Error(`${file}: more than ${BUDGET} bytes even at quality 50`);
}

// The screenshots side by side with Play's small gap, 560 px high.
async function strip(locale, file) {
  const h = 560, w = Math.round((h * 1080) / 1920), gap = 10;
  const tiles = await Promise.all(cards.map((id) => sharp(join(setDir, locale, `${id}.png`)).resize(w, h, { kernel: 'lanczos3' }).toBuffer()));
  const png = await sharp({ create: { width: cards.length * (w + gap) - gap, height: h, channels: 3, background: '#FFFFFF' } })
    .composite(tiles.map((input, i) => ({ input, left: i * (w + gap), top: 0 })))
    .png()
    .toBuffer();
  await jpeg(png, file);
}

await mkdir(out, { recursive: true });
// The page only shows this set; earlier comparison images (Espresso, the first Flock round) go.
for (const old of await readdir(out)) await rm(join(out, old));
for (const locale of ['sv', 'en']) {
  for (const id of cards) {
    await jpeg(await sharp(join(setDir, locale, `${id}.png`)).resize(720, 1280, { kernel: 'lanczos3' }).toBuffer(), `${locale}-${id}.jpg`);
  }
  await jpeg(join(setDir, locale, 'feature-graphic.png'), `${locale}-feature.jpg`);
  await strip(locale, `${locale}-strip.jpg`);
}
console.log(`done: ${out}`);
