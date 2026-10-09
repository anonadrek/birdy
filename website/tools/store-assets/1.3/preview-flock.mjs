// The images for the comparison page docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/index.html:
// the current Espresso set (1.3.0) next to the Flock set (1.3.1-flock), each image as a JPEG of at most 300 KB, plus
// each set's six Swedish screenshots side by side as Play shows them (the Flock stream joins from card to card).
// Run from website/ after rendering variant F:
//   node tools/store-assets/1.3/preview-flock.mjs
import sharp from 'sharp';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '../../../..');
const sets = {
  espresso: join(repo, 'docs/play-store/store-assets/1.3.0'),
  flock: join(repo, 'docs/play-store/store-assets/1.3.1-flock'),
};
const out = join(repo, 'docs/superpowers/specs/assets/2026-10-10-butiksbilder-flock/img');
const cards = ['01-identifiera', '02-lyssna', '03-match', '04-mina-arter', '05-uppslagsverk', '06-artprofil'];
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

await mkdir(out, { recursive: true });
for (const [name, dir] of Object.entries(sets)) {
  for (const locale of ['sv', 'en']) {
    for (const id of cards) {
      await jpeg(await sharp(join(dir, locale, `${id}.png`)).resize(720, 1280, { kernel: 'lanczos3' }).toBuffer(), `${name}-${locale}-${id}.jpg`);
    }
    await jpeg(join(dir, locale, 'feature-graphic.png'), `${name}-${locale}-feature-graphic.jpg`);
  }
  // The six Swedish screenshots side by side with Play's small gap, 600 px high.
  const h = 600, w = Math.round((h * 1080) / 1920), gap = 10;
  const tiles = await Promise.all(cards.map((id) => sharp(join(dir, 'sv', `${id}.png`)).resize(w, h, { kernel: 'lanczos3' }).toBuffer()));
  const strip = await sharp({ create: { width: cards.length * (w + gap) - gap, height: h, channels: 3, background: '#FFFFFF' } })
    .composite(tiles.map((input, i) => ({ input, left: i * (w + gap), top: 0 })))
    .png()
    .toBuffer();
  await jpeg(strip, `${name}-sv-strip.jpg`);
}
console.log(`done: ${out}`);
