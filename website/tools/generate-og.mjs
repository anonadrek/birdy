// Share images (1200×630), drawn in Chrome with the site's own fonts:
//   og-field-{en,sv}.jpg   the home pages and every page without its own: the espresso wall with a real, public domain
//                          robin photo hung as a plate beside the words. Nothing is laid over the photo (plan
//                          2026-10-08 house rules), and the photo is shown whole.
//   og-premium-{en,sv}.jpg the Premium page: espresso and brass like the app's Premium screen, with the app's own
//                          season statistics screen in a phone and a brass Premium seal.
// Run: npm run assets:og   (bump ?v= where the images are linked afterwards: Layout.astro, PremiumPage.astro)
import { chromium } from '@playwright/test';
import sharp from 'sharp';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const asset = (p) => pathToFileURL(resolve(root, p)).href;
// Rob Hille, public domain (src/assets/photos/SOURCES.md).
const robin = asset('src/assets/photos/rodhake-q25334.webp');

const fonts = `
@font-face { font-family: 'DM Serif Display'; src: url('${asset('public/fonts/dm-serif-display-regular.woff2')}') format('woff2'); }
@font-face { font-family: 'Caveat'; font-weight: 700; src: url('${asset('public/fonts/caveat-bold.woff2')}') format('woff2'); }
@font-face { font-family: 'Inter'; font-weight: 600; src: url('${asset('public/fonts/inter-semibold.woff2')}') format('woff2'); }
* { box-sizing: border-box; margin: 0; }
body { width: 1200px; height: 630px; overflow: hidden; background: #2A1D17; }
#card { position: relative; width: 1200px; height: 630px; overflow: hidden; color: #FFF8EE; font-family: 'Inter', sans-serif; }
.wm { position: absolute; left: 76px; top: 58px; font-family: 'Caveat'; font-weight: 700; font-size: 58px; line-height: 1; }
.kick { position: absolute; left: 78px; top: 190px; display: flex; align-items: center; gap: 14px; font-weight: 600; font-size: 17px; letter-spacing: .17em; text-transform: uppercase; }
.kick::before { content: ''; width: 30px; height: 1.5px; background: currentColor; }
h1 { position: absolute; left: 74px; top: 232px; width: 640px; font-family: 'DM Serif Display'; font-weight: 400; font-size: 78px; line-height: .98; letter-spacing: -.02em; }
h1 em { display: block; margin-top: 6px; font-family: 'Caveat'; font-weight: 700; font-style: normal; font-size: 96px; line-height: .95; letter-spacing: 0; }
.url { position: absolute; left: 78px; bottom: 58px; font-weight: 600; font-size: 19px; letter-spacing: .04em; color: rgba(255, 248, 238, .78); }`;

const field = (v) => `<style>${fonts}
#card { background: radial-gradient(60% 75% at 74% 46%, #3D2C22 0%, #2A1D17 55%, #1E1410 100%); }
.wm, .kick, h1 em { color: #F2B27A; }
.hang { position: absolute; right: 78px; top: 40px; width: 404px; }
.wire { position: absolute; left: 50%; top: 0; width: 200px; height: 40px; translate: -50% 0; }
.nail { position: absolute; left: 50%; top: -4px; width: 12px; height: 12px; margin-left: -6px; border-radius: 50%; background: radial-gradient(circle at 35% 30%, #E2C07E, #B8893A 55%, #241B0C); }
.frame { position: relative; margin-top: 38px; padding: 16px 16px 64px; background: #F3EADA; box-shadow: 0 2px 4px rgba(0,0,0,.35), 0 30px 50px -10px rgba(0,0,0,.6); }
.frame::before { content: ''; position: absolute; inset: 8px; border: 1px solid rgba(110, 88, 75, .35); }
.frame img { display: block; width: 100%; height: auto; }
.label { position: absolute; right: -26px; bottom: -38px; width: 250px; padding: 14px 18px 12px; background: #FFFAF1; color: #302019; transform: rotate(1.2deg); box-shadow: 0 16px 30px rgba(0,0,0,.4); }
.label b { display: block; font-family: 'DM Serif Display'; font-weight: 400; font-size: 34px; line-height: 1; }
.label i { display: block; margin-top: 2px; font-family: 'Caveat'; font-weight: 700; font-style: normal; font-size: 22px; color: #9A4526; }
</style><div id="card">
<div class="wm">Birdy.</div>
<p class="kick">${v.kicker}</p>
<h1>${v.line1}<em>${v.line2}</em></h1>
<p class="url">birdy.community</p>
<div class="hang">
  <svg class="wire" viewBox="0 0 200 40"><path d="M6 40 L100 3 L194 40" fill="none" stroke="#B8893A" stroke-width="1.6"/></svg>
  <span class="nail"></span>
  <div class="frame"><img src="${robin}" alt=""><div class="label"><b>${v.label}</b><i>${v.latin}</i></div></div>
</div>
</div>`;

