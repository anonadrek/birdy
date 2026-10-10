import { test, expect, type Locator, type Page } from '@playwright/test';
import sharp from 'sharp';
import { trackConsoleErrors } from './test-helpers';
import { FLOCK } from '../src/components/hero/flock-data.mjs';
import { DISC_SCALE, fitView, landing } from '../src/components/hero/flock.mjs';

type Box = { left: number; top: number; right: number; bottom: number };

// Birdy's channels (2026-10-08). The addresses are written out here rather than read from src/lib/links.ts, so a
// typo there fails the test. Instagram is @app.birdy: @birdy.community on Instagram belongs to someone else.
const channels = [
  ['Instagram', 'https://www.instagram.com/app.birdy/'],
  ['Facebook', 'https://www.facebook.com/profile.php?id=61595339305266'],
  ['YouTube', 'https://www.youtube.com/@birdy.community'],
  ['TikTok', 'https://www.tiktok.com/@birdy.app'],
] as const;

// WCAG contrast helpers for the pixel-contrast test (1c): hide the text, screenshot the real
// background behind it, composite the text's own colour (and any element opacity) over that
// measured background, and compute the standard relative-luminance contrast ratio. Mirrors the
// method the code review itself used, and scripts/check-contrast.mjs's formulas for solid tokens.
function relLuminance([r, g, b]: number[]): number {
  const channel = (v: number) => {
    const c = v / 255;
    return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}
function contrastRatio(a: number[], b: number[]): number {
  const [hi, lo] = [relLuminance(a), relLuminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
}
function parseCssColor(str: string): { rgb: number[]; a: number } {
  const parts = str.match(/rgba?\(([^)]+)\)/)![1].split(',').map((s) => parseFloat(s));
  return { rgb: parts.slice(0, 3), a: parts.length > 3 ? parts[3] : 1 };
}
async function avgColorInBox(page: Page, box: { x: number; y: number; width: number; height: number }): Promise<number[]> {
  const buf = await page.screenshot({ clip: box });
  const { data } = await sharp(buf).resize(1, 1, { fit: 'fill' }).raw().toBuffer({ resolveWithObject: true });
  return [data[0], data[1], data[2]];
}
/** Contrast of `locator`'s own computed text colour (colour alpha × element opacity), composited over its real measured background, with the locator's own text hidden first. */
async function textContrastAgainstBackground(page: Page, locator: Locator): Promise<number> {
  const box = await locator.boundingBox();
  if (!box) throw new Error('element not visible for contrast measurement');
  const bg = await avgColorInBox(page, box);
  const { color, opacity } = await locator.evaluate((el) => ({
    color: getComputedStyle(el).color,
    opacity: parseFloat(getComputedStyle(el).opacity),
  }));
  const { rgb: fg, a: colorAlpha } = parseCssColor(color);
  const alpha = colorAlpha * opacity;
  const effective = [0, 1, 2].map((i) => alpha * fg[i] + (1 - alpha) * bg[i]);
  return contrastRatio(effective, bg);
}

// The first view's geometry (the 'första vyn' tests): every box in page coordinates, and the tape and the photo also
// in the polaroid's own frame. Bird positions come from the same fitView as the canvas and the <noscript> SVG.
async function heroGeometry(page: Page) {
  await page.evaluate(() => document.fonts.ready);
  await page.locator('[data-hero] [data-polaroid] img').evaluate((i: HTMLImageElement) => i.decode());
  return page.evaluate(() => {
    const box = (el: Element) => {
      const r = el.getBoundingClientRect();
      return { left: r.left, top: r.top + scrollY, right: r.right, bottom: r.bottom + scrollY };
    };
    const $ = (selector: string) => document.querySelector(selector) as HTMLElement;
    const card = $('[data-hero] [data-polaroid]');
    const img = card.querySelector('img') as HTMLImageElement;
    const tape = card.querySelector('.tape') as HTMLElement;
    const out = {
      hero: box($('[data-hero]')),
      canvas: box($('[data-hero] [data-flock-canvas]')),
      fit: box($('[data-hero] [data-flock-fit]')),
      navHeight: $('#site-nav').getBoundingClientRect().height,
      words: ['.intro .kick', 'h1', '.intro .sub', '.intro .badges'].map((s) => box($(`[data-hero] ${s}`))),
      card: box(card),
      tape: box(tape),
      photo: { width: img.offsetWidth, height: img.offsetHeight, ratio: img.naturalWidth / img.naturalHeight },
      scrollWidth: document.documentElement.scrollWidth,
    };
    // The tape against the photo in the card's own frame (the card leans 2 degrees on the page).
    // Reduced motion still runs a 0.01 ms transition (global.css), so turn transitions off to read the card's own frame.
    card.style.transition = 'none';
    card.style.transform = 'none';
    const own = { tape: box(tape), photo: box(img) };
    card.style.transform = '';
    card.style.transition = '';
    return { ...out, own };
  });
}

const overlaps = (a: Box, b: Box, gap: number) => a.left < b.right + gap && a.right > b.left - gap && a.top < b.bottom + gap && a.bottom > b.top - gap;
const touches = (x: number, y: number, r: number, b: Box) => {
  const cx = Math.max(b.left, Math.min(x, b.right));
  const cy = Math.max(b.top, Math.min(y, b.bottom));
  return (x - cx) ** 2 + (y - cy) ** 2 < r * r;
};

// What every layout of the first view must keep: the polaroid (with its tape) inside the hero, under the menu and off
// the words; every bird that can be lit (the FLOCK.edge birds of the heart, one of which stands for each species)
// inside the hero and out from under the polaroid; no landed bird under the words; no sideways scroll. `where` prefixes
// the messages when one test checks several pages or sizes.
function expectHeroClear(g: Awaited<ReturnType<typeof heroGeometry>>, width: number, where = '') {
  const card: Box = {
    left: Math.min(g.card.left, g.tape.left),
    top: Math.min(g.card.top, g.tape.top),
    right: Math.max(g.card.right, g.tape.right),
    bottom: Math.max(g.card.bottom, g.tape.bottom),
  };
  expect(card.left, `${where}polaroiden inom hjälten`).toBeGreaterThanOrEqual(0);
  expect(card.right, `${where}polaroiden inom hjälten`).toBeLessThanOrEqual(width);
  expect(card.top, `${where}polaroiden under menyn`).toBeGreaterThanOrEqual(g.hero.top + g.navHeight);
  expect(card.bottom, `${where}polaroiden inom hjälten`).toBeLessThanOrEqual(g.hero.bottom);
  for (const [i, word] of g.words.entries()) expect(overlaps(card, word, 8), `${where}polaroiden över orden (${i})`).toBe(false);
  const fit = fitView({ left: g.fit.left - g.canvas.left, top: g.fit.top - g.canvas.top, width: g.fit.right - g.fit.left, height: g.fit.bottom - g.fit.top });
  for (let i = 0; i < FLOCK.edge; i += 1) {
    const b = landing(FLOCK.birds[i], fit);
    const x = b.x + g.canvas.left;
    const y = b.y + g.canvas.top;
    const r = b.size * DISC_SCALE + 3;
    expect(touches(x, y, r, card), `${where}fågel ${i} under polaroiden`).toBe(false);
    expect(x - r >= 0 && x + r <= width && y - r >= g.hero.top + g.navHeight && y + r <= g.hero.bottom, `${where}fågel ${i} inom hjälten`).toBe(true);
  }
  const under = FLOCK.birds.filter((bird) => {
    const b = landing(bird, fit);
    return g.words.some((word) => touches(b.x + g.canvas.left, b.y + g.canvas.top, b.size / 2, word));
  });
  expect(under.length, `${where}landade fåglar under orden`).toBe(0);
  expect(g.scrollWidth, `${where}inget sidledes scroll`).toBeLessThanOrEqual(width);
}

test.describe('meny och sidfot', () => {
  for (const [path, label, getApp] of [['/sv/', 'Möt fåglarna', 'Hämta appen'], ['/', 'Meet the birds', 'Get the app']] as const) {
    test(`menyn på ${path} har nya länkar och blir ljust papper efter första vyn`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(path);
      const nav = page.locator('#site-nav');
      await expect(nav.locator('.links a').first()).toHaveText(label);
      await expect(nav.locator('.nav-cta')).toHaveText(getApp);
      await expect(nav.locator('.nav-cta')).toHaveAttribute('href', `${path}#download`);
      await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await expect(nav).not.toHaveClass(/is-solid/);
      await expect(nav).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
      await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
      await expect(nav).toHaveClass(/is-solid/);
      await expect(nav).toHaveCSS('background-color', 'rgb(246, 239, 226)');
      expect(errors).toEqual([]);
    });
  }

  test('mobilmenyn öppnas och stängs med Esc', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const toggle = page.locator('#site-nav .menu-toggle');
    const menu = page.locator('#mobile-menu');
    await expect(menu).toBeHidden();
    await toggle.click();
    await expect(menu).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(toggle).toHaveAttribute('aria-label', 'Stäng menyn');
    await expect(menu.locator('a', { hasText: 'Integritet' })).toHaveAttribute('href', '/sv/#privacy');
    await expect(menu.locator('a[href="/sv/#guide"]')).toHaveCount(1);
    await expect(menu.locator('a[href="/sv/#faq"]')).toHaveCount(1);
    await menu.locator('a').first().focus();
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await expect(toggle).toHaveAttribute('aria-label', 'Öppna menyn');
    await expect(toggle).toBeFocused();
  });

  test('mobilmenyn går att scrolla i liggande läge och stängs av en länk', async ({ page }) => {
    await page.setViewportSize({ width: 844, height: 390 });
    await page.goto('/sv/');
    await page.locator('#site-nav .menu-toggle').click();
    const cta = page.locator('#mobile-menu a.btn');
    await cta.scrollIntoViewIfNeeded();
    await expect(cta).toBeInViewport();
    await cta.click();
    await expect(page.locator('#mobile-menu')).toBeHidden();
  });

  test('menyn markerar Blogg på bloggen', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/blog/');
    await expect(page.locator('#site-nav .links a[aria-current="page"]')).toHaveText('Blogg');
  });

  test('sidfoten har kolumnerna', async ({ page }) => {
    await page.goto('/sv/');
    const footer = page.locator('footer.footer');
    await expect(footer.locator('.fh')).toHaveText(['Möt fåglarna', 'Utforska', 'Läs', 'Information']);
    await expect(footer.locator('a[href="/legal/privacy/"]')).toHaveText('Integritetspolicy');
  });

  for (const [path, albitHref, builtBy] of [
    ['/sv/', 'https://www.albit.se/produkter/birdy/', 'Byggd av'],
    ['/', 'https://www.albit.se/en/products/birdy/', 'Built by'],
  ] as const) {
    test(`sidfoten på ${path} har AlbIT:s ordmärke och ingen LoopLead`, async ({ page }) => {
      await page.goto(path);
      const footer = page.locator('footer.footer');
      const credit = footer.locator(`a.albit-kredit[href="${albitHref}"]`);
      await expect(credit).toContainText(builtBy);
      await expect(credit.locator('img[alt="AlbIT"]')).toHaveAttribute('src', '/images/albit-ordmarke-vit.png');
      // Albin 2026-10-07: LoopLead is off Birdy's site for now.
      await expect(footer.locator('a[href*="looplead"]')).toHaveCount(0);
      await expect(footer).not.toContainText('LoopLead');
    });
  }

  for (const [path, follow, on] of [['/sv/', 'Följ Birdy', 'på'], ['/', 'Follow Birdy', 'on']] as const) {
    test(`sidfoten på ${path} länkar till Birdys fyra kanaler, och appens JSON-LD har dem som sameAs`, async ({ page }) => {
      await page.goto(path);
      const row = page.locator('footer.footer ul.fsoc');
      await expect(page.locator('footer.footer .fsoc-h')).toHaveText(follow);
      await expect(row).toHaveAccessibleName(follow);
      const links = row.locator('a');
      await expect(links).toHaveCount(channels.length);
      for (const [i, [network, href]] of channels.entries()) {
        const link = links.nth(i);
        await expect(link).toHaveAttribute('href', href);
        await expect(link).toHaveAccessibleName(`Birdy ${on} ${network}`);
        // The same new-tab behaviour as the footer's other external link (the AlbIT credit), plus rel="me".
        await expect(link).toHaveAttribute('target', '_blank');
        expect((await link.getAttribute('rel'))?.split(/\s+/), `${network}: rel`).toEqual(expect.arrayContaining(['me', 'noopener']));
        await expect(link.locator('svg')).toBeVisible();
      }
      await expect(page.locator('a[href*="instagram.com/birdy.community"]')).toHaveCount(0);

      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const app = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'MobileApplication');
      expect(app.sameAs).toEqual(channels.map(([, href]) => href));
    });
  }

  test('kanalerna finns i sidfoten på alla sorters sidor', async ({ page }) => {
    for (const path of ['/blog/', '/sv/blog/why-birdy/', '/premium/', '/sv/premium/', '/legal/', '/legal/privacy/', '/species/', '/sv/arter/talgoxe/']) {
      await page.goto(path);
      const links = page.locator('footer.footer ul.fsoc a');
      await expect(links, path).toHaveCount(channels.length);
      expect(await links.evaluateAll((els) => els.map((a) => a.getAttribute('href'))), path).toEqual(channels.map(([, href]) => href));
    }
  });
});

