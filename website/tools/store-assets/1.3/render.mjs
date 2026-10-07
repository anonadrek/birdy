// Butiksbilder 1.3.0 (Plan 3 Task 9): renders the store screenshots (1080x1920) and the feature
// graphic (1024x500) for each look variant and locale from template.html + copy.json.
// One page, one image at a time (the machine is shared). Output is PNG without an alpha channel
// (Play: PNG or JPEG, no alpha); --preview also writes JPEG copies for the review page.
//
// Usage (from website/):
//   node tools/store-assets/1.3/render.mjs --variants=a,b,c --locales=sv,en \
//     [--out=tools/store-assets/1.3/out] [--preview=../docs/superpowers/specs/assets/2026-10-08-butiksbilder/img]
import { chromium } from 'playwright';
import sharp from 'sharp';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const arg = (name, dflt) => process.argv.find((a) => a.startsWith(`--${name}=`))?.split('=')[1] ?? dflt;
const variants = arg('variants', 'a,b,c').split(',');
const locales = arg('locales', 'sv,en').split(',');
const what = arg('only', 'all'); // all | cards | feature
const outDir = resolve(process.cwd(), arg('out', join(here, 'out')));
const previewArg = arg('preview', '');
const previewDir = previewArg ? resolve(process.cwd(), previewArg) : '';

const copy = JSON.parse(await readFile(join(here, 'copy.json'), 'utf8'));
const repo = resolve(here, '../../../..');
const screenUrl = (loc, id) => pathToFileURL(join(repo, 'docs/play-store/screenshots/1.3.0', loc, `${id}.png`)).href;
const photoUrl = pathToFileURL(join(repo, 'website/src/assets/photos', copy.feature.plate.photo)).href;

await mkdir(outDir, { recursive: true });
if (previewDir) await mkdir(previewDir, { recursive: true });

async function save(buf, name, w, h) {
  const png = await sharp(buf).flatten({ background: '#000000' }).removeAlpha().png({ compressionLevel: 9 }).toBuffer();
  const meta = await sharp(png).metadata();
  if (meta.width !== w || meta.height !== h || meta.channels !== 3 || meta.hasAlpha) {
    throw new Error(`${name}: ${meta.width}x${meta.height}, ${meta.channels} channels, alpha=${meta.hasAlpha}`);
  }
  await writeFile(join(outDir, `${name}.png`), png);
  if (previewDir) await sharp(png).jpeg({ quality: 82, mozjpeg: true }).toFile(join(previewDir, `${name}.jpg`));
  console.log(`  ${name}.png ${w}x${h} RGB`);
}

const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
try {
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  await page.goto(pathToFileURL(join(here, 'template.html')).href, { waitUntil: 'load' });
  for (const variant of variants) {
    for (const locale of locales) {
      console.log(`variant ${variant} / ${locale}`);
      if (what !== 'feature') {
        for (const [index, card] of copy.cards.entries()) {
          await page.evaluate((o) => window.render(o), {
            mode: 'card', variant, locale, card, index, last: index === copy.cards.length - 1,
            screen: screenUrl(locale, card.id),
          });
          await save(await page.locator('#c').screenshot(), `${variant}-${locale}-${card.id}`, 1080, 1920);
        }
      }
      if (what !== 'cards') {
        await page.evaluate((o) => window.render(o), {
          mode: 'feature', variant, locale, feature: copy.feature, photo: photoUrl,
          screens: [screenUrl(locale, '01-identifiera'), screenUrl(locale, '02-match')],
        });
        await save(await page.locator('#c').screenshot(), `${variant}-${locale}-feature`, 1024, 500);
      }
    }
  }
  await page.close();
} finally {
  await browser.close();
}
