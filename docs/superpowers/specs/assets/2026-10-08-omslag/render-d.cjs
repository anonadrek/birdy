// Renders direction D (the flock as each species' silhouette) over a local http server,
// so the silhouette can be read back from the canvas (file:// would taint it).
const path = require('path');
const { chromium } = require('C:/Users/abbea/dev/1-mina-projekt/birdy/website/node_modules/playwright');

const base = process.argv[2] || 'http://127.0.0.1:8765';
const only = process.argv[3];

(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  page.on('pageerror', (e) => console.log('pageerror', e.message));
  for (let i = 0; i < 9; i++) {
    if (only && only !== String(i)) continue;
    await page.goto(`${base}/cover.html?d=D&i=${i}`);
    await page.evaluate(() => window.__ready);
    await page.waitForTimeout(150);
    const count = await page.evaluate(() => window.__count);
    await page.screenshot({ path: path.join(__dirname, 'out', `D-${i}.jpg`), type: 'jpeg', quality: 90 });
    console.log(`D${i} birds=${count}`);
  }
  await browser.close();
})();