test.describe('menyn över persikopappret', () => {
  for (const [path, label] of [['/sv/', 'Möt fåglarna'], ['/', 'Meet the birds']] as const) {
    // Albin 2026-10-10: the wordmark is the orange of the handwritten accent words (rust on light), and the bar the
    // site's light paper once it turns solid, with the same dark text as over the hero.
    test(`menyn på ${path} har mörk text och ett rostfärgat ordmärke, över hjälten och på det ljusa pappret`, async ({ page }) => {
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto(path);
      const nav = page.locator('#site-nav');
      await expect(nav).not.toHaveClass(/is-solid/);
      await expect(nav.locator('.links a').first()).toHaveText(label);
      await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
      await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
      await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
      await expect(nav).toHaveClass(/is-solid/);
      await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
      await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    });
  }

  test('menyknappen på mobilen är mörk och utan espressoskiva över hjälten', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const toggle = page.locator('#site-nav .menu-toggle');
    await expect(toggle).toHaveCSS('color', 'rgb(48, 32, 25)');
    await expect(toggle).toHaveCSS('background-color', 'rgba(0, 0, 0, 0)');
  });

  // Review fix nit: .is-open is excluded from the peach-over-transparent selector, so an open mobile menu
  // falls back to the solid bar, same as scrolled-past: the light paper with the rust wordmark (2026-10-10).
  test('mobilmenyn över hjälten blir ljust papper med det rostfärgade ordmärket', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    const nav = page.locator('#site-nav');
    await expect(nav).not.toHaveClass(/is-solid/);
    await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    await nav.locator('.menu-toggle').click();
    await expect(nav).toHaveClass(/is-open/);
    await expect(nav).toHaveCSS('background-color', 'rgb(246, 239, 226)');
    await expect(nav.locator('#mobile-menu a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
    await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', /^#f6efe2$/i);
    await nav.locator('.menu-toggle').click();
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#FDE5CB');
  });

  // The Premium page's first view is the peach paper too since 2026-10-10: over it the transparent bar has dark links and
  // the rust wordmark, the browser bar the hero's peach; scrolled past, the same light paper bar as everywhere else.
  test('Premium-sidans meny är mörk över persikopappret, som på startsidan', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/premium/');
    const nav = page.locator('#site-nav');
    await expect(nav).not.toHaveClass(/is-solid/);
    await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
    await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', '#FDE5CB');
    await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
    await expect(nav).toHaveClass(/is-solid/);
    await expect(nav).toHaveCSS('background-color', 'rgb(246, 239, 226)');
    await expect(nav.locator('.links a').first()).toHaveCSS('color', 'rgb(48, 32, 25)');
    await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', /^#f6efe2$/i);
  });

  test('sidor med fast meny har det ljusa pappret från början', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/arter/talgoxe/');
    const nav = page.locator('#site-nav');
    await expect(nav).toHaveCSS('background-color', 'rgb(246, 239, 226)');
    await expect(nav.locator('.brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', /^#f6efe2$/i);
  });

  test.describe('kontrast', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    test('den första menylänken klarar 4.5:1 mot hjälten', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/');
      await page.addStyleTag({ content: '#site-nav .links a { visibility: hidden !important; }' });
      await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await expect(page.locator('#site-nav')).not.toHaveClass(/is-solid/);
      const ratio = await textContrastAgainstBackground(page, page.locator('#site-nav .links a').first());
      expect(ratio, `första menylänken mot hjälten: ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
    });

    test('den första menylänken klarar 4.5:1 på det ljusa pappret', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/');
      await page.evaluate(() => window.scrollTo({ top: 3000, behavior: 'instant' }));
      await expect(page.locator('#site-nav')).toHaveClass(/is-solid/);
      const ratio = await textContrastAgainstBackground(page, page.locator('#site-nav .links a').first());
      expect(ratio, `första menylänken på pappret: ${ratio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
    });
  });
});

test.describe('utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('startsidans meny är ljust papper och den döda menyknappen dold', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await expect(page.locator('#site-nav')).toHaveCSS('background-color', 'rgb(246, 239, 226)');
    await expect(page.locator('#site-nav .menu-toggle')).toBeHidden();
    // The transparent rules are gated on html.js (review fix nit): without them the bar stays the solid paper bar
    // with the base colours, ink text and the rust wordmark.
    await expect(page.locator('#site-nav .brand .wordmark')).toHaveCSS('color', 'rgb(154, 69, 38)');
  });

  // Without JavaScript the <noscript> SVG shows the landed flock with Hornuggla's bird lit, in exactly the fit box the
  // canvas uses, and the polaroid hangs at once.
  test('hjälten visar den landade flocken och polaroiden utan JavaScript', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const still = page.locator('[data-hero] [data-flock-fit] svg.flock-still');
    await expect(still).toBeVisible();
    await expect(still.locator('use')).toHaveCount(FLOCK.birds.length);
    // The one lit disc sits on Hornuggla's own bird, index 2 (flockIndexFor in hero/flock.mjs).
    await expect(page.locator('[data-hero]')).toHaveAttribute('data-flock-index', '2');
    const disc = still.locator('circle');
    await expect(disc).toHaveCount(1);
    await expect(disc).toHaveAttribute('cx', String(FLOCK.birds[2][0]));
    await expect(disc).toHaveAttribute('cy', String(FLOCK.birds[2][1]));
    expect(await still.boundingBox()).toEqual(await page.locator('[data-hero] [data-flock-fit]').boundingBox());
    await expect(page.locator('[data-hero] [data-polaroid]')).toBeVisible();
    await expect(page.locator('[data-hero] [data-polaroid] .pol-name')).toHaveText('Dagens fågel: Hornuggla');
  });
});

