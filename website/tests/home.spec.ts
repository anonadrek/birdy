import { test, expect } from '@playwright/test';
import { trackConsoleErrors } from './test-helpers';

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
      imgs.every((img) => (img as HTMLImageElement).complete && (img as HTMLImageElement).naturalWidth > 0))).toBe(true);
  });

  test.describe('med minskad rörelse', () => {
    test.use({ contextOptions: { reducedMotion: 'reduce' } });
    test('karusellen och rödhaken står still', async ({ page }) => {
      await page.goto('/sv/');
      await page.locator('#app').scrollIntoViewIfNeeded();
      const transforms = await page.locator('#app .slide').evaluateAll((els) => els.map((e) => getComputedStyle(e).transform));
      expect(transforms.every((t) => t === 'none')).toBe(true);
      const running = await page.evaluate(() => document.getAnimations().filter((a) => a.playState === 'running').length);
      expect(running).toBe(0);
    });
  });
});
