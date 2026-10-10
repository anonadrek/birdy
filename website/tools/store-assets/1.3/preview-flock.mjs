// The images for the comparison page docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/index.html:
// the Flock set (1.3.1-flock, eight screenshots) next to the Espresso set (1.3.0, six) where Espresso has the same
// screen, each image as a JPEG of at most 300 KB, plus each set's Swedish screenshots side by side as Play shows them
// (the Flock stream joins from card to card). Run from website/ after rendering variant F:
//   node tools/store-assets/1.3/preview-flock.mjs
import sharp from 'sharp';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../../..');
const flockDir = join(repo, 'docs/play-store/store-assets/1.3.1-flock');
const espressoDir = join(repo, 'docs/play-store/store-assets/1.3.0');
const out = join(repo, 'docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/img');
// Flock card -> the Espresso card with the same screen (null: new in 1.3.1).
const pairs = {
  '01-identifiera': '01-identifiera',
  '02-lyssna': '02-lyssna',
  '03-match': '03-match',
  '04-artprofil': '06-artprofil',
  '05-mina-arter': '04-mina-arter',
  '06-statistik': null,
  '07-karta': null,
  '08-uppslagsverk': '05-uppslagsverk',
};
const espressoCards = ['01-identifiera', '02-lyssna', '03-match', '04-mina-arter', '05-uppslagsverk', '06-artprofil'];
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

const card = async (dir, locale, id) => sharp(join(dir, locale, `${id}.png`)).resize(720, 1280, { kernel: 'lanczos3' }).toBuffer();

// The Swedish screenshots side by side with Play's small gap, 600 px high.
async function strip(dir, ids, file) {
  const h = 600, w = Math.round((h * 1080) / 1920), gap = 10;
  const tiles = await Promise.all(ids.map((id) => sharp(join(dir, 'sv', `${id}.png`)).resize(w, h, { kernel: 'lanczos3' }).toBuffer()));
  const png = await sharp({ create: { width: ids.length * (w + gap) - gap, height: h, channels: 3, background: '#FFFFFF' } })
    .composite(tiles.map((input, i) => ({ input, left: i * (w + gap), top: 0 })))
    .png()
    .toBuffer();
  await jpeg(png, file);
}

await mkdir(out, { recursive: true });
for (const locale of ['sv', 'en']) {
  for (const [flock, espresso] of Object.entries(pairs)) {
    await jpeg(await card(flockDir, locale, flock), `flock-${locale}-${flock}.jpg`);
    if (espresso) await jpeg(await card(espressoDir, locale, espresso), `espresso-${locale}-${espresso}.jpg`);
  }
  await jpeg(join(flockDir, locale, 'feature-graphic.png'), `flock-${locale}-feature-graphic.jpg`);
  await jpeg(join(espressoDir, locale, 'feature-graphic.png'), `espresso-${locale}-feature-graphic.jpg`);
}
await strip(flockDir, Object.keys(pairs), 'flock-sv-strip.jpg');
await strip(espressoDir, espressoCards, 'espresso-sv-strip.jpg');
console.log(`done: ${out}`);