test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  // The fixture build pins BIRDY_TODAY=2026-10-15 (package.json build:fixtures); every build picks from the shipped app's
  // list (src/data/app-species-1.3.0.json), so the app's Dagens fågel is Hornuggla (Q25384), which has a fixture page, on
  // the day 1.3.0 goes out: the polaroid shows it with the line about the app.
  for (const [path, line1, line2, kicker, caption, same, credit, label] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Kamera, foto eller läte', 'Dagens fågel: Hornuggla', 'samma fågel som i appen i dag', 'Foto: Testfotograf, CC BY 4.0, via Wikimedia Commons, nedskalad', 'Dagens fågel, Hornuggla, lyser i flocken.'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Camera, photo or song', 'Bird of the day: Long-eared Owl', 'the same bird as in the app today', 'Photo: Testfotograf, CC BY 4.0, via Wikimedia Commons, resized', 'The bird of the day, Long-eared Owl, is lit up in the flock.'],
  ] as const) {
    test(`rubrik, flocken och Dagens fågel som polaroid på ${path}`, async ({ page, request }) => {
      const errors = trackConsoleErrors(page);
      // The browser's day is the build's day, so the stale-day guard (hero/same-as-app-guard.ts) keeps the app line.
      await page.clock.setFixedTime(new Date('2026-10-15T12:00:00+02:00'));
      await page.goto(path);
      const hero = page.locator('[data-hero]');
      await expect(hero.locator('h1')).toContainText(line1);
      await expect(hero.locator('h1 em')).toHaveText(line2);
      await expect(hero.locator('.intro .kick')).toHaveText(kicker);
      await expect(hero).toHaveAttribute('data-date', '2026-10-15');
      await expect(hero).toHaveAttribute('data-app-bird', 'Q25384');
      await expect(hero).toHaveAttribute('data-daily-bird', 'Q25384');
      // Hornuggla's own bird in the flock (flockIndexFor in hero/flock.mjs, pinned in tests/unit/flock.unit.mjs).
      await expect(hero).toHaveAttribute('data-flock-index', '2');
      const canvas = hero.locator('canvas[data-flock-canvas]');
      await expect(canvas).toHaveAttribute('role', 'img');
      expect(await canvas.getAttribute('aria-label')).toContain(label);
      const polaroid = hero.locator('[data-polaroid]');
      await expect(polaroid).toBeVisible();
      await expect(polaroid.locator('.pol-name')).toHaveText(caption);
      const href = await polaroid.locator('a.pol-name').getAttribute('href');
      expect(href).toMatch(path === '/sv/' ? /^\/sv\/arter\/hornuggla\/$/ : /^\/species\/long-eared-owl\/$/);
      expect((await request.get(href!)).status()).toBe(200);
      await expect(polaroid.locator('a.pol-photo')).toHaveAttribute('href', href!);
      // Hornuggla's test photo is CC BY and the only one it has: it may hang whole, credited like the species page.
      await expect(polaroid.locator('[data-credit]')).toHaveText(credit);
      await expect(polaroid.locator('[data-credit] a[href^="https://creativecommons.org/licenses/by/4.0/"]')).toHaveText('CC BY 4.0');
      await expect(polaroid.locator('[data-credit] a[href^="https://commons.wikimedia.org/"]')).toHaveCount(1);
      await expect(hero.locator('[data-same-as-app]')).toHaveText(same);
      await expect(hero.locator('[data-same-as-app]')).toBeVisible();
      const img = polaroid.locator('img');
      await expect(img).toHaveAttribute('width', /^\d+$/);
      await expect(img).toHaveAttribute('height', /^\d+$/);
      // The words are the page's largest content, not the photo (spec): the photo loads early but never first.
      await expect(img).toHaveAttribute('fetchpriority', 'low');
      // Nothing is written next to or over the flock (Albin 2026-10-09): no margin note.
      await expect(hero.locator('.mnote')).toHaveCount(0);
      expect(errors).toEqual([]);
    });
  }

  // A page built yesterday (the nightly build did not run) must not claim today's bird is the app's: the guard hides the
  // line when the browser's day in Stockholm is not the build's day (review 2026-10-08).
  test('raden "samma fågel som i appen i dag" döljs när bygget är från en annan dag', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-10-16T09:00:00+02:00'));
    await page.goto('/sv/');
    const line = page.locator('[data-hero] [data-same-as-app]');
    await expect(line).toHaveCount(1);
    await expect(line).toBeHidden();
  });

  // Every width (spec: no layout shift, the photo whole, the tape never on it, nothing over the words): the photo keeps
  // its shape, the tape sits above it, and everything expectHeroClear checks holds.
  for (const path of ['/sv/', '/'] as const) {
    for (const [width, height] of [[320, 700], [390, 844], [768, 1024], [1024, 768], [1280, 800], [1440, 900], [1920, 1080]] as const) {
      test(`polaroiden, flocken och orden går fria från varandra på ${path} i ${width}×${height}`, async ({ page }) => {
        await page.setViewportSize({ width, height });
        await page.goto(path);
        const g = await heroGeometry(page);
        expect(Math.abs(g.photo.width / g.photo.height - g.photo.ratio), 'fotot visas helt').toBeLessThan(0.02);
        expect(g.own.tape.bottom, 'tejpen sitter på kortets kant, ovanför fotot').toBeLessThanOrEqual(g.own.photo.top);
        expectHeroClear(g, width);
      });
    }
  }

  // What the fixture's 3:2 photo cannot show (review 2026-10-09): a tall photo (Turkduva's is 0.47), a long name and a
  // long credit make the card much taller. Forced from the test, with the line about the app showing too; the card
  // still keeps off the words and the lightable birds, inside the hero, and nothing scrolls sideways.
  test('en hög polaroid med långt namn och lång fotokredit går också fri', async ({ page }) => {
    await page.clock.setFixedTime(new Date('2026-10-15T12:00:00+02:00'));
    for (const path of ['/sv/', '/'] as const) {
      for (const [width, height] of [[1024, 600], [390, 844]] as const) {
        await page.setViewportSize({ width, height });
        await page.goto(path);
        await expect(page.locator('[data-hero] [data-same-as-app]')).toBeVisible();
        const sv = path === '/sv/';
        await page.locator('[data-hero] [data-polaroid]').evaluate((card: HTMLElement, text) => {
          // Reduced motion still runs a 0.01 ms transition on every style change (global.css), and nothing makes
          // the geometry read below wait a frame for it to finish: transitions off, so the new sizes apply at once.
          for (const el of [card, ...card.querySelectorAll<HTMLElement>('*')]) el.style.transition = 'none';
          card.style.setProperty('--ar', '0.47');
          (card.querySelector('img') as HTMLImageElement).style.aspectRatio = '0.47';
          (card.querySelector('.pol-name') as HTMLElement).textContent = text.caption;
          (card.querySelector('[data-credit]') as HTMLElement).textContent = text.credit;
        }, {
          caption: `${sv ? 'Dagens fågel' : 'Bird of the day'}: Eurasian Three-toed Woodpecker`,
          credit: `${sv ? 'Foto' : 'Photo'}: Ruth Annabelle Featherstonehaugh-Marjoribanks (Västergötlands Ornitologiska Förening), CC BY-SA 4.0, via Wikimedia Commons, ${sv ? 'nedskalad' : 'resized'}`,
        });
        const g = await heroGeometry(page);
        const where = `${path} ${width}×${height}: `;
        expect(g.photo.height / g.photo.width, `${where}fotot står på höjden`).toBeGreaterThan(2);
        expectHeroClear(g, width, where);
      }
    }
  });

  // The words stand in the page's column: from 1280 px the headline starts where the next section's text does (review
  // 2026-10-09: the site-wide .lead class on the words' wrapper had pinned them to x = 44 at every width).
  test('orden står i samma spalt som resten av sidan', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      for (const width of [1280, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const h1 = await page.locator('[data-hero] h1').evaluate((e) => e.getBoundingClientRect().left);
        const column = await page.locator('#season .wrap').evaluate((e) => e.getBoundingClientRect().left + parseFloat(getComputedStyle(e).paddingLeft));
        expect(h1, `${path} ${width} px`).toBeCloseTo(column, 1);
      }
    }
  });

  // Nothing invisible lies over the polaroid (review 2026-10-09: the words' wrapper had a z-index and covered the photo's
  // left edge at 1024 px): fifteen points across the photo, its middle included, all hit the photo's link. They stay 8 px
  // in from the sides and 10 px from the top and bottom, inside the photo although the card leans 2 degrees.
  for (const [width, height] of [[1024, 768], [1440, 900]] as const) {
    test(`hela fotot i polaroiden är fotots länk i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const img = page.locator('[data-hero] [data-polaroid] img');
      await img.evaluate((i: HTMLImageElement) => i.decode());
      const misses = await img.evaluate((i) => {
        const r = i.getBoundingClientRect();
        const xs = [r.left + 8, r.left + r.width / 4, r.left + r.width / 2, r.right - r.width / 4, r.right - 8];
        const ys = [r.top + 10, r.top + r.height / 2, r.bottom - 10];
        return xs.flatMap((x) => ys.map((y) => {
          const el = document.elementFromPoint(x, y);
          return el?.closest('[data-polaroid] a.pol-photo') ? '' : `(${Math.round(x)}, ${Math.round(y)}): ${el ? `${el.tagName.toLowerCase()}.${[...el.classList].join('.')}` : 'inget'}`;
        })).filter(Boolean);
      });
      expect(misses).toEqual([]);
    });
  }

  for (const [width, height] of [[390, 844], [1024, 768], [1440, 900]] as const) {
    test(`menyn blir espressobrun innan texten når den i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const nav = page.locator('#site-nav');
      const navH = await nav.evaluate((n) => n.getBoundingClientRect().height);
      const top = await page.locator('[data-hero] .intro').evaluate((c) => c.getBoundingClientRect().top + scrollY);
      const settle = () => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), Math.max(0, top - navH - 30));
      await settle();
      await expect(nav).not.toHaveClass(/is-solid/);
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), top - navH + 2);
      await expect(nav).toHaveClass(/is-solid/);
    });
  }

  test('orden börjar under menyn och den handskrivna raden ryms på en rad på dator', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      for (const width of [1024, 1280, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        const nav = (await page.locator('#site-nav').boundingBox())!;
        const kick = (await page.locator('[data-hero] .intro .kick').boundingBox())!;
        expect(kick.y, `${path} ${width} px`).toBeGreaterThanOrEqual(nav.y + nav.height);
        const em = await page.locator('[data-hero] h1 em').evaluate((e) => ({
          lines: Math.round((e as HTMLElement).offsetHeight / parseFloat(getComputedStyle(e).lineHeight)),
          over: e.scrollWidth - (e.parentElement as HTMLElement).clientWidth,
        }));
        expect(em.lines, `${path} ${width} px`).toBe(1);
        expect(em.over, `${path} ${width} px: raden går utanför spalten`).toBeLessThanOrEqual(0);
      }
    }
  });
});

// Wraps requestAnimationFrame before any of the page's own scripts run, so a test can prove that nothing is scheduled
// any more once the flock has settled (a frozen canvas alone could in principle be redrawn with identical pixels).
async function trackRaf(page: Page): Promise<() => Promise<number>> {
  await page.addInitScript(() => {
    (window as unknown as { __rafCalls: number }).__rafCalls = 0;
    const raw = window.requestAnimationFrame.bind(window);
    window.requestAnimationFrame = (cb: FrameRequestCallback) => {
      (window as unknown as { __rafCalls: number }).__rafCalls += 1;
      return raw(cb);
    };
  });
  return () => page.evaluate(() => (window as unknown as { __rafCalls: number }).__rafCalls);
}

