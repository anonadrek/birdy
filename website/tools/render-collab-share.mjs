// The Birdy × AlbIT field note's photo (birdy-x-albit, 2026-10-10): the same wide picture as the page's hero
// (src/lib/collab-hero.mjs), rendered to src/assets/photos/birdy-x-albit.webp at 1600 × 840. It is the note's share
// image (cropped to 1200 × 630) and its card image on the blog and the home page. Run from website/ after changing the
// hero: node tools/render-collab-share.mjs
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { chromium } from 'playwright';
import sharp from 'sharp';
import { collabArt, collabDefs } from '../src/lib/collab-hero.mjs';

const root = resolve(import.meta.dirname, '..');
const out = join(root, 'src/assets/photos/birdy-x-albit.webp');
const wordmark = pathToFileURL(join(root, 'src/assets/brand/albit-wordmark-white.png')).href;
const caveat = pathToFileURL(join(root, 'public/fonts/caveat-bold.woff2')).href;

const html = `<!doctype html><meta charset="utf-8"><style>
@font-face { font-family: Caveat; src: url('${caveat}') format('woff2'); font-weight: 700; }
html, body { margin: 0; background: #111111; }
#art { position: relative; width: 1600px; height: 840px; }
#art svg.bxa-art { position: absolute; inset: 0; width: 100%; height: 100%; }
.bxa-word { font-family: Caveat; font-weight: 700; }
</style>${collabDefs()}<div id="art">${collabArt('wide', wordmark)}</div>`;

const dir = mkdtempSync(join(tmpdir(), 'bxa-'));
const page = join(dir, 'share.html');
writeFileSync(page, html);
const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
try {
  const tab = await browser.newPage({ viewport: { width: 1600, height: 840 }, deviceScaleFactor: 1 });
  await tab.goto(pathToFileURL(page).href, { waitUntil: 'load' });
  await tab.evaluate(() => document.fonts.ready);
  const png = await tab.locator('#art').screenshot();
  await sharp(png).webp({ quality: 90 }).toFile(out);
  const meta = await sharp(out).metadata();
  console.log(`${out} ${meta.width}x${meta.height}`);
} finally {
  await browser.close();
  rmSync(dir, { recursive: true, force: true });
}
