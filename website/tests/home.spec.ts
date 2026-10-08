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
  for (const [path, label, getApp] of [['/sv/', 'Arter', 'Hämta appen'], ['/', 'Species', 'Get the app']] as const) {
    test(`menyn på ${path} har nya länkar och blir espressobrun efter första vyn`, async ({ page }) => {
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
      await expect(nav).toHaveCSS('background-color', 'rgb(42, 29, 23)');
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
    await expect(footer.locator('.fh')).toHaveText(['Arter', 'Utforska', 'Läs', 'Information']);
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
});

test.describe('utan JavaScript', () => {
  test.use({ javaScriptEnabled: false });

  test('startsidans meny är espressobrun och den döda menyknappen dold', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/sv/');
    await expect(page.locator('#site-nav')).toHaveCSS('background-color', 'rgb(42, 29, 23)');
    await expect(page.locator('#site-nav .menu-toggle')).toBeHidden();
  });
});

test.describe('första vyn', () => {
  test.use({ contextOptions: { reducedMotion: 'reduce' } });

  // The fixture build pins BIRDY_TODAY=2026-10-15 (package.json build:fixtures); every build picks from the shipped app's
  // list (src/data/app-species-1.3.0.json), so the app's Dagens fågel is Hornuggla (Q25384), which has a fixture page, on
  // the day 1.3.0 goes out: the plate shows it with the line about the app.
  for (const [path, line1, line2, kicker, plate, name, same, credit] of [
    ['/sv/', 'Känn igen fågeln.', 'Bevara stunden.', 'Fågelguide och fältdagbok', 'Dagens fågel · tors 15 okt', 'Hornuggla', 'samma fågel som i appen i dag', 'Foto: Testfotograf, CC BY 4.0, via Wikimedia Commons, nedskalad'],
    ['/', 'Know the bird.', 'Keep the moment.', 'Bird guide and field journal', 'Bird of the day · Thu 15 Oct', 'Long-eared Owl', 'the same bird as in the app today', 'Photo: Testfotograf, CC BY 4.0, via Wikimedia Commons, resized'],
  ] as const) {
    test(`rubrik, kicker och Dagens fågel som plansch på ${path}`, async ({ page, request }) => {
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
      await expect(hero.locator('.dp-top .kick')).toHaveText(plate);
      await expect(hero.locator('.dp-no')).toHaveText('Pl. 288');
      await expect(hero.locator('.dp-name')).toHaveText(name);
      await expect(hero.locator('.dp-lat')).toHaveText('Asio otus');
      await expect(hero.locator('.dp-bars i')).toHaveCount(12);
      await expect(hero.locator('.dp-bars i.now')).toHaveCount(1);
      await expect(hero.locator('.dp-letters .now')).toHaveText('O');
      // Hornuggla's test photo is CC BY and the only one it has: allowed on the plate, which shows the photo whole,
      // and credited exactly like the species page (photographer, linked licence, source).
      await expect(hero.locator('[data-credit]')).toHaveText(credit);
      await expect(hero.locator('[data-credit] a[href^="https://creativecommons.org/licenses/by/4.0/"]')).toHaveText('CC BY 4.0');
      await expect(hero.locator('[data-credit] a[href^="https://commons.wikimedia.org/"]')).toHaveCount(1);
      const href = await hero.locator('.dp-read').getAttribute('href');
      expect(href).toMatch(path === '/sv/' ? /^\/sv\/arter\/hornuggla\/$/ : /^\/species\/long-eared-owl\/$/);
      expect((await request.get(href!)).status()).toBe(200);
      await expect(hero.locator('[data-same-as-app]')).toHaveText(same);
      await expect(hero.locator('[data-same-as-app]')).toBeVisible();
      // The app's bird on a live day: the handwritten note is the one that says so (review I4; the empty build checks
      // the other note, without "och i appen", in scripts/check-empty-hub.mjs).
      await expect(hero.locator('.intro .mnote')).toHaveText(path === '/sv/' ? 'en ny fågel varje dag, här och i appen' : 'a new bird every day, here and in the app');
      const img = hero.locator('.dp-photo img');
      await expect(img).toHaveAttribute('loading', 'eager');
      await expect(img).toHaveAttribute('fetchpriority', 'high');
      await expect(img).toHaveAttribute('width', /^\d+$/);
      await expect(img).toHaveAttribute('height', /^\d+$/);
      await expect(hero.locator('[data-birdy] .birdy-shape')).toHaveCSS('opacity', '1');
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

  for (const width of [320, 390, 768, 1024, 1280, 1440, 1920]) {
    test(`fotot visas helt och etiketten ligger bara på passepartouten i ${width} px`, async ({ page }) => {
      await page.setViewportSize({ width, height: 900 });
      await page.goto('/sv/');
      const img = page.locator('[data-hero] .dp-photo img');
      await img.evaluate((i: HTMLImageElement) => i.decode());
      const { ratio, natural } = await img.evaluate((i: HTMLImageElement) => {
        const r = i.getBoundingClientRect();
        return { ratio: r.width / r.height, natural: i.naturalWidth / i.naturalHeight };
      });
      expect(Math.abs(ratio - natural), 'inte beskuret').toBeLessThan(0.02);
      const photo = (await img.boundingBox())!;
      const label = (await page.locator('[data-hero] .dp-label').boundingBox())!;
      expect(label.y, 'etiketten börjar under fotot').toBeGreaterThan(photo.y + photo.height + 4);
      const frame = (await page.locator('[data-hero] .dp-frame').boundingBox())!;
      expect(frame.x).toBeGreaterThanOrEqual(0);
      expect(frame.x + frame.width).toBeLessThanOrEqual(width);
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

  test('planschen börjar under menyn och den handskrivna raden ryms på en rad på dator', async ({ page }) => {
    for (const path of ['/sv/', '/']) {
      for (const width of [1024, 1280, 1440, 1920]) {
        await page.setViewportSize({ width, height: 900 });
        await page.goto(path);
        await page.evaluate(() => document.fonts.ready);
        const nav = (await page.locator('#site-nav').boundingBox())!;
        const plate = (await page.locator('[data-hero] .dp-top').boundingBox())!;
        expect(plate.y, `${path} ${width} px`).toBeGreaterThanOrEqual(nav.y + nav.height);
        const em = await page.locator('[data-hero] h1 em').evaluate((e) => ({
          lines: Math.round(e.getBoundingClientRect().height / parseFloat(getComputedStyle(e).lineHeight)),
          over: e.scrollWidth - (e.parentElement as HTMLElement).clientWidth,
        }));
        expect(em.lines, `${path} ${width} px`).toBe(1);
        expect(em.over, `${path} ${width} px: raden går utanför spalten`).toBeLessThanOrEqual(0);
      }
    }
  });
});

test.describe('Birdy-fågeln flyger', () => {
  test('fågeln landar på planschen, flyger iväg vid scroll och kommer tillbaka', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto('/sv/');
    const bird = page.locator('[data-birdy]');
    await expect(bird.locator('.birdy-shape')).toHaveCSS('opacity', '1', { timeout: 8000 });
    await page.evaluate(() => window.scrollTo({ top: 320, behavior: 'instant' }));
    await expect.poll(() => bird.evaluate((b) => Number(getComputedStyle(b).opacity))).toBeLessThan(0.05);
    await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
    await expect.poll(() => bird.evaluate((b) => getComputedStyle(b).opacity)).toBe('1');
    await expect.poll(() => bird.evaluate((b) => getComputedStyle(b).transform)).toMatch(/^(none|matrix\(1, 0, 0, 1, 0, 0\))$/);
  });
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
      await expect(page.locator('#how-it-works .row:nth-child(3) .free')).toHaveText(free);
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
  test('sex riktiga skärmar och pilarna byter text (SV)', async ({ page }) => {
    const errors = trackConsoleErrors(page);
    await page.goto('/sv/');
    const tour = page.locator('#app');
    await expect(tour.locator('.slide')).toHaveCount(6);
    await expect(tour.locator('.slide .phone picture source[type="image/avif"]')).toHaveCount(6);
    await expect(tour.locator('.plno span')).toHaveText(['Identifiera', 'Ljud-ID', 'Match', 'Mina arter', 'Uppslagsverk', 'Artprofil']);
    await expect(tour.locator('.plno b')).toHaveText(['Pl. I', 'Pl. II', 'Pl. III', 'Pl. IV', 'Pl. V', 'Pl. VI']);
    await tour.scrollIntoViewIfNeeded();
    const title = tour.locator('[data-ch]');
    await expect(title).toHaveText('Tre sätt att fånga');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Lyssna efter sång');
    await tour.locator('[data-next]').click();
    await expect(title).toHaveText('Ärlig om hur säker den är');
    await tour.locator('[data-prev]').click();
    await expect(title).toHaveText('Lyssna efter sång');
    await tour.locator('[data-track]').press('End');
    await expect(title).toHaveText('Allt om arten på ett uppslag');
    expect(errors).toEqual([]);
  });

  test('svenska sidan visar svenska skärmar, engelska sidan engelska, och aldrig kartan', async ({ page }) => {
    for (const [path, lang] of [['/sv/', 'sv'], ['/', 'en']] as const) {
      await page.goto(path);
      const screens = await page.locator('#app .slide').evaluateAll((els) => els.map((e) => (e as HTMLElement).dataset.screen));
      expect(screens).toEqual(['01-identifiera', '07-lyssna', '02-match', '03-mina-arter', '04-uppslagsverk', '05-artprofil']);
      await expect(page.locator('#app .phone img').first()).toHaveAttribute('alt', lang === 'sv' ? /Identifiera/ : /Identify/);
    }
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
    await expect(tour.locator('[data-ch]')).toHaveText('Lyssna efter sång');
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
        for (let i = 1; i <= 5; i++) {
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
    test('karusellen och planschen står still', async ({ page }) => {
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
    test('bildtextlistan visar alla sex skärmar', async ({ page }) => {
      await page.goto('/sv/');
      await expect(page.locator('#app .cap-list li')).toHaveCount(6);
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

  // The species pages (Task 14) carry a lastmod of their own from their data (spec §12); every other page
  // still has none.
  test('webbplatskartan har lastmod för inläggen och artsidorna, inte för andra sidor', async ({ page }) => {
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
    const isSpeciesPage = (loc: string) => /^https:\/\/birdy\.community\/(sv\/arter|species)\//.test(loc);
    expect(blockFor('https://birdy.community/sv/arter/talgoxe/')).toMatch(/<lastmod>2026-11-25/);
    for (const block of blocks) {
      const loc = block.match(/<loc>(.*?)<\/loc>/)?.[1] ?? block;
      if (postUrls.includes(loc) || isSpeciesPage(loc)) continue;
      expect(block, loc).not.toMatch(/<lastmod>/);
    }
  });

  for (const [width, height] of [[390, 844], [1440, 900]] as const) {
    test(`menyn på inlägget blir espressobrun innan rubriken når den i ${width}×${height}`, async ({ page }) => {
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

  test('listkickern och karusellkickern är apricot (espresso), inte bladets stil', async ({ page }) => {
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