// Records every value the hero's data-flock attribute takes, from a MutationObserver attached before the hero element
// even exists, so a test can assert the exact order (waiting, flying, landed, done) instead of racing the
// IntersectionObserver/rAF timing by polling for one value right after goto.
async function trackFlockState(page: Page): Promise<() => Promise<string[]>> {
  await page.addInitScript(() => {
    const log: string[] = [];
    (window as unknown as { __flockLog: string[] }).__flockLog = log;
    const push = (el: Element) => {
      const value = el.getAttribute('data-flock');
      if (value && log[log.length - 1] !== value) log.push(value);
    };
    new MutationObserver((records) => {
      for (const record of records) {
        if (record.type === 'attributes' && record.target instanceof Element) push(record.target);
        if (record.type === 'childList') {
          for (const node of Array.from(record.addedNodes)) {
            if (!(node instanceof Element)) continue;
            const hero = node.matches('[data-hero]') ? node : node.querySelector('[data-hero]');
            if (hero) push(hero);
          }
        }
      }
      // document, not document.documentElement: an init script runs this early enough that <html> may not exist yet.
    }).observe(document, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-flock'] });
  });
  return () => page.evaluate(() => (window as unknown as { __flockLog: string[] }).__flockLog);
}

test.describe('flocken lyfter', () => {
  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    test('flocken står landad direkt, med dagens fågel tänd', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/');
      const hero = page.locator('[data-hero]');
      await expect(hero).toHaveAttribute('data-flock', 'done');
      await expect(hero.locator('[data-polaroid]')).toBeVisible();
      const geo = await page.evaluate(() => {
        const c = (document.querySelector('[data-flock-canvas]') as HTMLElement).getBoundingClientRect();
        const f = (document.querySelector('[data-flock-fit]') as HTMLElement).getBoundingClientRect();
        return { left: f.left - c.left, top: f.top - c.top, width: f.width, height: f.height };
      });
      const lit = landing(FLOCK.birds[2], fitView(geo));
      const alpha = (x: number, y: number) =>
        page.locator('[data-flock-canvas]').evaluate((canvas: HTMLCanvasElement, [px, py]) => {
          const k = canvas.width / canvas.getBoundingClientRect().width;
          return canvas.getContext('2d')!.getImageData(Math.round(px * k), Math.round(py * k), 1, 1).data[3];
        }, [x, y] as const);
      expect(await alpha(lit.x, lit.y), 'dagens fågel är ritad').toBeGreaterThan(230);
      expect(await alpha(2, 2), 'resten av ytan är tom').toBe(0);
      expect(await hero.evaluate((h) => h.getAnimations({ subtree: true }).length), 'inget rör sig').toBe(0);
    });
  });

  test('flocken flyger in en gång, landar och står sedan still', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    const flockLog = await trackFlockState(page);
    const rafCalls = await trackRaf(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    const polaroid = hero.locator('[data-polaroid]');
    // Opacity, not visibility (review fix: the polaroid must stay in the accessibility tree while hidden), so
    // Playwright's own visible/hidden check (which does not look at opacity) cannot see the hidden state.
    await expect(polaroid).toHaveCSS('opacity', '0');
    await expect(hero).toHaveAttribute('data-flock', 'done', { timeout: 15_000 });
    await expect(polaroid).toHaveCSS('opacity', '1');
    // The exact order, from a MutationObserver attached before the page's own scripts ran, not from catching "flying"
    // by polling right after goto (races the IntersectionObserver/rAF timing).
    expect(await flockLog()).toEqual(['waiting', 'flying', 'landed', 'done']);
    expect(await hero.evaluate((h) => h.getAnimations({ subtree: true }).length), 'inga animationer kvar').toBe(0);
    const frame = () => page.locator('[data-flock-canvas]').evaluate((c: HTMLCanvasElement) => c.toDataURL());
    const landed = await frame();
    const scheduledAtLanding = await rafCalls();
    await page.waitForTimeout(600);
    expect(await frame(), 'inget ritas om efter landningen').toBe(landed);
    expect(await rafCalls(), 'inget requestAnimationFrame schemaläggs efter landningen').toBe(scheduledAtLanding);
    const shift = await page.evaluate(() => new Promise<number>((resolve) => {
      let sum = 0;
      new PerformanceObserver((list) => {
        for (const e of list.getEntries() as (PerformanceEntry & { value: number; hadRecentInput: boolean })[]) if (!e.hadRecentInput) sum += e.value;
      }).observe({ type: 'layout-shift', buffered: true });
      setTimeout(() => resolve(sum), 50);
    }));
    expect(shift, 'inga layoutskift').toBeLessThan(0.01);
    expect(errors).toEqual([]);
  });

  test('flocken väntar tills hjälten syns', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    // Scrolled to the bottom before the page's scripts run, as when a link opens the page further down. (A #fragment
    // does not do: Chrome reports the top as visible for a frame before it scrolls.)
    await page.addInitScript(() => {
      document.addEventListener('readystatechange', () => {
        if (document.readyState !== 'interactive') return;
        document.documentElement.style.scrollBehavior = 'auto';
        window.scrollTo(0, document.documentElement.scrollHeight);
      });
    });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await page.waitForTimeout(1000);
    await expect(hero).toHaveAttribute('data-flock', 'waiting');
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await expect(hero).toHaveAttribute('data-flock', /^(flying|landed|done)$/);
  });

  // Review fix: Chrome never reports isIntersecting at the old threshold (0.2 of the target's own area) for a hero
  // much taller than the viewport, since the hero's visible share never reaches a fifth of itself; the fix watches
  // the viewport's own middle band instead (rootMargin), which the hero reaches at any height. 320×170 is a short,
  // wide window (about 400 % zoom); 1440×3000 is the opposite, a window far taller than the hero itself, where a
  // percentage-only inset (30 % of 3000 px) would again overshoot the hero and never reach it (review fix: capped in
  // px).
  for (const [width, height] of [[320, 170], [1440, 3000]] as const) {
    test(`flocken startar även när hjälten är mycket högre än skärmen (${width}×${height})`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const hero = page.locator('[data-hero]');
      await expect(hero).toHaveAttribute('data-flock', 'done', { timeout: 15_000 });
      await expect(hero.locator('[data-polaroid]')).toBeVisible();
    });
  }

  // Review fix: any keydown anywhere (here, Tab) skips straight to the end, so a keyboard user never has to wait out
  // the flight. The link was never removed from the accessibility tree while hidden (opacity, not visibility), so it
  // can still be focused afterwards. Waits for "flying" via the MutationObserver log (review fix), not by polling the
  // live attribute right after goto, which assumes timing.
  test('en tangenttryckning under flykten hoppar till slutet, och polaroidens länk går att fokusera', async ({ page }) => {
    const flockLog = await trackFlockState(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect.poll(async () => (await flockLog()).includes('flying')).toBe(true);
    await page.keyboard.press('Tab');
    await expect(hero).toHaveAttribute('data-flock', 'done');
    // The skip jumps straight from flying to done (review fix nit): fails if the flight had already
    // reached its own natural "landed" or "done" state before the key press.
    expect(await flockLog()).toEqual(['waiting', 'flying', 'done']);
    const link = hero.locator('[data-polaroid] a.pol-name');
    await link.focus();
    await expect(link).toBeFocused();
  });

  // WCAG 2.2.2 (the motion runs about 6.8 s): a click anywhere on the hero skips to the end exactly like a keydown.
  // Never wired to touch-scroll, though (the earlier decision): a click event does not fire for a scroll gesture, so
  // scrolling past the hero on a phone still does not cut the motion short.
  test('ett klick på hjälten under flykten hoppar till slutet', async ({ page }) => {
    const flockLog = await trackFlockState(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect.poll(async () => (await flockLog()).includes('flying')).toBe(true);
    await hero.locator('.intro h1').click();
    await expect(hero).toHaveAttribute('data-flock', 'done');
    expect(await flockLog()).toEqual(['waiting', 'flying', 'done']);
  });

  // Reduced motion switched on mid-flight (a settings change, not just at load) gets the same snap-to-end as a
  // keydown or a click: the flight must never be the only way to reach the end for someone who has just asked for
  // less motion.
  test('minskad rörelse som slås på mitt i flykten hoppar till slutet', async ({ page }) => {
    const flockLog = await trackFlockState(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect.poll(async () => (await flockLog()).includes('flying')).toBe(true);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await expect(hero).toHaveAttribute('data-flock', 'done');
    expect(await flockLog()).toEqual(['waiting', 'flying', 'done']);
  });

  // Review fix: the resize handler used to redraw "now - t0" whenever t0 was set, even after a skip had stopped the
  // loop, so a skip followed soon after by a resize (zoom, F11, a docked DevTools panel, Win+arrow) froze the canvas
  // on a stray mid-air instant instead of keeping the already-landed picture.
  test('ett tangenttryck under flykten följt av en storleksändring visar ändå den landade flocken', async ({ page }) => {
    const flockLog = await trackFlockState(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect.poll(async () => (await flockLog()).includes('flying')).toBe(true);
    await page.keyboard.press('Tab');
    await expect(hero).toHaveAttribute('data-flock', 'done');
    // Same nit as the skip test above: fails if the flight had already ended before the key press.
    expect(await flockLog()).toEqual(['waiting', 'flying', 'done']);
    await page.setViewportSize({ width: 1024, height: 768 });
    // Lets the ResizeObserver's own debounced rAF callback run (same wait as the landing-redraw test above).
    await page.waitForTimeout(600);
    const geo = await page.evaluate(() => {
      const c = (document.querySelector('[data-flock-canvas]') as HTMLElement).getBoundingClientRect();
      const f = (document.querySelector('[data-flock-fit]') as HTMLElement).getBoundingClientRect();
      return { left: f.left - c.left, top: f.top - c.top, width: f.width, height: f.height };
    });
    const lit = landing(FLOCK.birds[2], fitView(geo));
    const alpha = await page.locator('[data-flock-canvas]').evaluate((canvas: HTMLCanvasElement, [px, py]) => {
      const k = canvas.width / canvas.getBoundingClientRect().width;
      return canvas.getContext('2d')!.getImageData(Math.round(px * k), Math.round(py * k), 1, 1).data[3];
    }, [lit.x, lit.y] as const);
    expect(alpha, 'dagens fågel är fortfarande ritad efter storleksändringen').toBeGreaterThan(230);
  });

  // Review fix (important): the polaroid must not stay invisible forever, its still-focusable link unreachable by a
  // pointer, if the module that drives the flight never runs at all (a dropped mobile connection, a parse failure).
  // Hero.astro's CSS-only fallback reveals the card on its own, with no module in the loop, within about 3 s.
  test('polaroidens länk blir synlig och går att klicka även om hjältens skript aldrig körs', async ({ page }) => {
    await page.route('**/Hero.astro_astro_type_script*', (route) => route.abort());
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const polaroid = page.locator('[data-hero] [data-polaroid]');
    await expect(polaroid).toHaveCSS('opacity', '1', { timeout: 4_000 });
    const hit = await polaroid.locator('a.pol-name').evaluate((link) => {
      const r = link.getBoundingClientRect();
      const el = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      return el === link || !!el?.closest('a.pol-name');
    });
    expect(hit, 'länken tar emot klick i mitten, inte bara synlig för en skärmläsare').toBe(true);
  });

  // Review fix (minor A, a regression from the previous round): the module's own no-2D-context path (an old or
  // locked-down browser) never calls run(), so it must set data-flock-live itself before falling back to "done".
  // Otherwise Hero.astro's CSS fallback rule keeps matching and the card stays hidden for its own ~3 s delay even
  // though the hero already finished synchronously, on page load.
  test('kortet syns direkt när kanvasen saknar en 2d-kontext, utan fallbackens fördröjning', async ({ page }) => {
    await page.addInitScript(() => {
      type GetContext = (type: string, ...rest: unknown[]) => unknown;
      // Only the flock's own canvas reports no context; every other canvas on the page (sprite canvases, the
      // coverage map further down) keeps behaving normally.
      const proto = HTMLCanvasElement.prototype as unknown as { getContext: GetContext };
      const raw = proto.getContext;
      proto.getContext = function (this: HTMLCanvasElement, type: string, ...rest: unknown[]) {
        if (type === '2d' && this.hasAttribute('data-flock-canvas')) return null;
        return raw.apply(this, [type, ...rest]);
      };
    });
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    await expect(hero).toHaveAttribute('data-flock', 'done');
    // Deterministic, not timing-dependent (review fix): this no-context path only reveals the card promptly because
    // it sets data-flock-live itself (flock-motion.ts's final else-branch) before falling back to "done", so this is
    // red against a regression that drops that line regardless of how fast or slow the page happens to load.
    await expect(hero).toHaveAttribute('data-flock-live', '');
    // Well under the fallback's own ~3 s delay (review fix: before it, this stayed at opacity 0 until the fallback's
    // timer caught up, even though the hero had already finished).
    await expect(hero.locator('[data-polaroid]')).toHaveCSS('opacity', '1', { timeout: 1_500 });
  });

  // The fallback above must never win a race with the module when it does load in time: the card stays hidden for
  // the whole flight, exactly as before the fallback existed, and only reveals through the normal landing.
  test('fallbacken poppar inte upp kortet i förtid när skriptet körs som vanligt', async ({ page }) => {
    const flockLog = await trackFlockState(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const hero = page.locator('[data-hero]');
    const polaroid = hero.locator('[data-polaroid]');
    await expect.poll(async () => (await flockLog()).includes('flying')).toBe(true);
    // Deterministic, not timing-dependent (review fix): data-flock-live is always set before the flight can reach
    // "flying" (run()'s own first act) and is never removed again, so Hero.astro's CSS fallback rule has stopped
    // matching for good by here, whatever the flight's own timing does below. The card's reveal only starts at
    // landing, so while flying it must carry no animation at all yet, not merely lack the fallback's name by itself
    // (stricter check, review fix: this also survives a keyframes rename).
    await expect(hero).toHaveAttribute('data-flock-live', '');
    const names = await polaroid.evaluate((el) => el.getAnimations().map((a) => (a instanceof CSSAnimation ? a.animationName : '')));
    expect(names, 'kortet ska inte ha några animationer medan flocken flyger').toEqual([]);
    await expect(polaroid).toHaveCSS('opacity', '0');
  });

  for (const [width, height] of [[390, 844], [1440, 900]] as const) {
    test(`det största innehållet är orden, inte fotot (${width}×${height})`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      await expect(page.locator('[data-hero]')).toHaveAttribute('data-flock', 'done', { timeout: 15_000 });
      const lcp = await page.evaluate(() => new Promise<string>((resolve) => {
        new PerformanceObserver((list) => {
          const entries = list.getEntries() as (PerformanceEntry & { element?: Element | null })[];
          const el = entries[entries.length - 1]?.element;
          resolve(!el ? 'inget' : el.closest('[data-hero] .intro') ? 'orden' : el.closest('[data-polaroid]') ? 'fotot' : el.tagName);
        }).observe({ type: 'largest-contentful-paint', buffered: true });
      }));
      expect(lcp).toBe('orden');
    });
  }
});

test.describe('kontrast i hjälten mot persikopappret', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  // Pixel contrast (like the #download guard): hide the hero's words, screenshot what lies behind them (peach, and any
  // bird of the landed flock), and check the kicker and the sub text (4.5:1) and the handwritten line (large, 3:1).
  for (const path of ['/sv/', '/'] as const) {
    for (const width of [390, 1024, 1440] as const) {
      test(`kicker, underrad och handskriven rad klarar kontrasten i ${width}px på ${path}`, async ({ page }) => {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        const hero = page.locator('[data-hero]');
        await expect(hero).toHaveAttribute('data-flock', 'done');
        await page.addStyleTag({ content: '[data-hero] .intro * { visibility: hidden !important; }' });
        await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
        const kick = await textContrastAgainstBackground(page, hero.locator('.intro .kick'));
        expect(kick, `kicker: ${kick.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
        const sub = await textContrastAgainstBackground(page, hero.locator('.intro .sub'));
        expect(sub, `underrad: ${sub.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
        const em = await textContrastAgainstBackground(page, hero.locator('h1 em'));
        expect(em, `handskriven rad: ${em.toFixed(2)}:1`).toBeGreaterThanOrEqual(3);
      });
    }
  }
});

test.describe('fåglarna i månaden', () => {
  // Fixture build, 15 October: every fixture species with a free photo and report data has the same October share,
  // above its yearly mean, so the four come in QID order (ties by QID, src/lib/month-birds.mjs).
  for (const [path, heading, names] of [
    ['/sv/', 'Fåglarna i oktober.', ['Gråsparv', 'Koltrast', 'Skata', 'Rödhake']],
    ['/', 'Birds in October.', ['House Sparrow', 'Common Blackbird', 'Eurasian Magpie', 'European Robin']],
  ] as const) {
    test(`fyra arter med sida, foto och staplar på ${path}`, async ({ page, request }) => {
      await page.goto(path);
      const section = page.locator('[data-month-birds]');
      await expect(section.locator('h2')).toHaveText(heading);
      const cards = section.locator('.spec');
      await expect(cards).toHaveCount(4);
      await expect(section.locator('.spec-name')).toHaveText([...names]);
      await expect(cards.first().locator('.spec-bars i.now')).toHaveCount(1);
      for (const href of await section.locator('.spec-link').evaluateAll((as) => as.map((a) => a.getAttribute('href')!))) {
        expect((await request.get(href)).status(), href).toBe(200);
      }
      // The cards crop the photo, so a CC BY-SA photo never hangs there: Gråsparv's test hero is CC BY-SA and the card
      // shows its CC BY extra instead, credited like the species page; Koltrast's CC0 hero is fine as it is.
      const sparrow = section.locator('.spec', { has: page.locator('[data-qid="Q14683"]') });
      await expect(sparrow.locator('img')).toHaveAttribute('src', /\/extra\./);
      await expect(sparrow.locator('.spec-credit')).toContainText('CC BY 2.0');
      await expect(sparrow.locator('.spec-credit a[href^="https://creativecommons.org/licenses/by/2.0/"]')).toHaveCount(1);
      await expect(section.locator('.spec', { has: page.locator('[data-qid="Q25234"]') }).locator('.spec-credit')).toContainText('CC0');
      for (const text of await section.locator('.spec-credit').allTextContents()) expect(text).not.toMatch(/BY-SA/);
      expect((await request.get((await section.locator('a.all').getAttribute('href'))!)).status()).toBe(200);
    });
  }
});

test.describe('så funkar det och fältboken', () => {
  for (const [path, how, journal, free, label] of [
    ['/sv/', 'Tre sätt att fånga.', 'Varje fynd får en egen sida.', 'Gratis', 'märken att samla'],
    ['/', 'Three ways to catch it.', 'Every sighting gets its own page.', 'Free', 'badges to collect'],
  ] as const) {
    test(`sektionerna finns på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator('#how-it-works h2')).toHaveText(how);
      await expect(page.locator('#how-it-works .row')).toHaveCount(3);
      // All three ways in are free (Albin 2026-10-08): camera, photo and sound.
      await expect(page.locator('#how-it-works .row .free')).toHaveText([free, free, free]);
      await expect(page.locator('#journal h2')).toHaveText(journal);
      await expect(page.locator('#journal .facts dt')).toHaveText(['34', '27', '0']);
      await expect(page.locator('#journal .facts dd').first()).toHaveText(label);
      await expect(page.locator('#journal img')).toHaveAttribute('alt', /.+/);
    });
  }

  test.describe('smala skärmar', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    for (const width of [320, 360]) {
      test(`inget sidledes scroll på ${width} px`, async ({ page }) => {
        await page.setViewportSize({ width, height: 780 });
        for (const path of ['/sv/', '/']) {
          await page.goto(path);
          expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(width);
        }
      });
    }
  });
});

test.describe('appkarusellen', () => {
  test('tio riktiga skärmar och pilarna byter text (SV)', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour.locator('.slide')).toHaveCount(10);
    await expect(tour.locator('.slide .phone picture source[type="image/avif"]')).toHaveCount(10);
    await expect(tour.locator('.plno span')).toHaveText([
      'Identifiera',
      'Artprofil',
      'Mina arter',
      'Ljud-ID',
      'Veckans uppslag',
      'Troférummet',
      'Uppslagsverk',
      'Premium',
      'Premium',
      'Premium',
    ]);
    await expect(tour.locator('.plno b')).toHaveText([
      'Pl. I', 'Pl. II', 'Pl. III', 'Pl. IV', 'Pl. V', 'Pl. VI', 'Pl. VII', 'Pl. VIII', 'Pl. IX', 'Pl. X',
    ]);
    await tour.scrollIntoViewIfNeeded();
    const title = tour.locator('[data-ch]');
    await expect(title).toHaveText('Tre sätt att fånga');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Allt om arten på ett uppslag');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Din egen fältbok');
    await tour.locator('[data-prev]').click();
    await expect(title).toHaveText('Allt om arten på ett uppslag');
    await tour.locator('[data-track]').press('End');
    await expect(title).toHaveText('Fältdagboken som PDF');
    expect(errors).toEqual([]);
  });

  test('svenska sidan visar svenska skärmar, engelska sidan engelska, och aldrig kartan', async ({ page }) => {
    for (const [path, lang] of [['/sv/', 'sv'], ['/', 'en']] as const) {
      await page.goto(path);
      const screens = await page.locator('#app .slide').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.screen));
      expect(screens).toEqual([
        '01-identifiera',
        '05-artprofil',
        '03-mina-arter',
        '07-lyssna',
        '12-veckans-uppslag',
        '14-troferum',
        '16-uppslagsverk-vadare',
        '17-fynd-karta',
        '18-sasongsstatistik',
        '19-exportera-pdf',
      ]);
      await expect(page.locator('#app .phone img').first()).toHaveAttribute('alt', lang === 'sv' ? /Identifiera/ : /Identify/);
    }
  });

  test('de tre Premium-korten har en diskret mässingstagg på bygeln och Premium i kickern', async ({ page }) => {
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour.locator('.pbadge')).toHaveCount(3);
    await expect(tour.locator('.pbadge')).toHaveText(['Premium', 'Premium', 'Premium']);
    await expect(tour.locator('.plno-premium')).toHaveCount(3);
    for (const i of [7, 8, 9]) {
      await expect(tour.locator('.slide').nth(i).locator('.pbadge')).toHaveCount(1);
    }
    for (const i of [0, 1, 2, 3, 4, 5, 6]) {
      await expect(tour.locator('.slide').nth(i).locator('.pbadge')).toHaveCount(0);
    }
  });

  test('ljudvågen på Lyssna-kortet animerar bara när kortet är centrerat, och står still vid minskad rörelse', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await tour.scrollIntoViewIfNeeded();
    const lyssna = tour.locator('.slide[data-screen="07-lyssna"]');
    await expect(lyssna.locator('.wave-bar')).toHaveCount(48);
    // Only the Listen card has a wave overlay.
    await expect(tour.locator('.wave-bar')).toHaveCount(48);
    // Lyssna is the 4th card (index 3), not centred on load — navigate there first.
    await tour.locator('[data-track]').focus();
    for (let i = 0; i < 3; i++) await tour.locator('[data-next]').click();
    await expect.poll(() => lyssna.evaluate((el) => el.classList.contains('is-active'))).toBe(true);
    await expect
      .poll(() => lyssna.locator('.wave-bar').first().evaluate((el) => getComputedStyle(el).animationPlayState))
      .toBe('running');
    await tour.locator('[data-prev]').click();
    await expect.poll(() => lyssna.evaluate((el) => el.classList.contains('is-active'))).toBe(false);
    await expect
      .poll(() => lyssna.locator('.wave-bar').first().evaluate((el) => getComputedStyle(el).animationPlayState))
      .toBe('paused');
  });

  test.describe('ljudvågen vid minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    test('baren rör sig inte', async ({ page }) => {
      await page.goto('/sv/');
      const tour = page.locator('#app');
      await tour.scrollIntoViewIfNeeded();
      const bar = tour.locator('.wave-bar').first();
      await expect.poll(() => bar.evaluate((el) => getComputedStyle(el).animationName)).toBe('none');
    });
  });

  test('alla telefonbilder är laddade när man når karusellen', async ({ page }) => {
    await page.goto('/sv/');
    await page.locator('#app').scrollIntoViewIfNeeded();
    await expect.poll(() => page.locator('#app img').evaluateAll((imgs) =>
      imgs.every((img) => (img as HTMLImageElement).loading === 'eager'))).toBe(true);
    await expect.poll(() => page.locator('#app img').evaluateAll((imgs) =>
      imgs.every((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0))).toBe(true);
  });

  test('ett musklick på grannen centrerar den', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await tour.scrollIntoViewIfNeeded();
    const neighbor = tour.locator('.slide').nth(1);
    const box = (await neighbor.boundingBox())!;
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);
    await expect(tour.locator('[data-ch]')).toHaveText('Allt om arten på ett uppslag');
  });

  for (const width of [320, 390]) {
    test(`bildtexten har samma höjd på alla skärmar (${width} px)`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      for (const path of ['/sv/', '/']) {
        await page.goto(path);
        const tour = page.locator('#app');
        await tour.scrollIntoViewIfNeeded();
        const cap = tour.locator('.cap');
        const heights: number[] = [(await cap.boundingBox())!.height];
        for (let i = 1; i <= 9; i++) {
          await tour.locator('[data-next]').click();
          const expected = await tour.locator('.slide').nth(i).getAttribute('data-h');
          await expect(tour.locator('[data-ch]')).toHaveText(expected ?? '');
          heights.push((await cap.boundingBox())!.height);
        }
        expect(Math.max(...heights) - Math.min(...heights), path).toBeLessThanOrEqual(1);
      }
    });
  }

  test('pilarna syns helt i 320 px', async ({ page }) => {
    await page.setViewportSize({ width: 320, height: 700 });
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      const tour = page.locator('#app');
      await tour.scrollIntoViewIfNeeded();
      for (const sel of ['[data-prev]', '[data-next]']) {
        const box = (await tour.locator(sel).boundingBox())!;
        expect(box.x, `${path} ${sel} x`).toBeGreaterThanOrEqual(0);
        expect(box.x + box.width, `${path} ${sel} x+width`).toBeLessThanOrEqual(320);
      }
    }
  });

  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    test('karusellen och polaroiden står still', async ({ page }) => {
      await page.goto('/sv/');
      await page.locator('#app').scrollIntoViewIfNeeded();
      const transforms = await page.locator('#app .slide').evaluateAll((els) => els.map((e) => getComputedStyle(e).transform));
      expect(transforms.every((t) => t === 'none')).toBe(true);
      // Reduced motion shortens every animation to 0.01 ms, but each still needs a frame to finish; poll until none run.
      await expect.poll(() => page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length)).toBe(0);
    });
  });

  test.describe('utan JavaScript', () => {
    test.use({ javaScriptEnabled: false });
    test('bildtextlistan visar alla tio skärmar', async ({ page }) => {
      await page.goto('/sv/');
      await expect(page.locator('#app .cap-list li')).toHaveCount(10);
      await expect(page.locator('#app .foot')).toBeHidden();
    });
  });
});

// The year ring's flight (2026-10-10): once the band is in view the flock flies in, then rests, whole and upright.
test.describe('flocken runt året', () => {
  test.use({ contextOptions: { reducedMotion: 'no-preference' } });
  test('flyger in när bandet syns och står sedan still', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const birds = page.locator('#premium [data-year-flock] .yf-birds');
    await page.locator('#premium').scrollIntoViewIfNeeded();
    await expect.poll(() => birds.evaluate((el) => getComputedStyle(el).animationName)).toBe('yf-fly');
    await expect.poll(() => birds.evaluate((el) => el.getAnimations().filter((a) => a.playState === 'running').length), { timeout: 6000 }).toBe(0);
    expect(await birds.evaluate((el) => getComputedStyle(el).opacity)).toBe('1');
    // At rest: no turn left (the keyframes' fill leaves the identity matrix, or none).
    expect(await birds.evaluate((el) => getComputedStyle(el).transform)).toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
  });
});

test.describe('premium och integritet', () => {
  for (const [path, features, freeLabel, firstCol] of [
    // The year first, as on the Premium page, so the numerals I to IV name the same thing on both (2026-10-10).
    ['/sv/', ['Säsongsstatistik', 'Fältdagboken som PDF', '7 premiummärken', 'Fynd-kartan'], 'Alltid gratis:', 'Inget konto'],
    ['/', ['Season statistics', 'Field journal as PDF', '7 premium badges', 'Finds map'], 'Always free:', 'No account'],
  ] as const) {
    test(`premium och integritet på ${path}`, async ({ page }) => {
      await page.goto(path);
      const prem = page.locator('#premium');
      // Premium is exactly these four. Sound ID is free for everyone (BirdNET licence) and never listed as Premium.
      await expect(prem.locator('.feat h3')).toHaveText([...features]);
      await expect(prem.locator('.feats')).not.toContainText(/ljud|sound|audio|birdnet/i);
      await expect(prem.locator('.alw')).toContainText(/ljud|sound/);
      await expect(prem.locator('.alw b')).toHaveText(freeLabel);
      // In the light since 2026-10-10: the site's peach paper, and the flock flying a year around the Premium seal.
      await expect(prem).toHaveCSS('background-color', 'rgb(253, 229, 203)');
      const ring = prem.locator('[data-year-flock]');
      await expect(ring).toHaveAttribute('role', 'img');
      await expect(ring).toHaveAttribute('aria-label', /839/);
      expect(await ring.locator('svg use').count()).toBe(839);
      await expect(ring.locator('.yf-months text')).toHaveCount(12);
      await expect(ring.locator('.yseal')).toHaveAttribute('aria-hidden', 'true');
      // No prices anywhere on the page: purchases and prices are handled in the app, through Google Play.
      await expect(page.locator('main')).not.toContainText(/\d[\d\s.,]*(?:kr(?:onor)?|sek|eur|usd|:-)(?![\p{L}\p{N}])|(?<![\p{L}\p{N}])(?:kr|sek|eur|usd)\s?\d|[€$£]/iu);
      const priv = page.locator('#privacy');
      await expect(priv.locator('.cols li')).toHaveCount(3);
      await expect(priv.locator('.cols h3').first()).toHaveText(firstCol);
      await expect(priv.locator('a.plink')).toHaveAttribute('href', '/legal/privacy/');
    });
  }
});

test.describe('bloggen', () => {
  for (const [prefix, minRead, allNotes] of [
    ['/sv', 'min läsning', 'Alla blogginlägg'],
    ['', 'min read', 'All blog posts'],
  ] as const) {
    test(`listan och inlägget med bild på ${prefix || 'EN'}`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(`${prefix}/blog/`);
      await expect(page.locator('#site-nav')).toHaveClass(/nav--solid/);
      const card = page.locator(`main a.ncard[href="${prefix}/blog/why-birdy/"]`);
      await expect(card).toBeVisible();
      await expect(card.locator('img')).toHaveAttribute('alt', /.+/);
      await expect(card.locator('.ncard-meta')).toContainText(minRead);

      await page.goto(`${prefix}/blog/why-birdy/`);
      await expect(page.locator('.phead .aplate img')).toBeVisible();
      await expect(page.locator('.phead .ameta')).toContainText(minRead);
      await expect(page.locator('.article-prose blockquote')).toHaveCount(1);
      await expect(page.locator('.aend a[href*="play.google.com"]')).toHaveCount(1);
      await expect(page.locator('.aback a').first()).toContainText(allNotes);
      // The note's own Flock picture in the page's language (2026-10-10), cropped to 1200 × 630 for sharing.
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', new RegExp(`/_astro/why-birdy-flock-q25334-${prefix === '/sv' ? 'sv' : 'en'}[^/]*\\.jpg$`));
      const imageAlt = prefix === '/sv'
        ? 'På persikofärgat papper bildar en flock små fåglar en rödhake bredvid Birdys rad ”Känn igen fågeln. Bevara stunden.”'
        : 'On peach paper, a flock of small birds forms a European Robin next to Birdy’s line “Know the bird. Keep the moment.”';
      await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute('content', imageAlt);
      const ogWidth = page.locator('meta[property="og:image:width"]');
      if (await ogWidth.count()) await expect(ogWidth).toHaveAttribute('content', '1200');

      const albitHref = prefix === '/sv' ? 'https://www.albit.se/produkter/birdy/' : 'https://www.albit.se/en/products/birdy/';
      await expect(page.locator('.phead .aby a')).toHaveText('Albin Abrahamsson, AlbIT');
      await expect(page.locator('.phead .aby a')).toHaveAttribute('href', albitHref);

      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const posting = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'BlogPosting');
      expect(posting.author).toEqual({ '@type': 'Person', name: 'Albin Abrahamsson', url: 'https://www.albit.se/om-albin/' });
      expect(posting.publisher).toEqual({ '@type': 'Organization', name: 'AlbIT AB', url: 'https://www.albit.se/' });

      expect(errors).toEqual([]);
    });
  }

  // The home page shows the three newest notes side by side (Albin 2026-10-10: See the song and Why Birdy exists both on
  // the front page), in the list's own order, lined up at their tops and at their meta lines.
  test.describe('startsidans fältanteckningar', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    for (const [path, prefix] of [['/sv/', '/sv'], ['/', '']] as const) {
      test(`startsidan visar de tre senaste inläggen bredvid varandra på ${path}`, async ({ page }) => {
        await page.goto(`${prefix}/blog/`);
        const newestThree = (await page.locator('main a.ncard').evaluateAll((els) => els.map((a) => a.getAttribute('href')))).slice(0, 3);
        await page.setViewportSize({ width: 1440, height: 900 });
        await page.goto(path);
        const cards = page.locator('#field-notes a.ncard');
        expect(await cards.evaluateAll((els) => els.map((a) => a.getAttribute('href')))).toEqual(newestThree);
        // Today these are the two notes Albin asked to see there, with Birdy × AlbIT.
        expect(newestThree).toContain(`${prefix}/blog/see-the-song/`);
        expect(newestThree).toContain(`${prefix}/blog/why-birdy/`);
        for (const card of await cards.all()) await expect(card.locator('img')).toBeVisible();
        await page.locator('#field-notes').scrollIntoViewIfNeeded();
        const tops = await cards.evaluateAll((els) => els.map((a) => Math.round(a.getBoundingClientRect().top)));
        expect(new Set(tops).size, `kortens överkanter ${tops}`).toBe(1);
        const metas = await page.locator('#field-notes .ncard-meta').evaluateAll((els) => els.map((m) => Math.round(m.getBoundingClientRect().bottom)));
        expect(new Set(metas).size, `metaraderna ${metas}`).toBe(1);
      });
    }

    // The list (2026-10-10, "more clean"): the newest note starts below the torn edge, the rest fill their columns (two
    // for two), and the large card stacks below 1100 px so its picture is never cut in half.
    test('bloggens lista: första kortet under revan, kolumnerna fyllda, det stora kortet staplat på smalare skärmar', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/blog/');
      const head = (await page.locator('.bhead').boundingBox())!;
      const first = (await page.locator('main .first').boundingBox())!;
      expect(first.y).toBeGreaterThanOrEqual(head.y + head.height);
      const rest = await page.locator('main .grid a.ncard').count();
      const cols = await page.locator('main .grid').evaluate((g) => getComputedStyle(g).gridTemplateColumns.split(' ').length);
      expect(cols).toBe(rest === 2 || rest === 4 || rest === 1 ? 2 : 3);
      await page.setViewportSize({ width: 1000, height: 900 });
      const card = (await page.locator('main .first a.ncard').boundingBox())!;
      const img = (await page.locator('main .first .ncard-img').boundingBox())!;
      expect(Math.abs(img.width - card.width)).toBeLessThan(2);
      expect(Math.abs(img.width / img.height - 1600 / 840)).toBeLessThan(0.02);
    });
  });

  // The note about the See the song videos (2026-10-09): one self-hosted video (no third-party player), its credit
  // right under it, the four channels and the first week's species pages.
  for (const [prefix, lang, minRead, photo, recording, deed, species] of [
    ['/sv', 'sv', 'min läsning', 'Foto: Kathy Büscher', 'Inspelning: Benoît Van Hecke', 'deed.sv',
      ['/sv/arter/blames/', '/sv/arter/trana/', '/sv/arter/grasparv/', '/sv/arter/grasand/', '/sv/arter/koltrast/', '/sv/arter/ormvrak/', '/sv/arter/talgoxe/']],
    ['', 'en', 'min read', 'Photo: Kathy Büscher', 'Recording: Benoît Van Hecke', '',
      ['/species/eurasian-blue-tit/', '/species/common-crane/', '/species/house-sparrow/', '/species/mallard/', '/species/common-blackbird/', '/species/common-buzzard/', '/species/great-tit/']],
  ] as const) {
    test(`inlägget See the song har videon med krediten, kanalerna och artlänkarna (${lang})`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(`${prefix}/blog/see-the-song/`);
      await expect(page.locator('.phead .aplate img')).toBeVisible();
      await expect(page.locator('.phead .ameta')).toContainText(minRead);
      await expect(page.locator('.aby a')).toHaveText('Albin Abrahamsson, AlbIT');
      // The series' new flock cover, drawn wide in the page's language (Albin 2026-10-10, "the new minityr picture").
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', new RegExp(`/_astro/see-the-song-flock-q25404-${lang}[^/]*\\.jpg$`));

      // Nothing plays, and nothing but the poster loads, until the visitor presses play; it plays inline on phones.
      const figure = page.locator('.article-prose figure.note-video');
      const video = figure.locator('video');
      await expect(video).toHaveCount(1);
      await expect(page.locator('iframe')).toHaveCount(0);
      expect(await video.evaluate((v) => ({
        controls: v.hasAttribute('controls'), autoplay: v.hasAttribute('autoplay'), muted: v.hasAttribute('muted'),
        playsinline: v.hasAttribute('playsinline'), preload: v.getAttribute('preload'), poster: v.getAttribute('poster'),
      }))).toEqual({ controls: true, autoplay: false, muted: false, playsinline: true, preload: 'none', poster: '/video/see-the-song/eurasian-blue-tit-cover.jpg' });
      await expect(video.locator('source')).toHaveAttribute('src', '/video/see-the-song/eurasian-blue-tit.mp4');
      await expect(video.locator('track')).toHaveAttribute('kind', 'captions');
      await expect(video.locator('track')).toHaveAttribute('srclang', lang);
      for (const url of ['/video/see-the-song/eurasian-blue-tit.mp4', '/video/see-the-song/eurasian-blue-tit-cover.jpg', `/video/see-the-song/eurasian-blue-tit.${lang}.vtt`]) {
        expect((await page.request.get(url)).status(), url).toBe(200);
      }

      // The credit right under the video, as on the species pages: who, the licence (linked) and the Commons file.
      const caption = figure.locator('figcaption');
      await expect(caption).toContainText(photo);
      await expect(caption).toContainText(recording);
      await expect(caption.locator('a[href="https://commons.wikimedia.org/wiki/File:Blaumeise_%2864%29_%2834633517080%29.jpg"]')).toHaveText('Wikimedia Commons');
      await expect(caption.locator('a[href="https://commons.wikimedia.org/wiki/File:Cyanistes_caeruleus_-_Eurasian_Blue_Tit_XC538220.mp3"]')).toHaveText('Wikimedia Commons');
      await expect(caption.locator(`a[href="https://creativecommons.org/licenses/by/2.0/${deed}"]`)).toHaveText('CC BY 2.0');
      await expect(caption.locator(`a[href="https://creativecommons.org/licenses/by-sa/4.0/${deed}"]`)).toHaveText(['CC BY-SA 4.0', 'CC BY-SA 4.0']);
      // The cover's silhouette (the poster and the picture at the top): Wouter Koch, CC0, via PhyloPic.
      await expect(caption.locator(`a[href="https://creativecommons.org/publicdomain/zero/1.0/${deed}"]`)).toHaveText('CC0');
      await expect(caption.locator('a[href="https://www.phylopic.org/images/069c4833-e1ac-48e7-90d5-f7bd11000588"]')).toHaveText('PhyloPic');

      for (const [, href] of channels) await expect(page.locator(`.article-prose a[href="${href}"]`)).toHaveCount(1);
      await expect(page.locator('.article-prose a[href*="instagram.com/birdy.community"]')).toHaveCount(0);
      // All seven are built in the test data as well as on the live site; a page this build lacks would be plain text.
      const speciesLinks = page.locator('.article-prose a[href^="/sv/arter/"], .article-prose a[href^="/species/"]');
      expect(await speciesLinks.evaluateAll((els) => els.map((a) => a.getAttribute('href')))).toEqual([...species]);
      for (const href of species) expect((await page.request.get(href)).status(), href).toBe(200);
      // The note's own "Follow Birdy" heading and the footer's must not share an id (the footer's list is named by its id).
      const ids = await page.locator('[id]').evaluateAll((els) => els.map((el) => el.id));
      expect(ids.filter((id, i) => ids.indexOf(id) !== i), 'dubbla id').toEqual([]);
      expect(errors).toEqual([]);
    });
  }

  test('appens strukturerade data har AlbIT som skapare', async ({ page }) => {
    for (const path of ['/', '/sv/'] as const) {
      await page.goto(path);
      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const app = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'MobileApplication');
      expect(app.creator.name, path).toBe('AlbIT AB');
    }
  });

  // The species pages (Task 14) carry a lastmod of their own from their data (spec §12); every other page
  // still has none.
  test('webbplatskartan har lastmod för inläggen och artsidorna, inte för andra sidor', async ({ page }) => {
    const xml = await (await page.request.get('/sitemap-0.xml')).text();
    const blocks = xml.match(/<url>[\s\S]*?<\/url>/g) ?? [];
    const blockFor = (url: string) => blocks.find((b) => b.includes(`<loc>${url}</loc>`));
    const postDates = [
      // A rewritten note's lastmod is its updated date (2026-10-10).
      ['https://birdy.community/blog/why-birdy/', '2026-10-10'],
      ['https://birdy.community/sv/blog/why-birdy/', '2026-10-10'],
      ['https://birdy.community/blog/see-the-song/', '2026-10-10'],
      ['https://birdy.community/sv/blog/see-the-song/', '2026-10-10'],
      ['https://birdy.community/blog/birdy-x-albit/', '2026-10-10'],
      ['https://birdy.community/sv/blog/birdy-x-albit/', '2026-10-10'],
    ] as const;
    for (const [url, date] of postDates) {
      const block = blockFor(url);
      expect(block, url).toBeTruthy();
      expect(block, url).toMatch(new RegExp(`<lastmod>${date}`));
    }
    // No other URL in the whole sitemap carries a lastmod, not just the two obvious home-page checks.
    expect(blocks.length).toBeGreaterThan(postDates.length);
    const isSpeciesPage = (loc: string) => /^https:\/\/birdy\.community\/(sv\/arter|species)\//.test(loc);
    const isPost = (loc: string) => /^https:\/\/birdy\.community\/(sv\/)?blog\/[^/]+\/$/.test(loc);
    expect(blockFor('https://birdy.community/sv/arter/talgoxe/')).toMatch(/<lastmod>2026-11-25/);
    for (const block of blocks) {
      const loc = block.match(/<loc>(.*?)<\/loc>/)?.[1] ?? block;
      if (isPost(loc) || isSpeciesPage(loc)) continue;
      expect(block, loc).not.toMatch(/<lastmod>/);
    }
  });

  // Every note but the collab one (2026-10-10): its picture whole as a plate on the paper, the title on the paper below
  // it and the light menu from the start. Nothing is laid over the picture, so there is no dark photo hero any more.
  for (const [width, height] of [[390, 844], [1440, 900]] as const) {
    test(`inläggets bild är en hel platta med rubriken under i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      for (const path of ['/sv/blog/why-birdy/', '/blog/see-the-song/'] as const) {
        await page.goto(path);
        await expect(page.locator('.ahero'), path).toHaveCount(0);
        await expect(page.locator('#site-nav'), path).toHaveClass(/nav--solid/);
        await expect(page.locator('meta[name="theme-color"]'), path).toHaveAttribute('content', /^#f6efe2$/i);
        const plate = (await page.locator('.phead .aplate img').boundingBox())!;
        expect(Math.abs(plate.width / plate.height - 1600 / 840), `${path}: plattans form`).toBeLessThan(0.02);
        const title = (await page.locator('.phead h1').boundingBox())!;
        expect(title.y, `${path}: rubriken under bilden`).toBeGreaterThan(plate.y + plate.height);
        // Nothing lies over the picture: the middle of the plate is the picture itself.
        const hit = await page.evaluate(([x, y]) => document.elementFromPoint(x, y)?.tagName, [plate.x + plate.width / 2, plate.y + plate.height / 2]);
        expect(hit, `${path}: över bilden`).toBe('IMG');
        // No caption is written unless the note has one (imageCaption).
        await expect(page.locator('.phead figcaption'), path).toHaveCount(0);
        expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(width);
      }
    });
  }

  // "Varför Birdy finns" was rewritten on 2026-10-10: the date next to the original one, and the article's modified time.
  for (const [prefix, label] of [['/sv', 'Uppdaterad 10 oktober 2026'], ['', 'Updated 10 October 2026']] as const) {
    test(`ett omskrivet inlägg visar när det uppdaterades (${prefix || 'EN'})`, async ({ page }) => {
      await page.goto(`${prefix}/blog/why-birdy/`);
      await expect(page.locator('.phead .ameta')).toContainText(label);
      await expect(page.locator('meta[property="article:modified_time"]')).toHaveAttribute('content', /^2026-10-10/);
      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const posting = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'BlogPosting');
      expect(posting.datePublished).toMatch(/^2026-09-24/);
      expect(posting.dateModified).toMatch(/^2026-10-10/);
      await expect(page.locator('.article-prose a[href$="/blog/birdy-x-albit/"]')).toHaveCount(1);
      await page.goto(`${prefix}/blog/see-the-song/`);
      await expect(page.locator('meta[property="article:modified_time"]')).toHaveCount(0);
    });
  }

  // Birdy × AlbIT (2026-10-10): the hero where the two brands meet instead of the photo, the title on the paper, the light
  // menu from the start, and an end card for both brands.
  for (const [prefix, title] of [['/sv', 'Birdy × AlbIT: en rädsla för fåglar blev en app'], ['', 'Birdy × AlbIT: how a fear of birds became an app']] as const) {
    test(`inlägget Birdy × AlbIT har hjälten där varumärkena möts (${prefix || 'EN'})`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto(`${prefix}/blog/birdy-x-albit/`);
      const hero = page.locator('[data-collab-hero]');
      await expect(hero).toHaveAttribute('role', 'img');
      expect((await hero.getAttribute('aria-label'))?.length ?? 0).toBeGreaterThan(60);
      await expect(page.locator('.ahero')).toHaveCount(0);
      await expect(page.locator('.phead')).toHaveCount(0);
      await expect(hero.locator('svg.bxa-wide')).toBeVisible();
      await expect(hero.locator('svg.bxa-tall')).toBeHidden();
      // The flock: the bird defined once, used by both variants, and the AlbIT wordmark drawn in each.
      await expect(hero.locator('#bxa-bird use')).not.toHaveCount(0);
      await expect(hero.locator('svg.bxa-art > use[href="#bxa-bird"]')).toHaveCount(2);
      await expect(hero.locator('svg.bxa-art image')).toHaveCount(2);
      const wordmark = await hero.locator('svg.bxa-wide image').getAttribute('href');
      expect(wordmark).toMatch(/\/_astro\/albit-wordmark-white[^/]*\.webp$/);
      expect((await page.request.get(wordmark!)).status()).toBe(200);
      await expect(page.locator('h1')).toHaveText(title);
      await expect(page.locator('h1 .ch1-x')).toHaveText('×');
      // The menu is the light paper bar from the start, and the browser bar the same paper.
      await expect(page.locator('#site-nav')).toHaveClass(/nav--solid/);
      await expect(page.locator('#site-nav')).toHaveCSS('background-color', 'rgb(246, 239, 226)');
      await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute('content', /^#f6efe2$/i);
      // The logbook figure, one pull quote, the end card for both brands.
      await expect(page.locator('.article-prose .note-facts li')).toHaveCount(6);
      await expect(page.locator('.article-prose blockquote')).toHaveCount(1);
      await expect(page.locator('.cend .cend-b a[href*="play.google.com"]')).toHaveCount(1);
      await expect(page.locator('.cend .cend-a a')).toHaveAttribute('href', 'https://www.albit.se/');
      await expect(page.locator('.cend .cend-a img')).toHaveAttribute('alt', 'AlbIT');
      await expect(page.locator('.aend')).toHaveCount(0);
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', /\/_astro\/birdy-x-albit[^/]*\.jpg$/);
      expect(errors).toEqual([]);
    });
  }

  // The polished article (2026-10-10): a reading line at the top, a drop cap on the lead, the author and two more notes.
  test('inlägget har läslinjen, anfangen, skribenten och fler fältanteckningar', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/blog/why-birdy/');
    const bar = page.locator('[data-read-progress]');
    const scale = () => bar.evaluate((el) => new DOMMatrix(getComputedStyle(el).transform).a);
    expect(await scale()).toBe(0);
    await page.evaluate(() => window.scrollTo({ top: document.body.scrollHeight, behavior: 'instant' }));
    await expect.poll(scale).toBe(1);
    const lead = page.locator('.article-prose > p').first();
    expect(await lead.evaluate((el) => getComputedStyle(el, '::first-letter').float)).toBe('left');
    await expect(page.locator('.aauthor .aauthor-n')).toHaveText('Albin Abrahamsson');
    await expect(page.locator('.aauthor a')).toHaveAttribute('href', 'https://www.albit.se/om-albin/');
    const more = page.locator('.amore a.ncard');
    await expect(more).toHaveCount(2);
    expect(await more.evaluateAll((els) => els.map((a) => a.getAttribute('href')))).not.toContain('/sv/blog/why-birdy/');
    await expect(page.locator('.aend')).toHaveCSS('background-color', 'rgb(253, 229, 203)');
    // The collab note introduces AlbIT in its end card instead of the author box.
    await page.goto('/sv/blog/birdy-x-albit/');
    await expect(page.locator('.aauthor')).toHaveCount(0);
    await expect(page.locator('.amore a.ncard')).toHaveCount(2);
  });

  test('inlägget Birdy × AlbIT byter till den höga bilden på mobilen', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/blog/birdy-x-albit/');
    const hero = page.locator('[data-collab-hero]');
    await expect(hero.locator('svg.bxa-tall')).toBeVisible();
    await expect(hero.locator('svg.bxa-wide')).toBeHidden();
    const box = await hero.boundingBox();
    expect(Math.abs(box!.height / box!.width - 1100 / 800)).toBeLessThan(0.02);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  });

  // The gallery wall and the blog's band both turned light on 2026-10-10, so both kickers are the light sections' rust.
  test('listkickern och karusellkickern är rost på det ljusa pappret', async ({ page }) => {
    await page.goto('/sv/blog/');
    await expect(page.locator('.bhead .kick').first()).toHaveCSS('color', 'rgb(154, 69, 38)');

    await page.goto('/sv/');
    await expect(page.locator('.tour-head .kick').first()).toHaveCSS('color', 'rgb(154, 69, 38)');
  });

  for (const [prefix, home] of [['/sv', '/sv/'], ['', '/']] as const) {
    test(`länken till Så funkar det från inlägget pekar på en sektion som finns (${prefix || 'EN'})`, async ({ page }) => {
      await page.goto(`${prefix}/blog/why-birdy/`);
      const seeHow = page.locator('.aback a').last();
      await expect(seeHow).toHaveAttribute('href', `${home}#how-it-works`);
      await page.goto(home);
      await expect(page.locator('#how-it-works')).toHaveCount(1);
    });
  }
});

