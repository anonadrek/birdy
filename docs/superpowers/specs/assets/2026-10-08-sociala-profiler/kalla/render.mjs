// Renders every <section class="art"> in brand-kit.html to a PNG at its own size.
//   node render.mjs [outDir] [id ...]
// outDir defaults to the folder above (the files Albin uploads). Playwright comes from tools/social/node_modules.
import { pathToFileURL, fileURLToPath } from 'node:url';
import path from 'node:path';
import fs from 'node:fs';

const here = path.dirname(fileURLToPath(import.meta.url));
const repo = path.resolve(here, '../../../../../..');
const { chromium } = await import(pathToFileURL(path.join(repo, 'tools/social/node_modules/playwright/index.mjs')).href);

const outDir = path.resolve(process.argv[2] || path.join(here, '..'));
const only = process.argv.slice(3);
fs.mkdirSync(outDir, { recursive: true });

const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
const page = await browser.newPage({ viewport: { width: 2700, height: 1600 }, deviceScaleFactor: 1 });
page.on('console', (m) => { if (m.type() === 'error') console.error('page:', m.text()); });
page.on('pageerror', (e) => console.error('page error:', e.message));
await page.goto(pathToFileURL(path.join(here, 'brand-kit.html')).href);
await page.evaluate(() => window.ready);
await page.waitForTimeout(250);
// Archived artboards (directions A and B) render only when asked for by id.
const items = await page.locator(only.length ? 'section.art' : 'section.art:not([data-archived])').evaluateAll((els) => els.map((e) => ({ id: e.id, dir: e.dataset.dir || '' })));
for (const { id, dir } of items) {
  if (only.length && !only.includes(id)) continue;
  // data-dir puts an artboard in a subfolder (the grid row and its tiles go to grid/).
  const file = path.join(outDir, dir, `${id}.png`);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  await page.locator(`#${id}`).screenshot({ path: file, animations: 'disabled' });
  console.log(`${path.join(dir, id)}.png  ${(fs.statSync(file).size / 1024).toFixed(0)} KB`);
}
await browser.close();
