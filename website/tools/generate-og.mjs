// Share images (1200×630), drawn in Chrome with the site's own fonts:
//   og-field-{en,sv}.jpg   the home pages and every page without its own: the home page's first view in the Flock look
//                          (spec 2026-10-09): peach paper, the slogan, the landed flock as Birdy's bird (flockSvg in
//                          src/components/hero/flock.mjs, the frame the page shows) with the robin's own bird lit, and
//                          a real, public domain robin photo as a taped polaroid, shown whole with nothing on it.
//   og-premium-{en,sv}.jpg the Premium page: espresso and brass like the app's Premium screen, with the app's own
//                          season statistics screen in a phone and a brass Premium seal.
// Run: npm run assets:og   (bump ?v= where the images are linked afterwards: Layout.astro, PremiumPage.astro)
import { chromium } from '@playwright/test';
import sharp from 'sharp';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { flockIndexFor, flockSvg } from '../src/components/hero/flock.mjs';

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

// The robin's own bird in the flock (flockIndexFor), lit like Dagens fågel on the page.
const robinFlock = flockSvg({ litIndex: flockIndexFor('Q25334') });

const field = (v) => `<style>${fonts}
#card { color: #302019; background: radial-gradient(130% 120% at 70% 40%, #FEEBD6 0%, #FDE5CB 45%, #F8D6B4 100%); }
.wm { color: #302019; }
.kick { top: 168px; color: #9A4526; }
h1 { top: 206px; width: 560px; font-size: 74px; }
h1 em { color: #9A4526; font-size: 84px; transform: rotate(-1.6deg); transform-origin: left; }
.url { top: 396px; bottom: auto; color: #6E584B; }
.fit { position: absolute; right: 34px; top: 22px; width: 552px; height: 584px; }
.fit svg { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
.pol { position: absolute; right: 68%; bottom: 3%; width: 230px; padding: 12px 10px 0; background: #FFFAF1; box-shadow: 0 14px 34px rgba(60, 34, 20, .22), 0 1px 3px rgba(60, 34, 20, .14); transform: rotate(2deg); }
.pol img { display: block; width: 100%; height: auto; }
.pol b { display: block; padding: 8px 2px 12px; font-family: 'Caveat'; font-weight: 700; font-size: 26px; line-height: 1; color: #302019; }
.tape { position: absolute; top: -18px; left: 50%; width: 84px; height: 22px; margin-left: -42px; transform: rotate(-8deg); background: linear-gradient(180deg, #F5C99B, #F0BB86); box-shadow: 0 1px 3px rgba(80, 50, 30, .2); opacity: .94; }
</style><div id="card">
<div class="wm">Birdy.</div>
<p class="kick">${v.kicker}</p>
<h1>${v.line1}<em>${v.line2}</em></h1>
<p class="url">birdy.community</p>
<div class="fit">${robinFlock}<figure class="pol"><img src="${robin}" alt=""><b>${v.label}</b><span class="tape"></span></figure></div>
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
  { file: 'og-field-en.jpg', html: field({ kicker: 'Camera, photo or song', line1: 'Know the bird.', line2: 'Keep the moment.', label: 'European Robin' }) },
  { file: 'og-field-sv.jpg', html: field({ kicker: 'Kamera, foto eller läte', line1: 'Känn igen fågeln.', line2: 'Bevara stunden.', label: 'Rödhake' }) },
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