const premium = (v) => `<style>${fonts}
#card { background: radial-gradient(55% 70% at 78% 40%, rgba(226, 192, 126, .2), transparent 62%), linear-gradient(180deg, #2A1D17 0%, #1E1410 100%); }
.wm { color: #F2B27A; }
.kick, h1 em { color: #E2C07E; }
h1 { top: 228px; }
.phone { position: absolute; right: 150px; top: 46px; width: 300px; padding: 10px; border-radius: 42px; background: #17100C; transform: rotate(-4deg); box-shadow: inset 0 0 0 1.5px rgba(255,248,238,.12), 0 40px 70px rgba(0,0,0,.55); }
.phone img { display: block; width: 100%; aspect-ratio: 9 / 16; object-fit: cover; object-position: top; border-radius: 32px; }
.seal { position: absolute; right: 92px; top: 92px; width: 112px; height: 112px; border-radius: 50%; display: grid; place-items: center; transform: rotate(-9deg); background: radial-gradient(circle at 35% 30%, rgba(255,255,255,.45), transparent 55%), #B8893A; color: #241B0C; font-family: 'DM Serif Display'; font-style: italic; font-size: 22px; box-shadow: 0 6px 14px rgba(0,0,0,.35); }
.seal::before { content: ''; position: absolute; inset: 6px; border-radius: 50%; border: 1px solid rgba(36, 27, 12, .3); }
</style><div id="card">
<div class="wm">Birdy.</div>
<p class="kick">Birdy Premium</p>
<h1>${v.line1}<em>${v.line2}</em></h1>
<p class="url">birdy.community/${v.locale === 'sv' ? 'sv/' : ''}premium</p>
<div class="phone"><img src="${asset(`src/assets/premium/emu-stats-${v.locale}-1.jpg`)}" alt=""></div>
<span class="seal">Premium</span>
</div>`;

const jobs = [
  { file: 'og-field-en.jpg', html: field({ locale: 'en', kicker: 'Bird guide and field journal', line1: 'Know the bird.', line2: 'Keep the moment.', label: 'European Robin', latin: 'Erithacus rubecula' }) },
  { file: 'og-field-sv.jpg', html: field({ locale: 'sv', kicker: 'Fågelguide och fältdagbok', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', label: 'Rödhake', latin: 'Erithacus rubecula' }) },
  { file: 'og-premium-en.jpg', html: premium({ locale: 'en', line1: 'A whole year as a', line2: 'field birder.' }) },
  { file: 'og-premium-sv.jpg', html: premium({ locale: 'sv', line1: 'Hela året som', line2: 'fältornitolog.' }) },
];

const dir = mkdtempSync(join(tmpdir(), 'birdy-og-'));
const browser = await chromium.launch(process.env.PLAYWRIGHT_CHANNEL ? { channel: process.env.PLAYWRIGHT_CHANNEL } : {});
try {
  const tab = await browser.newPage({ viewport: { width: 1200, height: 630 }, deviceScaleFactor: 1 });
  for (const job of jobs) {
    const file = join(dir, `${job.file}.html`);
    writeFileSync(file, `<!doctype html><html><head><meta charset="utf-8"></head><body>${job.html}</body></html>`);
    await tab.goto(pathToFileURL(file).href, { waitUntil: 'load' });
    await tab.evaluate(() => document.fonts.ready);
    const png = await tab.locator('#card').screenshot({ type: 'png' });
    await sharp(png).jpeg({ quality: 86, mozjpeg: true }).toFile(resolve(root, 'public', job.file));
    console.log(job.file);
  }
} finally {
  await browser.close();
  rmSync(dir, { recursive: true, force: true });
}