test.describe('frågor, slutet och ordningen', () => {
  test('startsidans sektioner kommer i rätt ordning, med hero först', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      await expect(page.locator('main > header.hero:first-child')).toHaveCount(1);
      const ids = await page.locator('main > section[id]').evaluateAll((els) => els.map((e) => e.id));
      expect(ids).toEqual(['season', 'app', 'how-it-works', 'journal', 'guide', 'premium', 'privacy', 'field-notes', 'faq', 'download']);
    }
  });

  for (const [path, firstQ, headline, kicker, sub] of [
    ['/sv/', 'Fungerar Birdy utan täckning?', 'Ta med Birdy ut i fält.', 'Birdy för Android och snart iPhone', 'Gratis att ladda ner. Inget konto. Fungerar utan täckning.'],
    ['/', 'Does Birdy work without a signal?', 'Take Birdy into the field.', 'Birdy for Android, soon on iPhone', 'Free to download. No account. Works without a signal.'],
  ] as const) {
    test(`frågor och slutsektion på ${path}`, async ({ page }) => {
      await page.goto(path);
      const faq = page.locator('#faq');
      const details = faq.locator('details');
      await expect(details).toHaveCount(5);
      await expect(details.first()).toHaveAttribute('open', '');
      for (let i = 1; i < 5; i++) await expect(details.nth(i), `fråga ${i + 1} ska vara stängd`).not.toHaveAttribute('open', '');
      await expect(faq.locator('summary .q').first()).toHaveText(firstQ);

      const download = page.locator('#download');
      await expect(download.locator('h2')).toHaveText(headline);
      await expect(download.locator('.kick')).toContainText(kicker);
      await expect(download.locator('.sub')).toHaveText(sub);
      await expect(download.locator('a[href*="play.google.com"]')).toHaveCount(1);
      await expect(download.locator('a .appstore'), 'App Store-märket ska inte ligga i en länk').toHaveCount(0);

      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const faqLd = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'FAQPage');
      expect(faqLd.mainEntity).toHaveLength(5);
      expect(faqLd.inLanguage).toBe(path === '/sv/' ? 'sv' : 'en');
      const visibleQ = await faq.locator('summary .q').allTextContents();
      const visibleA = await faq.locator('.a').allTextContents();
      expect(faqLd.mainEntity.map((m: { name: string }) => m.name)).toEqual(visibleQ);
      expect(faqLd.mainEntity.map((m: { acceptedAnswer: { text: string } }) => m.acceptedAnswer.text)).toEqual(visibleA);
    });
  }

  test('inget iPhone-datum och ingen "håll i 3 sekunder" på startsidan, även i stängda frågor och i html-koden', async ({ page }) => {
    // main.innerText() skips text inside closed <details> (quality-review item 2), so 4 of the 5
    // FAQ answers — including the iPhone one — were never actually checked. toContainText reads
    // textContent, which sees closed-details text too; the raw HTML check also covers the
    // FAQPage JSON-LD and any meta tags carrying the same old claims.
    const noOldClaims = /slutet av september|end of september|(3|tre)[\s-]*sekund|(3|three)[\s-]*second/i;
    for (const path of ['/sv/', '/'] as const) {
      await page.goto(path);
      await expect(page.locator('main')).not.toContainText(noOldClaims);
      expect(await (await page.request.get(path)).text()).not.toMatch(noOldClaims);
    }
  });

  test('alla menylänkar och sidfotslänkar till startsidan pekar på en sektion som finns', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      const hrefs = await page.locator('#site-nav a, footer a').evaluateAll((els) => els.map((a) => a.getAttribute('href') ?? ''));
      const hashes = [...new Set(hrefs.filter((h) => h.startsWith(`${path}#`)).map((h) => h.slice(path.length + 1)))];
      expect(hashes.length, `${path}: inga ankarlänkar hittades`).toBeGreaterThan(3);
      for (const id of hashes) await expect(page.locator(`#${id}`), `${path}#${id}`).toHaveCount(1);
    }
  });

  test.describe('kontrast i #download mot väggen', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    // Pixel-contrast guard (quality-review item 1). Since 2026-10-08 the reedling hangs beside the words as a
    // plate instead of under them, so the text sits on the espresso wall; the guard stays so that a later change
    // can't put the words back over a photo. Hides #download's content, screenshots what is behind the text, and
    // checks the kicker/sub (normal text, needs 4.5:1) and the headline's accent (large text, needs 3:1).
    for (const path of ['/sv/', '/'] as const) {
      for (const width of [320, 390, 800, 1440] as const) {
        test(`kicker, underrad och rubrikaccent klarar kontrasten mot fotot i ${width}px på ${path}`, async ({ page }) => {
          await page.setViewportSize({ width, height: 900 });
          await page.goto(path);
          const download = page.locator('#download');
          await download.scrollIntoViewIfNeeded();
          await expect.poll(() => download.locator('img').evaluateAll((imgs) =>
            imgs.every((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0))).toBe(true);
          await page.addStyleTag({ content: '#download .wrap > * { visibility: hidden !important; }' });
          await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));

          const kickerRatio = await textContrastAgainstBackground(page, download.locator('.kick'));
          expect(kickerRatio, `kicker: ${kickerRatio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);

          const subRatio = await textContrastAgainstBackground(page, download.locator('.sub'));
          expect(subRatio, `underrad: ${subRatio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);

          const accentRatio = await textContrastAgainstBackground(page, download.locator('.journal-headline .accent'));
          expect(accentRatio, `rubrikaccent: ${accentRatio.toFixed(2)}:1`).toBeGreaterThanOrEqual(3);
        });
      }
    }
  });
});
