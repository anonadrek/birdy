import { test, expect, type Locator, type Page } from '@playwright/test';
import sharp from 'sharp';
import { trackConsoleErrors } from './test-helpers';

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

test.describe('meny och sidfot', () => {
  for (const [path, label, getApp] of [['/sv/', 'Så funkar det', 'Hämta appen'], ['/', 'How it works', 'Get the app']] as const) {
    test(`menyn på ${path} har nya länkar och blir mossgrön efter första vyn`, async ({ page }) => {
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
      await expect(nav).toHaveCSS('background-color', 'rgb(31, 42, 25)');
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

  test('menyn markerar Fältanteckningar på bloggen', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/sv/blog/');
    await expect(page.locator('#site-nav .links a[aria-current="page"]')).toHaveText('Fältanteckningar');
  });

  test('sidfoten har kolumnerna', async ({ page }) => {
    await page.goto('/sv/');
    const footer = page.locator('footer.footer');
    await expect(footer.locator('.fh')).toHaveText(['Utforska', 'Läs', 'Information']);
    await expect(footer.locator('a[href="/legal/privacy/"]')).toHaveText('Integritetspolicy');
  });

  for (const [path, albitHref, workshop, line] of [
    ['/sv/', 'https://www.albit.se/produkter/birdy/', 'Från samma verkstad', 'HR-verktyg för chefer i växande bolag'],
    ['/', 'https://www.albit.se/en/products/birdy/', 'From the same workshop', 'An HR tool for managers in growing companies'],
  ] as const) {
    test(`sidfoten på ${path} länkar till AlbIT och LoopLead`, async ({ page }) => {
      await page.goto(path);
      const footer = page.locator('footer.footer');
      await expect(footer.locator(`a[href="${albitHref}"]`)).toHaveText('AlbIT');
      await expect(footer.locator('.sibling')).toContainText(workshop);
      const looplead = footer.locator('a[href="https://looplead.se/"]');
      await expect(looplead).toContainText('LoopLead');
      await expect(looplead).toContainText(line);
    });
  }
});

test.describe('utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('startsidans meny är mossgrön och den döda menyknappen dold', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await expect(page.locator('#site-nav')).toHaveCSS('background-color', 'rgb(31, 42, 25)');
    await expect(page.locator('#site-nav .menu-toggle')).toBeHidden();
  });
});

