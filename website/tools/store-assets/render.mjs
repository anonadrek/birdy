// Renders each card to a Play PNG (1080x1920, paper bg) and a transparent web
// cut-out. Usage: node render.mjs [--locale en|sv]
import { chromium } from 'playwright';
import { readFile, mkdir, copyFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const locale = (process.argv.find(a => a.startsWith('--locale='))?.split('=')[1]) || 'en';
const cfg = JSON.parse(await readFile(join(here, `cards.${locale}.json`), 'utf8'));

const playDir = resolve(here, '../../../Birdy AB/Birdy Screendumps v.1.2rc/Play Store');
const webReviewDir = resolve(here, '../../../Birdy AB/Birdy Screendumps v.1.2rc/Website');
const webAssetsDir = resolve(here, '../../src/assets/screens');
for (const d of [playDir, webReviewDir, webAssetsDir]) await mkdir(d, { recursive: true });

const templateUrl = pathToFileURL(join(here, 'template.html')).href;
const browser = await chromium.launch();

for (const card of cfg.cards) {
  const screenUrl = pathToFileURL(join(here, 'screens', `${card.id}.png`)).href;

  // --- Play card: 1080x1920, paper background ---
  const playPage = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
  await playPage.goto(templateUrl, { waitUntil: 'networkidle' });
  await playPage.evaluate(([c, o]) => window.renderCard(c, o), [card, { mode: 'play', screenUrl, aspect: cfg.screenWindowAspect }]);
  await playPage.waitForFunction(() => document.fonts.status === 'loaded');
  const playName = `${card.id}-${locale}.png`;
  await playPage.locator('#card').screenshot({ path: join(playDir, playName) });
  await playPage.close();

  // --- Web cut-out: transparent, framed screen only ---
  const webPage = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 2 });
  await webPage.goto(templateUrl, { waitUntil: 'networkidle' });
  await webPage.evaluate(([c, o]) => window.renderCard(c, o), [card, { mode: 'web', screenUrl, aspect: cfg.screenWindowAspect }]);
  await webPage.waitForFunction(() => document.fonts.status === 'loaded');
  const cutout = await webPage.locator('#screen').screenshot({ omitBackground: true });
  await webPage.close();
  const { writeFile } = await import('node:fs/promises');
  const webName = `${card.id}.png`;
  await writeFile(join(webReviewDir, webName), cutout);
  await copyFile(join(webReviewDir, webName), join(webAssetsDir, `vc124_${card.id.slice(3)}.png`));

  console.log(`rendered ${card.id} (${locale})`);
}

await browser.close();
