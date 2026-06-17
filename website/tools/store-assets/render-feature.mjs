// Renders the 1024x500 Play Store feature graphic from feature-graphic.html.
// Same Playwright + Google-Fonts pipeline as render.mjs (the screenshot cards).
// Usage: node render-feature.mjs [--screen=03-journal]
import { chromium } from 'playwright';
import { mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const screen = (process.argv.find(a => a.startsWith('--screen='))?.split('=')[1]) || '03-journal';
const outDir = resolve(here, '../../../docs/play-store');
await mkdir(outDir, { recursive: true });

const templateUrl = pathToFileURL(join(here, 'feature-graphic.html')).href;
const screenUrl = pathToFileURL(join(here, 'screens', `${screen}.png`)).href;

const browser = await chromium.launch();
try {
  const page = await browser.newPage({ viewport: { width: 1024, height: 500 } });
  await page.goto(templateUrl, { waitUntil: 'networkidle' });
  await page.evaluate((u) => window.setScreen(u), screenUrl);
  await page.waitForFunction(() => document.fonts.status === 'loaded');
  const buf = await page.locator('#fg').screenshot();
  await page.close();
  const out = join(outDir, 'feature-graphic-1024x500.png');
  await writeFile(out, buf);
  console.log(`wrote ${out} (screen: ${screen})`);
} finally {
  await browser.close();
}
