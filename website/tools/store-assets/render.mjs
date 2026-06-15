// Renders each card to a 1080x1920 PNG (paper bg + copy). The same image is used
// for the Play Store listing AND the website Glimpse carousel (per locale).
// Usage: node render.mjs [--locale=en|sv]
import { chromium } from 'playwright';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const locale = (process.argv.find(a => a.startsWith('--locale='))?.split('=')[1]) || 'en';
const cfg = JSON.parse(await readFile(join(here, `cards.${locale}.json`), 'utf8'));

// Derive source dirs from rawDir in the JSON — rename the folder by editing rawDir there
const rawBase = resolve(here, cfg.rawDir);
const playDir = join(rawBase, 'Play Store');       // upload these to Play Console
const webReviewDir = join(rawBase, 'Website');     // review copies
const webAssetsDir = resolve(here, '../../src/assets/screens'); // imported by the site
for (const d of [playDir, webReviewDir, webAssetsDir]) await mkdir(d, { recursive: true });

const templateUrl = pathToFileURL(join(here, 'template.html')).href;
const browser = await chromium.launch();

try {
  for (const card of cfg.cards) {
    const screenUrl = pathToFileURL(join(here, 'screens', `${card.id}.png`)).href;
    const suffix = card.id.slice(3); // "01-identify" -> "identify"

    // 1080x1920 card, paper background. No deviceScaleFactor — Play requires exactly 1080x1920.
    const page = await browser.newPage({ viewport: { width: 1080, height: 1920 } });
    await page.goto(templateUrl, { waitUntil: 'networkidle' });
    await page.evaluate(([c, o]) => window.renderCard(c, o), [card, { mode: 'play', screenUrl, aspect: cfg.screenWindowAspect, total: cfg.cards.length }]);
    await page.waitForFunction(() => document.fonts.status === 'loaded');
    const buf = await page.locator('#card').screenshot();
    await page.close();

    await writeFile(join(playDir, `${card.id}-${locale}.png`), buf);          // Play Console
    await writeFile(join(webReviewDir, `card_${locale}_${suffix}.png`), buf); // review
    await writeFile(join(webAssetsDir, `card_${locale}_${suffix}.png`), buf); // website import

    console.log(`rendered ${card.id} (${locale})`);
  }
} finally {
  await browser.close();
}
