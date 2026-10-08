// Renders the mockups in ./mockups to ./img (JPG) with Playwright + Chrome, one page at a time.
// Usage (from the repo root, with website/node_modules installed, or PLAYWRIGHT_MODULE pointing at one):
//   node docs/superpowers/specs/assets/2026-10-08-webb-lyft-premium/render.cjs [name ...]
// Each page is shot full length at 390 px (2x) and 1440 px (1x). Chrome captures at most 16384 device
// pixels in one screenshot (longer pages repeat), so a long page gets a lower scale.
const path = require('path');
const fs = require('fs');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || path.join(__dirname, '../../../../../website/node_modules/playwright'));

const dir = __dirname;
const all = ['lift-a', 'lift-b', 'lift-c', 'premium-a', 'premium-a-en', 'premium-b', 'premium-c'];
const names = process.argv.slice(2).length ? process.argv.slice(2) : all;

(async () => {
  const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome' });
  for (const name of names) {
    const url = 'file:///' + path.join(dir, 'mockups', name + '.html').split(path.sep).join('/').replace(/^\/+/, '');
    for (const [tag, width, scale] of [['m', 390, 2], ['d', 1440, 1]]) {
      const probe = await browser.newContext({ viewport: { width, height: 900 } });
      const pp = await probe.newPage();
      await pp.goto(url);
      await pp.evaluate(() => document.fonts.ready);
      const tall = await pp.evaluate(() => document.documentElement.scrollHeight);
      await probe.close();
      const dsf = Math.min(scale, Math.floor((16000 / tall) * 100) / 100);
      const ctx = await browser.newContext({ viewport: { width, height: 900 }, deviceScaleFactor: dsf });
      const page = await ctx.newPage();
      await page.goto(url);
      await page.evaluate(() => document.fonts.ready);
      await page.waitForTimeout(250);
      const jpg = await page.screenshot({ fullPage: true, type: 'jpeg', quality: 82 });
      fs.writeFileSync(path.join(dir, 'img', `${name}-${tag}.jpg`), jpg);
      console.log(`${name}-${tag}.jpg`, width, 'x', tall, '@', dsf);
      await ctx.close();
    }
  }
  await browser.close();
})().catch((e) => { console.error(e); process.exit(1); });
