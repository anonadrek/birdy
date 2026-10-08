// Renders the cover directions at 1080x1920 (scratchpad only).
const path = require('path');
const { chromium } = require('C:/Users/abbea/dev/1-mina-projekt/birdy/website/node_modules/playwright');

const dir = __dirname;
const only = process.argv[2]; // e.g. "A0" to render one

(async () => {
  const browser = await chromium.launch({ channel: 'chrome' });
  const page = await browser.newPage({ viewport: { width: 1080, height: 1920 }, deviceScaleFactor: 1 });
  for (const d of ['A', 'B', 'C']) {
    for (let i = 0; i < 9; i++) {
      if (only && only !== `${d}${i}`) continue;
      const url = 'file:///' + path.join(dir, 'cover.html').replace(/\\/g, '/') + `?d=${d}&i=${i}`;
      await page.goto(url);
      await page.evaluate(() => window.__ready);
      await page.waitForTimeout(150);
      await page.screenshot({ path: path.join(dir, 'out', `${d}-${i}.jpg`), type: 'jpeg', quality: 90 });
      process.stdout.write(`${d}${i} `);
    }
  }
  await browser.close();
  console.log('done');
})();