test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  for (const [path, line1, line2, kicker] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Fågelguide och fältdagbok'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Bird guide and field journal'],
  ] as const) {
    test(`rubrik, kicker, metarad och telefon på ${path}`, async ({ page }) => {
      const errors = trackConsoleErrors(page);
      await page.goto(path);
      const hero = page.locator('[data-hero]');
      await expect(hero.locator('h1')).toContainText(line1);
      await expect(hero.locator('h1 em')).toHaveText(line2);
      await expect(hero.locator('.copy .kick')).toHaveText(kicker);
      await expect(hero.locator('.meta li')).toHaveCount(3);
      await expect(hero.locator('[data-hero-phone] .ph[role="img"]')).toHaveCount(1);
      await expect(hero.locator('[data-robin] img').first()).toBeVisible();
      await expect(hero.locator('[data-birdy] .birdy-shape')).toHaveCSS('opacity', '1');
      expect(errors).toEqual([]);
    });
  }

  for (const width of [390, 1024, 1280, 1440, 1920]) {
    test(`telefonen täcker inte rödhaken och rödhaken syns helt i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const phone = (await page.locator('[data-hero-phone] .ph').boundingBox())!;
      const robin = (await page.locator('[data-robin]').boundingBox())!;
      const overlaps = phone.x < robin.x + robin.width && robin.x < phone.x + phone.width
        && phone.y < robin.y + robin.height && robin.y < phone.y + phone.height;
      expect(overlaps, `telefon ${JSON.stringify(phone)} rödhake ${JSON.stringify(robin)}`).toBe(false);
      expect(robin.x).toBeGreaterThanOrEqual(0);
      expect(robin.x + robin.width).toBeLessThanOrEqual(width);
    });
  }

  for (const [width, height] of [[1280, 720], [1366, 768], [1536, 730], [1600, 720], [1650, 700], [1920, 800], [1999, 800], [2000, 960], [2560, 1300]] as const) {
    test(`korta och breda fönster: telefonen går fri och fötterna syns i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const phone = (await page.locator('[data-hero-phone] .ph').boundingBox())!;
      const robin = (await page.locator('[data-robin]').boundingBox())!;
      const photo = (await page.locator('[data-hero] .photo').boundingBox())!;
      const overlaps = phone.x < robin.x + robin.width && robin.x < phone.x + phone.width
        && phone.y < robin.y + robin.height && robin.y < phone.y + phone.height;
      expect(overlaps, `telefon ${JSON.stringify(phone)} rödhake ${JSON.stringify(robin)}`).toBe(false);
      expect(robin.y + robin.height, 'fötterna ryms i fotot').toBeLessThanOrEqual(photo.y + photo.height);
      expect(robin.x + robin.width).toBeLessThanOrEqual(width);
      const last = (await page.locator('[data-hero] .meta li').last().boundingBox())!;
      const hits = await page.evaluate(({ x, ys }) => ys.map((y) => !!document.elementFromPoint(x, y)?.closest('[data-hero-phone]')),
        { x: last.x + last.width + 16, ys: [last.y + 1, last.y + last.height / 2, last.y + last.height - 1] });
      expect(hits, 'metaraden har minst 8 px synlig luft till telefonen').toEqual([false, false, false]);
    });
  }

  for (const [width, height] of [[390, 844], [1024, 768], [1440, 900]] as const) {
    test(`menyn blir mossgrön innan texten når den i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/');
      const nav = page.locator('#site-nav');
      const navH = await nav.evaluate((n) => n.getBoundingClientRect().height);
      const copyTop = await page.locator('[data-hero] .copy').evaluate((c) => c.getBoundingClientRect().top + scrollY);
      const settle = () => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), Math.max(0, copyTop - navH - 30));
      await settle();
      await expect(nav).not.toHaveClass(/is-solid/);
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), copyTop - navH + 2);
      await expect(nav).toHaveClass(/is-solid/);
    });
  }

  test('rödhakens ruta börjar under menyn i 1920 px', async ({ page }) => {
    await page.setViewportSize({ width: 1920, height: 900 });
    await page.goto('/sv/');
    const nav = (await page.locator('#site-nav').boundingBox())!;
    const robin = (await page.locator('[data-robin]').boundingBox())!;
    expect(robin.y).toBeGreaterThanOrEqual(nav.y + nav.height);
  });

  test('rubriken ryms på två rader på dator', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      for (const width of [1024, 1280, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        const lines = await page.locator('[data-hero] h1').evaluate((h) => Math.round(h.getBoundingClientRect().height / parseFloat(getComputedStyle(h).lineHeight)));
        expect(lines, `${path} ${width} px`).toBeLessThanOrEqual(2);
      }
    }
  });
});

test.describe('Birdy-fågeln flyger', () => {
  test('fågeln flyger iväg vid scroll och kommer tillbaka', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const bird = page.locator('[data-birdy]');
    await expect(bird.locator('.birdy-shape')).toHaveCSS('opacity', '1');
    await page.evaluate(() => window.scrollTo({ top: 320, behavior: 'instant' }));
    await expect.poll(() => bird.evaluate((b) => Number(getComputedStyle(b).opacity))).toBeLessThan(0.05);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await expect.poll(() => bird.evaluate((b) => getComputedStyle(b).opacity)).toBe('1');
    await expect.poll(() => bird.evaluate((b) => getComputedStyle(b).transform)).toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
  });
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
      await expect(page.locator('#how-it-works .row:nth-child(3) .free')).toHaveText(free);
      await expect(page.locator('#journal h2')).toHaveText(journal);
      await expect(page.locator('#journal .facts dt')).toHaveText(['34', '27', '0']);
      await expect(page.locator('#journal .facts dd').first()).toHaveText(label);
      await expect(page.locator('#journal img')).toHaveAttribute('alt', /.+/);
    });
  }

  test.describe('telefonen och smala skärmar', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    for (const [width, height] of [[390, 844], [1024, 900], [1279, 900], [1280, 720]] as const) {
      test(`herotelefonen slutar ovanför Så funkar det i ${width}×${height}`, async ({ page }) => {
        await page.setViewportSize({ width, height });
        await page.goto('/sv/');
        const phone = (await page.locator('[data-hero-phone] .ph').boundingBox())!;
        const kick = (await page.locator('#how-it-works .kick').first().boundingBox())!;
        expect(phone.y + phone.height, 'telefonens underkant').toBeLessThan(kick.y);
      });
    }
    test('inget sidledes scroll på 360 px', async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 780 });
      for (const path of ['/sv/', '/']) {
        await page.goto(path);
        expect(await page.evaluate(() => document.documentElement.scrollWidth), path).toBeLessThanOrEqual(360);
      }
    });
  });
});

test.describe('appkarusellen', () => {
  test('åtta telefoner och pilarna byter text (SV)', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour.locator('.slide')).toHaveCount(8);
    await expect(tour.locator('.slide .ph[role="img"]')).toHaveCount(8);
    await tour.scrollIntoViewIfNeeded();
    const title = tour.locator('[data-ch]');
    await expect(title).toHaveText('Tre sätt att fånga');
    await expect(tour.locator('[data-cp]')).toBeHidden();
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Ärlig om hur säker den är');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Lyssna på lätet');
    await tour.locator('[data-prev]').click();
    await expect(title).toHaveText('Ärlig om hur säker den är');
    expect(errors).toEqual([]);
  });

  test('sista skärmen är märkt Premium (EN)', async ({ page }) => {
    await page.goto('/');
    const tour = page.locator('#app');
    await tour.scrollIntoViewIfNeeded();
    await tour.locator('[data-track]').evaluate((t) => t.scrollTo({ left: t.scrollWidth }));
    await expect(tour.locator('[data-ch]')).toHaveText('A year in the field');
    await expect(tour.locator('[data-cp]')).toBeVisible();
    await expect(tour.locator('[data-cp]')).toHaveText('Premium');
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
    await expect(tour.locator('[data-ch]')).toHaveText('Ärlig om hur säker den är');
  });

  test('bildtexten har samma höjd på alla skärmar', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const path of ['/sv/', '/']) {
      await page.goto(path);
      const tour = page.locator('#app');
      await tour.scrollIntoViewIfNeeded();
      const cap = tour.locator('.cap');
      const heights: number[] = [(await cap.boundingBox())!.height];
      for (let i = 1; i <= 7; i++) {
        await tour.locator('[data-next]').click();
        const expected = await tour.locator('.slide').nth(i).getAttribute('data-h');
        await expect(tour.locator('[data-ch]')).toHaveText(expected ?? '');
        heights.push((await cap.boundingBox())!.height);
      }
      expect(Math.max(...heights) - Math.min(...heights), path).toBeLessThanOrEqual(1);
    }
  });

  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    test('karusellen och rödhaken står still', async ({ page }) => {
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
    test('bildtextlistan visar alla åtta skärmar', async ({ page }) => {
      await page.goto('/sv/');
      await expect(page.locator('#app .cap-list li')).toHaveCount(8);
      await expect(page.locator('#app .foot')).toBeHidden();
    });
  });
});

test.describe('premium och integritet', () => {
  for (const [path, features, freeLabel, firstCol] of [
    ['/sv/', ['Fynd-kartan', 'Fältdagboken som PDF', 'Säsongsstatistik', '7 premiummärken'], 'Alltid gratis:', 'Inget konto'],
    ['/', ['Finds map', 'Field journal as PDF', 'Season statistics', '7 premium badges'], 'Always free:', 'No account'],
  ] as const) {
    test(`premium och integritet på ${path}`, async ({ page }) => {
      await page.goto(path);
      const prem = page.locator('#premium');
      // Premium is exactly these four. Sound ID is free for everyone (BirdNET licence) and never listed as Premium.
      await expect(prem.locator('.feat h3')).toHaveText([...features]);
      await expect(prem.locator('.feats')).not.toContainText(/ljud|sound|audio|birdnet/i);
      await expect(prem.locator('.alw')).toContainText(/ljud|sound/);
      await expect(prem.locator('.alw b')).toHaveText(freeLabel);
      await expect(prem.locator('.pseal')).toHaveAttribute('aria-hidden', 'true');
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
    ['/sv', 'min läsning', 'Alla fältanteckningar'],
    ['', 'min read', 'All field notes'],
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
      await expect(page.locator('.ahero img')).toBeVisible();
      await expect(page.locator('.ahero .ameta')).toContainText(minRead);
      await expect(page.locator('.article-prose blockquote')).toHaveCount(1);
      await expect(page.locator('.aend a[href*="play.google.com"]')).toHaveCount(1);
      await expect(page.locator('.aback a').first()).toContainText(allNotes);
      await expect(page.locator('meta[property="og:image"]')).toHaveAttribute('content', /\/_astro\/rodhake-q25334[^/]*\.jpg$/);
      const imageAlt = prefix === '/sv'
        ? 'En rödhake som sitter på en vissnad hortensia och tittar åt vänster'
        : 'A European robin perched on a faded hydrangea, looking left';
      await expect(page.locator('meta[property="og:image:alt"]')).toHaveAttribute('content', imageAlt);
      const ogWidth = page.locator('meta[property="og:image:width"]');
      if (await ogWidth.count()) await expect(ogWidth).toHaveAttribute('content', '1200');

      const albitHref = prefix === '/sv' ? 'https://www.albit.se/produkter/birdy/' : 'https://www.albit.se/en/products/birdy/';
      await expect(page.locator('.ahero .aby a')).toHaveText('Albin Abrahamsson, AlbIT');
      await expect(page.locator('.ahero .aby a')).toHaveAttribute('href', albitHref);

      const ld = JSON.parse((await page.locator('script[type="application/ld+json"]').textContent()) ?? '{}');
      const posting = ld['@graph'].find((n: { '@type': string }) => n['@type'] === 'BlogPosting');
      expect(posting.author).toEqual({ '@type': 'Person', name: 'Albin Abrahamsson', url: 'https://www.albit.se/om-albin/' });
      expect(posting.publisher).toEqual({ '@type': 'Organization', name: 'AlbIT AB', url: 'https://www.albit.se/' });

      expect(errors).toEqual([]);
    });
  }

  for (const [path, href] of [['/sv/', '/sv/blog/why-birdy/'], ['/', '/blog/why-birdy/']] as const) {
    test(`startsidan visar senaste inlägget som fotokort på ${path}`, async ({ page }) => {
      await page.goto(path);
      await expect(page.locator(`#field-notes a.ncard[href="${href}"] img`)).toBeVisible();
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

  test('webbplatskartan har lastmod endast för inläggen', async ({ page }) => {
    const xml = await (await page.request.get('/sitemap-0.xml')).text();
    const blocks = xml.match(/<url>[\s\S]*?<\/url>/g) ?? [];
    const blockFor = (url: string) => blocks.find((b) => b.includes(`<loc>${url}</loc>`));
    const postUrls = ['https://birdy.community/blog/why-birdy/', 'https://birdy.community/sv/blog/why-birdy/'];
    for (const url of postUrls) {
      const block = blockFor(url);
      expect(block, url).toBeTruthy();
      expect(block, url).toMatch(/<lastmod>2026-09-24/);
    }
    // No other URL in the whole sitemap carries a lastmod, not just the two obvious home-page checks.
    expect(blocks.length).toBeGreaterThan(postUrls.length);
    for (const block of blocks) {
      const loc = block.match(/<loc>(.*?)<\/loc>/)?.[1] ?? block;
      if (postUrls.includes(loc)) continue;
      expect(block, loc).not.toMatch(/<lastmod>/);
    }
  });

  for (const [width, height] of [[390, 844], [1440, 900]] as const) {
    test(`menyn på inlägget blir mossgrön innan rubriken når den i ${width}×${height}`, async ({ page }) => {
      await page.setViewportSize({ width, height });
      await page.goto('/sv/blog/why-birdy/');
      const nav = page.locator('#site-nav');
      const navH = await nav.evaluate((n) => n.getBoundingClientRect().height);
      const inTop = await page.locator('.ahero .in').evaluate((c) => c.getBoundingClientRect().top + scrollY);
      const settle = () => page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), Math.max(0, inTop - navH - 30));
      await settle();
      await expect(nav).not.toHaveClass(/is-solid/);
      await page.evaluate((y) => window.scrollTo({ top: y, behavior: 'instant' }), inTop - navH + 2);
      await expect(nav).toHaveClass(/is-solid/);
    });
  }

  test.describe('kontrast över inläggsfotot', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    // Pixel-contrast guard (review item 1c): hides the hero text and the transparent nav's link
    // text, screenshots what's really behind them, and checks the two worst known spots (the
    // apricot kicker, and the first transparent nav link) clear WCAG AA at load (scrollY 0, before
    // the nav has flipped solid) at 1440×900. The full sweep across widths/locales/scroll steps
    // that justified the chosen scrim values lives in the PR report, not in CI, to keep this fast.
    test('kickern och den första menylänken klarar 4.5:1 mot fotot', async ({ page }) => {
      await page.setViewportSize({ width: 1440, height: 900 });
      await page.goto('/sv/blog/why-birdy/');
      await page.addStyleTag({ content: '.ahero .in * { visibility: hidden !important; } #site-nav .links a { visibility: hidden !important; }' });
      await page.evaluate(() => new Promise((r) => requestAnimationFrame(() => requestAnimationFrame(r))));
      await expect(page.locator('#site-nav')).not.toHaveClass(/is-solid/);

      const kickerRatio = await textContrastAgainstBackground(page, page.locator('.ahero .in .kick'));
      expect(kickerRatio, `kicker mot fotot: ${kickerRatio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);

      const navLinkRatio = await textContrastAgainstBackground(page, page.locator('#site-nav .links a').first());
      expect(navLinkRatio, `första menylänken mot fotot: ${navLinkRatio.toFixed(2)}:1`).toBeGreaterThanOrEqual(4.5);
    });
  });

  test('listkickern och karusellkickern är apricot, inte bladets stil', async ({ page }) => {
    await page.goto('/sv/blog/');
    await expect(page.locator('.bhead .kick').first()).toHaveCSS('color', 'rgb(242, 178, 122)');

    await page.goto('/sv/');
    await expect(page.locator('.tour-head .kick').first()).toHaveCSS('color', 'rgb(242, 178, 122)');
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
      expect(ids).toEqual(['how-it-works', 'journal', 'app', 'guide', 'premium', 'privacy', 'field-notes', 'faq', 'download']);
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

  test.describe('kontrast i #download mot fotot', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });

    // Pixel-contrast guard (quality-review item 1): the scrim's stops are percentages of the
    // section while the text column is a fixed width, so at medium/narrow widths the dark part of
    // the gradient doesn't reach far enough under the text. Hides #download's text, screenshots
    // the real photo behind it, and checks the kicker/sub (normal text, needs ≥4.5:1) and the
    // headline's accent span (large text, needs ≥3:1) in both languages. Mirrors 'kickern och den
    // första menylänken klarar 4.5:1 mot fotot' above. Widths: the review's own 390/800/1440, plus
    // 320 — in this environment the unfixed CSS is only knife-edge (~4.5-4.6:1) at exactly
    // 390/800/1440, not clearly red, while 320 reproducibly fails pre-fix (~4.2:1), so 320 is what
    // makes 'must fail before fixing' provable here (see the task report for the measured numbers).
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
